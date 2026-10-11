package com.example.swissquote.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.example.core.contract.ProvisionResult
import com.example.core.contract.WalletContract
import com.example.swissquote.domain.repository.WalletAppLauncher

/** Opens the external SwissWallet APK (the "redirect" from the Wallet tab). */
class AndroidWalletAppLauncher(private val context: Context) : WalletAppLauncher {

    override fun isWalletInstalled(): Boolean =
        context.packageManager.getLaunchIntentForPackage(WalletContract.WALLET_PACKAGE) != null

    override fun openWallet(): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(WalletContract.WALLET_PACKAGE)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }
}

/** What Swissquote sends when generating the wallet. */
data class ProvisionInput(
    val clientName: String,
    /** Swissquote's guardian address → guardian #1 of the wallet's social recovery, automatically. */
    val swissquoteGuardian: String,
)

/**
 * ActivityResult contract: asks SwissWallet to generate the wallet and give us back the
 * smart-account address. SwissWallet's activity is transparent, so the user stays on our
 * "Generating your wallet…" screen.
 */
class ProvisionWalletContract : ActivityResultContract<ProvisionInput, ProvisionResult>() {

    override fun createIntent(context: Context, input: ProvisionInput): Intent =
        Intent(WalletContract.ACTION_PROVISION)
            .setPackage(WalletContract.WALLET_PACKAGE)
            .putExtra(WalletContract.EXTRA_CLIENT_NAME, input.clientName)
            .putExtra(WalletContract.EXTRA_SWISSQUOTE_GUARDIAN, input.swissquoteGuardian)

    override fun parseResult(resultCode: Int, intent: Intent?): ProvisionResult {
        val address = intent?.getStringExtra(WalletContract.EXTRA_SMART_ACCOUNT)
        val error = intent?.getStringExtra(WalletContract.EXTRA_ERROR)
        return when {
            resultCode == Activity.RESULT_OK && address != null -> ProvisionResult.Success(address)
            error != null -> ProvisionResult.Failure(error)
            else -> ProvisionResult.Cancelled
        }
    }
}
