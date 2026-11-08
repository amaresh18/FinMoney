package com.example.data.model

import java.util.UUID

enum class BillCategory(val displayName: String) {
    ALL("All"),
    CREDIT_CARD("Credit Card"),
    UTILITY("Electricity"),
    MOBILE_RECHARGE("Mobile Recharge"),
    DTH("DTH Recharge"),
    WATER("Water Bill"),
    GAS("LPG & Gas"),
    BROADBAND("Broadband & Landline"),
    FASTAG("FASTag"),
    INSURANCE("Insurance"),
    LOANS("Loans"),
    NEW_CARDS("New Credit Cards")
}

data class BillItem(
    val id: String = UUID.randomUUID().toString(),
    val category: BillCategory = BillCategory.CREDIT_CARD,
    val providerName: String = "",
    val providerCode: String = "",
    val subCategory: String = "",
    val accountNumberMasked: String = "",
    val dueAmount: Double = 0.0,
    val dueDateTimestamp: Long = System.currentTimeMillis() + 86400000L * 3,
    val isOverdue: Boolean = false,
    val isPaid: Boolean = false,
    val paidTimestamp: Long? = null,
    val utrReference: String? = null,
    val autopaySupported: Boolean = false,
    val hiddenChargesProtection: Boolean = true,
    val consumerName: String = "You",
    val billPeriod: String = "Current Cycle",
    val ekoTransactionId: String? = null,
    val paynearbyTransactionId: String? = null
)

data class PreApprovedCreditCard(
    val id: String,
    val bankName: String,
    val bankCode: String,
    val cardName: String,
    val rewardHighlight: String,
    val annualFee: String,
    val cardType: String, // "RuPay", "Visa", "Mastercard"
    val joiningBonus: String,
    val accentColorHex: String,
    val gromoCommissionPayout: String = "₹2,200",
    val gromoProductSlug: String = "credit-card",
    val gromoTrackingUrl: String = "https://gromo.in/products/credit-cards",
    val productType: String = "Credit Card" // "Credit Card" or "Personal Loan"
)

data class CommissionRecord(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val category: String, // "PayNearby Utility BBPS", "Eko BBPS Bill Pay", "GroMo Credit Card", "GroMo Personal Loan"
    val amount: Double,
    val status: String, // "Credited", "Pending Verification"
    val timestamp: Long = System.currentTimeMillis(),
    val referenceNumber: String = "PNB-" + (100000 + (Math.random() * 900000).toInt())
)

data class PartnerCredentials(
    val paynearbyAgentId: String = "PNB_AGENT_883201",
    val paynearbyApiKey: String = "pnb_sec_key_902184_sandbox",
    val paynearbyMerchantMobile: String = "9876543210",
    val isPayNearbyActive: Boolean = true,
    val ekoDeveloperKey: String = "eko_dev_9841284a_sandbox",
    val ekoAuthKey: String = "eko_auth_secret_7721",
    val ekoInitiatorId: String = "9876543210", // Initiator phone / merchant code
    val isEkoProduction: Boolean = false,
    val gromoPartnerCode: String = "GRM_FIN_2026",
    val gromoPartnerName: String = "FinMoney Partner"
)
