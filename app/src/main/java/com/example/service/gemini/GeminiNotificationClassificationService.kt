package com.example.service.gemini

import android.content.Context
import android.util.Log
import com.example.data.bank.BankTxType
import com.example.data.local.AppDatabase
import com.example.data.model.BankTransactionRecord
import com.example.data.model.TransactionClassificationResult
import com.example.data.model.TransactionType
import com.example.service.BankSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Service that orchestrates Gemini-powered transaction classification
 * and updates application logic to accurately categorize financial notifications.
 */
object GeminiNotificationClassificationService {

    private const val TAG = "GeminiNotifService"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var engine = GeminiClassificationEngine()

    private val _classificationEvents = MutableSharedFlow<TransactionClassificationResult>(extraBufferCapacity = 10)
    val classificationEvents: SharedFlow<TransactionClassificationResult> = _classificationEvents.asSharedFlow()

    /**
     * Updates the underlying classification engine with an optional custom Gemini API key.
     */
    fun configureApiKey(apiKey: String?) {
        engine = GeminiClassificationEngine(customApiKey = apiKey)
    }

    /**
     * Parses and categorizes an incoming notification or SMS string.
     * Returns structured transaction details (date, amount, sender, type: credit/debit).
     */
    suspend fun parseAndCategorize(
        text: String,
        senderOrTitle: String = "",
        timestamp: Long = System.currentTimeMillis()
    ): TransactionClassificationResult {
        val result = engine.parseNotification(text, senderOrTitle, timestamp)
        _classificationEvents.emit(result)
        return result
    }

    /**
     * Refines and updates an existing BankTransactionRecord in the local database
     * using the Gemini classification engine.
     */
    suspend fun updateTransactionClassification(
        context: Context,
        txId: Long
    ): Result<TransactionClassificationResult> = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val dao = db.finMoneyDao()
            val existing = dao.getBankTransactionById(txId)
                ?: return@withContext Result.failure(IllegalArgumentException("Transaction $txId not found."))

            val classification = engine.parseNotification(
                rawText = existing.originalText,
                senderOrTitle = existing.bankName,
                timestamp = existing.timestamp
            )

            if (!classification.isFinancial || classification.amount <= 0.0) {
                dao.deleteBankTransaction(existing.id)
                Log.i(TAG, "Purged non-financial/promotional record $txId: ${existing.originalText}")
                return@withContext Result.failure(IllegalStateException("Non-financial or promotional message purged."))
            }

            val updatedType = if (classification.isCredit) "CREDIT" else "DEBIT"
            val updatedMerchant = classification.merchant.ifBlank { existing.merchant }
            val updatedCategory = if (classification.category != "GENERAL" && classification.category != "NON_FINANCIAL") classification.category else existing.category
            val updatedBank = classification.sender.ifBlank { existing.bankName }

            val updatedRecord = existing.copy(
                type = updatedType,
                merchant = updatedMerchant,
                category = updatedCategory,
                bankName = updatedBank,
                amount = if (classification.amount > 0.0) classification.amount else existing.amount,
                accountNumberLast4 = classification.accountEnding.ifBlank { existing.accountNumberLast4 },
                referenceNumber = classification.referenceNumber.ifBlank { existing.referenceNumber },
                availableBalance = classification.availableBalance ?: existing.availableBalance,
                isIncorrect = false,
                incorrectReason = null
            )

            dao.insertBankTransaction(updatedRecord)
            Log.i(TAG, "Updated transaction $txId via Gemini: ${classification.summaryText}")
            _classificationEvents.emit(classification)

            Result.success(classification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update transaction $txId: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Asynchronously schedules background refinement for newly intercepted notifications.
     * Ensures real-time local parsing is never blocked, while Gemini enhances categorization.
     */
    fun scheduleBackgroundRefinement(
        context: Context,
        savedRecordId: Long,
        rawText: String,
        senderOrTitle: String,
        timestamp: Long
    ) {
        scope.launch {
            try {
                val classification = engine.parseNotification(rawText, senderOrTitle, timestamp)
                val db = AppDatabase.getDatabase(context)
                val dao = db.finMoneyDao()
                val record = dao.getBankTransactionById(savedRecordId) ?: return@launch
                if (!record.isConfirmed) {
                    if (!classification.isFinancial || classification.amount <= 0.0) {
                        Log.i(TAG, "Gemini classified transaction $savedRecordId as non-financial/promo. Purging record.")
                        dao.deleteBankTransaction(record.id)
                        return@launch
                    }

                    if (classification.source == "GEMINI_AI") {
                        val newType = if (classification.isCredit) "CREDIT" else "DEBIT"
                        val updated = record.copy(
                            type = newType,
                            merchant = classification.merchant.ifBlank { record.merchant },
                            category = if (classification.category != "GENERAL" && classification.category != "NON_FINANCIAL") classification.category else record.category,
                            bankName = classification.sender.ifBlank { record.bankName }
                        )
                        dao.insertBankTransaction(updated)
                        Log.d(TAG, "Background Gemini refinement applied to tx $savedRecordId")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Background refinement skipped: ${e.message}")
            }
        }
    }

    /**
     * Batch updates all unconfirmed pending transactions using Gemini to ensure
     * every item in the inbox has accurate categorization and debit/credit status.
     * Automatically purges detected promotional, bill due, and marketing messages.
     */
    suspend fun batchRefinePendingTransactions(context: Context): Int = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val dao = db.finMoneyDao()
            val pending = dao.getPendingBankTransactionsDirect()
            var count = 0
            for (tx in pending) {
                // First perform local heuristic filter check
                val nonFinReason = com.example.service.TransactionParser.determineNonFinancialReason(tx.originalText, tx.bankName)
                if (nonFinReason != null) {
                    Log.i(TAG, "Purging promotional item ${tx.id}: $nonFinReason")
                    dao.deleteBankTransaction(tx.id)
                    count++
                    continue
                }

                val res = updateTransactionClassification(context, tx.id)
                if (res.isSuccess) count++
            }
            count
        } catch (e: Exception) {
            Log.e(TAG, "Batch refinement error: ${e.message}", e)
            0
        }
    }

    /**
     * Sweeps and purges all existing promotional, loan advertisement, recharge prompt,
     * and non-financial entries from pending unconfirmed transactions.
     */
    suspend fun sweepPromotionalMessages(context: Context): Int = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val dao = db.finMoneyDao()
            val pending = dao.getPendingBankTransactionsDirect()
            var purged = 0
            for (tx in pending) {
                val nonFinReason = com.example.service.TransactionParser.determineNonFinancialReason(tx.originalText, tx.bankName)
                if (nonFinReason != null) {
                    Log.i(TAG, "Sweeper purged promotional item ${tx.id} ($nonFinReason): ${tx.originalText}")
                    dao.deleteBankTransaction(tx.id)
                    purged++
                }
            }
            purged
        } catch (e: Exception) {
            Log.e(TAG, "Error sweeping promotional messages: ${e.message}", e)
            0
        }
    }
}
