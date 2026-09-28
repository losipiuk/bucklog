package net.osipiuk.bucklog.domain

import kotlin.math.pow
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** One past entry, as far as suggestions care. */
data class HistoryItem(val what: String, val category: String, val who: String, val timestamp: Instant)

data class Suggestion(val what: String, val category: String?)

/**
 * Autocomplete and category guessing from the family's history (SPEC §5.3).
 *
 * Every past entry adds `w_user × 0.5^(age / halfLife)` to the score of its normalized What text,
 * where `w_user` is 2 for my own entries. Built once per history change; queries are cheap.
 */
class SuggestionEngine(
    items: List<HistoryItem>,
    private val me: String,
    now: Instant,
    halfLifeDays: Double = 90.0,
) {
    private class Group(val normalized: String, var display: String, var displayAt: Instant) {
        var score = 0.0
        val categories = mutableMapOf<String, Double>()
        val words: List<String> = normalized.split(' ')
    }

    private val groups: List<Group>
    private val categoryScores = mutableMapOf<String, Double>()
    private val myLastCategory: String?

    init {
        val byText = mutableMapOf<String, Group>()
        var myLast: HistoryItem? = null
        for (item in items) {
            val weight = (if (item.who == me) 2.0 else 1.0) *
                0.5.pow(((now - item.timestamp) / 1.days).coerceAtLeast(0.0) / halfLifeDays)
            categoryScores[item.category] = (categoryScores[item.category] ?: 0.0) + weight
            if (item.who == me && (myLast == null || item.timestamp > myLast.timestamp)) myLast = item
            val normalized = normalizeText(item.what)
            if (normalized.isEmpty()) continue
            val group = byText.getOrPut(normalized) { Group(normalized, item.what.trim(), item.timestamp) }
            if (item.timestamp > group.displayAt) {
                group.display = item.what.trim()
                group.displayAt = item.timestamp
            }
            group.score += weight
            group.categories[item.category] = (group.categories[item.category] ?: 0.0) + weight
        }
        groups = byText.values.sortedByDescending { it.score }
        myLastCategory = myLast?.category
    }

    /** Best matches for what's been typed so far; the most frequent items when [query] is blank. */
    fun suggest(query: String, limit: Int = 6): List<Suggestion> {
        val q = normalizeText(query)
        val matches = if (q.isEmpty()) {
            groups.asSequence()
        } else {
            groups.asSequence()
                .filter { it.normalized != q && (it.normalized.startsWith(q) || it.words.any { w -> w.startsWith(q) }) }
                .sortedByDescending { if (it.normalized.startsWith(q)) it.score * 1.5 else it.score }
        }
        return matches.take(limit).map { Suggestion(it.display, it.categories.maxByOrNull { e -> e.value }?.key) }.toList()
    }

    /**
     * Most likely category for [what]: history (exact text, then shared words), then [hint]
     * (e.g. [CategoryHints] for never-seen products), then my last used category.
     */
    fun guessCategory(what: String, hint: (String) -> String? = { null }): String? =
        guessFromHistory(what) ?: hint(what) ?: myLastCategory

    private fun guessFromHistory(what: String): String? {
        val q = normalizeText(what)
        if (q.isEmpty()) return null
        groups.firstOrNull { it.normalized == q }?.let { exact ->
            return exact.categories.maxByOrNull { it.value }?.key
        }
        val tokens = q.split(' ').filter { it.length >= 3 }
        if (tokens.isEmpty()) return null
        val votes = mutableMapOf<String, Double>()
        for (group in groups) {
            if (tokens.any { t -> group.words.any { it.startsWith(t) } }) {
                group.categories.forEach { (c, s) -> votes[c] = (votes[c] ?: 0.0) + s }
            }
        }
        return votes.maxByOrNull { it.value }?.key
    }

    /** Category names ordered by weighted usage, most used first. */
    fun categoryRanking(): List<String> = categoryScores.entries.sortedByDescending { it.value }.map { it.key }
}
