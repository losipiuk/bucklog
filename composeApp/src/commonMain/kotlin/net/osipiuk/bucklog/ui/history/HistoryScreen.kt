package net.osipiuk.bucklog.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import net.osipiuk.bucklog.data.SyncStatus
import net.osipiuk.bucklog.domain.Currencies
import net.osipiuk.bucklog.domain.Entry
import net.osipiuk.bucklog.domain.EntryStatus
import net.osipiuk.bucklog.domain.Grouping
import net.osipiuk.bucklog.domain.History
import net.osipiuk.bucklog.domain.HistoryGroup
import net.osipiuk.bucklog.domain.MoneyFormat
import net.osipiuk.bucklog.ui.AppIcons
import net.osipiuk.bucklog.ui.LocalExtraColors
import net.osipiuk.bucklog.ui.Platform
import net.osipiuk.bucklog.ui.dayLabel
import net.osipiuk.bucklog.ui.label
import net.osipiuk.bucklog.ui.monthLabel
import net.osipiuk.bucklog.ui.shortLabel
import net.osipiuk.bucklog.ui.tabular
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.time.Clock

@Composable
fun HistoryScreen(
    vm: HistoryViewModel,
    platform: Platform,
    deleted: Entry?,
    onDeletedShown: () -> Unit,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onSettings: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    val money = remember { MoneyFormat(decimalSeparator = platform.decimalSeparator) }
    val tz = remember { TimeZone.currentSystemDefault() }
    val today = remember { Clock.System.todayIn(tz) }

    LaunchedEffect(deleted) {
        val entry = deleted ?: return@LaunchedEffect
        onDeletedShown()
        val result = snackbar.showSnackbar("Deleted “${entry.what.ifEmpty { entry.category }}”", actionLabel = "Undo", duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) vm.restore(entry)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, contentDescription = "Back") }
                Text("History", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onSettings) { Icon(AppIcons.Settings, contentDescription = "Settings") }
            }
            val ui = state ?: return@Column
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    // Local state, not ui.query: the filtered state arrives a frame later and would reset the cursor.
                    value = query,
                    onValueChange = { query = it; vm.setQuery(it) },
                    placeholder = { Text("Search") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    trailingIcon = if (query.isNotEmpty()) {
                        { IconButton(onClick = { query = ""; vm.setQuery("") }) { Icon(AppIcons.Close, contentDescription = "Clear search") } }
                    } else {
                        null
                    },
                    modifier = Modifier.weight(1f),
                )
                SingleChoiceSegmentedButtonRow(Modifier.padding(start = 8.dp)) {
                    Grouping.entries.forEachIndexed { i, g ->
                        SegmentedButton(
                            selected = ui.grouping == g,
                            onClick = { vm.setGrouping(g) },
                            shape = SegmentedButtonDefaults.itemShape(i, Grouping.entries.size),
                            icon = {},
                        ) { Text(if (g == Grouping.DAY) "Day" else "Month") }
                    }
                }
            }
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ui.sync.describe(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ui.sync?.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                )
                ui.categoryFilter?.let { name ->
                    InputChip(
                        selected = true,
                        onClick = { vm.setCategoryFilter(null) },
                        label = { Text(listOfNotNull(ui.emojis[name], name).joinToString(" ")) },
                        trailingIcon = { Icon(AppIcons.Close, contentDescription = "Clear filter", Modifier.size(16.dp)) },
                    )
                }
            }
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize()) {
                    if (ui.groups.isEmpty()) {
                        item {
                            Text(
                                if (ui.isEmpty) "No expenses yet." else "Nothing matches.",
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                            )
                        }
                    }
                    for (group in ui.groups) {
                        stickyHeader(key = "h-${group.start}") {
                            GroupHeader(group, ui, today, money, onCategory = vm::setCategoryFilter)
                        }
                        items(group.entries, key = { it.id }) { entry ->
                            EntryRow(entry, ui, money, tz, showDate = ui.grouping == Grouping.MONTH) { onEdit(entry.id) }
                            HorizontalDivider(Modifier.padding(start = 64.dp))
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun GroupHeader(
    group: HistoryGroup,
    ui: HistoryUiState,
    today: kotlinx.datetime.LocalDate,
    money: MoneyFormat,
    onCategory: (String) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (ui.grouping == Grouping.DAY) group.start.dayLabel(today) else group.start.monthLabel(),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    (if (group.approximate) "≈ " else "") + money.formatWithCode(group.total, ui.mainCurrency),
                    style = MaterialTheme.typography.titleSmall.tabular,
                )
            }
            if (ui.grouping == Grouping.MONTH && group.byCategory.size > 1 && ui.categoryFilter == null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((category, total) in group.byCategory) {
                        SuggestionChip(
                            onClick = { onCategory(category) },
                            label = { Text("${ui.emojis[category] ?: category} ${money.format(wholeUnits(total, ui.mainCurrency), 0)}", style = MaterialTheme.typography.labelMedium.tabular) },
                        )
                    }
                }
            }
        }
    }
}

private fun wholeUnits(minor: Long, currency: String): Long =
    (minor / 10.0.pow(Currencies.digits(currency))).roundToLong()

@Composable
private fun EntryRow(entry: Entry, ui: HistoryUiState, money: MoneyFormat, tz: TimeZone, showDate: Boolean, onClick: () -> Unit) {
    val invalid = entry.status == EntryStatus.INVALID
    val time = entry.timestamp.toLocalDateTime(tz)
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !invalid, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) {
            Text(if (invalid) "⚠️" else ui.emojis[entry.category] ?: entry.category.take(1).ifEmpty { "❓" }, style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.weight(1f)) {
            Text(entry.what.ifEmpty { entry.category }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val details = listOf(
                entry.category,
                entry.who,
                if (showDate) time.shortLabel() else time.time.label(),
            ).filter { it.isNotEmpty() }.joinToString(" · ")
            Text(
                if (invalid) "Can't read this row in the sheet. Fix it there." else details + if (entry.status == EntryStatus.PENDING) " · ⏳" else "",
                style = MaterialTheme.typography.bodySmall,
                color = if (invalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!invalid) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    (if (entry.isRefund) "+" else "") + money.formatWithCode(abs(entry.amountMinor), entry.currency),
                    style = MaterialTheme.typography.bodyLarge.tabular,
                    color = if (entry.isRefund) LocalExtraColors.current.refund else MaterialTheme.colorScheme.onSurface,
                )
                if (entry.currency != ui.mainCurrency) {
                    val converted = History.inMainCurrency(entry, ui.mainCurrency)
                    Text(
                        converted?.let { "≈ ${money.formatWithCode(abs(it), ui.mainCurrency)}" } ?: "rate pending",
                        style = MaterialTheme.typography.bodySmall.tabular,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

fun SyncStatus?.describe(): String {
    if (this == null) return ""
    val last = lastSuccess?.toLocalDateTime(TimeZone.currentSystemDefault())?.let { "Synced ${it.shortLabel()}" } ?: "Not synced yet"
    val pending = if (pendingChanges > 0) " · $pendingChanges waiting" else ""
    return error?.let { "Sync failed: $it$pending" } ?: "$last$pending"
}
