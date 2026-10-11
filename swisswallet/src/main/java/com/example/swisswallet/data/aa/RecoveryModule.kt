package com.example.swisswallet.data.aa

import com.example.swisswallet.domain.aa.EthereumRpc
import org.web3j.crypto.Hash
import org.web3j.crypto.Keys
import java.math.BigInteger

/**
 * Client for **SwissRecoveryModule.sol** (see /contracts). One shared contract per chain, deployed
 * deterministically with CREATE2, so its address is the same everywhere and known in advance.
 */
object RecoveryModule {

    const val CREATE2_DEPLOYER = "0x4e59b44847b379578588920cA78FbF26c0B4956C"

    val initCode: ByteArray by lazy { Hex.bytes(RecoveryModuleBytecode.INIT_CODE) }
    private val salt: ByteArray by lazy { Hex.bytes(RecoveryModuleBytecode.SALT) }

    /** CREATE2 address = keccak256(0xff ‖ deployer ‖ salt ‖ keccak256(initCode))[12:] */
    val address: String by lazy {
        val digest = Hash.sha3(byteArrayOf(0xff.toByte()) + Hex.bytes(CREATE2_DEPLOYER) + salt + Hash.sha3(initCode))
        Keys.toChecksumAddress(Hex.of(digest.copyOfRange(12, 32)))
    }

    /** Call to send to [CREATE2_DEPLOYER] to deploy the module (salt ‖ initCode). */
    fun deployCalldata(): ByteArray = salt + initCode

    suspend fun isDeployed(rpc: EthereumRpc): Boolean = rpc.getCode(address).isNotEmpty()

    // ---------------------------------------------------------------- calldata (executed via the wallet)

    fun setupGuardians(guardians: List<String>, threshold: Int, delaySeconds: Long): ByteArray =
        Abi.encodeCall(
            "setupGuardians(address[],uint64,uint64)",
            Abi.Arg.AddressArray(guardians), Abi.Arg.Uint(threshold.toLong()), Abi.Arg.Uint(delaySeconds),
        )

    fun cancelRecovery(): ByteArray = Abi.encodeCall("cancelRecovery()")

    fun initiateRecovery(wallet: String, x: ByteArray, y: ByteArray, revokeIndex: BigInteger, signatures: List<ByteArray>): ByteArray =
        Abi.encodeCall(
            "initiateRecovery(address,bytes32,bytes32,uint256,bytes[])",
            Abi.Arg.Address(wallet), Abi.Arg.Bytes32(x), Abi.Arg.Bytes32(y), Abi.Arg.Uint(revokeIndex),
            Abi.Arg.BytesArray(signatures),
        )

    fun finalizeRecovery(wallet: String): ByteArray =
        Abi.encodeCall("finalizeRecovery(address)", Abi.Arg.Address(wallet))

    // ---------------------------------------------------------------- reads

    data class Config(val threshold: Int, val delaySeconds: Long, val nonce: BigInteger, val guardianCount: Int) {
        val isActive: Boolean get() = threshold > 0
    }

    data class Pending(
        val x: ByteArray,
        val y: ByteArray,
        val revokeIndex: BigInteger,
        /** Unix seconds after which the recovery can be finalized. */
        val executeAfter: Long,
        val active: Boolean,
    )

    suspend fun configOf(rpc: EthereumRpc, wallet: String): Config {
        if (!isDeployed(rpc)) return Config(0, 0, BigInteger.ZERO, 0)
        val r = rpc.call(address, Abi.encodeCall("configOf(address)", Abi.Arg.Address(wallet)))
        return Config(
            threshold = Abi.decodeUint(r, 0).toInt(),
            delaySeconds = Abi.decodeUint(r, 1).toLong(),
            nonce = Abi.decodeUint(r, 2),
            guardianCount = Abi.decodeUint(r, 3).toInt(),
        )
    }

    suspend fun guardiansOf(rpc: EthereumRpc, wallet: String): List<String> {
        if (!isDeployed(rpc)) return emptyList()
        return Abi.decodeAddressArray(rpc.call(address, Abi.encodeCall("guardiansOf(address)", Abi.Arg.Address(wallet))))
    }

    suspend fun pendingOf(rpc: EthereumRpc, wallet: String): Pending? {
        if (!isDeployed(rpc)) return null
        val r = rpc.call(address, Abi.encodeCall("pendingOf(address)", Abi.Arg.Address(wallet)))
        if (r.size < 160) return null
        val p = Pending(
            x = Abi.decodeBytes32(r, 0),
            y = Abi.decodeBytes32(r, 1),
            revokeIndex = Abi.decodeUint(r, 2),
            executeAfter = Abi.decodeUint(r, 3).toLong(),
            active = Abi.decodeBool(r, 4),
        )
        return p.takeIf { it.active }
    }
}
