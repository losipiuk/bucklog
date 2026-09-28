package net.osipiuk.bucklog.sheet

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.osipiuk.bucklog.domain.Category
import net.osipiuk.bucklog.google.AccessTokenProvider
import net.osipiuk.bucklog.google.GoogleApi
import net.osipiuk.bucklog.google.SheetsClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SheetSetupTest {
    private val requests = mutableListOf<HttpRequestData>()
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun setup(respond: (HttpRequestData) -> String) = SheetSetup(
        SheetsClient(
            GoogleApi(
                HttpClient(MockEngine { r -> requests += r; respond(respond(r), headers = json) }),
                object : AccessTokenProvider {
                    override suspend fun accessToken() = "t"
                    override suspend fun invalidate(token: String) = Unit
                },
            ),
        ),
    )

    private fun HttpRequestData.bodyText() = (body as TextContent).text
    private fun HttpRequestData.bodyJson() = kotlinx.serialization.json.Json.parseToJsonElement(bodyText()).jsonObject

    @Test
    fun createsSheetWithTabsHeadersAndFormats() = runTest {
        val setup = setup { r ->
            if (r.url.encodedPath == "/v4/spreadsheets") {
                """{"spreadsheetId":"new","properties":{"title":"Bucklog"},"sheets":[
                   {"properties":{"sheetId":1,"title":"Categories"}},{"properties":{"sheetId":2,"title":"Settings"}},
                   {"properties":{"sheetId":3,"title":"2026"}}]}"""
            } else {
                "{}"
            }
        }

        setup.createFamilySheet("Bucklog", "PLN", "Europe/Warsaw", "pl_PL", listOf(Category("Spożywcze", "🛒")), 2026)

        val (create, values, formats) = requests
        val createBody = create.bodyJson()
        assertEquals("Europe/Warsaw", createBody["properties"]!!.jsonObject["timeZone"]!!.jsonPrimitive.content)
        assertEquals(
            listOf("Categories", "Settings", "2026"),
            createBody["sheets"]!!.jsonArray.map { it.jsonObject["properties"]!!.jsonObject["title"]!!.jsonPrimitive.content },
        )
        assertEquals("/v4/spreadsheets/new/values:batchUpdate", values.url.encodedPath)
        val valuesBody = values.bodyText()
        assertTrue("\"RAW\"" in valuesBody)
        assertTrue("""["Spożywcze","🛒",false]""" in valuesBody)
        assertTrue("""["main_currency","PLN"]""" in valuesBody)
        assertTrue("""["Date","Who","What","Category","Amount","Currency","Rate","ID"]""" in valuesBody)
        assertEquals("/v4/spreadsheets/new:batchUpdate", formats.url.encodedPath)
        assertTrue("ONE_OF_RANGE" in formats.bodyText())
        assertTrue("'Categories'!\$A\$2:\$A" in formats.bodyText())
    }

    @Test
    fun retriesWithoutLocaleWhenSheetsRejectsIt() = runTest {
        val failing = SheetSetup(
            SheetsClient(
                GoogleApi(
                    HttpClient(
                        MockEngine { r ->
                            requests += r
                            // Rejects any create request that sets a locale, like Sheets does for "en_PL_u_…".
                            if (r.url.encodedPath == "/v4/spreadsheets" && "\"locale\"" in r.bodyText()) {
                                respond(
                                    """{"error":{"code":400,"message":"Unsupported locale","status":"INVALID_ARGUMENT"}}""",
                                    io.ktor.http.HttpStatusCode.BadRequest,
                                    json,
                                )
                            } else if (r.url.encodedPath == "/v4/spreadsheets") {
                                respond("""{"spreadsheetId":"new","properties":{"title":"Bucklog"},"sheets":[]}""", headers = json)
                            } else {
                                respond("{}", headers = json)
                            }
                        },
                    ),
                    object : AccessTokenProvider {
                        override suspend fun accessToken() = "t"
                        override suspend fun invalidate(token: String) = Unit
                    },
                ),
            ),
        )

        failing.createFamilySheet("Bucklog", "PLN", "Europe/Warsaw", "en_PL", emptyList(), 2026)

        val creates = requests.filter { it.url.encodedPath == "/v4/spreadsheets" }
        assertEquals(2, creates.size)
        assertTrue("locale" !in creates[1].bodyText())
    }

    @Test
    fun ensureLayoutAddsOnlyMissingTabs() = runTest {
        val setup = setup { r ->
            when {
                r.url.encodedPath == "/v4/spreadsheets/abc" ->
                    """{"properties":{"title":"Family"},"sheets":[{"properties":{"sheetId":0,"title":"Categories"}}]}"""
                r.url.encodedPath == "/v4/spreadsheets/abc:batchUpdate" && "addSheet" in r.bodyText() ->
                    """{"replies":[{"addSheet":{"properties":{"sheetId":7,"title":"Settings"}}},
                                   {"addSheet":{"properties":{"sheetId":8,"title":"2026"}}}]}"""
                else -> "{}"
            }
        }

        val added = setup.ensureLayout("abc", "PLN", listOf(Category("Food", null)), 2026)

        assertEquals(listOf("Settings", "2026"), added)
        val valuesBody = requests.single { it.url.encodedPath.endsWith("values:batchUpdate") }.bodyText()
        assertTrue("Food" !in valuesBody, "existing Categories tab must not be rewritten")
    }

    @Test
    fun parsesCategoriesLeniently() {
        val rows = listOf(
            listOf(JsonPrimitive("Food"), JsonPrimitive("🍎")),
            listOf(JsonPrimitive("  ")),
            listOf(JsonPrimitive("food"), JsonPrimitive("dup")),
            listOf(JsonPrimitive("Kids"), JsonPrimitive(""), JsonPrimitive(true)),
            listOf(JsonPrimitive("Old"), JsonPrimitive(""), JsonPrimitive("TRUE")),
        )
        assertEquals(
            listOf(Category("Food", "🍎"), Category("Kids", null, archived = true), Category("Old", null, archived = true)),
            SheetSetup.parseCategories(rows),
        )
    }
}
