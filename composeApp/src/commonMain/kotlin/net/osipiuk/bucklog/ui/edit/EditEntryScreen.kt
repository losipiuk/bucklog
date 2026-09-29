package net.osipiuk.bucklog.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import net.osipiuk.bucklog.ui.AppIcons
import net.osipiuk.bucklog.ui.LocalExtraColors
import net.osipiuk.bucklog.ui.LocalStrings
import net.osipiuk.bucklog.ui.add.CurrencyPicker
import net.osipiuk.bucklog.ui.add.label
import net.osipiuk.bucklog.ui.label
import net.osipiuk.bucklog.ui.tabular

private const val MILLIS_PER_DAY = 86_400_000L

@Composable
fun EditEntryScreen(vm: EditViewModel, onClose: () -> Unit, onDeleted: (net.osipiuk.bucklog.domain.Entry) -> Unit) {
    LaunchedEffect(Unit) { vm.load() }
    val state by vm.state.collectAsState()
    val form by vm.form.collectAsState()
    val ui = state ?: return
    val f = form ?: return
    val s = LocalStrings.current
    var pickingCurrency by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(AppIcons.Close, contentDescription = s.cancel) }
            Text(s.editExpense, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.delete(onDeleted) }) { Text(s.delete, color = MaterialTheme.colorScheme.error) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    // Like the Add keypad: digits shift in from the right; the cursor stays at the end.
                    value = f.amountText(vm.money).let { TextFieldValue(it, TextRange(it.length)) },
                    onValueChange = { v -> vm.onAmountText(v.text) },
                    label = { Text(s.amount) },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.tabular,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.weight(1f),
                )
                AssistChip(onClick = { pickingCurrency = true }, label = { Text(f.currency, style = MaterialTheme.typography.titleMedium) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s.refund,
                    color = if (f.refund) LocalExtraColors.current.refund else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = f.refund, onCheckedChange = { v -> vm.update { copy(refund = v) } })
            }
            OutlinedTextField(
                value = f.what,
                onValueChange = { v -> vm.update { copy(what = v) } },
                label = { Text(s.what) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(s.category, style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ui.categories.forEach { c ->
                    FilterChip(selected = c.name == f.category, onClick = { vm.update { copy(category = c.name) } }, label = { Text(c.label()) })
                }
            }
            // Pick-only: the Who column should only ever hold names the family already uses.
            Text(s.who, style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ui.people.forEach { p -> FilterChip(selected = p == f.who, onClick = { vm.update { copy(who = p) } }, label = { Text(p) }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.weight(1f)) { Text(f.date.label()) }
                OutlinedButton(onClick = { pickingTime = true }, modifier = Modifier.weight(1f)) {
                    Text("${f.time.hour.toString().padStart(2, '0')}:${f.time.minute.toString().padStart(2, '0')}")
                }
            }
            f.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        Button(
            onClick = { vm.save(onClose) },
            enabled = !f.saving,
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp),
        ) { Text(s.save, style = MaterialTheme.typography.titleLarge) }
    }

    if (pickingCurrency) {
        CurrencyPicker(
            recent = ui.recentCurrencies,
            onPick = { c -> vm.update { copy(currency = c) }; pickingCurrency = false },
            onDismiss = { pickingCurrency = false },
        )
    }
    if (pickingDate) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = f.date.toEpochDays() * MILLIS_PER_DAY)
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { ms -> vm.update { copy(date = LocalDate.fromEpochDays(ms / MILLIS_PER_DAY)) } }
                    pickingDate = false
                }) { Text(s.ok) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text(s.cancel) } },
        ) { DatePicker(picker) }
    }
    if (pickingTime) {
        val picker = rememberTimePickerState(initialHour = f.time.hour, initialMinute = f.time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            confirmButton = {
                TextButton(onClick = {
                    vm.update { copy(time = LocalTime(picker.hour, picker.minute)) }
                    pickingTime = false
                }) { Text(s.ok) }
            },
            dismissButton = { TextButton(onClick = { pickingTime = false }) { Text(s.cancel) } },
            text = { TimePicker(picker) },
        )
    }
}
