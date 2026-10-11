package com.example.swisswallet.ui.splash

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.contract.WalletContract
import com.example.core.designsystem.SwissColors
import com.example.core.designsystem.SwissIcons
import com.example.core.designsystem.SwissType
import com.example.core.designsystem.components.SwissButton
import com.example.core.designsystem.components.SwissButtonStyle
import com.example.swisswallet.di.AppContainer
import com.example.swisswallet.domain.repository.WalletRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SplashViewModel(wallet: WalletRepository) : ViewModel() {
    /** null = still reading storage. */
    val hasWallet: StateFlow<Boolean?> = wallet.account
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        fun factory(c: AppContainer) = viewModelFactory { initializer { SplashViewModel(c.walletRepository) } }
    }
}

@Composable
private fun AppIcon(size: Int = 104) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .shadow(28.dp, RoundedCornerShape(28.dp), ambientColor = SwissColors.Orange, spotColor = SwissColors.Orange)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(SwissColors.OrangeLight, Color(0xFFD9441A)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(SwissIcons.Wallet, null, tint = Color.White, modifier = Modifier.size((size * 0.52f).dp))
    }
}

/** "Opening your wallet…" — progress bar animates while we read the local wallet state. */
@Composable
fun SplashScreen(viewModel: SplashViewModel, onReady: (hasWallet: Boolean) -> Unit) {
    val hasWallet by viewModel.hasWallet.collectAsStateWithLifecycle()
    var target by remember { mutableStateOf(0f) }
    val progress by animateFloatAsState(target, tween(1100), label = "splash-progress")

    LaunchedEffect(Unit) { target = 1f }
    LaunchedEffect(hasWallet) {
        val result = hasWallet ?: return@LaunchedEffect
        delay(1200) // let the brand moment breathe
        onReady(result)
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppIcon()
            Text("SwissWallet", style = SwissType.Title.copy(fontSize = 30.sp))
            Text("Signed in with your Swissquote account", style = SwissType.Body.copy(color = SwissColors.TextSecondary))
            Box(
                Modifier
                    .width(200.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(SwissColors.Surface)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(SwissColors.Orange)
                )
            }
            Text("Opening your wallet…", style = SwissType.Caption.copy(color = SwissColors.TextSoft, fontSize = 13.sp))
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(SwissIcons.Lock, null, tint = SwissColors.TextSecondary, modifier = Modifier.size(14.dp))
            Text("Encrypted keys · ERC-4337 smart account", style = SwissType.Caption)
        }
    }
}

/** Shown when SwissWallet is opened before the wallet was generated in Swissquote. */
@Composable
fun NoWalletScreen(viewModel: SplashViewModel, onWalletReady: () -> Unit, onRecover: () -> Unit, onGuardianMode: () -> Unit) {
    val hasWallet by viewModel.hasWallet.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(hasWallet) { if (hasWallet == true) onWalletReady() }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppIcon(88)
        Spacer(Modifier.height(24.dp))
        Text("No wallet on this device yet", style = SwissType.Section, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Open the Wallet tab in the Swissquote app and tap “Generate my wallet”. You'll come back here automatically.",
            style = SwissType.Body,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        SwissButton(
            "Open Swissquote",
            onClick = {
                val intent = context.packageManager.getLaunchIntentForPackage(WalletContract.SWISSQUOTE_PACKAGE)
                if (intent != null) context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = SwissIcons.External,
        )
        Spacer(Modifier.height(12.dp))
        SwissButton(
            "I lost my phone — recover my wallet",
            onClick = onRecover,
            modifier = Modifier.fillMaxWidth(),
            style = SwissButtonStyle.Dark,
            leadingIcon = SwissIcons.Shield,
        )
        Spacer(Modifier.height(4.dp))
        SwissButton(
            "Be a guardian for someone",
            onClick = onGuardianMode,
            modifier = Modifier.fillMaxWidth(),
            style = SwissButtonStyle.Text,
            leadingIcon = SwissIcons.Users,
            height = 46.dp,
        )
    }
}
