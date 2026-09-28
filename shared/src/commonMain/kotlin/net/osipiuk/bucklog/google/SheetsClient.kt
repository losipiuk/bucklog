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
import kotlinx.serialization.json.JsonObject

@Serializable
data class SpreadsheetProperties(val title: String, val timeZone: String? = null, val locale: String? = null)

@Serializable
data class GridProperties(val frozenRowCount: Int? = null)

@Serializable
data class SheetProperties(val sheetId: Int? = null, val title: String, val gridProperties: GridProperties? = null)

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

@Serializable
private data class ValuesBatchUpdate(val valueInputOption: String, val data: List<ValueRange>)

@Serializable
private data class BatchUpdate(val requests: List<JsonObject>)

@Serializable
private data class BatchUpdateResponse(val replies: List<JsonObject> = emptyList())

/** Thin wrapper over the Sheets v4 REST API. */
class SheetsClient(private val api: GoogleApi) {
    suspend fun create(spreadsheet: Spreadsheet): Spreadsheet =
        api.call {
            method = HttpMethod.Post
            url(BASE)
            contentType(ContentType.Application.Json)
            setBody(spreadsheet)
        }.body()

    /** Structural/format changes; returns one reply object per request (empty for most kinds). */
    suspend fun batchUpdate(spreadsheetId: String, requests: List<JsonObject>): List<JsonObject> =
        api.call {
            method = HttpMethod.Post
            url("$BASE/$spreadsheetId:batchUpdate")
            contentType(ContentType.Application.Json)
            setBody(BatchUpdate(requests))
        }.body<BatchUpdateResponse>().replies

    /** Writes values to several ranges. RAW stores values as given (no locale-dependent parsing). */
    suspend fun batchUpdateValues(spreadsheetId: String, data: List<ValueRange>, valueInputOption: String = "RAW") {
        api.call {
            method = HttpMethod.Post
            url("$BASE/$spreadsheetId/values:batchUpdate")
            contentType(ContentType.Application.Json)
            setBody(ValuesBatchUpdate(valueInputOption, data))
        }
    }

    suspend fun get(spreadsheetId: String): Spreadsheet =
        api.call {
            method = HttpMethod.Get
            url("$BASE/$spreadsheetId")
            parameter("fields", "spreadsheetId,properties(title,timeZone,locale),sheets.properties(sheetId,title)")
        }.body()

    suspend fun batchGet(spreadsheetId: String, ranges: List<String>): List<ValueRange> =
        api.call {
            method = HttpMethod.Get
            url("$BASE/$spreadsheetId/values:batchGet")
            ranges.forEach { parameter("ranges", it) }
            parameter("valueRenderOption", "UNFORMATTED_VALUE")
            parameter("dateTimeRenderOption", "SERIAL_NUMBER")
        }.body<BatchGetResponse>().valueRanges

    /**
     * Appends rows after the last row of the table found in [range]. RAW stores values as given;
     * USER_ENTERED parses them as if typed (locale-dependent).
     */
    suspend fun append(spreadsheetId: String, range: String, rows: List<List<JsonElement>>, valueInputOption: String = "RAW") {
        api.call {
            method = HttpMethod.Post
            url("$BASE/$spreadsheetId/values/${range.encodeURLPathPart()}:append")
            parameter("valueInputOption", valueInputOption)
            parameter("insertDataOption", "INSERT_ROWS")
            contentType(ContentType.Application.Json)
            setBody(WriteValues(rows))
        }
    }

    private companion object {
        const val BASE = "https://sheets.googleapis.com/v4/spreadsheets"
    }
}
