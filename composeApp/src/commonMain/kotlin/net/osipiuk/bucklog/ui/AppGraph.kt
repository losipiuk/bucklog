package net.osipiuk.bucklog.ui

import kotlin.time.Clock
import net.osipiuk.bucklog.data.LocalStore
import net.osipiuk.bucklog.google.DriveClient
import net.osipiuk.bucklog.google.SheetsClient
import net.osipiuk.bucklog.sheet.CategoryUploader
import net.osipiuk.bucklog.sheet.SheetSetup
import net.osipiuk.bucklog.sync.ExchangeRates
import net.osipiuk.bucklog.sync.SyncEngine

/** The app's long-lived services, created once by the platform. */
class AppGraph(
    val store: LocalStore,
    val sheets: SheetsClient,
    val drive: DriveClient,
    rates: ExchangeRates,
    val clock: Clock = Clock.System,
) {
    val setup = SheetSetup(sheets)
    val categoryUploader = CategoryUploader(store, sheets)
    val sync = SyncEngine(store, sheets, drive, setup, categoryUploader, rates, clock)

    /** Connects to [spreadsheetId]: reads its categories, main currency and time zone into the local store. */
    suspend fun connectSheet(spreadsheetId: String, fallbackCurrency: String) {
        val sheet = setup.readConfig(spreadsheetId)
        store.replaceCategories(sheet.categories)
        store.updateConfig(
            spreadsheetId = spreadsheetId,
            spreadsheetName = sheet.title,
            mainCurrency = sheet.mainCurrency ?: fallbackCurrency,
            timeZone = sheet.timeZone,
        )
    }

    /** Current UI language, for text produced outside composition (toasts, validation). Set by [BucklogApp]. */
    var strings: Strings = EnglishStrings
}
