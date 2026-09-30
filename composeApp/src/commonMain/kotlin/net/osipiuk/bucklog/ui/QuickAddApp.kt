package net.osipiuk.bucklog.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import net.osipiuk.bucklog.ui.add.AddExpenseScreen
import net.osipiuk.bucklog.ui.add.AddViewModel

/**
 * Quick add (M5): the add flow in a bottom panel over whatever app is open, opened from the
 * Quick Settings tile and other shortcuts. [onDismiss] closes it (tap outside, or cancel);
 * adding an expense closes it through the usual Platform.finishWithMessage.
 * [onNeedsSetup] runs instead when Bucklog isn't set up yet (the full app handles that).
 */
@Composable
fun QuickAddApp(
    graph: AppGraph,
    platform: Platform,
    dynamicScheme: ColorScheme?,
    onDismiss: () -> Unit,
    onNeedsSetup: () -> Unit,
) {
    val language by graph.store.valueFlow(LANGUAGE_KEY).collectAsState(null)
    val strings = stringsFor(language?.takeIf { it != "system" } ?: platform.language)
    graph.strings = strings
    val config by graph.store.config.collectAsState(null)
    val current = config ?: return
    if (!current.isComplete) {
        LaunchedEffect(Unit) { onNeedsSetup() }
        return
    }
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    CompositionLocalProvider(LocalStrings provides strings) {
        BucklogTheme(dynamicScheme) {
            Box(Modifier.fillMaxSize()) {
                AnimatedVisibility(shown, enter = fadeIn(), exit = fadeOut()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
                    )
                }
                AnimatedVisibility(
                    shown,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it },
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.86f),
                    ) {
                        Column {
                            Box(
                                Modifier
                                    .padding(top = 10.dp, bottom = 2.dp)
                                    .size(width = 36.dp, height = 4.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp))
                                    .align(Alignment.CenterHorizontally),
                            )
                            AddExpenseScreen(
                                viewModel { AddViewModel(graph, platform) },
                                graph,
                                platform,
                                onOpenHistory = null,
                                onOpenMenu = null,
                                modifier = Modifier.fillMaxSize().navigationBarsPadding().imePadding(),
                            )
                        }
                    }
                }
            }
        }
    }
}
