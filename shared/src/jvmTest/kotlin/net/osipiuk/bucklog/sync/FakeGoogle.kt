package net.osipiuk.bucklog.sync

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.decodeURLPart
import io.ktor.http.headersOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import net.osipiuk.bucklog.google.AccessTokenProvider
import net.osipiuk.bucklog.google.DriveClient
import net.osipiuk.bucklog.google.GoogleApi
import net.osipiuk.bucklog.google.SheetsClient

/**
 * In-memory stand-in for the parts of the Sheets v4 and Drive v3 APIs the app uses.
 * Cells hold what was written (RAW) and are returned like UNFORMATTED_VALUE reads.
 * Every write bumps the Drive version, like the real thing.
 */
class FakeGoogle(val spreadsheetId: String = "sheet", val timeZone: String = "Europe/Warsaw") {
    class Tab(val sheetId: Int, val rows: MutableList<MutableList<JsonElement>> = mutableListOf())

    val tabs = linkedMapOf<String, Tab>()

    /** Other Drive files (backup folder and copies): id → file. The spreadsheet itself isn't listed. */
    class DriveEntry(val id: String, val name: String, val mimeType: String?, val parents: List<String>, val createdTime: String)

    val driveFiles = linkedMapOf<String, DriveEntry>()
    private var nextFileNumber = 1
    private var clockTick = 0
    var version = 1L
        private set
    val requests = mutableListOf<HttpRequestData>()

    /** Kinds of structural requests received, e.g. "repeatCell@100" (with sheetId) or "deleteDimension@100". */
    val structuralRequests = mutableListOf<String>()
    private var nextSheetId = 100

    private val api = GoogleApi(
        HttpClient(MockEngine { handle(it) }),
        object : AccessTokenProvider {
            override suspend fun accessToken() = "token"
            override suspend fun invalidate(token: String) = Unit
        },
    )
    val sheets = SheetsClient(api)
    val drive = DriveClient(api)

    fun addTab(title: String, vararg rows: List<Any?>) {
        tabs[title] = Tab(nextSheetId++, rows.map { r -> r.map(::cell).toMutableList() }.toMutableList())
    }

    /** Simulates someone editing the sheet by hand. */
    fun handEdit(block: FakeGoogle.() -> Unit) {
        block()
        version++
    }

    fun rows(tab: String): List<List<Any?>> = tabs.getValue(tab).rows.map { row -> row.map(::plain) }

    fun setCell(tab: String, row: Int, col: Int, value: Any?) {
        val rows = tabs.getValue(tab).rows
        while (rows.size < row) rows.add(mutableListOf())
        val cells = rows[row - 1]
        while (cells.size <= col) cells += JsonPrimitive("")
        cells[col] = cell(value)
    }

    fun deleteRow(tab: String, row: Int) {
        tabs.getValue(tab).rows.removeAt(row - 1)
    }

    fun appendRow(tab: String, vararg values: Any?) {
        tabs.getValue(tab).rows += values.map(::cell).toMutableList()
    }

    fun requestCount(pathSuffix: String) = requests.count { it.url.encodedPath.endsWith(pathSuffix) }

    // ---- HTTP emulation ----

    private val json = Json

    private fun MockRequestHandleScope.handle(request: HttpRequestData) = run {
        requests += request
        val path = request.url.encodedPath.decodeURLPart()
        val body = (request.body as? TextContent)?.text?.let { json.parseToJsonElement(it).jsonObject }
        val base = "/v4/spreadsheets/$spreadsheetId"
        drive(request, path, body)?.let { return@run respond(it.toString(), headers = headersOf(HttpHeaders.ContentType, "application/json")) }
        if (spreadsheetId !in path) {
            return@run respond(
                """{"error":{"code":404,"message":"Requested entity was not found.","status":"NOT_FOUND"}}""",
                HttpStatusCode.NotFound,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val response: JsonElement = when {
            path == "/drive/v3/files/$spreadsheetId" -> buildJsonObject {
                put("id", spreadsheetId); put("name", "Bucklog"); put("version", version.toString())
            }
            path == base && request.method == HttpMethod.Get -> spreadsheet()
            path == "$base/values:batchGet" -> buildJsonObject {
                putJsonArray("valueRanges") {
                    request.url.parameters.getAll("ranges").orEmpty().forEach { add(read(it)) }
                }
            }
            path == "$base/values:batchUpdate" -> {
                body!!["data"]!!.jsonArray.forEach { write(it.jsonObject["range"]!!.jsonPrimitive.content, it.jsonObject["values"]!!.jsonArray) }
                version++
                JsonObject(emptyMap())
            }
            path.startsWith("$base/values/") && path.endsWith(":append") -> {
                append(path.removePrefix("$base/values/").removeSuffix(":append"), body!!["values"]!!.jsonArray)
                version++
                JsonObject(emptyMap())
            }
            path == "$base:batchUpdate" -> {
                val replies = body!!["requests"]!!.jsonArray.map { structural(it.jsonObject) }
                version++
                buildJsonObject { put("replies", JsonArray(replies)) }
            }
            else -> error("FakeGoogle: unhandled ${request.method.value} $path")
        }
        respond(response.toString(), headers = headersOf(HttpHeaders.ContentType, "application/json"))
    }

    /** Drive v3 file operations other than the spreadsheet's own metadata; null if not one of them. */
    private fun drive(request: HttpRequestData, path: String, body: JsonObject?): JsonElement? {
        fun entry(f: DriveEntry) = buildJsonObject { put("id", f.id); put("name", f.name); put("createdTime", f.createdTime) }
        fun newFile(name: String, mime: String?, parents: List<String>): DriveEntry {
            val f = DriveEntry("file${nextFileNumber++}", name, mime, parents, "2026-01-01T00:00:${(clockTick++).toString().padStart(2, '0')}Z")
            driveFiles[f.id] = f
            return f
        }
        return when {
            path == "/drive/v3/files" && request.method == HttpMethod.Get -> {
                val q = request.url.parameters["q"].orEmpty()
                val parent = Regex("'([^']+)' in parents").find(q)?.groupValues?.get(1)
                val name = Regex("name = '([^']+)'").find(q)?.groupValues?.get(1)
                val matches = driveFiles.values.filter { (parent == null || parent in it.parents) && (name == null || it.name == name) }
                    .sortedByDescending { it.createdTime }
                buildJsonObject { put("files", JsonArray(matches.map(::entry))) }
            }
            path == "/drive/v3/files" && request.method == HttpMethod.Post ->
                entry(newFile(body!!["name"]!!.jsonPrimitive.content, body["mimeType"]?.jsonPrimitive?.content, emptyList()))
            path.startsWith("/drive/v3/files/") && path.endsWith("/copy") -> {
                val parents = body!!["parents"]!!.jsonArray.map { it.jsonPrimitive.content }
                entry(newFile(body["name"]!!.jsonPrimitive.content, null, parents))
            }
            path.startsWith("/drive/v3/files/") && request.method == HttpMethod.Delete -> {
                driveFiles.remove(path.removePrefix("/drive/v3/files/"))
                JsonObject(emptyMap())
            }
            path.startsWith("/drive/v3/files/") && path.removePrefix("/drive/v3/files/") in driveFiles ->
                entry(driveFiles.getValue(path.removePrefix("/drive/v3/files/")))
            else -> null
        }
    }

    private fun spreadsheet() = buildJsonObject {
        put("spreadsheetId", spreadsheetId)
        putJsonObject("properties") { put("title", "Bucklog"); put("timeZone", timeZone) }
        putJsonArray("sheets") {
            tabs.forEach { (title, tab) ->
                add(buildJsonObject { putJsonObject("properties") { put("sheetId", tab.sheetId); put("title", title) } })
            }
        }
    }

    private fun read(range: String): JsonObject {
        val r = A1.parse(range)
        val rows = tabs[r.tab]?.rows.orEmpty()
        val selected = (r.startRow - 1 until rows.size).map { i ->
            val cells = rows[i]
            val slice = (r.startCol..minOf(r.endCol, cells.size - 1)).map { cells[it] }
            slice.dropLastWhile { plain(it) == null }
        }.dropLastWhile { it.isEmpty() }
        return buildJsonObject {
            put("range", range)
            put("values", JsonArray(selected.map { JsonArray(it) }))
        }
    }

    private fun write(range: String, values: JsonArray) {
        val r = A1.parse(range)
        values.forEachIndexed { i, row ->
            row.jsonArray.forEachIndexed { j, value -> setCell(r.tab, r.startRow + i, r.startCol + j, value) }
        }
    }

    private fun append(range: String, values: JsonArray) {
        val r = A1.parse(range)
        val rows = tabs.getValue(r.tab).rows
        val lastNonEmpty = rows.indexOfLast { row -> row.any { plain(it) != null } } + 1
        // Like INSERT_ROWS: rows go after the table, at the range start if that's further down.
        var at = if (r.explicitStart) maxOf(r.startRow - 1, lastNonEmpty) else lastNonEmpty
        while (rows.size < at) rows.add(mutableListOf())
        for (row in values) rows.add(at++, row.jsonArray.toMutableList())
    }

    private fun structural(request: JsonObject): JsonElement {
        val kind = request.keys.single()
        val sheetId = (request[kind] as? JsonObject)?.let { it["range"] as? JsonObject }?.get("sheetId")?.jsonPrimitive?.content
        structuralRequests += if (sheetId != null) "$kind@$sheetId" else kind
        request["addSheet"]?.let { add ->
            val title = add.jsonObject["properties"]!!.jsonObject["title"]!!.jsonPrimitive.content
            val tab = Tab(nextSheetId++)
            tabs[title] = tab
            return buildJsonObject {
                putJsonObject("addSheet") { putJsonObject("properties") { put("sheetId", tab.sheetId); put("title", title) } }
            }
        }
        request["deleteDimension"]?.let { delete ->
            val range = delete.jsonObject["range"]!!.jsonObject
            val tab = tabs.values.single { it.sheetId == range["sheetId"]!!.jsonPrimitive.int }
            val start = range["startIndex"]!!.jsonPrimitive.int
            repeat(range["endIndex"]!!.jsonPrimitive.int - start) { tab.rows.removeAt(start) }
        }
        return JsonObject(emptyMap())
    }

    private data class A1(val tab: String, val startRow: Int, val startCol: Int, val endCol: Int, val explicitStart: Boolean) {
        companion object {
            private val pattern = Regex("""^'?(.*?)'?!([A-Z]+)(\d*)(?::([A-Z]+)(\d*))?$""")
            fun parse(range: String): A1 {
                val m = pattern.matchEntire(range) ?: error("Bad range $range")
                val (tab, c1, r1, c2) = m.destructured
                fun col(c: String) = c.fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1
                return A1(tab.replace("''", "'"), r1.toIntOrNull() ?: 1, col(c1), if (c2.isEmpty()) col(c1) else col(c2), r1.isNotEmpty())
            }
        }
    }

    companion object {
        fun cell(value: Any?): JsonElement = when (value) {
            null -> JsonPrimitive("")
            is JsonElement -> value
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            else -> JsonPrimitive(value.toString())
        }

        /** Cell → Kotlin value for assertions: null (empty), Double, Boolean or String. */
        fun plain(e: JsonElement): Any? {
            val p = e as? JsonPrimitive ?: return e.toString()
            return when {
                p.isString -> p.content.ifEmpty { null }
                p.content == "true" || p.content == "false" -> p.content.toBoolean()
                else -> p.content.toDouble()
            }
        }

        fun headerRow(vararg names: String): List<Any?> = names.toList()
    }
}
