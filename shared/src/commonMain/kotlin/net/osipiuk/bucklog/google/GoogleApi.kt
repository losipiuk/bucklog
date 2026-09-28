package net.osipiuk.bucklog.google

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal val googleJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/** Authenticated HTTP access to Google REST APIs, with one retry on an expired token. */
class GoogleApi(
    httpClient: HttpClient,
    private val tokens: AccessTokenProvider,
) {
    internal val http = httpClient.config {
        install(ContentNegotiation) { json(googleJson) }
        expectSuccess = false
    }

    internal suspend fun call(block: HttpRequestBuilder.() -> Unit): HttpResponse {
        var token = tokens.accessToken()
        var response = http.request { bearerAuth(token); block() }
        if (response.status == HttpStatusCode.Unauthorized) {
            tokens.invalidate(token)
            token = tokens.accessToken()
            response = http.request { bearerAuth(token); block() }
        }
        if (!response.status.isSuccess()) throw response.toException()
        return response
    }

    private suspend fun HttpResponse.toException(): GoogleApiException {
        val text = bodyAsText()
        val error = runCatching { googleJson.parseToJsonElement(text).jsonObject["error"]!!.jsonObject }.getOrNull()
        return GoogleApiException(
            httpStatus = status.value,
            status = error?.get("status")?.jsonPrimitive?.content,
            message = error?.get("message")?.jsonPrimitive?.content ?: text.take(500),
        )
    }
}
