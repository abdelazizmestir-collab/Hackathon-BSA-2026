package com.example.swissquote.ui.welcome

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * MOCK of logging in to Swissquote with a second device: the app shows a QR code, the client
 * scans it with their phone, the phone shows a 6-digit code, and the client types it here.
 *
 * It stands for approval in the bank's mobile authenticator. Nothing is sent to a server: the
 * page at [PAGE_URL] and [expectedCode] compute the same code from the random session in the QR.
 */
object QrLogin {
    const val PAGE_URL = "https://wallet-login-code-demo.vercel.app/"

    /** 16 hex characters, new for every login screen. */
    fun newSession(): String {
        val bytes = ByteArray(8)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** The session travels in the URL fragment, which browsers never send to the server. */
    fun loginUrl(session: String): String = "$PAGE_URL#s=$session"

    /** First 4 bytes of SHA-256("swissquote-demo-login:" + session), as a number, modulo 1,000,000. */
    fun expectedCode(session: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest("swissquote-demo-login:$session".toByteArray(Charsets.UTF_8))
        val n = (d[0].toLong() and 0xFF) * 16_777_216L +
            (d[1].toLong() and 0xFF) * 65_536L +
            (d[2].toLong() and 0xFF) * 256L +
            (d[3].toLong() and 0xFF)
        return (n % 1_000_000L).toString().padStart(6, '0')
    }

    fun qrBitmap(content: String, size: Int = 600): Bitmap {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
        val pixels = IntArray(size * size) { i ->
            if (matrix.get(i % size, i / size)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }
}

@Composable
fun QrLoginDialog(onSuccess: () -> Unit, onDismiss: () -> Unit) {
    val session = remember { QrLogin.newSession() }
    val qr = remember(session) { QrLogin.qrBitmap(QrLogin.loginUrl(session)).asImageBitmap() }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log in with your phone") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Scan this code with your phone, then type the 6 digits it shows.")
                Image(
                    bitmap = qr,
                    contentDescription = "Login QR code",
                    modifier = Modifier.size(220.dp).background(Color.White).padding(6.dp),
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { value ->
                        code = value.filter { it.isDigit() }.take(6)
                        error = null
                    },
                    label = { Text("Code from your phone") },
                    singleLine = true,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                val message = error
                if (message != null) Text(message, color = Color(0xFFD32F2F))
                Text("Simulated: stands for approval in the bank's mobile authenticator.")
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (code == QrLogin.expectedCode(session)) {
                        onSuccess()
                    } else {
                        error = "Wrong code. Scan the QR code again and retry."
                    }
                },
                enabled = code.length == 6,
            ) { Text("Log in") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
