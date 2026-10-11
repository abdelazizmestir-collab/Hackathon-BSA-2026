package com.example.core.auth

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Approval on the client's phone with Face ID, through a QR code (SIMULATED second factor).
 *
 * The app shows a QR code. The client scans it with their phone; the page at [PAGE_URL] asks for
 * Face ID (a passkey with user verification: Face ID on iPhone, fingerprint or face on Android)
 * and only then shows a 6-digit code, which the client types in the app.
 *
 * No server: the page and the app derive the same code from the random session in the QR code
 * (and, for a payment, from the amount and the recipient, so a code can't approve another payment).
 * In production this is the bank's authenticator app, with the signature checked server-side.
 */
object PhoneApproval {
    /** Static page published from `docs/approve/` with GitHub Pages. */
    const val PAGE_URL = "https://abdelazizmestir-collab.github.io/Hackathon-BSA-2026/approve/"

    /** 16 hex characters, new for every approval. */
    fun newSession(): String {
        val bytes = ByteArray(8)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // ---------------------------------------------------------------- login (Swissquote app)

    fun loginUrl(session: String): String = "$PAGE_URL#a=login&s=$session"

    /** Same derivation as the original QR login, so its pinned test vector still holds. */
    fun loginCode(session: String): String = code("swissquote-demo-login:$session")

    // ---------------------------------------------------------------- payment (SwissWallet)

    fun paymentUrl(session: String, amount: String, symbol: String, to: String): String =
        PAGE_URL + "#a=pay&s=" + session + "&amt=" + enc(amount) + "&sym=" + enc(symbol) + "&to=" + enc(to)

    /** Bound to the payment: the same session with another amount or recipient gives another code. */
    fun paymentCode(session: String, amount: String, symbol: String, to: String): String =
        code("swisswallet-pay:$session:$amount:${symbol.uppercase()}:${to.trim().lowercase()}")

    // ---------------------------------------------------------------- helpers

    /** First 4 bytes of SHA-256(input), as an unsigned number, modulo 1,000,000. */
    fun code(input: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
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

    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8")
}
