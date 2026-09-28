package net.osipiuk.bucklog.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CategoryHintsTest {
    private val polish = DefaultCategories.forLanguage("pl").map { it.name }
    private val english = DefaultCategories.forLanguage("en").map { it.name }

    @Test
    fun guessesFromProductWords() {
        assertEquals("Spożywcze", CategoryHints.guess("Mleko i chleb", polish))
        assertEquals("Spożywcze", CategoryHints.guess("Żabka", polish))
        assertEquals("Transport", CategoryHints.guess("Orlen tankowanie", polish))
        assertEquals("Zdrowie", CategoryHints.guess("apteka", polish))
        assertEquals("Groceries", CategoryHints.guess("milk", english))
        assertEquals("Travel", CategoryHints.guess("Ryanair flight", english))
    }

    @Test
    fun shortKeywordsMatchWholeWordsOnly() {
        assertEquals("Transport", CategoryHints.guess("serwis auta mechanik", polish))
        assertEquals("Spożywcze", CategoryHints.guess("ser żółty", polish))
        assertEquals("Podróże", CategoryHints.guess("lot do Rzymu", polish))
        assertNull(CategoryHints.guess("lotion", english))
    }

    @Test
    fun typingACategoryNamePicksIt() {
        assertEquals("Dzieci", CategoryHints.guess("dzieci", polish))
        assertEquals("Eating out", CategoryHints.guess("eating out", english))
        assertEquals("Pies", CategoryHints.guess("karma pies", polish + "Pies"))
    }

    @Test
    fun hintsNeedTheCategoryToExist() {
        assertNull(CategoryHints.guess("mleko", listOf("Jedzenie", "Auto")))
        assertNull(CategoryHints.guess("", polish))
    }
}
