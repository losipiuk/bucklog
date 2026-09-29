package net.osipiuk.bucklog.ui

import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.osipiuk.bucklog.data.LocalStore
import net.osipiuk.bucklog.domain.Invite
import net.osipiuk.bucklog.domain.InviteLinks
import net.osipiuk.bucklog.google.DriveClient
import net.osipiuk.bucklog.google.SheetsClient
import net.osipiuk.bucklog.sheet.CategoryUploader
import net.osipiuk.bucklog.sheet.SheetSetup
import net.osipiuk.bucklog.sync.ExchangeRates
import net.osipiuk.bucklog.sync.SyncEngine

/** The app's long-lived services, created once by the platform. */
private const val PENDING_INVITE = "pending_invite"

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

    /** An invite this phone received (via its join link) and hasn't acted on yet. */
    val pendingInvite: Flow<Invite?> = store.valueFlow(PENDING_INVITE).map { it?.let(InviteLinks::parse) }

    suspend fun receiveInvite(invite: Invite) = store.putValue(PENDING_INVITE, InviteLinks.app(invite))

    suspend fun clearInvite() = store.putValue(PENDING_INVITE, null)

    /** Current UI language, for text produced outside composition (toasts, validation). Set by [BucklogApp]. */
    var strings: Strings = EnglishStrings
}
