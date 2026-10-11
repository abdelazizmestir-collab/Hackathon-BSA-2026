package com.example.swisswallet.domain.repository

import com.example.swisswallet.domain.aa.SmartAccount
import com.example.swisswallet.domain.model.ChartRange
import com.example.swisswallet.domain.model.Portfolio
import com.example.swisswallet.domain.model.Price
import com.example.swisswallet.domain.model.SendProgress
import com.example.swisswallet.domain.model.Token
import com.example.swisswallet.domain.model.TransferRequest
import com.example.swisswallet.domain.model.TxRecord
import com.example.swisswallet.domain.model.WalletAccount
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

interface WalletRepository {
    /** null until the wallet was provisioned (from Swissquote). */
    val account: Flow<WalletAccount?>
    suspend fun currentAccount(): WalletAccount?
    /** Creates the owner key if needed and computes the counterfactual smart-account address. Idempotent. */
    suspend fun provision(): WalletAccount
    suspend fun smartAccount(): SmartAccount
}

interface PortfolioRepository {
    /** Real on-chain balances (ETH + USDC on Sepolia) valued with fake CHF prices. */
    suspend fun loadPortfolio(address: String): Portfolio
}

/** Simulated market data (prices / chart): not blockchain related. */
interface PriceRepository {
    fun price(token: Token): Price
    fun history(range: ChartRange, currentValue: BigDecimal): List<Float>
}

interface TransactionRepository {
    val transactions: Flow<List<TxRecord>>
    suspend fun upsert(record: TxRecord)
    /** Polls the bundler for PENDING operations and updates their status. */
    suspend fun syncPending()
}

interface SettingsRepository {
    val biometrics: Flow<Boolean>
    val notifications: Flow<Boolean>
    suspend fun setBiometrics(enabled: Boolean)
    suspend fun setNotifications(enabled: Boolean)
}

/** Sends tokens through ERC-4337 (gasless via paymaster). Emits each step. */
interface TransferService {
    fun send(request: TransferRequest): Flow<SendProgress>
}
