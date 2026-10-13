package com.example.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

typealias BankBalanceItem = com.example.data.model.BankBalanceItem
typealias IncomeCategory = com.example.data.model.IncomeCategory
typealias CategorySummary = com.example.data.model.CategorySummary
typealias IncomeSource = com.example.data.model.IncomeSource
typealias DebitItem = com.example.data.model.DebitItem
typealias SalaryRecord = com.example.data.model.SalaryRecord
typealias LoanType = com.example.data.model.LoanType
typealias LoanApprovalStatus = com.example.data.model.LoanApprovalStatus
typealias ApprovalStatus = com.example.data.model.LoanApprovalStatus
typealias LoanTransaction = com.example.data.model.LoanTransaction
typealias PaymentRecord = com.example.util.PaymentRecord

data class CategoryAggregate(
    val category: String,
    val icon: ImageVector,
    val totalAmount: Double,
    val paidAmount: Double,
    val yetToPayAmount: Double,
    val totalCount: Int,
    val paidCount: Int
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
    val bidAmount: Double,
    val dividendPerMember: Double,
    val netPayablePerMember: Double,
    val dateTimestamp: Long
)

object DefaultDebitCategories {
    val CHIT_PAYMENTS = "Chit Payments"
    val CHIT = "Chit Payments"
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
    val SCHOOL_FEES = "School Fees"
    val HOSTEL_FEES = "Hostel Fees"
    val MAID = "Maid"
    val MAINTENANCE = "Maintenance"
    val ELECTRICITY = "Electricity"
    val INTERNET = "Internet"
    val OUTSIDE_FOOD = "Outside Food"
    val MILK = "Milk"
    val NEWSPAPER = "Newspaper"
    val OTT = "OTT"
    val PHARMACY = "Pharmacy"
    val TRAVEL = "Travel"
    val TOURS = "Tours"
    val SAVING = "Saving"
    val DEBT_REPAY = "Debt Repayment"
    val LENDING = "Lending"
    val DONATION = "Donation"
    val CUSTOM = "Custom"

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
            "maid", "cook", "domestic help" -> Icons.Default.CleaningServices
            "milk", "dairy", "daily milk" -> Icons.Default.LocalDrink
            "newspaper", "paper", "magazines" -> Icons.Default.Newspaper
            "ott", "ott subscription", "netflix", "prime", "hotstar" -> Icons.Default.Tv
            "clearing borrowed money", "debt repayment", "debt_repay" -> Icons.Default.Paid
            "lending to someone", "lending" -> Icons.AutoMirrored.Filled.CallMade
            "donation", "charity", "temple" -> Icons.Default.VolunteerActivism
            "insurance", "life insurance", "health insurance", "mediclaim" -> Icons.Default.HealthAndSafety
            "pharmacy", "medical", "medicines" -> Icons.Default.LocalPharmacy
            "travel", "commute", "cab", "metro", "bus" -> Icons.Default.DirectionsBus
            "tours", "vacation", "trip", "holiday" -> Icons.Default.Flight
            "outside food", "dining", "restaurant", "swiggy", "zomato" -> Icons.Default.Restaurant
            "school fees", "tuition fees", "college fees" -> Icons.Default.School
            "hostel fees", "accommodation", "pg" -> Icons.Default.Apartment
            "apartment maintenance", "society maintenance", "maintenance" -> Icons.Default.HomeRepairService
            "groceries", "grocery", "food", "supermarket" -> Icons.Default.ShoppingCart
            "recharge", "mobile recharge", "phone", "dth" -> Icons.Default.PhoneAndroid
            "fuel", "petrol", "diesel", "gas" -> Icons.Default.LocalGasStation
            "rd / fd", "rd", "fd", "fixed deposit", "saving" -> Icons.Default.Savings
            "bills & utilities", "bills", "electricity", "water", "flash" -> Icons.Default.Bolt
            "internet", "wifi", "broadband" -> Icons.Default.Wifi
            "shopping", "clothes", "lifestyle" -> Icons.Default.ShoppingBag
            else -> Icons.Default.Category
        }
    }
}
