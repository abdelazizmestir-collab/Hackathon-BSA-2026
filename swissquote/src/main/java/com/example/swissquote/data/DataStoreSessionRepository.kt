package com.example.swissquote.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.swissquote.domain.model.Session
import com.example.swissquote.domain.repository.SessionRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "swissquote_session")

class DataStoreSessionRepository(
    private val store: DataStore<Preferences>,
) : SessionRepository {

    private object Keys {
        val HAS_ACCOUNT = booleanPreferencesKey("has_account")
        val WALLET_ADDRESS = stringPreferencesKey("wallet_address")
    }

    override val session: Flow<Session> = store.data
        .map { p ->
            Session(
                // Same default as the Figma prototype: you start as an existing client.
                hasAccount = p[Keys.HAS_ACCOUNT] ?: true,
                walletAddress = p[Keys.WALLET_ADDRESS],
            )
        }
        .distinctUntilChanged()

    // Fake network latency for the (fake) Swissquote auth backend.
    override suspend fun signIn() {
        delay(800)
        store.edit { it[Keys.HAS_ACCOUNT] = true }
    }

    override suspend fun openAccount() {
        delay(1000)
        store.edit { it[Keys.HAS_ACCOUNT] = true }
    }

    override suspend fun continueAsGuest() {
        delay(400)
        store.edit {
            it[Keys.HAS_ACCOUNT] = false
            it.remove(Keys.WALLET_ADDRESS)
        }
    }

    override suspend fun saveWallet(address: String) {
        store.edit { it[Keys.WALLET_ADDRESS] = address }
    }
}
