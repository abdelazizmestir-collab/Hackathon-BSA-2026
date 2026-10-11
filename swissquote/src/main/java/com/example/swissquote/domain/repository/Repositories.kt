package com.example.swissquote.domain.repository

import com.example.swissquote.domain.model.Session
import com.example.swissquote.domain.model.WatchlistSnapshot
import kotlinx.coroutines.flow.Flow

/** Market data (fake in this concept — no real Swissquote API). */
interface MarketRepository {
    suspend fun fetchWatchlist(): WatchlistSnapshot
}

/** Login state + wallet link, persisted locally. */
interface SessionRepository {
    val session: Flow<Session>
    suspend fun signIn()
    suspend fun openAccount()
    suspend fun continueAsGuest()
    suspend fun saveWallet(address: String)
}

/** Knows how to find / open the external SwissWallet APK. */
interface WalletAppLauncher {
    fun isWalletInstalled(): Boolean
    /** @return false if SwissWallet isn't installed. */
    fun openWallet(): Boolean
}

/**
 * Swissquote as **guardian #1** of every client wallet (social recovery, 3 of 4).
 * In production the key would live in Swissquote's HSM, server side; here it's an encrypted key
 * in the app so the demo works offline.
 */
interface SwissquoteGuardian {
    suspend fun address(): String
    /** Signs a recovery request (`SWREC1.`) → approval code (`SWSIG1.`). Call only after the identity check. */
    suspend fun approve(requestCode: String): String
}
