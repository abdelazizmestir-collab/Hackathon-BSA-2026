package com.example.swisswallet.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.example.core.designsystem.components.EmptyState
import com.example.core.designsystem.components.ErrorState
import com.example.core.designsystem.components.SkeletonBox
import com.example.core.designsystem.components.SwissButton
import com.example.core.designsystem.components.SwissButtonStyle
import com.example.core.designsystem.components.SwissCard
import com.example.swisswallet.config.ChainConfig
import com.example.swisswallet.domain.model.TokenBalance
import com.example.swisswallet.domain.model.Tokens
import com.example.swisswallet.ui.components.InfoBanner
import com.example.swisswallet.ui.components.ScreenHeader
import com.example.swisswallet.ui.components.TokenAvatar
import com.example.swisswallet.util.copyToClipboard
import java.math.BigDecimal
import java.math.RoundingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    viewModel: WalletViewModel,
    onSend: (String) -> Unit,
    onReceive: () -> Unit,
    showMessage: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.onAppear() }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Wallet") {
            CircleIconButton(SwissIcons.Qr, "Receive", onClick = onReceive)
        }
        if (state.error != null && state.portfolio == null) {
            ErrorState("Couldn't load balances", state.error.orEmpty(), onRetry = viewModel::onRetry)
            return@Column
        }
        PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = viewModel::onRefresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    AccountCard(
                        address = state.account?.smartAccountAddress,
                        totalChf = state.portfolio?.totalChf,
                        loading = state.isLoading,
                        onCopy = { addr ->
                            context.copyToClipboard("Wallet address", addr)
                            showMessage("Address copied")
                        },
                        onQr = onReceive,
                    )
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SwissButton("Receive", onReceive, Modifier.weight(1f), style = SwissButtonStyle.Light, leadingIcon = SwissIcons.ArrowDown, height = 46.dp)
                        SwissButton("Send", { onSend("ETH") }, Modifier.weight(1f), style = SwissButtonStyle.Dark, leadingIcon = SwissIcons.ArrowUp, height = 46.dp)
                    }
                }
                item { Segmented(state.tab, viewModel::onTabSelected) }
                item {
                    when (state.tab) {
                        WalletTab.Tokens -> TokenList(
                            balances = state.portfolio?.balances,
                            loading = state.isLoading,
                            onToken = { onSend(it) },
                            onAddToken = { showMessage("Custom tokens are coming soon") },
                        )
                        WalletTab.Nft -> SwissCard(Modifier.fillMaxWidth()) {
                            EmptyState(SwissIcons.Image, "No NFTs yet", "NFTs sent to your smart account will show up here.")
                        }
                        WalletTab.Staking -> SwissCard(Modifier.fillMaxWidth()) {
                            EmptyState(SwissIcons.Layers, "Staking coming soon", "Earn rewards on your ETH directly from SwissWallet.")
                        }
                    }
                }
                item {
                    InfoBanner("Gas fees sponsored by Pimlico · ERC-4337 smart account", SwissColors.Green, SwissIcons.Zap)
                }
            }
        }
    }
}

@Composable
private fun AccountCard(
    address: String?,
    totalChf: BigDecimal?,
    loading: Boolean,
    onCopy: (String) -> Unit,
    onQr: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(176.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(SwissColors.Orange, Color(0xFF7A2510))))
    ) {
        Box(
            Modifier
                .size(220.dp)
                .offset(x = 190.dp, y = (-60).dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
        )
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Main wallet", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.22f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) { Text(ChainConfig.NETWORK_NAME, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium) }
            }
            if (loading || totalChf == null) {
                SkeletonBox(Modifier.padding(top = 10.dp).width(180.dp).height(34.dp))
            } else {
                Text("CHF ${Formats.money(totalChf)}", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                val ethPrice = BigDecimal("2789.10")
                Text(
                    "≈ ${Formats.token(totalChf.divide(ethPrice, 6, RoundingMode.HALF_UP), 4)} ETH",
                    color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp,
                )
            }
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(alpha = 0.25f))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                address?.let { Formats.shortAddress(it, 8, 8) } ?: "Loading address…",
                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
            )
            Icon(
                SwissIcons.Copy, "Copy address", tint = Color.White,
                modifier = Modifier.size(20.dp).clickable(enabled = address != null) { address?.let(onCopy) },
            )
            Icon(SwissIcons.Qr, "Show QR", tint = Color.White, modifier = Modifier.size(20.dp).clickable(onClick = onQr))
        }
    }
}

@Composable
private fun Segmented(selected: WalletTab, onSelect: (WalletTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SwissColors.Surface)
            .padding(4.dp)
    ) {
        WalletTab.entries.forEach { tab ->
            val sel = tab == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (sel) SwissColors.SurfaceHigh else Color.Transparent)
                    .clickable { onSelect(tab) },
                contentAlignment = Alignment.Center,
            ) {
                Text(tab.label, color = if (sel) Color.White else SwissColors.TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun TokenList(
    balances: List<TokenBalance>?,
    loading: Boolean,
    onToken: (String) -> Unit,
    onAddToken: () -> Unit,
) {
    SwissCard(Modifier.fillMaxWidth(), radius = 20.dp) {
        if (loading || balances == null) {
            repeat(Tokens.all.size) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    SkeletonBox(Modifier.size(38.dp), CircleShape)
                    Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SkeletonBox(Modifier.width(80.dp).height(14.dp))
                        SkeletonBox(Modifier.width(40.dp).height(10.dp))
                    }
                    SkeletonBox(Modifier.width(90.dp).height(14.dp))
                }
            }
        } else {
            balances.forEachIndexed { i, b ->
                if (i > 0) Divider1()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onToken(b.token.symbol) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TokenAvatar(b.token, 38.dp)
                    Column(Modifier.weight(1f)) {
                        Text(b.token.name, style = SwissType.BodyStrong)
                        Text(b.token.symbol, style = SwissType.Caption)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${Formats.token(b.amount)} ${b.token.symbol}", style = SwissType.BodyStrong)
                        Text("${Formats.money(b.valueChf)} CHF", style = SwissType.Caption)
                    }
                }
            }
        }
        Divider1()
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onAddToken)
                .padding(vertical = 13.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(SwissIcons.Plus, null, tint = SwissColors.Orange, modifier = Modifier.size(18.dp))
            Text("Add token", color = SwissColors.Orange, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
        }
    }
}
