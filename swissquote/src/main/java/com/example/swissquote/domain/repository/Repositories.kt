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
