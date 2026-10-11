package com.example.swisswallet.security

import android.app.Activity
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal

enum class AuthOutcome { Authenticated, NoScreenLock, Cancelled }

/**
 * Asks the phone to authenticate its user (fingerprint, face or screen lock) with the system
 * prompt. This is an app-level gate: the wallet key itself is not bound to it (see
 * AndroidPasskeyVault), so it protects against a person holding the phone, not against modified
 * software.
 */
object DeviceAuth {

    /** False on a phone with no screen lock (a fresh emulator) or before Android 11. */
    fun isAvailable(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val keyguard = activity.getSystemService(KeyguardManager::class.java) ?: return false
        return keyguard.isDeviceSecure
    }

    fun confirm(activity: Activity, title: String, subtitle: String, onResult: (AuthOutcome) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && isAvailable(activity)) {
            val prompt = BiometricPrompt.Builder(activity)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()
            prompt.authenticate(
                CancellationSignal(),
                activity.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onResult(AuthOutcome.Authenticated)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        onResult(AuthOutcome.Cancelled)
                    }
                },
            )
        } else {
            onResult(AuthOutcome.NoScreenLock)
        }
    }
}
