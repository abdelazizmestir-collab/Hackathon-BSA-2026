package com.example.core.designsystem.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.SwissColors
import com.example.core.designsystem.SwissType

enum class SwissButtonStyle { Orange, Light, Outline, Dark, Text, Danger }

/**
 * Main button used everywhere. Shows a spinner (and blocks clicks) while [loading].
 */
@Composable
fun SwissButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: SwissButtonStyle = SwissButtonStyle.Orange,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    loading: Boolean = false,
    enabled: Boolean = true,
    height: Dp = 52.dp,
    textStyle: TextStyle = SwissType.Button,
) {
    val shape = RoundedCornerShape(12.dp)
    val (bg, fg) = when (style) {
        SwissButtonStyle.Orange -> SwissColors.Orange to Color.White
        SwissButtonStyle.Light -> SwissColors.Light to SwissColors.Ink
        SwissButtonStyle.Outline -> Color.Transparent to Color.White
        SwissButtonStyle.Dark -> SwissColors.Surface to Color.White
        SwissButtonStyle.Text -> Color.Transparent to SwissColors.TextSoft
        SwissButtonStyle.Danger -> SwissColors.Red.copy(alpha = 0.12f) to SwissColors.Red
    }
    val clickable = enabled && !loading
    var m = modifier
        .height(height)
        .clip(shape)
        .background(bg)
    if (style == SwissButtonStyle.Outline) m = m.border(1.5.dp, Color.White.copy(alpha = 0.85f), shape)
    m = m
        .alpha(if (enabled) 1f else 0.45f)
        .clickable(enabled = clickable, role = Role.Button, onClick = onClick)

    Box(modifier = m, contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = loading,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "button-loading",
        ) { isLoading ->
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = fg,
                    strokeWidth = 2.5.dp,
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (leadingIcon != null) Icon(leadingIcon, null, tint = fg, modifier = Modifier.size(20.dp))
                    Text(text, style = textStyle, color = fg)
                    if (trailingIcon != null) Icon(trailingIcon, null, tint = fg, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** Round 48dp icon button (bell, settings, search…), optional orange badge. */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    badge: Boolean = false,
    background: Color = SwissColors.Surface,
    tint: Color = Color.White,
) {
    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(background)
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(22.dp))
        }
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(SwissColors.Orange)
            )
        }
    }
}

/** Full-width helper. */
fun Modifier.fullWidth(): Modifier = this.fillMaxWidth()
