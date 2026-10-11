package com.example.core.recovery

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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

/**
 * Stores secp256k1 keys (guardian keys) encrypted with an AES-256-GCM key that never leaves the
 * Android Keystore. Used by:
 *  - Swissquote: its guardian key (Swissquote is guardian #1 of every client wallet),
 *  - SwissWallet: "be a guardian for someone" + the simulated guardians of the demo.
 */
class EncryptedKeyStore(
    private val store: DataStore<Preferences>,
    private val aesAlias: String,
) {
    private val mutex = Mutex()

    suspend fun get(id: String): ECKeyPair? = mutex.withLock { load(id) }

    suspend fun getOrCreate(id: String): ECKeyPair = mutex.withLock { load(id) ?: create(id) }

    suspend fun delete(id: String) {
        mutex.withLock { store.edit { it.remove(ct(id)); it.remove(iv(id)) } }
    }

    private suspend fun load(id: String): ECKeyPair? = withContext(Dispatchers.IO) {
        val p = store.data.first()
        val c = p[ct(id)] ?: return@withContext null
        val i = p[iv(id)] ?: return@withContext null
        val secret = decrypt(Base64.getDecoder().decode(c), Base64.getDecoder().decode(i))
        try { ECKeyPair.create(BigInteger(1, secret)) } finally { secret.fill(0) }
    }

    private suspend fun create(id: String): ECKeyPair = withContext(Dispatchers.IO) {
        val secret = newSecp256k1Key()
        try {
            val (c, i) = encrypt(secret)
            store.edit {
                it[ct(id)] = Base64.getEncoder().encodeToString(c)
                it[iv(id)] = Base64.getEncoder().encodeToString(i)
            }
            ECKeyPair.create(BigInteger(1, secret))
        } finally {
            secret.fill(0)
        }
    }

    private fun ct(id: String) = stringPreferencesKey("ek_${id}_ct")
    private fun iv(id: String) = stringPreferencesKey("ek_${id}_iv")

    private fun newSecp256k1Key(): ByteArray {
        val random = SecureRandom()
        while (true) {
            val b = ByteArray(32).also { random.nextBytes(it) }
            val k = BigInteger(1, b)
            if (k.signum() > 0 && k < SECP256K1_N) return b
        }
    }

    private fun aesKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(aesAlias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(aesAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: ByteArray): Pair<ByteArray, ByteArray> {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, aesKey())
        return c.doFinal(plain) to c.iv
    }

    private fun decrypt(data: ByteArray, iv: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, aesKey(), GCMParameterSpec(128, iv))
        return c.doFinal(data)
    }

    private companion object {
        val SECP256K1_N = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16)
    }
}

/** Small helpers around a secp256k1 key. */
object EthKeys {
    fun address(key: ECKeyPair): String = Keys.toChecksumAddress(Keys.getAddress(key))

    /** EIP-191 personal_sign over a 32-byte hash → r ‖ s ‖ v (65 bytes). */
    fun personalSign(key: ECKeyPair, hash: ByteArray): ByteArray {
        val sig = Sign.signPrefixedMessage(hash, key)
        return sig.r + sig.s + sig.v
    }

    /** A guardian approves a request: sign its hash. */
    fun approve(key: ECKeyPair, request: RecoveryRequest): GuardianApproval =
        GuardianApproval(address(key), personalSign(key, request.hash()))
}
