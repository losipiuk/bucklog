package net.osipiuk.bucklog.ui.add

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.osipiuk.bucklog.domain.AmountKey
import net.osipiuk.bucklog.ui.AppIcons
import net.osipiuk.bucklog.ui.LocalExtraColors
import net.osipiuk.bucklog.ui.LocalStrings
import net.osipiuk.bucklog.ui.tabular

@Composable
fun AmountStep(
    state: AddUiState,
    onKey: (AmountKey) -> Unit,
    onToggleRefund: () -> Unit,
    onCurrencyClick: () -> Unit,
    onNext: () -> Unit,
    /** Null in the quick-add panel, which has no menu or History. */
    onOpenHistory: (() -> Unit)?,
    onOpenMenu: (() -> Unit)?,
) {
    val refund = state.form.refund
    val s = LocalStrings.current
    val amountColor = when {
        refund -> LocalExtraColors.current.refund
        state.form.amount.isZero -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            onOpenMenu?.let { IconButton(onClick = it) { Icon(AppIcons.Menu, contentDescription = s.menu) } }
            Text("Bucklog", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.weight(1f))
            onOpenHistory?.let { IconButton(onClick = it) { Icon(AppIcons.History, contentDescription = s.history) } }
        }
        Spacer(Modifier.weight(1f))
        if (refund) {
            Text(
                s.refund,
                color = amountColor,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
        BasicText(
            text = (if (refund) "+" else "") + state.amountText,
            style = MaterialTheme.typography.displayLarge.tabular.copy(
                color = amountColor,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 32.sp, maxFontSize = 76.sp),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = refund,
                onClick = onToggleRefund,
                label = { Text(s.refund) },
                leadingIcon = { Icon(AppIcons.Undo, contentDescription = null, Modifier.size(18.dp)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = LocalExtraColors.current.refund.copy(alpha = 0.18f),
                    selectedLabelColor = LocalExtraColors.current.refund,
                    selectedLeadingIconColor = LocalExtraColors.current.refund,
                ),
            )
            AssistChip(
                onClick = onCurrencyClick,
                label = { Text(state.currency, style = MaterialTheme.typography.titleMedium) },
            )
        }
        Spacer(Modifier.weight(1f))
        Keypad(onKey, Modifier.fillMaxWidth().weight(2.2f))
        Button(
            onClick = onNext,
            enabled = state.canContinue,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).height(64.dp),
        ) {
            Text(s.next, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.size(8.dp))
            Icon(AppIcons.ArrowForward, contentDescription = null)
        }
    }
}

private val keyRows = listOf(
    listOf(AmountKey.D1, AmountKey.D2, AmountKey.D3),
    listOf(AmountKey.D4, AmountKey.D5, AmountKey.D6),
    listOf(AmountKey.D7, AmountKey.D8, AmountKey.D9),
    listOf(AmountKey.D00, AmountKey.D0, AmountKey.BACKSPACE),
)

@Composable
private fun Keypad(onKey: (AmountKey) -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in keyRows) {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (key in row) Key(key, onKey)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.Key(key: AmountKey, onKey: (AmountKey) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val s = LocalStrings.current
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    onKey(key)
                },
                onLongClick = if (key == AmountKey.BACKSPACE) {
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onKey(AmountKey.CLEAR)
                    }
                } else {
                    null
                },
                onClickLabel = if (key == AmountKey.BACKSPACE) s.deleteDigit else null,
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (key) {
            AmountKey.BACKSPACE -> Icon(AppIcons.Backspace, contentDescription = s.delete, Modifier.size(28.dp))
            AmountKey.D00 -> KeyLabel("00")
            else -> KeyLabel(key.ordinal.toString())
        }
    }
}

@Composable
private fun KeyLabel(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium.tabular, fontWeight = FontWeight.Medium)
}
