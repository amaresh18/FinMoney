package com.example.util

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest
import com.google.android.gms.auth.api.identity.Identity

data class DetectedSim(
    val slotIndex: Int,
    val carrierName: String,
    val displayName: String,
    val rawNumber: String,
    val cleanDigits: String,
    val formattedDisplay: String
)

object SimDetectionHelper {
    private const val TAG = "SimDetectionHelper"

    fun hasPhonePermission(context: Context): Boolean {
        val hasState = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val hasNumbers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED
        } else true
        return hasState && hasNumbers
    }

    fun getDetectedSims(context: Context): List<DetectedSim> {
        val detected = mutableListOf<DetectedSim>()

        try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            if (subscriptionManager != null && hasPhonePermission(context)) {
                val activeList: List<SubscriptionInfo>? = subscriptionManager.activeSubscriptionInfoList
                if (!activeList.isNullOrEmpty()) {
                    for (info in activeList) {
                        var number = ""
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            try {
                                number = subscriptionManager.getPhoneNumber(info.subscriptionId)
                            } catch (e: Exception) {
                                Log.w(TAG, "getPhoneNumber API 33 failed: ${e.message}")
                            }
                        }

                        if (number.isBlank()) {
                            @Suppress("DEPRECATION")
                            number = info.number ?: ""
                        }

                        val digits = number.filter { it.isDigit() }.let {
                            if (it.length > 10 && it.startsWith("91")) it.drop(2) else it
                        }

                        val carrier = info.carrierName?.toString()?.takeIf { it.isNotBlank() } ?: "SIM ${info.simSlotIndex + 1}"
                        val display = info.displayName?.toString()?.takeIf { it.isNotBlank() } ?: carrier
                        val formatted = if (digits.length >= 10) {
                            "+91 ${digits.takeLast(10).chunked(5).joinToString(" ")}"
                        } else if (number.isNotBlank()) number else "SIM ${info.simSlotIndex + 1}"

                        detected.add(
                            DetectedSim(
                                slotIndex = info.simSlotIndex,
                                carrierName = carrier,
                                displayName = display,
                                rawNumber = number,
                                cleanDigits = digits.takeLast(10),
                                formattedDisplay = formatted
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "SubscriptionManager query failed: ${e.message}")
        }

        // Fallback: TelephonyManager line1Number
        if (detected.none { it.cleanDigits.length >= 10 }) {
            try {
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                if (telephonyManager != null && hasPhonePermission(context)) {
                    @Suppress("DEPRECATION")
                    val line1 = telephonyManager.line1Number ?: ""
                    val digits = line1.filter { it.isDigit() }.let {
                        if (it.length > 10 && it.startsWith("91")) it.drop(2) else it
                    }
                    if (digits.length >= 10) {
                        val carrier = telephonyManager.simOperatorName.takeIf { it.isNotBlank() } ?: "SIM 1"
                        detected.removeAll { it.cleanDigits.length < 10 }
                        detected.add(
                            DetectedSim(
                                slotIndex = 0,
                                carrierName = carrier,
                                displayName = carrier,
                                rawNumber = line1,
                                cleanDigits = digits.takeLast(10),
                                formattedDisplay = "+91 ${digits.takeLast(10).chunked(5).joinToString(" ")}"
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "TelephonyManager line1Number fallback failed: ${e.message}")
            }
        }

        return detected
    }

    fun requestPhoneNumberHint(
        activity: Activity,
        onSuccess: (PendingIntent) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        try {
            val request = GetPhoneNumberHintIntentRequest.builder().build()
            Identity.getSignInClient(activity)
                .getPhoneNumberHintIntent(request)
                .addOnSuccessListener { pendingIntent ->
                    onSuccess(pendingIntent)
                }
                .addOnFailureListener { exception ->
                    onFailure(exception)
                }
        } catch (e: Exception) {
            onFailure(e)
        }
    }

    fun extractPhoneNumberFromHintIntent(activity: Activity, data: Intent?): String? {
        if (data == null) return null
        return try {
            val rawPhone = Identity.getSignInClient(activity).getPhoneNumberFromIntent(data)
            val digits = rawPhone.filter { it.isDigit() }
            if (digits.length > 10 && digits.startsWith("91")) {
                digits.drop(2)
            } else if (digits.length >= 10) {
                digits.takeLast(10)
            } else {
                digits
            }
        } catch (e: Exception) {
            Log.w(TAG, "extractPhoneNumberFromHintIntent failed: ${e.message}")
            null
        }
    }
}
