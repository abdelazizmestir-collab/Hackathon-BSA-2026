package com.example.swisswallet.ui.receive

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.designsystem.SwissColors
import com.example.core.designsystem.SwissIcons
import com.example.core.designsystem.SwissType
import com.example.core.designsystem.components.ErrorState
import com.example.core.designsystem.components.SkeletonBox
import com.example.core.designsystem.components.SwissButton
import com.example.core.designsystem.components.SwissButtonStyle
import com.example.swisswallet.config.ChainConfig
import com.example.swisswallet.di.AppContainer
import com.example.swisswallet.domain.repository.WalletRepository
import com.example.swisswallet.ui.components.FlowTopBar
import com.example.swisswallet.ui.components.InfoBanner
import com.example.swisswallet.util.copyToClipboard
import com.example.swisswallet.util.openUrl
import com.example.swisswallet.util.qrBitmap
import com.example.swisswallet.util.shareText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReceiveUiState(
    val address: String? = null,
    val qr: ImageBitmap? = null,
    val error: String? = null,
)

class ReceiveViewModel(private val wallet: WalletRepository) : ViewModel() {
    private val _state = MutableStateFlow(ReceiveUiState())
    val state: StateFlow<ReceiveUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = ReceiveUiState()
        viewModelScope.launch {
            try {
                val address = wallet.currentAccount()?.smartAccountAddress ?: error("No wallet on this device")
                _state.value = ReceiveUiState(address = address)
                // QR generation is CPU work → off the main thread.
                val qr = withContext(Dispatchers.Default) { qrBitmap("ethereum:$address@${ChainConfig.CHAIN_ID}").asImageBitmap() }
                _state.value = ReceiveUiState(address = address, qr = qr)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = ReceiveUiState(error = e.message ?: "Couldn't load your address")
            }
        }
    }

    companion object {
        fun factory(c: AppContainer) = viewModelFactory { initializer { ReceiveViewModel(c.walletRepository) } }
    }
}

@Composable
fun ReceiveScreen(viewModel: ReceiveViewModel, onBack: () -> Unit, showMessage: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        FlowTopBar("Receive", onBack)
        if (state.error != null) {
            ErrorState("Address unavailable", state.error.orEmpty(), onRetry = viewModel::load)
            return@Column
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier.size(260.dp).clip(RoundedCornerShape(24.dp)).background(Color.White).padding(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                val qr = state.qr
                if (qr == null) SkeletonBox(Modifier.fillMaxSize(), RoundedCornerShape(12.dp))
                else Image(qr, contentDescription = "QR code of your address", modifier = Modifier.fillMaxSize())
            }
            Text("Your SwissWallet address", style = SwissType.BodyStrong)
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SwissColors.Surface).padding(14.dp)
            ) {
                Text(
                    state.address ?: "…",
                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SwissButton(
                    "Copy", {
                        state.address?.let { context.copyToClipboard("Wallet address", it); showMessage("Address copied") }
                    },
                    Modifier.weight(1f), style = SwissButtonStyle.Light, leadingIcon = SwissIcons.Copy, height = 48.dp,
                    enabled = state.address != null,
                )
                SwissButton(
                    "Share", { state.address?.let { context.shareText(it, "Share address") } },
                    Modifier.weight(1f), style = SwissButtonStyle.Dark, leadingIcon = SwissIcons.Share, height = 48.dp,
                    enabled = state.address != null,
                )
            }
            InfoBanner("Only send ${ChainConfig.NETWORK_NAME} ETH or USDC to this address.", SwissColors.Orange, SwissIcons.Alert)
            SwissButton("Get test ETH (faucet)", { context.openUrl(ChainConfig.FAUCET) }, Modifier.fillMaxWidth(), style = SwissButtonStyle.Outline, trailingIcon = SwissIcons.External, height = 48.dp)
            SwissButton("Get test USDC (Circle faucet)", { context.openUrl(ChainConfig.USDC_FAUCET) }, Modifier.fillMaxWidth(), style = SwissButtonStyle.Text, trailingIcon = SwissIcons.External, height = 44.dp)
        }
    }
}
