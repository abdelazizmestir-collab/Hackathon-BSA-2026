package com.example.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Colour tokens taken 1:1 from the Figma file (dark theme, Swissquote orange accent). */
object SwissColors {
    val Background = Color(0xFF0B0B0D)
    val Surface = Color(0xFF1C1C20)
    val SurfaceHigh = Color(0xFF2A2A30)
    val Line = Color(0xFF2C2C31)
    val NavBar = Color(0xFF111113)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF9B9BA3)
    val TextSoft = Color(0xFFD9D9DE)
    val Orange = Color(0xFFF25C2A)
    val OrangeLight = Color(0xFFFF7A45)
    val OrangeDark = Color(0xFFB8360F)
    val Green = Color(0xFF16B364)
    val GreenLine = Color(0xFF3DDC84)
    val Red = Color(0xFFF04438)
    val Blue = Color(0xFF5B9CFF)
    val Light = Color(0xFFF2F2F5)
    val Ink = Color(0xFF111113)
}

/** Text styles used across both apps. */
object SwissType {
    val Display = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, color = SwissColors.TextPrimary)
    val Title = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = SwissColors.TextPrimary)
    val Headline = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = SwissColors.TextPrimary, lineHeight = 32.sp)
    val Section = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = SwissColors.TextPrimary)
    val Body = TextStyle(fontSize = 14.sp, color = SwissColors.TextSoft, lineHeight = 20.sp)
    val BodyStrong = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SwissColors.TextPrimary)
    val ListTitle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = SwissColors.TextPrimary)
    val Caption = TextStyle(fontSize = 12.sp, color = SwissColors.TextSecondary)
    val Label = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SwissColors.TextSecondary)
    val Tab = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
    val Button = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
}

private val SwissColorScheme = darkColorScheme(
    primary = SwissColors.Orange,
    onPrimary = Color.White,
    secondary = SwissColors.Green,
    onSecondary = Color.White,
    background = SwissColors.Background,
    onBackground = SwissColors.TextPrimary,
    surface = SwissColors.Surface,
    onSurface = SwissColors.TextPrimary,
    surfaceVariant = SwissColors.SurfaceHigh,
    onSurfaceVariant = SwissColors.TextSecondary,
    surfaceContainer = SwissColors.Surface,
    surfaceContainerHigh = SwissColors.SurfaceHigh,
    surfaceContainerLow = SwissColors.Surface,
    error = SwissColors.Red,
    onError = Color.White,
    outline = SwissColors.Line,
    outlineVariant = SwissColors.Line,
    inverseSurface = SwissColors.Light,
    inverseOnSurface = SwissColors.Ink,
)

@Composable
fun SwissTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SwissColorScheme,
        typography = Typography(),
        content = content,
    )
}
