package net.osipiuk.bucklog.sheet

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.osipiuk.bucklog.domain.Category
import net.osipiuk.bucklog.google.GoogleApiException
import net.osipiuk.bucklog.google.GridProperties
import net.osipiuk.bucklog.google.Sheet
import net.osipiuk.bucklog.google.SheetProperties
import net.osipiuk.bucklog.google.SheetsClient
import net.osipiuk.bucklog.google.Spreadsheet
import net.osipiuk.bucklog.google.SpreadsheetProperties
import net.osipiuk.bucklog.google.ValueRange

/** What the app reads from the Categories and Settings tabs. */
data class SheetConfig(
    val title: String,
    val timeZone: String?,
    val mainCurrency: String?,
    val categories: List<Category>,
)

/** Creates the family sheet, or completes the layout of a picked one, and reads its configuration. */
class SheetSetup(private val sheets: SheetsClient) {

    suspend fun createFamilySheet(
        title: String,
        mainCurrency: String,
        timeZone: String,
        locale: String?,
        categories: List<Category>,
        year: Int,
    ): Spreadsheet {
        val tabs = listOf(SheetLayout.CATEGORIES, SheetLayout.SETTINGS, SheetLayout.yearTab(year))
        fun spec(locale: String?) = Spreadsheet(
            properties = SpreadsheetProperties(title, timeZone = timeZone, locale = locale),
            sheets = tabs.map { Sheet(SheetProperties(title = it, gridProperties = GridProperties(frozenRowCount = 1))) },
        )
        val created = try {
            sheets.create(spec(locale))
        } catch (e: GoogleApiException) {
            // Sheets supports only some locales; the owner's Google default is a fine fallback.
            if (e.httpStatus != 400 || locale == null) throw e
            sheets.create(spec(null))
        }
        val id = created.spreadsheetId ?: error("Sheets API returned no spreadsheetId")
        val sheetIds = created.sheets.associate { it.properties.title to (it.properties.sheetId ?: 0) }
        initializeTabs(id, sheetIds, mainCurrency, categories)
        return created
    }

    /**
     * Adds whichever of Categories, Settings and the current year tab are missing, leaving
     * existing tabs and data untouched. Returns the titles of the tabs that were added.
     */
    suspend fun ensureLayout(spreadsheetId: String, mainCurrency: String, categories: List<Category>, year: Int): List<String> {
        val existing = sheets.get(spreadsheetId).sheets.map { it.properties.title }.toSet()
        val missing = listOf(SheetLayout.CATEGORIES, SheetLayout.SETTINGS, SheetLayout.yearTab(year)).filter { it !in existing }
        if (missing.isEmpty()) return missing
        val sheetIds = addSheets(spreadsheetId, missing)
        initializeTabs(spreadsheetId, sheetIds, mainCurrency, categories)
        return missing
    }

    /** Adds year tabs (header, formats, category dropdown); returns title → sheetId. */
    suspend fun addYearTabs(spreadsheetId: String, titles: List<String>): Map<String, Int> {
        if (titles.isEmpty()) return emptyMap()
        val sheetIds = addSheets(spreadsheetId, titles)
        initializeTabs(spreadsheetId, sheetIds, mainCurrency = "", categories = emptyList())
        return sheetIds
    }

        suspend fun readConfig(spreadsheetId: String): SheetConfig {
        val spreadsheet = sheets.get(spreadsheetId)
        val (categoryRows, settingRows) = sheets.batchGet(
            spreadsheetId,
            listOf(SheetLayout.range(SheetLayout.CATEGORIES, "A2:C"), SheetLayout.range(SheetLayout.SETTINGS, "A2:B")),
        ).map { it.values }
        val settings = parseSettings(settingRows)
        return SheetConfig(
            title = spreadsheet.properties.title,
            timeZone = spreadsheet.properties.timeZone,
            mainCurrency = settings["main_currency"]?.uppercase()?.takeIf { it.length == 3 },
            categories = parseCategories(categoryRows),
        )
    }

    private suspend fun addSheets(spreadsheetId: String, titles: List<String>): Map<String, Int> =
        sheets.batchUpdate(spreadsheetId, titles.map(SheetLayout::addSheet)).associate { reply ->
            val properties = reply["addSheet"]!!.jsonObject["properties"]!!.jsonObject
            properties["title"]!!.jsonPrimitive.content to properties["sheetId"]!!.jsonPrimitive.int
        }

        /** Writes headers and initial content into freshly added tabs ([sheetIds]: title → sheetId). */
    private suspend fun initializeTabs(
        spreadsheetId: String,
        sheetIds: Map<String, Int>,
        mainCurrency: String,
        categories: List<Category>,
    ) {
        val values = mutableListOf<ValueRange>()
        val formats = mutableListOf<kotlinx.serialization.json.JsonObject>()
        for ((title, sheetId) in sheetIds) {
            when {
                title == SheetLayout.CATEGORIES -> {
                    val rows = listOf(SheetLayout.headerRow(SheetLayout.categoriesHeader)) +
                        categories.map { listOf(JsonPrimitive(it.name), JsonPrimitive(it.emoji ?: ""), JsonPrimitive(it.archived)) }
                    values += ValueRange(range = SheetLayout.range(title, "A1"), values = rows)
                    formats += SheetLayout.categoriesFormat(sheetId)
                }
                title == SheetLayout.SETTINGS -> {
                    val rows = listOf(
                        SheetLayout.headerRow(SheetLayout.settingsHeader),
                        listOf(JsonPrimitive("schema_version"), JsonPrimitive(SheetLayout.SCHEMA_VERSION)),
                        listOf(JsonPrimitive("main_currency"), JsonPrimitive(mainCurrency)),
                    )
                    values += ValueRange(range = SheetLayout.range(title, "A1"), values = rows)
                    formats += SheetLayout.headerFormat(sheetId)
                }
                SheetLayout.isYearTab(title) -> {
                    values += ValueRange(range = SheetLayout.range(title, "A1"), values = listOf(SheetLayout.headerRow(SheetLayout.yearHeader)))
                    formats += SheetLayout.yearTabFormat(sheetId)
                }
            }
        }
        sheets.batchUpdateValues(spreadsheetId, values)
        sheets.batchUpdate(spreadsheetId, formats)
    }

    companion object {
        /** Settings tab rows → key/value map. */
        fun parseSettings(rows: List<List<JsonElement>>): Map<String, String> = rows.mapNotNull { row ->
            val key = row.getOrNull(0)?.text()?.trim()?.ifEmpty { null } ?: return@mapNotNull null
            key to (row.getOrNull(1)?.text()?.trim() ?: "")
        }.toMap()

                /** Categories tab rows → categories; skips blank names and case-insensitive duplicates. */
        fun parseCategories(rows: List<List<JsonElement>>): List<Category> {
            val seen = mutableSetOf<String>()
            return rows.mapNotNull { row ->
                val name = row.getOrNull(0)?.text()?.trim().orEmpty()
                if (name.isEmpty() || !seen.add(name.lowercase())) return@mapNotNull null
                Category(
                    name = name,
                    emoji = row.getOrNull(1)?.text()?.trim()?.ifEmpty { null },
                    archived = row.getOrNull(2)?.isTruthy() ?: false,
                )
            }
        }

        private fun JsonElement.text(): String? = (this as? JsonPrimitive)?.content

        private fun JsonElement.isTruthy(): Boolean {
            val p = this as? JsonPrimitive ?: return false
            return p.booleanOrNull ?: (p.content.trim().lowercase() in setOf("true", "yes", "x", "1", "tak"))
        }
    }
}
