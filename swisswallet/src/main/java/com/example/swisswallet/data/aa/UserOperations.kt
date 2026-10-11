package com.example.swisswallet.data.aa

import com.example.swisswallet.domain.aa.UserOperation
import org.json.JSONObject
import org.web3j.crypto.Hash
import org.web3j.utils.Numeric
import java.math.BigInteger

/**
 * userOpHash for **EntryPoint v0.7** — exactly what `EntryPoint.getUserOpHash()` computes on-chain:
 *
 * ```
 * packed = abi.encode(
 *   sender, nonce, keccak(initCode), keccak(callData),
 *   accountGasLimits   = verificationGasLimit(16 bytes) ‖ callGasLimit(16 bytes),
 *   preVerificationGas,
 *   gasFees            = maxPriorityFeePerGas(16 bytes) ‖ maxFeePerGas(16 bytes),
 *   keccak(paymasterAndData))
 * userOpHash = keccak(abi.encode(keccak(packed), entryPoint, chainId))
 * ```
 * The account signs this hash (SimpleAccount checks it with `toEthSignedMessageHash`, i.e. EIP-191).
 */
object UserOperationHasher {

    private fun uint128(v: BigInteger): ByteArray = Numeric.toBytesPadded(v, 16)

    fun initCode(op: UserOperation): ByteArray =
        if (op.factory != null) Hex.bytes(op.factory) + (op.factoryData ?: ByteArray(0)) else ByteArray(0)

    fun paymasterAndData(op: UserOperation): ByteArray =
        if (op.paymaster != null) {
            Hex.bytes(op.paymaster) +
                uint128(op.paymasterVerificationGasLimit ?: BigInteger.ZERO) +
                uint128(op.paymasterPostOpGasLimit ?: BigInteger.ZERO) +
                (op.paymasterData ?: ByteArray(0))
        } else ByteArray(0)

    fun hash(op: UserOperation, entryPoint: String, chainId: Long): ByteArray {
        val packed = Abi.addressWord(op.sender) +
            Abi.word(op.nonce) +
            Hash.sha3(initCode(op)) +
            Hash.sha3(op.callData) +
            uint128(op.verificationGasLimit) + uint128(op.callGasLimit) +
            Abi.word(op.preVerificationGas) +
            uint128(op.maxPriorityFeePerGas) + uint128(op.maxFeePerGas) +
            Hash.sha3(paymasterAndData(op))
        return Hash.sha3(Hash.sha3(packed) + Abi.addressWord(entryPoint) + Abi.word(BigInteger.valueOf(chainId)))
    }
}

/** Serialises a UserOperation the way bundlers expect it over JSON-RPC (v0.7, hex quantities). */
fun UserOperation.toRpcJson(): JSONObject = JSONObject().apply {
    put("sender", sender)
    put("nonce", Hex.quantity(nonce))
    if (factory != null) {
        put("factory", factory)
        put("factoryData", Hex.of(factoryData ?: ByteArray(0)))
    }
    put("callData", Hex.of(callData))
    put("callGasLimit", Hex.quantity(callGasLimit))
    put("verificationGasLimit", Hex.quantity(verificationGasLimit))
    put("preVerificationGas", Hex.quantity(preVerificationGas))
    put("maxFeePerGas", Hex.quantity(maxFeePerGas))
    put("maxPriorityFeePerGas", Hex.quantity(maxPriorityFeePerGas))
    if (paymaster != null) {
        put("paymaster", paymaster)
        put("paymasterVerificationGasLimit", Hex.quantity(paymasterVerificationGasLimit ?: BigInteger.ZERO))
        put("paymasterPostOpGasLimit", Hex.quantity(paymasterPostOpGasLimit ?: BigInteger.ZERO))
        put("paymasterData", Hex.of(paymasterData ?: ByteArray(0)))
    }
    put("signature", Hex.of(signature))
}
