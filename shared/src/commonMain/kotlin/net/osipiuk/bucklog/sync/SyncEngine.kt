package net.osipiuk.bucklog.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import net.osipiuk.bucklog.data.LocalStore
import net.osipiuk.bucklog.data.OutboxOp
import net.osipiuk.bucklog.data.SyncErrorKind
import net.osipiuk.bucklog.data.SyncStatus
import kotlinx.io.IOException
import net.osipiuk.bucklog.google.AuthRequiredException
import net.osipiuk.bucklog.google.GoogleApiException
import net.osipiuk.bucklog.domain.Entry
import net.osipiuk.bucklog.domain.EntryStatus
import net.osipiuk.bucklog.domain.newEntryId
import net.osipiuk.bucklog.google.DriveClient
import net.osipiuk.bucklog.google.SheetsClient
import net.osipiuk.bucklog.google.ValueRange
import net.osipiuk.bucklog.sheet.CategoryUploader
import net.osipiuk.bucklog.sheet.SheetLayout
import net.osipiuk.bucklog.sheet.SheetSetup
import kotlin.time.Clock
import kotlin.time.Instant

sealed interface SyncOutcome {
    data object NotConfigured : SyncOutcome
    data object UpToDate : SyncOutcome
    data class Synced(val pulledRows: Int, val pushedChanges: Int) : SyncOutcome
    data class Failed(val error: Exception) : SyncOutcome
}

/**
 * Two-way sync between the local DB and the family sheet (SPEC §6).
 *
 * The sheet is the shared truth; rows are matched by ID only, never by position. A sync:
 * uploads queued categories, checks the Drive version, and if anything may have changed
 * pulls every year tab, merges it (assigning missing IDs, fixing duplicates, flagging
 * unparseable rows), fills in exchange rates, and pushes queued changes.
 */
class SyncEngine(
    private val store: LocalStore,
    private val sheets: SheetsClient,
    private val drive: DriveClient,
    private val setup: SheetSetup,
    private val categories: CategoryUploader,
    private val rates: ExchangeRates,
    private val clock: Clock = Clock.System,
) {
    private val mutex = Mutex()

    suspend fun sync(force: Boolean = false): SyncOutcome = mutex.withLock {
        val config = store.config.first()
        val spreadsheetId = config.spreadsheetId ?: return SyncOutcome.NotConfigured
        val tz = config.timeZone?.let { runCatching { TimeZone.of(it) }.getOrNull() } ?: TimeZone.currentSystemDefault()
        try {
            categories.upload(spreadsheetId)
            // Read the version before pulling: a change made after this point triggers another pull next time.
            val version = drive.getFile(spreadsheetId).version
            val outcome = if (!force && version != null && version == store.value(SyncStatus.DRIVE_VERSION) &&
                store.pendingEntryOps().isEmpty() && store.value(SyncStatus.FORMATS_APPLIED) == spreadsheetId
            ) {
                SyncOutcome.UpToDate
            } else {
                val snapshot = pull(spreadsheetId, tz, config.mainCurrency)
                fillRates(config.mainCurrency, tz)
                val pushed = push(spreadsheetId, tz, snapshot, who = config.myName.orEmpty())
                store.putValue(SyncStatus.DRIVE_VERSION, version)
                SyncOutcome.Synced(snapshot.rows.size, pushed)
            }
            store.putValue(SyncStatus.LAST_SUCCESS, clock.now().toEpochMilliseconds().toString())
            store.putValue(SyncStatus.ERROR, null)
            store.putValue(SyncStatus.ERROR_KIND, null)
            outcome
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            store.putValue(SyncStatus.ERROR, e.message ?: e::class.simpleName)
            store.putValue(SyncStatus.ERROR_KIND, classify(e).name)
            SyncOutcome.Failed(e)
        }
    }

    private fun classify(e: Exception): SyncErrorKind = when {
        e is AuthRequiredException -> SyncErrorKind.AUTH
        e is GoogleApiException && e.httpStatus == 401 -> SyncErrorKind.AUTH
        e is GoogleApiException && e.httpStatus in setOf(403, 404) -> SyncErrorKind.ACCESS
        e is IOException -> SyncErrorKind.OFFLINE
        else -> SyncErrorKind.OTHER
    }

    /** What the sheet looked like at pull time. */
    private class Snapshot(
        val sheetIds: Map<String, Int>,
        val rows: List<SheetRow>,
        /** Rows by (final) ID. */
        val byId: Map<String, SheetRow>,
        /** ID cells to write: rows typed by hand without one, and duplicates. */
        val idFixes: List<SheetRow>,
    )

    private suspend fun pull(spreadsheetId: String, tz: TimeZone, localMainCurrency: String): Snapshot {
        val spreadsheet = sheets.get(spreadsheetId)
        val sheetIds = spreadsheet.sheets.associate { it.properties.title to (it.properties.sheetId ?: 0) }
        val yearTabs = sheetIds.keys.filter(SheetLayout::isYearTab).sorted()
        val ranges = listOf(SheetLayout.range(SheetLayout.CATEGORIES, "A2:C"), SheetLayout.range(SheetLayout.SETTINGS, "A2:B")) +
            yearTabs.map { SheetLayout.range(it, "A2:H") }
        val values = sheets.batchGet(spreadsheetId, ranges).map { it.values }

        val sheetConfig = SheetSetup.parseSettings(values[1])
        val mainCurrency = sheetConfig["main_currency"]?.uppercase()?.takeIf { it.length == 3 } ?: localMainCurrency
        store.replaceCategories(SheetSetup.parseCategories(values[0]))
        if (mainCurrency != localMainCurrency) store.updateConfig(mainCurrency = mainCurrency)

        val seen = HashSet<String>()
        val idFixes = mutableListOf<SheetRow>()
        val rows = yearTabs.zip(values.drop(2)).flatMap { (tab, tabRows) ->
            SheetRows.parse(tab, tabRows, tz, mainCurrency).map { row ->
                if (row.id != null && seen.add(row.id)) return@map row
                // Missing ID (typed by hand) or a duplicate (copy-pasted row): give it a fresh one.
                val id = generateSequence { newEntryId() }.first { it !in seen }.also { seen += it }
                row.copy(id = id, entry = SheetRows.parseEntry(id, row.cells, tz, mainCurrency)).also { idFixes += it }
            }
        }
        val remote = rows.map { row -> row.entry ?: invalidEntry(row, tz, mainCurrency) }
        val raw = rows.filter { it.entry == null }.associate { it.id!! to SheetRows.rawJson(it.cells) }
        store.applyPull(remote, raw, pendingIds = store.pendingEntryOps().mapTo(HashSet()) { it.entryId }, now = clock.now())
        return Snapshot(
            sheetIds = sheetIds,
            rows = rows,
            byId = rows.associateBy { it.id!! },
            idFixes = idFixes,
        )
    }

    /** Placeholder for a row that can't be parsed; the sheet row itself is left alone. */
    private fun invalidEntry(row: SheetRow, tz: TimeZone, mainCurrency: String): Entry {
        fun text(i: Int) = (row.cells.getOrNull(i) as? JsonPrimitive)?.content.orEmpty()
        val date = (row.cells.getOrNull(0) as? JsonPrimitive)?.let { cell ->
            cell.content.toDoubleOrNull()?.let { SheetDates.fromSerial(it, tz) } ?: SheetDates.parseText(cell.content, tz)
        }
        return Entry(
            id = row.id!!, timestamp = date ?: Instant.fromEpochMilliseconds(0), who = text(1), what = text(2),
            category = text(3), amountMinor = 0, currency = mainCurrency, rate = null, status = EntryStatus.INVALID,
        )
    }

    private suspend fun fillRates(mainCurrency: String, tz: TimeZone) {
        for ((id, timestamp, currency) in store.entriesMissingRate(mainCurrency)) {
            val rate = rates.rate(currency, mainCurrency, timestamp.toLocalDateTime(tz).date) ?: continue
            store.setRate(id, rate)
        }
    }

    /**
     * Writes queued changes. Order keeps row numbers valid and never loses data if interrupted:
     * in-place updates, then appends below the last row, then deletions bottom-up (a move to
     * another year's tab is copy-then-delete). Every step is idempotent by row ID, so a retry
     * after a partial push converges. Returns the number of entries changed.
     *
     * Rows are addressed by number, so someone else deleting rows or sorting the sheet between
     * our pull and this push would shift them. To keep that window to milliseconds, the tabs are
     * re-read right before writing and every row is found again by its ID.
     */
    private suspend fun push(spreadsheetId: String, tz: TimeZone, snapshot: Snapshot, who: String): Int {
        val ops = store.pendingEntryOps()
        val latest = ops.associateBy { it.entryId }.values
        val touched = latest.mapTo(HashSet()) { it.entryId }
        val entries = latest.filter { it.kind == OutboxOp.Kind.UPSERT }.mapNotNull { store.entry(it.entryId) }.associateBy { it.id }
        val fixes = snapshot.idFixes.filter { it.id !in touched }
        fun tabOf(e: Entry) = SheetLayout.yearTab(e.timestamp.toLocalDateTime(tz).year)

        val rowTabs = buildSet {
            latest.forEach { op -> snapshot.byId[op.entryId]?.let { add(it.tab) } }
            fixes.forEach { add(it.tab) }
            entries.values.map(::tabOf).filter { it in snapshot.sheetIds }.forEach(::add)
        }.sorted()
        val fresh: Map<String, List<List<JsonElement>>> = if (rowTabs.isEmpty()) {
            emptyMap()
        } else {
            rowTabs.zip(sheets.batchGet(spreadsheetId, rowTabs.map { SheetLayout.range(it, "A2:H") })).associate { (tab, range) -> tab to range.values }
        }
        val rowById = buildMap {
            fresh.forEach { (tab, rows) -> rows.forEachIndexed { i, cells -> idOf(cells)?.let { putIfAbsent(it, tab to i + 2) } } }
        }

        val updates = mutableListOf<ValueRange>()
        val deletes = mutableMapOf<String, MutableList<Int>>()
        val appends = mutableMapOf<String, MutableList<Entry>>()
        val synced = mutableListOf<String>()
        val done = mutableSetOf<String>()
        val deleted = mutableListOf<String>()
        for (op in latest) {
            val id = op.entryId
            val now = rowById[id]
            if (op.kind == OutboxOp.Kind.DELETE) {
                now?.let { (tab, row) -> deletes.getOrPut(tab) { mutableListOf() } += row }
                if (now != null) deleted += id
                done += id
                continue
            }
            val entry = entries[id]
            if (entry == null) {
                done += id // deleted locally since it was queued
                continue
            }
            // Someone deleted the row after our pull: leave it queued, the next pull applies their delete.
            if (snapshot.byId[id] != null && now == null) continue
            val tab = tabOf(entry)
            if (now != null && now.first == tab) {
                updates += ValueRange(range = SheetLayout.range(tab, "A${now.second}:H${now.second}"), values = listOf(SheetRows.toRow(entry, tz)))
            } else {
                // New entry, or its date moved it to another year's tab.
                now?.let { (oldTab, row) -> deletes.getOrPut(oldTab) { mutableListOf() } += row }
                appends.getOrPut(tab) { mutableListOf() } += entry
            }
            synced += id
            done += id
        }
        for (fix in fixes) {
            // Stamp the ID only if that row still holds exactly what the pull saw (no ID, or the duplicate
            // one), i.e. nobody sorted, inserted or deleted rows meanwhile.
            val cells = fresh[fix.tab]?.getOrNull(fix.rowNumber - 2) ?: continue
            if (idOf(cells) == idOf(fix.cells) && sameContent(cells, fix.cells)) {
                updates += ValueRange(range = SheetLayout.range(fix.tab, "H${fix.rowNumber}"), values = listOf(listOf(JsonPrimitive(fix.id))))
            }
        }

        if (updates.isNotEmpty()) sheets.batchUpdateValues(spreadsheetId, updates)
        val newTabs = setup.addYearTabs(spreadsheetId, appends.keys.filter { it !in snapshot.sheetIds }.sorted())
        for ((tab, tabEntries) in appends) {
            // Values come back without trailing empty rows, so the last used row is size + 1 (data starts at row 2).
            val after = if (tab in newTabs) 1 else (fresh[tab]?.size ?: 0) + 1
            sheets.append(
                spreadsheetId,
                SheetLayout.range(tab, "A${after + 1}:H"),
                tabEntries.sortedBy { it.timestamp }.map { SheetRows.toRow(it, tz) },
            )
        }
        // Appended rows are inserted rows, which don't inherit column formats: reapply them so the
        // Date column shows dates, not serial numbers. Once per spreadsheet, fix every year tab.
        val formatAll = store.value(SyncStatus.FORMATS_APPLIED) != spreadsheetId
        val formatTabs = (if (formatAll) snapshot.sheetIds.keys.filter(SheetLayout::isYearTab) else emptyList()) +
            appends.keys.filter { it !in newTabs }
        val requests = formatTabs.distinct().flatMap { SheetLayout.yearTabFormat(snapshot.sheetIds.getValue(it)) } +
            deletes.flatMap { (tab, rowNumbers) -> rowNumbers.sortedDescending().map { deleteRow(snapshot.sheetIds.getValue(tab), it) } }
        if (requests.isNotEmpty()) sheets.batchUpdate(spreadsheetId, requests)
        if (formatAll) store.putValue(SyncStatus.FORMATS_APPLIED, spreadsheetId)

        // Read back what we wrote: a collision we couldn't prevent should at least not go unnoticed.
        val writtenTabs = (fresh.keys + appends.keys).sorted()
        val after = if (writtenTabs.isEmpty() || (synced.isEmpty() && deleted.isEmpty())) {
            emptyMap()
        } else {
            buildMap {
                writtenTabs.zip(sheets.batchGet(spreadsheetId, writtenTabs.map { SheetLayout.range(it, "A2:H") })).forEach { (_, range) ->
                    range.values.forEach { cells -> idOf(cells)?.let { putIfAbsent(it, cells) } }
                }
            }
        }
        val mainCurrency = store.config.first().mainCurrency
        val failedWrites = synced.filter { id ->
            val cells = after[id] ?: return@filter true
            val read = SheetRows.parseEntry(id, cells, tz, mainCurrency) ?: return@filter true
            !sameValues(read, entries.getValue(id))
        }
        val failedDeletes = deleted.filter { it in after }
        store.completeEntryOps(synced - failedWrites.toSet(), done, maxSeq = ops.maxOfOrNull { it.seq } ?: 0)
        if (failedWrites.isNotEmpty() || failedDeletes.isNotEmpty()) {
            store.requeue(failedWrites)
            store.requeueDeletes(failedDeletes)
            store.putValue(SyncStatus.WARNING, (failedWrites.size + failedDeletes.size).toString())
        } else if (synced.isNotEmpty() || deleted.isNotEmpty()) {
            store.putValue(SyncStatus.WARNING, null)
        }

        val logged = synced.filter { it !in failedWrites }.map { id ->
            val before = snapshot.byId[id]?.entry
            LogRow(if (before == null) "add" else "edit", id, before, entries.getValue(id))
        } + deleted.filter { it !in failedDeletes }.map { id -> LogRow("delete", id, snapshot.byId[id]?.entry, null) }
        appendLog(spreadsheetId, tz, snapshot, who, logged)
        return latest.size
    }

    private class LogRow(val action: String, val id: String, val before: Entry?, val after: Entry?)

    /**
     * Appends one row per change to the Log tab (created on first use): who changed what, with the values
     * before and after. Appends can't collide. Best effort: a failure here never fails the sync.
     */
    private suspend fun appendLog(spreadsheetId: String, tz: TimeZone, snapshot: Snapshot, who: String, rows: List<LogRow>) {
        if (rows.isEmpty()) return
        try {
            if (SheetLayout.LOG !in snapshot.sheetIds) setup.addLogTab(spreadsheetId)
            val time = JsonPrimitive(SheetDates.toSerial(clock.now(), tz))
            sheets.append(
                spreadsheetId,
                SheetLayout.range(SheetLayout.LOG, "A:F"),
                rows.map { listOf(time, JsonPrimitive(who), JsonPrimitive(it.action), JsonPrimitive(it.id), JsonPrimitive(describe(it.before, tz)), JsonPrimitive(describe(it.after, tz))) },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The data itself is written and verified; losing a log line is acceptable.
        }
    }

    /** "28.09.2026 18:42 · Milk · Groceries · 35.30 PLN · Łukasz" */
    private fun describe(e: Entry?, tz: TimeZone): String {
        e ?: return ""
        val t = e.timestamp.toLocalDateTime(tz)
        fun two(n: Int) = n.toString().padStart(2, '0')
        val date = "${two(t.day)}.${two(t.month.ordinal + 1)}.${t.year} ${two(t.hour)}:${two(t.minute)}"
        val amount = SheetRows.formatMinor(e.amountMinor, net.osipiuk.bucklog.domain.Currencies.digits(e.currency))
        return listOf(date, e.what, e.category, "$amount ${e.currency}", e.who).filter { it.isNotBlank() }.joinToString(" · ")
    }

    /** Whether the row read back holds what we wrote (sheets keep whole seconds and plain numbers). */
    private fun sameValues(read: Entry, wrote: Entry): Boolean =
        read.timestamp.epochSeconds == wrote.timestamp.epochSeconds && read.who == wrote.who.trim() &&
            read.what == wrote.what.trim() && read.category == wrote.category.trim() &&
            read.amountMinor == wrote.amountMinor && read.currency == wrote.currency &&
            read.rate?.toDoubleOrNull() == wrote.rate?.toDoubleOrNull()

    private fun idOf(cells: List<JsonElement>): String? = (cells.getOrNull(7) as? JsonPrimitive)?.content?.trim()?.ifEmpty { null }

    /** Same Date…Rate cells, i.e. still the same row. */
    private fun sameContent(a: List<JsonElement>, b: List<JsonElement>): Boolean =
        (0 until 7).all { i -> (a.getOrNull(i) as? JsonPrimitive)?.content.orEmpty() == (b.getOrNull(i) as? JsonPrimitive)?.content.orEmpty() }

    private fun deleteRow(sheetId: Int, rowNumber: Int) = buildJsonObject {
        putJsonObject("deleteDimension") {
            putJsonObject("range") {
                put("sheetId", sheetId)
                put("dimension", "ROWS")
                put("startIndex", rowNumber - 1)
                put("endIndex", rowNumber)
            }
        }
    }
}
