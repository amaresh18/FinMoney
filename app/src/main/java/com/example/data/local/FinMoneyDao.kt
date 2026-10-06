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

    // --- Debit Items ---
    @Query("SELECT * FROM debit_items WHERE month = :month AND year = :year ORDER BY dueDateDay ASC, id ASC")
    fun getDebitsForMonth(month: Int, year: Int): Flow<List<DebitItem>>

    @Query("SELECT * FROM debit_items WHERE month = :month AND year = :year ORDER BY dueDateDay ASC, id ASC")
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

    @Update
    suspend fun updateDebit(item: DebitItem)

    @Delete
    suspend fun deleteDebit(item: DebitItem)

    @Query("DELETE FROM debit_items WHERE id = :id")
    suspend fun deleteDebitById(id: Long)

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: AppNotification): Long

    @Query("UPDATE app_notifications SET isRead = 1 WHERE id = :id")
    suspend fun markNotificationRead(id: Long)

    @Query("UPDATE app_notifications SET isRead = 1")
    suspend fun markAllNotificationsRead()

    @Query("DELETE FROM app_notifications WHERE id = :id")
    suspend fun deleteNotification(id: Long)
}
