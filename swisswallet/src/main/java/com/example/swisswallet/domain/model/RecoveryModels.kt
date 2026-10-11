package com.example.swisswallet.domain.model

import com.example.core.recovery.GuardianApproval
import com.example.core.recovery.RecoveryRequest
import org.json.JSONObject
import java.util.Base64

/** Who a guardian is (only a label for the user — on-chain a guardian is just an address). */
enum class GuardianKind(val label: String) {
    /** Added automatically when Swissquote generated the wallet. Approves after an identity check. */
    SWISSQUOTE("Swissquote · automatic"),
    /** Another phone / tablet of mine running SwissWallet in "guardian" mode. */
    MY_DEVICE("My other device"),
    /** A friend or family member with SwissWallet in "guardian" mode. */
    CLOSE_ONE("Close one"),
    /** Key simulated on this phone so the demo works with a single emulator. */
    DEMO("Simulated (demo)"),
}

data class Guardian(
    val address: String,
    val name: String,
    val kind: GuardianKind,
)

/** "I can be your guardian, here is my address" — shared by message / QR. Prefix `SWGRD1.` */
data class GuardianInvite(val address: String, val name: String, val kind: GuardianKind) {
    fun encode(): String {
        val json = JSONObject().put("a", address).put("n", name).put("k", kind.name)
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(json.toString().toByteArray())
    }

    fun toGuardian() = Guardian(address, name, kind)

    companion object {
        private const val PREFIX = "SWGRD1."
        fun decode(code: String): GuardianInvite {
            val c = code.trim()
            require(c.startsWith(PREFIX)) { "Not a SwissWallet guardian code" }
            val o = JSONObject(String(Base64.getUrlDecoder().decode(c.removePrefix(PREFIX))))
            return GuardianInvite(o.getString("a"), o.getString("n"), runCatching { GuardianKind.valueOf(o.getString("k")) }.getOrDefault(GuardianKind.CLOSE_ONE))
        }
    }
}

/** A recovery waiting for its safety delay (visible to the legitimate owner, who can cancel it). */
data class PendingRecovery(
    val newKeyFingerprint: String,
    val executeAfterMillis: Long,
)

/** Recovery protection of the current wallet, read from the chain. */
data class ProtectionStatus(
    val active: Boolean,
    val threshold: Int,
    val delaySeconds: Long,
    /** On-chain guardians, with local names when we know them. */
    val guardians: List<Guardian>,
    val pending: PendingRecovery?,
    val walletDeployed: Boolean,
)

/** What a new device learns about a wallet before trying to recover it. */
data class RecoveryCandidate(
    val wallet: String,
    val guardians: List<Guardian>,
    val threshold: Int,
    val delaySeconds: Long,
    val pending: PendingRecovery?,
)

enum class RecoveryPhase {
    /** Asking guardians to approve. */
    COLLECTING,
    /** 3 approvals sent on-chain, safety delay running (the old phone can still cancel). */
    WAITING,
    /** Delay over: the new passkey can be added. */
    READY,
}

/** A recovery in progress on THIS (new) device. Persisted so the app can be closed meanwhile. */
data class RecoverySession(
    val request: RecoveryRequest,
    val passkeyId: String,
    val guardians: List<Guardian>,
    val threshold: Int,
    val delaySeconds: Long,
    val approvals: List<GuardianApproval>,
    /** Set once initiateRecovery() is mined. */
    val executeAfterMillis: Long? = null,
    val swissquoteGuardian: String? = null,
) {
    val approvedBy: Set<String> get() = approvals.map { it.guardian.lowercase() }.toSet()
    val hasEnoughApprovals: Boolean get() = approvedBy.size >= threshold

    fun phase(now: Long): RecoveryPhase = when {
        executeAfterMillis == null -> RecoveryPhase.COLLECTING
        now < executeAfterMillis -> RecoveryPhase.WAITING
        else -> RecoveryPhase.READY
    }
}

/** What a guardian sees before approving someone's recovery. */
data class GuardianReview(
    val request: RecoveryRequest,
    /** true / false = checked on-chain; null = couldn't check (offline). */
    val iAmGuardian: Boolean?,
)
