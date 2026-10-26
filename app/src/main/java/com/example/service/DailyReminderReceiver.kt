package com.example.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.model.AppNotification
import com.example.util.PhoneNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class DailyReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "DailyReminderReceiver triggered with action: $action")

        when (action) {
            ACTION_TRIGGER_DAILY_REVIEW, ACTION_TEST_NOTIFICATION -> {
                val topicType = intent.getStringExtra(EXTRA_TOPIC_TYPE) ?: TOPIC_DAILY_REVIEW
                triggerCreativeNotification(context, topicType)
                if (action == ACTION_TRIGGER_DAILY_REVIEW) {
                    scheduleNext1030PmAlarm(context)
                }
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                scheduleNext1030PmAlarm(context)
            }
            ACTION_SCHEDULE -> {
                scheduleNext1030PmAlarm(context)
            }
        }
    }

    companion object {
        private const val TAG = "DailyReminderReceiver"
        const val ACTION_TRIGGER_DAILY_REVIEW = "com.example.finmoney.ACTION_TRIGGER_DAILY_REVIEW"
        const val ACTION_TEST_NOTIFICATION = "com.example.finmoney.ACTION_TEST_NOTIFICATION"
        const val ACTION_SCHEDULE = "com.example.finmoney.ACTION_SCHEDULE_DAILY"
        const val EXTRA_TOPIC_TYPE = "extra_topic_type"

        const val TOPIC_DAILY_REVIEW = "daily_review"
        const val TOPIC_FINANCIAL_HEALTH = "financial_health"
        const val TOPIC_BUDGET_GUARDIAN = "budget_guardian"
        const val TOPIC_KHAATA_SYNC = "khaata_sync"
        const val TOPIC_RANDOM_CREATIVE = "random_creative"

        private val CREATIVE_HEALTH_TIPS = listOf(
            Pair("🌿 Take Care of Your Financial Health", "Just like physical health, a quick 1-minute daily expense check keeps your finances stress-free and thriving!"),
            Pair("💡 Smart Wealth Habit", "Small daily savings compound into big financial freedom! Have you reviewed your flexible cash balance today?"),
            Pair("📊 Budget Health Shield", "Stay ahead of your monthly EMIs and bill dues. Check your safe daily spending limit inside FinMoney."),
            Pair("🤝 Peer Khaata Balance", "Keep your borrowing & lending records synchronized. Settle up dues with contacts seamlessly over UPI."),
            Pair("💵 Income & Cash Flow Guard", "FinMoney auto-tracks your salary credits and recurring expenses so you never face end-of-month surprises.")
        )

        fun triggerCreativeNotification(context: Context, topicType: String = TOPIC_DAILY_REVIEW) {
            val (title, message) = when (topicType) {
                TOPIC_DAILY_REVIEW -> Pair(
                    "🌙 Nightly Expense & Income Review",
                    "It's 10:30 PM! Take 30 seconds to review your expenses and income for today. Keep your FinMoney ledger 100% updated."
                )
                TOPIC_FINANCIAL_HEALTH -> Pair(
                    "❤️ Take Care of Your Financial Health",
                    "A quick review of today's transactions keeps your budget healthy and stress-free. Tap to open FinMoney!"
                )
                TOPIC_BUDGET_GUARDIAN -> Pair(
                    "📊 Monthly Budget Guardian",
                    "Check your flexible cash and remaining monthly dues. FinMoney helps you stay within your safe daily budget."
                )
                TOPIC_KHAATA_SYNC -> Pair(
                    "🤝 Khaata Peer Ledger Check",
                    "Keep borrowing & lending transparent. Review contact settlements and record recent loans with 1-tap."
                )
                else -> CREATIVE_HEALTH_TIPS.random()
            }

            val notifId = (System.currentTimeMillis() % 10000).toInt()

            // 1. Fire System Status Bar Notification
            PhoneNotificationHelper.showReminderNotification(
                context = context,
                notificationId = notifId,
                title = title,
                message = message
            )

            // 2. Insert into Room Database so it appears in the in-app notification drawer
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val dao = db.finMoneyDao()
                    val activeUser = dao.getUserProfileDirect()?.userId ?: "user_default"
                    dao.insertNotification(
                        AppNotification(
                            userId = activeUser,
                            title = title,
                            message = message,
                            type = "REMINDER",
                            timestamp = System.currentTimeMillis(),
                            isRead = false
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error inserting in-app notification: ${e.message}")
                }
            }
        }

        fun scheduleNext1030PmAlarm(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context, DailyReminderReceiver::class.java).apply {
                    action = ACTION_TRIGGER_DAILY_REVIEW
                    putExtra(EXTRA_TOPIC_TYPE, TOPIC_DAILY_REVIEW)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    103022,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val now = Calendar.getInstance()
                val target = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 22) // 10 PM
                    set(Calendar.MINUTE, 30)      // 30 mins
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    if (before(now)) {
                        add(Calendar.DAY_OF_YEAR, 1) // Schedule for 10:30 PM tomorrow if 10:30 PM passed today
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        target.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        target.timeInMillis,
                        pendingIntent
                    )
                }
                Log.i(TAG, "Daily 10:30 PM reminder scheduled for: ${target.time}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule 10:30 PM daily alarm: ${e.message}", e)
            }
        }

        fun cancelDailyAlarm(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context, DailyReminderReceiver::class.java).apply {
                    action = ACTION_TRIGGER_DAILY_REVIEW
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    103022,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (pendingIntent != null) {
                    alarmManager.cancel(pendingIntent)
                    pendingIntent.cancel()
                }
                Log.i(TAG, "Daily 10:30 PM reminder cancelled.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cancel daily alarm: ${e.message}")
            }
        }
    }
}
