package net.osipiuk.bucklog.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.osipiuk.bucklog.db.BucklogDatabase
import net.osipiuk.bucklog.domain.Category
import net.osipiuk.bucklog.google.AccessTokenProvider
import net.osipiuk.bucklog.google.GoogleApi
import net.osipiuk.bucklog.google.SheetsClient
import net.osipiuk.bucklog.sheet.CategoryUploader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CategoryUploaderTest {
    private val store = LocalStore(
        BucklogDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { BucklogDatabase.Schema.create(it) }),
        Dispatchers.Unconfined,
    )
    private val requests = mutableListOf<HttpRequestData>()
    private var failing = false

    private val uploader = CategoryUploader(
        store,
        SheetsClient(
            GoogleApi(
                HttpClient(
                    MockEngine { r ->
                        requests += r
                        val json = headersOf(HttpHeaders.ContentType, "application/json")
                        if (failing) respond("""{"error":{"code":503,"message":"down"}}""", HttpStatusCode.ServiceUnavailable, json)
                        else respond("{}", headers = json)
                    },
                ),
                object : AccessTokenProvider {
                    override suspend fun accessToken() = "t"
                    override suspend fun invalidate(token: String) = Unit
                },
            ),
        ),
    )

    @Test
    fun appendsQueuedCategoriesAndClearsQueue() = runTest {
        store.replaceCategories(listOf(Category("Food", "🍎")))
        store.addCategory(Category("Pies", null))

        assertTrue(uploader.upload("abc"))

        assertEquals(listOf("Food", "Pies"), store.categories.first().map { it.name })
        assertEquals(0, store.outboxSize())
        val request = requests.single()
        assertEquals("/v4/spreadsheets/abc/values/'Categories'!A:C:append", request.url.encodedPath)
        assertEquals("""{"values":[["Pies","",false]]}""", (request.body as TextContent).text)
    }

    @Test
    fun keepsQueueOnFailure() = runTest {
        store.addCategory(Category("Pies", null))
        failing = true

        assertFalse(uploader.upload("abc"))

        assertEquals(1, store.pendingCategoryAdds().size)
    }
}
