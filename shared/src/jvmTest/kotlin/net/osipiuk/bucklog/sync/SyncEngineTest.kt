package net.osipiuk.bucklog.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.osipiuk.bucklog.data.LocalStore
import net.osipiuk.bucklog.data.SyncErrorKind
import net.osipiuk.bucklog.db.BucklogDatabase
import net.osipiuk.bucklog.domain.Category
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
    fun reappliesColumnFormatsAfterAppending() = runTest {
        google.addTab("2025", header)
        val y2025 = google.tabs.getValue("2025").sheetId
        val y2026 = google.tabs.getValue("2026").sheetId
        store.addEntry(entry("aaaa0001"))

        engine.sync()

        // First sync formats every year tab, including ones it didn't write to.
        assertTrue("repeatCell@$y2025" in google.structuralRequests)
        assertTrue("repeatCell@$y2026" in google.structuralRequests)

        google.structuralRequests.clear()
        store.addEntry(entry("aaaa0002"))
        engine.sync()
        assertTrue("repeatCell@$y2026" in google.structuralRequests)
        assertTrue("repeatCell@$y2025" !in google.structuralRequests, "later syncs only format tabs they appended to")

        google.structuralRequests.clear()
        google.handEdit { setCell("2026", 2, 2, "Edited") }
        engine.sync()
        assertTrue(google.structuralRequests.none { it.startsWith("repeatCell") }, "pull-only syncs don't write formats")
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
    fun editsRowInPlace() = runTest {
        store.addEntry(entry("aaaa0001"))
        store.addEntry(entry("aaaa0002", what = "Eggs"))
        engine.sync()

        store.updateEntry(local().getValue("aaaa0001").copy(what = "Oat milk", amountMinor = 499))
        engine.sync()

        val rows = google.rows("2026").drop(1).associateBy { it[7] }
        assertEquals("Oat milk", rows.getValue("aaaa0001")[2])
        assertEquals(4.99, rows.getValue("aaaa0001")[4])
        assertEquals(2, rows.size)
        assertEquals(EntryStatus.SYNCED, local().getValue("aaaa0001").status)
    }

    @Test
    fun movesRowWhenDateChangesYear() = runTest {
        store.addEntry(entry("aaaa0001"))
        engine.sync()

        store.updateEntry(local().getValue("aaaa0001").copy(timestamp = Instant.parse("2025-12-31T12:00:00Z")))
        engine.sync()

        assertEquals(1, google.rows("2026").size)
        assertEquals("aaaa0001", google.rows("2025")[1][7])
        assertEquals(setOf("aaaa0001"), local().keys)
    }

    @Test
    fun deletesRowsAndSupportsUndo() = runTest {
        store.addEntry(entry("aaaa0001"))
        store.addEntry(entry("aaaa0002", what = "Eggs"))
        engine.sync()
        val eggs = local().getValue("aaaa0002")

        store.deleteEntry("aaaa0002")
        engine.sync()
        assertEquals(listOf("aaaa0001"), google.rows("2026").drop(1).map { it[7] })

        // Undo after the delete already reached the sheet: the row comes back.
        store.restoreEntry(eggs)
        engine.sync()
        assertEquals(setOf("aaaa0001", "aaaa0002"), google.rows("2026").drop(1).map { it[7] }.toSet())
        assertEquals(setOf("aaaa0001", "aaaa0002"), local().keys)
    }

    @Test
    fun undoBeforeSyncKeepsTheRow() = runTest {
        store.addEntry(entry("aaaa0001"))
        engine.sync()
        val milk = local().getValue("aaaa0001")

        store.deleteEntry("aaaa0001")
        store.restoreEntry(milk)
        engine.sync()

        assertEquals(listOf("aaaa0001"), google.rows("2026").drop(1).map { it[7] })
        assertEquals(setOf("aaaa0001"), local().keys)
    }

    @Test
    fun deletingAnUnsyncedEntryNeverTouchesTheSheet() = runTest {
        store.addEntry(entry("aaaa0001"))
        store.deleteEntry("aaaa0001")

        engine.sync()

        assertEquals(1, google.rows("2026").size)
        assertEquals(0, store.outboxSize())
    }

    @Test
    fun renamingCategoryRewritesRowsAndTheCategoriesTab() = runTest {
        store.addEntry(entry("aaaa0001"))
        engine.sync()
        google.handEdit { appendRow("Categories", "Pets", "🐶", false) } // added by hand meanwhile

        store.updateCategory("Food", Category("Groceries", "🛒"))
        engine.sync()

        assertEquals("Groceries", google.rows("2026")[1][3])
        assertEquals(
            listOf(listOf("Groceries", "🛒", false), listOf("Fuel", "⛽", false), listOf("Pets", "🐶", false)),
            google.rows("Categories").drop(1),
        )
        assertEquals(listOf("Groceries", "Fuel", "Pets"), store.categories.first().map { it.name })
        assertEquals("Groceries", local().getValue("aaaa0001").category)
    }

    @Test
    fun renamingAPersonRewritesTheirRowsOnly() = runTest {
        store.addEntry(entry("aaaa0001"))
        google.handEdit { appendRow("2026", serial, "Anna", "Bread", "Food", 5, "PLN", "", "bbbb0001") }
        engine.sync()

        assertEquals(1L, store.countEntriesBy("Łukasz"))
        store.renamePerson("Łukasz", "Lukasz")
        engine.sync()

        val who = google.rows("2026").drop(1).associate { it[7] to it[1] }
        assertEquals<Map<Any?, Any?>>(mapOf("aaaa0001" to "Lukasz", "bbbb0001" to "Anna"), who)
        assertEquals(0L, store.countEntriesBy("Łukasz"))
    }

    @Test
    fun archivingCategoryUpdatesItsRow() = runTest {
        engine.sync()

        store.updateCategory("Fuel", Category("Fuel", "⛽", archived = true))
        engine.sync()

        assertEquals(listOf("Fuel", "⛽", true), google.rows("Categories")[2])
        assertTrue(store.categories.first().single { it.name == "Fuel" }.archived)
    }

    // Someone else changes the sheet between this phone's pull and its writes (SPEC §6.5).

    @Test
    fun editsTheRightRowWhenARowAboveIsDeletedMidSync() = runTest {
        store.addEntry(entry("aaaa0001", what = "First"))
        store.addEntry(entry("aaaa0002", what = "Second", at = "2026-09-28T17:00:00Z"))
        engine.sync()
        store.updateEntry(local().getValue("aaaa0002").copy(what = "Second, edited"))
        google.afterNextBatchGet = { deleteRow("2026", 2) } // "First" disappears right after our pull

        engine.sync()

        assertEquals(listOf("aaaa0002" to "Second, edited"), google.rows("2026").drop(1).map { it[7] to it[2] })
        engine.sync()
        assertEquals(setOf("aaaa0002"), local().keys, "the other person's delete reaches this phone too")
    }

    @Test
    fun deletesTheRightRowWhenTheSheetIsSortedMidSync() = runTest {
        store.addEntry(entry("aaaa0001", what = "A"))
        store.addEntry(entry("aaaa0002", what = "B"))
        store.addEntry(entry("aaaa0003", what = "C"))
        engine.sync()
        store.deleteEntry("aaaa0002")
        google.afterNextBatchGet = { tabs.getValue("2026").rows.subList(1, 4).reverse() } // someone sorts Z→A

        engine.sync()

        assertEquals(listOf("C", "A"), google.rows("2026").drop(1).map { it[2] })
    }

    @Test
    fun anEditLosesToADeleteMadeMidSync() = runTest {
        store.addEntry(entry("aaaa0001"))
        engine.sync()
        store.updateEntry(local().getValue("aaaa0001").copy(what = "Edited"))
        google.afterNextBatchGet = { deleteRow("2026", 2) }

        engine.sync()
        assertEquals(1, google.rows("2026").size, "the edit doesn't resurrect or overwrite anything")
        engine.sync()

        assertEquals(emptyMap(), local())
        assertEquals(0, store.outboxSize())
    }

    @Test
    fun doesNotStampAnIdOnARowThatMovedMidSync() = runTest {
        store.addEntry(entry("aaaa0001", what = "Mine"))
        engine.sync()
        google.handEdit { appendRow("2026", serial, "Anna", "Typed by hand", "Food", 5) } // row 3, no ID yet
        google.afterNextBatchGet = { tabs.getValue("2026").rows.subList(1, 3).reverse() } // sorted: hand row now row 2

        engine.sync()

        assertEquals("aaaa0001", google.rows("2026").single { it[2] == "Mine" }[7], "an existing ID is never overwritten")
        engine.sync()
        val ids = google.rows("2026").drop(1).map { it[7] }
        assertEquals(2, ids.toSet().size, "the hand-typed row gets its own ID on the next sync")
    }

    @Test
    fun recordsFailures() = runTest {
        store.updateConfig(spreadsheetId = "missing")

        assertIs<SyncOutcome.Failed>(engine.sync())

        assertNotNull(store.syncStatus.first().error)
        assertEquals(SyncErrorKind.ACCESS, store.syncStatus.first().errorKind, "missing sheet → pick it again")
    }
}
