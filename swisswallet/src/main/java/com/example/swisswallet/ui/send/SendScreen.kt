package com.example.swisswallet.ui.send

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.common.Formats
import com.example.core.designsystem.SwissColors
import com.example.core.designsystem.SwissIcons
import com.example.core.designsystem.SwissType
import com.example.core.designsystem.components.Divider1
import com.example.core.designsystem.components.IconTile
import com.example.core.designsystem.components.SkeletonBox
import com.example.core.designsystem.components.SwissButton
import com.example.core.designsystem.components.SwissButtonStyle
import com.example.core.designsystem.components.SwissCard
import com.example.swisswallet.config.ChainConfig
import com.example.swisswallet.domain.model.SendProgress
import com.example.swisswallet.domain.model.Tokens
import com.example.swisswallet.security.AuthOutcome
import com.example.swisswallet.security.DeviceAuth
import com.example.swisswallet.ui.components.FlowTopBar
import com.example.swisswallet.ui.components.InfoBanner
import com.example.swisswallet.ui.components.TokenAvatar
import com.example.swisswallet.util.openUrl
import com.example.swisswallet.util.readClipboard
import kotlinx.coroutines.delay

@Composable
fun SendScreen(
    viewModel: SendViewModel,
    onClose: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Back: Review → Form; block back while signing / submitting (nothing half-sent).
    BackHandler(enabled = state.step == SendStep.Review || state.isBusyBeforeBroadcast) {
        if (state.step == SendStep.Review) viewModel.onBackToForm()
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        FlowTopBar(
            title = when (state.step) {
                SendStep.Form -> "Send"
                SendStep.Review -> "Review"
                SendStep.Progress -> "Sending"
            },
            onBack = {
                when {
                    state.step == SendStep.Review -> viewModel.onBackToForm()
                    state.isBusyBeforeBroadcast -> Unit
                    else -> onClose()
                }
            },
        )
        AnimatedContent(
            targetState = state.step,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "send-step",
            modifier = Modifier.fillMaxSize(),
        ) { step ->
            when (step) {
                SendStep.Form -> FormStep(state, viewModel)
                SendStep.Review -> ReviewStep(state, viewModel::onConfirm)
                SendStep.Progress -> ProgressStep(state, onClose, onOpenHistory, viewModel::onRetry)
            }
        }
    }
}

// ------------------------------------------------------------------------------------------
// 1. Form
// ------------------------------------------------------------------------------------------

@Composable
private fun FormStep(state: SendUiState, vm: SendViewModel) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Label("Asset")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tokens.all.forEach { token ->
                    val selected = token.symbol == state.token.symbol
                    val balance = state.balances.firstOrNull { it.token.symbol == token.symbol }
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(SwissColors.Surface)
                            .border(1.5.dp, if (selected) SwissColors.Orange else Color.Transparent, RoundedCornerShape(16.dp))
                            .clickable { vm.onTokenSelected(token) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TokenAvatar(token, 34.dp)
                        Column {
                            Text(token.symbol, style = SwissType.BodyStrong)
                            if (state.balancesLoading) {
                                SkeletonBox(Modifier.width(56.dp).height(10.dp))
                            } else {
                                Text(balance?.let { Formats.token(it.amount, 5) } ?: "—", style = SwissType.Caption)
                            }
                        }
                    }
                }
            }
            state.balancesError?.let {
                InfoBanner("Couldn't load balances: $it — tap to retry", SwissColors.Orange, SwissIcons.Alert, Modifier.clickable { vm.loadBalances() })
            }

            Label("To (public address)")
            InputField(
                value = state.recipient,
                onValueChange = vm::onRecipientChanged,
                placeholder = "0x…",
                error = state.recipientError,
                keyboardType = KeyboardType.Ascii,
                trailing = {
                    Text(
                        "Paste",
                        color = SwissColors.Orange, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { context.readClipboard()?.let(vm::onRecipientChanged) }.padding(4.dp),
                    )
                },
            )

            Label("Amount")
            InputField(
                value = state.amount,
                onValueChange = vm::onAmountChanged,
                placeholder = "0.00",
                error = state.amountError,
                keyboardType = KeyboardType.Decimal,
                textStyle = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(state.token.symbol, color = SwissColors.TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SwissColors.Orange.copy(alpha = 0.15f))
                                .clickable(enabled = !state.balancesLoading) { vm.onMax() }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) { Text("Max", color = SwissColors.Orange, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    }
                },
            )
            Text(
                state.amountChf?.let { "≈ ${Formats.money(it)} CHF" } ?: " ",
                style = SwissType.Caption, modifier = Modifier.padding(start = 4.dp),
            )

            InfoBanner("Network fee: 0 — gas sponsored by Pimlico (ERC-4337 paymaster)", SwissColors.Green, SwissIcons.Zap)
        }
        SwissButton(
            "Review",
            vm::onReview,
            Modifier.fillMaxWidth().padding(16.dp),
            enabled = state.canReview,
        )
    }
}

@Composable
private fun Label(text: String) = Text(text, style = SwissType.Label, modifier = Modifier.padding(start = 4.dp, top = 4.dp))

@Composable
private fun InputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    error: String?,
    keyboardType: KeyboardType,
    textStyle: TextStyle = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium),
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SwissColors.Surface)
                .border(1.dp, if (error != null) SwissColors.Red else SwissColors.Line, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = textStyle,
                cursorBrush = SolidColor(SwissColors.Orange),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) Text(placeholder, style = textStyle.copy(color = SwissColors.TextSecondary))
                        inner()
                    }
                },
            )
            trailing?.invoke()
        }
        if (error != null) {
            Text(error, color = SwissColors.Red, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

// ------------------------------------------------------------------------------------------
// 2. Review
// ------------------------------------------------------------------------------------------

@Composable
private fun ReviewStep(state: SendUiState, onConfirm: () -> Unit) {
    val activity = LocalContext.current.findActivity()
    val authAvailable = activity != null && DeviceAuth.isAvailable(activity)
    var askExtraConfirmation by remember { mutableStateOf(false) }

    // Every payment: the phone authenticates its user before anything is signed.
    val authenticateThenSend: () -> Unit = {
        if (activity == null) {
            onConfirm()
        } else {
            DeviceAuth.confirm(
                activity,
                "Confirm payment",
                "${state.amount} ${state.token.symbol} to ${Formats.shortAddress(state.recipient, 8, 6)}",
            ) { outcome ->
                if (outcome != AuthOutcome.Cancelled) onConfirm()
            }
        }
    }
    // Large, over the daily total, or to a new recipient: stop and confirm a second time first.
    val onConfirmClick: () -> Unit = {
        if (state.safeguardReasons.isEmpty()) {
            authenticateThenSend()
        } else {
            askExtraConfirmation = true
        }
    }

    if (askExtraConfirmation) {
        ExtraConfirmationDialog(
            state = state,
            onConfirm = {
                askExtraConfirmation = false
                authenticateThenSend()
            },
            onDismiss = { askExtraConfirmation = false },
        )
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            TokenAvatar(state.token, 64.dp)
            Text("${state.amount} ${state.token.symbol}", style = SwissType.Headline)
            state.amountChf?.let { Text("≈ ${Formats.money(it)} CHF", style = SwissType.Body) }
            SwissCard(Modifier.fillMaxWidth()) {
                ReviewLine("From", Formats.shortAddress(state.fromAddress ?: "—", 8, 6), "Your smart account")
                Divider1()
                ReviewLine("To", Formats.shortAddress(state.recipient, 8, 6))
                Divider1()
                ReviewLine("Network", ChainConfig.NETWORK_NAME)
                Divider1()
                ReviewLine("Network fee", "0 ETH", "Sponsored by Pimlico", valueColor = SwissColors.Green)
            }
            Text(
                "Your key signs a UserOperation; a bundler submits it and Pimlico's paymaster pays the gas.",
                style = SwissType.Caption, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp),
            )
            if (state.safeguardReasons.isNotEmpty()) {
                Text(
                    "Extra confirmation needed: ${state.safeguardReasons.joinToString("; ")}.",
                    style = SwissType.Caption, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            Text(
                if (authAvailable) "You will confirm with your fingerprint, face or screen lock."
                else "No screen lock on this phone: set one so payments ask for it.",
                style = SwissType.Caption, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        SwissButton("Confirm & send", onConfirmClick, Modifier.fillMaxWidth().padding(16.dp), leadingIcon = SwissIcons.ArrowUp)
    }
}

private const val EXTRA_CONFIRMATION_PAUSE_SECONDS = 3

/** The second confirmation: the reasons, the full recipient, and a short pause before "send". */
@Composable
private fun ExtraConfirmationDialog(state: SendUiState, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var secondsLeft by remember { mutableIntStateOf(EXTRA_CONFIRMATION_PAUSE_SECONDS) }
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Check this payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.safeguardReasons.forEach { reason -> Text("\u2022 $reason") }
                Text("You are sending ${state.amount} ${state.token.symbol} to ${state.recipient}.")
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = secondsLeft == 0) {
                Text(if (secondsLeft == 0) "Yes, send it" else "Wait $secondsLeft s")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun ReviewLine(label: String, value: String, hint: String? = null, valueColor: Color = Color.White) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = SwissType.Body.copy(color = SwissColors.TextSecondary), modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(value, color = valueColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (hint != null) Text(hint, style = SwissType.Caption)
        }
    }
}

// ------------------------------------------------------------------------------------------
// 3. Progress / result
// ------------------------------------------------------------------------------------------

private val STEPS = listOf(
    "Preparing the UserOperation",
    "Gas sponsorship · Pimlico paymaster",
    "Signing with your key",
    "Sending to the bundler",
    "Waiting for on-chain confirmation",
)

private fun SendProgress?.stepIndex(): Int = when (this) {
    null, SendProgress.Preparing -> 0
    SendProgress.Sponsoring -> 1
    SendProgress.Signing -> 2
    SendProgress.Submitting -> 3
    is SendProgress.Pending -> 4
    is SendProgress.Confirmed -> 5
    is SendProgress.Failed -> -1
}

@Composable
private fun ProgressStep(state: SendUiState, onClose: () -> Unit, onOpenHistory: () -> Unit, onRetry: () -> Unit) {
    val context = LocalContext.current
    when (val p = state.progress) {
        is SendProgress.Confirmed -> ResultLayout(
            icon = SwissIcons.Check, color = SwissColors.Green,
            title = "Sent!",
            message = "${state.amount} ${state.token.symbol} sent to ${Formats.shortAddress(state.recipient)}. No gas paid.",
        ) {
            SwissButton("View on block explorer", { context.openUrl(ChainConfig.txUrl(p.txHash)) }, Modifier.fillMaxWidth(), style = SwissButtonStyle.Outline, trailingIcon = SwissIcons.External)
            SwissButton("Done", onClose, Modifier.fillMaxWidth())
        }

        is SendProgress.Failed -> ResultLayout(
            icon = SwissIcons.Alert, color = SwissColors.Red,
            title = if (p.userOpHash != null) "Not confirmed yet" else "Transfer failed",
            message = p.message,
        ) {
            if (p.userOpHash != null) {
                SwissButton("Open History", onOpenHistory, Modifier.fillMaxWidth())
            } else {
                SwissButton("Try again", onRetry, Modifier.fillMaxWidth())
            }
            SwissButton("Close", onClose, Modifier.fillMaxWidth(), style = SwissButtonStyle.Dark)
        }

        else -> {
            val current = p.stepIndex()
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                SwissCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        STEPS.forEachIndexed { i, label ->
                            StepRow(label, done = i < current, active = i == current)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (p is SendProgress.Pending) {
                    InfoBanner(
                        "Broadcast ✓ — you can leave this screen, History will update.",
                        SwissColors.Green, SwissIcons.Check,
                    )
                    Spacer(Modifier.weight(1f))
                    SwissButton("Close", onClose, Modifier.fillMaxWidth(), style = SwissButtonStyle.Dark)
                } else {
                    Text(
                        "Don't close the app while your operation is being signed and submitted.",
                        style = SwissType.Caption, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun StepRow(label: String, done: Boolean, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
            when {
                done -> Box(Modifier.size(24.dp).clip(CircleShape).background(SwissColors.Green), contentAlignment = Alignment.Center) {
                    Icon(SwissIcons.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
                active -> CircularProgressIndicator(Modifier.size(22.dp), color = SwissColors.Orange, strokeWidth = 2.5.dp)
                else -> Box(Modifier.size(24.dp).clip(CircleShape).background(SwissColors.SurfaceHigh))
            }
        }
        Text(
            label,
            color = when {
                done -> SwissColors.TextSoft
                active -> Color.White
                else -> SwissColors.TextSecondary
            },
            fontSize = 15.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ResultLayout(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    title: String,
    message: String,
    actions: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        IconTile(icon, color, size = 96.dp)
        Spacer(Modifier.height(20.dp))
        Text(title, style = SwissType.Headline, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message, style = SwissType.Body, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), content = actions)
    }
}
