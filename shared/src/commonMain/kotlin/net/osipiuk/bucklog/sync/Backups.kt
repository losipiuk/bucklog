package net.osipiuk.bucklog.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import net.osipiuk.bucklog.data.LocalStore
import net.osipiuk.bucklog.google.DriveClient
import net.osipiuk.bucklog.google.GoogleApiException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

data class BackupStatus(
    val enabled: Boolean,
    val lastBackup: Instant?,
    val folderId: String?,
    val error: String?,
)

/**
 * Periodic copies of the whole family spreadsheet into a "Bucklog backups" folder in the
 * user's Drive (SPEC §6.8). Only files this app created are ever deleted: old backups beyond [keep].
 */
class Backups(
    private val store: LocalStore,
    private val drive: DriveClient,
    private val clock: Clock = Clock.System,
    private val every: Duration = 7.days,
    private val keep: Int = 8,
) {
    val status: Flow<BackupStatus> = combine(
        store.valueFlow(ENABLED), store.valueFlow(LAST_AT), store.valueFlow(FOLDER_ID), store.valueFlow(ERROR),
    ) { enabled, lastAt, folder, error ->
        BackupStatus(enabled == "1", lastAt?.toLongOrNull()?.let(Instant::fromEpochMilliseconds), folder, error)
    }

    suspend fun setEnabled(enabled: Boolean) = store.putValue(ENABLED, if (enabled) "1" else "0")

    /** Backs up when enabled, the last backup is older than [every], and the sheet changed since. */
    suspend fun backUpIfDue(): Boolean {
        if (store.value(ENABLED) != "1") return false
        val last = store.value(LAST_AT)?.toLongOrNull()?.let(Instant::fromEpochMilliseconds)
        if (last != null && clock.now() - last < every) return false
        val spreadsheetId = store.config.first().spreadsheetId ?: return false
        val version = drive.getFile(spreadsheetId).version
        if (version != null && version == store.value(LAST_VERSION)) return false
        backUpNow()
        return true
    }

    /** Copies the spreadsheet now and prunes old copies. Records (and rethrows) failures. */
    suspend fun backUpNow() {
        val config = store.config.first()
        val spreadsheetId = config.spreadsheetId ?: return
        try {
            val source = drive.getFile(spreadsheetId)
            val folder = folderId()
            val date = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            drive.copy(spreadsheetId, "${source.name} backup $date", folder)
            drive.find("'$folder' in parents and trashed = false").drop(keep).forEach { drive.delete(it.id) }
            store.putValue(LAST_AT, clock.now().toEpochMilliseconds().toString())
            store.putValue(LAST_VERSION, source.version)
            store.putValue(ERROR, null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            store.putValue(ERROR, e.message ?: e::class.simpleName)
            throw e
        }
    }

    /** The app's backup folder: remembered, found again, or created. */
    private suspend fun folderId(): String {
        store.value(FOLDER_ID)?.let { id ->
            val exists = try {
                drive.getFile(id)
                true
            } catch (e: GoogleApiException) {
                if (e.httpStatus != 404) throw e
                false
            }
            if (exists) return id
        }
        val folder = drive.find("name = '$FOLDER_NAME' and mimeType = '${DriveClient.FOLDER}' and trashed = false").firstOrNull()
            ?: drive.createFolder(FOLDER_NAME)
        store.putValue(FOLDER_ID, folder.id)
        return folder.id
    }

    companion object {
        const val FOLDER_NAME = "Bucklog backups"
        const val ENABLED = "backup_enabled"
        private const val LAST_AT = "backup_last_at"
        private const val LAST_VERSION = "backup_last_version"
        private const val FOLDER_ID = "backup_folder_id"
        private const val ERROR = "backup_error"
    }
}
