package net.osipiuk.bucklog.google

private val idInUrl = Regex("""/spreadsheets/(?:u/\d+/)?d/([a-zA-Z0-9_-]{20,})""")
private val bareId = Regex("""^[a-zA-Z0-9_-]{20,}$""")

/** Extracts the spreadsheet ID from a Google Sheets link (or returns a bare ID as is). */
fun parseSpreadsheetId(linkOrId: String): String? {
    val text = linkOrId.trim()
    return idInUrl.find(text)?.groupValues?.get(1) ?: text.takeIf { bareId.matches(it) }
}
