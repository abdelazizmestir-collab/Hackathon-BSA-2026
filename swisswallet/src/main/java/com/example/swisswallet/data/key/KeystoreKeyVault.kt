package com.example.swisswallet.data.key

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.swisswallet.domain.aa.KeyVault
import com.example.swisswallet.domain.aa.Signer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Keys
import org.web3j.crypto.Sign
import java.math.BigInteger
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Owner EOA signer. Only used to sign userOpHashes — it never holds funds. */
class LocalSigner(private val keyPair: ECKeyPair) : Signer {

    override val address: String = Keys.toChecksumAddress(Keys.getAddress(keyPair))

    override fun signMessage(message: ByteArray): ByteArray {
        val sig = Sign.signPrefixedMessage(message, keyPair) // EIP-191 "\x19Ethereum Signed Message:\n32"
        return sig.r + sig.s + sig.v
    }
}

/**
 * The secp256k1 owner key is generated on-device, then encrypted with an AES-256-GCM key that
 * lives inside the Android Keystore (hardware-backed when available) — the AES key can't be
 * extracted, so the stored ciphertext is useless off this device.
 */
class KeystoreKeyVault(
    private val store: DataStore<Preferences>,
) : KeyVault {

    private val mutex = Mutex()

    override suspend fun hasKey(): Boolean = store.data.first()[KEY_CIPHERTEXT] != null

    override suspend fun getSigner(): Signer? = mutex.withLock { load() }

    override suspend fun getOrCreateSigner(): Signer = mutex.withLock { load() ?: create() }

    private suspend fun load(): Signer? = withContext(Dispatchers.IO) {
        val prefs = store.data.first()
        val ct = prefs[KEY_CIPHERTEXT] ?: return@withContext null
        val iv = prefs[KEY_IV] ?: return@withContext null
        val secret = decrypt(b64d(ct), b64d(iv))
        try {
            LocalSigner(ECKeyPair.create(BigInteger(1, secret)))
        } finally {
            secret.fill(0)
        }
    }

    private suspend fun create(): Signer = withContext(Dispatchers.IO) {
        val secret = newPrivateKey()
        try {
            val (ct, iv) = encrypt(secret)
            store.edit {
                it[KEY_CIPHERTEXT] = b64e(ct)
                it[KEY_IV] = b64e(iv)
            }
            LocalSigner(ECKeyPair.create(BigInteger(1, secret)))
        } finally {
            secret.fill(0)
        }
    }

    private fun newPrivateKey(): ByteArray {
        val random = SecureRandom()
        while (true) {
            val bytes = ByteArray(32).also { random.nextBytes(it) }
            val k = BigInteger(1, bytes)
            if (k.signum() > 0 && k < CURVE_N) return bytes
        }
    }

    private fun aesKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, aesKey())
        return cipher.doFinal(plain) to cipher.iv
    }

    private fun decrypt(ciphertext: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, aesKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun b64e(b: ByteArray): String = Base64.getEncoder().encodeToString(b)
    private fun b64d(s: String): ByteArray = Base64.getDecoder().decode(s)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "swisswallet_owner_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        val KEY_CIPHERTEXT = stringPreferencesKey("owner_key_ct")
        val KEY_IV = stringPreferencesKey("owner_key_iv")
        /** secp256k1 group order */
        val CURVE_N = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16)
    }
}
