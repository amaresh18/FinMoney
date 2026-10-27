package com.example.audit

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.model.BankTransactionRecord
import com.example.data.model.DebitItem
import com.example.data.model.UserActionAuditLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Robust Logger to capture all user movements regarding bank/SMS alerts:
 * 1. Rejected messages
 * 2. Approved messages
 * 3. Subsequent edits to approved transactions (category, amount, subcategory, notes)
 *
 * Saves into Room and writes to persistent local JSONL so that AI Studio can inspect
 * real-world user choices and refine parsers.
 */
object UserActionAuditLogger {

    private const val TAG = "AIStudioAudit"
    private const val AUDIT_FILE_NAME = "user_action_audit_logs.jsonl"

    const val EVENT_APPROVED = "MESSAGE_APPROVED"
    const val EVENT_REJECTED = "MESSAGE_REJECTED"
    const val EVENT_EDITED = "APPROVED_NEXT_EDITED"

    private val scope = CoroutineScope(Dispatchers.IO)

    fun logMessageApproved(
        context: Context,
        tx: BankTransactionRecord,
        approvedCategory: String? = null,
        notes: String? = null
    ) {
        val finalCat = approvedCategory?.ifBlank { tx.category } ?: tx.category.ifBlank { "General" }
        val diff = if (!tx.category.equals(finalCat, ignoreCase = true)) {
            "User changed category from '${tx.category}' to '$finalCat' before approving."
        } else {
            "Approved with predicted category '$finalCat'."
        }

        val logEntry = UserActionAuditLog(
            userId = tx.userId,
            eventType = EVENT_APPROVED,
            originalMessage = tx.originalText,
            sender = tx.bankName,
            amount = tx.amount,
            currency = "₹",
            type = tx.type,
            merchant = tx.merchant,
            accountNumber = tx.accountNumberLast4,
            referenceNumber = tx.referenceNumber,
            initialCategory = tx.category,
            finalCategory = finalCat,
            initialAmount = tx.amount,
            finalAmount = tx.amount,
            finalNotes = notes ?: tx.originalText,
            diffSummary = diff,
            timestamp = System.currentTimeMillis()
        )

        persist(context, logEntry)
    }

    fun logMessageRejected(
        context: Context,
        tx: BankTransactionRecord,
        reason: String? = null
    ) {
        val rejectionReason = reason?.ifBlank { null }
            ?: tx.duplicateReason
            ?: tx.incorrectReason
            ?: "User dismissed/rejected from Pending Transactions."

        val logEntry = UserActionAuditLog(
            userId = tx.userId,
            eventType = EVENT_REJECTED,
            originalMessage = tx.originalText,
            sender = tx.bankName,
            amount = tx.amount,
            currency = "₹",
            type = tx.type,
            merchant = tx.merchant,
            accountNumber = tx.accountNumberLast4,
            referenceNumber = tx.referenceNumber,
            initialCategory = tx.category,
            finalCategory = "REJECTED",
            initialAmount = tx.amount,
            finalAmount = 0.0,
            initialNotes = rejectionReason,
            finalNotes = rejectionReason,
            diffSummary = "REJECTED: $rejectionReason",
            timestamp = System.currentTimeMillis()
        )

        persist(context, logEntry)
    }

    fun logApprovedNextEdited(
        context: Context,
        oldItem: DebitItem,
        newItem: DebitItem,
        matchedOriginalText: String? = null
    ) {
        val diffs = mutableListOf<String>()

        if (!oldItem.category.equals(newItem.category, ignoreCase = true)) {
            diffs.add("Category: '${oldItem.category}' -> '${newItem.category}'")
        }
        if (oldItem.amount != newItem.amount) {
            diffs.add("Amount: ₹${oldItem.amount} -> ₹${newItem.amount}")
        }
        if (!oldItem.customCategoryName.equals(newItem.customCategoryName, ignoreCase = true)) {
            diffs.add("Sub-Category / Custom Tag: '${oldItem.customCategoryName}' -> '${newItem.customCategoryName}'")
        }
        if (!oldItem.notes.equals(newItem.notes, ignoreCase = true)) {
            diffs.add("Notes: '${oldItem.notes}' -> '${newItem.notes}'")
        }
        if (!oldItem.title.equals(newItem.title, ignoreCase = true)) {
            diffs.add("Title/Merchant: '${oldItem.title}' -> '${newItem.title}'")
        }

        if (diffs.isEmpty()) return // Nothing relevant changed

        val diffSummary = diffs.joinToString(" | ")

        // Extract original text from note if stored there or passed
        val originalText = matchedOriginalText?.takeIf { it.isNotBlank() }
            ?: extractOriginalTextFromNotes(oldItem.notes)
            ?: oldItem.notes

        val logEntry = UserActionAuditLog(
            userId = newItem.userId.ifBlank { oldItem.userId },
            eventType = EVENT_EDITED,
            originalMessage = originalText,
            sender = extractBankFromNotes(oldItem.notes).ifBlank { oldItem.title },
            amount = newItem.amount,
            currency = "₹",
            type = if (newItem.isPaidViaCreditCard) "CREDIT_CARD" else "DEBIT",
            merchant = newItem.title,
            accountNumber = "",
            referenceNumber = extractRefFromNotes(oldItem.notes),
            initialCategory = oldItem.category,
            finalCategory = newItem.category,
            initialSubCategory = oldItem.customCategoryName,
            finalSubCategory = newItem.customCategoryName,
            initialAmount = oldItem.amount,
            finalAmount = newItem.amount,
            initialNotes = oldItem.notes,
            finalNotes = newItem.notes,
            diffSummary = diffSummary,
            timestamp = System.currentTimeMillis()
        )

        persist(context, logEntry)
    }

    private fun persist(context: Context, logEntry: UserActionAuditLog) {
        val jsonString = toJson(logEntry).toString()
        Log.i(TAG, "USER ACTION AUDIT [${logEntry.eventType}]: $jsonString")

        scope.launch {
            try {
                // 1. Room DB
                val db = AppDatabase.getDatabase(context.applicationContext)
                db.finMoneyDao().insertAuditLog(logEntry)
            } catch (e: Exception) {
                Log.w(TAG, "Room audit insert failed: ${e.message}")
            }

            try {
                // 2. Append to local JSONL file for permanent backup
                val file = File(context.applicationContext.filesDir, AUDIT_FILE_NAME)
                FileWriter(file, true).use { writer ->
                    writer.appendLine(jsonString)
                }
            } catch (e: Exception) {
                Log.w(TAG, "JSONL append failed: ${e.message}")
            }
        }
    }

    fun toJson(log: UserActionAuditLog): JSONObject {
        return JSONObject().apply {
            put("id", log.id)
            put("eventType", log.eventType)
            put("timestamp", log.timestamp)
            put("formattedDate", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp)))
            put("sender", log.sender)
            put("amount", log.amount)
            put("currency", log.currency)
            put("type", log.type)
            put("merchant", log.merchant)
            put("accountNumber", log.accountNumber)
            put("referenceNumber", log.referenceNumber)
            put("initialCategory", log.initialCategory)
            put("finalCategory", log.finalCategory)
            put("initialSubCategory", log.initialSubCategory)
            put("finalSubCategory", log.finalSubCategory)
            put("initialAmount", log.initialAmount)
            put("finalAmount", log.finalAmount)
            put("initialNotes", log.initialNotes)
            put("finalNotes", log.finalNotes)
            put("diffSummary", log.diffSummary)
            put("originalMessage", log.originalMessage)
        }
    }

    /**
     * Formats all saved audit logs into an AI Studio Prompt Markdown block
     * that the user can copy with 1 click to train/improve the classifier.
     */
    fun exportLogsForAIStudioPrompt(logs: List<UserActionAuditLog>): String {
        val sb = StringBuilder()
        sb.appendLine("# FinMoney User Action Audit Logs for AI Studio")
        sb.appendLine("Generated at: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
        sb.appendLine("Total Events Logged: ${logs.size}")
        val approved = logs.count { it.eventType == EVENT_APPROVED }
        val rejected = logs.count { it.eventType == EVENT_REJECTED }
        val edited = logs.count { it.eventType == EVENT_EDITED }
        sb.appendLine("Approved: $approved | Rejected: $rejected | Edited After Approval: $edited")
        sb.appendLine()
        sb.appendLine("Use these ground-truth user decisions to improve the SMS parser rules and category classifier:")
        sb.appendLine()

        logs.forEachIndexed { index, item ->
            val date = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(item.timestamp))
            sb.appendLine("### Event #${index + 1}: ${item.eventType} ($date)")
            if (item.originalMessage.isNotBlank()) {
                sb.appendLine("```")
                sb.appendLine("Raw Message: ${item.originalMessage.trim()}")
                sb.appendLine("```")
            }
            sb.appendLine("- **Bank / Sender**: ${item.sender.ifBlank { "Unknown" }}")
            sb.appendLine("- **Amount**: ₹${"%,.2f".format(item.amount)} (${item.type})")
            if (item.merchant.isNotBlank()) sb.appendLine("- **Merchant**: ${item.merchant}")
            if (item.referenceNumber.isNotBlank()) sb.appendLine("- **Ref**: ${item.referenceNumber}")

            when (item.eventType) {
                EVENT_APPROVED -> {
                    sb.appendLine("- **Predicted Category**: ${item.initialCategory}")
                    sb.appendLine("- **User Approved Category**: ${item.finalCategory}")
                    if (item.diffSummary.isNotBlank()) sb.appendLine("- **Action**: ${item.diffSummary}")
                }
                EVENT_REJECTED -> {
                    sb.appendLine("- **Initial Category**: ${item.initialCategory}")
                    sb.appendLine("- **Rejection Reason**: ${item.initialNotes}")
                }
                EVENT_EDITED -> {
                    sb.appendLine("- **Changes Made By User**: ${item.diffSummary}")
                    if (item.finalSubCategory.isNotBlank()) sb.appendLine("- **Sub-category / Tag**: ${item.finalSubCategory}")
                    if (item.finalNotes.isNotBlank()) sb.appendLine("- **Updated Notes**: ${item.finalNotes}")
                }
            }
            sb.appendLine()
        }
        return sb.toString()
    }

    fun exportLogsAsJson(logs: List<UserActionAuditLog>): String {
        val array = JSONArray()
        logs.forEach { array.put(toJson(it)) }
        return array.toString(2)
    }

    private fun extractOriginalTextFromNotes(notes: String): String? {
        val prefix = "Alert: "
        if (notes.contains(prefix)) {
            return notes.substringAfter(prefix).substringBefore(" Ref:").trim()
        }
        return null
    }

    private fun extractBankFromNotes(notes: String): String {
        val match = Regex("Approved from (.*?) alert").find(notes)
        return match?.groupValues?.getOrNull(1)?.trim() ?: ""
    }

    private fun extractRefFromNotes(notes: String): String {
        val match = Regex("Ref: (\\w+)").find(notes)
        return match?.groupValues?.getOrNull(1)?.trim() ?: ""
    }
}
