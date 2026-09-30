package net.osipiuk.bucklog.ui.add

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import net.osipiuk.bucklog.ui.AppGraph
import net.osipiuk.bucklog.ui.AppIcons
import net.osipiuk.bucklog.ui.Platform
import net.osipiuk.bucklog.ui.components.SyncProblemBanner

@Composable
fun AddExpenseScreen(
    vm: AddViewModel,
    graph: AppGraph,
    platform: Platform,
    onOpenHistory: (() -> Unit)?,
    onOpenMenu: (() -> Unit)?,
    // The full screen pads for the system bars; the quick-add panel only for the bottom ones.
    modifier: Modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
) {
    val state by vm.state.collectAsState()
    val sync by graph.store.syncStatus.collectAsState(null)
    val ui = state ?: return
    var pickingCurrency by remember { mutableStateOf(false) }


    platform.BackHandler(enabled = ui.form.step == AddStep.DETAILS, onBack = vm::back)

    Column(modifier) {
        if (ui.form.step == AddStep.AMOUNT && onOpenMenu != null) SyncProblemBanner(sync, graph, platform)
        AnimatedContent(
            targetState = ui.form.step,
            transitionSpec = {
                val forward = targetState == AddStep.DETAILS
                (slideInHorizontally { if (forward) it / 3 else -it / 3 } + fadeIn()) togetherWith
                    (slideOutHorizontally { if (forward) -it / 3 else it / 3 } + fadeOut())
            },
            label = "add-step",
        ) { step ->
            when (step) {
                AddStep.AMOUNT -> AmountStep(
                    state = ui,
                    onKey = vm::press,
                    onToggleRefund = vm::toggleRefund,
                    onCurrencyClick = { pickingCurrency = true },
                    onNext = vm::next,
                    onOpenHistory = onOpenHistory,
                    onOpenMenu = onOpenMenu,
                )
                AddStep.DETAILS -> DetailsStep(
                    state = ui,
                    onBack = vm::back,
                    onWhatChange = vm::onWhatChange,
                    onSuggestion = vm::pickSuggestion,
                    onCategory = vm::pickCategory,
                    onCreateCategory = vm::createCategory,
                    onAdd = vm::add,
                )
            }
        }
    }
    SuccessOverlay(visible = ui.form.done)
    if (pickingCurrency) {
        CurrencyPicker(
            recent = ui.recentCurrencies,
            onPick = { vm.selectCurrency(it); pickingCurrency = false },
            onDismiss = { pickingCurrency = false },
        )
    }
}

/** A green check that pops in after Add, just before the app closes. */
@Composable
private fun SuccessOverlay(visible: Boolean) {
    AnimatedVisibility(visible, enter = fadeIn(), modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
            val scale = remember { Animatable(0.4f) }
            LaunchedEffect(Unit) { scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
            Box(
                Modifier.size(112.dp).scale(scale.value).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(64.dp))
            }
        }
    }
}
