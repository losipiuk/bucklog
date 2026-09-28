package net.osipiuk.bucklog.google

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpreadsheetUrlTest {
    @Test
    fun parsesLinksAndBareIds() {
        val id = "1AbCdEfGhIjKlMnOpQrStUvWxYz_0123456789-ab"
        assertEquals(id, parseSpreadsheetId("https://docs.google.com/spreadsheets/d/$id/edit?usp=sharing"))
        assertEquals(id, parseSpreadsheetId("https://docs.google.com/spreadsheets/u/1/d/$id/edit#gid=0"))
        assertEquals(id, parseSpreadsheetId("  $id "))
        assertNull(parseSpreadsheetId("https://example.com/whatever"))
    }
}
