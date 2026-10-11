package com.example.swisswallet.ui.recovery

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.swisswallet.data.aa.ErrorMapper
import com.example.swisswallet.data.recovery.SwissquoteApprovalResult
import com.example.swisswallet.di.AppContainer
import com.example.swisswallet.domain.model.RecoveryCandidate
import com.example.swisswallet.domain.model.RecoveryPhase
import com.example.swisswallet.domain.model.RecoverySession
import com.example.swisswallet.domain.model.SendProgress
import com.example.swisswallet.domain.repository.RecoveryService
import com.example.swisswallet.domain.repository.WalletRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecoverUiState(
    val addressInput: String = "",
    val deviceLabel: String = "New phone · ${Build.MODEL}",
    val busy: Boolean = false,
    val error: String? = null,
    val candidate: RecoveryCandidate? = null,
    val session: RecoverySession? = null,
    val approvalInput: String = "",
    val approvalError: String? = null,
    val operation: SendProgress? = null,
    val now: Long = System.currentTimeMillis(),
    val recovered: Boolean = false,
    val message: String? = null,
) {
    val phase: RecoveryPhase? get() = session?.phase(now)
    val secondsLeft: Long get() = session?.executeAfterMillis?.let { ((it - now) / 1000).coerceAtLeast(0) } ?: 0
}

/**
 * New-phone side of the social recovery:
 *  1. enter the wallet address → read its guardians from the chain
 *  2. create a new passkey + a request code → collect 3 guardian approvals
 *     (Swissquote in one tap, others by code, simulated ones automatically)
 *  3. initiateRecovery → 30 s safety delay → finalizeRecovery → this phone owns the wallet again.
 */
class RecoverViewModel(
    private val recovery: RecoveryService,
    private val wallet: WalletRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RecoverUiState())
    val state: StateFlow<RecoverUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            wallet.lastWalletHint.first()?.let { hint -> _state.update { if (it.addressInput.isBlank()) it.copy(addressInput = hint) else it } }
        }
        viewModelScope.launch {
            recovery.session.collect { s -> _state.update { it.copy(session = s) } }
        }
        viewModelScope.launch {
            // App closed during a recovery? Re-read the chain (it may even be finished already).
            val before = recovery.session.first()
            runCatching { recovery.syncSession() }
            if (before != null && recovery.session.first() == null && wallet.currentAccount() != null) {
                _state.update { it.copy(recovered = true) }
            }
        }
        // Clock for the countdown.
        viewModelScope.launch {
            while (true) {
                _state.update { it.copy(now = System.currentTimeMillis()) }
                delay(1_000)
            }
        }
    }

    fun onAddressChange(v: String) = _state.update { it.copy(addressInput = v.trim(), error = null, candidate = null) }
    fun onDeviceLabelChange(v: String) = _state.update { it.copy(deviceLabel = v) }
    fun onApprovalChange(v: String) = _state.update { it.copy(approvalInput = v, approvalError = null) }

    fun lookup() = work {
        val c = recovery.lookup(_state.value.addressInput)
        _state.update { it.copy(candidate = c) }
    }

    fun start() = work {
        recovery.startRecovery(_state.value.addressInput, _state.value.deviceLabel.ifBlank { "New phone" })
    }

    fun submitApprovalCode() {
        val code = _state.value.approvalInput
        viewModelScope.launch {
            try {
                val g = recovery.addApproval(code)
                _state.update { it.copy(approvalInput = "", approvalError = null, message = "${g.name} approved ✓") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(approvalError = e.message ?: "Invalid approval") }
            }
        }
    }

    fun onSwissquoteResult(result: SwissquoteApprovalResult) {
        when (result) {
            is SwissquoteApprovalResult.Approved -> viewModelScope.launch {
                try {
                    recovery.addApproval(result.code, fromSwissquote = true)
                    _state.update { it.copy(message = "Swissquote approved ✓") }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.update { it.copy(error = e.message) }
                }
            }
            is SwissquoteApprovalResult.Refused -> _state.update { it.copy(error = result.message) }
            SwissquoteApprovalResult.Cancelled -> _state.update { it.copy(message = "Swissquote approval cancelled") }
        }
    }

    fun swissquoteUnavailable() = _state.update { it.copy(error = "Couldn't open Swissquote (not installed, or not signed with the same key).") }

    fun approveWithDemoGuardians() = work {
        val n = recovery.approveWithDemoGuardians()
        _state.update { it.copy(message = if (n == 0) "No simulated guardian of this wallet on this phone" else "$n simulated guardian(s) approved ✓") }
    }

    fun submit() {
        viewModelScope.launch {
            recovery.submitRecovery().collect { p -> _state.update { it.copy(operation = p) } }
        }
    }

    fun finalize() {
        viewModelScope.launch {
            recovery.finalizeRecovery().collect { p ->
                _state.update { it.copy(operation = p, recovered = p is SendProgress.Confirmed) }
            }
        }
    }

    fun abandon() = work {
        recovery.abandon()
        _state.update { it.copy(candidate = null, operation = null) }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    private fun work(block: suspend () -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = ErrorMapper.humanize(e)) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    companion object {
        fun factory(c: AppContainer) = viewModelFactory {
            initializer { RecoverViewModel(c.recoveryService, c.walletRepository) }
        }
    }
}
