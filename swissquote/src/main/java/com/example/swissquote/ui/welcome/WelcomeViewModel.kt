package com.example.swissquote.ui.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.swissquote.di.AppContainer
import com.example.swissquote.domain.repository.SessionRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WelcomeAction { OpenAccount, Login, Discover }

data class WelcomeUiState(
    /** Which button is currently loading (null = idle). */
    val pending: WelcomeAction? = null,
    val error: String? = null,
)

class WelcomeViewModel(
    private val session: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WelcomeUiState())
    val state: StateFlow<WelcomeUiState> = _state.asStateFlow()

    private val _done = Channel<Unit>(Channel.CONFLATED)
    /** One-shot event: navigate back to Trade. */
    val done: Flow<Unit> = _done.receiveAsFlow()

    fun onAction(action: WelcomeAction) {
        if (_state.value.pending != null) return // ignore double taps
        _state.update { it.copy(pending = action, error = null) }
        viewModelScope.launch {
            runCatching {
                when (action) {
                    WelcomeAction.OpenAccount -> session.openAccount()   // simulated sign-up
                    WelcomeAction.Login -> session.signIn()              // simulated login
                    WelcomeAction.Discover -> session.continueAsGuest()  // guest = no account
                }
            }.onSuccess {
                _state.update { it.copy(pending = null) }
                _done.send(Unit)
            }.onFailure { e ->
                _state.update { it.copy(pending = null, error = e.message ?: "Something went wrong") }
            }
        }
    }

    companion object {
        fun factory(c: AppContainer) = viewModelFactory {
            initializer { WelcomeViewModel(c.sessionRepository) }
        }
    }
}
