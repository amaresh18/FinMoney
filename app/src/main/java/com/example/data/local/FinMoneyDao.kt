package com.example.data.local

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FinMoneyDao {

    // --- User Profile ---
    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    fun getUserProfile(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    suspend fun getUserProfileDirect(): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateUserProfile(profile: UserProfile): Long

    // --- Salary Records ---
    @Query("SELECT * FROM salary_records WHERE month = :month AND year = :year ORDER BY id DESC LIMIT 1")
    fun getSalaryRecord(month: Int, year: Int): Flow<SalaryRecord?>

    @Query("SELECT * FROM salary_records WHERE month = :month AND year = :year ORDER BY id DESC LIMIT 1")
    suspend fun getSalaryRecordDirect(month: Int, year: Int): SalaryRecord?

    @Query("SELECT * FROM salary_records ORDER BY year DESC, month DESC")
    fun getAllSalaryRecords(): Flow<List<SalaryRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSalary(record: SalaryRecord): Long

    @Query("DELETE FROM salary_records WHERE month = :month AND year = :year")
    suspend fun deleteSalary(month: Int, year: Int)

    @Query("DELETE FROM salary_records")
    suspend fun clearAllSalaryRecords()

    @Query("SELECT COUNT(*) FROM debit_items")
    suspend fun getDebitsCount(): Int


    // --- Debit Items ---
    @Query("SELECT * FROM debit_items WHERE month = :month AND year = :year ORDER BY dueDateDay DESC, createdAt DESC, id DESC")
    fun getDebitsForMonth(month: Int, year: Int): Flow<List<DebitItem>>

    @Query("SELECT * FROM debit_items WHERE month = :month AND year = :year ORDER BY dueDateDay DESC, createdAt DESC, id DESC")
    suspend fun getDebitsForMonthDirect(month: Int, year: Int): List<DebitItem>

    @Query("SELECT * FROM debit_items WHERE id = :id LIMIT 1")
    suspend fun getDebitById(id: Long): DebitItem?

    @Query("SELECT * FROM debit_items WHERE cloudId = :cloudId AND cloudId != '' LIMIT 1")
    suspend fun getDebitByCloudId(cloudId: String): DebitItem?

    @Query("SELECT * FROM debit_items ORDER BY year DESC, month DESC")
    fun getAllDebits(): Flow<List<DebitItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebit(item: DebitItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebits(items: List<DebitItem>)

    @Query("SELECT * FROM debit_items WHERE isRecurring = 1")
    suspend fun getRecurringDebitsDirect(): List<DebitItem>

    @Query("SELECT * FROM debit_items WHERE isRecurring = 1")
    fun getRecurringDebitsFlow(): Flow<List<DebitItem>>

    @Update
    suspend fun updateDebit(item: DebitItem)

    @Delete
    suspend fun deleteDebit(item: DebitItem)

    @Query("DELETE FROM debit_items WHERE id = :id")
    suspend fun deleteDebitById(id: Long)

    @Query("DELETE FROM debit_items")
    suspend fun clearAllDebits()

    @Query("UPDATE debit_items SET isPaid = :isPaid WHERE id = :id")
    suspend fun setDebitPaidStatus(id: Long, isPaid: Boolean)

    // --- Custom Categories ---
    @Query("SELECT * FROM custom_categories ORDER BY id ASC")
    fun getAllCustomCategories(): Flow<List<CustomCategory>>

    @Query("SELECT * FROM custom_categories WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) LIMIT 1")
    suspend fun getCustomCategoryByName(name: String): CustomCategory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomCategory(category: CustomCategory): Long

    @Delete
    suspend fun deleteCustomCategory(category: CustomCategory)

    // --- Loan Transactions (Lent & Borrowed) ---
    @Query("SELECT * FROM loan_transactions ORDER BY createdAt DESC")
    fun getAllLoans(): Flow<List<LoanTransaction>>

    @Query("SELECT * FROM loan_transactions WHERE type = :type ORDER BY createdAt DESC")
    fun getLoansByType(type: String): Flow<List<LoanTransaction>>

    @Query("SELECT * FROM loan_transactions WHERE status = 'PENDING_APPROVAL' ORDER BY createdAt DESC")
    fun getPendingApprovalLoans(): Flow<List<LoanTransaction>>

    @Query("SELECT * FROM loan_transactions WHERE id = :id LIMIT 1")
    suspend fun getLoanById(id: Long): LoanTransaction?

    @Query("SELECT * FROM loan_transactions WHERE cloudId = :cloudId AND cloudId != '' LIMIT 1")
    suspend fun getLoanByCloudId(cloudId: String): LoanTransaction?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoan(loan: LoanTransaction): Long

    @Update
    suspend fun updateLoan(loan: LoanTransaction)

    @Query("DELETE FROM loan_transactions WHERE id = :id")
    suspend fun deleteLoanById(id: Long)

    @Query("DELETE FROM loan_transactions")
    suspend fun clearAllLoans()


    @Query("DELETE FROM loan_transactions WHERE cloudId = :cloudId AND cloudId != ''")
    suspend fun deleteLoanByCloudId(cloudId: String)

    @Query("UPDATE loan_transactions SET status = :status, counterpartyApproved = :counterpartyApproved, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateLoanApproval(id: Long, status: String, counterpartyApproved: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE loan_transactions SET status = 'SETTLED', settledAmount = totalAmount, updatedAt = :updatedAt WHERE id = :id")
    suspend fun settleLoan(id: Long, updatedAt: Long = System.currentTimeMillis())

    // --- App Notifications ---
    @Query("SELECT * FROM app_notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<AppNotification>>

    @Query("SELECT COUNT(*) FROM app_notifications WHERE isRead = 0")
    fun getUnreadNotificationCount(): Flow<Int>

    @Query("SELECT * FROM app_notifications WHERE cloudId = :cloudId AND cloudId != '' LIMIT 1")
    suspend fun getNotificationByCloudId(cloudId: String): AppNotification?

    @Query("SELECT * FROM app_notifications WHERE id = :id LIMIT 1")
    suspend fun getNotificationById(id: Long): AppNotification?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: AppNotification): Long

    @Query("UPDATE app_notifications SET isRead = 1 WHERE id = :id")
    suspend fun markNotificationRead(id: Long)

    @Query("SELECT * FROM app_notifications WHERE relatedLoanId = :loanId")
    suspend fun getNotificationsForLoan(loanId: Long): List<AppNotification>

    @Query("UPDATE app_notifications SET isRead = 1")
    suspend fun markAllNotificationsRead()

    @Query("UPDATE app_notifications SET isRead = 1, actionType = 'SYSTEM' WHERE relatedLoanId = :loanId")
    suspend fun markNotificationsReadForLoan(loanId: Long)

    @Query("UPDATE app_notifications SET relatedLoanId = :loanId WHERE relatedLoanId IS NULL AND cloudId = :cloudId")
    suspend fun updateNotificationRelatedLoanId(cloudId: String, loanId: Long)

    @Query("DELETE FROM app_notifications WHERE id = :id")
    suspend fun deleteNotification(id: Long)

    // --- Monthly Reconciliations ---
    @Query("SELECT * FROM monthly_reconciliations WHERE month = :month AND year = :year ORDER BY id DESC LIMIT 1")
    fun getReconciliationForMonth(month: Int, year: Int): Flow<MonthlyReconciliation?>

    @Query("SELECT * FROM monthly_reconciliations WHERE month = :month AND year = :year ORDER BY id DESC LIMIT 1")
    suspend fun getReconciliationForMonthDirect(month: Int, year: Int): MonthlyReconciliation?

    @Query("SELECT * FROM monthly_reconciliations WHERE cloudId = :cloudId AND cloudId != '' LIMIT 1")
    suspend fun getReconciliationByCloudId(cloudId: String): MonthlyReconciliation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateReconciliation(reconciliation: MonthlyReconciliation): Long

    // --- Bank Transactions & Auto-Sync ---
    @Query("SELECT * FROM bank_transactions ORDER BY timestamp DESC")
    fun getAllBankTransactions(): Flow<List<BankTransactionRecord>>

    @Query("SELECT * FROM bank_transactions ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentBankTransactions(limit: Int = 50): Flow<List<BankTransactionRecord>>

    @Query("SELECT * FROM bank_transactions ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentBankTransactionsDirect(limit: Int = 50): List<BankTransactionRecord>

    @Query("SELECT * FROM bank_transactions WHERE type = 'DEBIT' ORDER BY timestamp DESC")
    fun getDebitBankTransactions(): Flow<List<BankTransactionRecord>>

    @Query("SELECT * FROM bank_transactions WHERE type = 'CREDIT' ORDER BY timestamp DESC")
    fun getCreditBankTransactions(): Flow<List<BankTransactionRecord>>

    @Query("SELECT * FROM bank_transactions WHERE isConfirmed = 0 ORDER BY timestamp DESC")
    fun getPendingBankTransactions(): Flow<List<BankTransactionRecord>>

    @Query("SELECT * FROM bank_transactions WHERE isConfirmed = 0 ORDER BY timestamp DESC")
    suspend fun getPendingBankTransactionsDirect(): List<BankTransactionRecord>

    @Query("SELECT * FROM bank_transactions WHERE id = :id LIMIT 1")
    suspend fun getBankTransactionById(id: Long): BankTransactionRecord?

    @Query("SELECT * FROM bank_transactions WHERE referenceNumber = :ref AND referenceNumber != '' LIMIT 1")
    suspend fun getBankTransactionByReference(ref: String): BankTransactionRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBankTransaction(transaction: BankTransactionRecord): Long

    @Query("UPDATE bank_transactions SET isConfirmed = :confirmed WHERE id = :id")
    suspend fun setBankTransactionConfirmed(id: Long, confirmed: Boolean)

    @Query("UPDATE bank_transactions SET isConfirmed = :confirmed, status = :status WHERE id = :id")
    suspend fun updateBankTransactionStatus(id: Long, confirmed: Boolean, status: String)

    @Query("SELECT * FROM bank_transactions WHERE fingerprint = :fingerprint AND fingerprint != '' LIMIT 1")
    suspend fun getBankTransactionByFingerprint(fingerprint: String): BankTransactionRecord?

    @Query("SELECT * FROM bank_transactions WHERE status = 'REJECTED'")
    suspend fun getRejectedBankTransactions(): List<BankTransactionRecord>

    @Query("DELETE FROM bank_transactions WHERE id = :id")
    suspend fun deleteBankTransaction(id: Long)

    @Query("DELETE FROM bank_transactions")
    suspend fun clearAllBankTransactions()

    // --- Raw Notifications Log ---
    @Query("SELECT * FROM raw_notifications ORDER BY timestamp DESC")
    fun getAllRawNotifications(): Flow<List<RawNotificationRecord>>

    @Query("SELECT * FROM raw_notifications WHERE isFlagged = 1 ORDER BY timestamp DESC")
    fun getFlaggedRawNotifications(): Flow<List<RawNotificationRecord>>

    @Query("SELECT * FROM raw_notifications WHERE needsUserReview = 1 ORDER BY timestamp DESC")
    fun getNeedsReviewNotifications(): Flow<List<RawNotificationRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRawNotification(record: RawNotificationRecord): Long

    @Query("UPDATE raw_notifications SET isFlagged = :isFlagged, flaggedReason = :reason, parseStatus = :parseStatus WHERE id = :id")
    suspend fun updateRawNotificationFlag(id: Long, isFlagged: Boolean, reason: String?, parseStatus: String)

    @Query("UPDATE raw_notifications SET needsUserReview = :needsReview, parseStatus = :parseStatus WHERE id = :id")
    suspend fun updateRawNotificationReview(id: Long, needsReview: Boolean, parseStatus: String)

    @Query("DELETE FROM raw_notifications WHERE id = :id")
    suspend fun deleteRawNotification(id: Long)

    @Query("DELETE FROM raw_notifications")
    suspend fun clearAllRawNotifications()
}
