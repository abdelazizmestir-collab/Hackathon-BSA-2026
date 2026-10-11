package com.example.swissquote.di

import android.content.Context
import com.example.swissquote.data.AndroidWalletAppLauncher
import com.example.swissquote.data.DataStoreSessionRepository
import com.example.swissquote.data.FakeMarketRepository
import com.example.swissquote.data.sessionDataStore
import com.example.swissquote.domain.repository.MarketRepository
import com.example.swissquote.domain.repository.SessionRepository
import com.example.swissquote.domain.repository.SwissquoteGuardian
import com.example.swissquote.domain.repository.WalletAppLauncher
import com.example.swissquote.data.KeystoreSwissquoteGuardian
import com.example.core.recovery.EncryptedKeyStore

/**
 * Manual dependency injection (one instance per process). ViewModels receive interfaces only,
 * so any fake can be swapped for a real implementation without touching the UI.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val sessionRepository: SessionRepository = DataStoreSessionRepository(appContext.sessionDataStore)
    val marketRepository: MarketRepository = FakeMarketRepository()
    val walletAppLauncher: WalletAppLauncher = AndroidWalletAppLauncher(appContext)
    val swissquoteGuardian: SwissquoteGuardian = KeystoreSwissquoteGuardian(
        EncryptedKeyStore(appContext.sessionDataStore, "swissquote_guardian_aes_v1")
    )
}
