package net.osipiuk.bucklog.sync

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.osipiuk.bucklog.data.LocalStore

/** Exchange rate lookup: how much 1 [from] is worth in [to] on [date], as a decimal string. */
fun interface ExchangeRates {
    suspend fun rate(from: String, to: String, date: LocalDate): String?
}

/**
 * Public rate sources (SPEC §6.7): NBP table A (then B) mid rates when converting to PLN,
 * ECB rates via Frankfurter otherwise. Uses the latest rate published on or before [date].
 * Results are cached in the local DB; failures return null (retried on a later sync).
 */
class PublicExchangeRates(private val http: HttpClient, private val store: LocalStore) : ExchangeRates {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun rate(from: String, to: String, date: LocalDate): String? {
        if (from == to) return null
        val key = "$from/$to"
        store.cachedRate(key, date.toString())?.let { return it }
        val rate = try {
            if (to == "PLN") nbp(from, date) else frankfurter(from, to, date)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return null
        store.cacheRate(key, date.toString(), rate)
        return rate
    }

    private suspend fun nbp(code: String, date: LocalDate): String? {
        // A two-week window always contains the last business day before holidays.
        val start = date.minus(DatePeriod(days = 14))
        for (table in listOf("a", "b")) {
            val response = http.get("https://api.nbp.pl/api/exchangerates/rates/$table/$code/$start/$date/?format=json")
            if (response.status == HttpStatusCode.NotFound) continue
            if (!response.status.isSuccess()) return null
            val rates = json.parseToJsonElement(response.bodyAsText()).jsonObject["rates"]?.jsonArray ?: return null
            return rates.lastOrNull()?.jsonObject?.get("mid")?.jsonPrimitive?.content
        }
        return null
    }

    private suspend fun frankfurter(from: String, to: String, date: LocalDate): String? {
        val response = http.get("https://api.frankfurter.app/$date?from=$from&to=$to")
        if (!response.status.isSuccess()) return null
        return json.parseToJsonElement(response.bodyAsText()).jsonObject["rates"]?.jsonObject?.get(to)?.jsonPrimitive?.content
    }
}
