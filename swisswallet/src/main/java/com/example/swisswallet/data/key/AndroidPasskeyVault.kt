package com.example.swisswallet.data.key

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.example.swisswallet.domain.aa.Passkey
import com.example.swisswallet.domain.aa.PasskeyVault
import com.example.swisswallet.domain.aa.WebAuthnSignature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

/**
 * Passkey = a **P-256 key pair created inside the phone's secure hardware** (Android Keystore,
 * TEE / StrongBox when available). The private key can't be read or exported, not even by us:
 * we can only ask the chip "please sign this".
 *
 * The wallet contract (Coinbase Smart Wallet) stores the public key (x, y) as an owner and checks
 * each signature on-chain, in the WebAuthn format ([WebAuthnAssertion]).
 *
 * Demo note: the key is not bound to the fingerprint (no `setUserAuthenticationRequired`) so the
 * emulator works without enrolling biometrics. On a real phone you'd enable it: every payment
 * would then need a fingerprint / face unlock.
 */
class AndroidPasskeyVault : PasskeyVault {

    override suspend fun create(id: String): Passkey = withContext(Dispatchers.IO) {
        val alias = alias(id)
        val ks = keyStore()
        if (ks.containsAlias(alias)) ks.deleteEntry(alias)
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        generator.initialize(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        generator.generateKeyPair()
        load(id) ?: error("Passkey creation failed")
    }

    override suspend fun get(id: String): Passkey? = withContext(Dispatchers.IO) { load(id) }

    override suspend fun delete(id: String) {
        withContext(Dispatchers.IO) {
            val ks = keyStore()
            if (ks.containsAlias(alias(id))) ks.deleteEntry(alias(id))
        }
    }

    private fun load(id: String): Passkey? {
        val ks = keyStore()
        val alias = alias(id)
        val privateKey = ks.getKey(alias, null) as? PrivateKey ?: return null
        val publicKey = ks.getCertificate(alias)?.publicKey as? ECPublicKey ?: return null
        return KeystorePasskey(
            id = id,
            x = WebAuthnAssertion.coord(publicKey.w.affineX),
            y = WebAuthnAssertion.coord(publicKey.w.affineY),
            privateKey = privateKey,
        )
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun alias(id: String) = "swisswallet_passkey_$id"

    private class KeystorePasskey(
        override val id: String,
        override val x: ByteArray,
        override val y: ByteArray,
        private val privateKey: PrivateKey,
    ) : Passkey {
        override fun sign(challenge: ByteArray): WebAuthnSignature =
            WebAuthnAssertion.build(challenge) { message ->
                Signature.getInstance("SHA256withECDSA").run {
                    initSign(privateKey)
                    update(message)
                    sign()
                }
            }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}
