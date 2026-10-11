package com.example.swissquote.guardian

import android.app.Activity
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.common.Formats
import com.example.core.contract.WalletContract
import com.example.core.designsystem.SwissColors
import com.example.core.designsystem.SwissIcons
import com.example.core.designsystem.SwissTheme
import com.example.core.designsystem.SwissType
import com.example.core.designsystem.components.Divider1
import com.example.core.designsystem.components.IconTile
import com.example.core.designsystem.components.SwissButton
import com.example.core.designsystem.components.SwissButtonStyle
import com.example.core.designsystem.components.SwissCard
import com.example.core.designsystem.components.SwissSpinner
import com.example.core.recovery.RecoveryRequest
import com.example.swissquote.SwissquoteApplication
import com.example.swissquote.domain.repository.SessionRepository
import com.example.swissquote.domain.repository.SwissquoteGuardian
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Swissquote = automatic guardian #1.
 *
 * SwissWallet (on the client's NEW phone) opens this screen with a recovery request. Swissquote:
 *  1. checks the request is for the wallet linked to this Swissquote account,
 *  2. checks the client's identity (SIMULATED here: ID + selfie + SMS code, like a bank's password reset),
 *  3. signs the request with its guardian key and returns the approval to SwissWallet.
 * Swissquote alone can't recover anything: 3 of the 4 guardians are needed.
 */
class GuardianApprovalActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val container = (application as SwissquoteApplication).container
        val code = intent.getStringExtra(WalletContract.EXTRA_RECOVERY_REQUEST).orEmpty()

        setContent {
            SwissTheme {
                val vm: GuardianApprovalViewModel = viewModel(
                    factory = viewModelFactory { initializer { GuardianApprovalViewModel(code, container.sessionRepository, container.swissquoteGuardian) } }
                )
                GuardianApprovalScreen(
                    vm,
                    onApproved = { approval ->
                        setResult(Activity.RESULT_OK, Intent().putExtra(WalletContract.EXTRA_GUARDIAN_APPROVAL, approval))
                        finish()
                    },
                    onRefused = { reason ->
                        setResult(Activity.RESULT_CANCELED, Intent().putExtra(WalletContract.EXTRA_ERROR, reason))
                        finish()
                    },
                )
            }
        }
    }
}

sealed interface ApprovalUiState {
    data object Loading : ApprovalUiState
    /** Can't approve (not logged in, wrong wallet, bad code…). */
    data class Rejected(val reason: String) : ApprovalUiState
    /** [checksDone] in 0..3; [verifying] = identity check running. */
    data class Review(
        val request: RecoveryRequest,
        val checksDone: Int = 0,
        val verifying: Boolean = false,
        val signing: Boolean = false,
    ) : ApprovalUiState {
        val verified: Boolean get() = checksDone >= IDENTITY_CHECKS.size
    }
    data class Approved(val code: String) : ApprovalUiState
}

val IDENTITY_CHECKS = listOf(
    "Swissquote login (2FA)",
    "Selfie matches your ID document",
    "SMS code sent to +41 •• ••• •• 12",
)

class GuardianApprovalViewModel(
    private val requestCode: String,
    private val session: SessionRepository,
    private val guardian: SwissquoteGuardian,
) : ViewModel() {

    private val _state = MutableStateFlow<ApprovalUiState>(ApprovalUiState.Loading)
    val state: StateFlow<ApprovalUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            delay(400)
            val request = runCatching { RecoveryRequest.decode(requestCode) }.getOrNull()
            val s = session.session.first()
            _state.value = when {
                request == null -> ApprovalUiState.Rejected("Invalid recovery request.")
                !s.hasAccount -> ApprovalUiState.Rejected("Log in to your Swissquote account first, then try again.")
                s.walletAddress == null -> ApprovalUiState.Rejected("No SwissWallet is linked to this Swissquote account.")
                !s.walletAddress.equals(request.wallet, ignoreCase = true) ->
                    ApprovalUiState.Rejected("This wallet (${Formats.shortAddress(request.wallet)}) isn't the one linked to your Swissquote account.")
                else -> ApprovalUiState.Review(request)
            }
        }
    }

    /** SIMULATED identity check (no real KYC in this concept). */
    fun verifyIdentity() {
        val r = _state.value as? ApprovalUiState.Review ?: return
        if (r.verifying || r.verified) return
        viewModelScope.launch {
            _state.value = r.copy(verifying = true)
            for (i in IDENTITY_CHECKS.indices) {
                delay(800)
                _state.update { (it as? ApprovalUiState.Review)?.copy(checksDone = i + 1) ?: it }
            }
            _state.update { (it as? ApprovalUiState.Review)?.copy(verifying = false) ?: it }
        }
    }

    fun approve() {
        val r = _state.value as? ApprovalUiState.Review ?: return
        if (!r.verified) return
        viewModelScope.launch {
            _state.value = r.copy(signing = true)
            try {
                val code = guardian.approve(requestCode)
                delay(500)
                _state.value = ApprovalUiState.Approved(code)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = ApprovalUiState.Rejected("Signing failed: ${e.message}")
            }
        }
    }
}

@Composable
private fun GuardianApprovalScreen(vm: GuardianApprovalViewModel, onApproved: (String) -> Unit, onRefused: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().background(SwissColors.Background).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(SwissIcons.Shield, SwissColors.Orange, size = 44.dp)
            Column {
                Text("Swissquote · Guardian", style = SwissType.Caption)
                Text("Approve wallet recovery", style = SwissType.Section)
            }
        }

        when (val s = state) {
            ApprovalUiState.Loading -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { SwissSpinner() }

            is ApprovalUiState.Rejected -> {
                SwissCard(Modifier.fillMaxWidth(), color = SwissColors.Red.copy(alpha = 0.12f)) {
                    Text(s.reason, color = SwissColors.Red, fontSize = 15.sp, modifier = Modifier.padding(16.dp))
                }
                SwissButton("Close", { onRefused(s.reason) }, Modifier.fillMaxWidth(), style = SwissButtonStyle.Dark)
            }

            is ApprovalUiState.Review -> {
                SwissCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Someone is recovering your SwissWallet on a new device.", style = SwissType.BodyStrong)
                        Info("Wallet", Formats.shortAddress(s.request.wallet))
                        Info("New device", s.request.deviceLabel)
                        Info("Key fingerprint", s.request.keyFingerprint())
                    }
                }
                Text(
                    "Swissquote is 1 of your 4 guardians. We approve only after checking it's really you. " +
                        "3 approvals are needed, then a safety delay during which your old phone can cancel.",
                    style = SwissType.Body,
                )

                SwissCard(Modifier.fillMaxWidth()) {
                    IDENTITY_CHECKS.forEachIndexed { i, label ->
                        if (i > 0) Divider1()
                        CheckLine(label, done = i < s.checksDone, active = s.verifying && i == s.checksDone)
                    }
                }
                Text("Identity check simulated for the demo.", style = SwissType.Caption)

                if (!s.verified) {
                    SwissButton("Verify my identity", vm::verifyIdentity, Modifier.fillMaxWidth(), loading = s.verifying, leadingIcon = SwissIcons.Fingerprint)
                } else {
                    SwissButton("Approve recovery", vm::approve, Modifier.fillMaxWidth(), loading = s.signing, leadingIcon = SwissIcons.Check)
                }
                SwissButton("It's not me — refuse", { onRefused("Refused by Swissquote.") }, Modifier.fillMaxWidth(), style = SwissButtonStyle.Danger, height = 48.dp)
            }

            is ApprovalUiState.Approved -> {
                SwissCard(Modifier.fillMaxWidth(), color = SwissColors.Green.copy(alpha = 0.12f)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(SwissIcons.Check, null, tint = SwissColors.Green)
                        Text("Swissquote approved your recovery.", color = SwissColors.Green, fontWeight = FontWeight.SemiBold)
                    }
                }
                SwissButton("Back to SwissWallet", { onApproved(s.code) }, Modifier.fillMaxWidth(), trailingIcon = SwissIcons.External)
            }
        }
    }
}

@Composable
private fun Info(label: String, value: String) {
    Row {
        Text(label, style = SwissType.Caption.copy(fontSize = 13.sp), modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CheckLine(label: String, done: Boolean, active: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when {
                done -> Box(Modifier.size(24.dp).clip(CircleShape).background(SwissColors.Green), contentAlignment = Alignment.Center) {
                    Icon(SwissIcons.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
                active -> CircularProgressIndicator(Modifier.size(20.dp), color = SwissColors.Orange, strokeWidth = 2.dp)
                else -> Box(Modifier.size(24.dp).clip(CircleShape).background(SwissColors.SurfaceHigh))
            }
        }
        Text(label, color = if (done) SwissColors.TextSoft else Color.White, fontSize = 14.sp)
    }
}
