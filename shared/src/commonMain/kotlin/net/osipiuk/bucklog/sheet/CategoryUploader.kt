package net.osipiuk.bucklog.sheet

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import net.osipiuk.bucklog.data.CategoryOp
import net.osipiuk.bucklog.data.LocalStore
import net.osipiuk.bucklog.domain.Category
import net.osipiuk.bucklog.domain.normalizeText
import net.osipiuk.bucklog.google.SheetsClient
import net.osipiuk.bucklog.google.ValueRange

/**
 * Writes category changes made in the app to the sheet's Categories tab. Updates touch only the
 * row with the category's (old) name, so categories others added by hand are never overwritten.
 */
class CategoryUploader(private val store: LocalStore, private val sheets: SheetsClient) {
    /** Applies queued ops in order; on failure records it, leaves the rest queued and rethrows. */
    suspend fun upload(spreadsheetId: String) {
        val ops = store.pendingCategoryOps()
        if (ops.isEmpty()) return
        // Names by row, read once when needed (row 2 is index 0).
        var names: MutableList<String>? = null
        suspend fun rows(): MutableList<String> = names ?: sheets.batchGet(spreadsheetId, listOf(SheetLayout.range(SheetLayout.CATEGORIES, "A2:A")))
            .single().values.map { (it.firstOrNull() as? JsonPrimitive)?.content.orEmpty() }.toMutableList().also { names = it }

        for (op in ops) {
            try {
                when (op) {
                    is CategoryOp.Add -> {
                        append(spreadsheetId, Category(op.name, emoji = null))
                        names?.add(op.name)
                    }
                    is CategoryOp.Update -> {
                        val all = rows()
                        val index = all.indexOfFirst { normalizeText(it) == normalizeText(op.name) }
                        if (index < 0) {
                            append(spreadsheetId, op.category)
                            all += op.category.name
                        } else {
                            val row = index + 2
                            sheets.batchUpdateValues(
                                spreadsheetId,
                                listOf(ValueRange(range = SheetLayout.range(SheetLayout.CATEGORIES, "A$row:C$row"), values = listOf(cells(op.category)))),
                            )
                            all[index] = op.category.name
                        }
                    }
                }
                store.completeOutbox(op.seq)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                store.failOutbox(op.seq, e.message ?: e::class.simpleName.orEmpty())
                throw e
            }
        }
    }

    private suspend fun append(spreadsheetId: String, category: Category) =
        sheets.append(spreadsheetId, SheetLayout.range(SheetLayout.CATEGORIES, "A:C"), listOf(cells(category)))

    private fun cells(c: Category): List<JsonElement> = listOf(JsonPrimitive(c.name), JsonPrimitive(c.emoji ?: ""), JsonPrimitive(c.archived))
}
