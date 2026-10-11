package com.example.swisswallet.ui.recovery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.common.Formats
import com.example.core.designsystem.SwissColors
import com.example.core.designsystem.SwissIcons
import com.example.core.designsystem.SwissType
import com.example.core.designsystem.components.IconTile
import com.example.core.designsystem.components.SwissCard
import com.example.core.designsystem.components.Tag
import com.example.swisswallet.domain.model.Guardian
import com.example.swisswallet.domain.model.GuardianKind
import com.example.swisswallet.domain.model.SendProgress
import com.example.swisswallet.util.copyToClipboard
import com.example.swisswallet.util.readClipboard
import com.example.swisswallet.util.shareText

@Composable
internal fun SectionLabel(text: String) {
    Text(text, style = SwissType.Label, modifier = Modifier.padding(start = 6.dp, top = 8.dp))
}

/** Plain-language explanation card. */
@Composable
internal fun ExplainCard(icon: ImageVector, title: String, text: String, color: Color = SwissColors.Blue) {
    SwissCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(icon, color, size = 36.dp)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
                Text(title, style = SwissType.BodyStrong)
                Text(text, style = SwissType.Body.copy(fontSize = 13.sp))
            }
        }
    }
}

internal fun GuardianKind.icon(): ImageVector = when (this) {
    GuardianKind.SWISSQUOTE -> SwissIcons.Bank
    GuardianKind.MY_DEVICE -> SwissIcons.Phone
    GuardianKind.CLOSE_ONE -> SwissIcons.Users
    GuardianKind.INSTITUTION -> SwissIcons.Shield
    GuardianKind.DEMO -> SwissIcons.Zap
}

internal fun GuardianKind.color(): Color = when (this) {
    GuardianKind.SWISSQUOTE -> SwissColors.Orange
    GuardianKind.MY_DEVICE -> SwissColors.Blue
    GuardianKind.CLOSE_ONE -> SwissColors.Green
    GuardianKind.INSTITUTION -> SwissColors.TextSoft
    GuardianKind.DEMO -> SwissColors.TextSecondary
}

/** One guardian line: icon · name / kind · address · trailing (check, remove…). */
@Composable
internal fun GuardianRow(guardian: Guardian, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(guardian.kind.icon(), guardian.kind.color(), size = 38.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(guardian.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${guardian.kind.label} · ${Formats.shortAddress(guardian.address)}", style = SwissType.Caption)
        }
        trailing()
    }
}

@Composable
internal fun EmptyGuardianSlot(index: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).border(1.5.dp, SwissColors.Line, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(SwissIcons.Plus, null, tint = SwissColors.TextSecondary, modifier = Modifier.size(18.dp)) }
        Text("Add guardian #$index", color = SwissColors.TextSoft, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Icon(SwissIcons.ChevronRight, null, tint = SwissColors.TextSecondary, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun CheckBadge(done: Boolean) {
    if (done) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(SwissColors.Green), contentAlignment = Alignment.Center) {
            Icon(SwissIcons.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
        }
    } else {
        Box(Modifier.size(26.dp).clip(CircleShape).border(1.5.dp, SwissColors.Line, CircleShape))
    }
}

/** A code to send to someone (SWREC1. / SWSIG1. / SWGRD1.), with Copy + Share. */
@Composable
internal fun CodeCard(title: String, code: String, hint: String, onCopied: (String) -> Unit) {
    val context = LocalContext.current
    SwissCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = SwissType.BodyStrong)
            Text(
                code,
                color = SwissColors.TextSoft,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SwissColors.SurfaceHigh)
                    .padding(10.dp),
            )
            Text(hint, style = SwissType.Caption)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallAction(SwissIcons.Copy, "Copy") {
                    context.copyToClipboard(title, code)
                    onCopied("Code copied")
                }
                SmallAction(SwissIcons.Share, "Share") { context.shareText(code, title) }
            }
        }
    }
}

@Composable
internal fun SmallAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SwissColors.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** Text input styled like the Send screen, with an optional "Paste" button. */
@Composable
internal fun RecoveryField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    error: String? = null,
    monospace: Boolean = false,
    pasteButton: Boolean = false,
) {
    val context = LocalContext.current
    val style = TextStyle(
        color = Color.White,
        fontSize = if (monospace) 13.sp else 15.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SwissColors.Surface)
                .border(1.dp, if (error != null) SwissColors.Red else SwissColors.Line, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = style,
                cursorBrush = SolidColor(SwissColors.Orange),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) Text(placeholder, style = style.copy(color = SwissColors.TextSecondary, fontFamily = FontFamily.Default))
                        inner()
                    }
                },
            )
            if (pasteButton) {
                Text(
                    "Paste",
                    color = SwissColors.Orange,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                        context.readClipboard()?.trim()?.let(onValueChange)
                    }.padding(4.dp),
                )
            }
        }
        if (error != null) Text(error, color = SwissColors.Red, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
    }
}

/** Human description of each ERC-4337 step. */
internal fun SendProgress.describe(): String = when (this) {
    SendProgress.Preparing -> "Preparing the operation…"
    SendProgress.Sponsoring -> "Pimlico checks it and pays the gas…"
    SendProgress.Signing -> "Signing with your passkey…"
    SendProgress.Submitting -> "Sending to the bundler…"
    is SendProgress.Pending -> "Waiting for the next block…"
    is SendProgress.Confirmed -> "Done ✓"
    is SendProgress.Failed -> message
}

/** Inline status of a running on-chain operation. */
@Composable
internal fun OperationStatus(progress: SendProgress?) {
    if (progress == null) return
    val failed = progress is SendProgress.Failed
    val done = progress is SendProgress.Confirmed
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background((if (failed) SwissColors.Red else if (done) SwissColors.Green else SwissColors.Orange).copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            failed -> Icon(SwissIcons.Alert, null, tint = SwissColors.Red, modifier = Modifier.size(18.dp))
            done -> Icon(SwissIcons.Check, null, tint = SwissColors.Green, modifier = Modifier.size(18.dp))
            else -> CircularProgressIndicator(Modifier.size(18.dp), color = SwissColors.Orange, strokeWidth = 2.dp)
        }
        Text(
            progress.describe(),
            color = if (failed) SwissColors.Red else if (done) SwissColors.Green else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
internal fun RuleTags(threshold: Int, count: Int, delaySeconds: Long) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tag("$threshold of $count guardians", SwissColors.Orange, leading = SwissIcons.Users)
        Tag("${delaySeconds}s safety delay", SwissColors.Blue, leading = SwissIcons.Clock)
    }
}

internal val SendProgress?.isRunning: Boolean
    get() = this != null && this !is SendProgress.Confirmed && this !is SendProgress.Failed
