package net.osipiuk.bucklog.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class HistoryTest {
    private val warsaw = TimeZone.of("Europe/Warsaw")
    private var n = 0
    private fun e(at: String, amount: Long, category: String = "Food", currency: String = "PLN", rate: String? = null, what: String = "x") =
        Entry("id${n++}", Instant.parse(at), "Anna", what, category, amount, currency, rate, EntryStatus.SYNCED)

    @Test
    fun convertsWithStoredRate() {
        assertEquals(10252, History.inMainCurrency(e("2026-09-28T10:00:00Z", 2400, currency = "EUR", rate = "4.2715"), "PLN"))
        assertEquals(2700, History.inMainCurrency(e("2026-09-28T10:00:00Z", 1000, currency = "JPY", rate = "0.027"), "PLN"))
        assertNull(History.inMainCurrency(e("2026-09-28T10:00:00Z", 100, currency = "EUR"), "PLN"))
    }

    @Test
    fun groupsByLocalDayWithTotals() {
        val groups = History.group(
            listOf(
                e("2026-09-28T21:30:00Z", 1000), // 23:30 in Warsaw: still the 28th
                e("2026-09-28T22:30:00Z", 500), // 00:30 on the 29th
                e("2026-09-28T08:00:00Z", -300, category = "Clothes"),
            ),
            Grouping.DAY, warsaw, "PLN",
        )
        assertEquals(listOf(LocalDate(2026, 9, 29), LocalDate(2026, 9, 28)), groups.map { it.start })
        assertEquals(listOf(500L, 700L), groups.map { it.total })
    }

    @Test
    fun groupsByMonthWithCategoryBreakdown() {
        val groups = History.group(
            listOf(
                e("2026-09-01T10:00:00Z", 1000, "Food"),
                e("2026-09-15T10:00:00Z", 3000, "Fuel"),
                e("2026-09-20T10:00:00Z", 500, "Food"),
                e("2026-09-21T10:00:00Z", 100, "Food", currency = "EUR"),
                e("2026-08-31T10:00:00Z", 999, "Food"),
            ),
            Grouping.MONTH, warsaw, "PLN",
        )
        val september = groups.first()
        assertEquals(LocalDate(2026, 9, 1), september.start)
        assertEquals(4, september.entries.size)
        assertEquals(4500, september.total)
        assertTrue(september.approximate)
        assertEquals(listOf("Fuel" to 3000L, "Food" to 1500L), september.byCategory)
        assertFalse(groups[1].approximate)
    }

    @Test
    fun filtersIgnoringCaseAndDiacritics() {
        val entries = listOf(e("2026-09-01T10:00:00Z", 1, what = "Żabka"), e("2026-09-01T10:00:00Z", 1, category = "Paliwo"))
        assertEquals(1, History.filter(entries, "zab").size)
        assertEquals(1, History.filter(entries, "", category = "Paliwo").size)
        assertEquals(1, History.filter(entries, "anna", category = "Paliwo").size)
    }
}
