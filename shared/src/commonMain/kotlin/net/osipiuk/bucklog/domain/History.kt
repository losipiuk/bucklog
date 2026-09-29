package net.osipiuk.bucklog.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.pow
import kotlin.math.roundToLong

enum class Grouping { DAY, MONTH }

/** Entries of one day or month, newest first, with totals in the main currency. */
data class HistoryGroup(
    /** The day, or the 1st of the month. */
    val start: LocalDate,
    val entries: List<Entry>,
    /** Sum in main-currency minor units; refunds subtract. Entries still waiting for a rate are left out. */
    val total: Long,
    /** True when some entry had no exchange rate yet, so [total] is incomplete. */
    val approximate: Boolean,
    /** Category → total, largest first. */
    val byCategory: List<Pair<String, Long>>,
)

object History {
    /** Amount in main-currency minor units, or null when the exchange rate isn't known yet. */
    fun inMainCurrency(entry: Entry, mainCurrency: String): Long? {
        if (entry.currency == mainCurrency) return entry.amountMinor
        val rate = entry.rate?.toDoubleOrNull() ?: return null
        val shift = Currencies.digits(mainCurrency) - Currencies.digits(entry.currency)
        return (entry.amountMinor * rate * 10.0.pow(shift)).roundToLong()
    }

    /** Case/diacritic-insensitive search in What, Category and Who; [category] narrows to one category. */
    fun filter(entries: List<Entry>, query: String, category: String? = null): List<Entry> {
        val q = normalizeText(query)
        return entries.filter { e ->
            (category == null || e.category == category) &&
                (q.isEmpty() || listOf(e.what, e.category, e.who).any { normalizeText(it).contains(q) })
        }
    }

    fun group(entries: List<Entry>, grouping: Grouping, tz: TimeZone, mainCurrency: String): List<HistoryGroup> =
        entries.sortedByDescending { it.timestamp }
            .groupBy { e ->
                val date = e.timestamp.toLocalDateTime(tz).date
                if (grouping == Grouping.DAY) date else LocalDate(date.year, date.month, 1)
            }
            .map { (start, list) ->
                val valid = list.filter { it.status != EntryStatus.INVALID }
                val converted = valid.map { it to inMainCurrency(it, mainCurrency) }
                HistoryGroup(
                    start = start,
                    entries = list,
                    total = converted.sumOf { it.second ?: 0L },
                    approximate = converted.any { it.second == null },
                    byCategory = converted.filter { it.second != null }
                        .groupBy({ it.first.category }, { it.second!! })
                        .mapValues { it.value.sum() }
                        .filter { it.value != 0L }
                        .entries.sortedByDescending { it.value }
                        .map { it.key to it.value },
                )
            }
}
