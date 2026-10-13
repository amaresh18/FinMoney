package com.example.domain.usecase

import com.example.data.model.BankTransactionRecord
import com.example.data.model.DebitItem
import kotlin.math.abs

/**
 * Pure Kotlin Use Case for matching bank transactions against planned debit items.
 * Provides isolated reconciliation scoring without database or UI side-effects.
 */
class ReconcileTransactionUseCase {

    data class MatchCandidate(
        val plannedItem: DebitItem,
        val confidenceScore: Double, // 0.0 to 1.0
        val isExactAmount: Boolean,
        val matchReason: String
    )

    /**
     * Finds the best planned debit match for a given detected bank transaction.
     */
    fun findBestDebitMatch(
        transaction: BankTransactionRecord,
        unpaidDebitItems: List<DebitItem>
    ): MatchCandidate? {
        if (unpaidDebitItems.isEmpty() || transaction.amount <= 0.0) return null

        var bestMatch: MatchCandidate? = null
        var highestScore = -1.0

        for (item in unpaidDebitItems) {
            val amountDiff = abs(item.amount - transaction.amount)
            val isExactAmount = amountDiff < 0.01

            var score = 0.0
            val reasons = mutableListOf<String>()

            // Amount matching
            if (isExactAmount) {
                score += 0.65
                reasons.add("Exact Amount Match (₹${transaction.amount})")
            } else if (amountDiff / transaction.amount < 0.05) {
                score += 0.35
                reasons.add("Close Amount (<5% diff)")
            }

            // Keyword / Merchant / Category matching
            val txText = "${transaction.merchant} ${transaction.originalText} ${transaction.category}".lowercase()
            val itemTitle = item.title.lowercase()
            val itemCategory = item.category.lowercase()

            if (txText.contains(itemTitle) || (itemTitle.isNotBlank() && txText.take(6).contains(itemTitle.take(6)))) {
                score += 0.35
                reasons.add("Merchant Name Match ('${item.title}')")
            } else if (txText.contains(itemCategory) || itemCategory.contains(transaction.category.lowercase())) {
                score += 0.15
                reasons.add("Category Match ('${item.category}')")
            }

            if (score > highestScore && score >= 0.5) {
                highestScore = score
                bestMatch = MatchCandidate(
                    plannedItem = item,
                    confidenceScore = score.coerceIn(0.0, 1.0),
                    isExactAmount = isExactAmount,
                    matchReason = reasons.joinToString(" • ")
                )
            }
        }

        return bestMatch
    }
}
