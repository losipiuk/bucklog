package net.osipiuk.bucklog.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.osipiuk.bucklog.db.BucklogDatabase
import net.osipiuk.bucklog.domain.Category
import net.osipiuk.bucklog.domain.Entry
import net.osipiuk.bucklog.domain.EntryStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class LocalStoreTest {
    private val store = LocalStore(
        BucklogDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { BucklogDatabase.Schema.create(it) }),
        Dispatchers.Unconfined,
    )

    private fun entry(id: String, at: String, currency: String = "PLN") =
        Entry(id, Instant.parse(at), "Łukasz", "Milk", "Groceries", 3530, currency, null, EntryStatus.PENDING)

    @Test
    fun configIsCompleteOnceNameSheetAndAccountAreSet() = runTest {
        assertFalse(store.config.first().isComplete)
        store.updateConfig(myName = "Łukasz", accountEmail = "l@example.com", spreadsheetId = "abc")
        val config = store.config.first()
        assertTrue(config.isComplete)
        assertEquals("PLN", config.defaultCurrency)
        store.updateConfig(lastCurrency = "EUR")
        assertEquals("EUR", store.config.first().defaultCurrency)
    }

    @Test
    fun addEntryQueuesUpload() = runTest {
        store.addEntry(entry("a", "2026-09-01T10:00:00Z"))
        store.addEntry(entry("b", "2026-09-02T10:00:00Z", currency = "EUR"))

        assertEquals(listOf("b", "a"), store.recentEntries(10).first().map { it.id })
        assertEquals(2, store.outboxSize())
        assertEquals(listOf("EUR", "PLN"), store.recentCurrencies(5).first())
        assertEquals(2, store.history.first().size)
    }

    @Test
    fun replaceCategoriesKeepsOrder() = runTest {
        store.replaceCategories(listOf(Category("B", "🅱️"), Category("A", null, archived = true)))
        assertEquals(listOf(Category("B", "🅱️"), Category("A", null, archived = true)), store.categories.first())
    }

    @Test
    fun resetForgetsEverything() = runTest {
        store.updateConfig(myName = "Łukasz")
        store.addEntry(entry("a", "2026-09-01T10:00:00Z"))
        store.reset()
        assertEquals(null, store.config.first().myName)
        assertEquals(0, store.outboxSize())
    }
}
