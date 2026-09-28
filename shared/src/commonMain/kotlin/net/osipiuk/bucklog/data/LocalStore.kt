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
    suspend fun addEntry(entry: Entry) = withContext(io) {
        q.transaction {
            q.insert(entry, raw = null, inSheet = false)
            q.enqueue(OP_UPSERT, entry.id)
        }
    }

    fun recentEntries(limit: Long): Flow<List<Entry>> = q.recentEntries(limit, ::toEntry).asFlow().mapToList(io)

    suspend fun entry(id: String): Entry? = withContext(io) { q.entryById(id, ::toEntry).executeAsOneOrNull() }

    // ---- Sync ----

    /**
     * Makes the local copy match the pulled sheet rows (SPEC §6.4), except entries with pending local changes:
     * - pulled rows without pending changes are upserted;
     * - entries known to be in the sheet but no longer there are deleted, and any pending change
     *   to them is dropped (a remote delete wins);
     * - new local entries (never in the sheet) are kept for upload.
     * [raw] holds the original cells of rows that couldn't be parsed (status INVALID).
     */
    suspend fun applyPull(remote: List<Entry>, raw: Map<String, String>, pendingIds: Set<String>) = withContext(io) {
        q.transaction {
            val remoteIds = remote.mapTo(HashSet()) { it.id }
            for (entry in remote) {
                if (entry.id !in pendingIds) q.insert(entry, raw[entry.id], inSheet = true)
            }
            for (id in q.entryIdsInSheet().executeAsList()) {
                if (id !in remoteIds) {
                    q.deleteEntry(id)
                    q.deleteEntryOpsFor(id)
                }
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

    /** After a successful push: marks [ids] as synced and drops entry ops up to [maxSeq] (later ones stay queued). */
    suspend fun completeEntryOps(ids: Collection<String>, maxSeq: Long) = withContext(io) {
        q.transaction {
            ids.forEach { q.markSynced(it) }
            q.deleteEntryOpsUpTo(maxSeq)
        }
    }

    suspend fun syncValue(name: String): String? = withContext(io) { q.getValue(name).executeAsOneOrNull() }

    suspend fun putSyncValue(name: String, value: String?) = withContext(io) {
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

    /** Categories created in the app but not yet in the sheet: outbox seq → name. */
    suspend fun pendingCategoryAdds(): List<Pair<Long, String>> = withContext(io) {
        q.outboxByOp(OP_ADD_CATEGORY).executeAsList().map { it.seq to it.entry_id }
    }

    suspend fun completeOutbox(seq: Long) = withContext(io) { q.deleteOutbox(seq) }

    suspend fun failOutbox(seq: Long, error: String) = withContext(io) { q.recordOutboxFailure(error, seq) }

    suspend fun outboxSize(): Long = withContext(io) { q.outboxSize().executeAsOne() }

    /** Forgets the configuration and all local data (the sheet is untouched). */
    suspend fun reset() = withContext(io) {
        q.transaction {
            q.clearValues()
            q.clearEntries()
            q.clearOutbox()
            q.deleteCategories()
        }
    }

    private companion object {
        const val OP_ADD_CATEGORY = "ADD_CATEGORY"
        const val OP_UPSERT = "UPSERT"
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
