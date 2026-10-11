package com.example.swisswallet.ui.home

import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.common.Formats
import com.example.core.designsystem.SwissColors
import com.example.core.designsystem.SwissIcons
import com.example.core.designsystem.SwissType
import com.example.core.designsystem.components.ChangePill
import com.example.core.designsystem.components.CircleIconButton
import com.example.core.designsystem.components.ErrorState
import com.example.core.designsystem.components.SectionHeader
import com.example.core.designsystem.components.SkeletonBox
import com.example.swisswallet.domain.model.ChartRange
import com.example.swisswallet.domain.model.Portfolio
import com.example.swisswallet.ui.components.AssetRow
import com.example.swisswallet.ui.components.AssetRowSkeleton
import com.example.swisswallet.ui.components.BalanceChart
import com.example.swisswallet.ui.components.ScreenHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onSend: () -> Unit,
    onReceive: () -> Unit,
    onSeeAll: () -> Unit,
    onSettings: () -> Unit,
    onAsset: (String) -> Unit,
    showMessage: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.onAppear() }
    LaunchedEffect(viewModel) { viewModel.messages.collect { showMessage(it) } }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Home") {
            CircleIconButton(SwissIcons.Bell, "Notifications", onClick = { showMessage("No new notifications") }, badge = true)
            CircleIconButton(SwissIcons.Settings, "Settings", onClick = onSettings)
        }

        if (state.error != null && state.portfolio == null) {
            ErrorState("Couldn't load your wallet", state.error.orEmpty(), onRetry = viewModel::onRetry)
            return@Column
        }

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "balance") {
                    BalanceCard(
                        portfolio = state.portfolio,
                        loading = state.isLoading,
                        hidden = state.hideBalance,
                        chart = state.chart,
                        range = state.range,
                        onToggleHide = viewModel::onToggleHide,
                        onRange = viewModel::onRangeSelected,
                    )
                }
                item(key = "actions") {
                    Row(Modifier.fillMaxWidth().height(98.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionTile("Deposit", SwissIcons.ArrowDown, onReceive, Modifier.weight(1f))
                        ActionTile("Swap", SwissIcons.Swap, { showMessage("Swap is simulated in this concept") }, Modifier.weight(1f))
                        ActionTile("Send", SwissIcons.ArrowUp, onSend, Modifier.weight(1f))
                    }
                }
                item(key = "assets-header") {
                    SectionHeader("My assets", action = "See all", onAction = onSeeAll, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
                }
                val portfolio = state.portfolio
                if (state.isLoading || portfolio == null) {
                    items(2) { AssetRowSkeleton() }
                } else {
                    items(portfolio.balances, key = { it.token.symbol }) { b ->
                        AssetRow(b, hidden = state.hideBalance, onClick = { onAsset(b.token.symbol) })
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceCard(
    portfolio: Portfolio?,
    loading: Boolean,
    hidden: Boolean,
    chart: List<Float>,
    range: ChartRange,
    onToggleHide: () -> Unit,
    onRange: (ChartRange) -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(268.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(SwissColors.Surface)
    ) {
        Crossfade(targetState = loading || portfolio == null, label = "balance") { isLoading ->
            if (isLoading) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SkeletonBox(Modifier.width(100.dp).height(14.dp))
                    SkeletonBox(Modifier.width(210.dp).height(40.dp))
                    SkeletonBox(Modifier.width(140.dp).height(18.dp))
                    SkeletonBox(Modifier.fillMaxWidth().height(110.dp), RoundedCornerShape(14.dp))
                }
            } else if (portfolio != null) {
                Box(Modifier.fillMaxSize()) {
                    BalanceChart(chart, Modifier.fillMaxWidth().height(140.dp).align(Alignment.BottomCenter))
                    Column(Modifier.padding(start = 22.dp, top = 22.dp, end = 22.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Row(
                                Modifier.weight(1f).clickable(onClick = onToggleHide),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text("Total balance", style = SwissType.Body.copy(color = SwissColors.TextSecondary))
                                Icon(if (hidden) SwissIcons.EyeOff else SwissIcons.Eye, "Hide balance", tint = SwissColors.TextSecondary, modifier = Modifier.size(16.dp))
                            }
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SwissColors.SurfaceHigh)
                                    .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text("CHF", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Icon(SwissIcons.ChevronDown, null, tint = SwissColors.TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("CHF", color = SwissColors.TextSecondary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
                            Text(
                                if (hidden) "••••••" else Formats.money(portfolio.totalChf),
                                color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val change = portfolio.change24hChf
                            Text(
                                if (hidden) "•••• CHF" else "${Formats.signedMoney(change)} CHF",
                                color = if (change.signum() >= 0) SwissColors.Green else SwissColors.Red,
                                fontSize = 15.sp, fontWeight = FontWeight.Medium,
                            )
                            ChangePill(portfolio.change24hPercent)
                        }
                    }
                    Row(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ChartRange.entries.forEach { r ->
                            val sel = r == range
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (sel) SwissColors.Ink.copy(alpha = 0.85f) else Color.Transparent)
                                    .clickable { onRange(r) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(r.label, color = if (sel) Color.White else SwissColors.TextSoft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionTile(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(22.dp))
            .background(SwissColors.Surface)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = SwissColors.Ink, modifier = Modifier.size(22.dp))
        }
        Text(label, style = SwissType.BodyStrong, modifier = Modifier.padding(top = 8.dp))
    }
}
