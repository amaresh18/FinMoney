package com.example.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.service.BankSyncManager

object NotificationPermissionsHelper {

    fun hasPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasNotificationListenerAccess(context: Context): Boolean =
        BankSyncManager.isNotificationAccessGranted(context)

    /** Ready to auto-detect bank/UPI spends from alerts and notify you to approve. */
    fun isAutoExpenseTrackingReady(context: Context): Boolean =
        hasPostNotifications(context) && hasNotificationListenerAccess(context)

    fun missingStepLabel(context: Context): String {
        val needsPost = !hasPostNotifications(context)
        val needsListener = !hasNotificationListenerAccess(context)
        return when {
            needsPost && needsListener -> "Allow notifications + read bank alerts"
            needsPost -> "Allow notifications for spend alerts"
            needsListener -> "Allow notification access to read bank & UPI alerts"
            else -> ""
        }
    }
}
