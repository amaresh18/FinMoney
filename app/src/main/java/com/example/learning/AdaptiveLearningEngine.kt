package com.example.learning

import android.content.Context
import android.util.Log
import com.example.data.bank.ParsedBankTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.FinMoneyDao
import com.example.data.model.BankTransactionRecord
import com.example.data.model.DebitItem
import com.example.data.model.LearnedTransactionRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Self-Learning Engine for automated expense intelligence and noise suppression:
 * 1. Automatically adapts categories, titles, tags, and subcategories whenever a user edits an expense.
 * 2. Remembers user customizations so future transactions from the same merchant/pattern require zero manual edits.
 * 3. Identifies and suppresses invalid/non-expense notifications that the user repeatedly rejects.
 * 4. Self-optimizes over time with real-time feedback loops and audit logs.
 */
object AdaptiveLearningEngine {

    private const val TAG = "AdaptiveLearningEngine"
    private val scope = CoroutineScope(Dispatchers.IO)

    data class EvaluationResult(
        val isSuppressed: Boolean = false,
        val suppressionReason: String? = null,
        val enhancedTx: ParsedBankTransaction? = null,
        val matchedRule: LearnedTransactionRule? = null
    )

    /**
     * Extracts a normalized canonical pattern key from merchant name, text, or sender.
     * Examples:
     * - "SWIGGY BANGALORE" -> "swiggy"
     * - "UPI/CR/123/ZOMATO/zomato@icici" -> "zomato"
     * - "Netflix Entertainment Services India" -> "netflix"
     * - "user@okaxis" -> "user@okaxis"
     */
    fun extractPatternKey(merchant: String, rawText: String = "", sender: String = ""): String {
        val cleanMerchant = merchant.trim()
        val combinedText = "$cleanMerchant $rawText".lowercase(Locale.ROOT)

        // Try extracting known keyword or merchant first (e.g. Swiggy in "SWIGGY BANGALORE")
        val knownPatterns = listOf(
            "swiggy", "zomato", "uber", "ola", "blinkit", "zepto", "instamart", "amazon", "flipkart",
            "myntra", "netflix", "prime video", "hotstar", "spotify", "airtel", "jio", "vi",
            "bsnl", "bescom", "tneb", "tatapower", "bookmyshow", "bigbasket", "bbdaily",
            "cult.fit", "cultfit", "dunzo", "rapido", "irctc", "makemytrip", "cred", "zerodha", "groww"
        )
        for (kp in knownPatterns) {
            if (combinedText.contains(kp)) {
                return kp
            }
        }

        // Try extracting UPI VPA from merchant or raw text
        val vpaMatch = Regex("""\b([a-zA-Z0-9.\-_]{2,30}@[a-zA-Z]{2,15})\b""").find(combinedText)
        if (vpaMatch != null) {
            val vpa = vpaMatch.groupValues[1].lowercase(Locale.ROOT).trim()
            if (!vpa.startsWith("paytm") && !vpa.startsWith("phonepe") && !vpa.startsWith("gpay")) {
                return vpa
            }
        }

        if (cleanMerchant.isNotBlank()) {
            val key = normalizeMerchantString(cleanMerchant)
            if (key.isNotBlank()) return key
        }

        if (cleanMerchant.isNotBlank()) {
            return cleanMerchant.lowercase(Locale.ROOT).replace(Regex("""[^a-z0-9]"""), "")
        }

        val senderClean = sender.trim().lowercase(Locale.ROOT)
        if (senderClean.isNotBlank()) {
            return senderClean.replace(Regex("""[^a-z0-9]"""), "")
        }

        return "unknown"
    }

    private fun normalizeMerchantString(merchant: String): String {
        var s = merchant.trim().lowercase(Locale.ROOT)
        // Remove common corporate suffixes
        s = s.replace(Regex("""\b(?:pvt|ltd|limited|private|llp|inc|corp|services|technologies|solutions|retail|store|india)\b"""), " ")
        // Remove common UPI prefixes
        s = s.replace(Regex("""^(?:upi[-_/]|pos[-_/]|ecomm[-_/]|neft[-_/]|rtgs[-_/]|imps[-_/])"""), " ")
        // Remove punctuation and digits
        s = s.replace(Regex("""[^a-z\s@]"""), " ")
        // Collapse spaces
        s = s.split(Regex("""\s+""")).filter { it.length >= 2 }.joinToString(" ").trim()
        return s.ifBlank { merchant.trim().lowercase(Locale.ROOT) }
    }

    /**
     * Triggered whenever an expense is updated by the user in FinMoney.
     * Automatically learns title, category, tags, and subcategory mappings for future transactions.
     */
    fun learnFromDebitEdit(
        context: Context,
        oldItem: DebitItem,
        newItem: DebitItem
    ) {
        scope.launch {
            try {
                val dao = AppDatabase.getDatabase(context).finMoneyDao()
                val patternKey = extractPatternKey(
                    merchant = newItem.title.ifBlank { oldItem.title },
                    rawText = oldItem.notes
                )
                if (patternKey.isBlank() || patternKey == "unknown") return@launch

                val userId = newItem.userId.ifBlank { oldItem.userId }
                val existing = dao.getLearnedRuleByPattern(patternKey, userId)

                val updatedRule = LearnedTransactionRule(
                    id = existing?.id ?: 0L,
                    userId = userId,
                    patternKey = patternKey,
                    merchantDisplayName = newItem.title.trim().ifBlank { existing?.merchantDisplayName ?: patternKey.replaceFirstChar { it.uppercase() } },
                    matchType = if (patternKey.contains("@")) "VPA" else "MERCHANT",
                    learnedCategory = newItem.category.trim(),
                    learnedCustomCategoryName = newItem.customCategoryName.trim(),
                    learnedTitle = newItem.title.trim(),
                    learnedTags = newItem.tags.trim().ifBlank { existing?.learnedTags ?: "" },
                    isAutoIgnored = false, // User actively edited and kept this, so it is a valid expense!
                    rejectCount = 0,
                    approveCount = (existing?.approveCount ?: 0) + 1,
                    editCount = (existing?.editCount ?: 0) + 1,
                    notes = "Learned from user edit: '${newItem.title}' [${newItem.category}]",
                    updatedAt = System.currentTimeMillis()
                )

                dao.insertOrUpdateLearnedRule(updatedRule)
                Log.i(TAG, "Self-learning: Updated rule for '$patternKey' -> Category: ${updatedRule.learnedCategory}, Title: ${updatedRule.learnedTitle}, Tags: ${updatedRule.learnedTags}")
            } catch (e: Exception) {
                Log.w(TAG, "Error in learnFromDebitEdit: ${e.message}")
            }
        }
    }

    /**
     * Triggered when a pending transaction is approved by the user.
     */
    fun learnFromTransactionApproval(
        context: Context,
        tx: BankTransactionRecord,
        customCategory: String? = null,
        customTitle: String? = null,
        customTags: String? = null
    ) {
        scope.launch {
            try {
                val dao = AppDatabase.getDatabase(context).finMoneyDao()
                val patternKey = extractPatternKey(
                    merchant = tx.merchant,
                    rawText = tx.originalText,
                    sender = tx.bankName
                )
                if (patternKey.isBlank() || patternKey == "unknown") return@launch

                val userId = tx.userId
                val existing = dao.getLearnedRuleByPattern(patternKey, userId)

                val finalCategory = customCategory?.trim()?.takeIf { it.isNotBlank() }
                    ?: existing?.learnedCategory?.takeIf { it.isNotBlank() }
                    ?: tx.category.ifBlank { "General" }

                val finalTitle = customTitle?.trim()?.takeIf { it.isNotBlank() }
                    ?: existing?.learnedTitle?.takeIf { it.isNotBlank() }
                    ?: tx.merchant.ifBlank { tx.bankName }

                val finalTags = customTags?.trim()?.takeIf { it.isNotBlank() }
                    ?: existing?.learnedTags
                    ?: ""

                val updatedRule = LearnedTransactionRule(
                    id = existing?.id ?: 0L,
                    userId = userId,
                    patternKey = patternKey,
                    merchantDisplayName = finalTitle,
                    matchType = if (patternKey.contains("@")) "VPA" else "MERCHANT",
                    learnedCategory = finalCategory,
                    learnedCustomCategoryName = existing?.learnedCustomCategoryName ?: "",
                    learnedTitle = finalTitle,
                    learnedTags = finalTags,
                    isAutoIgnored = false,
                    rejectCount = 0,
                    approveCount = (existing?.approveCount ?: 0) + 1,
                    editCount = existing?.editCount ?: 0,
                    notes = "Approved by user with category: $finalCategory",
                    updatedAt = System.currentTimeMillis()
                )

                dao.insertOrUpdateLearnedRule(updatedRule)
                Log.i(TAG, "Self-learning: Recorded approval for '$patternKey' ($finalCategory)")
            } catch (e: Exception) {
                Log.w(TAG, "Error in learnFromTransactionApproval: ${e.message}")
            }
        }
    }

    /**
     * Triggered when a user rejects a transaction.
     * If rejected multiple times (e.g. >= 2 times), marks pattern as auto-ignored so future alerts
     * are suppressed without bothering the user.
     */
    fun learnFromTransactionRejection(
        context: Context,
        tx: BankTransactionRecord,
        reason: String? = null
    ) {
        scope.launch {
            try {
                val dao = AppDatabase.getDatabase(context).finMoneyDao()
                val patternKey = extractPatternKey(
                    merchant = tx.merchant,
                    rawText = tx.originalText,
                    sender = tx.bankName
                )
                if (patternKey.isBlank() || patternKey == "unknown") return@launch

                val userId = tx.userId
                val existing = dao.getLearnedRuleByPattern(patternKey, userId)

                val newRejectCount = (existing?.rejectCount ?: 0) + 1
                val isExplicitMute = reason?.contains("never", ignoreCase = true) == true ||
                        reason?.contains("spam", ignoreCase = true) == true ||
                        reason?.contains("not an expense", ignoreCase = true) == true

                // Auto-suppress if rejected 2 or more times, or if user explicitly requested ignore
                val shouldAutoIgnore = newRejectCount >= 2 || isExplicitMute

                val updatedRule = LearnedTransactionRule(
                    id = existing?.id ?: 0L,
                    userId = userId,
                    patternKey = patternKey,
                    merchantDisplayName = tx.merchant.ifBlank { patternKey },
                    matchType = if (patternKey.contains("@")) "VPA" else "MERCHANT",
                    learnedCategory = existing?.learnedCategory ?: tx.category,
                    learnedCustomCategoryName = existing?.learnedCustomCategoryName ?: "",
                    learnedTitle = existing?.learnedTitle ?: tx.merchant,
                    learnedTags = existing?.learnedTags ?: "",
                    isAutoIgnored = shouldAutoIgnore,
                    rejectCount = newRejectCount,
                    approveCount = existing?.approveCount ?: 0,
                    editCount = existing?.editCount ?: 0,
                    notes = "Rejected $newRejectCount time(s) by user. Auto-ignored: $shouldAutoIgnore. Reason: ${reason ?: "User dismissed"}",
                    updatedAt = System.currentTimeMillis()
                )

                dao.insertOrUpdateLearnedRule(updatedRule)
                Log.i(TAG, "Self-learning: Recorded rejection for '$patternKey' (count=$newRejectCount, autoIgnored=$shouldAutoIgnore)")
            } catch (e: Exception) {
                Log.w(TAG, "Error in learnFromTransactionRejection: ${e.message}")
            }
        }
    }

    /**
     * Evaluates an incoming parsed transaction against learned rules:
     * 1. Checks if it should be suppressed (user repeatedly rejected this pattern).
     * 2. Enhances the transaction with user's historically edited title, category, and tags.
     */
    suspend fun evaluateAndApply(
        dao: FinMoneyDao,
        parsedTx: ParsedBankTransaction?,
        rawText: String,
        sender: String,
        userId: String = ""
    ): EvaluationResult {
        if (parsedTx == null) return EvaluationResult()

        val patternKey = extractPatternKey(
            merchant = parsedTx.merchantOrParty,
            rawText = rawText,
            sender = sender
        )
        if (patternKey.isBlank() || patternKey == "unknown") {
            return EvaluationResult(enhancedTx = parsedTx)
        }

        val rule = try {
            dao.getLearnedRuleByPattern(patternKey, userId)
        } catch (_: Exception) {
            null
        }

        if (rule == null) {
            return EvaluationResult(enhancedTx = parsedTx)
        }

        // Case 1: Pattern was rejected repeatedly -> Suppress completely!
        if (rule.isAutoIgnored) {
            Log.i(TAG, "Self-learning SUPPRESSION applied: '${parsedTx.merchantOrParty}' matching rule '${rule.patternKey}' was suppressed (rejected ${rule.rejectCount} times)")
            return EvaluationResult(
                isSuppressed = true,
                suppressionReason = "Auto-rejected based on ${rule.rejectCount} previous user rejections of '${rule.patternKey}'",
                enhancedTx = parsedTx,
                matchedRule = rule
            )
        }

        // Case 2: Apply user-edited values historically learned
        var enhanced = parsedTx
        if (rule.learnedCategory.isNotBlank()) {
            enhanced = enhanced.copy(category = rule.learnedCategory)
        }
        if (rule.learnedTitle.isNotBlank()) {
            enhanced = enhanced.copy(merchantOrParty = rule.learnedTitle)
        }

        // Update lastAppliedTimestamp
        try {
            dao.insertOrUpdateLearnedRule(
                rule.copy(
                    lastAppliedTimestamp = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
        } catch (_: Exception) {}

        Log.i(TAG, "Self-learning ENHANCEMENT applied for '${rule.patternKey}': Category='${enhanced.category}', Merchant='${enhanced.merchantOrParty}', Tags='${rule.learnedTags}'")

        return EvaluationResult(
            isSuppressed = false,
            enhancedTx = enhanced,
            matchedRule = rule
        )
    }

    /**
     * Looks up any learned rule for an existing BankTransactionRecord.
     */
    suspend fun getLearnedRuleForTx(
        dao: FinMoneyDao,
        tx: BankTransactionRecord,
        userId: String = ""
    ): LearnedTransactionRule? {
        val patternKey = extractPatternKey(
            merchant = tx.merchant,
            rawText = tx.originalText,
            sender = tx.bankName
        )
        if (patternKey.isBlank() || patternKey == "unknown") return null
        return try {
            dao.getLearnedRuleByPattern(patternKey, userId)
        } catch (_: Exception) {
            null
        }
    }
}
