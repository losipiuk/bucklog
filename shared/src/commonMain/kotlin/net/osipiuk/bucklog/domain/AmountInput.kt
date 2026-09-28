package net.osipiuk.bucklog.domain

enum class AmountKey { D0, D1, D2, D3, D4, D5, D6, D7, D8, D9, D00, BACKSPACE, CLEAR }

/**
 * ATM-style amount entry: the decimal point is fixed and digits shift in from the right,
 * so typing 3 5 3 0 gives 35.30. The value is kept as minor units; the currency decides
 * where the decimal point goes when displayed.
 */
data class AmountInput(val digits: String = "") {
    val minorUnits: Long get() = digits.toLongOrNull() ?: 0L
    val isZero: Boolean get() = minorUnits == 0L

    fun press(key: AmountKey): AmountInput = when (key) {
        AmountKey.BACKSPACE -> copy(digits = digits.dropLast(1))
        AmountKey.CLEAR -> AmountInput()
        AmountKey.D00 -> append("00")
        else -> append(key.ordinal.toString())
    }

    private fun append(more: String): AmountInput {
        val next = (digits + more).trimStart('0')
        return if (next.length > MAX_DIGITS) this else copy(digits = next)
    }

    companion object {
        const val MAX_DIGITS = 9
    }
}
