package net.osipiuk.bucklog.google

import io.ktor.client.call.body
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class SpreadsheetProperties(val title: String, val timeZone: String? = null)

@Serializable
data class SheetProperties(val sheetId: Int? = null, val title: String)

@Serializable
data class Sheet(val properties: SheetProperties)

@Serializable
data class Spreadsheet(
    val spreadsheetId: String? = null,
    val properties: SpreadsheetProperties,
    val sheets: List<Sheet> = emptyList(),
)

/** Cell values as returned with UNFORMATTED_VALUE: numbers, strings or booleans. */
@Serializable
data class ValueRange(
    val range: String? = null,
    val majorDimension: String? = null,
    val values: List<List<JsonElement>> = emptyList(),
)

@Serializable
private data class BatchGetResponse(val valueRanges: List<ValueRange> = emptyList())

@Serializable
private data class WriteValues(val values: List<List<JsonElement>>)

/** Thin wrapper over the Sheets v4 REST API. */
class SheetsClient(private val api: GoogleApi) {
    suspend fun create(title: String, sheetTitles: List<String>): Spreadsheet =
        api.call {
            method = HttpMethod.Post
            url(BASE)
            contentType(ContentType.Application.Json)
            setBody(
                Spreadsheet(
                    properties = SpreadsheetProperties(title),
                    sheets = sheetTitles.map { Sheet(SheetProperties(title = it)) },
                ),
            )
        }.body()

    suspend fun get(spreadsheetId: String): Spreadsheet =
        api.call {
            method = HttpMethod.Get
            url("$BASE/$spreadsheetId")
            parameter("fields", "spreadsheetId,properties(title,timeZone),sheets.properties(sheetId,title)")
        }.body()

    suspend fun batchGet(spreadsheetId: String, ranges: List<String>): List<ValueRange> =
        api.call {
            method = HttpMethod.Get
            url("$BASE/$spreadsheetId/values:batchGet")
            ranges.forEach { parameter("ranges", it) }
            parameter("valueRenderOption", "UNFORMATTED_VALUE")
            parameter("dateTimeRenderOption", "SERIAL_NUMBER")
        }.body<BatchGetResponse>().valueRanges

    /** Appends rows after the last row of the table found in [range]. Values are parsed as if typed by a user. */
    suspend fun append(spreadsheetId: String, range: String, rows: List<List<JsonElement>>) {
        api.call {
            method = HttpMethod.Post
            url("$BASE/$spreadsheetId/values/${range.encodeURLPathPart()}:append")
            parameter("valueInputOption", "USER_ENTERED")
            parameter("insertDataOption", "INSERT_ROWS")
            contentType(ContentType.Application.Json)
            setBody(WriteValues(rows))
        }
    }

    private companion object {
        const val BASE = "https://sheets.googleapis.com/v4/spreadsheets"
    }
}
