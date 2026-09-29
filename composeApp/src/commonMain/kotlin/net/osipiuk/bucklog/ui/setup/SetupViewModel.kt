package net.osipiuk.bucklog.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import net.osipiuk.bucklog.domain.DefaultCategories
import net.osipiuk.bucklog.domain.Invite
import net.osipiuk.bucklog.google.parseSpreadsheetId
import net.osipiuk.bucklog.ui.AppGraph
import net.osipiuk.bucklog.ui.Platform
import net.osipiuk.bucklog.ui.userMessage

enum class SetupStep { WELCOME, SHEET, NAME }

data class SetupUiState(
    val step: SetupStep = SetupStep.WELCOME,
    val busy: Boolean = false,
    val error: String? = null,
    val account: String? = null,
    val sheetName: String? = null,
    val link: String = "",
    val name: String = "",
    /** Set when this phone was opened from an invite link: setup offers to join that sheet. */
    val invite: Invite? = null,
)

/** First-run flow (SPEC §5.1): Google account → family sheet → your name. */
class SetupViewModel(private val graph: AppGraph, private val platform: Platform) : ViewModel() {
    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state

    init {
        viewModelScope.launch { graph.pendingInvite.collect { invite -> _state.update { it.copy(invite = invite) } } }
    }

    /** Picks up where setup stands in the stored config (after a restart, switching sheets or signing out). */
    fun resume() {
        viewModelScope.launch {
            val config = graph.store.config.first()
            _state.update {
                it.copy(
                    step = when {
                        config.spreadsheetId != null -> SetupStep.NAME
                        config.accountEmail != null -> SetupStep.SHEET
                        else -> SetupStep.WELCOME
                    },
                    account = config.accountEmail,
                    sheetName = config.spreadsheetName,
                    name = config.myName.orEmpty(),
                )
            }
            if (config.accountEmail != null && config.myName == null) prefillName()
        }
    }

    private suspend fun prefillName() {
        val firstName = runCatching { graph.drive.currentUser().displayName }.getOrNull()?.substringBefore(' ') ?: return
        _state.update { it.copy(name = it.name.ifEmpty { firstName }) }
    }

    fun signIn() = work {
        val email = platform.signIn() ?: return@work
        graph.store.updateConfig(accountEmail = email)
        _state.update { it.copy(step = SetupStep.SHEET, account = email) }
        prefillName()
    }

    fun onLinkChange(link: String) = _state.update { it.copy(link = link, error = null) }

    fun onNameChange(name: String) = _state.update { it.copy(name = name) }

    fun createSheet() = work {
        val created = graph.setup.createFamilySheet(
            title = "Bucklog",
            mainCurrency = platform.deviceCurrency,
            timeZone = platform.timeZoneId,
            locale = platform.sheetLocale,
            categories = DefaultCategories.forLanguage(platform.contentLanguage),
            year = currentYear(),
        )
        // Whoever creates the sheet backs it up by default; others can turn it on in Settings.
        graph.backups.setEnabled(true)
        load(created.spreadsheetId!!)
    }

    fun pickSheet() = work {
        val picked = platform.pickSheet(parseSpreadsheetId(state.value.link)) ?: return@work
        graph.setup.ensureLayout(
            picked.id,
            mainCurrency = platform.deviceCurrency,
            categories = DefaultCategories.forLanguage(platform.contentLanguage),
            year = currentYear(),
        )
        load(picked.id)
    }

    /** Picks the invited sheet (the Picker shows just that file) and connects to it. */
    fun joinInvite() = work {
        val invite = state.value.invite ?: return@work
        val picked = platform.pickSheet(invite.spreadsheetId) ?: return@work
        graph.setup.ensureLayout(
            picked.id,
            mainCurrency = platform.deviceCurrency,
            categories = DefaultCategories.forLanguage(platform.contentLanguage),
            year = currentYear(),
        )
        load(picked.id)
        graph.clearInvite()
    }

    fun useOtherAccount() = _state.update { it.copy(step = SetupStep.WELCOME, error = null) }

    fun chooseOtherSheet() = _state.update { it.copy(step = SetupStep.SHEET, error = null) }

    fun finish() = work {
        val name = state.value.name.trim()
        if (name.isEmpty()) return@work
        graph.store.updateConfig(myName = name)
        // First pull brings in the family's existing entries (and history for suggestions).
        platform.requestSync()
    }

    private suspend fun load(spreadsheetId: String) {
        graph.connectSheet(spreadsheetId, fallbackCurrency = platform.deviceCurrency)
        _state.update { it.copy(step = SetupStep.NAME, sheetName = graph.store.config.first().spreadsheetName) }
    }

    private fun currentYear() = graph.clock.todayIn(TimeZone.currentSystemDefault()).year

    private fun work(block: suspend () -> Unit) {
        if (state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.userMessage(graph.strings)) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
