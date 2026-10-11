package com.example.core.recovery

import org.json.JSONObject
import org.web3j.crypto.Hash
import org.web3j.crypto.Keys
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
import java.math.BigInteger
import java.util.Base64

/**
 * Social-recovery policy of the demo: 4 guardians, 3 must approve, 30 s safety delay
 * (in production this would be 48 h).
 */
object RecoveryPolicy {
    const val GUARDIAN_COUNT = 4
    const val THRESHOLD = 3
    const val DELAY_SECONDS = 30L

    /** `type(uint256).max` in the contract = "don't revoke any old key". */
    val NO_REVOKE: BigInteger = BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE)

    /** Short, human-comparable fingerprint of a passkey, e.g. "3F2A-91C0". */
    fun fingerprint(x: ByteArray, y: ByteArray): String =
        Numeric.toHexStringNoPrefix(Hash.sha3(x + y)).take(8).uppercase().chunked(4).joinToString("-")

    /** keccak256 of the type string — identical to `RECOVERY_TYPEHASH` in SwissRecoveryModule.sol. */
    val TYPEHASH: ByteArray = Hash.sha3(
        "SwissRecovery(address wallet,bytes32 x,bytes32 y,uint256 revokeIndex,uint256 nonce,uint256 chainId,address module)"
            .toByteArray(Charsets.US_ASCII)
    )
}

/**
 * "Please let this NEW passkey (x, y) control my wallet."
 *
 * Every guardian signs [hash] — exactly what `SwissRecoveryModule.recoveryHash()` computes on-chain.
 * The hash includes the wallet, the new key, the module's nonce (so a signature can be used only once),
 * the chain id and the module address (so it can't be replayed elsewhere).
 */
data class RecoveryRequest(
    val wallet: String,
    val x: ByteArray,
    val y: ByteArray,
    val revokeIndex: BigInteger,
    val nonce: BigInteger,
    val chainId: Long,
    val module: String,
    /** Human label shown to guardians, e.g. "New phone · Pixel 10a". */
    val deviceLabel: String,
) {
    init {
        require(x.size == 32 && y.size == 32) { "Passkey coordinates must be 32 bytes" }
    }

    fun hash(): ByteArray = Hash.sha3(
        RecoveryPolicy.TYPEHASH +
            addressWord(wallet) + x + y +
            Numeric.toBytesPadded(revokeIndex, 32) +
            Numeric.toBytesPadded(nonce, 32) +
            Numeric.toBytesPadded(BigInteger.valueOf(chainId), 32) +
            addressWord(module)
    )

    /** Short, human-comparable fingerprint of the new key (guardians can read it to the user on the phone). */
    fun keyFingerprint(): String = RecoveryPolicy.fingerprint(x, y)

    /** Text code that can be sent by message / QR to a guardian. */
    fun encode(): String {
        val json = JSONObject()
            .put("w", wallet).put("x", Numeric.toHexString(x)).put("y", Numeric.toHexString(y))
            .put("r", revokeIndex.toString()).put("n", nonce.toString()).put("c", chainId)
            .put("m", module).put("d", deviceLabel)
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(json.toString().toByteArray())
    }

    override fun equals(other: Any?): Boolean =
        other is RecoveryRequest && wallet.equals(other.wallet, true) && x.contentEquals(other.x) &&
            y.contentEquals(other.y) && revokeIndex == other.revokeIndex && nonce == other.nonce &&
            chainId == other.chainId && module.equals(other.module, true)

    override fun hashCode(): Int = wallet.lowercase().hashCode() * 31 + x.contentHashCode()

    companion object {
        private const val PREFIX = "SWREC1."

        fun decode(code: String): RecoveryRequest {
            val body = code.trim().removePrefix(PREFIX)
            require(code.trim().startsWith(PREFIX)) { "Not a SwissWallet recovery request" }
            val o = JSONObject(String(Base64.getUrlDecoder().decode(body)))
            return RecoveryRequest(
                wallet = o.getString("w"),
                x = Numeric.hexStringToByteArray(o.getString("x")),
                y = Numeric.hexStringToByteArray(o.getString("y")),
                revokeIndex = BigInteger(o.getString("r")),
                nonce = BigInteger(o.getString("n")),
                chainId = o.getLong("c"),
                module = o.getString("m"),
                deviceLabel = o.optString("d", "New device"),
            )
        }

        private fun addressWord(address: String): ByteArray {
            val raw = Numeric.hexStringToByteArray(address)
            require(raw.size == 20) { "Invalid address $address" }
            return ByteArray(12) + raw
        }
    }
}

/** A guardian's signature (EIP-191 personal_sign of [RecoveryRequest.hash]). */
data class GuardianApproval(
    val guardian: String,
    val signature: ByteArray,
) {
    /** True if [signature] was really produced by [guardian] for [request]. */
    fun isValidFor(request: RecoveryRequest): Boolean = runCatching {
        recoverSigner(request.hash(), signature).equals(guardian, ignoreCase = true)
    }.getOrDefault(false)

    fun encode(): String {
        val json = JSONObject().put("g", guardian).put("s", Numeric.toHexString(signature))
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(json.toString().toByteArray())
    }

    override fun equals(other: Any?): Boolean =
        other is GuardianApproval && guardian.equals(other.guardian, true) && signature.contentEquals(other.signature)

    override fun hashCode(): Int = guardian.lowercase().hashCode()

    companion object {
        private const val PREFIX = "SWSIG1."

        fun decode(code: String): GuardianApproval {
            require(code.trim().startsWith(PREFIX)) { "Not a SwissWallet guardian approval" }
            val o = JSONObject(String(Base64.getUrlDecoder().decode(code.trim().removePrefix(PREFIX))))
            return GuardianApproval(o.getString("g"), Numeric.hexStringToByteArray(o.getString("s")))
        }

        /** EIP-191: who signed "\x19Ethereum Signed Message:\n32" + hash ? */
        fun recoverSigner(hash: ByteArray, signature: ByteArray): String {
            require(signature.size == 65) { "Signature must be 65 bytes" }
            val v = signature[64].let { if (it < 27) (it + 27).toByte() else it }
            val data = Sign.SignatureData(v, signature.copyOfRange(0, 32), signature.copyOfRange(32, 64))
            val pub = Sign.signedPrefixedMessageToKey(hash, data)
            return Keys.toChecksumAddress(Keys.getAddress(pub))
        }
    }
}
