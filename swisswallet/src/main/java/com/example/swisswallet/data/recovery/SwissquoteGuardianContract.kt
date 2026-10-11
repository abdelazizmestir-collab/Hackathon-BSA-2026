package com.example.swisswallet.data.recovery

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.example.core.contract.WalletContract

sealed interface SwissquoteApprovalResult {
    /** `SWSIG1.` code signed by Swissquote's guardian key. */
    data class Approved(val code: String) : SwissquoteApprovalResult
    data class Refused(val message: String) : SwissquoteApprovalResult
    data object Cancelled : SwissquoteApprovalResult
}

/**
 * Opens Swissquote's "Approve wallet recovery" screen (identity check) with our `SWREC1.` request.
 * Protected by a signature permission: only our own apps can talk to Swissquote's guardian.
 */
class SwissquoteGuardianContract : ActivityResultContract<String, SwissquoteApprovalResult>() {

    override fun createIntent(context: Context, input: String): Intent =
        Intent(WalletContract.ACTION_APPROVE_RECOVERY)
            .setPackage(WalletContract.SWISSQUOTE_PACKAGE)
            .putExtra(WalletContract.EXTRA_RECOVERY_REQUEST, input)

    override fun parseResult(resultCode: Int, intent: Intent?): SwissquoteApprovalResult {
        val code = intent?.getStringExtra(WalletContract.EXTRA_GUARDIAN_APPROVAL)
        val error = intent?.getStringExtra(WalletContract.EXTRA_ERROR)
        return when {
            resultCode == Activity.RESULT_OK && code != null -> SwissquoteApprovalResult.Approved(code)
            error != null -> SwissquoteApprovalResult.Refused(error)
            else -> SwissquoteApprovalResult.Cancelled
        }
    }
}
