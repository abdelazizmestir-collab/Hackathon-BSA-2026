package com.example.swisswallet.di

import android.content.Context
import com.example.core.recovery.EncryptedKeyStore
import com.example.swisswallet.config.ChainConfig
import com.example.swisswallet.data.aa.AaTransferService
import com.example.swisswallet.data.aa.PimlicoClient
import com.example.swisswallet.data.aa.UserOperationSender
import com.example.swisswallet.data.key.AndroidPasskeyVault
import com.example.swisswallet.data.recovery.DataStoreGuardianRepository
import com.example.swisswallet.data.recovery.KeystoreGuardianModeService
import com.example.swisswallet.data.recovery.SocialRecoveryService
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
import com.example.swisswallet.domain.aa.PaymasterClient
import com.example.swisswallet.domain.aa.PasskeyVault
import com.example.swisswallet.domain.repository.GuardianModeService
import com.example.swisswallet.domain.repository.GuardianRepository
import com.example.swisswallet.domain.repository.PortfolioRepository
import com.example.swisswallet.domain.repository.PriceRepository
import com.example.swisswallet.domain.repository.RecoveryService
import com.example.swisswallet.domain.repository.SettingsRepository
import com.example.swisswallet.domain.repository.TransactionRepository
import com.example.swisswallet.domain.repository.TransferService
import com.example.swisswallet.domain.repository.WalletRepository
import com.example.swisswallet.security.LocalPaymentSafeguards
import com.example.swisswallet.security.PaymentSafeguards
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Manual DI: the whole object graph in one place. ViewModels only see domain interfaces.
 *
 *   UI (Compose) → ViewModel → Repository / Service (domain interfaces)
 *                                   ↓
 *   data: AndroidPasskeyVault · CoinbaseSmartAccount · UserOperationSender · PimlicoClient
 *         · RecoveryModule · SocialRecoveryService · EthereumRpcClient
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
    private val sender = UserOperationSender(bundler, paymaster)

    /** Passkeys (P-256, Android Keystore) = the wallet owners. */
    val passkeyVault: PasskeyVault = AndroidPasskeyVault()
    /** Guardian keys (secp256k1, encrypted with a Keystore AES key): "be a guardian" + demo guardians. */
    private val guardianKeys = EncryptedKeyStore(store, "swisswallet_guardian_aes_v1")

    val walletRepository: WalletRepository = DefaultWalletRepository(store, passkeyVault, ethereumRpc)
    val priceRepository: PriceRepository = FakePriceRepository()
    val portfolioRepository: PortfolioRepository = DefaultPortfolioRepository(ethereumRpc, priceRepository)
    val transactionRepository: TransactionRepository = DataStoreTransactionRepository(store, bundler)
    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(store)

    /** Blocked list, payment limits and the new-recipient rule applied before a payment is signed. */
    val paymentSafeguards: PaymentSafeguards = LocalPaymentSafeguards(context)

    val transferService: TransferService = AaTransferService(
        wallet = walletRepository,
        rpc = ethereumRpc,
        sender = sender,
        history = transactionRepository,
    )

    val guardianRepository: GuardianRepository = DataStoreGuardianRepository(store, guardianKeys)
    val guardianModeService: GuardianModeService = KeystoreGuardianModeService(guardianKeys, ethereumRpc)
    val recoveryService: RecoveryService = SocialRecoveryService(
        store = store,
        wallet = walletRepository,
        guardianRepository = guardianRepository,
        vault = passkeyVault,
        rpc = ethereumRpc,
        sender = sender,
    )
}
