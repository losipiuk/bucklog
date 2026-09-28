package net.osipiuk.bucklog.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
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
            q.insert(entry)
            q.enqueue("UPSERT", entry.id)
        }
    }

    fun recentEntries(limit: Long): Flow<List<Entry>> =
        q.recentEntries(limit) { id, ts, who, what, category, amount, currency, rate, status, _ ->
            Entry(id, Instant.fromEpochMilliseconds(ts), who, what, category, amount, currency, rate, EntryStatus.valueOf(status))
        }.asFlow().mapToList(io)

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
    }

    private fun net.osipiuk.bucklog.db.BucklogQueries.insert(e: Entry) = insertEntry(
        e.id, e.timestamp.toEpochMilliseconds(), e.who, e.what, e.category, e.amountMinor, e.currency, e.rate, e.status.name, null,
    )
}
