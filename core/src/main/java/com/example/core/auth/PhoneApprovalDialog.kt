package com.example.core.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.designsystem.SwissColors

/**
 * "Scan with your phone and confirm with Face ID": shows the QR code of [url], then checks the
 * 6-digit code the phone shows after Face ID against [expectedCode].
 */
@Composable
fun PhoneApprovalDialog(
    title: String,
    detail: String,
    url: String,
    expectedCode: String,
    confirmLabel: String,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit,
) {
    val qr = remember(url) { PhoneApproval.qrBitmap(url).asImageBitmap() }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SwissColors.Surface,
        title = { Text(title, color = Color.White) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(detail, color = SwissColors.TextSoft, textAlign = TextAlign.Center)
                Image(
                    bitmap = qr,
                    contentDescription = "Approval QR code",
                    modifier = Modifier.size(210.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(6.dp),
                )
                Text(
                    "1. Scan with your phone's camera\n2. Confirm with Face ID\n3. Type the 6 digits it shows",
                    color = SwissColors.TextSoft,
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { v ->
                        code = v.filter { it.isDigit() }.take(6)
                        error = null
                    },
                    label = { Text("Code from your phone") },
                    singleLine = true,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = SwissColors.Orange,
                        focusedLabelColor = SwissColors.Orange,
                        cursorColor = SwissColors.Orange,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = SwissColors.Red, fontSize = 13.sp) }
                Text(
                    "Simulated second factor: stands for approval in the bank's authenticator app.",
                    color = SwissColors.TextSecondary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (code == expectedCode) onSuccess() else error = "Wrong code. Scan the QR code again and retry."
                },
                enabled = code.length == 6,
            ) { Text(confirmLabel, color = if (code.length == 6) SwissColors.Orange else SwissColors.TextSecondary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White) } },
    )
}
