package com.example.swisswallet.data.aa

import com.example.swisswallet.config.ChainConfig
import com.example.swisswallet.domain.aa.BundlerClient
import com.example.swisswallet.domain.aa.PaymasterClient
import com.example.swisswallet.domain.aa.SmartAccount
import com.example.swisswallet.domain.aa.UserOperation
import com.example.swisswallet.domain.aa.UserOperationReceipt
import com.example.swisswallet.domain.model.SendProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * The ERC-4337 pipeline, shared by every on-chain action of the app (send money, activate
 * guardians, cancel a recovery, start / finish a recovery):
 *
 *  1. read nonce + "is the account deployed?" + gas price
 *  2. add initCode (factory + createAccount(owners, nonce)) if the account doesn't exist yet
 *  3. pm_sponsorUserOperation → Pimlico estimates the gas and agrees to pay it (gasless for the user)
 *  4. sign the userOpHash with the passkey (WebAuthn P-256)
 *  5. eth_sendUserOperation → the bundler puts it in a real transaction
 *  6. eth_getUserOperationReceipt (polling) → real transaction hash
 */
class UserOperationSender(
    private val bundler: BundlerClient,
    private val paymaster: PaymasterClient,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val entryPoint = ChainConfig.ENTRY_POINT_V06

    /** Steps 1 → 5. Returns the userOpHash. */
    suspend fun submit(
        account: SmartAccount,
        callData: ByteArray,
        progress: suspend (SendProgress) -> Unit = {},
    ): String {
        check(ChainConfig.hasPimlicoKey) { "Missing Pimlico API key. Add PIMLICO_API_KEY=… to local.properties, then rebuild." }

        progress(SendProgress.Preparing)
        val (deployed, nonce, gasPrice) = coroutineScope {
            val d = async { account.isDeployed() }
            val n = async { account.getNonce() }
            val g = async { bundler.getUserOperationGasPrice() }
            Triple(d.await(), n.await(), g.await())
        }
        var op = UserOperation(
            sender = account.address,
            nonce = nonce,
            initCode = if (deployed) ByteArray(0) else account.initCode(),
            callData = callData,
            maxFeePerGas = gasPrice.maxFeePerGas,
            maxPriorityFeePerGas = gasPrice.maxPriorityFeePerGas,
            signature = account.stubSignature(),
        )

        progress(SendProgress.Sponsoring)
        val sponsor = paymaster.sponsorUserOperation(op, entryPoint)
        op = op.copy(
            callGasLimit = sponsor.callGasLimit,
            verificationGasLimit = sponsor.verificationGasLimit,
            preVerificationGas = sponsor.preVerificationGas,
            paymasterAndData = sponsor.paymasterAndData,
        )

        progress(SendProgress.Signing)
        op = op.copy(signature = account.signUserOperation(op))

        progress(SendProgress.Submitting)
        return bundler.sendUserOperation(op, entryPoint)
    }

    /** Step 6. null if still not mined after [timeoutMs]. */
    suspend fun waitForReceipt(userOpHash: String, timeoutMs: Long = 120_000): UserOperationReceipt? {
        val deadline = clock() + timeoutMs
        while (clock() < deadline) {
            try {
                bundler.getUserOperationReceipt(userOpHash)?.let { return it }
            } catch (e: CancellationException) {
                throw e
            } catch (ignored: Exception) {
                // transient network error: keep polling
            }
            delay(2_000)
        }
        return null
    }

    /** Whole pipeline as a Flow, ending with [SendProgress.Confirmed] or [SendProgress.Failed]. */
    fun execute(account: suspend () -> SmartAccount, callData: suspend (SmartAccount) -> ByteArray): Flow<SendProgress> = flow {
        val acc = account()
        val hash = submit(acc, callData(acc)) { emit(it) }
        emit(SendProgress.Pending(hash))
        val receipt = waitForReceipt(hash)
        when {
            receipt == null -> emit(SendProgress.Failed("Still pending after 2 minutes. Try again in a moment.", hash))
            receipt.success -> emit(SendProgress.Confirmed(hash, receipt.transactionHash))
            else -> emit(SendProgress.Failed("Reverted on-chain${receipt.reason?.let { ": ${ErrorMapper.humanize(IllegalStateException(it))}" } ?: "."}", hash))
        }
    }
        .catch { e -> emit(SendProgress.Failed(ErrorMapper.humanize(e))) }
        .flowOn(Dispatchers.IO)
}
