package com.example.swisswallet.ui.recovery

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.common.Formats
import com.example.core.designsystem.SwissColors
import com.example.core.designsystem.SwissIcons
import com.example.core.designsystem.SwissType
import com.example.core.designsystem.components.Divider1
import com.example.core.designsystem.components.IconTile
import com.example.core.designsystem.components.SwissButton
import com.example.core.designsystem.components.SwissButtonStyle
import com.example.core.designsystem.components.SwissCard
import com.example.core.designsystem.components.Tag
import com.example.swisswallet.data.recovery.SwissquoteGuardianContract
import com.example.swisswallet.domain.model.GuardianKind
import com.example.swisswallet.domain.model.RecoveryPhase
import com.example.swisswallet.domain.model.RecoverySession
import com.example.swisswallet.ui.components.FlowTopBar
import com.example.swisswallet.ui.components.InfoBanner

@Composable
fun RecoverScreen(viewModel: RecoverViewModel, onBack: () -> Unit, onRecovered: () -> Unit, showMessage: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.recovered) { if (state.recovered) onRecovered() }
    LaunchedEffect(state.message) { state.message?.let { showMessage(it); viewModel.messageShown() } }

    Column(Modifier.fillMaxSize()) {
        FlowTopBar("Recover my wallet", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val session = state.session
            when {
                session == null -> AddressStep(state, viewModel)
                state.phase == RecoveryPhase.COLLECTING -> ApprovalsStep(state, session, viewModel, showMessage)
                else -> DelayStep(state, session, viewModel)
            }
            state.error?.let { InfoBanner(it, SwissColors.Red, SwissIcons.Alert) }
        }
    }
}

// ------------------------------------------------------------------------------------ 1. which wallet?

@Composable
private fun AddressStep(state: RecoverUiState, vm: RecoverViewModel) {
    StepTitle(1, "Which wallet?", "Your wallet address doesn't change when you lose your phone. Enter it (Swissquote shows it in the Wallet tab).")
    RecoveryField(state.addressInput, vm::onAddressChange, "0x… wallet address", monospace = true, pasteButton = true)

    val c = state.candidate
    if (c == null) {
        SwissButton("Find my guardians", vm::lookup, Modifier.fillMaxWidth(), loading = state.busy, enabled = state.addressInput.length == 42, leadingIcon = SwissIcons.Search)
        return
    }

    SwissCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Wallet found · ${Formats.shortAddress(c.wallet)}", style = SwissType.BodyStrong)
            RuleTags(c.threshold, c.guardians.size, c.delaySeconds)
        }
    }
    if (c.pending != null) {
        InfoBanner("A recovery is already running for this wallet (key ${c.pending.newKeyFingerprint}).", SwissColors.Orange, SwissIcons.Alert)
    }
    SwissButton("Create my new key & ask guardians", vm::start, Modifier.fillMaxWidth(), loading = state.busy, enabled = c.pending == null, leadingIcon = SwissIcons.Key)
    Text(
        "This phone creates a NEW passkey in its secure chip. Your guardians will approve adding it to your wallet.",
        style = SwissType.Caption,
    )
    SectionLabel("Guardians of this wallet")
    SwissCard(Modifier.fillMaxWidth()) {
        c.guardians.forEachIndexed { i, g ->
            if (i > 0) Divider1()
            GuardianRow(g)
        }
    }
    RecoveryField(state.deviceLabel, vm::onDeviceLabelChange, "Name of this phone")
}

// ------------------------------------------------------------------------------------ 2. approvals

@Composable
private fun ApprovalsStep(state: RecoverUiState, s: RecoverySession, vm: RecoverViewModel, showMessage: (String) -> Unit) {
    val swissquote = rememberLauncherForActivityResult(SwissquoteGuardianContract(), vm::onSwissquoteResult)

    StepTitle(2, "Ask your guardians", "${s.threshold} of ${s.guardians.size} must approve. Each one checks it's really you, then sends you an approval.")

    // Progress n / threshold
    val got = s.approvedBy.size.coerceAtMost(s.threshold)
    SwissCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Approvals", style = SwissType.BodyStrong, modifier = Modifier.weight(1f))
                Text("$got / ${s.threshold}", color = if (s.hasEnoughApprovals) SwissColors.Green else Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            LinearProgressIndicator(
                progress = { got / s.threshold.toFloat() },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = if (s.hasEnoughApprovals) SwissColors.Green else SwissColors.Orange,
                trackColor = SwissColors.SurfaceHigh,
            )
            Text("New key fingerprint: ${s.request.keyFingerprint()}  ·  guardians see the same one", style = SwissType.Caption)
        }
        Divider1()
        s.guardians.forEachIndexed { i, g ->
            if (i > 0) Divider1()
            GuardianRow(g) { CheckBadge(g.address.lowercase() in s.approvedBy) }
        }
    }

    // ---- Ways to get approvals
    val swissquoteDone = s.guardians.any { it.kind == GuardianKind.SWISSQUOTE && it.address.lowercase() in s.approvedBy }
    SwissButton(
        if (swissquoteDone) "Swissquote approved" else "Ask Swissquote (identity check)",
        {
            try {
                swissquote.launch(s.request.encode())
            } catch (e: ActivityNotFoundException) {
                vm.swissquoteUnavailable()
            } catch (e: SecurityException) {
                vm.swissquoteUnavailable()
            }
        },
        Modifier.fillMaxWidth(),
        enabled = !swissquoteDone,
        leadingIcon = SwissIcons.Bank,
    )

    SwissButton("Approve with simulated guardians (demo)", vm::approveWithDemoGuardians, Modifier.fillMaxWidth(), style = SwissButtonStyle.Outline, leadingIcon = SwissIcons.Zap, loading = state.busy, height = 46.dp)

    OperationStatus(state.operation)
    SwissButton(
        "Start recovery",
        vm::submit,
        Modifier.fillMaxWidth(),
        enabled = s.hasEnoughApprovals && !state.operation.isRunning,
        loading = state.operation.isRunning,
        leadingIcon = SwissIcons.Shield,
    )
    SectionLabel("Other guardians: send them this request")
    CodeCard(
        title = "Recovery request",
        code = s.request.encode(),
        hint = "Send this code to your other device / close ones. In SwissWallet → “Be a guardian” → “Approve a recovery”, they paste it and send you back an approval code.",
        onCopied = showMessage,
    )

    SectionLabel("Received an approval code?")
    RecoveryField(state.approvalInput, vm::onApprovalChange, "SWSIG1.…", state.approvalError, monospace = true, pasteButton = true)
    SwissButton("Add approval", vm::submitApprovalCode, Modifier.fillMaxWidth(), style = SwissButtonStyle.Dark, enabled = state.approvalInput.isNotBlank(), height = 46.dp)

    SwissButton("Cancel and start over", vm::abandon, Modifier.fillMaxWidth(), style = SwissButtonStyle.Text, height = 44.dp)
}

// ------------------------------------------------------------------------------------ 3. delay + finish

@Composable
private fun DelayStep(state: RecoverUiState, s: RecoverySession, vm: RecoverViewModel) {
    val ready = state.phase == RecoveryPhase.READY
    StepTitle(3, if (ready) "Ready to finish" else "Safety delay", "During this time the real owner (if it's not you!) can cancel from the old phone. In real life: 48 h.")

    SwissCard(Modifier.fillMaxWidth(), radius = 22.dp) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            IconTile(if (ready) SwissIcons.Check else SwissIcons.Clock, if (ready) SwissColors.Green else SwissColors.Orange, size = 64.dp)
            Text(
                if (ready) "Delay over" else "${state.secondsLeft}s",
                style = SwissType.Title,
                textAlign = TextAlign.Center,
            )
            val total = (s.delaySeconds * 1000).coerceAtLeast(1L)
            val left = ((s.executeAfterMillis ?: 0L) - state.now).coerceIn(0L, total)
            LinearProgressIndicator(
                progress = { 1f - left / total.toFloat() },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = if (ready) SwissColors.Green else SwissColors.Orange,
                trackColor = SwissColors.SurfaceHigh,
            )
            Tag("${s.approvedBy.size} guardians approved", SwissColors.Green, leading = SwissIcons.Users)
        }
    }

    OperationStatus(state.operation)
    SwissButton(
        "Finish recovery",
        vm::finalize,
        Modifier.fillMaxWidth(),
        enabled = ready && !state.operation.isRunning,
        loading = state.operation.isRunning,
        leadingIcon = SwissIcons.Key,
    )
    Text(
        "Finishing adds your new passkey to the wallet and removes the lost one. Same address, same funds.",
        style = SwissType.Caption,
    )
    if (state.operation is com.example.swisswallet.domain.model.SendProgress.Failed) {
        SwissButton("Start over", vm::abandon, Modifier.fillMaxWidth(), style = SwissButtonStyle.Text, height = 44.dp)
    }
}

@Composable
private fun StepTitle(n: Int, title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
        Text("STEP $n OF 3", color = SwissColors.Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(title, style = SwissType.Section)
        Text(subtitle, style = SwissType.Body)
    }
}
