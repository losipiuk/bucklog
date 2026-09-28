package net.osipiuk.bucklog.sheet

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonPrimitive
import net.osipiuk.bucklog.data.LocalStore
import net.osipiuk.bucklog.google.SheetsClient

/** Appends categories created in the app to the sheet's Categories tab. */
class CategoryUploader(private val store: LocalStore, private val sheets: SheetsClient) {
    /** Uploads queued categories in order; on failure records it, leaves the rest queued and rethrows. */
    suspend fun upload(spreadsheetId: String) {
        for ((seq, name) in store.pendingCategoryAdds()) {
            try {
                sheets.append(
                    spreadsheetId,
                    SheetLayout.range(SheetLayout.CATEGORIES, "A:C"),
                    listOf(listOf(JsonPrimitive(name), JsonPrimitive(""), JsonPrimitive(false))),
                )
                store.completeOutbox(seq)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                store.failOutbox(seq, e.message ?: e::class.simpleName.orEmpty())
                throw e
            }
        }
    }
}
