package net.osipiuk.bucklog.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.osipiuk.bucklog.domain.Entry
import net.osipiuk.bucklog.ui.add.AddExpenseScreen
import net.osipiuk.bucklog.ui.add.AddViewModel
import net.osipiuk.bucklog.ui.edit.EditEntryScreen
import net.osipiuk.bucklog.ui.edit.EditViewModel
import net.osipiuk.bucklog.ui.history.HistoryScreen
import net.osipiuk.bucklog.ui.history.HistoryViewModel
import net.osipiuk.bucklog.ui.invite.InviteScreen
import net.osipiuk.bucklog.ui.invite.InviteViewModel
import net.osipiuk.bucklog.ui.settings.SettingsScreen
import net.osipiuk.bucklog.ui.settings.SettingsViewModel
import net.osipiuk.bucklog.ui.setup.SetupScreen
import net.osipiuk.bucklog.ui.setup.SetupViewModel

/** Settings key for the UI language: "system", "en" or "pl". */
const val LANGUAGE_KEY = "ui_language"

private sealed interface Screen {
    data object History : Screen
    data class Edit(val entryId: String) : Screen
    data object Settings : Screen
    data object Invite : Screen
}

/** Root: setup until configured, then straight into Add Expense (SPEC §5.2), with History/Edit/Settings on top. */
@Composable
fun BucklogApp(graph: AppGraph, platform: Platform, dynamicScheme: ColorScheme? = null) {
    val language by graph.store.valueFlow(LANGUAGE_KEY).collectAsState(null)
    val strings = stringsFor(language?.takeIf { it != "system" } ?: platform.language)
    graph.strings = strings
    CompositionLocalProvider(LocalStrings provides strings) {
        BucklogTheme(dynamicScheme) {
            Surface(Modifier.fillMaxSize()) {
                val config by graph.store.config.collectAsState(null)
                val current = config ?: return@Surface
                if (!current.isComplete) {
                    SetupScreen(viewModel { SetupViewModel(graph, platform) })
                } else {
                    Screens(graph, platform)
                    InvitePrompt(graph, current.spreadsheetId, current.spreadsheetName.orEmpty())
                }
            }
        }
    }
}

@Composable
private fun Screens(graph: AppGraph, platform: Platform) {
    var stack by remember { mutableStateOf(emptyList<Screen>()) }
    var deleted by remember { mutableStateOf<Entry?>(null) }
    fun push(screen: Screen) { stack = stack + screen }
    fun pop() { stack = stack.dropLast(1) }

    platform.BackHandler(enabled = stack.isNotEmpty(), onBack = ::pop)
    val padding = Modifier.fillMaxSize().safeDrawingPadding().imePadding()
    when (val top = stack.lastOrNull()) {
        null -> {
            val drawer = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            fun go(action: () -> Unit) {
                scope.launch { drawer.close() }
                action()
            }
            platform.BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
            ModalNavigationDrawer(
                drawerState = drawer,
                // Only the ☰ button opens it: an edge swipe would fight the keypad and the back gesture.
                gesturesEnabled = drawer.isOpen,
                drawerContent = {
                    MainMenu(
                        graph,
                        onHistory = { go { push(Screen.History) } },
                        onSettings = { go { push(Screen.Settings) } },
                        onInvite = { go { push(Screen.Invite) } },
                        onOpenSheet = {
                            go {
                                scope.launch {
                                    graph.store.config.first().spreadsheetId?.let { platform.openUrl("https://docs.google.com/spreadsheets/d/$it/edit") }
                                }
                            }
                        },
                    )
                },
            ) {
                AddExpenseScreen(
                    viewModel { AddViewModel(graph, platform) }, graph, platform,
                    onOpenHistory = { push(Screen.History) },
                    onOpenMenu = { scope.launch { drawer.open() } },
                )
            }
        }
        Screen.History -> Surface(padding) {
            HistoryScreen(
                vm = viewModel { HistoryViewModel(graph, platform) },
                graph = graph,
                platform = platform,
                deleted = deleted,
                onDeletedShown = { deleted = null },
                onBack = ::pop,
                onEdit = { push(Screen.Edit(it)) },
            )
        }
        is Screen.Edit -> Surface(padding) {
            EditEntryScreen(
                vm = viewModel(key = "edit-${top.entryId}") { EditViewModel(graph, platform, top.entryId) },
                onClose = ::pop,
                onDeleted = { entry -> deleted = entry; pop() },
            )
        }
        Screen.Settings -> Surface(padding) { SettingsScreen(viewModel { SettingsViewModel(graph, platform) }, graph, platform, onBack = ::pop) }
        Screen.Invite -> Surface(padding) { InviteScreen(viewModel { InviteViewModel(graph, platform) }, onBack = ::pop) }
    }
}

/**
 * An invite opened on a phone that's already set up: nothing to do for the same sheet, otherwise
 * ask before switching (switching drops the local copy and continues in setup with the invite).
 */
@Composable
private fun InvitePrompt(graph: AppGraph, currentSheet: String?, currentName: String) {
    val invite by graph.pendingInvite.collectAsState(null)
    val pending = invite ?: return
    val scope = rememberCoroutineScope()
    if (pending.spreadsheetId == currentSheet) {
        LaunchedEffect(pending) { graph.clearInvite() }
        return
    }
    val s = LocalStrings.current
    AlertDialog(
        onDismissRequest = { scope.launch { graph.clearInvite() } },
        title = { Text(s.switchToInvitedQuestion(pending.sheetName)) },
        text = { Text(s.switchToInvitedText(currentName)) },
        confirmButton = { TextButton(onClick = { scope.launch { graph.store.forgetSheet() } }) { Text(s.continueLabel) } },
        dismissButton = { TextButton(onClick = { scope.launch { graph.clearInvite() } }) { Text(s.cancel) } },
    )
}
