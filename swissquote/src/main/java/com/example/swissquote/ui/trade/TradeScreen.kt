package com.example.swissquote.ui.trade

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.common.Formats
import com.example.core.designsystem.SwissColors
import com.example.core.designsystem.SwissIcons
import com.example.core.designsystem.SwissType
import com.example.core.designsystem.components.ChangeBlock
import com.example.core.designsystem.components.EmptyState
import com.example.core.designsystem.components.ErrorState
import com.example.core.designsystem.components.SkeletonBox
import com.example.swissquote.domain.model.Quote
import com.example.swissquote.domain.model.WatchlistSort
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeScreen(viewModel: TradeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SearchRow()
            TopTabs(selected = state.tab, onSelect = viewModel::onTabSelected)
            Text(
                text = state.updatedAt?.let { "Updated: " + SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it)) } ?: " ",
                style = SwissType.Caption.copy(color = SwissColors.TextSoft, fontSize = 11.sp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            if (state.tab != TradeTab.Watchlists) {
                EmptyState(
                    icon = SwissIcons.Trade,
                    title = "${state.tab.label} — coming soon",
                    message = "Only the Watchlists tab is wired in this concept.",
                )
            } else {
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Crossfade(targetState = when {
                        state.isLoading -> 0
                        state.error != null -> 1
                        else -> 2
                    }, label = "watchlist") { mode ->
                        when (mode) {
                            0 -> WatchlistSkeleton()
                            1 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                                ErrorState(
                                    title = "Market data unavailable",
                                    message = state.error.orEmpty(),
                                    onRetry = viewModel::onRetry,
                                )
                            }
                            else -> WatchlistCard(
                                quotes = state.quotes,
                                sort = state.sort,
                                onSort = viewModel::onSortClicked,
                            )
                        }
                    }
                }
            }
        }

        // Floating "+" button (bottom-right, like the app)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
                .size(52.dp)
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                .background(Color(0xFFF5F5F7))
                .clickable { },
            contentAlignment = Alignment.Center,
        ) {
            Icon(SwissIcons.Plus, contentDescription = "Add", tint = SwissColors.Ink)
        }
    }
}

@Composable
private fun SearchRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(SwissColors.Surface)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(SwissIcons.Search, null, tint = SwissColors.TextSoft, modifier = Modifier.size(20.dp))
            Text("Search", color = SwissColors.TextSecondary, fontSize = 15.sp)
        }
        Spacer(Modifier.width(12.dp))
        Icon(SwissIcons.UserCircle, contentDescription = "Profile", tint = SwissColors.TextSoft, modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun TopTabs(selected: TradeTab, onSelect: (TradeTab) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        TradeTab.entries.forEach { tab ->
            val isSel = tab == selected
            Column(
                modifier = Modifier.clickable { onSelect(tab) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    tab.label,
                    color = if (isSel) Color.White else Color(0xFFE4E4E8),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .height(3.dp)
                        .width(if (isSel) 72.dp else 0.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(SwissColors.Orange)
                )
            }
        }
    }
}

@Composable
private fun WatchlistCard(quotes: List<Quote>, sort: WatchlistSort, onSort: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 88.dp),
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    .background(SwissColors.Surface)
                    .padding(start = 16.dp, end = 12.dp)
                    .height(50.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Favorites", style = SwissType.ListTitle, modifier = Modifier.weight(1f))
                Text("Edit", color = SwissColors.Orange, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onSort).padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(SwissIcons.Sort, null, tint = SwissColors.Orange, modifier = Modifier.size(16.dp))
                    Text(sort.label, color = SwissColors.Orange, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(3) { Box(Modifier.size(4.dp).clip(CircleShape).background(SwissColors.Orange)) }
                }
            }
        }
        items(quotes, key = { it.ticker }) { q ->
            QuoteRow(q, isLast = q == quotes.last())
        }
    }
}

@Composable
private fun QuoteRow(q: Quote, isLast: Boolean) {
    val shape = if (isLast) RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp) else RoundedCornerShape(0.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SwissColors.Surface)
            .clickable { }
            .padding(start = 16.dp, end = 8.dp)
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(q.ticker, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text(q.name, color = SwissColors.TextSoft, fontSize = 9.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(Formats.money(q.price), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text(q.currency, color = SwissColors.TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(bottom = 2.dp))
        }
        ChangeBlock(q.changePercent)
        Icon(SwissIcons.ChevronRight, null, tint = SwissColors.TextSecondary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun WatchlistSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SwissColors.Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        SkeletonBox(Modifier.width(110.dp).height(20.dp))
        repeat(8) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkeletonBox(Modifier.width(56.dp).height(16.dp))
                    SkeletonBox(Modifier.width(120.dp).height(9.dp))
                }
                SkeletonBox(Modifier.width(80.dp).height(18.dp))
                Spacer(Modifier.width(10.dp))
                SkeletonBox(Modifier.width(70.dp).height(32.dp), RoundedCornerShape(4.dp))
            }
        }
    }
}
