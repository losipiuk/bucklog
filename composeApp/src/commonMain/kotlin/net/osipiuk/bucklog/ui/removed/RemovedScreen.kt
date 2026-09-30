package net.osipiuk.bucklog.ui.removed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import net.osipiuk.bucklog.domain.MoneyFormat
import net.osipiuk.bucklog.ui.AppGraph
import net.osipiuk.bucklog.ui.AppIcons
import net.osipiuk.bucklog.ui.LocalStrings
import net.osipiuk.bucklog.ui.Platform
import net.osipiuk.bucklog.ui.shortLabel
import net.osipiuk.bucklog.ui.tabular
import kotlin.math.abs

/** Expenses that disappeared from the sheet, with Restore (SPEC §6.5). */
@Composable
fun RemovedScreen(graph: AppGraph, platform: Platform, onBack: () -> Unit) {
    val s = LocalStrings.current
    val removed by remember { graph.store.removedEntries }.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    val money = remember { MoneyFormat() }
    val tz = remember { TimeZone.currentSystemDefault() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, contentDescription = s.back) }
            Text(s.recentlyRemoved, style = MaterialTheme.typography.titleLarge)
        }
        Text(
            s.recentlyRemovedHelp,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        if (removed.isEmpty()) {
            Text(s.nothingRemoved, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(removed, key = { it.entry.id }) { r ->
                val e = r.entry
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(e.what.ifEmpty { e.category }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(
                            (if (e.isRefund) "+" else "") + money.formatWithCode(abs(e.amountMinor), e.currency),
                            style = MaterialTheme.typography.bodyLarge.tabular,
                        )
                    }
                    Text(
                        listOf(e.category, e.who, e.timestamp.toLocalDateTime(tz).shortLabel(), s.removedOn(r.removedAt.toLocalDateTime(tz).shortLabel()))
                            .filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { scope.launch { graph.store.dismissRemoved(e.id) } }) { Text(s.dismiss) }
                        TextButton(onClick = {
                            scope.launch {
                                graph.store.restoreRemoved(r)
                                platform.requestSync()
                            }
                        }) { Text(s.restore) }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
