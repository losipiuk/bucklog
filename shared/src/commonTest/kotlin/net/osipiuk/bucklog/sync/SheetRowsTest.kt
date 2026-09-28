package net.osipiuk.bucklog.sync

import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import net.osipiuk.bucklog.domain.Entry
import net.osipiuk.bucklog.domain.EntryStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class SheetRowsTest {
    private val warsaw = TimeZone.of("Europe/Warsaw")

    @Test
    fun dateSerialsUseSheetLocalTime() {
        // 2026-09-28 18:42 in Warsaw (UTC+2) is serial 46293 + 18:42/24h.
        val instant = Instant.parse("2026-09-28T16:42:00Z")
        val serial = SheetDates.toSerial(instant, warsaw)
        assertEquals(46293 + (18 * 60 + 42) / 1440.0, serial, 1e-9)
        assertEquals(instant, SheetDates.fromSerial(serial, warsaw))
    }

    @Test
    fun parsesTextDates() {
        assertEquals(Instant.parse("2026-09-27T22:00:00Z"), SheetDates.parseText("2026-09-28", warsaw))
        assertEquals(Instant.parse("2026-09-28T16:42:00Z"), SheetDates.parseText("28.09.2026 18:42", warsaw))
        assertNull(SheetDates.parseText("yesterday", warsaw))
        assertNull(SheetDates.parseText("2026-13-01", warsaw))
    }

    @Test
    fun writesExactDecimals() {
        val entry = Entry("abc", Instant.parse("2026-09-28T16:42:00Z"), "Łukasz", "Milk", "Food", 3530, "PLN", null, EntryStatus.PENDING)
        val row = JsonArray(SheetRows.toRow(entry, warsaw)).toString()
        assertTrue(""","Łukasz","Milk","Food",35.30,"PLN","","abc"]""" in row, row)
        val refund = JsonArray(SheetRows.toRow(entry.copy(amountMinor = -5, currency = "EUR", rate = "4.2715"), warsaw)).toString()
        assertTrue("-0.05,\"EUR\",4.2715" in refund, refund)
        val yen = JsonArray(SheetRows.toRow(entry.copy(amountMinor = 3530, currency = "JPY"), warsaw)).toString()
        assertTrue(",3530,\"JPY\"" in yen, yen)
    }

    @Test
    fun parsesHandTypedRows() {
        val cells = listOf(JsonPrimitive("28.09.2026"), JsonPrimitive("Anna"), JsonPrimitive(123), JsonPrimitive("Food"),
            JsonPrimitive("12,50"), JsonPrimitive(""), JsonPrimitive(""), JsonPrimitive("x1"))
        val entry = SheetRows.parseEntry("x1", cells, warsaw, "PLN")!!
        assertEquals(1250, entry.amountMinor)
        assertEquals("PLN", entry.currency)
        assertEquals("123", entry.what)
        assertNull(entry.rate)
        assertNull(SheetRows.parseEntry("x2", cells.toMutableList().also { it[4] = JsonPrimitive("abc") }, warsaw, "PLN"))
        assertNull(SheetRows.parseEntry("x3", cells.toMutableList().also { it[0] = JsonPrimitive("") }, warsaw, "PLN"))
    }

    @Test
    fun readsNumbersBackToMinorUnits() {
        assertEquals(3530, SheetRows.parseAmount(JsonPrimitive(35.3), 2))
        assertEquals(-19900, SheetRows.parseAmount(JsonPrimitive(-199), 2))
        assertEquals(1, SheetRows.parseAmount(JsonPrimitive(0.01), 2))
        assertEquals(123456, SheetRows.parseAmount(JsonPrimitive("1 234,56"), 2))
    }

    @Test
    fun skipsBlankRowsAndKeepsRowNumbers() {
        val rows = listOf(
            listOf(JsonPrimitive(46293.5), JsonPrimitive("A"), JsonPrimitive("x"), JsonPrimitive("F"), JsonPrimitive(1), JsonPrimitive("PLN"), JsonPrimitive(""), JsonPrimitive("id1")),
            emptyList(),
            listOf(JsonPrimitive(46293.5), JsonPrimitive("B"), JsonPrimitive("y"), JsonPrimitive("F"), JsonPrimitive(2)),
        )
        val parsed = SheetRows.parse("2026", rows, warsaw, "PLN")
        assertEquals(listOf(2, 4), parsed.map { it.rowNumber })
        assertEquals(listOf("id1", null), parsed.map { it.id })
    }
}
