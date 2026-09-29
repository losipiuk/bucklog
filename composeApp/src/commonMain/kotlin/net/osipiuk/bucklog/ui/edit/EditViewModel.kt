package net.osipiuk.bucklog.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import net.osipiuk.bucklog.domain.AmountInput
import net.osipiuk.bucklog.domain.Category
import net.osipiuk.bucklog.domain.Entry
import net.osipiuk.bucklog.domain.MoneyFormat
import net.osipiuk.bucklog.ui.AppGraph
import net.osipiuk.bucklog.ui.Platform
import net.osipiuk.bucklog.ui.userMessage

data class EditForm(
    val original: Entry,
    /** Entered like on the Add keypad: digits only, the decimal point fixed by the currency. */
    val amount: AmountInput,
    val currency: String,
    val refund: Boolean,
    val what: String,
    val category: String,
    val who: String,
    val date: LocalDate,
    val time: LocalTime,
    val error: String? = null,
    val saving: Boolean = false,
)

data class EditUiState(val form: EditForm, val categories: List<Category>, val people: List<String>, val recentCurrencies: List<String>)

/** [amount] as shown in the field, e.g. "55,43" (with the platform's decimal separator). */
fun EditForm.amountText(money: MoneyFormat): String = money.format(amount.minorUnits, currency)

/** Edit or delete one entry (SPEC §5.4). Changes are queued and synced like new entries. */
class EditViewModel(
    private val graph: AppGraph,
    private val platform: Platform,
    private val entryId: String,
) : ViewModel() {
    val money = MoneyFormat()
    private val _form = MutableStateFlow<EditForm?>(null)

    /** Collected directly by text fields: it updates synchronously as the user types. */
    val form: StateFlow<EditForm?> = _form
    private var tz = TimeZone.currentSystemDefault()

    /**
     * (Re)loads the entry. Called every time the screen opens: the view model outlives the screen
     * (it's kept per entry), so a previous visit's state (e.g. "saving") must not carry over.
     */
    fun load() {
        _form.value = null
        viewModelScope.launch {
            val config = graph.store.config.first()
            config.timeZone?.let { runCatching { TimeZone.of(it) }.getOrNull() }?.let { tz = it }
            val entry = graph.store.entry(entryId) ?: return@launch
            val at = entry.timestamp.toLocalDateTime(tz)
            _form.value = EditForm(
                original = entry,
                amount = AmountInput(abs(entry.amountMinor).toString()),
                currency = entry.currency,
                refund = entry.isRefund,
                what = entry.what,
                category = entry.category,
                who = entry.who,
                date = at.date,
                time = LocalTime(at.hour, at.minute),
            )
        }
    }

    val state: StateFlow<EditUiState?> = combine(
        _form, graph.store.categories, graph.store.people, graph.store.recentCurrencies(6),
    ) { f, categories, people, currencies ->
        f ?: return@combine null
        EditUiState(
            form = f,
            // Active categories, plus the entry's own even if archived or unknown.
            categories = categories.filter { !it.archived || it.name == f.category } +
                listOfNotNull(Category(f.category, null).takeIf { c -> c.name.isNotEmpty() && categories.none { it.name == c.name } }),
            people = people,
            recentCurrencies = currencies,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The field's new text → digits typed so far (anything else, like the separator, is ignored). */
    fun onAmountText(text: String) = update {
        val digits = text.filter { it.isDigit() }.trimStart('0')
        copy(amount = if (digits.length > AmountInput.MAX_DIGITS) amount else AmountInput(digits))
    }

    fun update(change: EditForm.() -> EditForm) = _form.update { it?.change()?.copy(error = null) }

    fun save(onDone: () -> Unit) {
        val f = _form.value ?: return
        val minor = f.amount.minorUnits
        val s = graph.strings
        val error = when {
            minor == 0L -> s.errEnterAmount
            f.category.isBlank() -> s.errChooseCategory
            f.who.isBlank() -> s.errWhoPaid
            else -> null
        }
        if (error != null) {
            _form.value = f.copy(error = error)
            return
        }
        _form.value = f.copy(saving = true)
        viewModelScope.launch {
            try {
                val timestamp = LocalDateTime(f.date, f.time).toInstant(tz)
                val original = f.original
                val sameDay = original.timestamp.toLocalDateTime(tz).date == f.date
                graph.store.updateEntry(
                    original.copy(
                        timestamp = timestamp,
                        who = f.who.trim(),
                        what = f.what.trim(),
                        category = f.category,
                        amountMinor = if (f.refund) -minor else minor,
                        currency = f.currency,
                        // The rate belongs to the currency and day; fetch again if either changed.
                        rate = original.rate.takeIf { sameDay && f.currency == original.currency },
                    ),
                )
                platform.requestSync()
                onDone()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _form.value = f.copy(saving = false, error = e.userMessage(graph.strings))
            }
        }
    }

    fun delete(onDeleted: (Entry) -> Unit) {
        val f = _form.value ?: return
        viewModelScope.launch {
            graph.store.deleteEntry(f.original.id)
            platform.requestSync()
            onDeleted(f.original)
        }
    }
}
