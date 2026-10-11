package com.example.core.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.core.designsystem.SwissColors

/**
 * Skeleton placeholder with an animated shimmer — used for every loading state
 * (instead of an empty screen + spinner).
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer-progress",
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(SwissColors.SurfaceHigh)
            .drawWithContent {
                drawContent()
                val w = size.width
                val x = -w + progress * 3f * w
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.07f), Color.Transparent),
                        start = Offset(x, 0f),
                        end = Offset(x + w, size.height),
                    )
                )
            }
    )
}

@Composable
fun SwissSpinner(size: Dp = 28.dp, color: Color = SwissColors.Orange, modifier: Modifier = Modifier) {
    CircularProgressIndicator(modifier = modifier.size(size), color = color, strokeWidth = 3.dp, trackColor = SwissColors.SurfaceHigh)
}
