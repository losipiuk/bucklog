package net.osipiuk.bucklog.ui

import net.osipiuk.bucklog.data.LocalStore
import net.osipiuk.bucklog.google.DriveClient
import net.osipiuk.bucklog.google.SheetsClient
import net.osipiuk.bucklog.sheet.CategoryUploader
import net.osipiuk.bucklog.sheet.SheetSetup
import net.osipiuk.bucklog.sync.ExchangeRates
import net.osipiuk.bucklog.sync.SyncEngine
import kotlin.time.Clock

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
}
