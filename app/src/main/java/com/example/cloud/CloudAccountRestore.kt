package com.example.cloud

import android.content.Context
import android.util.Log
import com.example.data.local.FinMoneyDao
import com.example.data.model.BankTransactionRecord
import com.example.data.model.CustomCategory
import com.example.data.model.CustomExpenseTag
import com.example.data.model.DebitItem
import com.example.data.model.LoanApprovalStatus
import com.example.data.model.LoanTransaction
import com.example.data.model.LoanType
import com.example.data.model.SalaryRecord

/**
 * Pulls the signed-in user's Firestore ledger back into Room after reinstall,
 * then uploads any local rows that are not yet in the cloud.
 */
object CloudAccountRestore {
    private const val TAG = "CloudAccountRestore"

    data class Result(
        val restoredExpenses: Int = 0,
        val restoredLoans: Int = 0,
        val restoredBankTx: Int = 0,
        val queuedUploads: Int = 0
    ) {
        fun totalRestored() = restoredExpenses + restoredLoans + restoredBankTx
        fun totalQueuedUpload() = queuedUploads
    }

    suspend fun syncAccount(
        context: Context,
        dao: FinMoneyDao,
        cloud: UserCloudDataRepository,
        user: CloudUserContext,
        onProgress: (CloudSyncProgress) -> Unit = {}
    ): Result {
        if (!user.canSyncToCloud) return Result()
        val uid = user.userId
        var restoredExpenses = 0
        var restoredLoans = 0
        var restoredBankTx = 0

        onProgress(CloudSyncProgress(CloudSyncStage.DOWNLOADING_EXPENSES, "Downloading expenses & bills…"))
        val expenses = cloud.listUserSubcollection(uid, "expenses")
        for ((docId, data) in expenses) {
            val cloudId = str(data["cloudId"]).ifBlank { docId }
            if (dao.getDebitByCloudId(cloudId) != null) continue
            dao.insertDebit(debitFromCloud(uid, cloudId, data))
            restoredExpenses++
        }

        onProgress(CloudSyncProgress(CloudSyncStage.DOWNLOADING_SALARY, "Downloading income & salary…"))
        val salaries = cloud.listUserSubcollection(uid, "salary_records")
        for ((_, data) in salaries) {
            val month = int(data["month"])
            val year = int(data["year"])
            if (month <= 0 || year <= 0) continue
            val existing = dao.getSalaryRecordDirect(month, year, uid)
            if (existing != null && existing.incomeSourcesJson.isNotBlank() && existing.incomeSourcesJson != "[]") {
                continue
            }
            dao.insertOrUpdateSalary(
                SalaryRecord(
                    id = existing?.id ?: 0L,
                    userId = uid,
                    month = month,
                    year = year,
                    monthYear = str(data["monthYear"]).ifBlank { "%04d-%02d".format(year, month) },
                    salaryAmount = num(data["salaryAmount"]),
                    additionalIncome = num(data["additionalIncome"]),
                    incomeSourcesJson = str(data["incomeSourcesJson"]).ifBlank { "[]" },
                    notes = str(data["notes"]),
                    updatedAt = lng(data["updatedAt"]).takeIf { it > 0 } ?: System.currentTimeMillis()
                )
            )
        }

        onProgress(CloudSyncProgress(CloudSyncStage.DOWNLOADING_CATEGORIES, "Downloading categories & tags…"))
        for ((_, data) in cloud.listUserSubcollection(uid, "custom_categories")) {
            val name = str(data["name"]).trim()
            if (name.isBlank()) continue
            if (dao.getCustomCategoryByName(name, uid) != null) continue
            dao.insertCustomCategory(
                CustomCategory(
                    userId = uid,
                    name = name,
                    displayName = str(data["displayName"]).ifBlank { name },
                    iconName = str(data["iconName"]).ifBlank { "Category" },
                    colorHex = str(data["colorHex"]).ifBlank { "#00B386" }
                )
            )
        }

        for ((_, data) in cloud.listUserSubcollection(uid, "custom_expense_tags")) {
            val name = str(data["name"]).trim()
            if (name.isBlank()) continue
            if (dao.getCustomExpenseTagByName(name, uid) != null) continue
            dao.insertCustomExpenseTag(
                CustomExpenseTag(
                    userId = uid,
                    name = name,
                    iconName = str(data["iconName"]).ifBlank { "LocalOffer" },
                    colorHex = str(data["colorHex"]).ifBlank { "#0066FF" }
                )
            )
        }

        onProgress(CloudSyncProgress(CloudSyncStage.DOWNLOADING_LOANS, "Downloading lent & borrowed…"))
        val loanDocs = cloud.listUserSubcollection(uid, "loans")
        for ((docId, data) in loanDocs) {
            val cloudId = str(data["cloudId"]).ifBlank { docId }
            if (dao.getLoanByCloudId(cloudId) != null) continue
            dao.insertLoan(loanFromCloud(uid, cloudId, data))
            restoredLoans++
        }

        onProgress(CloudSyncProgress(CloudSyncStage.DOWNLOADING_BANK, "Downloading bank activity…"))
        val bankDocs = cloud.listUserSubcollection(uid, "bank_transactions")
        val bankCloudIds = bankDocs.map { (id, data) -> str(data["cloudId"]).ifBlank { id } }.toSet()
        for ((docId, data) in bankDocs) {
            val cloudId = str(data["cloudId"]).ifBlank { docId }
            if (cloudId.isNotBlank() && dao.getBankTransactionByCloudId(cloudId) != null) continue
            val fingerprint = str(data["fingerprint"])
            if (fingerprint.isNotBlank() && dao.getBankTransactionByFingerprint(fingerprint) != null) continue
            dao.insertBankTransaction(bankTxFromCloud(uid, cloudId, data))
            restoredBankTx++
        }

        onProgress(CloudSyncProgress(CloudSyncStage.UPLOADING, "Uploading your latest changes…"))
        val expenseCloudIds = expenses.map { (id, data) -> str(data["cloudId"]).ifBlank { id } }.toSet()
        val loanCloudIds = loanDocs.map { (id, data) -> str(data["cloudId"]).ifBlank { id } }.toSet()
        var queued = 0
        for (item in dao.getAllDebitsDirect(uid)) {
            val cid = item.cloudId
            if (cid.isNotBlank() && expenseCloudIds.contains(cid)) continue
            LocalFirstCloudSync.queueExpense(context, user, item)
            queued++
        }
        for (record in dao.getAllSalaryRecordsDirect(uid)) {
            LocalFirstCloudSync.queueSalary(context, user, record)
            queued++
        }
        for (category in dao.getAllCustomCategoriesDirect(uid)) {
            LocalFirstCloudSync.queueCustomCategory(context, user, category)
            queued++
        }
        for (tag in dao.getAllCustomExpenseTagsDirect(uid)) {
            LocalFirstCloudSync.queueCustomExpenseTag(context, user, tag)
            queued++
        }
        for (loan in dao.getAllLoansDirect(uid)) {
            val cid = loan.cloudId.ifBlank { "loan_${loan.id}" }
            if (cid.isNotBlank() && loanCloudIds.contains(cid)) continue
            LocalFirstCloudSync.queueLoan(context, user, loan)
            queued++
        }
        for (bankTx in dao.getBankTransactionsDirect(uid)) {
            val cid = bankTx.cloudId.ifBlank { "bank_${bankTx.id}" }
            if (cid.isNotBlank() && bankCloudIds.contains(cid)) continue
            LocalFirstCloudSync.queueBankTransaction(context, user, bankTx)
            queued++
        }
        Log.i(
            TAG,
            "Account sync uid=$uid expenses=$restoredExpenses loans=$restoredLoans bank=$restoredBankTx queued=$queued"
        )
        return Result(restoredExpenses, restoredLoans, restoredBankTx, queued)
    }

    private fun debitFromCloud(uid: String, cloudId: String, data: Map<String, Any?>): DebitItem {
        val month = int(data["month"]).coerceAtLeast(1)
        val year = int(data["year"]).takeIf { it > 2000 } ?: 2026
        return DebitItem(
            userId = uid,
            cloudId = cloudId,
            month = month,
            year = year,
            monthYear = str(data["monthYear"]).ifBlank { "%04d-%02d".format(year, month) },
            category = str(data["category"]).ifBlank { "GENERAL" },
            title = str(data["title"]),
            amount = num(data["amount"]),
            dueDateDay = int(data["dueDateDay"]).coerceIn(1, 31),
            dueDate = lng(data["dueDate"]).takeIf { it > 0 },
            isPaid = bool(data["isPaid"]),
            isPaidViaCreditCard = bool(data["isPaidViaCreditCard"]),
            isPaidViaDebitCard = bool(data["isPaidViaDebitCard"]),
            notes = str(data["notes"]),
            customCategoryName = str(data["customCategoryName"]),
            isRecurring = bool(data["isRecurring"]),
            principalAmount = num(data["principalAmount"]),
            interestRate = num(data["interestRate"]),
            totalTenureMonths = int(data["totalTenureMonths"]),
            currentMonthTenure = int(data["currentMonthTenure"]).takeIf { it > 0 } ?: 1,
            createdAt = lng(data["createdAt"]).takeIf { it > 0 }
                ?: lng(data["timestamp"]).takeIf { it > 0 }
                ?: System.currentTimeMillis()
        )
    }

    private fun str(v: Any?) = v?.toString()?.trim().orEmpty()
    private fun num(v: Any?) = when (v) {
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull() ?: 0.0
        else -> 0.0
    }
    private fun int(v: Any?) = num(v).toInt()
    private fun lng(v: Any?) = when (v) {
        is Number -> v.toLong()
        is String -> v.toLongOrNull() ?: 0L
        else -> 0L
    }
    private fun bool(v: Any?) = v == true || v == "true" || v == 1 || v == 1L

    private fun loanFromCloud(uid: String, cloudId: String, data: Map<String, Any?>): LoanTransaction {
        return LoanTransaction(
            userId = uid,
            cloudId = cloudId,
            personName = str(data["personName"]),
            personContact = str(data["personContact"]),
            type = loanType(data["type"]),
            totalAmount = num(data["totalAmount"]),
            outstandingAmount = num(data["outstandingAmount"]),
            paidAmount = num(data["paidAmount"]),
            status = loanStatus(data["status"]),
            counterpartyApproved = bool(data["counterpartyApproved"]),
            amendmentPending = bool(data["amendmentPending"]),
            notes = str(data["notes"]),
            creatorPhone = str(data["creatorPhone"]),
            updatedAt = lng(data["updatedAt"]).takeIf { it > 0 } ?: System.currentTimeMillis(),
            createdAt = lng(data["createdAt"]).takeIf { it > 0 } ?: System.currentTimeMillis()
        )
    }

    private fun bankTxFromCloud(uid: String, cloudId: String, data: Map<String, Any?>): BankTransactionRecord {
        return BankTransactionRecord(
            userId = uid,
            cloudId = cloudId,
            bankName = str(data["bankName"]),
            accountNumberLast4 = str(data["accountNumberLast4"]),
            amount = num(data["amount"]),
            type = str(data["type"]).ifBlank { "DEBIT" },
            classification = str(data["classification"]).ifBlank { "Bank" },
            category = str(data["category"]).ifBlank { "General" },
            merchant = str(data["merchant"]),
            referenceNumber = str(data["referenceNumber"]),
            availableBalance = num(data["availableBalance"]),
            originalText = str(data["originalText"]),
            source = str(data["source"]).ifBlank { "NOTIFICATION" },
            timestamp = lng(data["timestamp"]).takeIf { it > 0 } ?: System.currentTimeMillis(),
            isConfirmed = bool(data["isConfirmed"]),
            status = str(data["status"]).ifBlank { "PENDING" },
            fingerprint = str(data["fingerprint"]),
            matchedDebitId = lng(data["matchedDebitId"]).takeIf { it > 0 },
            matchedDebitTitle = str(data["matchedDebitTitle"]).takeIf { it.isNotBlank() }
        )
    }

    private fun loanType(v: Any?): LoanType = try {
        LoanType.valueOf(str(v).uppercase())
    } catch (_: Exception) {
        LoanType.LENT
    }

    private fun loanStatus(v: Any?): LoanApprovalStatus = try {
        LoanApprovalStatus.valueOf(str(v).uppercase())
    } catch (_: Exception) {
        LoanApprovalStatus.PENDING
    }
}
