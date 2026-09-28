package net.osipiuk.bucklog.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.viewmodel.compose.viewModel
import net.osipiuk.bucklog.ui.add.AddExpenseScreen
import net.osipiuk.bucklog.ui.add.AddViewModel
import net.osipiuk.bucklog.ui.recent.RecentScreen
import net.osipiuk.bucklog.ui.setup.SetupScreen
import net.osipiuk.bucklog.ui.setup.SetupViewModel

/** Root: setup until configured, then straight into Add Expense (SPEC §5.2). */
@Composable
fun BucklogApp(graph: AppGraph, platform: Platform, dynamicScheme: ColorScheme? = null) {
    BucklogTheme(dynamicScheme) {
        Surface(Modifier.fillMaxSize()) {
            val config by graph.store.config.collectAsState(null)
            var showRecent by remember { mutableStateOf(false) }
            val current = config ?: return@Surface
            when {
                !current.isComplete -> SetupScreen(viewModel { SetupViewModel(graph, platform) })
                showRecent -> RecentScreen(graph, platform, onBack = { showRecent = false })
                else -> AddExpenseScreen(viewModel { AddViewModel(graph, platform) }, platform, onOpenRecent = { showRecent = true })
            }
        }
    }
}
