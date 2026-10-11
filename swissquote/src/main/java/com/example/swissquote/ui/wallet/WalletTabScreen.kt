package com.example.swissquote.ui.wallet

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.common.Formats
import com.example.core.contract.ProvisionResult
import com.example.core.designsystem.SwissColors
import com.example.core.designsystem.SwissIcons
import com.example.core.designsystem.SwissType
import com.example.core.designsystem.components.SkeletonBox
import com.example.core.designsystem.components.SwissButton
import com.example.core.designsystem.components.SwissButtonStyle
import com.example.core.designsystem.components.SwissCard
import com.example.core.designsystem.components.SwissSpinner
import com.example.core.designsystem.components.Tag
import com.example.swissquote.data.ProvisionWalletContract

@Composable
fun WalletTabScreen(
    viewModel: WalletTabViewModel,
    onSignUp: () -> Unit,
    onOpenSwissWallet: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val provisionLauncher = rememberLauncherForActivityResult(ProvisionWalletContract()) { result ->
        viewModel.onProvisionResult(result)
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                WalletTabEvent.LaunchProvisioning -> try {
                    provisionLauncher.launch("Swissquote")
                } catch (e: ActivityNotFoundException) {
                    viewModel.onProvisionResult(ProvisionResult.Failure("SwissWallet isn't installed on this device."))
                } catch (e: SecurityException) {
                    viewModel.onProvisionResult(
                        ProvisionResult.Failure("Permission denied: both apps must be signed with the same key (reinstall both).")
                    )
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(start = 20.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Wallet", style = SwissType.Title)
            Tag("New", SwissColors.Orange)
        }

        AnimatedContent(
            targetState = state,
            contentKey = { it::class },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "wallet-tab",
            modifier = Modifier.fillMaxSize(),
        ) { s ->
            when (s) {
                WalletTabUiState.Loading -> LoadingContent()
                WalletTabUiState.NoAccount -> NoAccountContent(onSignUp)
                is WalletTabUiState.Generate -> GenerateContent(s.error, viewModel::onGenerateClicked)
                is WalletTabUiState.Generating -> GeneratingContent(s.completedSteps)
                is WalletTabUiState.Ready -> ReadyContent(s.address, onOpenSwissWallet)
            }
        }
    }
}

@Composable
private fun ScrollColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}

@Composable
private fun Hero(icon: ImageVector, color: Color) {
    Box(
        modifier = Modifier
            .size(width = 160.dp, height = 140.dp)
            .background(Brush.radialGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent))),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(CircleShape)
                .background(Color(0xFF17171A))
                .border(2.dp, color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(52.dp))
        }
    }
}

@Composable
private fun Texts(title: String, body: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = SwissType.Headline, textAlign = TextAlign.Center)
        Text(body, style = SwissType.Body, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ListCard(rows: List<Triple<Any, String, String>>) {
    SwissCard(Modifier.fillMaxWidth(), radius = 14.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            rows.forEach { (lead, t1, t2) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(SwissColors.Orange.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (lead) {
                            is ImageVector -> Icon(lead, null, tint = SwissColors.Orange, modifier = Modifier.size(16.dp))
                            else -> Text(lead.toString(), color = SwissColors.Orange, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                    Column {
                        Text(t1, style = SwissType.BodyStrong)
                        Text(t2, style = SwissType.Caption)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    ScrollColumn {
        SkeletonBox(Modifier.size(104.dp), CircleShape)
        SkeletonBox(Modifier.fillMaxWidth(0.7f).height(28.dp))
        SkeletonBox(Modifier.fillMaxWidth().height(40.dp))
        SkeletonBox(Modifier.fillMaxWidth().height(170.dp), RoundedCornerShape(14.dp))
    }
}

@Composable
private fun NoAccountContent(onSignUp: () -> Unit) {
    ScrollColumn {
        Hero(SwissIcons.Profile, SwissColors.Orange)
        Texts(
            "Create your crypto wallet",
            "A SwissWallet is linked to your Swissquote account. Sign up in a few minutes to create yours — 100% online.",
        )
        ListCard(
            listOf(
                Triple("1", "Open a Swissquote account", "100% online, in 10 minutes"),
                Triple("2", "Verify your identity", "ID document + selfie"),
                Triple("3", "Generate your wallet", "Right here, in one tap"),
            )
        )
        SwissButton("Sign up to Swissquote", onSignUp, Modifier.fillMaxWidth())
        SwissButton(
            "Already a client? Log in", onSignUp, Modifier.fillMaxWidth(),
            style = SwissButtonStyle.Text, height = 40.dp, textStyle = SwissType.BodyStrong.copy(fontSize = 14.sp),
        )
    }
}

@Composable
private fun GenerateContent(error: String?, onGenerate: () -> Unit) {
    ScrollColumn {
        Hero(SwissIcons.Wallet, SwissColors.Orange)
        Texts(
            "Generate your wallet",
            "Your Swissquote account is ready. Create your SwissWallet in one tap and manage your crypto in a dedicated app.",
        )
        ListCard(
            listOf(
                Triple(SwissIcons.Shield, "Self-custody, encrypted keys", "Stored in your phone's secure hardware"),
                Triple(SwissIcons.Link, "Linked to your account", "Fund it directly from Swissquote"),
                Triple(SwissIcons.Zap, "No gas fees", "Transactions sponsored via ERC-4337"),
            )
        )
        if (error != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SwissColors.Red.copy(alpha = 0.12f))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(SwissIcons.Alert, null, tint = SwissColors.Red, modifier = Modifier.size(18.dp))
                Text(error, color = SwissColors.Red, fontSize = 13.sp)
            }
        }
        SwissButton(
            if (error == null) "Generate my wallet" else "Try again",
            onGenerate,
            Modifier.fillMaxWidth(),
            leadingIcon = SwissIcons.Wallet,
        )
        Text("Takes less than 10 seconds", style = SwissType.Caption)
    }
}

@Composable
private fun GeneratingContent(completed: Int) {
    val steps = listOf(
        "Creating your keys",
        "Encrypting & storing in secure hardware",
        "Linking to your Swissquote account",
    )
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SwissSpinner(size = 96.dp)
        Spacer(Modifier.height(24.dp))
        Text("Generating your wallet…", style = SwissType.Section)
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            steps.forEachIndexed { i, label ->
                val done = i < completed
                val bg by animateColorAsState(if (done) SwissColors.Green else SwissColors.SurfaceHigh, label = "step$i")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(20.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
                        if (done) Icon(SwissIcons.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                    Text(label, color = if (done) SwissColors.TextSoft else SwissColors.TextSecondary, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun ReadyContent(address: String, onOpen: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        ScrollColumn {
            Hero(SwissIcons.Check, SwissColors.Green)
            Texts("Your wallet is ready!", "Tap the Wallet icon below anytime to open the SwissWallet app.")
            SwissCard(Modifier.fillMaxWidth(), radius = 14.dp) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Main wallet", style = SwissType.BodyStrong, modifier = Modifier.weight(1f))
                        Tag("Active", SwissColors.Green)
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SwissColors.SurfaceHigh)
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        Text(Formats.shortAddress(address, 8, 6), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                    Text("ERC-4337 smart account · Sepolia testnet", style = SwissType.Caption)
                }
            }
            SwissButton("Open SwissWallet", onOpen, Modifier.fillMaxWidth(), style = SwissButtonStyle.Outline, trailingIcon = SwissIcons.External)
            Spacer(Modifier.height(56.dp))
        }
        // Tooltip pointing at the Wallet tab (right-most item of the bottom bar)
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 6.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(SwissColors.Orange)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text("Tap to open SwissWallet", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Text("▼", color = SwissColors.Orange, fontSize = 12.sp, modifier = Modifier.padding(end = 26.dp))
        }
    }
}
