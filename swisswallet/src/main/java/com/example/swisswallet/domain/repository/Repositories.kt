package com.example.swisswallet.domain.repository

import com.example.swisswallet.domain.aa.SmartAccount
import com.example.core.recovery.RecoveryRequest
import com.example.swisswallet.domain.model.ChartRange
import com.example.swisswallet.domain.model.Guardian
import com.example.swisswallet.domain.model.GuardianKind
import com.example.swisswallet.domain.model.GuardianReview
import com.example.swisswallet.domain.model.ProtectionStatus
import com.example.swisswallet.domain.model.RecoveryCandidate
import com.example.swisswallet.domain.model.RecoverySession
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
    /** null until the wallet was provisioned (from Swissquote) or recovered. */
    val account: Flow<WalletAccount?>
    suspend fun currentAccount(): WalletAccount?
    /**
     * Creates the passkey and computes the counterfactual Coinbase Smart Wallet address
     * (owners = [passkey, recovery module]). Idempotent.
     */
    suspend fun provision(swissquoteGuardian: String?): WalletAccount
    suspend fun smartAccount(): SmartAccount
    /** After a successful recovery: this phone's new passkey now controls [wallet]. */
    suspend fun adoptRecoveredWallet(wallet: String, passkeyId: String, ownerIndex: Int, swissquoteGuardian: String?): WalletAccount
    /** Last wallet used on this phone (kept after "simulate lost phone" to prefill the recovery). */
    val lastWalletHint: Flow<String?>
    /** Deletes the passkey + local wallet data (demo: "I lost my phone"). Funds stay on-chain. */
    suspend fun wipeThisDevice()
}

/** Local names of guardians (on-chain they're only addresses). */
interface GuardianRepository {
    val guardians: Flow<List<Guardian>>
    suspend fun upsert(guardian: Guardian)
    suspend fun remove(address: String)
    /**
     * Creates a guardian key on this phone (demo only) and returns it.
     * [kind] = [GuardianKind.INSTITUTION] for the simulated independent institution.
     */
    suspend fun createDemoGuardian(kind: GuardianKind = GuardianKind.DEMO): Guardian
    /** Keys of the simulated guardians stored on this phone. */
    suspend fun demoGuardianKeys(): Map<String, org.web3j.crypto.ECKeyPair>
}

/** "Be a guardian for someone": this phone's guardian key. */
interface GuardianModeService {
    suspend fun myGuardianAddress(): String
    suspend fun inviteCode(name: String, kind: GuardianKind): String
    /** Decodes a `SWREC1.` code and checks on-chain that this phone is one of the wallet's guardians. */
    suspend fun review(requestCode: String): GuardianReview
    /** Signs the request → `SWSIG1.` code to send back. */
    suspend fun approve(request: RecoveryRequest): String
}

/** Social recovery with SwissRecoveryModule (3-of-4 guardians + safety delay). */
interface RecoveryService {
    // ---- owner side (phone still in hand)
    suspend fun protectionStatus(): ProtectionStatus
    /** Deploys the module if needed + setupGuardians(4 guardians, 3, 30 s), in ONE gasless UserOperation. */
    fun activateProtection(guardians: List<Guardian>): Flow<SendProgress>
    /** Stops a recovery during the delay (someone is trying to take over the wallet). */
    fun cancelPendingRecovery(): Flow<SendProgress>

    // ---- new device side
    val session: Flow<RecoverySession?>
    suspend fun lookup(wallet: String): RecoveryCandidate
    /** Creates the new passkey and the request guardians must sign. */
    suspend fun startRecovery(wallet: String, deviceLabel: String): RecoverySession
    /** Adds a guardian approval (`SWSIG1.` code). Returns who approved. */
    suspend fun addApproval(code: String, fromSwissquote: Boolean = false): Guardian
    /** Re-reads the chain (app was closed meanwhile?): recovery started / already finished. */
    suspend fun syncSession()
    /** Signs with the simulated guardians stored on this phone. Returns how many approved. */
    suspend fun approveWithDemoGuardians(): Int
    /** initiateRecovery(...) with the approvals → starts the safety delay. */
    fun submitRecovery(): Flow<SendProgress>
    /** finalizeRecovery(...) → the new passkey becomes an owner; this phone controls the wallet again. */
    fun finalizeRecovery(): Flow<SendProgress>
    suspend fun abandon()
}

interface PortfolioRepository {
    /** Real on-chain balances (ETH + USDC on the test network) valued with fake CHF prices. */
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
    suspend fun clear()
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
