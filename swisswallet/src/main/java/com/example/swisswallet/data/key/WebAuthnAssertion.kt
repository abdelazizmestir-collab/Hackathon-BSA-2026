package com.example.swisswallet.data.key

import com.example.swisswallet.domain.aa.WebAuthnSignature
import java.math.BigInteger
import java.security.MessageDigest
import java.util.Base64

/**
 * Builds a WebAuthn assertion exactly like a browser passkey would, so Coinbase Smart Wallet
 * (webauthn-sol) can verify it on-chain:
 *
 *   authenticatorData = sha256(rpId) ‖ flags ‖ signCount
 *   clientDataJSON    = {"type":"webauthn.get","challenge":"<base64url(userOpHash)>",...}
 *   signed message    = authenticatorData ‖ sha256(clientDataJSON)   (P-256 / ECDSA-SHA256)
 *
 * The contract checks: the type string at typeIndex, the challenge at challengeIndex, the
 * "user present" flag, then the P-256 signature (s must be in the lower half of the curve order).
 */
object WebAuthnAssertion {

    const val RP_ID = "swisswallet.app"
    const val ORIGIN = "https://swisswallet.app"

    /** UP (user present) | UV (user verified) */
    private const val FLAGS: Byte = 0x05

    /** Order of the P-256 curve. */
    val P256_N = BigInteger("FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551", 16)

    /**
     * @param ecdsaSha256 signs a message with SHA256withECDSA and returns the DER signature
     *                    (Android Keystore on the phone, plain JDK key in unit tests).
     */
    fun build(challenge: ByteArray, ecdsaSha256: (ByteArray) -> ByteArray): WebAuthnSignature {
        val authenticatorData = sha256(RP_ID.toByteArray()) + byteArrayOf(FLAGS) + ByteArray(4)
        val encodedChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(challenge)
        val clientDataJSON =
            """{"type":"webauthn.get","challenge":"$encodedChallenge","origin":"$ORIGIN","crossOrigin":false}"""
        val typeIndex = clientDataJSON.indexOf("\"type\":\"webauthn.get\"")
        val challengeIndex = clientDataJSON.indexOf("\"challenge\":\"")

        val message = authenticatorData + sha256(clientDataJSON.toByteArray(Charsets.UTF_8))
        val (r, sRaw) = parseDer(ecdsaSha256(message))
        // webauthn-sol rejects "high s" signatures (malleability): use n - s instead.
        val s = if (sRaw > P256_N.shiftRight(1)) P256_N.subtract(sRaw) else sRaw

        return WebAuthnSignature(authenticatorData, clientDataJSON, challengeIndex, typeIndex, r, s)
    }

    fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    /** DER: 0x30 len 0x02 lenR R 0x02 lenS S → (r, s) */
    fun parseDer(der: ByteArray): Pair<BigInteger, BigInteger> {
        require(der.isNotEmpty() && der[0] == 0x30.toByte()) { "Not a DER signature" }
        var i = 2
        if (der[1].toInt() and 0x80 != 0) i = 2 + (der[1].toInt() and 0x7F)
        require(der[i] == 0x02.toByte()) { "Bad DER (r)" }
        val lenR = der[i + 1].toInt()
        val r = BigInteger(1, der.copyOfRange(i + 2, i + 2 + lenR))
        i += 2 + lenR
        require(der[i] == 0x02.toByte()) { "Bad DER (s)" }
        val lenS = der[i + 1].toInt()
        val s = BigInteger(1, der.copyOfRange(i + 2, i + 2 + lenS))
        return r to s
    }

    /** 32-byte big-endian coordinate. */
    fun coord(v: BigInteger): ByteArray {
        val raw = v.toByteArray().let { if (it.size > 32 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it }
        return ByteArray(32 - raw.size) + raw
    }
}
