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
}
