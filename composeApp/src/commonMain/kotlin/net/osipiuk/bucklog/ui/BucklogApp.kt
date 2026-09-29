package net.osipiuk.bucklog.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import net.osipiuk.bucklog.domain.Entry
import net.osipiuk.bucklog.ui.add.AddExpenseScreen
import net.osipiuk.bucklog.ui.add.AddViewModel
import net.osipiuk.bucklog.ui.edit.EditEntryScreen
import net.osipiuk.bucklog.ui.edit.EditViewModel
import net.osipiuk.bucklog.ui.history.HistoryScreen
import net.osipiuk.bucklog.ui.history.HistoryViewModel
import net.osipiuk.bucklog.ui.settings.SettingsScreen
import net.osipiuk.bucklog.ui.settings.SettingsViewModel
import net.osipiuk.bucklog.ui.setup.SetupScreen
import net.osipiuk.bucklog.ui.setup.SetupViewModel

private sealed interface Screen {
    data object History : Screen
    data class Edit(val entryId: String) : Screen
    data object Settings : Screen
}

/** Root: setup until configured, then straight into Add Expense (SPEC §5.2), with History/Edit/Settings on top. */
@Composable
fun BucklogApp(graph: AppGraph, platform: Platform, dynamicScheme: ColorScheme? = null) {
    BucklogTheme(dynamicScheme) {
        Surface(Modifier.fillMaxSize()) {
            val config by graph.store.config.collectAsState(null)
            var stack by remember { mutableStateOf(emptyList<Screen>()) }
            var deleted by remember { mutableStateOf<Entry?>(null) }
            val current = config ?: return@Surface
            fun push(screen: Screen) { stack = stack + screen }
            fun pop() { stack = stack.dropLast(1) }

            if (!current.isComplete) {
                stack = emptyList()
                SetupScreen(viewModel { SetupViewModel(graph, platform) })
                return@Surface
            }
            platform.BackHandler(enabled = stack.isNotEmpty(), onBack = ::pop)
            val padding = Modifier.fillMaxSize().safeDrawingPadding().imePadding()
            when (val top = stack.lastOrNull()) {
                null -> AddExpenseScreen(viewModel { AddViewModel(graph, platform) }, platform, onOpenHistory = { push(Screen.History) })
                Screen.History -> Surface(padding) {
                    HistoryScreen(
                        vm = viewModel { HistoryViewModel(graph, platform) },
                        platform = platform,
                        deleted = deleted,
                        onDeletedShown = { deleted = null },
                        onBack = ::pop,
                        onEdit = { push(Screen.Edit(it)) },
                        onSettings = { push(Screen.Settings) },
                    )
                }
                is Screen.Edit -> Surface(padding) {
                    EditEntryScreen(
                        vm = viewModel(key = "edit-${top.entryId}") { EditViewModel(graph, platform, top.entryId) },
                        onClose = ::pop,
                        onDeleted = { entry -> deleted = entry; pop() },
                    )
                }
                Screen.Settings -> Surface(padding) { SettingsScreen(viewModel { SettingsViewModel(graph, platform) }, onBack = ::pop) }
            }
        }
    }
}
