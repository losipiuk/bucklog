package net.osipiuk.bucklog.data

import kotlin.time.Instant

/** One queued local change. */
data class OutboxOp(val seq: Long, val kind: Kind, val entryId: String) {
    enum class Kind { UPSERT, DELETE }
}

data class SyncStatus(
    val lastSuccess: Instant?,
    /** Message of the last failed sync; cleared by the next successful one. */
    val error: String?,
    val pendingChanges: Long,
) {
    companion object {
        const val LAST_SUCCESS = "sync_last_success"
        const val ERROR = "sync_error"
        const val DRIVE_VERSION = "sync_drive_version"

        /** Set once year-tab column formats were (re)applied to every tab of this spreadsheet. */
        const val FORMATS_APPLIED = "sync_formats_applied"
    }
}
