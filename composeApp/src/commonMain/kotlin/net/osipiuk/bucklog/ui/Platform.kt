package net.osipiuk.bucklog.ui

import androidx.compose.runtime.Composable

/** Things only the host platform can do. Implemented in androidApp (and later iosApp). */
interface Platform {
    /** Lets the user choose a Google account and grant access; returns its email, or null when cancelled. */
    suspend fun signIn(): String?

    /** Opens Google Picker for spreadsheets; returns null when cancelled. */
    suspend fun pickSheet(preselectFileId: String?): PickedSheet?

    /** Shows a short confirmation and closes the app (the Add flow's last step). */
    fun finishWithMessage(message: String)

    /** Handles the system back gesture while [enabled]. */
    @Composable
    fun BackHandler(enabled: Boolean, onBack: () -> Unit)

    val deviceCurrency: String
    val timeZoneId: String

    /** ISO 639 language of the UI, e.g. "en". */
    val language: String

    /** ISO 3166 country of the device's region, e.g. "PL"; empty when unknown. */
    val country: String
    val decimalSeparator: Char

    /** Language for a new sheet's default categories: the region wins, so English UI in Poland gets Polish names. */
    val contentLanguage: String get() = if (country == "PL") "pl" else language

    /** Spreadsheet locale for a new sheet, e.g. "pl_PL" (Sheets rejects Android's -u- extensions). */
    val sheetLocale: String get() = if (country.isEmpty()) language else "${contentLanguage}_$country"
}

data class PickedSheet(val id: String, val name: String)
