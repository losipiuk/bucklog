package net.osipiuk.bucklog.ui.invite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.osipiuk.bucklog.domain.Invite
import net.osipiuk.bucklog.domain.InviteLinks
import net.osipiuk.bucklog.ui.AppGraph
import net.osipiuk.bucklog.ui.Platform
import net.osipiuk.bucklog.ui.userMessage

data class InviteUiState(
    val sheetName: String = "",
    val email: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    /** Confirmation after the sheet was shared, e.g. "Shared with anna@…". */
    val info: String? = null,
)

/** Invite someone: share the sheet with their Google account, then send them the join link. */
class InviteViewModel(private val graph: AppGraph, private val platform: Platform) : ViewModel() {
    private val _state = MutableStateFlow(InviteUiState())
    val state: StateFlow<InviteUiState> = _state

    init {
        viewModelScope.launch { _state.update { it.copy(sheetName = graph.store.config.first().spreadsheetName.orEmpty()) } }
    }

    fun onEmailChange(email: String) = _state.update { it.copy(email = email, error = null, info = null) }

    fun shareAndInvite() {
        val email = state.value.email.trim()
        if (!EMAIL.matches(email)) {
            _state.update { it.copy(error = graph.strings.errEmail) }
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val message = inviteMessage()
                graph.drive.shareWith(graph.store.config.first().spreadsheetId!!, email, message)
                _state.update { it.copy(info = graph.strings.sharedWith(email)) }
                platform.shareText(message, graph.strings.sendInvite)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.userMessage(graph.strings)) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun justSendLink() = viewModelScope.launch { platform.shareText(inviteMessage(), graph.strings.sendInvite) }

    private suspend fun inviteMessage(): String {
        val config = graph.store.config.first()
        val invite = Invite(config.spreadsheetId!!, config.spreadsheetName.orEmpty(), config.myName.orEmpty())
        return graph.strings.inviteMessage(invite.sheetName, InviteLinks.page(invite))
    }

    private companion object {
        val EMAIL = Regex("""[^@\s]+@[^@\s]+\.[^@\s]+""")
    }
}
