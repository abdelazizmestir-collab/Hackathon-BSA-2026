package com.example.swisswallet.data.aa

import com.example.swisswallet.config.ChainConfig
import com.example.swisswallet.data.rpc.RpcException
import com.example.swisswallet.domain.aa.EthereumRpc
import com.example.swisswallet.domain.aa.Signer
import com.example.swisswallet.domain.aa.SmartAccount
import com.example.swisswallet.domain.aa.UserOperation
import kotlinx.coroutines.CancellationException
import java.math.BigInteger

/**
 * eth-infinitism **SimpleAccount** (EntryPoint v0.7): a minimal smart contract wallet with one
 * owner (our Keystore EOA). It is deployed lazily by [ChainConfig.SIMPLE_ACCOUNT_FACTORY_V07]
 * with CREATE2, so its address is known *before* it exists ("counterfactual" address) and can
 * already receive funds.
 */
class SimpleSmartAccount(
    override val owner: Signer,
    override val address: String,
    private val rpc: EthereumRpc,
    private val factory: String = ChainConfig.SIMPLE_ACCOUNT_FACTORY_V07,
    private val entryPoint: String = ChainConfig.ENTRY_POINT_V07,
    private val salt: BigInteger = BigInteger.ZERO,
) : SmartAccount {

    override suspend fun isDeployed(): Boolean = rpc.getCode(address).isNotEmpty()

    /** EntryPoint.getNonce(sender, key=0) */
    override suspend fun getNonce(): BigInteger {
        val data = Abi.encodeCall(
            "getNonce(address,uint192)",
            Abi.Arg.Address(address),
            Abi.Arg.Uint(BigInteger.ZERO),
        )
        return Abi.decodeUint(rpc.call(entryPoint, data))
    }

    /** factory.createAccount(owner, salt) — executed by the EntryPoint on the first UserOperation. */
    override fun factoryArgs(): Pair<String, ByteArray> =
        factory to createAccountCall(owner.address, salt)

    /** SimpleAccount.execute(dest, value, func) */
    override fun encodeExecute(to: String, value: BigInteger, data: ByteArray): ByteArray =
        Abi.encodeCall(
            "execute(address,uint256,bytes)",
            Abi.Arg.Address(to),
            Abi.Arg.Uint(value),
            Abi.Arg.Bytes(data),
        )

    /** Same stub permissionless.js uses: a valid-looking 65-byte ECDSA signature for simulation. */
    override fun stubSignature(): ByteArray =
        Hex.bytes("0x" + "f".repeat(31) + "0".repeat(33) + "7" + "a".repeat(63) + "1c")

    override fun signUserOperation(userOp: UserOperation, entryPoint: String, chainId: Long): ByteArray =
        owner.signMessage(UserOperationHasher.hash(userOp, entryPoint, chainId))

    companion object {
        private const val SENDER_ADDRESS_RESULT = "0x6ca7b806" // error SenderAddressResult(address)

        private fun createAccountCall(owner: String, salt: BigInteger) =
            Abi.encodeCall("createAccount(address,uint256)", Abi.Arg.Address(owner), Abi.Arg.Uint(salt))

        /**
         * Counterfactual address: asks the factory (`getAddress(owner, salt)`), and falls back to
         * `EntryPoint.getSenderAddress(initCode)` which reverts with `SenderAddressResult(address)`.
         */
        suspend fun computeAddress(
            owner: String,
            rpc: EthereumRpc,
            factory: String = ChainConfig.SIMPLE_ACCOUNT_FACTORY_V07,
            entryPoint: String = ChainConfig.ENTRY_POINT_V07,
            salt: BigInteger = BigInteger.ZERO,
        ): String {
            try {
                val res = rpc.call(
                    factory,
                    Abi.encodeCall("getAddress(address,uint256)", Abi.Arg.Address(owner), Abi.Arg.Uint(salt)),
                )
                if (res.size >= 32) return Abi.decodeAddress(res)
            } catch (e: CancellationException) {
                throw e
            } catch (ignored: Exception) {
                // fall through to EntryPoint.getSenderAddress
            }

            val initCode = Hex.bytes(factory) + createAccountCall(owner, salt)
            val call = Abi.encodeCall("getSenderAddress(bytes)", Abi.Arg.Bytes(initCode))
            try {
                rpc.call(entryPoint, call)
            } catch (e: RpcException) {
                val data = e.data?.trim('"')
                if (data != null && data.startsWith(SENDER_ADDRESS_RESULT, ignoreCase = true)) {
                    return Abi.decodeAddress(Hex.bytes("0x" + data.removePrefix("0x").substring(8)))
                }
                throw e
            }
            throw IllegalStateException("Couldn't compute the smart account address")
        }
    }
}
