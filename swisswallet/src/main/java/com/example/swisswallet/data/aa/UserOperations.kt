package com.example.swisswallet.data.aa

import com.example.swisswallet.domain.aa.UserOperation
import org.json.JSONObject
import org.web3j.crypto.Hash
import java.math.BigInteger

/**
 * userOpHash for **EntryPoint v0.6** — exactly what `EntryPoint.getUserOpHash()` computes on-chain:
 *
 * ```
 * packed = abi.encode(sender, nonce, keccak(initCode), keccak(callData), callGasLimit,
 *                     verificationGasLimit, preVerificationGas, maxFeePerGas,
 *                     maxPriorityFeePerGas, keccak(paymasterAndData))
 * userOpHash = keccak(abi.encode(keccak(packed), entryPoint, chainId))
 * ```
 * Coinbase Smart Wallet signs this raw hash (it becomes the WebAuthn "challenge").
 */
object UserOperationHasher {

    fun hash(op: UserOperation, entryPoint: String, chainId: Long): ByteArray {
        val packed = Abi.encodeArgs(
            Abi.Arg.Address(op.sender),
            Abi.Arg.Uint(op.nonce),
            Abi.Arg.Bytes32(Hash.sha3(op.initCode)),
            Abi.Arg.Bytes32(Hash.sha3(op.callData)),
            Abi.Arg.Uint(op.callGasLimit),
            Abi.Arg.Uint(op.verificationGasLimit),
            Abi.Arg.Uint(op.preVerificationGas),
            Abi.Arg.Uint(op.maxFeePerGas),
            Abi.Arg.Uint(op.maxPriorityFeePerGas),
            Abi.Arg.Bytes32(Hash.sha3(op.paymasterAndData)),
        )
        return Hash.sha3(
            Abi.encodeArgs(
                Abi.Arg.Bytes32(Hash.sha3(packed)),
                Abi.Arg.Address(entryPoint),
                Abi.Arg.Uint(BigInteger.valueOf(chainId)),
            )
        )
    }
}

/** Serialises a v0.6 UserOperation the way bundlers expect it over JSON-RPC (hex quantities). */
fun UserOperation.toRpcJson(): JSONObject = JSONObject()
    .put("sender", sender)
    .put("nonce", Hex.quantity(nonce))
    .put("initCode", Hex.of(initCode))
    .put("callData", Hex.of(callData))
    .put("callGasLimit", Hex.quantity(callGasLimit))
    .put("verificationGasLimit", Hex.quantity(verificationGasLimit))
    .put("preVerificationGas", Hex.quantity(preVerificationGas))
    .put("maxFeePerGas", Hex.quantity(maxFeePerGas))
    .put("maxPriorityFeePerGas", Hex.quantity(maxPriorityFeePerGas))
    .put("paymasterAndData", Hex.of(paymasterAndData))
    .put("signature", Hex.of(signature))
