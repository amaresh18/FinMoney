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
import com.example.R

object PhoneNotificationHelper {
    private const val CHANNEL_ID = "finmoney_reminders"
    private const val CHANNEL_NAME = "Financial Reminders"

    fun showReminderNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Due date, mutual agreements, and payment alerts for FinMoney"
                enableLights(true)
                lightColor = Color.GREEN
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_finmoney)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    fun cancelNotification(context: Context, notificationId: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(notificationId)
    }

    fun cancelForLoan(context: Context, loanId: Long? = null, cloudId: String = "") {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (loanId != null && loanId > 0) {
            notificationManager.cancel(Math.abs((loanId % 10000).toInt()))
        }
        if (cloudId.isNotBlank()) {
            notificationManager.cancel(Math.abs((cloudId.hashCode() % 10000)))
        }
    }

    fun cancelAll(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancelAll()
    }

    fun getNotificationId(loanId: Long? = null, cloudId: String = "", title: String = ""): Int {
        if (loanId != null && loanId > 0) return Math.abs((loanId % 10000).toInt())
        if (cloudId.isNotBlank()) return Math.abs((cloudId.hashCode() % 10000))
        return Math.abs((title.hashCode() % 10000))
    }

    fun showNotification(
        context: Context,
        title: String,
        message: String,
        loanId: Long? = null,
        actionType: String = "",
        cloudId: String = ""
    ) {
        val id = getNotificationId(loanId = loanId, cloudId = cloudId, title = title)
        showReminderNotification(context, id, title, message)
    }
}
