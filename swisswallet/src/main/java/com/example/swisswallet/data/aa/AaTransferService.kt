package com.example.swisswallet.data.aa

import com.example.core.common.Formats
import com.example.swisswallet.config.ChainConfig
import com.example.swisswallet.data.rpc.RpcException
import com.example.swisswallet.domain.aa.EthereumRpc
import com.example.swisswallet.domain.model.SendProgress
import com.example.swisswallet.domain.model.TransferRequest
import com.example.swisswallet.domain.model.TxKind
import com.example.swisswallet.domain.model.TxRecord
import com.example.swisswallet.domain.model.TxStatus
import com.example.swisswallet.domain.repository.TransactionRepository
import com.example.swisswallet.domain.repository.TransferService
import com.example.swisswallet.domain.repository.WalletRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.IOException
import java.math.BigInteger
import java.net.UnknownHostException

/**
 * Gasless transfer (ETH or USDC) through ERC-4337 + Pimlico.
 * callData = CoinbaseSmartWallet.execute(to, value, data); the rest is [UserOperationSender].
 */
class AaTransferService(
    private val wallet: WalletRepository,
    private val rpc: EthereumRpc,
    private val sender: UserOperationSender,
    private val history: TransactionRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : TransferService {

    override fun send(request: TransferRequest): Flow<SendProgress> = flow {
        if (!ChainConfig.hasPimlicoKey) {
            emit(SendProgress.Failed("Missing Pimlico API key. Add PIMLICO_API_KEY=… to local.properties, then rebuild."))
            return@flow
        }
        val token = request.token
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

        val callData = if (token.isNative) {
            account.encodeExecute(request.to, request.amountRaw, ByteArray(0))
        } else {
            account.encodeExecute(token.contractAddress!!, BigInteger.ZERO, Erc20.transfer(request.to, request.amountRaw))
        }

        val userOpHash = sender.submit(account, callData) { if (it != SendProgress.Preparing) emit(it) }

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

        val receipt = sender.waitForReceipt(userOpHash)
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
}

/** Turns raw RPC / EntryPoint errors (AAxx codes) into messages a user understands. */
object ErrorMapper {
    /** Custom errors of SwissRecoveryModule.sol, recognised by their 4-byte selector in revert data. */
    private val moduleErrors: List<Pair<String, String>> by lazy {
        listOf(
            "NotEnoughApprovals(uint256,uint256)" to "Not enough guardian approvals yet (3 of 4 needed).",
            "TooEarly(uint256)" to "The safety delay isn't over yet. Wait a few seconds and try again.",
            "NoPendingRecovery()" to "There is no recovery in progress for this wallet.",
            "RecoveryAlreadyPending()" to "A recovery is already in progress for this wallet.",
            "NotConfigured()" to "This wallet has no guardians set up, it can't be recovered.",
            "InvalidConfig()" to "Invalid guardian list (4 different addresses needed).",
        ).map { (sig, text) -> Hex.of(Abi.selector(sig)).removePrefix("0x") to text }
    }

    fun humanize(e: Throwable): String {
        val msg = e.message.orEmpty()
        val data = ((e as? RpcException)?.data.orEmpty() + " " + msg).lowercase()
        moduleErrors.firstOrNull { data.contains(it.first) }?.let { return it.second }
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
