package net.osipiuk.bucklog.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.osipiuk.bucklog.data.LocalStore
import net.osipiuk.bucklog.db.BucklogDatabase
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class BackupsTest {
    private val google = FakeGoogle()
    private val store = LocalStore(
        BucklogDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { BucklogDatabase.Schema.create(it) }),
        Dispatchers.Unconfined,
    )
    private var now = Instant.parse("2026-09-29T10:00:00Z")
    private val backups = Backups(store, google.drive, clock = object : Clock { override fun now() = now }, keep = 3)

    @BeforeTest
    fun setUp() = runTest {
        google.addTab("2026", listOf("Date"))
        store.updateConfig(spreadsheetId = google.spreadsheetId)
    }

    private fun copies() = google.driveFiles.values.filter { it.mimeType == null }.map { it.name }

    @Test
    fun copiesIntoOneFolderAndKeepsTheNewest() = runTest {
        repeat(5) { backups.backUpNow() }

        val folders = google.driveFiles.values.filter { it.mimeType == "application/vnd.google-apps.folder" }
        assertEquals(listOf("Bucklog backups"), folders.map { it.name })
        assertEquals(3, copies().size, "older copies beyond `keep` are deleted")
        assertTrue(copies().all { it == "Bucklog backup 2026-09-29" })
        assertTrue(google.driveFiles.values.filter { it.mimeType == null }.all { folders.single().id in it.parents })
        assertNotNull(backups.status.first().lastBackup)
    }

    @Test
    fun backsUpOnlyWhenEnabledDueAndChanged() = runTest {
        assertFalse(backups.backUpIfDue(), "off by default")

        backups.setEnabled(true)
        assertTrue(backups.backUpIfDue())
        assertFalse(backups.backUpIfDue(), "not due again within a week")

        now += 8.days
        assertFalse(backups.backUpIfDue(), "due, but nothing changed")

        google.handEdit { setCell("2026", 2, 2, "changed") }
        assertTrue(backups.backUpIfDue())
        assertEquals(2, copies().size)
    }

    @Test
    fun recreatesAFolderThatWasDeleted() = runTest {
        backups.backUpNow()
        google.driveFiles.clear()

        backups.backUpNow()

        assertEquals(1, copies().size)
    }
}
