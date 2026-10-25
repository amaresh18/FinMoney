package com.example.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Explicit transaction flow indicating whether funds are incoming (Credit) or outgoing (Debit).
 */
enum class TransactionType {
    CREDIT,
    DEBIT;

    companion object {
        fun fromString(value: String): TransactionType {
            val v = value.trim().uppercase()
            return if (v == "CREDIT" || v == "CR" || v == "INCOME") CREDIT else DEBIT
        }
    }
}

/**
 * Data model for financial transaction details extracted from SMS or status bar notifications.
 *
 * Core required fields:
 * @param date Timestamp in milliseconds of the transaction event.
 * @param amount Extracted absolute monetary value in currency (INR).
 * @param sender Originating financial institution, bank, or payment application name.
 * @param type Nature of transaction (CREDIT for incoming/income, DEBIT for outgoing/expenses).
 */
data class TransactionClassificationResult(
    val date: Long = System.currentTimeMillis(),
    val amount: Double,
    val sender: String,
    val type: TransactionType,
    val merchant: String = "",
    val category: String = "GENERAL",
    val accountEnding: String = "",
    val referenceNumber: String = "",
    val availableBalance: Double? = null,
    val isFinancial: Boolean = true,
    val rawText: String = "",
    val confidence: Float = 1.0f,
    val source: String = "GEMINI_AI"
) {
    val isCredit: Boolean get() = type == TransactionType.CREDIT
    val isDebit: Boolean get() = type == TransactionType.DEBIT

    val formattedDate: String
        get() = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(date))

    val displayTitle: String
        get() = if (merchant.isNotBlank() && merchant != sender) merchant else sender.ifBlank { "Bank Transaction" }

    val summaryText: String
        get() {
            val prefix = if (isCredit) "🟢 Credit" else "🔴 Debit"
            val bal = availableBalance?.let { " • Avl: ₹${"%,.0f".format(it)}" } ?: ""
            return "$prefix ₹${"%,.0f".format(amount)} via $sender$bal"
        }
}
