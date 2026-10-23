package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DebitCategory {
    GROCERIES, UTILITIES, RENT, EMI, LOAN, SUBSCRIPTION, DINING, TRANSPORT, SHOPPING, INVESTMENT, GENERAL,
    CHIT, INSURANCE, SCHOOL_FEES, HOSTEL_FEES, MAID, MAINTENANCE, ELECTRICITY, INTERNET, RECHARGE,
    OUTSIDE_FOOD, MILK, NEWSPAPER, OTT, PHARMACY, TRAVEL, TOURS, FUEL,
    SAVING, DEBT_REPAY, LENDING, DONATION, CUSTOM
}

data class BankBalanceItem(
    val id: String = "",
    val bankName: String = "",
    val accountNumberLast4: String = "",
    val startingBalance: Double = 0.0,
    val endingBalance: Double = 0.0,
    val availableBalance: Double = 0.0,
    val notes: String = "",
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
)

enum class IncomeCategory {
    SALARY, FREELANCE, BUSINESS, RENT, INVESTMENT, DIVIDEND, REFUND, GIFT, OTHER
}

data class CategorySummary(
    val categoryName: String = "",
    val categoryKey: String = "",
    val iconKey: String = "",
    val totalAmount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val yetToPayAmount: Double = 0.0,
    val itemCount: Int = 0,
    val paidCount: Int = 0,
    val percentage: Float = 0f,
    val colorHex: String = "#00B386"
)

data class MonthlyFinanceSummary(
    val monthYear: String = "2026-10",
    val totalIncome: Double = 0.0,
    val totalDebits: Double = 0.0,
    val totalPaid: Double = 0.0,
    val totalYetToPay: Double = 0.0,
    val flexibleCash: Double = 0.0,
    val netFlexibleSpending: Double = 0.0,
    val dailySafeSpend: Double = 0.0,
    val currentBankBalance: Double = 0.0,
    val daysRemainingInMonth: Int = 30,
    val incomeSources: List<IncomeSource> = emptyList(),
    val creditCardDebits: List<DebitItem> = emptyList(),
    val categoryBreakdown: List<CategorySummary> = emptyList(),
    val totalBankDebits: Double = 0.0,
    val totalCcDebits: Double = 0.0,
    val totalPaidBankDebits: Double = 0.0,
    val totalPaidCcDebits: Double = 0.0
)

data class ReconciliationSummary(
    val totalInflows: Double = 0.0,
    val totalOutflows: Double = 0.0,
    val totalPaidOutflows: Double = 0.0,
    val totalYetToPayOutflows: Double = 0.0,
    val totalBankDebits: Double = 0.0,
    val totalCcDebits: Double = 0.0,
    val totalPaidBankDebits: Double = 0.0,
    val totalPaidCcDebits: Double = 0.0,
    val netBalance: Double = 0.0,
    val incomeSourcesCount: Int = 0,
    val bankDebitsCount: Int = 0,
    val ccDebitsCount: Int = 0,
    val paidBankDebitsCount: Int = 0,
    val paidCcDebitsCount: Int = 0
)

data class IncomeSource(
    val id: String = "",
    val title: String = "",
    val name: String = "",
    val amount: Double = 0.0,
    val category: String = "General",
    val isReceived: Boolean = false,
    val isRecurring: Boolean = false,
    val receivedDateTimestamp: Long? = null
)

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Long = 1L,
    val name: String = "You",
    val email: String = "",
    val phoneNumber: String = "",
    val profilePicUri: String? = null,
    val isOtpVerified: Boolean = true,
    val isEmailVerified: Boolean = true,
    val monthlyBudget: Double = 0.0,
    val currencySymbol: String = "₹",
    val profileImageUri: String? = null
)

@Entity(tableName = "salary_records")
data class SalaryRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val month: Int = 10,
    val year: Int = 2026,
    val monthYear: String = "2026-10",
    val baseSalary: Double = 0.0,
    val salaryAmount: Double = 0.0,
    val additionalIncome: Double = 0.0,
    val incomeSourcesJson: String = "[]",
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "debit_items")
data class DebitItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val cloudId: String = "",
    val month: Int = 10,
    val year: Int = 2026,
    val monthYear: String = "2026-10",
    val category: String = "General",
    val title: String = "",
    val amount: Double = 0.0,
    val dueDateDay: Int = 1,
    val dueDate: Long? = null,
    val isPaid: Boolean = false,
    val isPaidViaDebitCard: Boolean = false,
    val isPaidViaCreditCard: Boolean = false,
    val paidDateTimestamp: Long? = null,
    val paidDate: Long? = null,
    val interestRatePercent: Double = 0.0,
    val notes: String = "",
    val customCategoryName: String = "",
    val principalAmount: Double = 0.0,
    val interestRate: Double = 0.0,
    val totalTenureMonths: Int = 0,
    val currentMonthTenure: Int = 0,
    val isRecurring: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

fun DebitItem.isActualCreditCardTransaction(): Boolean =
    isPaidViaCreditCard || category.equals("Credit Card", ignoreCase = true)

@Entity(tableName = "custom_categories")
data class CustomCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String = "",
    val iconName: String = "Category",
    val isDefault: Boolean = false,
    val colorHex: String = "#00B386",
    val displayName: String = ""
)

enum class LoanType {
    LENT,
    BORROWED,
    GIVEN,
    TAKEN
}

enum class LoanApprovalStatus {
    PENDING,
    MUTUAL_APPROVED,
    APPROVED,
    SETTLED,
    REJECTED,
    PENDING_APPROVAL,
    PENDING_SETTLEMENT
}

typealias ApprovalStatus = LoanApprovalStatus

@Entity(tableName = "loan_transactions")
data class LoanTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val cloudId: String = "",
    val personName: String = "",
    val personContact: String = "",
    val type: LoanType = LoanType.LENT,
    val totalAmount: Double = 0.0,
    val settledAmount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val interestRatePercent: Double = 0.0,
    val principalAmount: Double = 0.0,
    val isMonthlyInterest: Boolean = false,
    val startDateTimestamp: Long = System.currentTimeMillis(),
    val counterpartyName: String = "",
    val counterpartyContact: String = "",
    val status: LoanApprovalStatus = LoanApprovalStatus.PENDING,
    val counterpartyApproved: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val dueDateTimestamp: Long? = null,
    val dueDate: Long? = null,
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    /** Local copies of image/PDF proofs, one path per line. */
    val proofPaths: String = "",
    /** Frozen text of the mutual agreement after both sides accept. */
    val agreementText: String = ""
)

@Entity(tableName = "app_notifications")
data class AppNotification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val cloudId: String = "",
    val title: String = "",
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = "GENERAL",
    val actionType: String = "NONE",
    val isRead: Boolean = false,
    val relatedLoanId: Long? = null,
    val referenceId: Long = 0L
)

@Entity(tableName = "monthly_reconciliations")
data class MonthlyReconciliation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val cloudId: String = "",
    val month: Int = 10,
    val year: Int = 2026,
    val bankBalancesJson: String = "{}",
    val manualAdjustment: Double = 0.0,
    val adjustmentNote: String = "",
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "bank_transactions")
data class BankTransactionRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val cloudId: String = "",
    val bankName: String = "",
    val accountNumberLast4: String = "",
    val amount: Double = 0.0,
    val availableBalance: Double = 0.0,
    val matchedDebitId: Long? = null,
    val matchedDebitTitle: String? = null,
    val isDuplicate: Boolean = false,
    val duplicateReason: String? = null,
    val isIncorrect: Boolean = false,
    val incorrectReason: String? = null,
    val type: String = "DEBIT",
    val classification: String = "Bank",
    val category: String = "General",
    val merchant: String = "",
    val referenceNumber: String = "",
    val originalText: String = "",
    val source: String = "NOTIFICATION",
    val timestamp: Long = System.currentTimeMillis(),
    val isConfirmed: Boolean = false,
    val status: String = "PENDING", // PENDING, APPROVED, REJECTED
    val fingerprint: String = "",
    val sbnKey: String = ""
)

fun BankTransactionRecord.isCreditCardRecord(): Boolean =
    classification.equals("Credit Card", ignoreCase = true) || bankName.contains("Credit Card", ignoreCase = true)

@Entity(tableName = "raw_notifications")
data class RawNotificationRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val packageName: String = "",
    val title: String = "",
    val body: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isMonitoredPkg: Boolean = false,
    val isFinancialContent: Boolean = false,
    val parsedBankName: String? = null,
    val parsedAmount: Double? = null,
    val parsedType: String? = null,
    val parseStatus: String = "PENDING",
    val isFlagged: Boolean = false,
    val flaggedReason: String? = null,
    val needsUserReview: Boolean = false
)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val amount: Double,
    val type: String,
    val category: String,
    val date: Long,
    val note: String = ""
)

@Entity(tableName = "planned_debits")
data class PlannedDebitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val amount: Double,
    val category: String,
    val dueDate: String,
    val isPaid: Boolean = false,
    val loanAmount: Double = 0.0,
    val interestRate: Double = 0.0,
    val tenureMonths: Int = 0
)

@Entity(tableName = "loans")
data class LoanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val counterpartyName: String,
    val amount: Double,
    val type: String,
    val status: String,
    val date: Long,
    val note: String = "",
    val counterpartySigned: Boolean = false
)
