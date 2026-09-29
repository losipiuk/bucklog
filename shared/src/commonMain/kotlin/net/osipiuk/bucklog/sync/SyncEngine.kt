package net.osipiuk.bucklog.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import net.osipiuk.bucklog.data.LocalStore
import net.osipiuk.bucklog.data.OutboxOp
import net.osipiuk.bucklog.data.SyncStatus
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
                val pushed = push(spreadsheetId, tz, snapshot)
                store.putValue(SyncStatus.DRIVE_VERSION, version)
                SyncOutcome.Synced(snapshot.rows.size, pushed)
            }
            store.putValue(SyncStatus.LAST_SUCCESS, clock.now().toEpochMilliseconds().toString())
            store.putValue(SyncStatus.ERROR, null)
            outcome
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            store.putValue(SyncStatus.ERROR, e.message ?: e::class.simpleName)
            SyncOutcome.Failed(e)
        }
    }

    /** What the sheet looked like at pull time. */
    private class Snapshot(
        val sheetIds: Map<String, Int>,
        val rows: List<SheetRow>,
        /** Rows by (final) ID. */
        val byId: Map<String, SheetRow>,
        /** Last non-empty row number per year tab. */
        val lastRow: Map<String, Int>,
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
        store.applyPull(remote, raw, pendingIds = store.pendingEntryOps().mapTo(HashSet()) { it.entryId })
        return Snapshot(
            sheetIds = sheetIds,
            rows = rows,
            byId = rows.associateBy { it.id!! },
            lastRow = rows.groupBy { it.tab }.mapValues { (_, r) -> r.maxOf { it.rowNumber } },
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
     */
    private suspend fun push(spreadsheetId: String, tz: TimeZone, snapshot: Snapshot): Int {
        val ops = store.pendingEntryOps()
        val latest = ops.associateBy { it.entryId }.values
        val updates = mutableListOf<ValueRange>()
        val deletes = mutableMapOf<String, MutableList<Int>>()
        val appends = mutableMapOf<String, MutableList<Entry>>()
        val synced = mutableListOf<String>()
        for (op in latest) {
            val remote = snapshot.byId[op.entryId]
            if (op.kind == OutboxOp.Kind.DELETE) {
                remote?.let { deletes.getOrPut(it.tab) { mutableListOf() } += it.rowNumber }
                continue
            }
            val entry = store.entry(op.entryId) ?: continue
            val tab = SheetLayout.yearTab(entry.timestamp.toLocalDateTime(tz).year)
            if (remote != null && remote.tab == tab) {
                updates += ValueRange(range = SheetLayout.range(tab, "A${remote.rowNumber}:H${remote.rowNumber}"), values = listOf(SheetRows.toRow(entry, tz)))
            } else {
                // New entry, or its date moved it to another year's tab.
                remote?.let { deletes.getOrPut(it.tab) { mutableListOf() } += it.rowNumber }
                appends.getOrPut(tab) { mutableListOf() } += entry
            }
            synced += entry.id
        }
        val touched = latest.mapTo(HashSet()) { it.entryId }
        for (row in snapshot.idFixes) {
            if (row.id !in touched) updates += ValueRange(range = SheetLayout.range(row.tab, "H${row.rowNumber}"), values = listOf(listOf(JsonPrimitive(row.id))))
        }

        if (updates.isNotEmpty()) sheets.batchUpdateValues(spreadsheetId, updates)
        val newTabs = setup.addYearTabs(spreadsheetId, appends.keys.filter { it !in snapshot.sheetIds }.sorted())
        for ((tab, entries) in appends) {
            val after = if (tab in newTabs) 1 else snapshot.lastRow[tab] ?: 1
            sheets.append(
                spreadsheetId,
                SheetLayout.range(tab, "A${after + 1}:H"),
                entries.sortedBy { it.timestamp }.map { SheetRows.toRow(it, tz) },
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
        store.completeEntryOps(synced, maxSeq = ops.maxOfOrNull { it.seq } ?: 0)
        return latest.size
    }

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
