package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "FinMoneyBootReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val action = intent.action ?: return
        Log.i(TAG, "Broadcast received: $action. Ensuring BankNotificationListenerService is active and bound.")

        try {
            BankNotificationListenerService.requestRebindService(context)
            BankSyncManager.ensureServiceBound(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error rebinding BankNotificationListenerService on $action: ${e.message}", e)
        }
    }
}
