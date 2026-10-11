package com.example.swisswallet

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.core.contract.WalletContract
import com.example.swisswallet.data.aa.ErrorMapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Invisible activity started by Swissquote ("Generate my wallet").
 * Protected by a signature permission: only apps signed with our key can call it.
 *
 * It creates the passkey in the Keystore (first time only), computes the counterfactual
 * Coinbase Smart Wallet address (owners = [passkey, recovery module]) and returns it.
 * Swissquote also sends its guardian address: it becomes guardian #1 automatically when the
 * user activates recovery. Nothing is deployed on-chain yet: the account is deployed
 * automatically with its first UserOperation.
 */
class ProvisionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as SwissWalletApplication).container

        lifecycleScope.launch {
            try {
                val swissquoteGuardian = intent.getStringExtra(WalletContract.EXTRA_SWISSQUOTE_GUARDIAN)
                val account = container.walletRepository.provision(swissquoteGuardian)
                setResult(
                    Activity.RESULT_OK,
                    Intent().putExtra(WalletContract.EXTRA_SMART_ACCOUNT, account.smartAccountAddress),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setResult(
                    Activity.RESULT_CANCELED,
                    Intent().putExtra(WalletContract.EXTRA_ERROR, "Couldn't generate the wallet: " + ErrorMapper.humanize(e)),
                )
            }
            finish()
        }
    }
}
