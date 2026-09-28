package net.osipiuk.bucklog.ui.recent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import net.osipiuk.bucklog.data.SyncStatus
import net.osipiuk.bucklog.domain.Entry
import net.osipiuk.bucklog.domain.EntryStatus
import net.osipiuk.bucklog.domain.MoneyFormat
import net.osipiuk.bucklog.ui.AppGraph
import net.osipiuk.bucklog.ui.AppIcons
import net.osipiuk.bucklog.ui.LocalExtraColors
import net.osipiuk.bucklog.ui.Platform
import net.osipiuk.bucklog.ui.tabular

/** Latest family entries and sync status. The full History with editing comes in M3. */
@Composable
fun RecentScreen(graph: AppGraph, platform: Platform, onBack: () -> Unit) {
    val entries by remember { graph.store.recentEntries(100) }.collectAsState(emptyList())
    val config by graph.store.config.collectAsState(null)
    val emojis by remember { graph.store.categories.map { list -> list.associate { it.name to it.emoji } } }.collectAsState(emptyMap())
    val sync by graph.store.syncStatus.collectAsState(null)
    val money = remember { MoneyFormat(decimalSeparator = platform.decimalSeparator) }
    val scope = rememberCoroutineScope()
    var confirmReset by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    platform.BackHandler(enabled = true, onBack = onBack)

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, contentDescription = "Back") }
            Text("Recent", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { confirmReset = true }) { Text("Reset app") }
        }
        config?.let {
            Text(
                "${it.myName} · ${it.accountEmail}\nSheet: ${it.spreadsheetName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                sync.describe(),
                style = MaterialTheme.typography.bodySmall,
                color = if (sync?.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                enabled = !syncing,
                onClick = {
                    syncing = true
                    scope.launch {
                        graph.sync.sync(force = true)
                        syncing = false
                    }
                },
            ) { Text(if (syncing) "Syncing…" else "Sync now") }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(entries, key = { it.id }) { entry ->
                EntryRow(entry, emojis[entry.category], money)
                HorizontalDivider()
            }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset app?") },
            text = { Text("Forgets your name, account and sheet, and deletes entries on this phone. The Google Sheet isn't touched.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    scope.launch {
                        graph.store.reset()
                        platform.finishWithMessage("Bucklog was reset")
                    }
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EntryRow(entry: Entry, emoji: String?, money: MoneyFormat) {
    val time = entry.timestamp.toLocalDateTime(TimeZone.currentSystemDefault())
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val invalid = entry.status == EntryStatus.INVALID
        // Fixed-width icon slot so descriptions line up whatever the emoji/letter width.
        Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) {
            Text(if (invalid) "⚠️" else emoji ?: entry.category.take(1), style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.weight(1f)) {
            Text(entry.what.ifEmpty { entry.category }, style = MaterialTheme.typography.bodyLarge)
            if (invalid) {
                Text("Can't read this row in the sheet. Fix it there.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Text(
                "${entry.category} · ${entry.who} · ${time.date} ${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!invalid) Text(
            (if (entry.isRefund) "+" else "") + money.formatWithCode(kotlin.math.abs(entry.amountMinor), entry.currency),
            style = MaterialTheme.typography.bodyLarge.tabular,
            color = if (entry.isRefund) LocalExtraColors.current.refund else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun SyncStatus?.describe(): String {
    if (this == null) return ""
    val last = lastSuccess?.toLocalDateTime(TimeZone.currentSystemDefault())?.let {
        "Synced ${it.date} ${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}"
    } ?: "Not synced yet"
    val pending = if (pendingChanges > 0) " · $pendingChanges waiting" else ""
    return error?.let { "Sync failed: $it$pending" } ?: "$last$pending"
}
