package com.example.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.data.bank.BankTransactionParser

class BankNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "BankNotifListener"

        private var instance: BankNotificationListenerService? = null

        fun getInstance(): BankNotificationListenerService? = instance

        /**
         * Rebinds service or triggers active notifications scan with self-healing capability.
         */
        fun requestRebindService(context: Context) {
            try {
                if (instance != null) {
                    instance?.scanActiveNotifications(context)
                } else {
                    val cn = ComponentName(context, BankNotificationListenerService::class.java)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        requestRebind(cn)
                    }
                    // Self-healing fallback for OEM task killers (MIUI, ColorOS, Samsung, etc.):
                    // Toggling component state re-triggers Android NotificationManagerService's internal registerListener
                    val pm = context.packageManager
                    pm.setComponentEnabledSetting(
                        cn,
                        android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        android.content.pm.PackageManager.DONT_KILL_APP
                    )
                    pm.setComponentEnabledSetting(
                        cn,
                        android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        android.content.pm.PackageManager.DONT_KILL_APP
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Request rebind failed: ${e.message}")
            }
        }

        /**
         * Cancels original status bar notification when approved or rejected by user.
         */
        fun cancelOriginalNotification(sbnKey: String) {
            if (sbnKey.isBlank()) return
            try {
                instance?.cancelNotification(sbnKey)
                Log.d(TAG, "Cancelled notification with key: $sbnKey")
            } catch (e: Exception) {
                Log.w(TAG, "Could not cancel notification $sbnKey: ${e.message}")
            }
        }

        // Packages to monitor for financial transactions & RCS/WhatsApp Banking
        private val MONITORED_PACKAGES = setOf(
            // SMS & RCS Apps
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.mms",
            "com.oneplus.mms",
            "com.miui.smsextra",
            "com.coloros.mms",
            "com.vivo.mms",
            "com.transsion.message",
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.airtel.myairtel",
            "com.google.android.talk",
            "com.truecaller",

            // UPI, Fintech & Wallet Apps
            "com.google.android.apps.nbu.paisa.user", // Google Pay
            "com.phonepe.app",                       // PhonePe
            "net.one97.paytm",                       // Paytm
            "com.dreamplug.androidapp",              // CRED
            "in.org.npci.upiapp",                    // BHIM UPI
            "in.amazon.mShop.android.shopping",      // Amazon Pay
            "money.jupiter",                         // Jupiter
            "co.fi.money",                           // Fi Money
            "com.sodexo.pass.india",                 // Pluxee (Sodexo)
            "com.freecharge.android",                // Freecharge
            "com.mobikwik_new",                      // MobiKwik
            "com.navi.app",                          // Navi
            "com.groww.app",                         // Groww
            "com.slice.app",                         // Slice
            "com.supermoney.app",                    // Super.money
            "com.tata.neu",                          // Tata Neu
            "in.fampay",                             // FamPay
            "com.niyo.bank",                         // Niyo
            "com.postpe.app",                        // PostPe
            "com.onecard.app",                       // OneCard
            "com.pop.club",                          // Pop
            "com.shriram.app",                       // Shriram
            "com.jio.myjio",                          // JioFinance

            // Major Indian Bank Apps (Push alert notifications)
            "com.snapwork.hdfc",                     // HDFC Bank
            "com.sbi.lotusintouch",                  // SBI YONO
            "com.csam.icici.bank.imobile",           // ICICI iMobile
            "com.axis.mobile",                       // Axis Mobile
            "com.msf.kbank.mobile",                  // Kotak 811
            "com.pnb.pnbone",                        // PNB ONE
            "com.bankofbaroda.mconnect",             // bob World
            "com.canarabank.mobility",               // Canara ai1
            "com.idfcfirstbank.optimus",             // IDFC FIRST Bank
            "com.indusind.indusmobile",              // IndusInd
            "com.federalbank.fedmobile",             // FedMobile
            "com.rblbank.mobank",                    // RBL
            "com.aubank.mobilebanking",              // AU 0101
            "com.equitasbank.mobile",                // Equitas
            "com.ujjivan.mobile",                    // Ujjivan
            "com.bandhanbank.mobile",                // Bandhan
            "com.boi.mobile",                        // BOI
            "com.centralbank.mobile",                // Central Bank
            "com.iob.mobile",                        // IOB
            "com.kvb.mobile",                        // KVB
            "com.sib.mobile",                        // South Indian Bank
            "com.karnatakabank.mobile",              // Karnataka Bank
            "com.cub.mobile",                        // City Union
            "com.dhanlaxmi.mobile",                  // Dhanlaxmi
            "com.jkbank.mobile",                     // J&K Bank
            "com.bajaj.finserv"                      // Bajaj Finserv
        )
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.i(TAG, "BankNotificationListenerService successfully connected to Android OS.")
        BankSyncManager.updateListenerConnectionStatus(true)
        scanActiveNotifications(applicationContext)

        // Start foreground service to prevent Doze mode / background termination by OS task killers
        try {
            val channelId = "finmoney_listener_channel"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    "FinMoney Smart Sync Active",
                    android.app.NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Maintains active bank notification listening in background"
                }
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
            val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
                .setContentTitle("FinMoney Smart Sync Active")
                .setContentText("Listening for bank & UPI transaction alerts")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
                .build()
            startForeground(1001, notification)
        } catch (e: Exception) {
            Log.w(TAG, "Could not start foreground service: ${e.message}")
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
        Log.i(TAG, "BankNotificationListenerService disconnected. Triggering self-healing rebind...")
        BankSyncManager.updateListenerConnectionStatus(false)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            try {
                val cn = ComponentName(applicationContext, BankNotificationListenerService::class.java)
                requestRebind(cn)
            } catch (e: Exception) {
                Log.e(TAG, "Self-healing rebind failed on disconnection: ${e.message}")
            }
        }
    }

    /**
     * Scans currently visible notifications in the status bar to catch any missed bank alerts.
     */
    fun scanActiveNotifications(context: Context) {
        try {
            val activeNotifs = activeNotifications ?: return
            Log.d(TAG, "Scanning ${activeNotifs.size} active notifications in status bar drawer")
            for (sbn in activeNotifs) {
                processStatusBarNotification(sbn, context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning active notifications: ${e.message}")
        }
    }

    private val processedNotificationKeys = java.util.Collections.synchronizedSet(LinkedHashSet<String>())

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        // 1. Ignore ongoing / progress notifications (e.g. background music, call in progress, downloading)
        if (sbn.isOngoing || (sbn.notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) {
            Log.d(TAG, "Ignoring ongoing notification from ${sbn.packageName}")
            return
        }

        // 2. Ignore group summary notifications (child notifications contain actual transaction content)
        if ((sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
            Log.d(TAG, "Ignoring group summary notification from ${sbn.packageName}")
            return
        }

        // 3. Ignore active progress bar notifications (e.g. UPI transfer in progress before final completion)
        val extras = sbn.notification.extras
        if (extras != null && extras.containsKey(Notification.EXTRA_PROGRESS)) {
            val max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
            val indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
            if (max > 0 || indeterminate) {
                Log.d(TAG, "Ignoring in-progress notification from ${sbn.packageName}")
                return
            }
        }

        // 4. Handle notifications posted while device is locked (Keyguard active)
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
        val isLocked = keyguardManager?.isKeyguardLocked == true
        if (isLocked) {
            Log.d(TAG, "Device is locked (keyguard active). Capturing financial notification from ${sbn.packageName}")
        }

        // 5. Basic deduplication to ignore rapid duplicate SBN events
        val sbnKey = sbn.key
        if (processedNotificationKeys.contains(sbnKey)) {
            Log.d(TAG, "Ignoring rapid duplicate notification key: $sbnKey")
            return
        }
        processedNotificationKeys.add(sbnKey)
        if (processedNotificationKeys.size > 100) {
            val iterator = processedNotificationKeys.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }

        processStatusBarNotification(sbn, applicationContext)
    }

    private fun processStatusBarNotification(sbn: StatusBarNotification, context: Context) {
        val packageName = sbn.packageName ?: return

        try {
            val notification = sbn.notification ?: return
            var (title, fullBody) = extractAllNotificationText(notification)

            // If lock screen privacy masks notification text, fallback to publicVersion
            if (fullBody.isBlank() && notification.publicVersion != null) {
                val (pubTitle, pubBody) = extractAllNotificationText(notification.publicVersion)
                if (pubBody.isNotBlank()) {
                    title = pubTitle
                    fullBody = pubBody
                }
            }

            if (fullBody.isBlank()) return

            val isFinancialContent = BankTransactionParser.isFinancialMessage(fullBody, title) ||
                TransactionParser.isFinancialText(fullBody, title)

            val isMonitoredPkg = MONITORED_PACKAGES.contains(packageName) ||
                packageName.contains("messaging", ignoreCase = true) ||
                packageName.contains("message", ignoreCase = true) ||
                packageName.contains("sms", ignoreCase = true) ||
                packageName.contains("mms", ignoreCase = true) ||
                packageName.contains("bank", ignoreCase = true) ||
                packageName.contains("whatsapp", ignoreCase = true) ||
                packageName.contains("upi", ignoreCase = true) ||
                packageName.contains("finance", ignoreCase = true)

            if (!isMonitoredPkg && !isFinancialContent) {
                if (packageName.contains("message") || packageName.contains("sms") || packageName.contains("bank") || packageName.contains("pay")) {
                    NotificationRawLogger.logRawNotification(sbn, title, fullBody, isMonitoredPkg, isFinancialContent)
                }
                return
            }

            // Print full raw dump to Logcat for inspection
            NotificationRawLogger.logRawNotification(sbn, title, fullBody, isMonitoredPkg, isFinancialContent)

            Log.d(TAG, "Intercepted notification from $packageName ($title):\n$fullBody")

            BankSyncManager.processNotification(
                context = context,
                title = title.ifBlank { "Bank Alert" },
                text = fullBody,
                packageName = packageName,
                timestamp = sbn.postTime,
                sbnKey = sbn.key
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error handling notification: ${e.message}", e)
        }
    }

    /**
     * Deep extraction of notification text from standard fields, BigTextStyle, InboxStyle, and
     * MessagingStyle (used by Google Messages, Samsung Messages, WhatsApp, and RCS apps).
     */
    private fun extractAllNotificationText(notification: Notification): Pair<String, String> {
        val extras = notification.extras ?: return Pair("", "")

        val textPieces = mutableListOf<String>()

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
            ?: ""

        extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }?.let { textPieces.add(it) }
        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.takeIf { it.isNotBlank() }?.let { textPieces.add(it) }
        extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.takeIf { it.isNotBlank() }?.let { textPieces.add(it) }
        extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()?.takeIf { it.isNotBlank() }?.let { textPieces.add(it) }
        extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString()?.takeIf { it.isNotBlank() }?.let { textPieces.add(it) }
        notification.tickerText?.toString()?.takeIf { it.isNotBlank() }?.let { textPieces.add(it) }

        // InboxStyle line array
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        textLines?.forEach { line ->
            line?.toString()?.takeIf { it.isNotBlank() }?.let { textPieces.add(it) }
        }

        // MessagingStyle messages extraction for Google Messages, RCS, WhatsApp
        val messagingKeys = listOf(
            Notification.EXTRA_MESSAGES,
            Notification.EXTRA_HISTORIC_MESSAGES,
            "android.messages",
            "android.messages.historic"
        )

        for (key in messagingKeys) {
            try {
                val rawObj = extras.get(key) ?: continue
                val items: List<*> = when (rawObj) {
                    is Array<*> -> rawObj.toList()
                    is List<*> -> rawObj
                    else -> listOf(rawObj)
                }

                for (item in items) {
                    if (item == null) continue
                    when (item) {
                        is android.os.Bundle -> {
                            val msgText = item.getCharSequence("text")?.toString() ?: item.getString("text")
                            val msgSender = item.getCharSequence("sender")?.toString() ?: item.getString("sender")
                            if (!msgText.isNullOrBlank()) {
                                if (!msgSender.isNullOrBlank()) {
                                    textPieces.add("$msgSender: $msgText")
                                } else {
                                    textPieces.add(msgText)
                                }
                            }
                        }
                        is Notification.MessagingStyle.Message -> {
                            val msgText = item.text?.toString()
                            val msgSender = item.senderPerson?.name?.toString() ?: item.sender?.toString()
                            if (!msgText.isNullOrBlank()) {
                                if (!msgSender.isNullOrBlank()) {
                                    textPieces.add("$msgSender: $msgText")
                                } else {
                                    textPieces.add(msgText)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error reading messaging extra $key: ${e.message}")
            }
        }

        // Fallback key-set scan for any hidden text/title fields
        try {
            for (key in extras.keySet()) {
                val value = extras.get(key) ?: continue
                if (key.contains("title", ignoreCase = true) ||
                    key.contains("text", ignoreCase = true) ||
                    key.contains("message", ignoreCase = true) ||
                    key.contains("body", ignoreCase = true) ||
                    key.contains("sub", ignoreCase = true)) {
                    when (value) {
                        is CharSequence -> value.toString().takeIf { it.isNotBlank() }?.let { textPieces.add(it) }
                        is String -> value.takeIf { it.isNotBlank() }?.let { textPieces.add(it) }
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }

        val fullBody = listOf(title)
            .plus(textPieces)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")

        return Pair(title, fullBody)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // No action needed on removal
    }
}
