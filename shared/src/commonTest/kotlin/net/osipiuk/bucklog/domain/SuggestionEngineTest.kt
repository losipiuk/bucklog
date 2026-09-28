package net.osipiuk.bucklog.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class SuggestionEngineTest {
    private val now = Instant.parse("2026-09-28T12:00:00Z")
    private fun item(what: String, category: String, who: String = "Łukasz", daysAgo: Int = 1) =
        HistoryItem(what, category, who, now - daysAgo.days)

    @Test
    fun suggestsByPrefixOfTextOrWord() {
        val engine = SuggestionEngine(
            listOf(item("Milk", "Groceries"), item("Almond milk", "Groceries"), item("Mint tea", "Eating out"), item("Fuel", "Transport")),
            me = "Łukasz", now = now,
        )
        assertEquals(listOf("Milk", "Mint tea", "Almond milk"), engine.suggest("mi").map { it.what })
        assertEquals(listOf("Milk", "Almond milk"), engine.suggest("mil").map { it.what })
        assertEquals(emptyList(), engine.suggest("xyz"))
    }

    @Test
    fun ranksFrequentRecentAndMineHigher() {
        val engine = SuggestionEngine(
            listOf(
                item("Bread", "Groceries", daysAgo = 1),
                item("Bananas", "Groceries", who = "Anna", daysAgo = 1),
                item("Batteries", "Home", daysAgo = 400),
                item("Batteries", "Home", daysAgo = 401),
            ),
            me = "Łukasz", now = now,
        )
        assertEquals(listOf("Bread", "Bananas", "Batteries"), engine.suggest("b").map { it.what })
    }

    @Test
    fun blankQueryReturnsTopItemsWithCategories() {
        val engine = SuggestionEngine(
            listOf(item("Coffee", "Eating out"), item("Coffee", "Eating out"), item("Coffee", "Groceries"), item("Fuel", "Transport")),
            me = "Łukasz", now = now,
        )
        assertEquals(Suggestion("Coffee", "Eating out"), engine.suggest("").first())
    }

    @Test
    fun matchingIgnoresCaseAndDiacritics() {
        val engine = SuggestionEngine(listOf(item("Żabka", "Groceries")), me = "Łukasz", now = now)
        assertEquals("Żabka", engine.suggest("zab").single().what)
        assertEquals("Groceries", engine.guessCategory("ZABKA"))
    }

    @Test
    fun guessesCategoryFromExactThenWordsThenMyLast() {
        val engine = SuggestionEngine(
            listOf(
                item("Pizza Hut", "Eating out", daysAgo = 3),
                item("Orlen fuel", "Transport", daysAgo = 2),
                item("Shoes", "Clothes", who = "Anna", daysAgo = 0),
            ),
            me = "Łukasz", now = now,
        )
        assertEquals("Eating out", engine.guessCategory("pizza hut"))
        assertEquals("Transport", engine.guessCategory("Orlen"))
        assertEquals("Transport", engine.guessCategory("something new"))
    }

    @Test
    fun historyBeatsHintAndHintBeatsMyLastCategory() {
        val engine = SuggestionEngine(
            listOf(item("Mleko owsiane", "Kawa", daysAgo = 5), item("Buty", "Ubrania", daysAgo = 1)),
            me = "Łukasz", now = now,
        )
        val hint = { what: String -> CategoryHints.guess(what, listOf("Spożywcze", "Ubrania", "Kawa")) }
        assertEquals("Kawa", engine.guessCategory("mleko owsiane", hint))
        assertEquals("Spożywcze", engine.guessCategory("chleb", hint))
        assertEquals("Ubrania", engine.guessCategory("coś nowego", hint))
    }

    @Test
    fun noHistoryNoGuess() {
        assertNull(SuggestionEngine(emptyList(), me = "Łukasz", now = now).guessCategory("anything"))
    }

    @Test
    fun displaysMostRecentSpelling() {
        val engine = SuggestionEngine(
            listOf(item("milk", "Groceries", daysAgo = 5), item("Milk", "Groceries", daysAgo = 1)),
            me = "Łukasz", now = now,
        )
        assertEquals("Milk", engine.suggest("m").single().what)
    }
}
