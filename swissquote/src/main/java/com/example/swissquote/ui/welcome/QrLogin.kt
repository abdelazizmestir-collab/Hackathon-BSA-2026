package com.example.swissquote.ui.welcome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.core.auth.PhoneApproval
import com.example.core.auth.PhoneApprovalDialog

/**
 * MOCK of logging in to Swissquote with a second device: the app shows a QR code, the client
 * scans it with their phone, confirms with Face ID there, and types the 6-digit code it shows.
 *
 * It stands for approval in the bank's authenticator app. Nothing is sent to a server: the page
 * ([PhoneApproval.PAGE_URL]) and [expectedCode] compute the same code from the session in the QR.
 */
object QrLogin {
    fun newSession(): String = PhoneApproval.newSession()

    /** The session travels in the URL fragment, which browsers never send to the server. */
    fun loginUrl(session: String): String = PhoneApproval.loginUrl(session)

    /** First 4 bytes of SHA-256("swissquote-demo-login:" + session), as a number, modulo 1,000,000. */
    fun expectedCode(session: String): String = PhoneApproval.loginCode(session)
}

@Composable
fun QrLoginDialog(onSuccess: () -> Unit, onDismiss: () -> Unit) {
    val session = remember { QrLogin.newSession() }
    PhoneApprovalDialog(
        title = "Log in with Face ID",
        detail = "Scan this code with your phone and confirm with Face ID.",
        url = QrLogin.loginUrl(session),
        expectedCode = QrLogin.expectedCode(session),
        confirmLabel = "Log in",
        onSuccess = onSuccess,
        onDismiss = onDismiss,
    )
}
