package com.example.swissquote.ui.welcome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.designsystem.SwissColors
import com.example.core.designsystem.SwissType
import com.example.core.designsystem.components.SwissButton
import com.example.core.designsystem.components.SwissButtonStyle

@Composable
fun WelcomeScreen(viewModel: WelcomeViewModel, onFinished: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.done.collect { onFinished() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Placeholder logo — replace with the official asset if you have the rights.
            Box(Modifier.size(38.dp).clip(CircleShape).background(SwissColors.Orange))
            Spacer(Modifier.size(10.dp))
            Text("Swissquote", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "Access millions of trading products",
            style = SwissType.Headline.copy(fontSize = 27.sp, lineHeight = 33.sp),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Reach your investment goals with a wide range of attractive financial products",
            style = SwissType.Body,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        TradingIllustration(Modifier.size(240.dp))
        Spacer(Modifier.height(16.dp))
        PagerDots(count = 5, selected = 0)
        Spacer(Modifier.height(28.dp))

        val busy = state.pending != null
        SwissButton(
            text = "Open your account",
            onClick = { viewModel.onAction(WelcomeAction.OpenAccount) },
            style = SwissButtonStyle.Light,
            loading = state.pending == WelcomeAction.OpenAccount,
            enabled = !busy || state.pending == WelcomeAction.OpenAccount,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        SwissButton(
            text = "Login",
            onClick = { viewModel.onAction(WelcomeAction.Login) },
            style = SwissButtonStyle.Outline,
            loading = state.pending == WelcomeAction.Login,
            enabled = !busy || state.pending == WelcomeAction.Login,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        SwissButton(
            text = "Discover the app",
            onClick = { viewModel.onAction(WelcomeAction.Discover) },
            style = SwissButtonStyle.Text,
            loading = state.pending == WelcomeAction.Discover,
            enabled = !busy || state.pending == WelcomeAction.Discover,
            modifier = Modifier.fillMaxWidth(),
            height = 46.dp,
        )
        state.error?.let {
            Text(it, color = SwissColors.Red, style = SwissType.Caption, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PagerDots(count: Int, selected: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (i == selected) 1f else 0.35f))
            )
        }
    }
}

/** The orange arrow over a dark disc with bars — redrawn from the Figma (240x240 design units). */
@Composable
private fun TradingIllustration(modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val words = listOf("FOREX & CFDS", "FUNDS  ETFS", "OPTIONS & FUTURES", "STOCKS  BONDS", "WARRANTS  ESG", "CRYPTOS")
    Canvas(modifier) {
        val s = size.width / 240f
        val center = Offset(120f * s, 124f * s)
        val radius = 102f * s

        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0xFF4A2216), Color(0xFF2A1610)), center = center, radius = radius),
            radius = radius,
            center = center,
        )
        val disc = Path().apply { addOval(Rect(center, radius)) }
        clipPath(disc) {
            words.forEachIndexed { i, w ->
                drawText(
                    textMeasurer = measurer,
                    text = w,
                    topLeft = Offset((32f + (i % 2) * 6f) * s, (60f + i * 22f) * s),
                    style = TextStyle(
                        color = Color.White.copy(alpha = 0.32f),
                        fontSize = (12.5f * s).toSp(),
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
            val bars = listOf(
                floatArrayOf(78f, 172f, 24f), floatArrayOf(114f, 152f, 24f), floatArrayOf(150f, 162f, 24f),
                floatArrayOf(168f, 122f, 22f), floatArrayOf(196f, 92f, 22f),
            )
            bars.forEach { (x, y, w) ->
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(SwissColors.Orange.copy(alpha = 0.35f), SwissColors.Orange.copy(alpha = 0.02f)),
                        startY = y * s, endY = 226f * s,
                    ),
                    topLeft = Offset(x * s, y * s),
                    size = androidx.compose.ui.geometry.Size(w * s, (226f - y) * s),
                )
            }
        }
        val arrow = Path().apply {
            moveTo(24f * s, 196f * s); lineTo(92f * s, 118f * s); lineTo(124f * s, 150f * s); lineTo(198f * s, 48f * s)
        }
        drawPath(arrow, SwissColors.Orange, style = Stroke(width = 22f * s, join = StrokeJoin.Miter))
        val head = Path().apply {
            moveTo(222f * s, 14f * s); lineTo(216f * s, 64f * s); lineTo(176f * s, 36f * s); close()
        }
        drawPath(head, SwissColors.Orange)
    }
}
