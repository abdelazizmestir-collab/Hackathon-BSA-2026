package com.example.swisswallet.ui.recovery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.designsystem.SwissColors
import com.example.core.designsystem.SwissIcons
import com.example.core.designsystem.SwissType
import com.example.core.designsystem.components.Divider1
import com.example.core.designsystem.components.ErrorState
import com.example.core.designsystem.components.IconTile
import com.example.core.designsystem.components.SwissButton
import com.example.core.designsystem.components.SwissButtonStyle
import com.example.core.designsystem.components.SwissCard
import com.example.core.designsystem.components.SwissSpinner
import com.example.core.designsystem.components.Tag
import com.example.swisswallet.domain.model.GuardianKind
import com.example.swisswallet.ui.components.FlowTopBar
import kotlinx.coroutines.delay

@Composable
fun GuardiansScreen(viewModel: GuardiansViewModel, onBack: () -> Unit, onGuardianMode: () -> Unit, showMessage: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.message) {
        state.message?.let { showMessage(it); viewModel.messageShown() }
    }
    // A recovery is pending: refresh every few seconds so the countdown / cancel button stay correct.
    LaunchedEffect(state.pendingRecovery) {
        while (state.pendingRecovery != null) { delay(5_000); viewModel.load() }
    }

    Column(Modifier.fillMaxSize()) {
        FlowTopBar("Guardians & recovery", onBack)
        when {
            state.loading && state.status == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { SwissSpinner() }
            state.error != null && state.status == null -> ErrorState("Couldn't read the recovery module", state.error.orEmpty(), viewModel::load)
            else -> Content(state, viewModel, onGuardianMode)
        }
    }
}

@Composable
private fun Content(state: GuardiansUiState, vm: GuardiansViewModel, onGuardianMode: () -> Unit) {
    val status = state.status
    val active = status?.active == true
    val editable = !active || state.editing

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ---- Someone is trying to recover this wallet → the owner can stop it
        status?.pending?.let { pending ->
            SwissCard(Modifier.fillMaxWidth(), color = SwissColors.Red.copy(alpha = 0.14f)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(SwissIcons.Alert, null, tint = SwissColors.Red, modifier = Modifier.size(20.dp))
                        Text("Recovery in progress", color = SwissColors.Red, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Text(
                        "3 guardians approved adding a new key (${pending.newKeyFingerprint}) to your wallet. " +
                            "If it isn't you, cancel now — you still hold this phone, so you win.",
                        style = SwissType.Body,
                    )
                    SwissButton("Cancel this recovery", vm::cancelPending, Modifier.fillMaxWidth(), style = SwissButtonStyle.Danger, loading = state.operation.isRunning, height = 46.dp)
                }
            }
        }

        // ---- Status header
        SwissCard(Modifier.fillMaxWidth(), radius = 22.dp) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconTile(SwissIcons.Shield, if (active) SwissColors.Green else SwissColors.Orange, size = 48.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (active) "Recovery protection is ON" else "Protect your wallet", style = SwissType.ListTitle)
                    if (active) RuleTags(status!!.threshold, status.guardians.size, status.delaySeconds)
                    else Text("Choose 4 guardians. If you lose this phone, any 3 of them can give you access again.", style = SwissType.Body.copy(fontSize = 13.sp))
                }
            }
        }

        // ---- The 4 guardians
        SectionLabel("Your guardians (${state.draft.size}/${state.slots})")
        SwissCard(Modifier.fillMaxWidth()) {
            state.draft.forEachIndexed { i, g ->
                if (i > 0) Divider1()
                GuardianRow(g) {
                    when {
                        g.kind == GuardianKind.SWISSQUOTE -> Tag("Auto", SwissColors.Orange)
                        editable -> Box(Modifier.size(32.dp).clickable { vm.remove(g) }, contentAlignment = Alignment.Center) {
                            Icon(SwissIcons.Close, "Remove", tint = SwissColors.TextSecondary, modifier = Modifier.size(18.dp))
                        }
                        else -> Icon(SwissIcons.Check, null, tint = SwissColors.Green, modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (editable) {
                for (i in state.draft.size until state.slots) {
                    if (i > 0) Divider1()
                    EmptyGuardianSlot(i + 1, vm::openAdd)
                }
            }
        }

        // ---- Add-guardian panel
        if (editable && state.addMode != null) AddGuardianPanel(state, vm, onGuardianMode)

        OperationStatus(state.operation)

        if (editable) {
            SwissButton(
                if (active) "Save new guardians" else "Activate protection (3 of 4)",
                vm::activate,
                Modifier.fillMaxWidth(),
                enabled = state.canActivate,
                loading = state.operation.isRunning,
                leadingIcon = SwissIcons.Shield,
            )
            Text(
                "One gasless operation: the wallet registers your 4 guardians in the recovery module " +
                    "(deployed automatically if it's the first time on this network). Pimlico pays the gas.",
                style = SwissType.Caption,
            )
        } else {
            SwissButton("Change guardians", vm::startEditing, Modifier.fillMaxWidth(), style = SwissButtonStyle.Dark, leadingIcon = SwissIcons.Edit)
        }

        // ---- Plain-language explanations
        SectionLabel("How it works")
        ExplainCard(SwissIcons.Bank, "Swissquote is guardian #1, automatically",
            "When you generated your wallet, Swissquote gave its guardian address. To approve a recovery it checks your identity (like a password reset at your bank).", SwissColors.Orange)
        ExplainCard(SwissIcons.Phone, "Add one of your own devices",
            "On your tablet or old phone: install SwissWallet → “Be a guardian” → share the code. Paste it here with “My other device”.")
        ExplainCard(SwissIcons.Users, "Add a close one",
            "A friend or a relative… They open SwissWallet → “Be a guardian” and send you their code. They never see your money and can't move it.", SwissColors.Green)
        ExplainCard(SwissIcons.Shield, "An independent institution",
            "A neutral third party (e.g. a notary or a trust company), independent from you and from Swissquote. Swissquote and the institution together still need one of your own guardians.", SwissColors.TextSoft)
        ExplainCard(SwissIcons.Clock, "3 of 4 + a safety delay",
            "Losing 1 guardian isn't a problem, and 1 guardian alone can do nothing. After 3 approvals there's a 30 s wait (48 h in real life) during which you can cancel.", SwissColors.TextSoft)
    }
}

@Composable
private fun AddGuardianPanel(state: GuardiansUiState, vm: GuardiansViewModel, onGuardianMode: () -> Unit) {
    SwissCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (state.addMode) {
                        AddMode.MY_DEVICE -> "Add my other device"
                        AddMode.CLOSE_ONE -> "Add a close one"
                        else -> "Who should be your guardian?"
                    },
                    style = SwissType.BodyStrong,
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.size(32.dp).clickable(onClick = vm::closeAdd), contentAlignment = Alignment.Center) {
                    Icon(SwissIcons.Close, "Close", tint = SwissColors.TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
            when (state.addMode) {
                AddMode.CHOOSE, null -> {
                    ChoiceRow(SwissIcons.Phone, SwissColors.Blue, "My other device", "Tablet, old phone… with SwissWallet") { vm.chooseMode(AddMode.MY_DEVICE) }
                    ChoiceRow(SwissIcons.Users, SwissColors.Green, "A close one", "Family or friend with SwissWallet") { vm.chooseMode(AddMode.CLOSE_ONE) }
                    ChoiceRow(SwissIcons.Shield, SwissColors.TextSoft, "Independent institution (demo)", "Neutral third party, e.g. a notary. Simulated on this phone", vm::addInstitution)
                    ChoiceRow(SwissIcons.Zap, SwissColors.TextSecondary, "Simulated guardian (demo)", "Key kept on this phone to test with one emulator", vm::addDemoGuardian)
                }
                AddMode.MY_DEVICE, AddMode.CLOSE_ONE -> {
                    Text(
                        if (state.addMode == AddMode.MY_DEVICE)
                            "On your other device open SwissWallet → “Be a guardian” → copy the guardian code, then paste it here."
                        else
                            "Ask them to open SwissWallet → “Be a guardian” and send you their guardian code (or their 0x address).",
                        style = SwissType.Body.copy(fontSize = 13.sp),
                    )
                    RecoveryField(state.codeInput, vm::onCodeChange, "SWGRD1.… or 0x…", monospace = true, pasteButton = true)
                    RecoveryField(state.nameInput, vm::onNameChange, if (state.addMode == AddMode.MY_DEVICE) "Name (e.g. My iPad)" else "Name (e.g. Alex)")
                    state.inputError?.let { Text(it, color = SwissColors.Red, fontSize = 12.sp) }
                    SwissButton("Add guardian", vm::confirmAdd, Modifier.fillMaxWidth(), height = 46.dp)
                    Text(
                        "Testing with one emulator? This app's own “Be a guardian” screen also gives you a code.",
                        style = SwissType.Caption,
                        modifier = Modifier.clickable(onClick = onGuardianMode),
                    )
                }
            }
            if (state.addMode == AddMode.CHOOSE) state.inputError?.let { Text(it, color = SwissColors.Red, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun ChoiceRow(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(icon, color, size = 38.dp)
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = SwissType.Caption)
        }
        Icon(SwissIcons.ChevronRight, null, tint = SwissColors.TextSecondary, modifier = Modifier.size(18.dp))
    }
}
