package com.example.service.ai

import android.util.Log
import com.example.data.bank.BankTxType
import com.example.data.bank.ParsedBankTransaction
import com.example.data.model.DebitCategory
import com.example.data.model.TransactionClassificationResult
import com.example.service.TransactionParseResult
import com.example.service.gemini.GeminiClassificationEngine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * App-owned Gemini Flash classification (API key in release build only). No user keys.
 */
object TransactionAiClassifier {

    private const val TAG = "TxnAiClassifier"
    private const val REFINE_TIMEOUT_MS = 12_000L

    private val engine = GeminiClassificationEngine()

    fun isConfigured(): Boolean = engine.hasConfiguredApiKey()

    fun shouldRefine(local: TransactionParseResult): Boolean {
        if (!local.isFinancialNotification) return false
        if ((local.amount ?: 0.0) <= 0.0) return false
        if (local.confidenceScore < 0.75f) return true
        if (local.category == DebitCategory.GENERAL.name) return true
        val title = local.merchantName.trim()
        if (title.isBlank() || title.equals(local.bankName, ignoreCase = true)) return true
        if (title.length < 3) return true
        return false
    }

    suspend fun classify(
        rawText: String,
        senderOrTitle: String,
        timestamp: Long = System.currentTimeMillis()
    ): TransactionClassificationResult {
        if (!isConfigured()) {
            return engine.fallbackLocalParse(rawText, senderOrTitle, timestamp)
        }
        return engine.parseNotification(rawText, senderOrTitle, timestamp)
    }

    suspend fun refineIfNeeded(
        rawText: String,
        senderOrTitle: String,
        sourcePackage: String,
        timestamp: Long,
        local: TransactionParseResult,
        parsed: ParsedBankTransaction?
    ): Pair<TransactionParseResult, ParsedBankTransaction?>? {
        if (!isConfigured() || !shouldRefine(local)) return null

        val ai = withTimeoutOrNull(REFINE_TIMEOUT_MS) {
            classify(rawText, senderOrTitle, timestamp)
        } ?: return null

        if (!ai.isFinancial || ai.source == "HEURISTIC_LOCAL") return null

        val mergedParse = mergeIntoLocal(local, ai)
        val mergedParsed = applyToBankTx(parsed, mergedParse, rawText, sourcePackage, timestamp)
        Log.i(TAG, "Gemini refined: ${mergedParse.merchantName} | ${mergedParse.category}")
        return mergedParse to mergedParsed
    }

    fun mergeIntoLocal(
        local: TransactionParseResult,
        ai: TransactionClassificationResult
    ): TransactionParseResult {
        val title = ai.displayTitle.ifBlank { ai.merchant }.trim()
        val category = resolveCategory(ai.category, ai.subCategory, local.category)
        val amount = if (ai.amount > 0.0) ai.amount else local.amount
        val txType = if (ai.isCredit) BankTxType.CREDIT else BankTxType.DEBIT

        return local.copy(
            bankName = ai.sender.ifBlank { local.bankName },
            merchantName = title.ifBlank { local.merchantName },
            category = category,
            amount = amount,
            transactionType = txType,
            accountEnding = ai.accountEnding.ifBlank { local.accountEnding },
            referenceNumber = ai.referenceNumber.ifBlank { local.referenceNumber },
            availableBalance = ai.availableBalance ?: local.availableBalance,
            confidenceScore = maxOf(local.confidenceScore, ai.confidence)
        )
    }

    private fun resolveCategory(primary: String, subCategory: String, fallback: String): String {
        val main = primary.trim().uppercase().replace(" ", "_")
        if (main.isNotBlank() && main != "GENERAL" && main != "NON_FINANCIAL") {
            if (DebitCategory.entries.any { it.name == main }) return main
        }
        val sub = subCategory.trim().uppercase().replace(" ", "_")
        if (sub.isNotBlank() && DebitCategory.entries.any { it.name == sub }) return sub
        return fallback
    }

    private fun applyToBankTx(
        parsed: ParsedBankTransaction?,
        mergedParse: TransactionParseResult,
        rawText: String,
        sourcePackage: String,
        timestamp: Long
    ): ParsedBankTransaction? {
        val base = parsed ?: mergedParse.toParsedBankTransaction() ?: return null
        return base.copy(
            bankName = mergedParse.bankName,
            merchantOrParty = mergedParse.merchantName,
            category = mergedParse.category,
            amount = mergedParse.amount ?: base.amount,
            type = mergedParse.transactionType,
            accountNumberLast4 = mergedParse.accountEnding.ifBlank { base.accountNumberLast4 },
            referenceNumber = mergedParse.referenceNumber.ifBlank { base.referenceNumber },
            availableBalance = mergedParse.availableBalance ?: base.availableBalance,
            rawText = rawText,
            sourcePackage = sourcePackage,
            timestamp = timestamp
        )
    }
}
