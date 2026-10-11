package com.example.swisswallet.domain.aa

import java.math.BigInteger

/**
 * ERC-4337 UserOperation, **EntryPoint v0.6** format (the version Coinbase Smart Wallet uses).
 *
 * A UserOperation is a signed "letter of request" from a smart account: the bundler collects many
 * of them and submits them in one real transaction to the EntryPoint contract.
 */
data class UserOperation(
    /** The smart account (contract) that executes the call. */
    val sender: String,
    /** Anti-replay nonce managed by the EntryPoint (`getNonce(sender, key)`). */
    val nonce: BigInteger,
    /** factory ‖ factoryData — only for the very first operation (deploys the account). */
    val initCode: ByteArray = ByteArray(0),
    /** What the account must do, e.g. `execute(to, value, data)`. */
    val callData: ByteArray,
    val callGasLimit: BigInteger = BigInteger.ZERO,
    val verificationGasLimit: BigInteger = BigInteger.ZERO,
    val preVerificationGas: BigInteger = BigInteger.ZERO,
    val maxFeePerGas: BigInteger,
    val maxPriorityFeePerGas: BigInteger,
    /** paymaster ‖ its data — set by Pimlico when it agrees to pay the gas. */
    val paymasterAndData: ByteArray = ByteArray(0),
    /** Owner signature (Coinbase "SignatureWrapper": owner index + passkey/WebAuthn signature). */
    val signature: ByteArray = ByteArray(0),
)

data class GasPrice(val maxFeePerGas: BigInteger, val maxPriorityFeePerGas: BigInteger)

/** What Pimlico's paymaster returns for v0.6: gas limits + its signed `paymasterAndData`. */
data class SponsorshipData(
    val callGasLimit: BigInteger,
    val verificationGasLimit: BigInteger,
    val preVerificationGas: BigInteger,
    val paymasterAndData: ByteArray,
)

data class UserOperationReceipt(
    val success: Boolean,
    val transactionHash: String,
    val reason: String?,
)

/** A WebAuthn-style assertion produced by a passkey (what Coinbase Smart Wallet verifies on-chain). */
class WebAuthnSignature(
    val authenticatorData: ByteArray,
    val clientDataJSON: String,
    val challengeIndex: Int,
    val typeIndex: Int,
    val r: BigInteger,
    val s: BigInteger,
)

/**
 * A passkey: a P-256 key pair generated inside the phone's secure hardware (Android Keystore).
 * The private key can never be exported; we only get its public coordinates (x, y).
 */
interface Passkey {
    val id: String
    val x: ByteArray
    val y: ByteArray

    /** Signs [challenge] (e.g. a userOpHash) the WebAuthn way. */
    fun sign(challenge: ByteArray): WebAuthnSignature
}

interface PasskeyVault {
    suspend fun create(id: String): Passkey
    suspend fun get(id: String): Passkey?
    suspend fun delete(id: String)
}

/** Minimal Ethereum JSON-RPC (a normal node, not the bundler). */
interface EthereumRpc {
    suspend fun call(to: String, data: ByteArray): ByteArray
    suspend fun getBalance(address: String): BigInteger
    suspend fun getCode(address: String): ByteArray
}

/** One call made by the smart account: "call [target] with [value] wei and [data]". */
class Call(val target: String, val value: BigInteger, val data: ByteArray)

/** An ERC-4337 smart account able to sign its own UserOperations. */
interface SmartAccount {
    val address: String
    suspend fun isDeployed(): Boolean
    suspend fun getNonce(): BigInteger
    /** factory ‖ createAccount(...) — used only while the account isn't deployed. */
    fun initCode(): ByteArray
    /** callData for "call [to] with [value] wei and [data]". */
    fun encodeExecute(to: String, value: BigInteger, data: ByteArray): ByteArray
    /** callData for several calls executed atomically. */
    fun encodeExecuteBatch(calls: List<Call>): ByteArray
    /** Well-formed signature used during gas estimation (same size & code path as a real one). */
    fun stubSignature(): ByteArray
    fun signUserOperation(userOp: UserOperation): ByteArray
}

/** Bundler (ERC-4337 mempool). */
interface BundlerClient {
    suspend fun getUserOperationGasPrice(): GasPrice
    suspend fun sendUserOperation(userOp: UserOperation, entryPoint: String): String
    suspend fun getUserOperationReceipt(userOpHash: String): UserOperationReceipt?
}

/** Paymaster (sponsors gas). */
interface PaymasterClient {
    suspend fun sponsorUserOperation(userOp: UserOperation, entryPoint: String): SponsorshipData
}
