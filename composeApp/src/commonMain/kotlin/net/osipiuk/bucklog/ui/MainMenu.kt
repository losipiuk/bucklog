package net.osipiuk.bucklog.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** The ☰ menu on the main screen: less frequent destinations, and room for more flows later. */
@Composable
fun MainMenu(graph: AppGraph, onHistory: () -> Unit, onSettings: () -> Unit, onOpenSheet: () -> Unit) {
    val s = LocalStrings.current
    val config by graph.store.config.collectAsState(null)
    ModalDrawerSheet {
        Column(Modifier.padding(horizontal = 28.dp, vertical = 20.dp)) {
            Text("Bucklog", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            config?.let {
                Text(
                    listOfNotNull(it.myName, it.spreadsheetName).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val item = Modifier.padding(horizontal = 12.dp)
        NavigationDrawerItem(
            label = { Text(s.history) }, selected = false, onClick = onHistory, modifier = item,
            icon = { Icon(AppIcons.History, contentDescription = null) },
        )
        NavigationDrawerItem(
            label = { Text(s.settings) }, selected = false, onClick = onSettings, modifier = item,
            icon = { Icon(AppIcons.Settings, contentDescription = null) },
        )
        HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
        NavigationDrawerItem(
            label = { Text(s.openSheet) }, selected = false, onClick = onOpenSheet, modifier = item,
            icon = { Icon(AppIcons.OpenInNew, contentDescription = null) },
        )
    }
}
