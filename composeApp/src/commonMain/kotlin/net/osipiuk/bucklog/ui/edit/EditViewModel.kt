package net.osipiuk.bucklog.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import net.osipiuk.bucklog.domain.Category
import net.osipiuk.bucklog.domain.Entry
import net.osipiuk.bucklog.domain.MoneyFormat
import net.osipiuk.bucklog.ui.AppGraph
import net.osipiuk.bucklog.ui.Platform
import net.osipiuk.bucklog.ui.userMessage
import kotlin.math.abs

data class EditForm(
    val original: Entry,
    val amount: String,
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

/** Edit or delete one entry (SPEC §5.4). Changes are queued and synced like new entries. */
class EditViewModel(
    private val graph: AppGraph,
    private val platform: Platform,
    private val entryId: String,
) : ViewModel() {
    private val money = MoneyFormat(decimalSeparator = platform.decimalSeparator)
    private val _form = MutableStateFlow<EditForm?>(null)

    /** Collected directly by text fields: it updates synchronously as the user types. */
    val form: StateFlow<EditForm?> = _form
    private var tz = TimeZone.currentSystemDefault()

    init {
        viewModelScope.launch {
            val config = graph.store.config.first()
            config.timeZone?.let { runCatching { TimeZone.of(it) }.getOrNull() }?.let { tz = it }
            val entry = graph.store.entry(entryId) ?: return@launch
            val at = entry.timestamp.toLocalDateTime(tz)
            _form.value = EditForm(
                original = entry,
                amount = money.formatPlain(abs(entry.amountMinor), entry.currency),
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

    fun update(change: EditForm.() -> EditForm) = _form.update { it?.change()?.copy(error = null) }

    fun save(onDone: () -> Unit) {
        val f = _form.value ?: return
        val minor = MoneyFormat.parse(f.amount, f.currency)
        val error = when {
            minor == null || minor == 0L -> "Enter an amount, e.g. 35,30"
            f.category.isBlank() -> "Choose a category"
            f.who.isBlank() -> "Who paid?"
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
                        amountMinor = if (f.refund) -minor!! else minor!!,
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
                _form.value = f.copy(saving = false, error = e.userMessage())
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
