package net.osipiuk.bucklog.sync

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import net.osipiuk.bucklog.domain.Currencies
import net.osipiuk.bucklog.domain.Entry
import net.osipiuk.bucklog.domain.EntryStatus
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.time.Instant

/** Sheets date-time serials: days since 1899-12-30, as local time in the spreadsheet's time zone. */
object SheetDates {
    private const val UNIX_EPOCH_SERIAL = 25569.0
    private const val SECONDS_PER_DAY = 86400.0

    fun toSerial(instant: Instant, tz: TimeZone): Double {
        val localSeconds = instant.toLocalDateTime(tz).toInstant(TimeZone.UTC).epochSeconds
        return localSeconds / SECONDS_PER_DAY + UNIX_EPOCH_SERIAL
    }

    fun fromSerial(serial: Double, tz: TimeZone): Instant {
        val localSeconds = ((serial - UNIX_EPOCH_SERIAL) * SECONDS_PER_DAY).roundToLong()
        return Instant.fromEpochSeconds(localSeconds).toLocalDateTime(TimeZone.UTC).toInstant(tz)
    }

    /** Text dates typed by hand where Sheets didn't recognize them: 2026-09-28[ 18:42], 28.09.2026[ 18:42]. */
    fun parseText(text: String, tz: TimeZone): Instant? {
        val match = TEXT_DATE.matchEntire(text.trim()) ?: return null
        val (a, b, c) = match.destructured
        val date = runCatching {
            if (a.length == 4) LocalDate(a.toInt(), b.toInt(), c.toInt()) else LocalDate(c.toInt(), b.toInt(), a.toInt())
        }.getOrNull() ?: return null
        val hour = match.groupValues[4].toIntOrNull() ?: 0
        val minute = match.groupValues[5].toIntOrNull() ?: 0
        val time = runCatching { LocalTime(hour, minute) }.getOrNull() ?: return null
        return LocalDateTime(date, time).toInstant(tz)
    }

    private val TEXT_DATE = Regex("""(\d{4}|\d{1,2})[-./](\d{1,2})[-./](\d{4}|\d{1,2})(?:[ T]+(\d{1,2}):(\d{2})(?::\d{2})?)?""")
}

/** One data row of a year tab, as read. [entry] is null when the row can't be parsed. */
data class SheetRow(
    val tab: String,
    /** 1-based sheet row number. */
    val rowNumber: Int,
    val id: String?,
    val entry: Entry?,
    val cells: List<JsonElement>,
)

/** Year tab columns (SPEC §3.3): Date | Who | What | Category | Amount | Currency | Rate | ID. */
object SheetRows {
    const val COLUMNS = 8
    private const val ID_COLUMN = 7

    /** Parses rows read from A2 with UNFORMATTED_VALUE; skips blank rows. */
    fun parse(tab: String, rows: List<List<JsonElement>>, tz: TimeZone, mainCurrency: String): List<SheetRow> =
        rows.mapIndexedNotNull { index, cells ->
            if (cells.all { it.text().isEmpty() }) return@mapIndexedNotNull null
            val id = cells.getOrNull(ID_COLUMN)?.text()?.ifEmpty { null }
            SheetRow(tab, rowNumber = index + 2, id = id, entry = id?.let { parseEntry(it, cells, tz, mainCurrency) }, cells = cells)
        }

    /** Parses a row with a known [id]; null when Date or Amount can't be understood. */
    fun parseEntry(id: String, cells: List<JsonElement>, tz: TimeZone, mainCurrency: String): Entry? {
        fun cell(i: Int) = cells.getOrNull(i)
        val timestamp = cell(0)?.let { date ->
            (date as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.let { SheetDates.fromSerial(it, tz) }
                ?: SheetDates.parseText(date.text(), tz)
        } ?: return null
        val currency = cell(5)?.text()?.uppercase()?.ifEmpty { null } ?: mainCurrency
        val amount = cell(4)?.let { parseAmount(it, Currencies.digits(currency)) } ?: return null
        return Entry(
            id = id,
            timestamp = timestamp,
            who = cell(1)?.text().orEmpty(),
            what = cell(2)?.text().orEmpty(),
            category = cell(3)?.text().orEmpty(),
            amountMinor = amount,
            currency = currency,
            rate = cell(6)?.let(::parseRate),
            status = EntryStatus.SYNCED,
        )
    }

    fun toRow(entry: Entry, tz: TimeZone): List<JsonElement> = listOf(
        JsonPrimitive(SheetDates.toSerial(entry.timestamp, tz)),
        JsonPrimitive(entry.who),
        JsonPrimitive(entry.what),
        JsonPrimitive(entry.category),
        decimal(formatMinor(entry.amountMinor, Currencies.digits(entry.currency))),
        JsonPrimitive(entry.currency),
        entry.rate?.takeIf { DECIMAL.matches(it) }?.let(::decimal) ?: JsonPrimitive(""),
        JsonPrimitive(entry.id),
    )

    fun rawJson(cells: List<JsonElement>): String = JsonArray(cells).toString()

    internal fun parseAmount(cell: JsonElement, digits: Int): Long? {
        val primitive = cell as? JsonPrimitive ?: return null
        val value = if (!primitive.isString) {
            primitive.doubleOrNull
        } else {
            primitive.content.replace(" ", "").replace(" ", "").replace(',', '.').toDoubleOrNull()
        } ?: return null
        return (value * 10.0.pow(digits)).roundToLong()
    }

    private fun parseRate(cell: JsonElement): String? {
        val primitive = cell as? JsonPrimitive ?: return null
        val text = primitive.content.trim().replace(',', '.')
        return text.takeIf { it.isNotEmpty() && DECIMAL.matches(it) && (text.toDoubleOrNull() ?: 0.0) > 0.0 }
    }

    internal fun formatMinor(minor: Long, digits: Int): String {
        val sign = if (minor < 0) "-" else ""
        val abs = minor.absoluteValue.toString().padStart(digits + 1, '0')
        return if (digits == 0) "$sign$abs" else "$sign${abs.dropLast(digits)}.${abs.takeLast(digits)}"
    }

    /** A JSON number written exactly as given (no binary floating-point rounding). */
    @OptIn(ExperimentalSerializationApi::class)
    private fun decimal(text: String): JsonElement = JsonUnquotedLiteral(text)

    private val DECIMAL = Regex("""-?\d+(\.\d+)?(E-?\d+)?""")

    private fun JsonElement.text(): String = when (this) {
        is JsonPrimitive -> if (isString) content.trim() else (booleanOrNull?.toString() ?: content)
        else -> ""
    }
}
