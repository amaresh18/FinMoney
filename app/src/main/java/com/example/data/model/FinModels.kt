package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

enum class DebitCategory(val displayName: String, val iconKey: String) {
    RENT("Rent / Housing", "home"),
    EMI("Loan / EMI", "emi"),
    ELECTRICITY("Electricity", "flash"),
    INTERNET("Internet & WiFi", "internet"),
    SHOPPING("Shopping", "shopping"),
    INVESTMENT("Investment / SIP", "investment"),
    SAVING("Saving / RD", "saving"),
    GENERAL("General Expenses", "general"),
    CUSTOM("Custom Category", "custom")
}

enum class IncomeCategory(val displayName: String, val iconKey: String) {
    SALARY("Primary Salary", "salary"),
    RENT("Rental Income", "rent_income"),
    INTEREST("Interest / Dividends", "interest"),
    FREELANCE("Business / Freelance", "freelance"),
    OTHER("Other Credit", "other_income")
}

@JsonClass(generateAdapter = true)
data class IncomeSource(
    val name: String,
    val amount: Double,
    val category: String = IncomeCategory.OTHER.name
)

@Entity(tableName = "salary_records")
data class SalaryRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val month: Int, // 1 to 12
    val year: Int,  // e.g. 2026
    val salaryAmount: Double,
    val additionalIncome: Double = 0.0,
    val currencySymbol: String = "₹",
    val notes: String = "",
    val incomeSourcesJson: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "debit_items")
data class DebitItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cloudId: String = "",
    val month: Int,
    val year: Int,
    val category: String, // from DebitCategory.name or CUSTOM
    val customCategoryName: String = "",
    val title: String,
    val amount: Double,
    val dueDateDay: Int = 1, // 1st to 31st
    val isRecurring: Boolean = true,
    val isPaid: Boolean = false,
    val interestRate: Double = 0.0,
    val totalTenureMonths: Int = 0,
    val currentMonthTenure: Int = 0,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "custom_categories")
data class CustomCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconName: String = "category",
    val colorHex: String = "#00D09C",
    val isDefault: Boolean = false
)

enum class LoanType {
    GIVEN, // Money given to someone (Lent / Owed to me)
    TAKEN  // Money taken from someone (Borrowed / I Owe)
}

enum class ApprovalStatus {
    PENDING_APPROVAL,
    APPROVED,
    PENDING_SETTLEMENT,
    SETTLED,
    REJECTED
}

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Long = 1,
    val name: String = "You",
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val profilePicUri: String = "",
    val isOtpVerified: Boolean = false,
    val isEmailVerified: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class PaymentRecord(
    val id: String = java.util.UUID.randomUUID().toString(),
    val amount: Double,
    val paymentDateTimestamp: Long = System.currentTimeMillis(),
    val note: String = "",
    val proofUri: String = "",
    val recordedBy: String = "You",
    val isSettlement: Boolean = false,
    val isApproved: Boolean = true,
    val approvedBy: String = "",
    val deletionRequestedBy: String = "",
    val paymentTarget: String = "COMBINED",
    val proofFilesJson: String = ""
)

@Entity(tableName = "loan_transactions")
data class LoanTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cloudId: String = "",
    val type: String, // LoanType.name: GIVEN or TAKEN
    val totalAmount: Double,
    val counterpartyName: String,
    val counterpartyContact: String = "",
    val creatorContact: String = "",
    val interestRatePercent: Double = 0.0,
    val isMonthlyInterest: Boolean = true,
    val startDateTimestamp: Long = System.currentTimeMillis(),
    val dueDateTimestamp: Long? = null,
    val note: String = "",
    val proofUri: String = "", // Payment proof (e.g. PhonePe screenshot URI)
    val userProfilePicUri: String = "",
    val counterpartyProfilePicUri: String = "",
    val paymentHistoryJson: String = "", // JSON list of PaymentRecord
    val status: String = ApprovalStatus.PENDING_APPROVAL.name,
    val createdBy: String = "You",
    val userApproved: Boolean = true,
    val counterpartyApproved: Boolean = false,
    val userSigned: Boolean = true,
    val counterpartySigned: Boolean = false,
    val userSignature: String = "",
    val counterpartySignature: String = "",
    val pendingEditJson: String = "",
    val editRequestedBy: String = "",
    val deletionRequestedBy: String = "",
    val settlementRequestedBy: String = "",
    val settlementProofUri: String = "",
    val proofFilesJson: String = "",
    val settledAmount: Double = 0.0,
    val remindedAtTimestamp: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_notifications")
data class AppNotification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cloudId: String = "",
    val targetPhone: String = "",
    val senderPhone: String = "",
    val senderName: String = "",
    val title: String,
    val message: String,
    val relatedLoanId: Long? = null,
    val actionType: String = "APPROVAL_REQUEST", // APPROVAL_REQUEST, PAYMENT_REMINDER, SYSTEM
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

data class InviteDialogData(
    val name: String,
    val phone: String,
    val amount: Double,
    val isGiven: Boolean
)
