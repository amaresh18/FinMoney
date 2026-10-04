package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity

object PhoneNotificationHelper {
    const val CHANNEL_ID = "finmoney_p2p_alerts"
    const val CHANNEL_NAME = "FinMoney Approvals & Transactions"
    const val CHANNEL_DESC = "Notifications for peer loan approvals, payments, settlements, and reminders"

    fun initChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableLights(true)
                lightColor = Color.parseColor("#00D09C") // INDmoney Teal
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun showNotification(
        context: Context,
        title: String,
        message: String,
        loanId: Long? = null,
        actionType: String = "SYSTEM",
        notificationId: Int = (System.currentTimeMillis() % 100000).toInt()
    ) {
        initChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAVIGATE_TO", if (actionType == "APPROVAL_REQUEST") "APPROVALS" else "LENT_BORROWED")
            if (loanId != null) putExtra("LOAN_ID", loanId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("FinMoney • $title")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSubText("Peer Khaata")
            .setColor(Color.parseColor("#00D09C"))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        try {
            notificationManager?.notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS permission not granted yet
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
