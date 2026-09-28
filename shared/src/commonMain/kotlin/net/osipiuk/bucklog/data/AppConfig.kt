package net.osipiuk.bucklog.data

/** What the user configured on first run (SPEC §5.1), plus what was read from the sheet. */
data class AppConfig(
    val myName: String?,
    val accountEmail: String?,
    val spreadsheetId: String?,
    val spreadsheetName: String?,
    val mainCurrency: String,
    val timeZone: String?,
    val lastCurrency: String?,
) {
    val isComplete: Boolean get() = !myName.isNullOrBlank() && spreadsheetId != null && accountEmail != null

    /** Currency preselected on the amount screen: the last one used, else the main currency. */
    val defaultCurrency: String get() = lastCurrency ?: mainCurrency

    internal companion object Keys {
        const val MY_NAME = "my_name"
        const val ACCOUNT_EMAIL = "account_email"
        const val SPREADSHEET_ID = "spreadsheet_id"
        const val SPREADSHEET_NAME = "spreadsheet_name"
        const val MAIN_CURRENCY = "main_currency"
        const val TIME_ZONE = "time_zone"
        const val LAST_CURRENCY = "last_currency"

        fun from(values: Map<String, String>) = AppConfig(
            myName = values[MY_NAME],
            accountEmail = values[ACCOUNT_EMAIL],
            spreadsheetId = values[SPREADSHEET_ID],
            spreadsheetName = values[SPREADSHEET_NAME],
            mainCurrency = values[MAIN_CURRENCY] ?: "PLN",
            timeZone = values[TIME_ZONE],
            lastCurrency = values[LAST_CURRENCY],
        )
    }
}
