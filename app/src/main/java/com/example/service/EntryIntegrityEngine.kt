package com.example.service

import com.example.data.bank.BankTxType
import com.example.data.bank.ParsedBankTransaction
import com.example.data.model.BankTransactionRecord
import com.example.data.model.DebitItem
import kotlin.math.abs

/**
 * Result of auditing a bank transaction for duplicates and data errors.
 */
data class EntryAuditResult(
    val isDuplicate: Boolean = false,
    val duplicateConfidence: DuplicateConfidence = DuplicateConfidence.UNIQUE,
    val duplicateReason: String = "",
    val matchedTxId: Long? = null,
    val isIncorrect: Boolean = false,
    val incorrectReason: String = ""
)

/**
 * Result of auditing a planned debit/expense entry.
 */
data class DebitAuditStatus(
    val isDuplicate: Boolean = false,
    val duplicateWithId: Long? = null,
    val duplicateTitle: String = "",
    val isIncorrect: Boolean = false,
    val incorrectReason: String = ""
)

/**
 * Automated Entry Integrity & Deduplication Engine.
 *
 * Automatically inspects incoming transactions, pending alerts, and planned debits
 * to detect:
 * 1. Duplicate entries (exact UTR, rapid broadcast dual-alerts, carrier retries, duplicate ledger debits).
 * 2. Incorrect entries (credit/debit type inversion, non-financial OTPs, delivery trackers, bill due notices, corrupted amounts).
 */
object EntryIntegrityEngine {

    /**
     * Audits an incoming parsed transaction against existing database records.
     */
    fun auditIncomingTransaction(
        newTx: ParsedBankTransaction,
        rawText: String,
        existingRecords: List<BankTransactionRecord>
    ): EntryAuditResult {
        // 1. Evaluate Duplication
        val dedupResult = BankTransactionDeduplicator.evaluate(newTx, existingRecords)

        // 2. Evaluate Correctness
        val incorrectReason = evaluateIncorrectness(
            amount = newTx.amount,
            type = newTx.type,
            bankName = newTx.bankName,
            merchant = newTx.merchantOrParty,
            rawText = rawText
        )

        return EntryAuditResult(
            isDuplicate = dedupResult.isDuplicate,
            duplicateConfidence = dedupResult.confidence,
            duplicateReason = dedupResult.reason,
            matchedTxId = dedupResult.matchedTxId,
            isIncorrect = incorrectReason != null,
            incorrectReason = incorrectReason ?: ""
        )
    }

    /**
     * Audits a list of pending transactions dynamically.
     * Detects cross-item duplicates within the pending list as well as data errors.
     */
    fun auditPendingTransactions(
        records: List<BankTransactionRecord>
    ): Map<Long, EntryAuditResult> {
        val resultMap = mutableMapOf<Long, EntryAuditResult>()

        for (i in records.indices) {
            val current = records[i]
            var isDup = current.isDuplicate
            var dupReason = current.duplicateReason
            var matchedId: Long? = null

            // Check if it duplicates an earlier item in this pending batch
            for (j in 0 until i) {
                val previous = records[j]

                // Same UTR / Reference
                val curRef = cleanRef(current.referenceNumber)
                val prevRef = cleanRef(previous.referenceNumber)
                if (curRef.isNotBlank() && prevRef.isNotBlank() && curRef.equals(prevRef, ignoreCase = true)) {
                    isDup = true
                    dupReason = "Duplicate: Identical UTR/Ref ($curRef) already pending."
                    matchedId = previous.id
                    break
                }

                // Same payment shown as SMS + UPI app + carrier notification
                val isSameAmount = abs(current.amount - previous.amount) < 0.01
                val sameType = current.type.equals(previous.type, ignoreCase = true)
                val accountsConflict = current.accountNumberLast4.isNotBlank() &&
                    previous.accountNumberLast4.isNotBlank() &&
                    !current.accountNumberLast4.equals(previous.accountNumberLast4, ignoreCase = true)
                val timeDiff = abs(current.timestamp - previous.timestamp)
                if (isSameAmount && sameType && !accountsConflict && timeDiff <= BankTransactionDeduplicator.SAME_PAYMENT_BURST_MS) {
                    isDup = true
                    dupReason = "Duplicate: Multiple alerts for one ₹${"%,.0f".format(current.amount)} payment."
                    matchedId = previous.id
                    break
                }
            }

            // Check incorrectness
            val txType = if (current.type.equals("CREDIT", ignoreCase = true)) BankTxType.CREDIT else BankTxType.DEBIT
            val detectedIncorrectReason = evaluateIncorrectness(
                amount = current.amount,
                type = txType,
                bankName = current.bankName,
                merchant = current.merchant,
                rawText = current.originalText
            )

            val isIncorrect = current.isIncorrect || (detectedIncorrectReason != null)
            val finalIncorrectReason = if (!current.incorrectReason.isNullOrBlank()) current.incorrectReason!! else (detectedIncorrectReason ?: "")

            resultMap[current.id] = EntryAuditResult(
                isDuplicate = isDup,
                duplicateReason = dupReason ?: "",
                matchedTxId = matchedId,
                isIncorrect = isIncorrect,
                incorrectReason = finalIncorrectReason
            )
        }

        return resultMap
    }

    /**
     * Audits planned debits to identify duplicate entries and incorrect parameters.
     */
    fun auditPlannedDebits(debits: List<DebitItem>): Map<Long, DebitAuditStatus> {
        val result = mutableMapOf<Long, DebitAuditStatus>()
        val seenSignatures = mutableMapOf<String, DebitItem>()

        for (item in debits) {
            val normTitle = item.title.trim().lowercase()
            val amountKey = "%.2f".format(java.util.Locale.US, item.amount)
            val sig = "$normTitle|$amountKey"

            var isDup = false
            var dupWithId: Long? = null
            var dupTitle = ""

            if (normTitle.isNotBlank() && seenSignatures.containsKey(sig)) {
                val previous = seenSignatures[sig]!!
                isDup = true
                dupWithId = previous.id
                dupTitle = previous.title
            } else if (normTitle.isNotBlank()) {
                seenSignatures[sig] = item
            }

            // Check incorrect entries
            val incorrectReason = when {
                item.amount <= 0.0 -> "Amount must be greater than ₹0"
                item.dueDateDay !in 1..31 -> "Due day must be between 1 and 31"
                item.title.isBlank() -> "Title/Merchant name cannot be empty"
                else -> null
            }

            result[item.id] = DebitAuditStatus(
                isDuplicate = isDup,
                duplicateWithId = dupWithId,
                duplicateTitle = dupTitle,
                isIncorrect = incorrectReason != null,
                incorrectReason = incorrectReason ?: ""
            )
        }

        return result
    }

    /**
     * Analyzes raw notification content and parsed attributes for logical errors.
     */
    fun evaluateIncorrectness(
        amount: Double?,
        type: BankTxType,
        bankName: String,
        merchant: String,
        rawText: String
    ): String? {
        val lower = rawText.lowercase()

        // 1. Transaction Type / Verb Inversion Check
        val hasRefundCreditKeywords = lower.contains("refund") || lower.contains("refunded") ||
                lower.contains("cashback") || lower.contains("reversal") ||
                lower.contains("credited to your account") || lower.contains("credited by") ||
                lower.contains("deposited to") || lower.contains("received from")

        val hasDebitKeywords = lower.contains("debited from") || lower.contains("paid to") ||
                lower.contains("sent to") || lower.contains("spent on") ||
                lower.contains("withdrawn from") || lower.contains("purchase of") ||
                lower.contains("charged to")

        if (type == BankTxType.DEBIT && hasRefundCreditKeywords && !hasDebitKeywords) {
            return "Incorrect entry: Transaction wording indicates a refund/credit, but parsed as Debit."
        }

        if (type == BankTxType.CREDIT && hasDebitKeywords && !hasRefundCreditKeywords) {
            return "Incorrect entry: Transaction wording indicates a debit/payment, but parsed as Credit."
        }

        // 2. Non-financial OTP / Verification Code Check
        if (lower.contains("otp") || lower.contains("verification code") ||
            lower.contains("one time password") || lower.contains("secret code") ||
            lower.contains("do not share this")
        ) {
            return "Incorrect entry: Non-financial OTP or verification security alert."
        }

        // 3. Delivery / Order Status Ping Check
        if (lower.contains("is on the way") || lower.contains("picked up your order") ||
            lower.contains("out for delivery") || lower.contains("valet has picked") ||
            lower.contains("order placed") || lower.contains("preparing your order") ||
            lower.contains("dispatched") || lower.contains("arriving soon")
        ) {
            return "Incorrect entry: Delivery/order status update rather than bank transaction."
        }

        // 4. Upcoming Bill Due Reminder Check
        if (lower.contains("bill of rs") || lower.contains("bill generated") ||
            lower.contains("reminder: pay your") || lower.contains("due date is") ||
            lower.contains("payment due of") || lower.contains("statement is ready")
        ) {
            return "Incorrect entry: Upcoming bill due notification, not an actual bank deduction."
        }

        // 5. Amount Sanity Check
        if (amount == null || amount <= 0.0) {
            return "Incorrect entry: Transaction amount is zero or missing."
        }

        // 6. Check if amount matches a 6-digit OTP code in text
        val otpMatch = Regex("\\b(\\d{6})\\b").find(rawText)
        if (otpMatch != null && abs(amount - otpMatch.value.toDouble()) < 0.01 && !lower.contains("rs") && !lower.contains("inr")) {
            return "Incorrect entry: Parsed amount appears to be a 6-digit OTP code."
        }

        // 7. Missing identifiers
        if ((bankName.isBlank() || bankName.equals("Unknown", ignoreCase = true)) && merchant.isBlank()) {
            return "Incorrect entry: Missing bank and merchant identity."
        }

        return null
    }

    private fun cleanRef(ref: String): String {
        return ref.trim().replace(Regex("[^a-zA-Z0-9]"), "").uppercase()
    }

    private fun isBankMatch(bankA: String, bankB: String): Boolean {
        if (bankA.equals(bankB, ignoreCase = true)) return true
        val a = bankA.lowercase()
        val b = bankB.lowercase()
        return a.contains(b) || b.contains(a)
    }
}
