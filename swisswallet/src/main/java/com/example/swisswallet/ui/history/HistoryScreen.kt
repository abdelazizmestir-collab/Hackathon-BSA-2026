package com.example.swisswallet.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.core.designsystem.components.IconTile
import com.example.core.designsystem.components.SwissButton
import com.example.core.designsystem.components.SwissButtonStyle
import com.example.core.designsystem.components.SwissCard
import com.example.core.designsystem.components.Tag
import com.example.swisswallet.config.ChainConfig
import com.example.swisswallet.domain.model.TxKind
import com.example.swisswallet.domain.model.TxRecord
import com.example.swisswallet.domain.model.TxStatus
import com.example.swisswallet.ui.components.ScreenHeader
import com.example.swisswallet.util.copyToClipboard
import com.example.swisswallet.util.openUrl
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.onAppear() }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("History") {
            CircleIconButton(SwissIcons.Search, "Search", onClick = { })
        }
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HistoryFilter.entries.forEach { f ->
                val sel = f == state.filter
                Box(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (sel) SwissColors.Orange else SwissColors.Surface)
                        .clickable { viewModel.onFilter(f) }
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Text(f.label, color = if (sel) Color.White else SwissColors.TextSoft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        PullToRefreshBox(isRefreshing = state.isSyncing, onRefresh = viewModel::onRefresh, modifier = Modifier.fillMaxSize()) {
            if (!state.isLoading && state.sections.isEmpty()) {
                EmptyState(SwissIcons.History, "No transactions", "Transfers you make will appear here.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    state.sections.forEach { section ->
                        item(key = "h-" + section.title) {
                            Text(section.title, style = SwissType.Label, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                        }
                        item(key = "g-" + section.title) {
                            SwissCard(Modifier.fillMaxWidth()) {
                                section.items.forEachIndexed { i, tx ->
                                    if (i > 0) Divider1()
                                    TxRow(tx) { viewModel.onSelect(tx) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val selected = state.selected
    if (selected != null) {
        TxDetailSheet(selected, onDismiss = { viewModel.onSelect(null) })
    }
}

private data class TxVisual(val icon: ImageVector, val color: Color, val title: String)

private fun TxRecord.visual(): TxVisual = when (kind) {
    TxKind.RECEIVED -> TxVisual(SwissIcons.ArrowDownLeft, SwissColors.Green, "Received · $tokenSymbol")
    TxKind.SENT -> TxVisual(SwissIcons.ArrowUpRight, SwissColors.Red, "Sent · $tokenSymbol")
    TxKind.SWAP -> TxVisual(SwissIcons.Swap, SwissColors.Orange, "Swap · $counterparty")
    TxKind.DEPOSIT -> TxVisual(SwissIcons.Bank, SwissColors.Blue, "Deposit $tokenSymbol")
}

private fun TxRecord.subtitle(): String {
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    val who = when (kind) {
        TxKind.SENT -> "To " + Formats.shortAddress(counterparty)
        TxKind.RECEIVED -> "From " + if (counterparty.startsWith("0x")) Formats.shortAddress(counterparty) else counterparty
        TxKind.DEPOSIT -> "From $counterparty"
        TxKind.SWAP -> "Swapped"
    }
    return "$who · $time"
}

@Composable
private fun TxRow(tx: TxRecord, onClick: () -> Unit) {
    val v = tx.visual()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(v.icon, v.color)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(v.title, style = SwissType.BodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (tx.isDemo) Tag("Demo", SwissColors.TextSecondary)
            }
            Text(tx.subtitle(), style = SwissType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            val sign = if (tx.kind == TxKind.SENT) "−" else "+"
            Text(
                "$sign${tx.amount} ${tx.tokenSymbol}",
                color = if (tx.kind == TxKind.SENT) Color.White else SwissColors.Green,
                fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold,
            )
            StatusText(tx.status)
        }
    }
}

@Composable
private fun StatusText(status: TxStatus) {
    val (label, color) = when (status) {
        TxStatus.PENDING -> "Pending" to SwissColors.Orange
        TxStatus.CONFIRMED -> "Completed" to SwissColors.TextSecondary
        TxStatus.FAILED -> "Failed" to SwissColors.Red
    }
    Text(label, color = color, fontSize = 12.sp, fontWeight = if (status == TxStatus.CONFIRMED) FontWeight.Normal else FontWeight.SemiBold)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TxDetailSheet(tx: TxRecord, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = SwissColors.Surface) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val v = tx.visual()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(v.icon, v.color, size = 48.dp)
                Column {
                    Text(v.title, style = SwissType.ListTitle)
                    StatusText(tx.status)
                }
            }
            DetailLine("Amount", "${tx.amount} ${tx.tokenSymbol}")
            DetailLine(if (tx.kind == TxKind.SENT) "To" else "From", tx.counterparty, copyable = tx.counterparty.startsWith("0x"))
            DetailLine("Date", SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.ENGLISH).format(Date(tx.timestamp)))
            DetailLine("Network fee", if (tx.isDemo) "—" else "0 (sponsored by Pimlico)")
            tx.userOpHash?.let { DetailLine("UserOp hash", it, copyable = true) }
            tx.txHash?.let { DetailLine("Tx hash", it, copyable = true) }
            tx.error?.let { DetailLine("Error", it) }
            if (tx.isDemo) {
                Text("Demo entry — not an on-chain transaction.", style = SwissType.Caption)
            }
            val hash = tx.txHash
            if (hash != null) {
                SwissButton(
                    "View on Etherscan", { context.openUrl(ChainConfig.txUrl(hash)) },
                    Modifier.fillMaxWidth(), style = SwissButtonStyle.Outline, trailingIcon = SwissIcons.External,
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String, copyable: Boolean = false) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.Top) {
        Text(label, style = SwissType.Caption.copy(fontSize = 13.sp), modifier = Modifier.weight(0.35f))
        Text(
            value,
            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier
                .weight(0.65f)
                .then(if (copyable) Modifier.clickable { context.copyToClipboard(label, value) } else Modifier),
        )
    }
}
