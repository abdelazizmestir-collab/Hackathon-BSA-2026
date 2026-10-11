package com.example.swisswallet.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.common.Formats
import com.example.core.designsystem.SwissColors
import com.example.core.designsystem.SwissIcons
import com.example.core.designsystem.SwissType
import com.example.core.designsystem.components.CircleIconButton
import com.example.core.designsystem.components.Divider1
import com.example.core.designsystem.components.IconTile
import com.example.core.designsystem.components.SwissButton
import com.example.core.designsystem.components.SwissButtonStyle
import com.example.core.designsystem.components.SwissCard
import com.example.core.designsystem.components.SwissSwitch
import com.example.core.designsystem.components.Tag
import com.example.swisswallet.config.ChainConfig
import com.example.swisswallet.ui.components.ScreenHeader
import com.example.swisswallet.util.copyToClipboard
import com.example.swisswallet.util.openUrl

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    showMessage: (String) -> Unit,
    onLock: () -> Unit,
    onGuardians: () -> Unit,
    onGuardianMode: () -> Unit,
    onWalletWiped: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmLock by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }

    fun copy(label: String, value: String?) {
        if (value == null) return
        context.copyToClipboard(label, value)
        showMessage("$label copied")
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Profile") {
            CircleIconButton(SwissIcons.Edit, "Edit profile", onClick = { showMessage("Profile editing is managed in Swissquote") })
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Identity card
            SwissCard(Modifier.fillMaxWidth(), radius = 22.dp) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        Modifier.size(60.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(SwissColors.OrangeLight, SwissColors.OrangeDark))),
                        contentAlignment = Alignment.Center,
                    ) { Text("AM", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Abdelaziz M.", style = SwissType.ListTitle.copy(fontSize = 18.sp))
                        Tag("Swissquote account linked", SwissColors.Green, leading = SwissIcons.Check)
                    }
                }
            }

            SectionTitle("Recovery")
            SwissCard(Modifier.fillMaxWidth()) {
                SettingRow(SwissIcons.Users, "Guardians", "3 of 4", onGuardians)
                Divider1()
                SettingRow(SwissIcons.Shield, "Be a guardian for someone", null, onGuardianMode)
                Divider1()
                SettingRow(SwissIcons.Phone, "Simulate lost phone (demo)", null) { confirmWipe = true }
            }

            SectionTitle("Smart account (ERC-4337)")
            SwissCard(Modifier.fillMaxWidth()) {
                SettingRow(SwissIcons.Wallet, "Account address", Formats.shortAddress(state.account?.smartAccountAddress ?: "—")) {
                    copy("Account address", state.account?.smartAccountAddress)
                }
                Divider1()
                SettingRow(
                    SwissIcons.Layers, "Deployment",
                    when (state.deployState) {
                        DeployState.Checking -> "Checking…"
                        DeployState.Deployed -> "Deployed"
                        DeployState.NotDeployed -> "On first send"
                        DeployState.Unknown -> "Unknown"
                    },
                ) { viewModel.checkDeployment() }
                Divider1()
                SettingRow(SwissIcons.Zap, "Gas sponsor", if (state.paymasterConfigured) "Pimlico · active" else "API key missing") {
                    if (!state.paymasterConfigured) showMessage("Add PIMLICO_API_KEY to local.properties")
                }
                Divider1()
                SettingRow(SwissIcons.External, "View on block explorer", null) {
                    state.account?.let { context.openUrl(ChainConfig.addressUrl(it.smartAccountAddress)) }
                }
            }

            SectionTitle("Security")
            SwissCard(Modifier.fillMaxWidth()) {
                ToggleRow(SwissIcons.Fingerprint, "Biometrics", state.biometrics, viewModel::onBiometrics)
                Divider1()
                SettingRow(SwissIcons.Key, "Passkey (this phone)", state.account?.passkeyFingerprint ?: "—") {
                    showMessage("P-256 key in the secure chip · owner #${state.account?.ownerIndex ?: 0} of the wallet")
                }
                Divider1()
                SettingRow(SwissIcons.Lock, "PIN code", null) { showMessage("PIN code is simulated in this concept") }
            }

            SectionTitle("Preferences")
            SwissCard(Modifier.fillMaxWidth()) {
                SettingRow(SwissIcons.Coins, "Currency", "CHF") { }
                Divider1()
                ToggleRow(SwissIcons.Bell, "Notifications", state.notifications, viewModel::onNotifications)
                Divider1()
                SettingRow(SwissIcons.Globe, "Network", ChainConfig.NETWORK_NAME) { }
            }

            Spacer(Modifier.height(4.dp))
            SwissButton("Log out", { confirmLock = true }, Modifier.fillMaxWidth(), style = SwissButtonStyle.Danger, leadingIcon = SwissIcons.Logout, height = 50.dp)
        }
    }

    if (confirmWipe) {
        WipeDialog(
            onConfirm = { confirmWipe = false; viewModel.simulateLostPhone(onWalletWiped) },
            onDismiss = { confirmWipe = false },
        )
    }

    if (confirmLock) {
        AlertDialog(
            onDismissRequest = { confirmLock = false },
            containerColor = SwissColors.Surface,
            title = { Text("Log out of SwissWallet?", color = Color.White) },
            text = { Text("Your keys stay encrypted on this device. You can reopen SwissWallet from Swissquote anytime.", color = SwissColors.TextSoft) },
            confirmButton = { TextButton(onClick = { confirmLock = false; onLock() }) { Text("Log out", color = SwissColors.Red) } },
            dismissButton = { TextButton(onClick = { confirmLock = false }) { Text("Cancel", color = Color.White) } },
        )
    }
}

@Composable
private fun WipeDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SwissColors.Surface,
        title = { Text("Simulate a lost phone?", color = Color.White) },
        text = {
            Text(
                "This deletes this phone's passkey and local wallet data — like losing the phone. " +
                    "Your funds stay on-chain. You'll then recover the wallet with your guardians (3 of 4). " +
                    "Make sure protection is active first!",
                color = SwissColors.TextSoft,
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete my key", color = SwissColors.Red) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White) } },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = SwissType.Label, modifier = Modifier.padding(start = 6.dp, top = 10.dp))
}

@Composable
private fun SettingRow(icon: ImageVector, label: String, value: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(icon, SwissColors.TextSoft, size = 34.dp, background = SwissColors.SurfaceHigh, round = false)
        Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (value != null) Text(value, color = SwissColors.TextSecondary, fontSize = 14.sp)
        Icon(SwissIcons.ChevronRight, null, tint = SwissColors.TextSecondary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(icon, SwissColors.TextSoft, size = 34.dp, background = SwissColors.SurfaceHigh, round = false)
        Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        SwissSwitch(checked, onChange)
    }
}
