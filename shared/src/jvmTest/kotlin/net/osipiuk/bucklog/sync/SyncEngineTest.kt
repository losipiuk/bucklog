package net.osipiuk.bucklog.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.osipiuk.bucklog.data.LocalStore
import net.osipiuk.bucklog.db.BucklogDatabase
import net.osipiuk.bucklog.domain.Entry
import net.osipiuk.bucklog.domain.EntryStatus
import net.osipiuk.bucklog.sheet.CategoryUploader
import net.osipiuk.bucklog.sheet.SheetSetup
import net.osipiuk.bucklog.sync.FakeGoogle.Companion.headerRow
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class SyncEngineTest {
    private val google = FakeGoogle()
    private val store = LocalStore(
        BucklogDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { BucklogDatabase.Schema.create(it) }),
        Dispatchers.Unconfined,
    )
    private val rateRequests = mutableListOf<String>()
    private val engine = SyncEngine(
        store, google.sheets, google.drive, SheetSetup(google.sheets), CategoryUploader(store, google.sheets),
        rates = { from, to, date -> rateRequests += "$from/$to@$date"; if (from == "EUR" && to == "PLN") "4.2715" else null },
        clock = object : Clock { override fun now() = Instant.parse("2026-09-28T20:00:00Z") },
    )

    private val header = headerRow("Date", "Who", "What", "Category", "Amount", "Currency", "Rate", "ID")
    private val serial = 46293.75 // 2026-09-28 18:00 Warsaw

    @BeforeTest
    fun setUp() = runTest {
        google.addTab("Categories", headerRow("Name", "Emoji", "Archived"), listOf("Food", "🍎", false), listOf("Fuel", "⛽", false))
        google.addTab("Settings", headerRow("Key", "Value"), listOf("main_currency", "PLN"))
        google.addTab("2026", header)
        store.updateConfig(myName = "Łukasz", accountEmail = "l@x", spreadsheetId = google.spreadsheetId, timeZone = "Europe/Warsaw")
    }

    private fun entry(id: String, what: String = "Milk", amount: Long = 3530, currency: String = "PLN", at: String = "2026-09-28T16:00:00Z") =
        Entry(id, Instant.parse(at), "Łukasz", what, "Food", amount, currency, null, EntryStatus.PENDING)

    private suspend fun local() = store.recentEntries(1000).first().associateBy { it.id }

    @Test
    fun uploadsNewEntries() = runTest {
        store.addEntry(entry("aaaa0001"))

        assertIs<SyncOutcome.Synced>(engine.sync())

        assertEquals(listOf(serial, "Łukasz", "Milk", "Food", 35.3, "PLN", null, "aaaa0001"), google.rows("2026")[1])
        assertEquals(EntryStatus.SYNCED, local().getValue("aaaa0001").status)
        assertEquals(0, store.outboxSize())
    }

    @Test
    fun pullsCategoriesAndSettings() = runTest {
        google.handEdit { setCell("Settings", 2, 1, "EUR") }

        engine.sync()

        assertEquals(listOf("Food", "Fuel"), store.categories.first().map { it.name })
        assertEquals("EUR", store.config.first().mainCurrency)
    }

    @Test
    fun adoptsHandTypedRowsAndAssignsIds() = runTest {
        google.handEdit {
            appendRow("2026", "28.09.2026", "Anna", "Bread", "Food", "12,50")
            appendRow("2026", serial, "Anna", "Fuel", "Fuel", 200)
        }

        engine.sync()

        val entries = local().values.sortedBy { it.what }
        assertEquals(listOf("Bread" to 1250L, "Fuel" to 20000L), entries.map { it.what to it.amountMinor })
        val ids = google.rows("2026").drop(1).map { it[7] }
        assertEquals(entries.map { it.id }.toSet(), ids.toSet())
        assertEquals("28.09.2026", google.rows("2026")[1][0], "hand-typed cells other than ID stay as they were")
    }

    @Test
    fun fixesDuplicateIds() = runTest {
        google.handEdit {
            appendRow("2026", serial, "Anna", "Bread", "Food", 5, "PLN", "", "dup")
            appendRow("2026", serial, "Anna", "Bread", "Food", 5, "PLN", "", "dup")
        }

        engine.sync()

        val ids = google.rows("2026").drop(1).map { it[7] as String }
        assertEquals("dup", ids[0])
        assertTrue(ids[1] != "dup")
        assertEquals(2, local().size)
    }

    @Test
    fun adoptsRemoteEditsAndDeletes() = runTest {
        store.addEntry(entry("aaaa0001"))
        store.addEntry(entry("aaaa0002", what = "Eggs"))
        engine.sync()

        google.handEdit {
            setCell("2026", 2, 4, 99.99)
            deleteRow("2026", 3)
        }
        engine.sync()

        assertEquals(mapOf("aaaa0001" to 9999L), local().mapValues { it.value.amountMinor })
    }

    @Test
    fun keepsPendingLocalChangesOverRemote() = runTest {
        store.addEntry(entry("aaaa0001", currency = "USD"))
        engine.sync()
        google.handEdit { setCell("2026", 2, 2, "Changed remotely") }

        // A local change queued before the next sync (here: a rate that arrived) must survive the pull.
        store.setRate("aaaa0001", "3.9")
        engine.sync()

        assertEquals("3.9", local().getValue("aaaa0001").rate)
        assertEquals(3.9, google.rows("2026")[1][6])
    }

    @Test
    fun remoteDeleteWinsOverPendingChange() = runTest {
        store.addEntry(entry("aaaa0001", currency = "USD"))
        engine.sync()
        google.handEdit { deleteRow("2026", 2) }
        store.setRate("aaaa0001", "3.9")

        engine.sync()

        assertEquals(emptyMap(), local())
        assertEquals(1, google.rows("2026").size, "deleted row is not resurrected")
        assertEquals(0, store.outboxSize())
    }

    @Test
    fun fillsExchangeRatesForLocalAndHandTypedRows() = runTest {
        store.addEntry(entry("aaaa0001", currency = "EUR", amount = 2400))
        google.handEdit { appendRow("2026", serial, "Anna", "Museum", "Food", 10, "EUR", "", "bbbb0001") }

        engine.sync()

        val rows = google.rows("2026").drop(1).associateBy { it[7] }
        assertEquals(4.2715, rows.getValue("aaaa0001")[6])
        assertEquals(4.2715, rows.getValue("bbbb0001")[6])
        assertEquals("4.2715", local().getValue("bbbb0001").rate)
        assertEquals(setOf("EUR/PLN@2026-09-28"), rateRequests.toSet())
    }

    @Test
    fun flagsUnparseableRowsWithoutTouchingThem() = runTest {
        google.handEdit { appendRow("2026", serial, "Anna", "Weird", "Food", "twelve", "PLN", "", "cccc0001") }

        engine.sync()

        val invalid = local().getValue("cccc0001")
        assertEquals(EntryStatus.INVALID, invalid.status)
        assertEquals("Weird", invalid.what)
        assertEquals("twelve", google.rows("2026")[1][4])
        assertTrue(store.history.first().none { it.what == "Weird" }, "invalid rows don't feed suggestions")
    }

    @Test
    fun createsNewYearTab() = runTest {
        store.addEntry(entry("aaaa0001", at = "2027-01-01T10:00:00Z"))

        engine.sync()

        assertEquals("Date", google.rows("2027")[0][0])
        assertEquals("aaaa0001", google.rows("2027")[1][7])
        assertEquals(1, google.rows("2026").size)
    }

    @Test
    fun appendsAfterLastRowDespiteBlankRows() = runTest {
        google.handEdit {
            appendRow("2026", serial, "Anna", "A", "Food", 1, "PLN", "", "row2")
            appendRow("2026")
            appendRow("2026", serial, "Anna", "B", "Food", 2, "PLN", "", "row4")
        }
        engine.sync()
        store.addEntry(entry("aaaa0001"))

        engine.sync()

        assertEquals(listOf("row2", null, "row4", "aaaa0001"), google.rows("2026").drop(1).map { it.getOrNull(7) })
    }

    @Test
    fun skipsPullWhenNothingChanged() = runTest {
        store.addEntry(entry("aaaa0001"))
        engine.sync()
        engine.sync() // our own write bumped the version: one more pull
        val batchGets = google.requestCount("values:batchGet")

        assertIs<SyncOutcome.UpToDate>(engine.sync())

        assertEquals(batchGets, google.requestCount("values:batchGet"))
        assertNotNull(store.syncStatus.first().lastSuccess)
        assertNull(store.syncStatus.first().error)
    }

    @Test
    fun recordsFailures() = runTest {
        store.updateConfig(spreadsheetId = "missing")

        assertIs<SyncOutcome.Failed>(engine.sync())

        assertNotNull(store.syncStatus.first().error)
    }
}
