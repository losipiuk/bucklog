package net.osipiuk.bucklog.domain

/** An ISO 4217 currency; [digits] is the number of minor-unit digits (2 for PLN, 0 for JPY). */
data class Currency(val code: String, val digits: Int, val name: String)

object Currencies {
    val all: List<Currency> = listOf(
        Currency("PLN", 2, "Polish złoty"),
        Currency("EUR", 2, "Euro"),
        Currency("USD", 2, "US dollar"),
        Currency("GBP", 2, "British pound"),
        Currency("CHF", 2, "Swiss franc"),
        Currency("CZK", 2, "Czech koruna"),
        Currency("HUF", 2, "Hungarian forint"),
        Currency("SEK", 2, "Swedish krona"),
        Currency("NOK", 2, "Norwegian krone"),
        Currency("DKK", 2, "Danish krone"),
        Currency("ISK", 0, "Icelandic króna"),
        Currency("RON", 2, "Romanian leu"),
        Currency("RSD", 2, "Serbian dinar"),
        Currency("UAH", 2, "Ukrainian hryvnia"),
        Currency("TRY", 2, "Turkish lira"),
        Currency("GEL", 2, "Georgian lari"),
        Currency("ILS", 2, "Israeli shekel"),
        Currency("AED", 2, "UAE dirham"),
        Currency("EGP", 2, "Egyptian pound"),
        Currency("MAD", 2, "Moroccan dirham"),
        Currency("TND", 3, "Tunisian dinar"),
        Currency("ZAR", 2, "South African rand"),
        Currency("CAD", 2, "Canadian dollar"),
        Currency("MXN", 2, "Mexican peso"),
        Currency("BRL", 2, "Brazilian real"),
        Currency("ARS", 2, "Argentine peso"),
        Currency("AUD", 2, "Australian dollar"),
        Currency("NZD", 2, "New Zealand dollar"),
        Currency("JPY", 0, "Japanese yen"),
        Currency("KRW", 0, "South Korean won"),
        Currency("CNY", 2, "Chinese yuan"),
        Currency("HKD", 2, "Hong Kong dollar"),
        Currency("SGD", 2, "Singapore dollar"),
        Currency("THB", 2, "Thai baht"),
        Currency("VND", 0, "Vietnamese dong"),
        Currency("IDR", 2, "Indonesian rupiah"),
        Currency("INR", 2, "Indian rupee"),
    )

    private val byCode = all.associateBy { it.code }

    fun find(code: String): Currency? = byCode[code.uppercase()]

    /** Minor-unit digits; unknown codes are treated as having 2. */
    fun digits(code: String): Int = find(code)?.digits ?: 2
}
