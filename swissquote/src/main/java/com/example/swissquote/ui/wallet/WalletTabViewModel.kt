package com.example.swissquote.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.contract.ProvisionResult
import com.example.swissquote.di.AppContainer
import com.example.swissquote.domain.repository.SessionRepository
import com.example.swissquote.domain.repository.SwissquoteGuardian
import com.example.swissquote.domain.repository.WalletAppLauncher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The 4 states of the Wallet tab designed in Figma. */
sealed interface WalletTabUiState {
    data object Loading : WalletTabUiState
    /** No Swissquote account → ask to sign up (no "generate" button). */
    data object NoAccount : WalletTabUiState
    /** Client without wallet → "Generate your wallet". */
    data class Generate(val error: String? = null) : WalletTabUiState
    /** Generation running; [completedSteps] in 0..3. */
    data class Generating(val completedSteps: Int) : WalletTabUiState
    /** Wallet exists → tapping the Wallet icon opens SwissWallet. */
    data class Ready(val address: String) : WalletTabUiState
}

sealed interface WalletTabEvent {
    /** Screen must start SwissWallet's provisioning activity (needs an Activity launcher). */
    data class LaunchProvisioning(val swissquoteGuardian: String) : WalletTabEvent
}

class WalletTabViewModel(
    private val sessionRepository: SessionRepository,
    private val launcher: WalletAppLauncher,
    private val guardian: SwissquoteGuardian,
) : ViewModel() {

    private val generatingSteps = MutableStateFlow<Int?>(null)
    private val error = MutableStateFlow<String?>(null)

    val state: StateFlow<WalletTabUiState> =
        combine(sessionRepository.session, generatingSteps, error) { session, steps, err ->
            when {
                steps != null -> WalletTabUiState.Generating(steps)
                !session.hasAccount -> WalletTabUiState.NoAccount
                session.walletAddress != null -> WalletTabUiState.Ready(session.walletAddress)
                else -> WalletTabUiState.Generate(err)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WalletTabUiState.Loading)

    private val _events = Channel<WalletTabEvent>(Channel.BUFFERED)
    val events: Flow<WalletTabEvent> = _events.receiveAsFlow()

    private var stepsJob: Job? = null

    fun onGenerateClicked() {
        if (generatingSteps.value != null) return // already running
        if (!launcher.isWalletInstalled()) {
            error.value = "SwissWallet isn't installed on this device. Run the “swisswallet” configuration first."
            return
        }
        error.value = null
        generatingSteps.value = 0
        // Progress animation runs in parallel with the real provisioning call.
        stepsJob = viewModelScope.launch {
            delay(700); generatingSteps.value = 1
            delay(700); generatingSteps.value = 2
        }
        viewModelScope.launch {
            // Swissquote's guardian key (one per client) → its address goes into the new wallet's recovery setup.
            val guardianAddress = runCatching { guardian.address() }.getOrElse {
                fail("Couldn't prepare Swissquote's guardian key: ${it.message}")
                return@launch
            }
            _events.send(WalletTabEvent.LaunchProvisioning(guardianAddress))
        }
    }

    fun onProvisionResult(result: ProvisionResult) {
        viewModelScope.launch {
            when (result) {
                is ProvisionResult.Success -> {
                    stepsJob?.join()
                    generatingSteps.value = 3
                    delay(450)
                    sessionRepository.saveWallet(result.smartAccountAddress)
                    generatingSteps.value = null
                }
                is ProvisionResult.Failure -> fail(result.message)
                ProvisionResult.Cancelled -> fail("Wallet generation was cancelled.")
            }
        }
    }

    private fun fail(message: String) {
        stepsJob?.cancel()
        generatingSteps.value = null
        error.value = message
    }

    companion object {
        fun factory(c: AppContainer) = viewModelFactory {
            initializer { WalletTabViewModel(c.sessionRepository, c.walletAppLauncher, c.swissquoteGuardian) }
        }
    }
}
