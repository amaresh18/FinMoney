package com.example.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver enabling users to approve or dismiss financial transactions directly
 * from the Android system notification drawer ("Approve from IN").
 */
class BankExpenseActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_APPROVE_EXPENSE = "com.example.finmoney.ACTION_APPROVE_EXPENSE"
        const val ACTION_REJECT_EXPENSE = "com.example.finmoney.ACTION_REJECT_EXPENSE"
        const val EXTRA_TX_ID = "extra_tx_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        private const val TAG = "BankExpenseReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        val txId = intent.getLongExtra(EXTRA_TX_ID, -1L)
        val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notifId > 0) {
            notificationManager.cancel(notifId)
        }

        if (txId <= 0L) {
            Log.w(TAG, "Invalid transaction ID ($txId) in action $action")
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_APPROVE_EXPENSE -> {
                        Log.d(TAG, "Approving transaction $txId from notification")
                        val resultMsg = BankSyncManager.approveTransactionAndCreateExpense(context, txId)
                        Log.d(TAG, "Approval result: $resultMsg")
                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(context, resultMsg, Toast.LENGTH_LONG).show()
                        }
                    }
                    ACTION_REJECT_EXPENSE -> {
                        Log.d(TAG, "Rejecting transaction $txId from notification")
                        BankSyncManager.rejectTransaction(context, txId)
                        Log.d(TAG, "Transaction rejected")
                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(context, "Transaction dismissed from IN", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling notification action $action: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
