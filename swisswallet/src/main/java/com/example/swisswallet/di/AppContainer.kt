package com.example.swisswallet.di

import android.content.Context
import com.example.swisswallet.config.ChainConfig
import com.example.swisswallet.data.aa.AaTransferService
import com.example.swisswallet.data.aa.PimlicoClient
import com.example.swisswallet.data.key.KeystoreKeyVault
import com.example.swisswallet.data.repository.DataStoreSettingsRepository
import com.example.swisswallet.data.repository.DataStoreTransactionRepository
import com.example.swisswallet.data.repository.DefaultPortfolioRepository
import com.example.swisswallet.data.repository.DefaultWalletRepository
import com.example.swisswallet.data.repository.FakePriceRepository
import com.example.swisswallet.data.repository.walletDataStore
import com.example.swisswallet.data.rpc.EthereumRpcClient
import com.example.swisswallet.data.rpc.JsonRpcClient
import com.example.swisswallet.domain.aa.BundlerClient
import com.example.swisswallet.domain.aa.EthereumRpc
import com.example.swisswallet.domain.aa.KeyVault
import com.example.swisswallet.domain.aa.PaymasterClient
import com.example.swisswallet.domain.repository.PortfolioRepository
import com.example.swisswallet.domain.repository.PriceRepository
import com.example.swisswallet.domain.repository.SettingsRepository
import com.example.swisswallet.domain.repository.TransactionRepository
import com.example.swisswallet.domain.repository.TransferService
import com.example.swisswallet.domain.repository.WalletRepository
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Manual DI: the whole object graph in one place. ViewModels only see domain interfaces.
 *
 *   UI (Compose) → ViewModel → Repository / TransferService (domain interfaces)
 *                                   ↓
 *          data: KeystoreKeyVault · SimpleSmartAccount · PimlicoClient · EthereumRpcClient
 */
class AppContainer(context: Context) {

    private val store = context.applicationContext.walletDataStore

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    val ethereumRpc: EthereumRpc = EthereumRpcClient(JsonRpcClient({ ChainConfig.rpcUrl }, http))

    private val pimlico = PimlicoClient(JsonRpcClient({ ChainConfig.pimlicoUrl }, http))
    val bundler: BundlerClient = pimlico
    val paymaster: PaymasterClient = pimlico

    val keyVault: KeyVault = KeystoreKeyVault(store)
    val walletRepository: WalletRepository = DefaultWalletRepository(store, keyVault, ethereumRpc)
    val priceRepository: PriceRepository = FakePriceRepository()
    val portfolioRepository: PortfolioRepository = DefaultPortfolioRepository(ethereumRpc, priceRepository)
    val transactionRepository: TransactionRepository = DataStoreTransactionRepository(store, bundler)
    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(store)

    val transferService: TransferService = AaTransferService(
        wallet = walletRepository,
        rpc = ethereumRpc,
        bundler = bundler,
        paymaster = paymaster,
        history = transactionRepository,
    )
}
