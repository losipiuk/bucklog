package net.osipiuk.bucklog.domain

import net.osipiuk.bucklog.domain.AmountKey.BACKSPACE
import net.osipiuk.bucklog.domain.AmountKey.CLEAR
import net.osipiuk.bucklog.domain.AmountKey.D0
import net.osipiuk.bucklog.domain.AmountKey.D00
import net.osipiuk.bucklog.domain.AmountKey.D1
import net.osipiuk.bucklog.domain.AmountKey.D3
import net.osipiuk.bucklog.domain.AmountKey.D5
import net.osipiuk.bucklog.domain.AmountKey.D9
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AmountInputTest {
    private fun type(vararg keys: AmountKey) = keys.fold(AmountInput()) { input, key -> input.press(key) }

    @Test
    fun digitsShiftInFromTheRight() {
        assertEquals(3530, type(D3, D5, D3, D0).minorUnits)
        assertEquals(100, type(D1, D00).minorUnits)
    }

    @Test
    fun leadingZerosAreIgnored() {
        assertTrue(type(D0, D00, D0).isZero)
        assertEquals("", type(D0, D00).digits)
        assertEquals(5, type(D0, D5).minorUnits)
    }

    @Test
    fun backspaceAndClear() {
        assertEquals(353, type(D3, D5, D3, D0, BACKSPACE).minorUnits)
        assertTrue(type(D3, BACKSPACE, BACKSPACE).isZero)
        assertTrue(type(D3, D5, CLEAR).isZero)
    }

    @Test
    fun capsLength() {
        val nine = type(*Array(AmountInput.MAX_DIGITS) { D9 })
        assertEquals(nine, nine.press(D1))
        assertEquals(nine, nine.press(D00))
    }
}
