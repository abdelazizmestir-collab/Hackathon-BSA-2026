package com.example.swisswallet.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.common.Formats
import com.example.core.designsystem.SwissColors
import com.example.core.designsystem.SwissIcons
import com.example.core.designsystem.SwissType
import com.example.core.designsystem.components.ChangePill
import com.example.core.designsystem.components.CoinAvatar
import com.example.core.designsystem.components.SkeletonBox
import com.example.swisswallet.domain.model.Token
import com.example.swisswallet.domain.model.TokenBalance

/** "Home ………… (bell) (gear)" header used by the 4 tab screens. */
@Composable
fun ScreenHeader(title: String, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = SwissType.Display, modifier = Modifier.weight(1f))
        actions()
    }
}

/** Back arrow + title for full-screen flows (Send, Receive). */
@Composable
fun FlowTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(SwissIcons.Back, contentDescription = "Back", tint = Color.White)
        }
        Spacer(Modifier.width(4.dp))
        Text(title, style = SwissType.Section)
    }
}

@Composable
fun TokenAvatar(token: Token, size: androidx.compose.ui.unit.Dp = 44.dp) =
    CoinAvatar(token.glyph, Color(token.color), size)

/** Asset row on Home (rounded card) — "Ethereum / 0.05 ETH ……… 139.45 CHF ▲3.8%". */
@Composable
fun AssetRow(balance: TokenBalance, hidden: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SwissColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TokenAvatar(balance.token)
        Column(Modifier.weight(1f)) {
            Text(balance.token.name, style = SwissType.ListTitle)
            Text(
                if (hidden) "•••• ${balance.token.symbol}" else "${Formats.token(balance.amount)} ${balance.token.symbol}",
                style = SwissType.Caption.copy(fontSize = 13.sp),
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (hidden) "•••• CHF" else "${Formats.money(balance.valueChf)} CHF", style = SwissType.ListTitle)
            ChangePill(balance.price.change24hPercent)
        }
    }
}

@Composable
fun AssetRowSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SwissColors.Surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SkeletonBox(Modifier.size(44.dp), CircleShape)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SkeletonBox(Modifier.width(90.dp).height(16.dp))
            SkeletonBox(Modifier.width(70.dp).height(12.dp))
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SkeletonBox(Modifier.width(80.dp).height(16.dp))
            SkeletonBox(Modifier.width(52.dp).height(18.dp), RoundedCornerShape(12.dp))
        }
    }
}

/** Smooth green area chart (same look as the Figma balance card). */
@Composable
fun BalanceChart(points: List<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val min = points.min()
        val max = points.max()
        val span = (max - min).takeIf { it > 0f } ?: 1f
        val top = size.height * 0.12f
        val usable = size.height * 0.62f
        val stepX = size.width / (points.size - 1)
        val pts = points.mapIndexed { i, v -> Offset(i * stepX, top + (1f - (v - min) / span) * usable) }

        val line = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            for (i in 1 until pts.size) {
                val p0 = pts[i - 1]
                val p1 = pts[i]
                val cx = (p0.x + p1.x) / 2f
                cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
            }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            area,
            Brush.verticalGradient(listOf(SwissColors.Green.copy(alpha = 0.55f), SwissColors.Green.copy(alpha = 0f))),
        )
        drawPath(line, SwissColors.GreenLine, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Info banner (green = good, orange = warning). */
@Composable
fun InfoBanner(text: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector = SwissIcons.Shield, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Text(text, color = color, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
    }
}
