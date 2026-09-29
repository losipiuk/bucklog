package net.osipiuk.bucklog.ui.add

import kotlinx.coroutines.delay
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.osipiuk.bucklog.domain.AmountInput
import net.osipiuk.bucklog.domain.AmountKey
import net.osipiuk.bucklog.domain.Category
import net.osipiuk.bucklog.domain.CategoryHints
import net.osipiuk.bucklog.domain.Entry
import net.osipiuk.bucklog.domain.EntryStatus
import net.osipiuk.bucklog.domain.MoneyFormat
import net.osipiuk.bucklog.domain.Suggestion
import net.osipiuk.bucklog.domain.SuggestionEngine
import net.osipiuk.bucklog.domain.newEntryId
import net.osipiuk.bucklog.domain.normalizeText
import net.osipiuk.bucklog.ui.AppGraph
import net.osipiuk.bucklog.ui.Platform
import net.osipiuk.bucklog.ui.userMessage

enum class AddStep { AMOUNT, DETAILS }

private const val SUCCESS_ANIMATION_MS = 450L

/** What the user has entered so far. */
data class AddForm(
    val step: AddStep = AddStep.AMOUNT,
    val amount: AmountInput = AmountInput(),
    val currency: String? = null,
    val refund: Boolean = false,
    val what: String = "",
    /** Category chosen by tapping a chip; overrides the guess until What changes via a suggestion. */
    val pickedCategory: String? = null,
    val saving: Boolean = false,
    /** Saved: the success animation plays, then the app closes. */
    val done: Boolean = false,
    val error: String? = null,
)

data class AddUiState(
    val form: AddForm,
    val currency: String,
    val amountText: String,
    val suggestions: List<Suggestion>,
    /** Active categories in sheet order (stable, for muscle memory), plus an explicitly picked archived one. */
    val categories: List<Category>,
    /** Every category including archived ones, for filtering. */
    val allCategories: List<Category>,
    val selectedCategory: String?,
    val recentCurrencies: List<String>,
) {
    val canContinue: Boolean get() = !form.amount.isZero
    val canAdd: Boolean get() = canContinue && selectedCategory != null && !form.saving
}

/** The main flow (SPEC §5.2): amount → what + category → Add closes the app. */
class AddViewModel(private val graph: AppGraph, private val platform: Platform) : ViewModel() {
    private val money = MoneyFormat(decimalSeparator = platform.decimalSeparator)
    private val form = MutableStateFlow(AddForm())

    private val engine = combine(graph.store.history, graph.store.config) { history, config ->
        SuggestionEngine(history, me = config.myName.orEmpty(), now = graph.clock.now())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SuggestionEngine(emptyList(), "", graph.clock.now()))

    private val recentCurrencies = graph.store.recentCurrencies(6)

    val state: StateFlow<AddUiState?> = combine(
        form, engine, graph.store.categories, graph.store.config, recentCurrencies,
    ) { f, engine, categories, config, recent ->
        val currency = f.currency ?: config.defaultCurrency
        val allNames = categories.map { it.name }.toSet()
        val activeNames = categories.filter { !it.archived }.map { it.name }
        val guess = engine.guessCategory(f.what) { CategoryHints.guess(it, activeNames) }?.takeIf { it in activeNames }
        val selected = f.pickedCategory?.takeIf { it in allNames } ?: guess
        // An archived category stays usable when picked explicitly (e.g. found via the filter).
        val shown = categories.filter { !it.archived || it.name == selected }
        AddUiState(
            form = f,
            currency = currency,
            amountText = money.format(f.amount.minorUnits, currency),
            suggestions = engine.suggest(f.what),
            categories = shown,
            allCategories = categories,
            selectedCategory = selected,
            recentCurrencies = (recent + config.mainCurrency).distinct(),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun press(key: AmountKey) = form.update { it.copy(amount = it.amount.press(key)) }

    fun toggleRefund() = form.update { it.copy(refund = !it.refund) }

    fun selectCurrency(code: String) = form.update { it.copy(currency = code) }

    fun next() = form.update { if (it.amount.isZero) it else it.copy(step = AddStep.DETAILS) }

    fun back() = form.update { it.copy(step = AddStep.AMOUNT) }

    fun onWhatChange(text: String) = form.update { it.copy(what = text, error = null) }

    fun pickSuggestion(suggestion: Suggestion) =
        form.update { it.copy(what = suggestion.what, pickedCategory = suggestion.category) }

    fun pickCategory(name: String) = form.update { it.copy(pickedCategory = name) }

    /** Creates a category typed in the category search (or picks the existing one with that name). */
    fun createCategory(rawName: String) {
        val name = rawName.trim().replace(Regex("\\s+"), " ")
        if (name.isEmpty()) return
        viewModelScope.launch {
            val existing = graph.store.categories.first().firstOrNull { normalizeText(it.name) == normalizeText(name) }
            if (existing != null) {
                pickCategory(existing.name)
                return@launch
            }
            graph.store.addCategory(Category(name, emoji = null))
            pickCategory(name)
            // Upload right away; if that fails it stays queued and the next sync retries.
            platform.requestSync()
        }
    }

    fun add() {
        val ui = state.value ?: return
        val category = ui.selectedCategory ?: return
        if (!ui.canAdd) return
        form.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val config = graph.store.config.first()
                val minor = ui.form.amount.minorUnits.let { if (ui.form.refund) -it else it }
                graph.store.addEntry(
                    Entry(
                        id = newEntryId(),
                        // Whole seconds: that's what survives a round trip through the sheet.
                        timestamp = Instant.fromEpochSeconds(graph.clock.now().epochSeconds),
                        who = config.myName.orEmpty(),
                        what = ui.form.what.trim(),
                        category = category,
                        amountMinor = minor,
                        currency = ui.currency,
                        rate = null,
                        status = EntryStatus.PENDING,
                    ),
                )
                graph.store.updateConfig(lastCurrency = ui.currency)
                platform.requestSync()
                val emoji = ui.categories.firstOrNull { it.name == category }?.emoji?.let { "$it " }.orEmpty()
                form.update { it.copy(done = true) }
                delay(SUCCESS_ANIMATION_MS)
                platform.finishWithMessage(
                    graph.strings.added(money.formatWithCode(ui.form.amount.minorUnits, ui.currency), "$emoji$category", ui.form.refund),
                )
                form.value = AddForm()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                form.update { it.copy(saving = false, error = e.userMessage(graph.strings)) }
            }
        }
    }
}
