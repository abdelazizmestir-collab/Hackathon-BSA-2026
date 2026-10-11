package com.example.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.designsystem.SwissColors
import com.example.core.designsystem.SwissIcons
import com.example.core.designsystem.SwissType
import kotlin.math.abs

/** Rounded dark card (#1C1C20) used for lists and groups. */
@Composable
fun SwissCard(
    modifier: Modifier = Modifier,
    radius: Dp = 18.dp,
    color: Color = SwissColors.Surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .background(color),
        content = content,
    )
}

@Composable
fun Divider1(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(SwissColors.Line))
}

/** Swissquote watchlist chip: solid green/red block with the % change. */
@Composable
fun ChangeBlock(percent: Double, modifier: Modifier = Modifier) {
    val bg = if (percent >= 0) SwissColors.Green else SwissColors.Red
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .size(width = 70.dp, height = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = String.format(java.util.Locale.US, "%.2f%%", abs(percent)),
            color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium,
        )
    }
}

/** SwissWallet pill: ▲ 3.8% on tinted background. */
@Composable
fun ChangePill(percent: Double, modifier: Modifier = Modifier) {
    val up = percent >= 0
    val c = if (up) SwissColors.Green else SwissColors.Red
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(c.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = (if (up) "▲ " else "▼ ") + String.format(java.util.Locale.US, "%.1f%%", abs(percent)),
            color = c, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Small status / info tag. */
@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier, leading: ImageVector? = null) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (leading != null) Icon(leading, null, tint = color, modifier = Modifier.size(12.dp))
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Coloured coin avatar (₿ Ξ ◎ $). */
@Composable
fun CoinAvatar(glyph: String, color: Color, size: Dp = 44.dp) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.48f).sp)
    }
}

/** Rounded square tinted icon (used in lists). */
@Composable
fun IconTile(icon: ImageVector, tint: Color, size: Dp = 40.dp, background: Color = tint.copy(alpha = 0.15f), round: Boolean = true) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(if (round) CircleShape else RoundedCornerShape(10.dp))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun SwissSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = SwissColors.Orange,
            checkedBorderColor = SwissColors.Orange,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = SwissColors.SurfaceHigh,
            uncheckedBorderColor = SwissColors.SurfaceHigh,
        ),
    )
}

/** Error screen/card with a retry button. */
@Composable
fun ErrorState(
    title: String,
    message: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconTile(SwissIcons.Alert, SwissColors.Red, size = 56.dp)
        Spacer(Modifier.height(16.dp))
        Text(title, style = SwissType.ListTitle, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = SwissType.Body, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Spacer(Modifier.height(20.dp))
            SwissButton("Try again", onRetry, style = SwissButtonStyle.Dark, modifier = Modifier.width(180.dp), height = 46.dp)
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconTile(icon, SwissColors.TextSecondary, size = 56.dp, background = SwissColors.SurfaceHigh)
        Spacer(Modifier.height(14.dp))
        Text(title, style = SwissType.BodyStrong, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(message, style = SwissType.Caption, textAlign = TextAlign.Center)
    }
}

/** "My assets ……… See all >" header. */
@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = SwissType.Section, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickableNoRole(onAction)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(action, style = SwissType.Body.copy(color = SwissColors.TextSecondary))
                Icon(SwissIcons.ChevronRight, null, tint = SwissColors.TextSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

internal fun Modifier.clickableNoRole(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)
