package net.osipiuk.bucklog.sheet

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Tab names, headers and formatting of the family sheet (SPEC §3). */
object SheetLayout {
    const val CATEGORIES = "Categories"
    const val SETTINGS = "Settings"
    const val LOG = "Log"
    const val SCHEMA_VERSION = 1

    val categoriesHeader = listOf("Name", "Emoji", "Archived")
    val settingsHeader = listOf("Key", "Value")
    val yearHeader = listOf("Date", "Who", "What", "Category", "Amount", "Currency", "Rate", "ID")
    val logHeader = listOf("Time", "Who", "Action", "ID", "Before", "After")

    const val DATE_PATTERN = "yyyy-mm-dd hh:mm"

    fun yearTab(year: Int): String = year.toString()

    fun isYearTab(title: String): Boolean = title.length == 4 && title.all { it.isDigit() }

    /** Sheets A1 range for a whole tab, quoted so digit-only names like 2026 parse. */
    fun range(tab: String, cells: String): String = "'${tab.replace("'", "''")}'!$cells"

    fun headerRow(values: List<String>): List<JsonElement> = values.map(::JsonPrimitive)

    /** Bold header row for any tab. */
    fun headerFormat(sheetId: Int): JsonObject = repeatCell(
        sheetId, startRow = 0, endRow = 1, startCol = null, endCol = null,
        format = buildJsonObject { putJsonObject("textFormat") { put("bold", true) } },
        fields = "userEnteredFormat.textFormat.bold",
    )

    /** Checkbox validation for the Archived column. */
    fun categoriesFormat(sheetId: Int): List<JsonObject> = listOf(
        headerFormat(sheetId),
        buildJsonObject {
            putJsonObject("setDataValidation") {
                put("range", gridRange(sheetId, startRow = 1, startCol = 2, endCol = 3))
                putJsonObject("rule") { putJsonObject("condition") { put("type", "BOOLEAN") } }
            }
        },
    )

    /** Date/number formats, category dropdown (warning only) and the greyed-out ID column. */
    fun yearTabFormat(sheetId: Int): List<JsonObject> = listOf(
        headerFormat(sheetId),
        numberFormat(sheetId, col = 0, type = "DATE_TIME", pattern = DATE_PATTERN),
        numberFormat(sheetId, col = 4, type = "NUMBER", pattern = "#,##0.00"),
        numberFormat(sheetId, col = 6, type = "NUMBER", pattern = "0.0000"),
        repeatCell(
            sheetId, startRow = 1, endRow = null, startCol = 7, endCol = 8,
            format = buildJsonObject {
                putJsonObject("textFormat") {
                    putJsonObject("foregroundColorStyle") {
                        putJsonObject("rgbColor") { put("red", 0.6); put("green", 0.6); put("blue", 0.6) }
                    }
                }
            },
            fields = "userEnteredFormat.textFormat.foregroundColorStyle",
        ),
        buildJsonObject {
            putJsonObject("setDataValidation") {
                put("range", gridRange(sheetId, startRow = 1, startCol = 3, endCol = 4))
                putJsonObject("rule") {
                    putJsonObject("condition") {
                        put("type", "ONE_OF_RANGE")
                        putJsonArray("values") {
                            add(buildJsonObject { put("userEnteredValue", "=${range(CATEGORIES, "\$A\$2:\$A")}") })
                        }
                    }
                    put("strict", false)
                    put("showCustomUi", true)
                }
            }
        },
    )

    /** Log tab: bold header, date-time Time column. */
    fun logFormat(sheetId: Int): List<JsonObject> = listOf(
        headerFormat(sheetId),
        numberFormat(sheetId, col = 0, type = "DATE_TIME", pattern = DATE_PATTERN),
    )

    fun addSheet(title: String): JsonObject = buildJsonObject {
        putJsonObject("addSheet") {
            putJsonObject("properties") {
                put("title", title)
                putJsonObject("gridProperties") { put("frozenRowCount", 1) }
            }
        }
    }

    private fun numberFormat(sheetId: Int, col: Int, type: String, pattern: String) = repeatCell(
        sheetId, startRow = 1, endRow = null, startCol = col, endCol = col + 1,
        format = buildJsonObject { putJsonObject("numberFormat") { put("type", type); put("pattern", pattern) } },
        fields = "userEnteredFormat.numberFormat",
    )

    private fun repeatCell(
        sheetId: Int, startRow: Int, endRow: Int?, startCol: Int?, endCol: Int?, format: JsonObject, fields: String,
    ) = buildJsonObject {
        putJsonObject("repeatCell") {
            put("range", gridRange(sheetId, startRow, endRow, startCol, endCol))
            putJsonObject("cell") { put("userEnteredFormat", format) }
            put("fields", fields)
        }
    }

    private fun gridRange(sheetId: Int, startRow: Int, endRow: Int? = null, startCol: Int? = null, endCol: Int? = null) =
        buildJsonObject {
            put("sheetId", sheetId)
            put("startRowIndex", startRow)
            endRow?.let { put("endRowIndex", it) }
            startCol?.let { put("startColumnIndex", it) }
            endCol?.let { put("endColumnIndex", it) }
        }
}
