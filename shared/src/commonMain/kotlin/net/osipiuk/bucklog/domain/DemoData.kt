package net.osipiuk.bucklog.domain

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random
import kotlin.time.Instant

/** Realistic-looking fake expenses for trying the app out (debug builds only). */
object DemoData {
    private class Kind(val items: List<String>, val min: Int, val max: Int, val perWeek: Double)

    // Keyed by normalized default category names (PL and EN).
    private val kinds = mapOf(
        "spozywcze" to Kind(listOf("Biedronka", "Lidl", "Żabka", "Mleko", "Chleb", "Warzywa", "Owoce", "Mięso", "Ser", "Kawa ziarnista"), 8, 260, 7.0),
        "restauracje" to Kind(listOf("Pizza", "Sushi", "Kebab", "Kawiarnia", "Obiad", "Lody", "Burger"), 15, 220, 2.0),
        "transport" to Kind(listOf("Orlen", "Paliwo", "Parking", "Bilet MPK", "Myjnia", "Bolt"), 6, 330, 2.0),
        "dom" to Kind(listOf("Ikea", "Castorama", "Chemia", "Rossmann", "Żarówki", "Kwiaty doniczkowe"), 12, 400, 0.8),
        "rachunki" to Kind(listOf("Prąd", "Gaz", "Internet", "Telefon", "Netflix", "Spotify", "Czynsz"), 30, 900, 1.0),
        "zdrowie" to Kind(listOf("Apteka", "Witaminy", "Dentysta", "Leki"), 15, 350, 0.6),
        "dzieci" to Kind(listOf("Zabawki", "Przedszkole", "Książeczki", "Lego", "Basen dzieci"), 20, 450, 0.8),
        "ubrania" to Kind(listOf("Buty", "Zara", "Reserved", "Kurtka", "Skarpetki", "Sinsay"), 25, 480, 0.5),
        "rozrywka" to Kind(listOf("Kino", "Teatr", "Koncert", "Książka", "Gra planszowa"), 20, 240, 0.7),
        "podroze" to Kind(listOf("Hotel", "Booking", "Ryanair", "Pociąg PKP"), 90, 1400, 0.25),
        "prezenty" to Kind(listOf("Prezent urodzinowy", "Kwiaty", "Prezent dla mamy"), 30, 300, 0.3),
        "inne" to Kind(listOf("Poczta", "Ksero", "Klucze"), 5, 120, 0.4),
    )

    /** English default category names → the Polish keys above. */
    private val english = mapOf(
        "groceries" to "spozywcze", "eating out" to "restauracje", "home" to "dom", "bills" to "rachunki",
        "health" to "zdrowie", "kids" to "dzieci", "clothes" to "ubrania", "fun" to "rozrywka",
        "travel" to "podroze", "gifts" to "prezenty", "other" to "inne",
    )

    private val generic = Kind(listOf("Zakupy", "Różne", "Drobne"), 10, 200, 0.5)

    fun generate(
        categories: List<String>,
        people: List<String>,
        now: Instant,
        tz: TimeZone,
        days: Int = 180,
        random: Random = Random.Default,
    ): List<Entry> {
        if (categories.isEmpty() || people.isEmpty()) return emptyList()
        val today = now.toLocalDateTime(tz).date
        val byCategory = categories.associateWith { name ->
            val key = normalizeText(name)
            kinds[english[key] ?: key] ?: generic
        }
        val entries = mutableListOf<Entry>()
        for (daysAgo in 0 until days) {
            val date = today.minus(DatePeriod(days = daysAgo))
            for ((category, kind) in byCategory) {
                var expected = kind.perWeek / 7
                while (random.nextDouble() < expected) {
                    expected -= 1.0
                    val at = LocalDateTime(date, LocalTime(random.nextInt(7, 22), random.nextInt(60))).toInstant(tz)
                    if (at > now) continue
                    val foreign = category in byCategory.keys.take(3) && random.nextDouble() < 0.04
                    val refund = random.nextDouble() < 0.02
                    val whole = random.nextInt(kind.min, kind.max + 1)
                    val minor = (whole * 100L + random.nextInt(100)).let { if (foreign) it / 4 else it }
                    entries += Entry(
                        id = newEntryId(random),
                        timestamp = Instant.fromEpochSeconds(at.epochSeconds),
                        who = people[random.nextInt(people.size)],
                        what = kind.items[random.nextInt(kind.items.size)] + if (refund) " (zwrot)" else "",
                        category = category,
                        amountMinor = if (refund) -minor / 2 else minor,
                        currency = if (foreign) "EUR" else "PLN",
                        rate = null,
                        status = EntryStatus.PENDING,
                    )
                }
            }
        }
        return entries.sortedBy { it.timestamp }
    }
}
