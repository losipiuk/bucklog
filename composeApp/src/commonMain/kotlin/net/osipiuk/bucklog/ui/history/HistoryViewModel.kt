package net.osipiuk.bucklog.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import net.osipiuk.bucklog.data.SyncStatus
import net.osipiuk.bucklog.domain.Entry
import net.osipiuk.bucklog.domain.Grouping
import net.osipiuk.bucklog.domain.History
import net.osipiuk.bucklog.domain.HistoryGroup
import net.osipiuk.bucklog.ui.AppGraph
import net.osipiuk.bucklog.ui.Platform

data class HistoryUiState(
    val groups: List<HistoryGroup>,
    val grouping: Grouping,
    val query: String,
    val categoryFilter: String?,
    val emojis: Map<String, String?>,
    val mainCurrency: String,
    val sync: SyncStatus?,
    val isEmpty: Boolean,
)

private data class Filters(val query: String, val category: String?, val grouping: Grouping)

class HistoryViewModel(private val graph: AppGraph, private val platform: Platform) : ViewModel() {
    private val query = MutableStateFlow("")
    private val category = MutableStateFlow<String?>(null)
    private val grouping = MutableStateFlow(Grouping.DAY)
    val refreshing = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            graph.store.value(GROUPING_KEY)?.let { saved -> Grouping.entries.firstOrNull { it.name == saved }?.let { grouping.value = it } }
        }
    }

    private val filters = combine(query, category, grouping, ::Filters)

    val state: StateFlow<HistoryUiState?> = combine(
        graph.store.allEntries, graph.store.categories, graph.store.config, filters, graph.store.syncStatus,
    ) { entries, categories, config, f, sync ->
        val tz = config.timeZone?.let { runCatching { TimeZone.of(it) }.getOrNull() } ?: TimeZone.currentSystemDefault()
        val filtered = History.filter(entries, f.query, f.category)
        HistoryUiState(
            groups = History.group(filtered, f.grouping, tz, config.mainCurrency),
            grouping = f.grouping,
            query = f.query,
            categoryFilter = f.category,
            emojis = categories.associate { it.name to it.emoji },
            mainCurrency = config.mainCurrency,
            sync = sync,
            isEmpty = entries.isEmpty(),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setQuery(text: String) { query.value = text }

    fun setCategoryFilter(name: String?) { category.value = name }

    fun setGrouping(value: Grouping) {
        grouping.value = value
        viewModelScope.launch { graph.store.putValue(GROUPING_KEY, value.name) }
    }

    fun refresh() {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            graph.sync.sync(force = true)
            refreshing.value = false
        }
    }

    fun restore(entry: Entry) {
        viewModelScope.launch {
            graph.store.restoreEntry(entry)
            platform.requestSync()
        }
    }

    private companion object {
        const val GROUPING_KEY = "ui_history_grouping"
    }
}
