package net.osipiuk.bucklog.domain

import kotlin.math.absoluteValue

/** Formats minor units with a fixed number of decimals, e.g. 353000 → "3 530.00". */
class MoneyFormat(
    private val decimalSeparator: Char = '.',
    private val groupingSeparator: Char = ' ',
) {
    fun format(minorUnits: Long, digits: Int): String {
        val sign = if (minorUnits < 0) "-" else ""
        val abs = minorUnits.absoluteValue.toString().padStart(digits + 1, '0')
        val whole = abs.dropLast(digits).reversed().chunked(3).joinToString(groupingSeparator.toString()).reversed()
        val fraction = abs.takeLast(digits)
        return if (digits == 0) "$sign$whole" else "$sign$whole$decimalSeparator$fraction"
    }

    fun format(minorUnits: Long, currency: String): String = format(minorUnits, Currencies.digits(currency))

    fun formatWithCode(minorUnits: Long, currency: String): String = "${format(minorUnits, currency)} $currency"

    /** For editing: no grouping, e.g. 353000 → "3530.00" (with this format's decimal separator). */
    fun formatPlain(minorUnits: Long, currency: String): String =
        MoneyFormat(decimalSeparator, groupingSeparator = '\u0000').format(minorUnits, currency).replace("\u0000", "")

    companion object {
        /** Parses a typed amount ("35,3", "35.30", "1 234,5") into minor units; null if it isn't a number. */
        fun parse(text: String, currency: String): Long? {
            val digits = Currencies.digits(currency)
            val cleaned = text.trim().replace(" ", "").replace("\u00A0", "").replace(',', '.')
            if (!Regex("""\d+(\.\d*)?|\.\d+""").matches(cleaned)) return null
            val whole = cleaned.substringBefore('.').ifEmpty { "0" }
            val fraction = cleaned.substringAfter('.', "")
            if (fraction.length > digits) return null
            return (whole + fraction.padEnd(digits, '0')).toLongOrNull()
        }
    }
}
