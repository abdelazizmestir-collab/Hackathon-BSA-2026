package com.example.swisswallet.ui.recovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.recovery.RecoveryPolicy
import com.example.swisswallet.data.aa.ErrorMapper
import com.example.swisswallet.di.AppContainer
import com.example.swisswallet.domain.model.Guardian
import com.example.swisswallet.domain.model.GuardianInvite
import com.example.swisswallet.domain.model.GuardianKind
import com.example.swisswallet.domain.model.ProtectionStatus
import com.example.swisswallet.domain.model.SendProgress
import com.example.swisswallet.domain.repository.GuardianRepository
import com.example.swisswallet.domain.repository.RecoveryService
import com.example.swisswallet.domain.repository.WalletRepository
import com.example.swisswallet.util.AddressValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which "add a guardian" form is open. */
enum class AddMode { CHOOSE, MY_DEVICE, CLOSE_ONE }

data class GuardiansUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val status: ProtectionStatus? = null,
    /** The 4 guardians being chosen (or the current ones when protection is active). */
    val draft: List<Guardian> = emptyList(),
    val editing: Boolean = false,
    val addMode: AddMode? = null,
    val codeInput: String = "",
    val nameInput: String = "",
    val inputError: String? = null,
    val operation: SendProgress? = null,
    val message: String? = null,
) {
    val slots: Int get() = RecoveryPolicy.GUARDIAN_COUNT
    val canActivate: Boolean get() = draft.size == slots && !operation.isRunning
    val pendingRecovery get() = status?.pending
}

/**
 * Owner side: choose the 4 guardians (Swissquote is pre-filled), activate 3-of-4 protection in one
 * gasless UserOperation, and cancel a recovery someone else started.
 */
class GuardiansViewModel(
    private val wallet: WalletRepository,
    private val guardians: GuardianRepository,
    private val recovery: RecoveryService,
) : ViewModel() {

    private val _state = MutableStateFlow(GuardiansUiState())
    val state: StateFlow<GuardiansUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val status = recovery.protectionStatus()
                val draft = if (status.active) status.guardians else initialDraft()
                _state.update { it.copy(loading = false, status = status, draft = if (it.editing) it.draft else draft) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = ErrorMapper.humanize(e)) }
            }
        }
    }

    /** Swissquote is guardian #1 automatically (its address came with the wallet generation). */
    private suspend fun initialDraft(): List<Guardian> {
        val sq = wallet.currentAccount()?.swissquoteGuardian ?: return _state.value.draft
        val swissquote = Guardian(sq, "Swissquote", GuardianKind.SWISSQUOTE)
        return listOf(swissquote) + _state.value.draft.filterNot { it.address.equals(sq, true) }
    }

    fun startEditing() = _state.update { it.copy(editing = true) }

    fun openAdd() = _state.update { it.copy(addMode = AddMode.CHOOSE, codeInput = "", nameInput = "", inputError = null) }
    fun closeAdd() = _state.update { it.copy(addMode = null, inputError = null) }
    fun chooseMode(mode: AddMode) = _state.update { it.copy(addMode = mode, inputError = null) }
    fun onCodeChange(v: String) = _state.update { it.copy(codeInput = v, inputError = null) }
    fun onNameChange(v: String) = _state.update { it.copy(nameInput = v, inputError = null) }

    /** Accepts a `SWGRD1.` code (from the guardian's SwissWallet) or a plain 0x address + a name. */
    fun confirmAdd() {
        val s = _state.value
        val kind = if (s.addMode == AddMode.MY_DEVICE) GuardianKind.MY_DEVICE else GuardianKind.CLOSE_ONE
        val input = s.codeInput.trim()
        val guardian = try {
            if (input.startsWith("SWGRD1.")) {
                val invite = GuardianInvite.decode(input)
                Guardian(invite.address, s.nameInput.trim().ifBlank { invite.name }, kind)
            } else {
                AddressValidator.validate(input, null)?.let { throw IllegalArgumentException(it) }
                require(s.nameInput.isNotBlank()) { "Give this guardian a name" }
                Guardian(input, s.nameInput.trim(), kind)
            }
        } catch (e: Exception) {
            _state.update { it.copy(inputError = e.message ?: "Invalid code") }
            return
        }
        addToDraft(guardian)
    }

    /** DEMO: guardian whose key is kept on this phone (lets you test 3-of-4 with one emulator). */
    fun addDemoGuardian() {
        viewModelScope.launch {
            try {
                addToDraft(guardians.createDemoGuardian())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(inputError = ErrorMapper.humanize(e)) }
            }
        }
    }

    private fun addToDraft(g: Guardian) {
        val s = _state.value
        when {
            s.draft.any { it.address.equals(g.address, true) } -> _state.update { it.copy(inputError = "This guardian is already in the list") }
            s.draft.size >= s.slots -> _state.update { it.copy(inputError = "You already have ${s.slots} guardians") }
            else -> _state.update { it.copy(draft = it.draft + g, addMode = null, inputError = null, message = "${g.name} added") }
        }
    }

    fun remove(g: Guardian) {
        if (g.kind == GuardianKind.SWISSQUOTE) {
            _state.update { it.copy(message = "Swissquote is your automatic guardian") }
            return
        }
        _state.update { it.copy(draft = it.draft.filterNot { d -> d.address.equals(g.address, true) }) }
    }

    fun activate() {
        val draft = _state.value.draft
        if (draft.size != RecoveryPolicy.GUARDIAN_COUNT) return
        viewModelScope.launch {
            recovery.activateProtection(draft).collect { p ->
                _state.update { it.copy(operation = p) }
                if (p is SendProgress.Confirmed) {
                    _state.update { it.copy(editing = false, message = "Protection active: 3 of 4 guardians") }
                    load()
                }
            }
        }
    }

    fun cancelPending() {
        viewModelScope.launch {
            recovery.cancelPendingRecovery().collect { p ->
                _state.update { it.copy(operation = p) }
                if (p is SendProgress.Confirmed) {
                    _state.update { it.copy(message = "Recovery cancelled. Your wallet is safe.") }
                    load()
                }
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    companion object {
        fun factory(c: AppContainer) = viewModelFactory {
            initializer { GuardiansViewModel(c.walletRepository, c.guardianRepository, c.recoveryService) }
        }
    }
}
