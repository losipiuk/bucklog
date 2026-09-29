package net.osipiuk.bucklog.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyFormatTest {
    private val format = MoneyFormat(decimalSeparator = ',', groupingSeparator = ' ')

    @Test
    fun formatsWithFixedDecimals() {
        assertEquals("0,00", format.format(0, 2))
        assertEquals("0,05", format.format(5, 2))
        assertEquals("35,30", format.format(3530, 2))
        assertEquals("1 234 567,89", format.format(123456789, 2))
        assertEquals("-199,00", format.format(-19900, 2))
    }

    @Test
    fun plainFormatAndParseRoundTrip() {
        assertEquals("3530,00", format.formatPlain(353000, "PLN"))
        assertEquals(353000, MoneyFormat.parse("3530,00", "PLN"))
        assertEquals(3530, MoneyFormat.parse("35.3", "PLN"))
        assertEquals(123450, MoneyFormat.parse("1 234,5", "PLN"))
        assertEquals(50, MoneyFormat.parse(",5", "PLN"))
        assertEquals(3530, MoneyFormat.parse("3530", "JPY"))
        assertEquals(null, MoneyFormat.parse("35,305", "PLN"))
        assertEquals(null, MoneyFormat.parse("abc", "PLN"))
        assertEquals(null, MoneyFormat.parse("-5", "PLN"))
    }

    @Test
    fun usesCurrencyDigits() {
        assertEquals("3 530", format.format(3530, "JPY"))
        assertEquals("3,530", format.format(3530, "TND"))
        assertEquals("35,30 EUR", format.formatWithCode(3530, "EUR"))
    }
}
