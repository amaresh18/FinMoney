package com.example.service

import com.example.data.bank.BankTxType
import com.example.data.bank.ParsedBankTransaction
import com.example.data.model.BankTransactionRecord
import com.example.data.model.DebitItem
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs

/**
 * Confidence level of a detected duplicate transaction.
 */
enum class DuplicateConfidence {
    UNIQUE,
    EXACT_FINGERPRINT_MATCH,  // Exact SHA-256 fingerprint match (100% duplicate)
    EXACT_REF_MATCH,          // Identical 12-digit UTR/RRN/Ref number (100% duplicate)
    RAPID_BROADCAST_MATCH,    // Same bank, amount, account within 3 minutes (SMS + App Push duplicate)
    IDENTICAL_CONTENT_HASH,   // Exact normalized text fingerprint within 24h
    ALREADY_RECORDED_DEBIT,   // Transaction already recorded in debit_transactions table
    REJECTED_TX_DISCARD,      // Transaction was explicitly rejected by user (prevent re-alert)
    SUSPECTED_REPEAT          // Similar amount and merchant within 1 hour without UTR (User should review)
}

data class DeduplicationResult(
    val isDuplicate: Boolean,
    val confidence: DuplicateConfidence,
    val reason: String,
    val matchedTxId: Long? = null
)

/**
 * Enterprise-grade deduplication engine designed to handle Indian and global banking SMS/RCS/Push alerts.
 *
 * Implements:
 * 1. SHA-256 cryptographic fingerprint from:
 *    (bankName + cleanAmount + transactionType + normalizedMerchant + accountLast4 + referenceNumber)
 * 2. Sliding 3-minute time window when reference number is absent (e.g. dual push + SMS alert).
 * 3. Cross-table check against already recorded debit_transactions.
 * 4. Rejection preservation: rejected transactions are recognized and permanently discarded if re-posted.
 */
object BankTransactionDeduplicator {

    const val RAPID_BROADCAST_WINDOW_MS = 180_000L     // 3 minutes (SMS + Push dual alert window)
    const val REPEAT_SUSPICION_WINDOW_MS = 3_600_000L   // 1 hour (Suspicious repeat without UTR)
    const val DEDUPLICATION_MAX_AGE_MS = 86_400_000L * 2 // 48 hours

    /**
     * Computes an idempotent SHA-256 cryptographic fingerprint from:
     * (bankName + cleanAmount + transactionType + normalizedMerchant + accountLast4 + referenceNumber)
     */
    fun computeFingerprint(
        bankName: String,
        amount: Double,
        transactionType: String,
        merchant: String,
        accountLast4: String,
        referenceNumber: String
    ): String {
        val cleanBank = bankName.trim().lowercase(Locale.ROOT)
        val cleanAmount = String.format(Locale.US, "%.2f", amount)
        val cleanType = transactionType.trim().uppercase(Locale.ROOT)
        val cleanMerchant = merchant.trim().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
        val cleanAccount = accountLast4.trim()
        val cleanRef = cleanReference(referenceNumber)

        val payload = "$cleanBank|$cleanAmount|$cleanType|$cleanMerchant|$cleanAccount|$cleanRef"
        return try {
            val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            payload.hashCode().toString()
        }
    }

    /**
     * Checks if a new parsed transaction is a duplicate against recent database records and confirmed debits.
     */
    fun evaluate(
        newTx: ParsedBankTransaction,
        existingRecords: List<BankTransactionRecord>,
        existingDebits: List<DebitItem> = emptyList()
    ): DeduplicationResult {
        val newRef = cleanReference(newTx.referenceNumber)
        val newFingerprint = computeFingerprint(
            bankName = newTx.bankName,
            amount = newTx.amount,
            transactionType = newTx.type.name,
            merchant = newTx.merchantOrParty,
            accountLast4 = newTx.accountNumberLast4,
            referenceNumber = newTx.referenceNumber
        )
        val newTextHash = normalizeContentHash(newTx.rawText)

        // 1. Check against existing bank transactions
        for (existing in existingRecords) {
            val timeDiff = abs(newTx.timestamp - existing.timestamp)
            if (timeDiff > DEDUPLICATION_MAX_AGE_MS) continue

            // SCENARIO 1: REJECTED TRANSACTION PRESERVATION
            // If user previously rejected this exact transaction, never re-ingest or re-alert
            if (existing.status.equals("REJECTED", ignoreCase = true)) {
                val isFingerprintMatch = existing.fingerprint.isNotBlank() && existing.fingerprint == newFingerprint
                val isRefMatch = newRef.isNotBlank() && cleanReference(existing.referenceNumber) == newRef
                val isRapidMatch = isBankMatch(newTx.bankName, existing.bankName) &&
                        abs(existing.amount - newTx.amount) < 0.01 &&
                        timeDiff <= RAPID_BROADCAST_WINDOW_MS

                if (isFingerprintMatch || isRefMatch || isRapidMatch) {
                    return DeduplicationResult(
                        isDuplicate = true,
                        confidence = DuplicateConfidence.REJECTED_TX_DISCARD,
                        reason = "Discarded: Transaction was previously rejected by user.",
                        matchedTxId = existing.id
                    )
                }
            }

            // SCENARIO 2: OPPOSITE TRANSACTION TYPES ARE NEVER DUPLICATES
            // e.g. DEBIT ₹500 vs CREDIT ₹500 (Refund / Reversal)
            val existingType = if (existing.type.equals("CREDIT", ignoreCase = true)) BankTxType.CREDIT else BankTxType.DEBIT
            if (existingType != newTx.type) {
                continue
            }

            // SCENARIO 3: DIFFERENT ACCOUNTS AT SAME BANK ARE NEVER DUPLICATES
            // e.g. Account ••1234 vs Account ••5678
            if (newTx.accountNumberLast4.isNotBlank() &&
                existing.accountNumberLast4.isNotBlank() &&
                !newTx.accountNumberLast4.equals(existing.accountNumberLast4, ignoreCase = true)
            ) {
                continue
            }

            val isSameAmount = abs(existing.amount - newTx.amount) < 0.01
            val existingRef = cleanReference(existing.referenceNumber)

            // SCENARIO 4: EXACT SHA-256 FINGERPRINT MATCH
            if (existing.fingerprint.isNotBlank() && existing.fingerprint == newFingerprint) {
                return DeduplicationResult(
                    isDuplicate = true,
                    confidence = DuplicateConfidence.EXACT_FINGERPRINT_MATCH,
                    reason = "Duplicate: Identical SHA-256 fingerprint for ₹${"%,.0f".format(newTx.amount)} from ${newTx.bankName}.",
                    matchedTxId = existing.id
                )
            }

            // SCENARIO 5: IDENTICAL REFERENCE / UTR / RRN NUMBER (100% Guaranteed Duplicate)
            if (newRef.isNotBlank() && existingRef.isNotBlank() && newRef.equals(existingRef, ignoreCase = true)) {
                return DeduplicationResult(
                    isDuplicate = true,
                    confidence = DuplicateConfidence.EXACT_REF_MATCH,
                    reason = "Duplicate: Identical Reference/UTR number ($newRef) for ₹${"%,.0f".format(newTx.amount)} already recorded.",
                    matchedTxId = existing.id
                )
            }

            // SCENARIO 6: DIFFERENT UTRS ARE NEVER DUPLICATES
            if (newRef.isNotBlank() && existingRef.isNotBlank() && !newRef.equals(existingRef, ignoreCase = true)) {
                continue
            }

            if (!isSameAmount) continue

            // SCENARIO 7: RAPID DUAL-ALERT BROADCAST (< 3 minutes)
            // Same bank, amount, account/merchant received within 3 minutes (e.g., SMS + App Push notification)
            val isSameBank = isBankMatch(newTx.bankName, existing.bankName)
            if (isSameBank && timeDiff <= RAPID_BROADCAST_WINDOW_MS) {
                return DeduplicationResult(
                    isDuplicate = true,
                    confidence = DuplicateConfidence.RAPID_BROADCAST_MATCH,
                    reason = "Duplicate alert: Received within ${timeDiff / 1000}s for ₹${"%,.0f".format(newTx.amount)} from ${newTx.bankName} (Dual SMS/Push alert).",
                    matchedTxId = existing.id
                )
            }

            // SCENARIO 8: EXACT CONTENT FINGERPRINT HASH WITHIN 24 HOURS
            val existingTextHash = normalizeContentHash(existing.originalText)
            if (newTextHash == existingTextHash && timeDiff <= 86_400_000L) {
                return DeduplicationResult(
                    isDuplicate = true,
                    confidence = DuplicateConfidence.IDENTICAL_CONTENT_HASH,
                    reason = "Duplicate alert: Exact identical notification content received within last 24 hours.",
                    matchedTxId = existing.id
                )
            }

            // SCENARIO 9: SUSPECTED REPEAT TRANSACTION (Within 1 hour, same merchant, no UTR)
            val isSameMerchant = newTx.merchantOrParty.isNotBlank() &&
                    existing.merchant.isNotBlank() &&
                    (newTx.merchantOrParty.contains(existing.merchant, ignoreCase = true) || existing.merchant.contains(newTx.merchantOrParty, ignoreCase = true))

            if (isSameBank && (isSameMerchant || newTx.merchantOrParty.isBlank()) && timeDiff <= REPEAT_SUSPICION_WINDOW_MS) {
                val mins = timeDiff / 60_000
                return DeduplicationResult(
                    isDuplicate = true,
                    confidence = DuplicateConfidence.SUSPECTED_REPEAT,
                    reason = "Possible repeat: Similar ₹${"%,.0f".format(newTx.amount)} payment detected ${mins}m ago. Verify if separate transaction.",
                    matchedTxId = existing.id
                )
            }
        }

        // 2. Cross-table check against already confirmed debits in debit_transactions
        if (newTx.type == BankTxType.DEBIT && existingDebits.isNotEmpty()) {
            for (debit in existingDebits) {
                if (newRef.isNotBlank() && debit.notes.contains(newRef, ignoreCase = true)) {
                    return DeduplicationResult(
                        isDuplicate = true,
                        confidence = DuplicateConfidence.ALREADY_RECORDED_DEBIT,
                        reason = "Duplicate: Transaction with Reference/UTR $newRef is already recorded as confirmed expense '${debit.title}'.",
                        matchedTxId = debit.id
                    )
                }

                // If reference number is absent: check same amount and merchant within 3 minutes
                val isSameAmount = abs(debit.amount - newTx.amount) < 0.01
                if (isSameAmount && newRef.isBlank()) {
                    val debitTime = debit.createdAt.takeIf { it > 0 } ?: debit.dueDate ?: 0L
                    if (debitTime > 0) {
                        val timeDiff = abs(newTx.timestamp - debitTime)
                        if (timeDiff <= RAPID_BROADCAST_WINDOW_MS) {
                            val merchant = newTx.merchantOrParty.ifBlank { newTx.bankName }
                            if (debit.title.contains(merchant, ignoreCase = true) || merchant.contains(debit.title, ignoreCase = true)) {
                                return DeduplicationResult(
                                    isDuplicate = true,
                                    confidence = DuplicateConfidence.ALREADY_RECORDED_DEBIT,
                                    reason = "Duplicate: Expense for ₹${"%,.0f".format(newTx.amount)} already confirmed as '${debit.title}'.",
                                    matchedTxId = debit.id
                                )
                            }
                        }
                    }
                }
            }
        }

        return DeduplicationResult(
            isDuplicate = false,
            confidence = DuplicateConfidence.UNIQUE,
            reason = "Unique transaction"
        )
    }

    fun cleanReference(ref: String): String {
        return ref.trim()
            .replace(Regex("[^a-zA-Z0-9]"), "")
            .uppercase(Locale.ROOT)
    }

    fun normalizeContentHash(text: String): String {
        val normalized = text.lowercase(Locale.ROOT)
            .replace(Regex("\\b\\d{1,2}[-/:.]\\d{1,2}[-/:.]\\d{2,4}\\b"), "")
            .replace(Regex("\\b\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\s*[ap]m)?\\b"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return normalized.hashCode().toString()
    }

    private fun isBankMatch(bankA: String, bankB: String): Boolean {
        if (bankA.equals(bankB, ignoreCase = true)) return true
        val a = bankA.lowercase(Locale.ROOT)
        val b = bankB.lowercase(Locale.ROOT)
        return a.contains(b) || b.contains(a)
    }
}
