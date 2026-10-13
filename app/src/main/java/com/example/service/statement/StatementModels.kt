package com.example.service.statement

import com.example.data.bank.BankTxType
import java.util.UUID

/**
 * Represents a parsed transaction extracted from an imported bank/UPI statement.
 */
data class ImportedStatementTransaction(
    val id: String = UUID.randomUUID().toString(),
    val rawLine: String,
    val merchantOrName: String,
    val bankName: String,
    val accountLast4: String,
    val amount: Double,
    val type: BankTxType, // DEBIT or CREDIT
    val dateText: String,
    val timestamp: Long,
    val day: Int,
    val month: Int,
    val year: Int,
    val status: String, // SUCCESS, FAILED, etc.
    val isSuccess: Boolean,
    val referenceNumber: String = "",
    val category: String = "GENERAL",
    val paymentMode: String = "UPI", // UPI, NET_BANKING, CARD, etc.
    val sourceSystem: String = "STATEMENT" // SUPER_MONEY, PHONEPE, GPAY, HDFC, ICICI, SBI, AXIS, etc.
)

/**
 * Result of parsing a statement document.
 */
data class StatementParseResult(
    val sourceName: String,
    val totalRowsFound: Int,
    val successfulTransactions: List<ImportedStatementTransaction>,
    val failedOrSkippedTransactions: List<ImportedStatementTransaction>,
    val totalDebitAmount: Double,
    val totalCreditAmount: Double,
    val detectedSystem: String
)
