package com.example.swisswallet.data.aa

import com.example.core.common.Formats
import com.example.swisswallet.config.ChainConfig
import com.example.swisswallet.data.rpc.RpcException
import com.example.swisswallet.domain.aa.BundlerClient
import com.example.swisswallet.domain.aa.EthereumRpc
import com.example.swisswallet.domain.aa.PaymasterClient
import com.example.swisswallet.domain.aa.UserOperation
import com.example.swisswallet.domain.aa.UserOperationReceipt
import com.example.swisswallet.domain.model.SendProgress
import com.example.swisswallet.domain.model.TransferRequest
import com.example.swisswallet.domain.model.TxKind
import com.example.swisswallet.domain.model.TxRecord
import com.example.swisswallet.domain.model.TxStatus
import com.example.swisswallet.domain.repository.TransactionRepository
import com.example.swisswallet.domain.repository.TransferService
import com.example.swisswallet.domain.repository.WalletRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.IOException
import java.math.BigInteger
import java.net.UnknownHostException

/**
 * Full gasless transfer through ERC-4337 + Pimlico:
 *
 *  1. build callData = SimpleAccount.execute(to, value, data)
 *  2. add initCode (factory/factoryData) if the account isn't deployed yet
 *  3. pm_sponsorUserOperation  → gas limits + paymaster signature (Pimlico pays the gas)
 *  4. sign userOpHash with the owner key (Android Keystore)
 *  5. eth_sendUserOperation    → userOpHash
 *  6. poll eth_getUserOperationReceipt → real transaction hash
 */
class AaTransferService(
    private val wallet: WalletRepository,
    private val rpc: EthereumRpc,
    private val bundler: BundlerClient,
    private val paymaster: PaymasterClient,
    private val history: TransactionRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : TransferService {

    override fun send(request: TransferRequest): Flow<SendProgress> = flow {
        if (!ChainConfig.hasPimlicoKey) {
            emit(SendProgress.Failed("Missing Pimlico API key. Add PIMLICO_API_KEY=… to local.properties, then rebuild."))
            return@flow
        }
        val token = request.token
        val entryPoint = ChainConfig.ENTRY_POINT_V07

        // ---- 1. Prepare ------------------------------------------------------------------
        emit(SendProgress.Preparing)
        val account = wallet.smartAccount()

        val balance = if (token.isNative) {
            rpc.getBalance(account.address)
        } else {
            Abi.decodeUint(rpc.call(token.contractAddress!!, Erc20.balanceOf(account.address)))
        }
        if (balance < request.amountRaw) {
            emit(SendProgress.Failed("Insufficient ${token.symbol} balance. Use the faucet in Receive to get test funds."))
            return@flow
        }

        val (deployed, nonce, gasPrice) = coroutineScope {
            val d = async { account.isDeployed() }
            val n = async { account.getNonce() }
            val g = async { bundler.getUserOperationGasPrice() }
            Triple(d.await(), n.await(), g.await())
        }

        val callData = if (token.isNative) {
            account.encodeExecute(request.to, request.amountRaw, ByteArray(0))
        } else {
            account.encodeExecute(token.contractAddress!!, BigInteger.ZERO, Erc20.transfer(request.to, request.amountRaw))
        }
        val init: Pair<String?, ByteArray?> = if (deployed) null to null else account.factoryArgs()

        var userOp = UserOperation(
            sender = account.address,
            nonce = nonce,
            factory = init.first,
            factoryData = init.second,
            callData = callData,
            maxFeePerGas = gasPrice.maxFeePerGas,
            maxPriorityFeePerGas = gasPrice.maxPriorityFeePerGas,
            signature = account.stubSignature(),
        )

        // ---- 2. Paymaster sponsorship (gasless) -------------------------------------------
        emit(SendProgress.Sponsoring)
        val sponsor = paymaster.sponsorUserOperation(userOp, entryPoint)
        userOp = userOp.copy(
            callGasLimit = sponsor.callGasLimit,
            verificationGasLimit = sponsor.verificationGasLimit,
            preVerificationGas = sponsor.preVerificationGas,
            paymaster = sponsor.paymaster,
            paymasterVerificationGasLimit = sponsor.paymasterVerificationGasLimit,
            paymasterPostOpGasLimit = sponsor.paymasterPostOpGasLimit,
            paymasterData = sponsor.paymasterData,
        )

        // ---- 3. Sign -----------------------------------------------------------------------
        emit(SendProgress.Signing)
        userOp = userOp.copy(signature = account.signUserOperation(userOp, entryPoint, ChainConfig.CHAIN_ID))

        // ---- 4. Send to bundler ------------------------------------------------------------
        emit(SendProgress.Submitting)
        val userOpHash = bundler.sendUserOperation(userOp, entryPoint)

        val record = TxRecord(
            id = userOpHash,
            kind = TxKind.SENT,
            tokenSymbol = token.symbol,
            amount = Formats.token(token.toDecimal(request.amountRaw)),
            counterparty = request.to,
            timestamp = clock(),
            status = TxStatus.PENDING,
            userOpHash = userOpHash,
        )
        history.upsert(record)
        emit(SendProgress.Pending(userOpHash))

        // ---- 5. Wait for inclusion ---------------------------------------------------------
        val receipt = waitForReceipt(userOpHash)
        when {
            receipt == null -> emit(
                SendProgress.Failed("Still pending after 2 minutes. History will update once it's mined.", userOpHash)
            )
            receipt.success -> {
                history.upsert(record.copy(status = TxStatus.CONFIRMED, txHash = receipt.transactionHash))
                emit(SendProgress.Confirmed(userOpHash, receipt.transactionHash))
            }
            else -> {
                history.upsert(record.copy(status = TxStatus.FAILED, txHash = receipt.transactionHash, error = receipt.reason))
                emit(SendProgress.Failed("The transfer reverted on-chain${receipt.reason?.let { ": $it" } ?: "."}", userOpHash))
            }
        }
    }
        .catch { e -> emit(SendProgress.Failed(ErrorMapper.humanize(e))) }
        .flowOn(Dispatchers.IO)

    private suspend fun waitForReceipt(userOpHash: String): UserOperationReceipt? {
        val deadline = clock() + 120_000
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
}

/** Turns raw RPC / EntryPoint errors (AAxx codes) into messages a user understands. */
object ErrorMapper {
    fun humanize(e: Throwable): String {
        val msg = e.message.orEmpty()
        return when {
            e is UnknownHostException -> "No internet connection."
            e is RpcException && (e.code == 401 || e.code == 403 || msg.contains("api key", true) || msg.contains("unauthorized", true)) ->
                "Pimlico rejected the API key. Check PIMLICO_API_KEY in local.properties."
            msg.contains("AA21") -> "The account can't pre-fund gas and sponsorship was refused."
            msg.contains("AA23") || msg.contains("AA24") -> "Signature rejected by the smart account."
            msg.contains("AA25") -> "Nonce conflict — another operation is pending. Try again in a few seconds."
            msg.contains("AA3") -> "Pimlico's paymaster couldn't sponsor this operation ($msg)."
            msg.contains("AA1") -> "Smart account deployment failed ($msg)."
            msg.contains("insufficient", true) || msg.contains("exceeds balance", true) ->
                "Insufficient balance for this transfer."
            msg.contains("revert", true) -> "The transfer would fail on-chain. Check the amount and the address."
            e is IOException -> "Network error: ${msg.ifBlank { "please retry" }}"
            else -> msg.ifBlank { "Unexpected error" }
        }
    }
}
