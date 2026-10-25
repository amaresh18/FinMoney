package com.example.cloud

import android.content.Context
import com.example.data.model.BankTransactionRecord
import com.example.data.model.DebitItem
import com.example.data.model.IncomeSource
import com.example.data.model.LoanLedgerEntry
import com.example.data.model.LoanTransaction
import com.example.data.model.SalaryRecord

/**
 * Maps Room rows to outbox payloads after the local write has already succeeded.
 */
object LocalFirstCloudSync {

    suspend fun queueBankTransaction(context: Context, cloudUser: CloudUserContext, record: BankTransactionRecord) {
        val docId = record.cloudId.ifBlank { "bank_${record.id}" }
        CloudSyncOutboxManager.enqueueAndSchedule(
            context = context,
            cloudUser = cloudUser,
            subcollection = "bank_transactions",
            documentId = docId,
            data = mapOf(
                "cloudId" to docId,
                "localBankTxId" to record.id,
                "status" to record.status,
                "bankName" to record.bankName,
                "accountNumberLast4" to record.accountNumberLast4,
                "amount" to record.amount,
                "type" to record.type,
                "classification" to record.classification,
                "category" to record.category,
                "merchant" to record.merchant,
                "referenceNumber" to record.referenceNumber,
                "availableBalance" to record.availableBalance,
                "originalText" to record.originalText,
                "source" to record.source,
                "timestamp" to record.timestamp,
                "isConfirmed" to record.isConfirmed,
                "matchedDebitId" to record.matchedDebitId,
                "matchedDebitTitle" to record.matchedDebitTitle,
                "fingerprint" to record.fingerprint
            ),
            legacyCollections = listOf("bank_transactions")
        )
    }

    suspend fun queueExpense(
        context: Context,
        cloudUser: CloudUserContext,
        item: DebitItem,
        bankTx: BankTransactionRecord? = null
    ) {
        val docId = item.cloudId.ifBlank { "debit_${item.id}" }
        CloudSyncOutboxManager.enqueueAndSchedule(
            context = context,
            cloudUser = cloudUser,
            subcollection = "expenses",
            documentId = docId,
            data = mapOf(
                "cloudId" to docId,
                "localDebitId" to item.id,
                "month" to item.month,
                "year" to item.year,
                "monthYear" to item.monthYear,
                "category" to item.category,
                "title" to item.title,
                "amount" to item.amount,
                "dueDateDay" to item.dueDateDay,
                "dueDate" to item.dueDate,
                "timestamp" to item.createdAt,
                "isPaid" to item.isPaid,
                "isPaidViaCreditCard" to item.isPaidViaCreditCard,
                "isPaidViaDebitCard" to item.isPaidViaDebitCard,
                "notes" to item.notes,
                "transactionType" to "EXPENSE",
                "bankTxCloudId" to (bankTx?.cloudId ?: ""),
                "sourceBank" to (bankTx?.bankName ?: ""),
                "referenceNumber" to (bankTx?.referenceNumber ?: "")
            ),
            legacyCollections = listOf("expenses", "debit_items")
        )
    }

    suspend fun queueIncome(
        context: Context,
        cloudUser: CloudUserContext,
        income: IncomeSource,
        month: Int,
        year: Int,
        bankTx: BankTransactionRecord? = null
    ) {
        val docId = income.id.ifBlank { "income_${month}_${year}_${income.amount}" }
        CloudSyncOutboxManager.enqueueAndSchedule(
            context = context,
            cloudUser = cloudUser,
            subcollection = "incomes",
            documentId = docId,
            data = mapOf(
                "id" to docId,
                "name" to income.name,
                "title" to income.title,
                "amount" to income.amount,
                "category" to income.category,
                "isReceived" to income.isReceived,
                "month" to month,
                "year" to year,
                "receivedDateTimestamp" to income.receivedDateTimestamp,
                "bankTxCloudId" to (bankTx?.cloudId ?: ""),
                "transactionType" to "INCOME"
            ),
            legacyCollections = listOf("incomes", "income_records")
        )
    }

    suspend fun queueSalary(context: Context, cloudUser: CloudUserContext, record: SalaryRecord) {
        val docId = "salary_${cloudUser.userId}_${record.month}_${record.year}"
        CloudSyncOutboxManager.enqueueAndSchedule(
            context = context,
            cloudUser = cloudUser,
            subcollection = "salary_records",
            documentId = docId,
            data = mapOf(
                "month" to record.month,
                "year" to record.year,
                "monthYear" to record.monthYear,
                "salaryAmount" to record.salaryAmount,
                "additionalIncome" to record.additionalIncome,
                "notes" to record.notes,
                "incomeSourcesJson" to record.incomeSourcesJson,
                "updatedAt" to record.updatedAt
            ),
            legacyCollections = listOf("salary_records")
        )
    }

    suspend fun queueLoan(context: Context, cloudUser: CloudUserContext, loan: LoanTransaction) {
        val docId = loan.cloudId.ifBlank { "loan_${loan.id}" }
        CloudSyncOutboxManager.enqueueAndSchedule(
            context = context,
            cloudUser = cloudUser,
            subcollection = "loans",
            documentId = docId,
            data = mapOf(
                "localLoanId" to loan.id,
                "personName" to loan.personName,
                "personContact" to loan.personContact,
                "type" to loan.type.name,
                "totalAmount" to loan.totalAmount,
                "outstandingAmount" to loan.outstandingAmount,
                "paidAmount" to loan.paidAmount,
                "status" to loan.status.name,
                "counterpartyApproved" to loan.counterpartyApproved,
                "amendmentPending" to loan.amendmentPending,
                "notes" to loan.notes,
                "creatorPhone" to loan.creatorPhone,
                "updatedAt" to loan.updatedAt
            ),
            legacyCollections = listOf("loans")
        )
    }

    suspend fun queueLedgerEntry(context: Context, cloudUser: CloudUserContext, entry: LoanLedgerEntry) {
        val docId = "loan_entry_${entry.loanId}_${entry.id}"
        CloudSyncOutboxManager.enqueueAndSchedule(
            context = context,
            cloudUser = cloudUser,
            subcollection = "loan_ledger",
            documentId = docId,
            data = mapOf(
                "localEntryId" to entry.id,
                "loanId" to entry.loanId,
                "kind" to entry.kind.name,
                "amount" to entry.amount,
                "status" to entry.status.name,
                "notes" to entry.notes,
                "amendmentPending" to entry.amendmentPending,
                "updatedAt" to entry.updatedAt
            ),
            legacyCollections = emptyList()
        )
    }
}
