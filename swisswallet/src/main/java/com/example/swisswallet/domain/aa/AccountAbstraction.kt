package com.example.swisswallet.domain.aa

import java.math.BigInteger

/**
 * ERC-4337 UserOperation, **EntryPoint v0.7** "unpacked" format (what bundlers expect over RPC).
 *
 * A UserOperation is a "transaction intent" for a smart account. Instead of being sent by an EOA,
 * it's sent to a *bundler*, who packs many of them into one real transaction calling
 * `EntryPoint.handleOps(...)`.
 */
data class UserOperation(
    /** The smart account (contract) that executes the call. */
    val sender: String,
    /** Anti-replay nonce managed by the EntryPoint (`getNonce(sender, key)`). */
    val nonce: BigInteger,
    /** Only for the very first operation: factory that deploys the account (CREATE2). */
    val factory: String? = null,
    val factoryData: ByteArray? = null,
    /** What the account must do: here `execute(to, value, data)`. */
    val callData: ByteArray,
    val callGasLimit: BigInteger = BigInteger.ZERO,
    val verificationGasLimit: BigInteger = BigInteger.ZERO,
    val preVerificationGas: BigInteger = BigInteger.ZERO,
    val maxFeePerGas: BigInteger,
    val maxPriorityFeePerGas: BigInteger,
    /** Paymaster = the contract that pays the gas instead of the account (Pimlico). */
    val paymaster: String? = null,
    val paymasterVerificationGasLimit: BigInteger? = null,
    val paymasterPostOpGasLimit: BigInteger? = null,
    val paymasterData: ByteArray? = null,
    /** Owner's signature over the userOpHash (validated on-chain by the account). */
    val signature: ByteArray,
)

data class GasPrice(val maxFeePerGas: BigInteger, val maxPriorityFeePerGas: BigInteger)

/** What Pimlico's paymaster returns: gas limits + its own signed `paymasterData`. */
data class SponsorshipData(
    val callGasLimit: BigInteger,
    val verificationGasLimit: BigInteger,
    val preVerificationGas: BigInteger,
    val paymaster: String,
    val paymasterVerificationGasLimit: BigInteger,
    val paymasterPostOpGasLimit: BigInteger,
    val paymasterData: ByteArray,
)

data class UserOperationReceipt(
    val success: Boolean,
    val transactionHash: String,
    val reason: String?,
)

/** Signs messages with the owner key (never leaves the device). */
interface Signer {
    val address: String

    /** EIP-191 personal_sign over [message] → 65 bytes r ‖ s ‖ v. */
    fun signMessage(message: ByteArray): ByteArray
}

/** Secure storage for the owner key. */
interface KeyVault {
    suspend fun hasKey(): Boolean
    /** Returns the signer, creating a fresh key the first time. */
    suspend fun getOrCreateSigner(): Signer
    suspend fun getSigner(): Signer?
}

/** Minimal Ethereum JSON-RPC (a normal node, not the bundler). */
interface EthereumRpc {
    suspend fun call(to: String, data: ByteArray): ByteArray
    suspend fun getBalance(address: String): BigInteger
    suspend fun getCode(address: String): ByteArray
}

/** A smart contract account compatible with ERC-4337. */
interface SmartAccount {
    val address: String
    val owner: Signer
    suspend fun isDeployed(): Boolean
    suspend fun getNonce(): BigInteger
    /** (factory, factoryData) used to deploy the account on its first UserOperation. */
    fun factoryArgs(): Pair<String, ByteArray>
    /** callData for "call [to] with [value] wei and [data]". */
    fun encodeExecute(to: String, value: BigInteger, data: ByteArray): ByteArray
    /** Well-formed fake signature used during gas estimation. */
    fun stubSignature(): ByteArray
    fun signUserOperation(userOp: UserOperation, entryPoint: String, chainId: Long): ByteArray
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
