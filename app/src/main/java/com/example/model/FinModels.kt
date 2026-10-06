package com.example.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

data class IncomeSource(
    val id: String,
    val title: String,
    val amount: Double
)

data class DebitItem(
    val id: String,
    val title: String,
    val amount: Double,
    val category: String,
    val dueDateDay: Int,
    val currentMonth: Int = 1,
    val totalMonths: Int = 1,
    val isPaid: Boolean = false,
    val paidDate: Long? = null,
    val interestRatePercent: Double = 0.0,
    val monthYear: String, // format "YYYY-MM"
    val notes: String = ""
)

data class CategoryAggregate(
    val category: String,
    val icon: ImageVector,
    val totalAmount: Double,
    val paidAmount: Double,
    val yetToPayAmount: Double,
    val totalCount: Int,
    val paidCount: Int
)

data class SalaryRecord(
    val monthYear: String,
    val baseSalary: Double,
    val additionalIncome: Double = 0.0,
    val incomeSources: List<IncomeSource> = emptyList(),
    val notes: String = ""
)

data class MonthlySalarySummary(
    val monthYear: String,
    val totalIncome: Double,
    val totalDebits: Double,
    val totalPaid: Double,
    val totalYetToPay: Double,
    val flexibleCash: Double,
    val dailySafeSpend: Double,
    val daysRemainingInMonth: Int,
    val categoryAggregates: List<CategoryAggregate>
)

data class ChitGroup(
    val id: String,
    val name: String,
    val joinCode: String,
    val monthlyInstallment: Double,
    val totalMonths: Int,
    val currentMonth: Int,
    val totalMembers: Int,
    val managerUid: String,
    val managerName: String,
    val members: List<ChitMember> = emptyList(),
    val auctions: List<ChitAuction> = emptyList()
)

data class ChitMember(
    val uid: String,
    val name: String,
    val email: String,
    val monthsPaid: List<Int> = emptyList(),
    val hasLifted: Boolean = false,
    val liftedMonth: Int? = null,
    val liftedBidAmount: Double? = null
)

data class ChitAuction(
    val month: Int,
    val winnerUid: String,
    val winnerName: String,
    val bidAmount: Double, // Discount bid
    val dividendPerMember: Double,
    val netPayablePerMember: Double,
    val dateTimestamp: Long
)

enum class LoanType {
    LENT, // I gave money
    BORROWED // I took money
}

enum class LoanApprovalStatus {
    PENDING,
    MUTUAL_APPROVED,
    SETTLED
}

data class PaymentRecord(
    val id: String,
    val amount: Double,
    val dateTimestamp: Long,
    val notes: String = ""
)

data class LoanTransaction(
    val id: String,
    val personName: String,
    val personContact: String = "",
    val type: LoanType,
    val totalAmount: Double,
    val paidAmount: Double = 0.0,
    val interestRatePercent: Double = 0.0,
    val status: LoanApprovalStatus = LoanApprovalStatus.PENDING,
    val dateTimestamp: Long,
    val dueDateTimestamp: Long? = null,
    val notes: String = "",
    val payments: List<PaymentRecord> = emptyList()
)

object DefaultDebitCategories {
    val CHIT_PAYMENTS = "Chit Payments"
    val EMI = "EMI"
    val SIP = "SIP"
    val RENT = "Rent"
    val GROCERIES = "Groceries"
    val RECHARGE = "Recharge"
    val FUEL = "Fuel"
    val INSURANCE = "Insurance"
    val RD_FD = "RD / FD"
    val BILLS = "Bills & Utilities"
    val OTHER = "Other"

    val list = listOf(
        CHIT_PAYMENTS,
        EMI,
        SIP,
        RENT,
        GROCERIES,
        RECHARGE,
        FUEL,
        INSURANCE,
        RD_FD,
        BILLS,
        OTHER
    )

    fun getIconForCategory(category: String): ImageVector {
        return when (category.trim().lowercase()) {
            "chit payments", "chit", "chits" -> Icons.Default.Savings
            "emi", "loan emi", "loan" -> Icons.Default.CreditCard
            "sip", "investment", "mutual fund" -> Icons.AutoMirrored.Filled.TrendingUp
            "rent", "house rent" -> Icons.Default.Home
            "groceries", "grocery", "food" -> Icons.Default.ShoppingCart
            "recharge", "mobile recharge", "phone" -> Icons.Default.PhoneAndroid
            "fuel", "petrol", "diesel", "gas" -> Icons.Default.LocalGasStation
            "insurance", "life insurance", "health insurance" -> Icons.Default.Shield
            "rd / fd", "rd", "fd", "fixed deposit" -> Icons.Default.Savings
            "bills & utilities", "bills", "electricity", "water", "wifi" -> Icons.Default.ReceiptLong
            else -> Icons.Default.Category
        }
    }
}
