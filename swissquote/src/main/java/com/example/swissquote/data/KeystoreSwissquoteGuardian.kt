package com.example.swissquote.data

import com.example.core.recovery.EncryptedKeyStore
import com.example.core.recovery.EthKeys
import com.example.core.recovery.RecoveryRequest
import com.example.swissquote.domain.repository.SwissquoteGuardian

/** Swissquote's guardian key: secp256k1, encrypted with an AES key that never leaves the Android Keystore. */
class KeystoreSwissquoteGuardian(private val keys: EncryptedKeyStore) : SwissquoteGuardian {

    override suspend fun address(): String = EthKeys.address(keys.getOrCreate(KEY_ID))

    override suspend fun approve(requestCode: String): String {
        val request = RecoveryRequest.decode(requestCode)
        return EthKeys.approve(keys.getOrCreate(KEY_ID), request).encode()
    }

    private companion object {
        const val KEY_ID = "swissquote-guardian"
    }
}
