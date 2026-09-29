package net.osipiuk.bucklog.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import net.osipiuk.bucklog.data.SyncErrorKind
import net.osipiuk.bucklog.data.SyncStatus
import net.osipiuk.bucklog.domain.DefaultCategories
import net.osipiuk.bucklog.ui.AppGraph
import net.osipiuk.bucklog.ui.LocalStrings
import net.osipiuk.bucklog.ui.Platform

/**
 * Shown when sync can't fix itself: Google authorization expired, or the sheet can't be accessed.
 * Offline and other transient errors don't get a banner (sync retries on its own).
 */
@Composable
fun SyncProblemBanner(status: SyncStatus?, graph: AppGraph, platform: Platform, modifier: Modifier = Modifier) {
    val kind = status?.errorKind?.takeIf { it == SyncErrorKind.AUTH || it == SyncErrorKind.ACCESS } ?: return
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (kind == SyncErrorKind.AUTH) s.bannerSignIn else s.bannerAccess,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            if (kind == SyncErrorKind.AUTH) signInAgain(graph, platform) else chooseSheetAgain(graph, platform)
                        } finally {
                            busy = false
                        }
                    }
                },
            ) { Text(if (kind == SyncErrorKind.AUTH) s.bannerSignInAction else s.bannerAccessAction) }
        }
    }
}

private suspend fun signInAgain(graph: AppGraph, platform: Platform) {
    val email = graph.store.config.first().accountEmail ?: return
    if (platform.reauthorize(email)) graph.sync.sync(force = true)
}

/** Picking the same sheet restores access; picking another one switches to it. */
private suspend fun chooseSheetAgain(graph: AppGraph, platform: Platform) {
    val config = graph.store.config.first()
    val picked = platform.pickSheet(preselectFileId = config.spreadsheetId) ?: return
    if (picked.id != config.spreadsheetId) {
        graph.store.forgetSheet()
        graph.setup.ensureLayout(picked.id, config.mainCurrency, DefaultCategories.forLanguage(platform.contentLanguage), currentYear(graph))
        graph.connectSheet(picked.id, fallbackCurrency = config.mainCurrency)
    }
    graph.sync.sync(force = true)
}

private fun currentYear(graph: AppGraph) = graph.clock.todayIn(TimeZone.currentSystemDefault()).year
