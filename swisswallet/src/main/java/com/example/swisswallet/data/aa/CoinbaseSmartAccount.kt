package com.example.swisswallet.data.aa

import com.example.swisswallet.domain.aa.Call
import com.example.swisswallet.domain.aa.EthereumRpc
import com.example.swisswallet.domain.aa.Passkey
import com.example.swisswallet.domain.aa.SmartAccount
import com.example.swisswallet.domain.aa.UserOperation
import com.example.swisswallet.domain.aa.WebAuthnSignature
import java.math.BigInteger

/**
 * **Coinbase Smart Wallet** (ERC-4337, EntryPoint v0.6).
 *
 * Plain words: the wallet is a smart contract (a programmable safe). Its list of "owners" says
 * who may give it orders. Ours has:
 *   - owner #0: a passkey (P-256 key in the phone's secure chip) → used every day,
 *   - owner #1: the SwissRecoveryModule → can only ADD a new passkey after 3-of-4 guardians + delay.
 * Because the safe's address doesn't depend on the key, the key can be replaced without moving funds.
 *
 * @param initialOwners owners used when the account was created (needed for its initCode / address)
 * @param ownerIndex index of OUR passkey in the wallet's owner list (changes after a recovery)
 */
class CoinbaseSmartAccount(
    override val address: String,
    private val passkey: Passkey,
    private val ownerIndex: Int,
    private val initialOwners: List<ByteArray>,
    private val factory: String,
    private val factoryNonce: BigInteger,
    private val rpc: EthereumRpc,
    private val entryPoint: String,
    private val chainId: Long,
) : SmartAccount {

    /** Our passkey's public key as stored by the wallet (x ‖ y). */
    val passkeyOwnerBytes: ByteArray get() = passkeyOwner(passkey.x, passkey.y)

    override suspend fun isDeployed(): Boolean = rpc.getCode(address).isNotEmpty()

    /** EntryPoint.getNonce(sender, key = 0) */
    override suspend fun getNonce(): BigInteger = Abi.decodeUint(
        rpc.call(entryPoint, Abi.encodeCall("getNonce(address,uint192)", Abi.Arg.Address(address), Abi.Arg.Uint(0)))
    )

    override fun initCode(): ByteArray = Hex.bytes(factory) + createAccountCall(initialOwners, factoryNonce)

    /** CoinbaseSmartWallet.execute(target, value, data) */
    override fun encodeExecute(to: String, value: BigInteger, data: ByteArray): ByteArray =
        Abi.encodeCall(
            "execute(address,uint256,bytes)",
            Abi.Arg.Address(to), Abi.Arg.Uint(value), Abi.Arg.Bytes(data),
        )

    /** CoinbaseSmartWallet.executeBatch(Call[]) — several calls, one signature, all-or-nothing. */
    override fun encodeExecuteBatch(calls: List<Call>): ByteArray =
        Abi.encodeCall(
            "executeBatch((address,uint256,bytes)[])",
            Abi.Arg.TupleArray(calls.map { Abi.Arg.Tuple(Abi.Arg.Address(it.target), Abi.Arg.Uint(it.value), Abi.Arg.Bytes(it.data)) }),
        )

    /**
     * Gas estimation needs a signature with the same size and the same verification path as the real
     * one. We simply sign a dummy challenge with the real passkey: it's well-formed but won't match the
     * userOpHash (so it can't be abused), and costs at least as much gas to verify.
     */
    override fun stubSignature(): ByteArray =
        wrapSignature(ownerIndex, encodeWebAuthn(passkey.sign(ByteArray(32) { 0x5A })))

    override fun signUserOperation(userOp: UserOperation): ByteArray {
        val userOpHash = UserOperationHasher.hash(userOp, entryPoint, chainId)
        return wrapSignature(ownerIndex, encodeWebAuthn(passkey.sign(userOpHash)))
    }

    companion object {
        /** abi.encode(bytes32 x, bytes32 y) — how Coinbase Smart Wallet stores a passkey owner (64 bytes). */
        fun passkeyOwner(x: ByteArray, y: ByteArray): ByteArray = x + y

        /** abi.encode(address) — an address owner (32 bytes), e.g. the recovery module. */
        fun addressOwner(address: String): ByteArray = Abi.addressWord(address)

        fun createAccountCall(owners: List<ByteArray>, nonce: BigInteger): ByteArray =
            Abi.encodeCall("createAccount(bytes[],uint256)", Abi.Arg.BytesArray(owners), Abi.Arg.Uint(nonce))

        /** Counterfactual address: known before the contract exists (CREATE2), so it can already receive funds. */
        suspend fun computeAddress(rpc: EthereumRpc, factory: String, owners: List<ByteArray>, nonce: BigInteger): String {
            val res = rpc.call(
                factory,
                Abi.encodeCall("getAddress(bytes[],uint256)", Abi.Arg.BytesArray(owners), Abi.Arg.Uint(nonce)),
            )
            return Abi.decodeAddress(res)
        }

        /** abi.encode(SignatureWrapper{ownerIndex, signatureData}) */
        fun wrapSignature(ownerIndex: Int, signatureData: ByteArray): ByteArray =
            Abi.encodeArgs(Abi.Arg.Tuple(Abi.Arg.Uint(ownerIndex.toLong()), Abi.Arg.Bytes(signatureData)))

        /** abi.encode(WebAuthnAuth{authenticatorData, clientDataJSON, challengeIndex, typeIndex, r, s}) */
        fun encodeWebAuthn(sig: WebAuthnSignature): ByteArray =
            Abi.encodeArgs(
                Abi.Arg.Tuple(
                    Abi.Arg.Bytes(sig.authenticatorData),
                    Abi.Arg.Bytes(sig.clientDataJSON.toByteArray(Charsets.UTF_8)),
                    Abi.Arg.Uint(sig.challengeIndex.toLong()),
                    Abi.Arg.Uint(sig.typeIndex.toLong()),
                    Abi.Arg.Uint(sig.r),
                    Abi.Arg.Uint(sig.s),
                )
            )

        /** All current owners as (index, ownerBytes). Removed owners are skipped. */
        suspend fun owners(rpc: EthereumRpc, wallet: String): List<Pair<Int, ByteArray>> {
            val next = Abi.decodeUint(rpc.call(wallet, Abi.encodeCall("nextOwnerIndex()"))).toInt()
            return (0 until next).mapNotNull { i ->
                val bytes = Abi.decodeBytes(rpc.call(wallet, Abi.encodeCall("ownerAtIndex(uint256)", Abi.Arg.Uint(i.toLong()))))
                if (bytes.isEmpty()) null else i to bytes
            }
        }

        suspend fun findOwnerIndex(rpc: EthereumRpc, wallet: String, owner: ByteArray): Int? =
            owners(rpc, wallet).firstOrNull { it.second.contentEquals(owner) }?.first
    }
}
