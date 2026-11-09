package com.example.service.gemini

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.model.TransactionClassificationResult
import com.example.service.TransactionParser
import com.example.service.ai.TransactionAiClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

object GeminiNotificationClassificationService {

    private const val TAG = "GeminiNotifService"

    private val _classificationEvents = MutableSharedFlow<TransactionClassificationResult>(extraBufferCapacity = 10)
    val classificationEvents: SharedFlow<TransactionClassificationResult> = _classificationEvents.asSharedFlow()

    suspend fun parseAndCategorize(
        text: String,
        senderOrTitle: String = "",
        timestamp: Long = System.currentTimeMillis()
    ): TransactionClassificationResult {
        val result = TransactionAiClassifier.classify(text, senderOrTitle, timestamp)
        _classificationEvents.emit(result)
        return result
    }

    suspend fun updateTransactionClassification(
        context: Context,
        txId: Long
    ): Result<TransactionClassificationResult> = withContext(Dispatchers.IO) {
        try {
            val dao = AppDatabase.getDatabase(context).finMoneyDao()
            val existing = dao.getBankTransactionById(txId)
                ?: return@withContext Result.failure(IllegalArgumentException("Transaction $txId not found."))

            val classification = TransactionAiClassifier.classify(
                rawText = existing.originalText,
                senderOrTitle = existing.bankName,
                timestamp = existing.timestamp
            )

            if (!classification.isFinancial || classification.amount <= 0.0) {
                dao.deleteBankTransaction(existing.id)
                return@withContext Result.failure(IllegalStateException("Non-financial message removed."))
            }

            if (classification.source == "HEURISTIC_LOCAL") {
                return@withContext Result.failure(IllegalStateException("AI refine is temporarily unavailable."))
            }

            val updated = existing.copy(
                type = if (classification.isCredit) "CREDIT" else "DEBIT",
                merchant = classification.displayTitle.ifBlank { existing.merchant },
                category = TransactionAiClassifier.mergeIntoLocal(
                    TransactionParser.parse(existing.originalText, existing.bankName, existing.source, existing.timestamp),
                    classification
                ).category,
                bankName = classification.sender.ifBlank { existing.bankName },
                amount = if (classification.amount > 0.0) classification.amount else existing.amount,
                accountNumberLast4 = classification.accountEnding.ifBlank { existing.accountNumberLast4 },
                referenceNumber = classification.referenceNumber.ifBlank { existing.referenceNumber },
                availableBalance = classification.availableBalance ?: existing.availableBalance,
                isIncorrect = false,
                incorrectReason = null
            )

            dao.insertBankTransaction(updated)
            Log.i(TAG, "Updated tx $txId: ${classification.displayTitle}")
            _classificationEvents.emit(classification)
            Result.success(classification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update transaction $txId: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun batchRefinePendingTransactions(context: Context): Int = withContext(Dispatchers.IO) {
        if (!TransactionAiClassifier.isConfigured()) return@withContext 0
        val dao = AppDatabase.getDatabase(context).finMoneyDao()
        val pending = dao.getPendingBankTransactionsDirect()
        var count = 0
        for (tx in pending) {
            if (TransactionParser.determineNonFinancialReason(tx.originalText, tx.bankName) != null) {
                dao.deleteBankTransaction(tx.id)
                count++
                continue
            }
            if (updateTransactionClassification(context, tx.id).isSuccess) count++
        }
        count
    }

    suspend fun sweepPromotionalMessages(context: Context): Int = withContext(Dispatchers.IO) {
        val dao = AppDatabase.getDatabase(context).finMoneyDao()
        var purged = 0
        for (tx in dao.getPendingBankTransactionsDirect()) {
            val reason = TransactionParser.determineNonFinancialReason(tx.originalText, tx.bankName)
            if (reason != null) {
                dao.deleteBankTransaction(tx.id)
                purged++
            }
        }
        purged
    }
}
