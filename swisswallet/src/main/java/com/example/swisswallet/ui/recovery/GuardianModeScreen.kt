package com.example.swisswallet.ui.recovery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.common.Formats
import com.example.core.designsystem.SwissColors
import com.example.core.designsystem.SwissIcons
import com.example.core.designsystem.SwissType
import com.example.core.designsystem.components.Divider1
import com.example.core.designsystem.components.SwissButton
import com.example.core.designsystem.components.SwissButtonStyle
import com.example.core.designsystem.components.SwissCard
import com.example.swisswallet.data.aa.ErrorMapper
import com.example.swisswallet.di.AppContainer
import com.example.swisswallet.domain.model.GuardianKind
import com.example.swisswallet.domain.model.GuardianReview
import com.example.swisswallet.domain.repository.GuardianModeService
import com.example.swisswallet.ui.components.FlowTopBar
import com.example.swisswallet.ui.components.InfoBanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GuardianModeUiState(
    val myAddress: String? = null,
    val name: String = "",
    val kind: GuardianKind = GuardianKind.CLOSE_ONE,
    val inviteCode: String? = null,
    val requestInput: String = "",
    val review: GuardianReview? = null,
    val approvalCode: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
)

/** "Be a guardian": share my guardian code, and approve a friend's (or my own) recovery request. */
class GuardianModeViewModel(private val service: GuardianModeService) : ViewModel() {

    private val _state = MutableStateFlow(GuardianModeUiState())
    val state: StateFlow<GuardianModeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val address = service.myGuardianAddress()
            _state.update { it.copy(myAddress = address) }
            refreshInvite()
        }
    }

    fun onNameChange(v: String) { _state.update { it.copy(name = v) }; refreshInvite() }
    fun onKindChange(k: GuardianKind) { _state.update { it.copy(kind = k) }; refreshInvite() }
    fun onRequestChange(v: String) = _state.update { it.copy(requestInput = v, review = null, approvalCode = null, error = null) }

    private fun refreshInvite() {
        viewModelScope.launch {
            val s = _state.value
            val default = if (s.kind == GuardianKind.MY_DEVICE) "My other device" else "Guardian"
            val code = service.inviteCode(s.name.ifBlank { default }, s.kind)
            _state.update { it.copy(inviteCode = code) }
        }
    }

    fun review() = work {
        val review = service.review(_state.value.requestInput)
        _state.update { it.copy(review = review) }
    }

    fun approve() = work {
        val r = _state.value.review ?: return@work
        val code = service.approve(r.request)
        _state.update { it.copy(approvalCode = code) }
    }

    private fun work(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: ErrorMapper.humanize(e)) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    companion object {
        fun factory(c: AppContainer) = viewModelFactory { initializer { GuardianModeViewModel(c.guardianModeService) } }
    }
}

@Composable
fun GuardianModeScreen(viewModel: GuardianModeViewModel, onBack: () -> Unit, showMessage: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        FlowTopBar("Be a guardian", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ExplainCard(
                SwissIcons.Shield, "What's a guardian?",
                "You help someone (or yourself, from a second device) get their wallet back if they lose their phone. " +
                    "You can't see or move their money: you can only say “yes, this new phone is really them”.",
                SwissColors.Green,
            )

            // ---- 1. My guardian code
            SectionLabel("1 · Share your guardian code")
            SwissCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth()) {
                    KindChip("For a close one", state.kind == GuardianKind.CLOSE_ONE, Modifier.weight(1f)) { viewModel.onKindChange(GuardianKind.CLOSE_ONE) }
                    KindChip("For my own wallet", state.kind == GuardianKind.MY_DEVICE, Modifier.weight(1f)) { viewModel.onKindChange(GuardianKind.MY_DEVICE) }
                }
            }
            RecoveryField(state.name, viewModel::onNameChange, if (state.kind == GuardianKind.MY_DEVICE) "Name of this device (e.g. My iPad)" else "Your name (e.g. Mom)")
            state.inviteCode?.let {
                CodeCard(
                    title = "Guardian code",
                    code = it,
                    hint = "Address ${Formats.shortAddress(state.myAddress.orEmpty())}. Send this code to the wallet owner: they paste it in Profile → Guardians.",
                    onCopied = showMessage,
                )
            }

            // ---- 2. Approve a recovery
            SectionLabel("2 · Approve a recovery")
            RecoveryField(state.requestInput, viewModel::onRequestChange, "SWREC1.… request code", monospace = true, pasteButton = true)
            if (state.review == null) {
                SwissButton("Check the request", viewModel::review, Modifier.fillMaxWidth(), style = SwissButtonStyle.Dark, enabled = state.requestInput.isNotBlank(), loading = state.busy, height = 46.dp)
            }
            state.review?.let { r ->
                SwissCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Recovery request", style = SwissType.BodyStrong)
                        Line("Wallet", Formats.shortAddress(r.request.wallet))
                        Line("New device", r.request.deviceLabel)
                        Line("Key fingerprint", r.request.keyFingerprint())
                    }
                    Divider1()
                    when (r.iAmGuardian) {
                        true -> InfoBanner("You are a guardian of this wallet", SwissColors.Green, SwissIcons.Check, Modifier.padding(10.dp))
                        false -> InfoBanner("You are NOT a guardian of this wallet: your approval won't count", SwissColors.Red, SwissIcons.Alert, Modifier.padding(10.dp))
                        null -> InfoBanner("Couldn't check on-chain (offline?)", SwissColors.Orange, SwissIcons.Alert, Modifier.padding(10.dp))
                    }
                }
                InfoBanner("Call the person first and compare the fingerprint ${r.request.keyFingerprint()}. Never approve a request you didn't expect.", SwissColors.Orange, SwissIcons.Alert)
                if (state.approvalCode == null) {
                    SwissButton("Approve", viewModel::approve, Modifier.fillMaxWidth(), loading = state.busy, leadingIcon = SwissIcons.Check)
                }
            }
            state.approvalCode?.let {
                CodeCard("Approval code", it, "Send this code back to the person recovering their wallet.", showMessage)
            }
            state.error?.let { InfoBanner(it, SwissColors.Red, SwissIcons.Alert) }
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row {
        Text(label, style = SwissType.Caption.copy(fontSize = 13.sp), modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun KindChip(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, color = if (selected) SwissColors.Orange else SwissColors.TextSecondary, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp)
    }
}
