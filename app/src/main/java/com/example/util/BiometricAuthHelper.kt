package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Enterprise-grade biometric authentication helper using androidx.biometric.
 * Supports Fingerprint, Face Unlock, and device PIN/Pattern fallback for banking-grade security.
 */
object BiometricAuthHelper {

    private const val TAG = "BiometricAuthHelper"
    private const val PREFS_NAME = "finmoney_biometric_prefs"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_lock_enabled"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Checks if biometric hardware and capabilities are available on the device.
     */
    fun canAuthenticate(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        } else {
            BIOMETRIC_STRONG
        }
        val result = biometricManager.canAuthenticate(authenticators)
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Checks if user has enabled biometric lock in FinMoney preferences.
     * Defaults to true if hardware is capable and enrolled.
     */
    fun isBiometricEnabled(context: Context): Boolean {
        val prefs = getPrefs(context)
        if (!prefs.contains(KEY_BIOMETRIC_ENABLED)) {
            // Default to enabled if hardware is available
            val capable = canAuthenticate(context)
            prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, capable).apply()
            return capable
        }
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }

    /**
     * Updates biometric lock preference.
     */
    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    /**
     * Shows system BiometricPrompt dialog for unlocking financial data.
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "Unlock FinMoney",
        subtitle: String = "Confirm your fingerprint or face unlock to access financial data",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                Log.d(TAG, "Biometric authentication succeeded")
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Log.w(TAG, "Biometric authentication error ($errorCode): $errString")
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                ) {
                    onError("Authentication cancelled by user.")
                } else {
                    onError(errString.toString())
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Log.w(TAG, "Biometric authentication failed (unrecognized credential)")
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)

        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            promptInfoBuilder.setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
        } else {
            promptInfoBuilder.setAllowedAuthenticators(BIOMETRIC_STRONG)
            promptInfoBuilder.setNegativeButtonText("Use App PIN")
        }

        try {
            prompt.authenticate(promptInfoBuilder.build())
        } catch (e: Exception) {
            Log.e(TAG, "BiometricPrompt launch failed: ${e.message}", e)
            onError(e.localizedMessage ?: "Could not launch biometric unlock.")
        }
    }
}
