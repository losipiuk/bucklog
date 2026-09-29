package net.osipiuk.bucklog.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.osipiuk.bucklog.domain.Category
import net.osipiuk.bucklog.domain.normalizeText
import net.osipiuk.bucklog.ui.AppIcons
import net.osipiuk.bucklog.ui.LocalExtraColors
import net.osipiuk.bucklog.ui.LocalStrings
import net.osipiuk.bucklog.ui.tabular

@Composable
fun DetailsStep(
    state: AddUiState,
    onBack: () -> Unit,
    onWhatChange: (String) -> Unit,
    onSuggestion: (net.osipiuk.bucklog.domain.Suggestion) -> Unit,
    onCategory: (String) -> Unit,
    onCreateCategory: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val haptics = LocalHapticFeedback.current
    var filter by remember { mutableStateOf("") }
    val s = LocalStrings.current
    // The field owns its text (the view-model state arrives a frame later and would fight the cursor).
    // A tapped suggestion replaces it with the cursor at the end.
    var whatField by remember { mutableStateOf(TextFieldValue(state.form.what, TextRange(state.form.what.length))) }
    val categoriesByName = state.allCategories.associateBy { it.name }
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    val add = {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        onAdd()
    }

    // Filtering: word-prefix match, case/diacritic-insensitive; archived ones only when typed for.
    val q = normalizeText(filter)
    val shown = if (q.isEmpty()) {
        state.categories
    } else {
        state.allCategories.filter { c -> normalizeText(c.name).let { n -> n.startsWith(q) || n.split(' ').any { it.startsWith(q) } } }
    }
    val canCreate = q.isNotEmpty() && state.allCategories.none { normalizeText(it.name) == q }
    fun choose(name: String) {
        onCategory(name)
        filter = ""
    }
    fun create() {
        onCreateCategory(filter)
        filter = ""
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, contentDescription = s.backToAmount) }
            Text(
                (if (state.form.refund) "${s.refund} +" else "") + "${state.amountText} ${state.currency}",
                style = MaterialTheme.typography.titleLarge.tabular,
                color = if (state.form.refund) LocalExtraColors.current.refund else MaterialTheme.colorScheme.onSurface,
            )
        }
        OutlinedTextField(
            value = whatField,
            onValueChange = {
                whatField = it
                onWhatChange(it.text)
            },
            placeholder = { Text(s.whatDidYouBuy) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (state.canAdd) add() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus),
        )
        // Suggestion strip, like a keyboard's: one row, most likely first.
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(state.suggestions, key = { it.what }) { suggestion ->
                val emoji = suggestion.category?.let(categoriesByName::get)?.emoji
                SuggestionChip(
                    onClick = {
                        whatField = TextFieldValue(suggestion.what, TextRange(suggestion.what.length))
                        onSuggestion(suggestion)
                    },
                    label = {
                        Text(
                            listOfNotNull(suggestion.what, emoji).joinToString("  "),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                placeholder = { Text(s.filterOrAddCategory) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    when {
                        shown.size == 1 -> choose(shown.single().name)
                        canCreate -> create()
                        shown.isNotEmpty() -> choose(shown.first().name)
                    }
                }),
                modifier = Modifier.fillMaxWidth(),
            )
            // 40dp touch targets instead of 48dp: fits all categories above the keyboard.
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (canCreate) {
                        AssistChip(
                            onClick = ::create,
                            label = { Text("＋ ${filter.trim()}", fontWeight = FontWeight.SemiBold) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                    shown.forEach { category ->
                        FilterChip(
                            selected = category.name == state.selectedCategory,
                            onClick = { choose(category.name) },
                            label = { Text(category.label()) },
                        )
                    }
                }
            }
        }
        state.form.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        }
        Button(
            onClick = add,
            enabled = state.canAdd,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp).height(56.dp),
        ) {
            val selected = state.selectedCategory?.let(categoriesByName::get)
            Text(
                (if (state.form.refund) s.addRefund else s.add) + (selected?.let { " · ${it.label()}" } ?: ""),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

fun Category.label(): String = emoji?.let { "$it $name" } ?: name
