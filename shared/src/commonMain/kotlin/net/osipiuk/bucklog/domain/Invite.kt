package net.osipiuk.bucklog.domain

import io.ktor.http.Parameters
import io.ktor.http.formUrlEncode
import io.ktor.http.parseQueryString

/** An invitation to join a shared sheet. */
data class Invite(val spreadsheetId: String, val sheetName: String, val from: String)

/**
 * Invite links: an https page (clickable in any messenger) whose fragment carries the details,
 * never sent to a server. The page's button hands them to the app as `net.osipiuk.bucklog://join?…`.
 */
object InviteLinks {
    private const val PAGE = "https://losipiuk.github.io/bucklog-picker/join.html"
    const val APP_SCHEME = "net.osipiuk.bucklog"
    const val APP_HOST = "join"

    /** Where anyone can download the latest APK (public GitHub releases). */
    const val DOWNLOAD = "https://github.com/losipiuk/bucklog/releases/latest"

    fun page(invite: Invite): String = "$PAGE#${params(invite)}"

    fun app(invite: Invite): String = "$APP_SCHEME://$APP_HOST?${params(invite)}"

    /** Parses the query or fragment of either link; null when the sheet is missing. */
    fun parse(query: String): Invite? {
        val p = parseQueryString(query.substringAfter('#').substringAfter('?'))
        val sheet = p["sheet"]?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{20,}")) } ?: return null
        return Invite(sheet, p["name"].orEmpty(), p["from"].orEmpty())
    }

    private fun params(invite: Invite) = Parameters.build {
        append("sheet", invite.spreadsheetId)
        append("name", invite.sheetName)
        append("from", invite.from)
    }.formUrlEncode()
}
