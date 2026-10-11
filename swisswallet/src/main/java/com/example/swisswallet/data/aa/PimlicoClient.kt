package com.example.swisswallet.data.aa

import com.example.swisswallet.data.rpc.JsonRpcClient
import com.example.swisswallet.data.rpc.RpcException
import com.example.swisswallet.domain.aa.BundlerClient
import com.example.swisswallet.domain.aa.GasPrice
import com.example.swisswallet.domain.aa.PaymasterClient
import com.example.swisswallet.domain.aa.SponsorshipData
import com.example.swisswallet.domain.aa.UserOperation
import com.example.swisswallet.domain.aa.UserOperationReceipt
import org.json.JSONObject

/**
 * Pimlico exposes the **bundler** (eth_sendUserOperation, eth_getUserOperationReceipt…) and the
 * **verifying paymaster** (pm_sponsorUserOperation) on the same endpoint:
 * `https://api.pimlico.io/v2/sepolia/rpc?apikey=…`
 */
class PimlicoClient(private val rpc: JsonRpcClient) : BundlerClient, PaymasterClient {

    override suspend fun getUserOperationGasPrice(): GasPrice {
        val result = rpc.call("pimlico_getUserOperationGasPrice") as? JSONObject
            ?: throw RpcException(-1, "Empty gas price response")
        val fast = result.getJSONObject("fast")
        return GasPrice(
            maxFeePerGas = Hex.toBigInt(fast.getString("maxFeePerGas")),
            maxPriorityFeePerGas = Hex.toBigInt(fast.getString("maxPriorityFeePerGas")),
        )
    }

    /**
     * The paymaster simulates the UserOperation, estimates its gas and signs `paymasterData`
     * ("I, Pimlico, will pay for this op"). Gas is deducted from your Pimlico balance — free on testnets.
     */
    override suspend fun sponsorUserOperation(userOp: UserOperation, entryPoint: String): SponsorshipData {
        val r = rpc.call("pm_sponsorUserOperation", userOp.toRpcJson(), entryPoint) as? JSONObject
            ?: throw RpcException(-1, "Empty sponsorship response")
        return SponsorshipData(
            callGasLimit = Hex.toBigInt(r.getString("callGasLimit")),
            verificationGasLimit = Hex.toBigInt(r.getString("verificationGasLimit")),
            preVerificationGas = Hex.toBigInt(r.getString("preVerificationGas")),
            paymaster = r.getString("paymaster"),
            paymasterVerificationGasLimit = Hex.toBigInt(r.getString("paymasterVerificationGasLimit")),
            paymasterPostOpGasLimit = Hex.toBigInt(r.getString("paymasterPostOpGasLimit")),
            paymasterData = Hex.bytes(r.optString("paymasterData", "0x")),
        )
    }

    override suspend fun sendUserOperation(userOp: UserOperation, entryPoint: String): String =
        rpc.call("eth_sendUserOperation", userOp.toRpcJson(), entryPoint) as? String
            ?: throw RpcException(-1, "Bundler returned no userOpHash")

    /** null while the operation is still in the mempool. */
    override suspend fun getUserOperationReceipt(userOpHash: String): UserOperationReceipt? {
        val r = rpc.call("eth_getUserOperationReceipt", userOpHash) as? JSONObject ?: return null
        val receipt = r.optJSONObject("receipt")
        val reason = if (r.has("reason") && !r.isNull("reason")) r.getString("reason") else null
        return UserOperationReceipt(
            success = r.optBoolean("success", false),
            transactionHash = receipt?.optString("transactionHash").orEmpty(),
            reason = reason?.takeIf { it.isNotBlank() && it != "0x" },
        )
    }
}
