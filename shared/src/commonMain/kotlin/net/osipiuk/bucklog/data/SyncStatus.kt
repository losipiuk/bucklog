package net.osipiuk.bucklog.data

import kotlin.time.Instant

/** One queued local change. */
data class OutboxOp(val seq: Long, val kind: Kind, val entryId: String) {
    enum class Kind { UPSERT, DELETE }
}

enum class SyncErrorKind {
    /** Google authorization expired or was revoked: the user has to sign in again. */
    AUTH,

    /** The sheet is gone or no longer shared with this account: pick it again. */
    ACCESS,

    /** No network; sync resumes by itself. */
    OFFLINE,
    OTHER,
}

data class SyncStatus(
    val lastSuccess: Instant?,
    /** Message of the last failed sync; cleared by the next successful one. */
    val error: String?,
    val errorKind: SyncErrorKind?,
    val pendingChanges: Long,
) {
    companion object {
        const val LAST_SUCCESS = "sync_last_success"
        const val ERROR = "sync_error"
        const val ERROR_KIND = "sync_error_kind"
        const val DRIVE_VERSION = "sync_drive_version"

        /** Set once year-tab column formats were (re)applied to every tab of this spreadsheet. */
        const val FORMATS_APPLIED = "sync_formats_applied"
    }
}
