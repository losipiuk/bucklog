package net.osipiuk.bucklog.google

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SheetsClientTest {
    private class FakeTokens : AccessTokenProvider {
        var current = "t1"
        val invalidated = mutableListOf<String>()
        override suspend fun accessToken() = current
        override suspend fun invalidate(token: String) {
            invalidated += token
            current = "t2"
        }
    }

    private val tokens = FakeTokens()
    private val requests = mutableListOf<HttpRequestData>()

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): SheetsClient {
        val engine = MockEngine { request ->
            requests += request
            handler(request)
        }
        return SheetsClient(GoogleApi(HttpClient(engine), tokens))
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    @Test
    fun batchGetParsesMixedCellValues() = runTest {
        val sheets = client {
            json(
                """{"spreadsheetId":"abc","valueRanges":[{"range":"'2026'!A1:H2","majorDimension":"ROWS",
                   "values":[["Date","Who"],[46293.78,"Łukasz",35.3,true]]}]}""",
            )
        }

        val ranges = sheets.batchGet("abc", listOf("2026!A:H", "Categories!A:C"))

        val row = ranges.single().values[1]
        assertEquals(46293.78, (row[0] as JsonPrimitive).content.toDouble())
        assertEquals("Łukasz", (row[1] as JsonPrimitive).content)
        val url = requests.single().url
        assertEquals("/v4/spreadsheets/abc/values:batchGet", url.encodedPath)
        assertEquals(listOf("2026!A:H", "Categories!A:C"), url.parameters.getAll("ranges"))
        assertEquals("UNFORMATTED_VALUE", url.parameters["valueRenderOption"])
        assertEquals("Bearer t1", requests.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun appendSendsRowsToEncodedRange() = runTest {
        val sheets = client { json("{}") }

        sheets.append("abc", "2026!A:H", listOf(listOf(JsonPrimitive("2026-09-28 18:42"), JsonPrimitive(35.3))))

        val request = requests.single()
        assertEquals("/v4/spreadsheets/abc/values/2026!A:H:append", request.url.encodedPath)
        assertEquals("RAW", request.url.parameters["valueInputOption"])
        assertEquals("""{"values":[["2026-09-28 18:42",35.3]]}""", (request.body as TextContent).text)
    }

    @Test
    fun retriesOnceWithFreshTokenAfter401() = runTest {
        val sheets = client { request ->
            if (request.headers[HttpHeaders.Authorization] == "Bearer t1") {
                json("""{"error":{"code":401,"message":"expired","status":"UNAUTHENTICATED"}}""", HttpStatusCode.Unauthorized)
            } else {
                json("""{"properties":{"title":"Bucklog"}}""")
            }
        }

        assertEquals("Bucklog", sheets.get("abc").properties.title)
        assertEquals(listOf("t1"), tokens.invalidated)
        assertEquals(2, requests.size)
    }

    @Test
    fun surfacesGoogleErrorDetails() = runTest {
        val sheets = client {
            json("""{"error":{"code":404,"message":"Requested entity was not found.","status":"NOT_FOUND"}}""", HttpStatusCode.NotFound)
        }

        val e = assertFailsWith<GoogleApiException> { sheets.get("missing") }
        assertEquals(404, e.httpStatus)
        assertEquals("NOT_FOUND", e.status)
    }
}
