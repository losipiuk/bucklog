package net.osipiuk.bucklog.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class TextNormalizeTest {
    @Test
    fun foldsCaseDiacriticsAndWhitespace() {
        assertEquals("zabka kawa", normalizeText("  Żabka   Kawa "))
        assertEquals("zolc gesla", normalizeText("ŻÓŁĆ gęślą"))
        assertEquals("strasse", normalizeText("Straße"))
        assertEquals("", normalizeText("   "))
    }
}
