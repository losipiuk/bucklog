package net.osipiuk.bucklog.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.CoroutineDispatcher
import app.cash.sqldelight.coroutines.mapToOne
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import net.osipiuk.bucklog.db.BucklogDatabase
import net.osipiuk.bucklog.domain.Category
import net.osipiuk.bucklog.domain.Entry
import net.osipiuk.bucklog.domain.EntryStatus
import net.osipiuk.bucklog.domain.HistoryItem
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** The on-device database: entries, the sync outbox, categories and settings. */
class LocalStore(
    private val db: BucklogDatabase,
    private val io: CoroutineDispatcher,
) {
    private val q = db.bucklogQueries

    val config: Flow<AppConfig> =
        q.allValues().asFlow().mapToList(io).map { rows -> AppConfig.from(rows.associate { it.name to it.content }) }

    suspend fun updateConfig(
        myName: String? = null,
        accountEmail: String? = null,
        spreadsheetId: String? = null,
        spreadsheetName: String? = null,
        mainCurrency: String? = null,
        timeZone: String? = null,
        lastCurrency: String? = null,
    ) = withContext(io) {
        q.transaction {
            myName?.let { q.putValue(AppConfig.MY_NAME, it) }
            accountEmail?.let { q.putValue(AppConfig.ACCOUNT_EMAIL, it) }
            spreadsheetId?.let { q.putValue(AppConfig.SPREADSHEET_ID, it) }
            spreadsheetName?.let { q.putValue(AppConfig.SPREADSHEET_NAME, it) }
            mainCurrency?.let { q.putValue(AppConfig.MAIN_CURRENCY, it) }
            timeZone?.let { q.putValue(AppConfig.TIME_ZONE, it) }
            lastCurrency?.let { q.putValue(AppConfig.LAST_CURRENCY, it) }
        }
    }

    /** Stores a new entry and queues it for upload in one transaction. */
    suspend fun addEntry(entry: Entry) = addEntries(listOf(entry))

    suspend fun addEntries(entries: List<Entry>) = withContext(io) {
        q.transaction {
            for (entry in entries) {
                q.insert(entry, raw = null, inSheet = false)
                q.enqueue(OP_UPSERT, entry.id)
            }
        }
    }

    fun recentEntries(limit: Long): Flow<List<Entry>> = q.recentEntries(limit, ::toEntry).asFlow().mapToList(io)

    /** Every entry, newest first (including rows that couldn't be read). */
    val allEntries: Flow<List<Entry>> = q.allEntries(::toEntry).asFlow().mapToList(io)

    /** Names in the Who column, most recently active first. */
    val people: Flow<List<String>> = q.knownPeople().asFlow().mapToList(io)

    /** How many entries name [who] in the Who column. */
    suspend fun countEntriesBy(who: String): Long = withContext(io) { q.entryIdsByWho(who).executeAsList().size.toLong() }

    /** Renames [from] to [to] in the Who column of every entry, queued for upload. */
    suspend fun renamePerson(from: String, to: String) = withContext(io) {
        q.transaction {
            val ids = q.entryIdsByWho(from).executeAsList()
            q.renameWho(to, from)
            ids.forEach { q.enqueue(OP_UPSERT, it) }
        }
    }

    /** Saves an edit and queues it for upload. The exchange rate is kept only if [Entry.rate] still holds it. */
    suspend fun updateEntry(e: Entry) = withContext(io) {
        q.transaction {
            q.updateEntry(e.timestamp.toEpochMilliseconds(), e.who, e.what, e.category, e.amountMinor, e.currency, e.rate, e.id)
            q.enqueue(OP_UPSERT, e.id)
        }
    }

    /** Deletes an entry; a row already in the sheet is queued for deletion there. */
    suspend fun deleteEntry(id: String) = withContext(io) {
        q.transaction {
            val inSheet = q.isInSheet(id).executeAsOneOrNull() == 1L
            q.deleteEntry(id)
            q.deleteEntryOpsFor(id)
            if (inSheet) q.enqueue(OP_DELETE, id)
        }
    }

    /**
     * Undo of [deleteEntry]. The entry comes back as new: if its deletion already reached the sheet
     * it's appended again, otherwise the queued delete is superseded and the row updated in place.
     */
    suspend fun restoreEntry(e: Entry) = withContext(io) {
        q.transaction {
            q.insert(e.copy(status = EntryStatus.PENDING), raw = null, inSheet = false)
            q.enqueue(OP_UPSERT, e.id)
        }
    }

    /**
     * Renames a category and/or changes its emoji or archived flag. A rename also moves every
     * entry to the new name (queued for upload), and the Categories tab row is updated on sync.
     */
    suspend fun updateCategory(oldName: String, updated: Category) = withContext(io) {
        q.transaction {
            val position = q.categoryPosition(oldName).executeAsOneOrNull() ?: q.nextCategoryPosition().executeAsOne()
            q.deleteCategory(oldName)
            q.insertCategory(updated.name, updated.emoji, if (updated.archived) 1 else 0, position)
            if (updated.name != oldName) {
                val ids = q.entryIdsWithCategory(oldName).executeAsList()
                q.recategorize(updated.name, oldName)
                ids.forEach { q.enqueue(OP_UPSERT, it) }
            }
            q.enqueueWithPayload(OP_UPDATE_CATEGORY, oldName, CategoryUpdate.encode(updated))
        }
    }

    /** Queued category changes, oldest first. */
    suspend fun pendingCategoryOps(): List<CategoryOp> = withContext(io) {
        q.categoryOps().executeAsList().map { row ->
            when (row.op) {
                OP_ADD_CATEGORY -> CategoryOp.Add(row.seq, row.entry_id)
                else -> CategoryOp.Update(row.seq, row.entry_id, CategoryUpdate.decode(row.payload!!))
            }
        }
    }

    suspend fun entry(id: String): Entry? = withContext(io) { q.entryById(id, ::toEntry).executeAsOneOrNull() }

    // ---- Sync ----

    /**
     * Makes the local copy match the pulled sheet rows (SPEC §6.4), except entries with pending local changes:
     * - pulled rows without pending changes are upserted;
     * - entries known to be in the sheet but no longer there are deleted, and any pending change
     *   to them is dropped (a remote delete wins);
     * - new local entries (never in the sheet) are kept for upload.
     * [raw] holds the original cells of rows that couldn't be parsed (status INVALID).
     * Entries removed this way are kept in "Recently removed" (as of [now]) so they can be restored.
     */
    suspend fun applyPull(remote: List<Entry>, raw: Map<String, String>, pendingIds: Set<String>, now: Instant) = withContext(io) {
        q.transaction {
            val remoteIds = remote.mapTo(HashSet()) { it.id }
            for (entry in remote) {
                if (entry.id !in pendingIds) q.insert(entry, raw[entry.id], inSheet = true)
            }
            for (id in q.entryIdsInSheet().executeAsList()) {
                if (id !in remoteIds) {
                    q.keepRemoved(now.toEpochMilliseconds(), id)
                    q.deleteEntry(id)
                    q.deleteEntryOpsFor(id)
                }
            }
            q.pruneRemoved((now - KEEP_REMOVED).toEpochMilliseconds())
        }
    }

    /** Entries that disappeared from the sheet, newest first. */
    val removedEntries: Flow<List<RemovedEntry>> = q.removedEntries { id, ts, who, what, category, amount, currency, rate, removedAt ->
        RemovedEntry(
            Entry(id, Instant.fromEpochMilliseconds(ts), who, what, category, amount, currency, rate, EntryStatus.PENDING),
            Instant.fromEpochMilliseconds(removedAt),
        )
    }.asFlow().mapToList(io)

    /** Puts a removed entry back: it's uploaded again as new. */
    suspend fun restoreRemoved(removed: RemovedEntry) = withContext(io) {
        q.transaction {
            q.insert(removed.entry.copy(status = EntryStatus.PENDING), raw = null, inSheet = false)
            q.enqueue(OP_UPSERT, removed.entry.id)
            q.forgetRemoved(removed.entry.id)
        }
    }

    suspend fun dismissRemoved(id: String) = withContext(io) { q.forgetRemoved(id) }

    /** Queues deletions again (a delete that didn't land as expected). */
    suspend fun requeueDeletes(ids: Collection<String>) = withContext(io) {
        q.transaction { ids.forEach { q.enqueue(OP_DELETE, it) } }
    }

    /** Queues entries for upload again (a write that didn't land as expected). */
    suspend fun requeue(ids: Collection<String>) = withContext(io) {
        q.transaction {
            ids.forEach {
                q.requeueUpsert(it)
                q.enqueue(OP_UPSERT, it)
            }
        }
    }

    /** Entries in a foreign currency with no exchange rate yet: (id, timestamp, currency). */
    suspend fun entriesMissingRate(mainCurrency: String): List<Triple<String, Instant, String>> = withContext(io) {
        q.entriesMissingRate(mainCurrency).executeAsList().map { Triple(it.id, Instant.fromEpochMilliseconds(it.ts), it.currency) }
    }

    /** Stores a fetched rate and queues the entry so the Rate cell gets written. */
    suspend fun setRate(id: String, rate: String) = withContext(io) {
        q.transaction {
            q.setRate(rate, id)
            q.enqueue(OP_UPSERT, id)
        }
    }

    suspend fun pendingEntryOps(): List<OutboxOp> = withContext(io) {
        q.pendingEntryOps().executeAsList().map { OutboxOp(it.seq, OutboxOp.Kind.valueOf(it.op), it.entry_id) }
    }

    /**
     * After a successful push: marks [synced] entries as in the sheet, and drops the queued ops of [done]
     * entries up to [maxSeq]. Ops queued later, or for entries the push had to skip, stay queued.
     */
    suspend fun completeEntryOps(synced: Collection<String>, done: Collection<String>, maxSeq: Long) = withContext(io) {
        q.transaction {
            synced.forEach { q.markSynced(it) }
            done.forEach { q.deleteEntryOpsForUpTo(it, maxSeq) }
        }
    }

    /** A named value as it changes (e.g. a UI preference). */
    fun valueFlow(name: String): Flow<String?> = q.getValue(name).asFlow().map { it.executeAsOneOrNull() }

    /** Small named values: sync bookkeeping and UI preferences. */
    suspend fun value(name: String): String? = withContext(io) { q.getValue(name).executeAsOneOrNull() }

    suspend fun putValue(name: String, value: String?) = withContext(io) {
        if (value == null) q.deleteValue(name) else q.putValue(name, value)
    }

    suspend fun cachedRate(key: String, date: String): String? = withContext(io) { q.getRate(key, date).executeAsOneOrNull() }

    suspend fun cacheRate(key: String, date: String, rate: String) = withContext(io) { q.putRate(key, date, rate) }

    val syncStatus: Flow<SyncStatus> = combine(
        q.allValues().asFlow().mapToList(io),
        q.pendingCount().asFlow().mapToOne(io),
    ) { rows, pending ->
        val values = rows.associate { it.name to it.content }
        SyncStatus(
            lastSuccess = values[SyncStatus.LAST_SUCCESS]?.toLongOrNull()?.let(Instant::fromEpochMilliseconds),
            error = values[SyncStatus.ERROR],
            warning = values[SyncStatus.WARNING],
            errorKind = values[SyncStatus.ERROR_KIND]?.let { k -> SyncErrorKind.entries.firstOrNull { it.name == k } },
            pendingChanges = pending,
        )
    }

    val history: Flow<List<HistoryItem>> =
        q.historyItems { what, category, who, ts -> HistoryItem(what, category, who, Instant.fromEpochMilliseconds(ts)) }
            .asFlow().mapToList(io)

    fun recentCurrencies(limit: Long): Flow<List<String>> = q.recentCurrencies(limit).asFlow().mapToList(io)

    val categories: Flow<List<Category>> =
        q.categories { name, emoji, archived, _ -> Category(name, emoji, archived != 0L) }.asFlow().mapToList(io)

    suspend fun replaceCategories(categories: List<Category>) = withContext(io) {
        q.transaction {
            q.deleteCategories()
            categories.forEachIndexed { i, c -> q.insertCategory(c.name, c.emoji, if (c.archived) 1 else 0, i.toLong()) }
        }
    }

    /** Appends a category locally and queues it for the sheet's Categories tab. */
    suspend fun addCategory(category: Category) = withContext(io) {
        q.transaction {
            q.insertCategory(category.name, category.emoji, if (category.archived) 1 else 0, q.nextCategoryPosition().executeAsOne())
            q.enqueue(OP_ADD_CATEGORY, category.name)
        }
    }

    suspend fun completeOutbox(seq: Long) = withContext(io) { q.deleteOutbox(seq) }

    suspend fun failOutbox(seq: Long, error: String) = withContext(io) { q.recordOutboxFailure(error, seq) }

    suspend fun outboxSize(): Long = withContext(io) { q.outboxSize().executeAsOne() }

    /** Disconnects from the current sheet (keeps name and account); its local copy and queue are dropped. */
    suspend fun forgetSheet() = withContext(io) {
        q.transaction {
            q.clearSheetValues()
            q.clearEntries()
            q.clearRemoved()
            q.clearOutbox()
            q.deleteCategories()
        }
    }

    /** Forgets the configuration and all local data (the sheet is untouched). */
    suspend fun reset() = withContext(io) {
        q.transaction {
            q.clearValues()
            q.clearEntries()
            q.clearRemoved()
            q.clearOutbox()
            q.deleteCategories()
        }
    }

    private companion object {
        const val OP_ADD_CATEGORY = "ADD_CATEGORY"
        const val OP_UPSERT = "UPSERT"
        const val OP_DELETE = "DELETE"
        const val OP_UPDATE_CATEGORY = "UPDATE_CATEGORY"
        val KEEP_REMOVED = 60.days
    }

    private fun toEntry(
        id: String, ts: Long, who: String, what: String, category: String, amount: Long,
        currency: String, rate: String?, status: String, @Suppress("UNUSED_PARAMETER") raw: String?,
        @Suppress("UNUSED_PARAMETER") inSheet: Long,
    ) = Entry(id, Instant.fromEpochMilliseconds(ts), who, what, category, amount, currency, rate, EntryStatus.valueOf(status))

    private fun net.osipiuk.bucklog.db.BucklogQueries.insert(e: Entry, raw: String?, inSheet: Boolean) = insertEntry(
        e.id, e.timestamp.toEpochMilliseconds(), e.who, e.what, e.category, e.amountMinor, e.currency, e.rate,
        e.status.name, raw, if (inSheet) 1 else 0,
    )
}
