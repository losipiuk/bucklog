package net.osipiuk.bucklog.ui.add

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.osipiuk.bucklog.domain.Currencies
import net.osipiuk.bucklog.domain.Currency
import net.osipiuk.bucklog.domain.normalizeText
import net.osipiuk.bucklog.ui.LocalStrings

@Composable
fun CurrencyPicker(recent: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val q = normalizeText(query)
    val recentCurrencies = recent.map { Currencies.find(it) ?: Currency(it, 2, it) }
    val filtered = (if (q.isEmpty()) recentCurrencies + Currencies.all else Currencies.all)
        .distinctBy { it.code }
        .filter { q.isEmpty() || normalizeText(it.code).startsWith(q) || normalizeText(it.name).contains(q) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(LocalStrings.current.searchCurrency) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        LazyColumn {
            items(filtered, key = { it.code }) { currency ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(currency.code) }.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(currency.code, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(64.dp))
                    Text(currency.name, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
