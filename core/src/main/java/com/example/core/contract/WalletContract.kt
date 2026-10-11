package com.example.core.contract

/**
 * Inter-app contract between Swissquote and SwissWallet.
 *
 * Swissquote → SwissWallet: `startActivityForResult(ACTION_PROVISION)` on an invisible,
 * signature-permission-protected activity. SwissWallet creates (or reuses) the passkey in the
 * Android Keystore, computes the counterfactual ERC-4337 smart-account address and returns it.
 */
object WalletContract {
    const val WALLET_PACKAGE = "com.example.swisswallet"
    const val SWISSQUOTE_PACKAGE = "com.example.swissquote"

    const val ACTION_PROVISION = "com.example.swisswallet.action.PROVISION_WALLET"
    /** Declared (signature level) in both manifests so install order doesn't matter. */
    const val PERMISSION_PROVISION = "com.example.swisswallet.permission.PROVISION_WALLET"

    const val EXTRA_CLIENT_NAME = "client_name"
    const val EXTRA_SMART_ACCOUNT = "smart_account_address"
    const val EXTRA_ERROR = "error_message"
    /** Swissquote's guardian address, sent at provisioning → Swissquote becomes guardian #1 automatically. */
    const val EXTRA_SWISSQUOTE_GUARDIAN = "swissquote_guardian_address"

    // --- SwissWallet → Swissquote: "please approve this recovery as my guardian" -------------
    const val ACTION_APPROVE_RECOVERY = "com.example.swissquote.action.APPROVE_RECOVERY"
    /** Signature-level: only our own apps can ask Swissquote to sign a recovery. */
    const val PERMISSION_GUARDIAN = "com.example.swissquote.permission.GUARDIAN"
    /** The recovery request, encoded with [com.example.core.recovery.RecoveryRequest.encode]. */
    const val EXTRA_RECOVERY_REQUEST = "recovery_request"
    /** The guardian approval code ([com.example.core.recovery.GuardianApproval.encode]). */
    const val EXTRA_GUARDIAN_APPROVAL = "guardian_approval"
}

sealed interface ProvisionResult {
    data class Success(val smartAccountAddress: String) : ProvisionResult
    data class Failure(val message: String) : ProvisionResult
    data object Cancelled : ProvisionResult
}
