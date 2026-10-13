package com.example.service

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.data.bank.BankTransactionParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Logging wrapper for NotificationListenerService that captures and logs the complete
 * raw structure, extras bundle, and parsed financial data of incoming notifications to Logcat.
 */
object NotificationRawLogger {

    private const val TAG = "NotificationRawLogger"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    /**
     * Dumps complete raw content and structure of a [StatusBarNotification] to Logcat.
     */
    fun logRawNotification(
        sbn: StatusBarNotification,
        extractedTitle: String,
        extractedBody: String,
        isMonitoredPkg: Boolean,
        isFinancialContent: Boolean
    ) {
        try {
            val notification = sbn.notification
            val pkg = sbn.packageName ?: "unknown"
            val postTimeStr = try { dateFormat.format(Date(sbn.postTime)) } catch (e: Exception) { "${sbn.postTime}" }

            val builder = StringBuilder()
            builder.appendLine("=================== [RAW NOTIFICATION INTERCEPTED] ===================")
            builder.appendLine("Package Name : $pkg")
            builder.appendLine("Post Time    : $postTimeStr (${sbn.postTime})")
            builder.appendLine("Tag / ID     : ${sbn.tag} / ${sbn.id}")
            builder.appendLine("Category     : ${notification?.category ?: "none"}")
            builder.appendLine("Key          : ${sbn.key}")
            builder.appendLine("Ticker       : ${notification?.tickerText ?: "<null>"}")
            builder.appendLine("----------------------------------------------------------------------")
            builder.appendLine("EXTRAS BUNDLE DUMP:")

            val extras = notification?.extras
            if (extras == null || extras.isEmpty) {
                builder.appendLine("  <Extras Bundle is NULL or EMPTY>")
            } else {
                for (key in extras.keySet()) {
                    val value = extras.get(key)
                    builder.appendLine("  - $key -> ${formatValue(value)}")
                }
            }

            builder.appendLine("----------------------------------------------------------------------")
            builder.appendLine("TEXT EXTRACTION OUTPUT:")
            builder.appendLine("  Title    : \"$extractedTitle\"")
            builder.appendLine("  FullBody : \"${extractedBody.replace("\n", "\\n")}\"")
            builder.appendLine("----------------------------------------------------------------------")
            builder.appendLine("FILTER & PARSER STATUS:")
            builder.appendLine("  Monitored Package Filter : $isMonitoredPkg")
            builder.appendLine("  Financial Content Check  : $isFinancialContent")

            // Evaluate BankTransactionParser
            val bankTx = BankTransactionParser.parse(extractedBody, extractedTitle, pkg)
            builder.appendLine("  BankTransactionParser    : ${bankTx?.toString() ?: "<NULL - Failed to parse transaction>"}")

            // Evaluate TransactionParser
            val genericTx = TransactionParser.parse(extractedBody, extractedTitle, pkg)
            builder.appendLine("  TransactionParser        : ${genericTx?.toString() ?: "<NULL - Failed to parse generic transaction>"}")
            builder.appendLine("======================================================================")

            Log.i(TAG, builder.toString())

        } catch (e: Exception) {
            Log.e(TAG, "Error generating raw notification log dump: ${e.message}", e)
        }
    }

    private fun formatValue(value: Any?): String {
        if (value == null) return "<null>"
        return when (value) {
            is CharSequence -> "[CharSequence] \"$value\""
            is String -> "[String] \"$value\""
            is Bundle -> formatBundle(value)
            is Array<*> -> "[Array size=${value.size}] ${value.joinToString(", ") { formatValue(it) }}"
            is List<*> -> "[List size=${value.size}] ${value.joinToString(", ") { formatValue(it) }}"
            is Notification.MessagingStyle.Message -> {
                "[MessagingStyle.Message] text=\"${value.text}\", sender=\"${value.senderPerson?.name ?: value.sender}\""
            }
            else -> "[${value.javaClass.simpleName}] $value"
        }
    }

    private fun formatBundle(bundle: Bundle): String {
        if (bundle.isEmpty) return "[Bundle empty]"
        val items = bundle.keySet().map { key ->
            "$key=${formatValue(bundle.get(key))}"
        }
        return "[Bundle] { ${items.joinToString("; ")} }"
    }
}
