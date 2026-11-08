package com.example.learning

import android.content.Context
import android.util.Log
import com.example.data.local.FinMoneyDao
import com.example.data.model.BankTransactionRecord
import com.example.data.model.DebitItem
import com.example.data.model.IncomeSource
import com.example.data.model.LoanApprovalStatus
import com.example.data.model.LoanLedgerEntry
import com.example.data.model.LoanLedgerKind
import com.example.data.model.LoanTransaction
import com.example.data.model.LoanType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DueItemType {
    DUE_EXPENSE,           // Unpaid DebitItem
    LENT_MONEY_RECOVERY,   // Lent money owed to user (Credit received)
    BORROWED_MONEY_REPAY,  // Borrowed money user owes (Debit paid)
    PENDING_INCOME         // Unreceived IncomeSource
}

data class DuePaymentMatchCandidate(
    val id: String, // "debit_123", "loan_456", "income_789"
    val itemType: DueItemType,
    val title: String,
    val personOrCategoryName: String = "",
    val amount: Double,
    val dateInfo: String,
    val targetDebit: DebitItem? = null,
    val targetLoan: LoanTransaction? = null,
    val targetIncome: IncomeSource? = null
)

data class DuePaymentMatchPrompt(
    val transaction: BankTransactionRecord,
    val candidate: DuePaymentMatchCandidate
)

/**
 * Axio-style Due Payment Match Engine:
 * When a debit or credit happens with the same amount as a pending/due expense,
 * lent money record, or pending income, detects the match and asks the user for confirmation.
 */
object DuePaymentMatchEngine {

    private const val TAG = "DuePaymentMatchEngine"
    private val dateFormatter = SimpleDateFormat("dd MMM", Locale.getDefault())

    /**
     * Evaluates a bank transaction (DEBIT or CREDIT) against unpaid/due expenses,
     * lent/borrowed money records, and pending income sources with matching amounts.
     */
    suspend fun findMatchingDueItem(
        dao: FinMoneyDao,
        tx: BankTransactionRecord,
        userId: String = ""
    ): DuePaymentMatchPrompt? {
        val txAmount = tx.amount
        if (txAmount <= 0.0) return null

        val isDebit = tx.type.equals("DEBIT", ignoreCase = true)
        val isCredit = tx.type.equals("CREDIT", ignoreCase = true)

        val activeUserId = userId.ifBlank { tx.userId }

        if (isDebit) {
            // 1. Check for unpaid/due expenses with the same amount
            val allDebits = try {
                dao.getAllDebitsDirect(activeUserId)
            } catch (_: Exception) {
                emptyList()
            }

            val matchingDebit = allDebits.firstOrNull { debit ->
                !debit.isPaid && Math.abs(debit.amount - txAmount) < 2.0
            }

            if (matchingDebit != null) {
                val cal = java.util.Calendar.getInstance()
                cal.set(matchingDebit.year, matchingDebit.month - 1, matchingDebit.dueDateDay)
                val candidate = DuePaymentMatchCandidate(
                    id = "debit_${matchingDebit.id}",
                    itemType = DueItemType.DUE_EXPENSE,
                    title = matchingDebit.title.ifBlank { matchingDebit.category },
                    personOrCategoryName = matchingDebit.category,
                    amount = matchingDebit.amount,
                    dateInfo = "Due on ${dateFormatter.format(cal.time)}",
                    targetDebit = matchingDebit
                )
                return DuePaymentMatchPrompt(transaction = tx, candidate = candidate)
            }

            // 2. Check for borrowed money (repayment) with the same amount
            val allLoans = try {
                dao.getAllLoansDirect(activeUserId)
            } catch (_: Exception) {
                emptyList()
            }

            val matchingBorrowedLoan = allLoans.firstOrNull { loan ->
                (loan.type == LoanType.BORROWED || loan.type == LoanType.TAKEN) &&
                        loan.status != LoanApprovalStatus.SETTLED &&
                        loan.status != LoanApprovalStatus.REJECTED &&
                        (Math.abs(loan.outstandingAmount - txAmount) < 2.0 || Math.abs(loan.totalAmount - txAmount) < 2.0)
            }

            if (matchingBorrowedLoan != null) {
                val candidate = DuePaymentMatchCandidate(
                    id = "loan_${matchingBorrowedLoan.id}",
                    itemType = DueItemType.BORROWED_MONEY_REPAY,
                    title = "Borrowed Money Repayment",
                    personOrCategoryName = matchingBorrowedLoan.personName.ifBlank { matchingBorrowedLoan.counterpartyName },
                    amount = matchingBorrowedLoan.totalAmount,
                    dateInfo = "Borrowed from ${matchingBorrowedLoan.personName.ifBlank { "Lender" }}",
                    targetLoan = matchingBorrowedLoan
                )
                return DuePaymentMatchPrompt(transaction = tx, candidate = candidate)
            }
        } else if (isCredit) {
            // 1. Check for lent money recovery (credit received) with the same amount
            val allLoans = try {
                dao.getAllLoansDirect(activeUserId)
            } catch (_: Exception) {
                emptyList()
            }

            val matchingLentLoan = allLoans.firstOrNull { loan ->
                (loan.type == LoanType.LENT || loan.type == LoanType.GIVEN) &&
                        loan.status != LoanApprovalStatus.SETTLED &&
                        loan.status != LoanApprovalStatus.REJECTED &&
                        (Math.abs(loan.outstandingAmount - txAmount) < 2.0 || Math.abs(loan.totalAmount - txAmount) < 2.0)
            }

            if (matchingLentLoan != null) {
                val candidate = DuePaymentMatchCandidate(
                    id = "loan_${matchingLentLoan.id}",
                    itemType = DueItemType.LENT_MONEY_RECOVERY,
                    title = "Lent Money Recovery",
                    personOrCategoryName = matchingLentLoan.personName.ifBlank { matchingLentLoan.counterpartyName },
                    amount = matchingLentLoan.totalAmount,
                    dateInfo = "Lent to ${matchingLentLoan.personName.ifBlank { "Borrower" }}",
                    targetLoan = matchingLentLoan
                )
                return DuePaymentMatchPrompt(transaction = tx, candidate = candidate)
            }
        }

        return null
    }

    /**
     * Executes settlement/payment confirmation on the candidate item.
     */
    suspend fun executeSettleAndApprove(
        context: Context,
        dao: FinMoneyDao,
        prompt: DuePaymentMatchPrompt,
        userId: String = ""
    ): String {
        val tx = prompt.transaction
        val candidate = prompt.candidate

        return when (candidate.itemType) {
            DueItemType.DUE_EXPENSE -> {
                candidate.targetDebit?.let { debit ->
                    dao.setDebitPaidStatus(debit.id, true)
                }
                // Mark transaction as confirmed & approved
                dao.updateBankTransactionStatus(tx.id, confirmed = true, status = "APPROVED")
                "Marked due expense '${candidate.title}' as PAID!"
            }
            DueItemType.LENT_MONEY_RECOVERY, DueItemType.BORROWED_MONEY_REPAY -> {
                candidate.targetLoan?.let { loan ->
                    dao.insertLedgerEntry(
                        LoanLedgerEntry(
                            loanId = loan.id,
                            userId = loan.userId,
                            proposerPhone = loan.creatorPhone,
                            proposerIsCreator = true,
                            kind = LoanLedgerKind.CLEARING,
                            amount = tx.amount,
                            notes = "Settled via incoming ${tx.type} alert of ₹${tx.amount} (${tx.merchant.ifBlank { tx.bankName }})",
                            status = LoanApprovalStatus.MUTUAL_APPROVED,
                            agreementText = "Payment of ₹${tx.amount} recognized automatically.",
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    dao.settleLoan(loan.id)
                }
                dao.updateBankTransactionStatus(tx.id, confirmed = true, status = "APPROVED")
                val typeDesc = if (candidate.itemType == DueItemType.LENT_MONEY_RECOVERY) "Lent money recovery" else "Borrowed money repayment"
                "Settled $typeDesc with ${candidate.personOrCategoryName}!"
            }
            DueItemType.PENDING_INCOME -> {
                dao.updateBankTransactionStatus(tx.id, confirmed = true, status = "APPROVED")
                "Recorded credit of ₹${tx.amount} as income!"
            }
        }
    }
}
