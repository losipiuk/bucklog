package net.osipiuk.bucklog.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import net.osipiuk.bucklog.data.AppConfig
import net.osipiuk.bucklog.data.SyncStatus
import net.osipiuk.bucklog.domain.Category
import net.osipiuk.bucklog.domain.DemoData
import net.osipiuk.bucklog.domain.normalizeText
import net.osipiuk.bucklog.ui.AppGraph
import net.osipiuk.bucklog.ui.LANGUAGE_KEY
import net.osipiuk.bucklog.ui.Platform

data class SettingsUiState(val config: AppConfig, val categories: List<Category>, val sync: SyncStatus, val language: String)

class SettingsViewModel(private val graph: AppGraph, private val platform: Platform) : ViewModel() {
    val syncing = MutableStateFlow(false)
    val backingUp = MutableStateFlow(false)
    val backup = graph.backups.status

    fun setAutomaticBackups(enabled: Boolean) = viewModelScope.launch { graph.backups.setEnabled(enabled) }

    fun backUpNow() {
        if (backingUp.value) return
        backingUp.value = true
        viewModelScope.launch {
            runCatching { graph.backups.backUpNow() } // failures show up in the backup status
            backingUp.value = false
        }
    }

    val canAddQuickTile: Boolean get() = platform.canAddQuickTile

    fun addQuickTile() = platform.addQuickTile()

    fun openBackups(folderId: String) = platform.openUrl("https://drive.google.com/drive/folders/$folderId")

    val state: StateFlow<SettingsUiState?> = combine(
        graph.store.config, graph.store.categories, graph.store.syncStatus, graph.store.valueFlow(LANGUAGE_KEY),
    ) { config, categories, sync, language -> SettingsUiState(config, categories, sync, language ?: "system") }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** "system", "en" or "pl". */
    fun setLanguage(code: String) = viewModelScope.launch { graph.store.putValue(LANGUAGE_KEY, code) }

    /** How many expenses carry my current name (offered for renaming along with it). */
    suspend fun myPastEntries(): Long = graph.store.config.first().myName?.let { graph.store.countEntriesBy(it) } ?: 0

    /** Saves my new name; with [renamePast], my past expenses (and their sheet rows) follow. */
    fun saveName(name: String, renamePast: Boolean) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val old = graph.store.config.first().myName
            graph.store.updateConfig(myName = trimmed)
            if (renamePast && old != null && old != trimmed) {
                graph.store.renamePerson(old, trimmed)
                platform.requestSync()
            }
        }
    }

    fun syncNow() {
        if (syncing.value) return
        syncing.value = true
        viewModelScope.launch {
            graph.sync.sync(force = true)
            syncing.value = false
        }
    }

    fun openSheet() {
        state.value?.config?.spreadsheetId?.let { platform.openUrl("https://docs.google.com/spreadsheets/d/$it/edit") }
    }

    /** Validation message for saving [updated] over [oldName], or null if fine. */
    fun validate(oldName: String?, updated: Category): String? {
        val name = updated.name.trim()
        if (name.isEmpty()) return graph.strings.errEnterName
        val clash = state.value?.categories.orEmpty().any { it.name != oldName && normalizeText(it.name) == normalizeText(name) }
        return if (clash) graph.strings.errCategoryExists(name) else null
    }

    /** Saves a category edit ([oldName] set) or a new category. */
    fun saveCategory(oldName: String?, updated: Category) {
        val category = updated.copy(name = updated.name.trim().replace(Regex("\\s+"), " "), emoji = updated.emoji?.trim()?.ifEmpty { null })
        viewModelScope.launch {
            if (oldName == null) graph.store.addCategory(category) else graph.store.updateCategory(oldName, category)
            platform.requestSync()
        }
    }

    /** Debug builds: about six months of fake expenses, queued for upload like real ones. */
    fun addDemoData() = viewModelScope.launch {
        val config = graph.store.config.first()
        val tz = config.timeZone?.let { runCatching { TimeZone.of(it) }.getOrNull() } ?: TimeZone.currentSystemDefault()
        val categories = graph.store.categories.first().filter { !it.archived }.map { it.name }
        val entries = DemoData.generate(categories, listOf(config.myName.orEmpty(), graph.strings.demoPartner), graph.clock.now(), tz)
        graph.store.addEntries(entries)
        platform.requestSync()
    }

    val isDebugBuild: Boolean get() = platform.isDebugBuild

    fun switchSheet() = viewModelScope.launch { graph.store.forgetSheet() }


    suspend fun pendingChanges(): Long = graph.store.syncStatus.first().pendingChanges
}
