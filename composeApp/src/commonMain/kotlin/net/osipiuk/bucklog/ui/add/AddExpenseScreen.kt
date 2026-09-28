package net.osipiuk.bucklog.ui.add

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import net.osipiuk.bucklog.ui.Platform

@Composable
fun AddExpenseScreen(vm: AddViewModel, platform: Platform, onOpenRecent: () -> Unit) {
    val state by vm.state.collectAsState()
    val ui = state ?: return
    var pickingCurrency by remember { mutableStateOf(false) }


    platform.BackHandler(enabled = ui.form.step == AddStep.DETAILS, onBack = vm::back)

    Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
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
                    onOpenRecent = onOpenRecent,
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
    if (pickingCurrency) {
        CurrencyPicker(
            recent = ui.recentCurrencies,
            onPick = { vm.selectCurrency(it); pickingCurrency = false },
            onDismiss = { pickingCurrency = false },
        )
    }
}
