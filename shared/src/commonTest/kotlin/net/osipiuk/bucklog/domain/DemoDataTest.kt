package net.osipiuk.bucklog.domain

import kotlinx.datetime.TimeZone
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant

class DemoDataTest {
    @Test
    fun generatesPlausibleHistory() {
        val now = Instant.parse("2026-09-29T12:00:00Z")
        val categories = DefaultCategories.forLanguage("pl").map { it.name } + "Pies"
        val entries = DemoData.generate(categories, listOf("Łukasz", "Partner"), now, TimeZone.of("Europe/Warsaw"), random = Random(1))

        assertTrue(entries.size in 250..700, "got ${entries.size}")
        assertTrue(entries.all { it.timestamp <= now && it.category in categories })
        assertTrue(entries.map { it.id }.toSet().size == entries.size)
        assertTrue(entries.any { it.currency == "EUR" } && entries.any { it.isRefund })
        assertTrue(entries.any { it.category == "Pies" }, "unknown categories get generic items")
    }
}
