package com.example.swisswallet.data.recovery

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.core.recovery.EthKeys
import com.example.core.recovery.GuardianApproval
import com.example.core.recovery.RecoveryPolicy
import com.example.core.recovery.RecoveryRequest
import com.example.swisswallet.config.ChainConfig
import com.example.swisswallet.data.aa.CoinbaseSmartAccount
import com.example.swisswallet.data.aa.ErrorMapper
import com.example.swisswallet.data.aa.RecoveryModule
import com.example.swisswallet.data.aa.UserOperationSender
import com.example.swisswallet.domain.aa.Call
import com.example.swisswallet.domain.aa.EthereumRpc
import com.example.swisswallet.domain.aa.PasskeyVault
import com.example.swisswallet.domain.model.Guardian
import com.example.swisswallet.domain.model.GuardianKind
import com.example.swisswallet.domain.model.PendingRecovery
import com.example.swisswallet.domain.model.ProtectionStatus
import com.example.swisswallet.domain.model.RecoveryCandidate
import com.example.swisswallet.domain.model.RecoverySession
import com.example.swisswallet.domain.model.SendProgress
import com.example.swisswallet.domain.repository.GuardianRepository
import com.example.swisswallet.domain.repository.RecoveryService
import com.example.swisswallet.domain.repository.WalletRepository
import com.example.swisswallet.util.AddressValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger

/**
 * Social recovery, in plain words:
 *
 *  - The wallet (Coinbase Smart Wallet) has 2 owners: this phone's passkey + the SwissRecoveryModule.
 *  - With the phone in hand, the owner registers 4 guardians in the module: Swissquote (automatic),
 *    + e.g. another device of mine + close ones. Rule: **3 of 4** must agree, then **30 s** wait (48 h in prod).
 *  - Phone lost → the new phone creates a NEW passkey and asks guardians to sign "add this key".
 *    With 3 signatures the module starts a timer; after the delay it adds the new passkey to the wallet
 *    (and removes the lost one). Same address, same funds.
 *  - During the delay the real owner can cancel (if it's an attack).
 *
 * The new phone doesn't control the wallet yet, so it sends its transactions through a small
 * "helper" Coinbase Smart Wallet owned by the new passkey (gas still paid by Pimlico).
 */
class SocialRecoveryService(
    private val store: DataStore<Preferences>,
    private val wallet: WalletRepository,
    private val guardianRepository: GuardianRepository,
    private val vault: PasskeyVault,
    private val rpc: EthereumRpc,
    private val sender: UserOperationSender,
    private val clock: () -> Long = System::currentTimeMillis,
) : RecoveryService {

    private val mutex = Mutex()

    // =========================================================================== owner side

    override suspend fun protectionStatus(): ProtectionStatus {
        val acc = wallet.currentAccount() ?: error("No wallet on this device")
        val deployed = rpc.getCode(acc.smartAccountAddress).isNotEmpty()
        val config = RecoveryModule.configOf(rpc, acc.smartAccountAddress)
        val onChain = if (config.isActive) RecoveryModule.guardiansOf(rpc, acc.smartAccountAddress) else emptyList()
        val pending = RecoveryModule.pendingOf(rpc, acc.smartAccountAddress)
        return ProtectionStatus(
            active = config.isActive,
            threshold = config.threshold,
            delaySeconds = config.delaySeconds,
            guardians = label(onChain, acc.swissquoteGuardian),
            pending = pending?.let { PendingRecovery(RecoveryPolicy.fingerprint(it.x, it.y), it.executeAfter * 1000) },
            walletDeployed = deployed,
        )
    }

    override fun activateProtection(guardians: List<Guardian>): Flow<SendProgress> = flow {
        require(guardians.size == RecoveryPolicy.GUARDIAN_COUNT) { "Choose exactly ${RecoveryPolicy.GUARDIAN_COUNT} guardians." }
        require(guardians.map { it.address.lowercase() }.toSet().size == guardians.size) { "Each guardian must be different." }
        guardians.forEach { guardianRepository.upsert(it) }

        emitAll(
            sender.execute(account = { wallet.smartAccount() }) { account ->
                val calls = buildList {
                    // First wallet on this chain to use recovery? Deploy the shared module (CREATE2, same address everywhere).
                    if (!RecoveryModule.isDeployed(rpc)) {
                        add(Call(ChainConfig.CREATE2_DEPLOYER, BigInteger.ZERO, RecoveryModule.deployCalldata()))
                    }
                    add(
                        Call(
                            RecoveryModule.address, BigInteger.ZERO,
                            RecoveryModule.setupGuardians(guardians.map { it.address }, RecoveryPolicy.THRESHOLD, RecoveryPolicy.DELAY_SECONDS),
                        )
                    )
                }
                if (calls.size == 1) account.encodeExecute(calls[0].target, calls[0].value, calls[0].data)
                else account.encodeExecuteBatch(calls)
            }
        )
    }.catchToFailed()

    override fun cancelPendingRecovery(): Flow<SendProgress> =
        sender.execute(account = { wallet.smartAccount() }) { account ->
            account.encodeExecute(RecoveryModule.address, BigInteger.ZERO, RecoveryModule.cancelRecovery())
        }

    // =========================================================================== new device side

    override val session: Flow<RecoverySession?> = store.data
        .map { p -> p[K_SESSION]?.let(::decodeSession) }
        .distinctUntilChanged()

    override suspend fun lookup(wallet: String): RecoveryCandidate {
        val address = wallet.trim()
        AddressValidator.validate(address, null)?.let { throw IllegalArgumentException(it) }
        if (rpc.getCode(address).isEmpty()) throw IllegalStateException("No smart wallet at this address on ${ChainConfig.NETWORK_NAME}.")
        val config = RecoveryModule.configOf(rpc, address)
        if (!config.isActive) throw IllegalStateException("This wallet has no guardians: it can't be recovered.")
        val pending = RecoveryModule.pendingOf(rpc, address)
        return RecoveryCandidate(
            wallet = address,
            guardians = label(RecoveryModule.guardiansOf(rpc, address), this.wallet.currentAccount()?.swissquoteGuardian),
            threshold = config.threshold,
            delaySeconds = config.delaySeconds,
            pending = pending?.let { PendingRecovery(RecoveryPolicy.fingerprint(it.x, it.y), it.executeAfter * 1000) },
        )
    }

    override suspend fun startRecovery(wallet: String, deviceLabel: String): RecoverySession = mutex.withLock {
        current()?.takeIf { it.request.wallet.equals(wallet.trim(), true) }?.let { return@withLock it }

        val candidate = lookup(wallet)
        if (candidate.pending != null) {
            throw IllegalStateException("A recovery is already running for this wallet (key ${candidate.pending.newKeyFingerprint}).")
        }

        // 1. NEW passkey for this phone.
        val passkeyId = "recovery-${clock()}"
        val passkey = vault.create(passkeyId)

        // 2. Which old key to remove? The lost passkey = the (only) 64-byte owner of the wallet.
        val passkeyOwners = CoinbaseSmartAccount.owners(rpc, candidate.wallet).filter { it.second.size == 64 }
        val revokeIndex = if (passkeyOwners.size == 1) BigInteger.valueOf(passkeyOwners[0].first.toLong()) else RecoveryPolicy.NO_REVOKE

        // 3. What the guardians will sign (includes the module's nonce → one-time use).
        val config = RecoveryModule.configOf(rpc, candidate.wallet)
        val request = RecoveryRequest(
            wallet = candidate.wallet,
            x = passkey.x,
            y = passkey.y,
            revokeIndex = revokeIndex,
            nonce = config.nonce,
            chainId = ChainConfig.CHAIN_ID,
            module = RecoveryModule.address,
            deviceLabel = deviceLabel,
        )
        val s = RecoverySession(
            request = request,
            passkeyId = passkeyId,
            guardians = candidate.guardians,
            threshold = candidate.threshold,
            delaySeconds = candidate.delaySeconds,
            approvals = emptyList(),
            swissquoteGuardian = candidate.guardians.firstOrNull { it.kind == GuardianKind.SWISSQUOTE }?.address,
        )
        save(s)
        s
    }

    override suspend fun addApproval(code: String, fromSwissquote: Boolean): Guardian = mutex.withLock {
        val s = current() ?: error("No recovery in progress")
        val approval = try {
            GuardianApproval.decode(code)
        } catch (e: Exception) {
            throw IllegalArgumentException("This is not a guardian approval code (it starts with SWSIG1.).")
        }
        require(approval.isValidFor(s.request)) { "This approval was signed for another request. Ask the guardian to approve the current code." }
        val known = s.guardians.firstOrNull { it.address.equals(approval.guardian, true) }
            ?: throw IllegalArgumentException("${approval.guardian.take(10)}… is not one of this wallet's guardians.")
        val guardian = if (fromSwissquote) known.copy(name = "Swissquote", kind = GuardianKind.SWISSQUOTE) else known
        save(
            s.copy(
                approvals = s.approvals.filterNot { it.guardian.equals(approval.guardian, true) } + approval,
                guardians = s.guardians.map { if (it.address.equals(guardian.address, true)) guardian else it },
                swissquoteGuardian = if (fromSwissquote) guardian.address else s.swissquoteGuardian,
            )
        )
        guardian
    }

    override suspend fun approveWithDemoGuardians(): Int = mutex.withLock {
        val s = current() ?: error("No recovery in progress")
        val keys = guardianRepository.demoGuardianKeys()
        val newApprovals = s.guardians
            .filter { it.address.lowercase() in keys && it.address.lowercase() !in s.approvedBy }
            .map { EthKeys.approve(keys.getValue(it.address.lowercase()), s.request) }
        if (newApprovals.isNotEmpty()) save(s.copy(approvals = s.approvals + newApprovals))
        newApprovals.size
    }

    override fun submitRecovery(): Flow<SendProgress> = flow {
        val s = current() ?: error("No recovery in progress")
        require(s.hasEnoughApprovals) { "${s.threshold} guardian approvals are needed (you have ${s.approvedBy.size})." }
        sender.execute(account = { helperAccount(s) }) { helper ->
            helper.encodeExecute(
                RecoveryModule.address, BigInteger.ZERO,
                RecoveryModule.initiateRecovery(s.request.wallet, s.request.x, s.request.y, s.request.revokeIndex, s.approvals.map { it.signature }),
            )
        }.collect { p ->
            if (p is SendProgress.Confirmed) markStarted()
            emit(p)
        }
    }.catchToFailed()

    override fun finalizeRecovery(): Flow<SendProgress> = flow {
        val s = current() ?: error("No recovery in progress")
        sender.execute(account = { helperAccount(s) }) { helper ->
            helper.encodeExecute(RecoveryModule.address, BigInteger.ZERO, RecoveryModule.finalizeRecovery(s.request.wallet))
        }.collect { p ->
            if (p is SendProgress.Confirmed) {
                val adopted = runCatching { adopt(s) }
                emit(adopted.fold({ p }, { SendProgress.Failed("Recovered on-chain, but: ${ErrorMapper.humanize(it)}", p.userOpHash) }))
            } else {
                emit(p)
            }
        }
    }.catchToFailed()

    override suspend fun syncSession() {
        val s = current() ?: return
        // Already finished (app closed right after finalize)?
        val ownerBytes = CoinbaseSmartAccount.passkeyOwner(s.request.x, s.request.y)
        if (CoinbaseSmartAccount.findOwnerIndex(rpc, s.request.wallet, ownerBytes) != null) {
            adopt(s)
            return
        }
        val pending = RecoveryModule.pendingOf(rpc, s.request.wallet)
        if (s.executeAfterMillis == null && pending != null && pending.x.contentEquals(s.request.x) && pending.y.contentEquals(s.request.y)) {
            save(s.copy(executeAfterMillis = pending.executeAfter * 1000 + CLOCK_MARGIN_MS))
        }
    }

    /** initiateRecovery() is mined: remember when the delay ends (from the chain, or estimated). */
    private suspend fun markStarted() {
        runCatching { syncSession() }
        val s = current() ?: return
        if (s.executeAfterMillis == null) save(s.copy(executeAfterMillis = clock() + s.delaySeconds * 1000 + CLOCK_MARGIN_MS))
    }

    override suspend fun abandon() {
        mutex.withLock {
            current()?.let { s ->
                if (wallet.currentAccount()?.passkeyId != s.passkeyId) vault.delete(s.passkeyId)
            }
            store.edit { it.remove(K_SESSION) }
        }
    }

    // =========================================================================== helpers

    /** Coinbase Smart Wallet owned only by the new passkey: it pays nothing, it just carries our calls. */
    private suspend fun helperAccount(s: RecoverySession): CoinbaseSmartAccount {
        val passkey = vault.get(s.passkeyId) ?: error("The new passkey was lost. Start the recovery again.")
        val owners = listOf(CoinbaseSmartAccount.passkeyOwner(passkey.x, passkey.y))
        val factory = if (rpc.getCode(ChainConfig.CSW_FACTORY_V1).isNotEmpty()) ChainConfig.CSW_FACTORY_V1 else ChainConfig.CSW_FACTORY_V1_1
        return CoinbaseSmartAccount(
            address = CoinbaseSmartAccount.computeAddress(rpc, factory, owners, BigInteger.ZERO),
            passkey = passkey,
            ownerIndex = 0,
            initialOwners = owners,
            factory = factory,
            factoryNonce = BigInteger.ZERO,
            rpc = rpc,
            entryPoint = ChainConfig.ENTRY_POINT_V06,
            chainId = ChainConfig.CHAIN_ID,
        )
    }

    /** The new passkey is now an owner of the wallet: find its index and make it this phone's wallet. */
    private suspend fun adopt(s: RecoverySession) {
        val index = CoinbaseSmartAccount.findOwnerIndex(rpc, s.request.wallet, CoinbaseSmartAccount.passkeyOwner(s.request.x, s.request.y))
            ?: error("The new passkey isn't an owner of the wallet yet")
        s.guardians.filter { it.kind != GuardianKind.CLOSE_ONE || it.name != UNKNOWN_NAME }.forEach { guardianRepository.upsert(it) }
        wallet.adoptRecoveredWallet(s.request.wallet, s.passkeyId, index, s.swissquoteGuardian)
        store.edit { it.remove(K_SESSION) }
    }

    /** On-chain addresses → guardians with the local names we know. */
    private suspend fun label(addresses: List<String>, swissquote: String?): List<Guardian> {
        val known = guardianRepository.guardians.first()
        return addresses.map { address ->
            known.firstOrNull { it.address.equals(address, true) }
                ?: if (swissquote != null && address.equals(swissquote, true)) Guardian(address, "Swissquote", GuardianKind.SWISSQUOTE)
                else Guardian(address, UNKNOWN_NAME, GuardianKind.CLOSE_ONE)
        }
    }

    private suspend fun current(): RecoverySession? = store.data.first()[K_SESSION]?.let(::decodeSession)

    private suspend fun save(s: RecoverySession) {
        store.edit { it[K_SESSION] = encodeSession(s) }
    }

    private fun encodeSession(s: RecoverySession): String = JSONObject()
        .put("req", s.request.encode())
        .put("pk", s.passkeyId)
        .put("t", s.threshold)
        .put("d", s.delaySeconds)
        .put("ea", s.executeAfterMillis ?: JSONObject.NULL)
        .put("sq", s.swissquoteGuardian ?: JSONObject.NULL)
        .put("g", JSONArray().apply { s.guardians.forEach { put(JSONObject().put("a", it.address).put("n", it.name).put("k", it.kind.name)) } })
        .put("ap", JSONArray().apply { s.approvals.forEach { put(it.encode()) } })
        .toString()

    private fun decodeSession(json: String): RecoverySession? = runCatching {
        val o = JSONObject(json)
        val g = o.getJSONArray("g")
        val ap = o.getJSONArray("ap")
        RecoverySession(
            request = RecoveryRequest.decode(o.getString("req")),
            passkeyId = o.getString("pk"),
            threshold = o.getInt("t"),
            delaySeconds = o.getLong("d"),
            executeAfterMillis = if (o.isNull("ea")) null else o.getLong("ea"),
            swissquoteGuardian = if (o.isNull("sq")) null else o.getString("sq"),
            guardians = (0 until g.length()).map { i ->
                val x = g.getJSONObject(i)
                Guardian(x.getString("a"), x.getString("n"), GuardianKind.valueOf(x.getString("k")))
            },
            approvals = (0 until ap.length()).map { i -> GuardianApproval.decode(ap.getString(i)) },
        )
    }.getOrNull()

    private fun Flow<SendProgress>.catchToFailed(): Flow<SendProgress> =
        catch { e -> emit(SendProgress.Failed(ErrorMapper.humanize(e))) }

    private companion object {
        val K_SESSION = stringPreferencesKey("recovery_session_v1")
        const val UNKNOWN_NAME = "Guardian"
        /** Phone clock vs. block time: wait a little longer than the contract's delay before finalizing. */
        const val CLOCK_MARGIN_MS = 4_000L
    }
}
