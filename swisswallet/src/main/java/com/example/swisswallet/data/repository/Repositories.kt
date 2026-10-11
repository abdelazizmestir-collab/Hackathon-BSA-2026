package com.example.swisswallet.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.swisswallet.config.ChainConfig
import com.example.swisswallet.data.aa.Abi
import com.example.swisswallet.data.aa.Erc20
import com.example.swisswallet.data.aa.SimpleSmartAccount
import com.example.swisswallet.domain.aa.BundlerClient
import com.example.swisswallet.domain.aa.EthereumRpc
import com.example.swisswallet.domain.aa.KeyVault
import com.example.swisswallet.domain.aa.SmartAccount
import com.example.swisswallet.domain.model.ChartRange
import com.example.swisswallet.domain.model.Portfolio
import com.example.swisswallet.domain.model.Price
import com.example.swisswallet.domain.model.Token
import com.example.swisswallet.domain.model.TokenBalance
import com.example.swisswallet.domain.model.Tokens
import com.example.swisswallet.domain.model.TxKind
import com.example.swisswallet.domain.model.TxRecord
import com.example.swisswallet.domain.model.TxStatus
import com.example.swisswallet.domain.model.WalletAccount
import com.example.swisswallet.domain.repository.PortfolioRepository
import com.example.swisswallet.domain.repository.PriceRepository
import com.example.swisswallet.domain.repository.SettingsRepository
import com.example.swisswallet.domain.repository.TransactionRepository
import com.example.swisswallet.domain.repository.WalletRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import kotlin.random.Random

/** Single DataStore for the whole SwissWallet process. */
val Context.walletDataStore: DataStore<Preferences> by preferencesDataStore(name = "swisswallet")

// ---------------------------------------------------------------------------------------------
// Wallet
// ---------------------------------------------------------------------------------------------

class DefaultWalletRepository(
    private val store: DataStore<Preferences>,
    private val vault: KeyVault,
    private val rpc: EthereumRpc,
) : WalletRepository {

    private val mutex = Mutex()

    override val account: Flow<WalletAccount?> = store.data
        .map { p ->
            val owner = p[KEY_OWNER]
            val sa = p[KEY_SMART_ACCOUNT]
            if (owner != null && sa != null) WalletAccount(owner, sa) else null
        }
        .distinctUntilChanged()

    override suspend fun currentAccount(): WalletAccount? = account.first()

    override suspend fun provision(): WalletAccount = mutex.withLock {
        currentAccount()?.let { return@withLock it }
        val signer = vault.getOrCreateSigner()
        val address = SimpleSmartAccount.computeAddress(signer.address, rpc)
        store.edit {
            it[KEY_OWNER] = signer.address
            it[KEY_SMART_ACCOUNT] = address
        }
        WalletAccount(signer.address, address)
    }

    override suspend fun smartAccount(): SmartAccount {
        val acc = currentAccount() ?: error("No wallet on this device yet. Generate it from the Swissquote app.")
        val signer = vault.getSigner() ?: error("Owner key not found on this device.")
        return SimpleSmartAccount(owner = signer, address = acc.smartAccountAddress, rpc = rpc)
    }

    private companion object {
        val KEY_OWNER = stringPreferencesKey("owner_address")
        val KEY_SMART_ACCOUNT = stringPreferencesKey("smart_account_address")
    }
}

// ---------------------------------------------------------------------------------------------
// Portfolio (real balances) + prices (fake)
// ---------------------------------------------------------------------------------------------

class DefaultPortfolioRepository(
    private val rpc: EthereumRpc,
    private val prices: PriceRepository,
) : PortfolioRepository {

    override suspend fun loadPortfolio(address: String): Portfolio = coroutineScope {
        val eth = async { rpc.getBalance(address) }
        val usdc = async {
            Abi.decodeUint(rpc.call(Tokens.USDC.contractAddress!!, Erc20.balanceOf(address)))
        }
        Portfolio(
            listOf(
                TokenBalance(Tokens.ETH, eth.await(), prices.price(Tokens.ETH)),
                TokenBalance(Tokens.USDC, usdc.await(), prices.price(Tokens.USDC)),
            )
        )
    }
}

/** CHF prices + chart history are simulated (not blockchain related). */
class FakePriceRepository : PriceRepository {

    override fun price(token: Token): Price = when (token.symbol) {
        "ETH" -> Price(BigDecimal("2789.10"), 3.8)
        "USDC" -> Price(BigDecimal("0.88"), 0.01)
        else -> Price(BigDecimal.ONE, 0.0)
    }

    override fun history(range: ChartRange, currentValue: BigDecimal): List<Float> {
        val random = Random(range.ordinal * 31 + 7)
        val walk = ArrayList<Float>(range.points)
        var v = 100f
        repeat(range.points) {
            v *= 1f + (random.nextFloat() - 0.42f) * 0.03f // gentle upward trend
            walk += v
        }
        val end = currentValue.toFloat().takeIf { it > 0f } ?: 1f
        val scale = end / walk.last()
        return walk.map { it * scale }
    }
}

// ---------------------------------------------------------------------------------------------
// History (local) — real sends + a few demo entries
// ---------------------------------------------------------------------------------------------

class DataStoreTransactionRepository(
    private val store: DataStore<Preferences>,
    private val bundler: BundlerClient,
) : TransactionRepository {

    private val mutex = Mutex()

    override val transactions: Flow<List<TxRecord>> = store.data
        .map { p -> (p[KEY_HISTORY]?.let(::decode) ?: demoSeed()).sortedByDescending { it.timestamp } }
        .distinctUntilChanged()

    override suspend fun upsert(record: TxRecord) {
        mutex.withLock {
            store.edit { p ->
                val list = p[KEY_HISTORY]?.let(::decode) ?: demoSeed()
                p[KEY_HISTORY] = encode(list.filterNot { it.id == record.id } + record)
            }
        }
    }

    override suspend fun syncPending() {
        if (!ChainConfig.hasPimlicoKey) return
        val pending = transactions.first().filter { it.status == TxStatus.PENDING && !it.isDemo && it.userOpHash != null }
        for (record in pending) {
            val receipt = try {
                bundler.getUserOperationReceipt(record.userOpHash!!)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } ?: continue
            upsert(
                record.copy(
                    status = if (receipt.success) TxStatus.CONFIRMED else TxStatus.FAILED,
                    txHash = receipt.transactionHash,
                    error = receipt.reason,
                )
            )
        }
    }

    private fun demoSeed(): List<TxRecord> {
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        return listOf(
            TxRecord("demo-1", TxKind.DEPOSIT, "CHF", "500.00", "Swissquote account", now - day - 3_600_000, TxStatus.CONFIRMED, isDemo = true),
            TxRecord("demo-2", TxKind.SWAP, "USDC", "25.00", "ETH → USDC", now - day - 7_200_000, TxStatus.CONFIRMED, isDemo = true),
            TxRecord("demo-3", TxKind.RECEIVED, "ETH", "0.05", "Sepolia faucet", now - 3 * day, TxStatus.CONFIRMED, isDemo = true),
        )
    }

    private fun encode(list: List<TxRecord>): String = JSONArray().apply {
        list.forEach { r ->
            put(
                JSONObject()
                    .put("id", r.id).put("kind", r.kind.name).put("token", r.tokenSymbol)
                    .put("amount", r.amount).put("counterparty", r.counterparty).put("ts", r.timestamp)
                    .put("status", r.status.name).put("userOpHash", r.userOpHash ?: JSONObject.NULL)
                    .put("txHash", r.txHash ?: JSONObject.NULL).put("error", r.error ?: JSONObject.NULL)
                    .put("demo", r.isDemo)
            )
        }
    }.toString()

    private fun decode(json: String): List<TxRecord> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            TxRecord(
                id = o.getString("id"),
                kind = TxKind.valueOf(o.getString("kind")),
                tokenSymbol = o.getString("token"),
                amount = o.getString("amount"),
                counterparty = o.getString("counterparty"),
                timestamp = o.getLong("ts"),
                status = TxStatus.valueOf(o.getString("status")),
                userOpHash = o.optStringOrNull("userOpHash"),
                txHash = o.optStringOrNull("txHash"),
                error = o.optStringOrNull("error"),
                isDemo = o.optBoolean("demo", false),
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key).takeIf { it.isNotBlank() } else null

    private companion object {
        val KEY_HISTORY = stringPreferencesKey("tx_history_v1")
    }
}

// ---------------------------------------------------------------------------------------------
// Settings (fake toggles, persisted)
// ---------------------------------------------------------------------------------------------

class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {
    override val biometrics: Flow<Boolean> = store.data.map { it[KEY_BIO] ?: true }.distinctUntilChanged()
    override val notifications: Flow<Boolean> = store.data.map { it[KEY_NOTIF] ?: true }.distinctUntilChanged()
    override suspend fun setBiometrics(enabled: Boolean) { store.edit { it[KEY_BIO] = enabled } }
    override suspend fun setNotifications(enabled: Boolean) { store.edit { it[KEY_NOTIF] = enabled } }

    private companion object {
        val KEY_BIO = booleanPreferencesKey("settings_biometrics")
        val KEY_NOTIF = booleanPreferencesKey("settings_notifications")
    }
}
