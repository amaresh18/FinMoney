package com.example.learning

import android.content.Context
import android.util.Log
import com.example.cloud.CloudUserContextResolver
import com.example.cloud.LocalFirstCloudSync
import com.example.data.local.FinMoneyDao
import com.example.data.model.BankTransactionRecord
import com.example.data.model.DebitItem
import com.example.data.model.LearnedTransactionRule
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class SimilarItemTarget {
    data class PendingTx(val tx: BankTransactionRecord) : SimilarItemTarget()
    data class ExistingDebit(val debit: DebitItem) : SimilarItemTarget()
}

data class SimilarTransactionCandidate(
    val id: String, // "tx_123" or "debit_456"
    val title: String,
    val amount: Double,
    val dateFormatted: String,
    val currentCategory: String,
    val rawSnippet: String,
    val target: SimilarItemTarget
)

data class SimilarTransactionsPrompt(
    val patternKey: String,
    val merchantName: String,
    val newCategory: String,
    val newTitle: String = "",
    val newTags: String = "",
    val newCustomCategoryName: String = "",
    val sourceDescription: String = "",
    val candidates: List<SimilarTransactionCandidate> = emptyList()
)

/**
 * Axio-style Similarity Intelligence Engine:
 * When a user edits a transaction (category, title, or tags), detects all similar
 * transactions from the same merchant/pattern/SMS and prompts the user to retroactively
 * bulk update them in one tap.
 */
object SimilarTransactionsManager {

    private const val TAG = "SimilarTransactionsMgr"
    private val dateFormatter = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    private val debitDateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    /**
     * Scans for similar unconfirmed bank transactions or existing debits when a pending transaction's
     * category or merchant/title is updated.
     */
    fun findSimilarForPendingTx(
        editedTx: BankTransactionRecord,
        newCategory: String,
        newTitle: String = "",
        pendingList: List<BankTransactionRecord>,
        debitList: List<DebitItem> = emptyList()
    ): SimilarTransactionsPrompt? {
        val patternKey = AdaptiveLearningEngine.extractPatternKey(
            merchant = newTitle.ifBlank { editedTx.merchant },
            rawText = editedTx.originalText,
            sender = editedTx.bankName
        )
        if (patternKey.isBlank() || patternKey == "unknown") return null

        val merchantDisplayName = newTitle.trim().ifBlank {
            editedTx.merchant.trim().ifBlank { patternKey.replaceFirstChar { it.uppercase() } }
        }

        val candidates = mutableListOf<SimilarTransactionCandidate>()

        // 1. Scan other pending transactions
        for (tx in pendingList) {
            if (tx.id == editedTx.id) continue
            val txPattern = AdaptiveLearningEngine.extractPatternKey(
                merchant = tx.merchant,
                rawText = tx.originalText,
                sender = tx.bankName
            )
            val isMatch = txPattern == patternKey ||
                (patternKey.length >= 3 && (tx.merchant.contains(patternKey, ignoreCase = true) || tx.originalText.contains(patternKey, ignoreCase = true)))

            if (isMatch) {
                val categoryDiffers = !tx.category.equals(newCategory, ignoreCase = true)
                val merchantDiffers = newTitle.isNotBlank() && !tx.merchant.equals(newTitle, ignoreCase = true)
                if (categoryDiffers || merchantDiffers) {
                    candidates.add(
                        SimilarTransactionCandidate(
                            id = "tx_${tx.id}",
                            title = tx.merchant.ifBlank { tx.bankName },
                            amount = tx.amount,
                            dateFormatted = dateFormatter.format(Date(tx.timestamp)),
                            currentCategory = tx.category.ifBlank { "General" },
                            rawSnippet = tx.originalText.take(120),
                            target = SimilarItemTarget.PendingTx(tx)
                        )
                    )
                }
            }
        }

        // 2. Scan recent historical debits
        for (debit in debitList) {
            val debitPattern = AdaptiveLearningEngine.extractPatternKey(
                merchant = debit.title,
                rawText = debit.notes
            )
            val isMatch = debitPattern == patternKey ||
                (patternKey.length >= 3 && (debit.title.contains(patternKey, ignoreCase = true) || debit.notes.contains(patternKey, ignoreCase = true)))

            if (isMatch) {
                val categoryDiffers = !debit.category.equals(newCategory, ignoreCase = true)
                val titleDiffers = newTitle.isNotBlank() && !debit.title.equals(newTitle, ignoreCase = true)
                if (categoryDiffers || titleDiffers) {
                    val cal = java.util.Calendar.getInstance()
                    cal.set(debit.year, debit.month - 1, debit.dueDateDay)
                    candidates.add(
                        SimilarTransactionCandidate(
                            id = "debit_${debit.id}",
                            title = debit.title.ifBlank { debit.category },
                            amount = debit.amount,
                            dateFormatted = debitDateFormatter.format(cal.time),
                            currentCategory = debit.category,
                            rawSnippet = debit.notes.take(120),
                            target = SimilarItemTarget.ExistingDebit(debit)
                        )
                    )
                }
            }
        }

        if (candidates.isEmpty()) return null

        return SimilarTransactionsPrompt(
            patternKey = patternKey,
            merchantName = merchantDisplayName,
            newCategory = newCategory,
            newTitle = newTitle,
            sourceDescription = "Updated from transaction of ₹${editedTx.amount}",
            candidates = candidates
        )
    }

    /**
     * Scans for similar transactions when an existing expense (DebitItem) is edited by the user.
     */
    fun findSimilarForDebit(
        oldDebit: DebitItem,
        newDebit: DebitItem,
        pendingList: List<BankTransactionRecord>,
        debitList: List<DebitItem>
    ): SimilarTransactionsPrompt? {
        val patternKey = AdaptiveLearningEngine.extractPatternKey(
            merchant = newDebit.title.ifBlank { oldDebit.title },
            rawText = "${newDebit.notes} ${oldDebit.notes}"
        )
        if (patternKey.isBlank() || patternKey == "unknown") return null

        val merchantDisplayName = newDebit.title.trim().ifBlank {
            oldDebit.title.trim().ifBlank { patternKey.replaceFirstChar { it.uppercase() } }
        }

        val candidates = mutableListOf<SimilarTransactionCandidate>()

        // 1. Scan other historical debits
        for (debit in debitList) {
            if (debit.id == newDebit.id) continue
            val debitPattern = AdaptiveLearningEngine.extractPatternKey(
                merchant = debit.title,
                rawText = debit.notes
            )
            val isMatch = debitPattern == patternKey ||
                (patternKey.length >= 3 && (debit.title.contains(patternKey, ignoreCase = true) || debit.notes.contains(patternKey, ignoreCase = true)))

            if (isMatch) {
                val categoryDiffers = !debit.category.equals(newDebit.category, ignoreCase = true)
                val titleDiffers = newDebit.title.isNotBlank() && !debit.title.equals(newDebit.title, ignoreCase = true)
                val tagsDiffer = newDebit.tags.isNotBlank() && !debit.tags.equals(newDebit.tags, ignoreCase = true)
                if (categoryDiffers || titleDiffers || tagsDiffer) {
                    val cal = java.util.Calendar.getInstance()
                    cal.set(debit.year, debit.month - 1, debit.dueDateDay)
                    candidates.add(
                        SimilarTransactionCandidate(
                            id = "debit_${debit.id}",
                            title = debit.title.ifBlank { debit.category },
                            amount = debit.amount,
                            dateFormatted = debitDateFormatter.format(cal.time),
                            currentCategory = debit.category,
                            rawSnippet = debit.notes.take(120),
                            target = SimilarItemTarget.ExistingDebit(debit)
                        )
                    )
                }
            }
        }

        // 2. Scan pending bank transactions
        for (tx in pendingList) {
            val txPattern = AdaptiveLearningEngine.extractPatternKey(
                merchant = tx.merchant,
                rawText = tx.originalText,
                sender = tx.bankName
            )
            val isMatch = txPattern == patternKey ||
                (patternKey.length >= 3 && (tx.merchant.contains(patternKey, ignoreCase = true) || tx.originalText.contains(patternKey, ignoreCase = true)))

            if (isMatch) {
                val categoryDiffers = !tx.category.equals(newDebit.category, ignoreCase = true)
                val merchantDiffers = newDebit.title.isNotBlank() && !tx.merchant.equals(newDebit.title, ignoreCase = true)
                if (categoryDiffers || merchantDiffers) {
                    candidates.add(
                        SimilarTransactionCandidate(
                            id = "tx_${tx.id}",
                            title = tx.merchant.ifBlank { tx.bankName },
                            amount = tx.amount,
                            dateFormatted = dateFormatter.format(Date(tx.timestamp)),
                            currentCategory = tx.category.ifBlank { "General" },
                            rawSnippet = tx.originalText.take(120),
                            target = SimilarItemTarget.PendingTx(tx)
                        )
                    )
                }
            }
        }

        if (candidates.isEmpty()) return null

        return SimilarTransactionsPrompt(
            patternKey = patternKey,
            merchantName = merchantDisplayName,
            newCategory = newDebit.category,
            newTitle = newDebit.title,
            newTags = newDebit.tags,
            newCustomCategoryName = newDebit.customCategoryName,
            sourceDescription = "Updated from expense '${newDebit.title}'",
            candidates = candidates
        )
    }

    /**
     * Executes the bulk retroactive update across selected candidates.
     */
    suspend fun executeBulkUpdate(
        context: Context,
        dao: FinMoneyDao,
        prompt: SimilarTransactionsPrompt,
        selectedCandidateIds: Set<String>,
        userId: String = ""
    ): Int {
        var updatedCount = 0

        for (candidate in prompt.candidates) {
            if (!selectedCandidateIds.contains(candidate.id)) continue

            try {
                when (val target = candidate.target) {
                    is SimilarItemTarget.PendingTx -> {
                        val currentTx = dao.getBankTransactionById(target.tx.id) ?: target.tx
                        val updatedTx = currentTx.copy(
                            category = prompt.newCategory.ifBlank { currentTx.category },
                            merchant = prompt.newTitle.ifBlank { currentTx.merchant }
                        )
                        dao.updateBankTransaction(updatedTx)
                        updatedCount++
                    }
                    is SimilarItemTarget.ExistingDebit -> {
                        val currentDebit = dao.getDebitById(target.debit.id) ?: target.debit
                        val updatedDebit = currentDebit.copy(
                            category = prompt.newCategory.ifBlank { currentDebit.category },
                            title = prompt.newTitle.ifBlank { currentDebit.title },
                            tags = if (prompt.newTags.isNotBlank()) prompt.newTags else currentDebit.tags,
                            customCategoryName = if (prompt.newCustomCategoryName.isNotBlank()) prompt.newCustomCategoryName else currentDebit.customCategoryName
                        )
                        dao.updateDebit(updatedDebit)
                        try {
                            val cloudUser = CloudUserContextResolver.resolve(dao)
                            LocalFirstCloudSync.queueExpense(context, cloudUser, updatedDebit)
                        } catch (_: Exception) {}
                        updatedCount++
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed updating candidate ${candidate.id}: ${e.message}")
            }
        }

        // Also ensure the learned rule reflects this comprehensive update
        try {
            val existing = dao.getLearnedRuleByPattern(prompt.patternKey, userId)
            val updatedRule = LearnedTransactionRule(
                id = existing?.id ?: 0L,
                userId = userId,
                patternKey = prompt.patternKey,
                merchantDisplayName = prompt.merchantName,
                matchType = if (prompt.patternKey.contains("@")) "VPA" else "MERCHANT",
                learnedCategory = prompt.newCategory,
                learnedCustomCategoryName = prompt.newCustomCategoryName.ifBlank { existing?.learnedCustomCategoryName ?: "" },
                learnedTitle = prompt.newTitle.ifBlank { existing?.learnedTitle ?: prompt.merchantName },
                learnedTags = prompt.newTags.ifBlank { existing?.learnedTags ?: "" },
                isAutoIgnored = false,
                rejectCount = 0,
                approveCount = (existing?.approveCount ?: 0) + updatedCount,
                editCount = (existing?.editCount ?: 0) + 1,
                notes = "Bulk updated $updatedCount similar transaction(s) by user",
                lastAppliedTimestamp = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            dao.insertOrUpdateLearnedRule(updatedRule)
        } catch (e: Exception) {
            Log.w(TAG, "Failed updating learned rule after bulk update: ${e.message}")
        }

        return updatedCount
    }
}
