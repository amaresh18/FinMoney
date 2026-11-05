package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import com.example.data.model.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class BillsRepository private constructor(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("finmoney_bills_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _bills = MutableStateFlow<List<BillItem>>(emptyList())
    val bills: StateFlow<List<BillItem>> = _bills.asStateFlow()

    private val _credentials = MutableStateFlow(PartnerCredentials())
    val credentials: StateFlow<PartnerCredentials> = _credentials.asStateFlow()

    private val _commissions = MutableStateFlow<List<CommissionRecord>>(emptyList())
    val commissions: StateFlow<List<CommissionRecord>> = _commissions.asStateFlow()

    init {
        loadCredentials()
        loadBills()
        loadCommissions()
    }

    private fun loadCredentials() {
        val json = prefs.getString(KEY_CREDENTIALS, null)
        if (json != null) {
            try {
                val parsed = gson.fromJson(json, PartnerCredentials::class.java)
                if (parsed != null) {
                    _credentials.value = parsed
                    return
                }
            } catch (e: Exception) {
                // fallback
            }
        }
        _credentials.value = PartnerCredentials()
    }

    fun updateCredentials(newCreds: PartnerCredentials) {
        _credentials.value = newCreds
        prefs.edit().putString(KEY_CREDENTIALS, gson.toJson(newCreds)).apply()
    }

    private fun loadCommissions() {
        val json = prefs.getString(KEY_COMMISSIONS, null)
        if (json != null) {
            try {
                val type = object : TypeToken<List<CommissionRecord>>() {}.type
                val parsed: List<CommissionRecord>? = gson.fromJson(json, type)
                if (!parsed.isNullOrEmpty()) {
                    _commissions.value = parsed
                    return
                }
            } catch (e: Exception) {
                // fallback
            }
        }
        // Initialize with default earnings sample so the user immediately sees how GroMo & Eko track
        val defaultCommissions = listOf(
            CommissionRecord(
                title = "HDFC Regalia Gold Credit Card",
                category = "GroMo Credit Card",
                amount = 2200.0,
                status = "Credited",
                timestamp = System.currentTimeMillis() - 86400000L * 2,
                referenceNumber = "GRM-892104"
            ),
            CommissionRecord(
                title = "ICICI Coral RuPay Credit Card",
                category = "GroMo Credit Card",
                amount = 1800.0,
                status = "Credited",
                timestamp = System.currentTimeMillis() - 86400000L * 5,
                referenceNumber = "GRM-553198"
            ),
            CommissionRecord(
                title = "Electricity Bill Pay (NPDCL)",
                category = "Eko BBPS Bill Pay",
                amount = 8.50,
                status = "Credited",
                timestamp = System.currentTimeMillis() - 86400000L * 1,
                referenceNumber = "EKO-902184"
            )
        )
        saveCommissionsInternal(defaultCommissions)
    }

    private fun saveCommissionsInternal(list: List<CommissionRecord>) {
        prefs.edit().putString(KEY_COMMISSIONS, gson.toJson(list)).apply()
        _commissions.value = list
    }

    private fun loadBills() {
        val json = prefs.getString(KEY_BILLS, null)
        if (json.isNullOrBlank()) {
            val defaultList = createDefaultBills()
            saveBillsInternal(defaultList)
            _bills.value = defaultList
        } else {
            try {
                val type = object : TypeToken<List<BillItem>>() {}.type
                val parsed: List<BillItem>? = gson.fromJson(json, type)
                if (parsed.isNullOrEmpty()) {
                    val defaultList = createDefaultBills()
                    saveBillsInternal(defaultList)
                    _bills.value = defaultList
                } else {
                    _bills.value = parsed
                }
            } catch (e: Exception) {
                val defaultList = createDefaultBills()
                saveBillsInternal(defaultList)
                _bills.value = defaultList
            }
        }
    }

    private fun saveBillsInternal(list: List<BillItem>) {
        prefs.edit().putString(KEY_BILLS, gson.toJson(list)).apply()
        _bills.value = list
    }

    fun payBill(
        billId: String,
        amount: Double,
        paymentMethod: String,
        currentMonth: Int,
        currentYear: Int,
        onDebitCreated: suspend (DebitItem) -> Unit
    ): BillItem? {
        val currentList = _bills.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == billId }
        if (index == -1) return null

        val bill = currentList[index]
        val utr = "IND" + (1000000000L + (Math.random() * 9000000000L).toLong())
        val ekoTid = "EKO" + System.currentTimeMillis()

        val updated = bill.copy(
            isPaid = true,
            isOverdue = false,
            dueAmount = 0.0,
            paidTimestamp = System.currentTimeMillis(),
            utrReference = utr,
            ekoTransactionId = ekoTid
        )
        currentList[index] = updated
        saveBillsInternal(currentList)

        // Credit Eko BBPS retailer commission (₹5 - ₹12 per utility payment)
        val billCommission = if (bill.category == BillCategory.UTILITY) 8.50 else 5.00
        val commRecord = CommissionRecord(
            title = "${bill.providerName} Bill Payment",
            category = "Eko BBPS Bill Pay",
            amount = billCommission,
            status = "Credited",
            timestamp = System.currentTimeMillis(),
            referenceNumber = ekoTid
        )
        val commList = _commissions.value.toMutableList()
        commList.add(0, commRecord)
        saveCommissionsInternal(commList)

        // Create a corresponding debit item in FinMoney
        val categoryName = when (bill.category) {
            BillCategory.CREDIT_CARD -> "Credit Card"
            BillCategory.UTILITY -> "Electricity"
            BillCategory.INSURANCE -> "Insurance"
            BillCategory.LOANS -> "EMI"
            else -> "Utilities"
        }

        val debit = DebitItem(
            userId = "",
            month = currentMonth,
            year = currentYear,
            monthYear = "$currentYear-${if (currentMonth < 10) "0$currentMonth" else "$currentMonth"}",
            category = categoryName,
            title = "${bill.providerName} Bill ($paymentMethod)",
            amount = amount,
            dueDateDay = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH),
            isPaid = true,
            isPaidViaCreditCard = bill.category == BillCategory.CREDIT_CARD,
            paidDateTimestamp = System.currentTimeMillis(),
            notes = "Processed via Eko BBPS / INDpay. UTR: $utr, Eko TID: $ekoTid"
        )

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launchSafe {
            onDebitCreated(debit)
        }

        return updated
    }

    /**
     * Handles GroMo Credit Card & Loan Application
     * Returns tracking URL with user's GroMo partner code, and adds an active track to Commission Ledger
     */
    fun recordGroMoLead(card: PreApprovedCreditCard): String {
        val partnerCode = _credentials.value.gromoPartnerCode
        val cleanSlug = card.gromoProductSlug
        val trackingUrl = "https://gromo.in/referral?code=${Uri.encode(partnerCode)}&product=${Uri.encode(cleanSlug)}"

        // Parse numerical amount from payout string (e.g. "₹2,200" -> 2200.0)
        val numAmount = card.gromoCommissionPayout.replace("[^0-9]".toRegex(), "").toDoubleOrNull() ?: 1500.0

        val newRecord = CommissionRecord(
            title = "${card.bankName} ${card.cardName}",
            category = if (card.productType == "Personal Loan") "GroMo Personal Loan" else "GroMo Credit Card",
            amount = numAmount,
            status = "Pending Verification",
            timestamp = System.currentTimeMillis(),
            referenceNumber = "GRM-${(100000 + (Math.random() * 900000).toInt())}"
        )
        val commList = _commissions.value.toMutableList()
        commList.add(0, newRecord)
        saveCommissionsInternal(commList)

        return trackingUrl
    }

    fun addBill(bill: BillItem) {
        val currentList = _bills.value.toMutableList()
        currentList.add(0, bill)
        saveBillsInternal(currentList)
    }

    fun deleteBill(billId: String) {
        val currentList = _bills.value.filter { it.id != billId }
        saveBillsInternal(currentList)
    }

    fun refreshBill(billId: String): BillItem? {
        val currentList = _bills.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == billId }
        if (index == -1) return null
        val b = currentList[index]
        val updated = if (b.dueAmount == 0.0 || b.isPaid) {
            b.copy(isPaid = true, isOverdue = false, dueAmount = 0.0)
        } else {
            b
        }
        currentList[index] = updated
        saveBillsInternal(currentList)
        return updated
    }

    fun toggleAutopay(billId: String): Boolean {
        val currentList = _bills.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == billId }
        if (index == -1) return false
        val b = currentList[index]
        val newAutopay = !b.autopaySupported
        currentList[index] = b.copy(autopaySupported = newAutopay)
        saveBillsInternal(currentList)
        return newAutopay
    }

    val preApprovedCards: List<PreApprovedCreditCard> = listOf(
        PreApprovedCreditCard(
            id = "card_hdfc_regalia",
            bankName = "HDFC Bank",
            bankCode = "HDFC",
            cardName = "HDFC Regalia Gold Credit Card",
            rewardHighlight = "4X Reward Points on Dining & Travel • Complimentary Lounge Access",
            annualFee = "Lifetime Free (Special Offer)",
            cardType = "Visa Signature",
            joiningBonus = "₹1,500 Amazon Gift Voucher on 1st transaction",
            accentColorHex = "#004B87",
            gromoCommissionPayout = "₹2,200",
            gromoProductSlug = "hdfc-regalia-gold",
            gromoTrackingUrl = "https://gromo.in/products/credit-cards/hdfc-regalia-gold",
            productType = "Credit Card"
        ),
        PreApprovedCreditCard(
            id = "card_icici_coral",
            bankName = "ICICI Bank",
            bankCode = "ICICI",
            cardName = "ICICI Coral RuPay Credit Card",
            rewardHighlight = "UPI Payments Enabled • Flat 2% Instant Cashback on Fuel & Dining",
            annualFee = "₹0 Joining Fee • ₹0 Annual Fee",
            cardType = "RuPay Platinum",
            joiningBonus = "2,000 Reward Points on card activation",
            accentColorHex = "#A62024",
            gromoCommissionPayout = "₹1,800",
            gromoProductSlug = "icici-coral-rupay",
            gromoTrackingUrl = "https://gromo.in/products/credit-cards/icici-coral-rupay",
            productType = "Credit Card"
        ),
        PreApprovedCreditCard(
            id = "card_axis_neo",
            bankName = "Axis Bank",
            bankCode = "AXIS",
            cardName = "Axis Bank Neo Credit Card",
            rewardHighlight = "40% off on Zomato • 10% off on BookMyShow & Blinkit",
            annualFee = "₹250 / year (Waived on ₹25,000 spend)",
            cardType = "Mastercard",
            joiningBonus = "100% cashback on first utility bill payment",
            accentColorHex = "#861F41",
            gromoCommissionPayout = "₹1,950",
            gromoProductSlug = "axis-neo-card",
            gromoTrackingUrl = "https://gromo.in/products/credit-cards/axis-neo-card",
            productType = "Credit Card"
        ),
        PreApprovedCreditCard(
            id = "card_sbi_cashback",
            bankName = "SBI Card",
            bankCode = "SBI",
            cardName = "SBI Cashback Credit Card",
            rewardHighlight = "Flat 5% Cashback on ALL online purchases • Auto-credited to statement",
            annualFee = "₹999 / year (Waived on ₹2 Lakh spend)",
            cardType = "Visa Platinum",
            joiningBonus = "₹1,000 welcome cashback in statement",
            accentColorHex = "#1E88E5",
            gromoCommissionPayout = "₹2,100",
            gromoProductSlug = "sbi-cashback-card",
            gromoTrackingUrl = "https://gromo.in/products/credit-cards/sbi-cashback-card",
            productType = "Credit Card"
        ),
        PreApprovedCreditCard(
            id = "card_idfc_first_wow",
            bankName = "IDFC FIRST Bank",
            bankCode = "IDFC",
            cardName = "IDFC FIRST WOW Credit Card",
            rewardHighlight = "Zero Forex Markup • 100% Guaranteed Approval against FD • No Income Proof",
            annualFee = "Lifetime Free (Zero Annual & Joining Fee)",
            cardType = "Visa Infinite",
            joiningBonus = "5% cashback on first EMI transaction",
            accentColorHex = "#991B1B",
            gromoCommissionPayout = "₹1,500",
            gromoProductSlug = "idfc-first-wow",
            gromoTrackingUrl = "https://gromo.in/products/credit-cards/idfc-first-wow",
            productType = "Credit Card"
        ),
        PreApprovedCreditCard(
            id = "loan_kreditbee_instant",
            bankName = "KreditBee / NBFC",
            bankCode = "KREDITBEE",
            cardName = "KreditBee Instant Cash Loan",
            rewardHighlight = "Up to ₹4,00,000 Instant Disbursement in 10 mins • 100% Digital",
            annualFee = "Interest starting 1.02% / month",
            cardType = "Personal Loan",
            joiningBonus = "Zero processing fee on first loan",
            accentColorHex = "#059669",
            gromoCommissionPayout = "₹2,500",
            gromoProductSlug = "kreditbee-personal-loan",
            gromoTrackingUrl = "https://gromo.in/products/loans/kreditbee",
            productType = "Personal Loan"
        )
    )

    private fun createDefaultBills(): List<BillItem> {
        val now = System.currentTimeMillis()
        return listOf(
            BillItem(
                id = "bill_rupay_8320",
                category = BillCategory.CREDIT_CARD,
                providerName = "UPI RuPay Credit Card",
                providerCode = "HDFC",
                subCategory = "Credit Card",
                accountNumberMasked = "•• 8320",
                dueAmount = 511.0,
                dueDateTimestamp = now - 86400000L * 2, // 2 days overdue
                isOverdue = true,
                isPaid = false,
                autopaySupported = false,
                hiddenChargesProtection = true
            ),
            BillItem(
                id = "bill_icici_9005",
                category = BillCategory.CREDIT_CARD,
                providerName = "ICICI Amazon Pay Credit Card",
                providerCode = "ICICI",
                subCategory = "Credit Card",
                accountNumberMasked = "•• 9005",
                dueAmount = 10418.67,
                dueDateTimestamp = now - 86400000L * 4, // 4 days overdue
                isOverdue = true,
                isPaid = false,
                autopaySupported = false,
                hiddenChargesProtection = true
            ),
            BillItem(
                id = "bill_npdcl_16258886",
                category = BillCategory.UTILITY,
                providerName = "Northern Power Distribution Company of Telangana Limited",
                providerCode = "NPDCL",
                subCategory = "Electricity",
                accountNumberMasked = "16258886",
                dueAmount = 0.0,
                dueDateTimestamp = now + 86400000L * 14,
                isOverdue = false,
                isPaid = true,
                autopaySupported = false,
                hiddenChargesProtection = true
            ),
            BillItem(
                id = "bill_tata_aia",
                category = BillCategory.INSURANCE,
                providerName = "Tata AIA Life Insurance",
                providerCode = "TATA_AIA",
                subCategory = "Term Life Policy",
                accountNumberMasked = "POL-984210",
                dueAmount = 4250.0,
                dueDateTimestamp = now + 86400000L * 5,
                isOverdue = false,
                isPaid = false,
                autopaySupported = true,
                hiddenChargesProtection = true
            ),
            BillItem(
                id = "bill_hdfc_home_loan",
                category = BillCategory.LOANS,
                providerName = "HDFC Bank Home Loan EMI",
                providerCode = "HDFC",
                subCategory = "Home Loan",
                accountNumberMasked = "HL-883192",
                dueAmount = 24500.0,
                dueDateTimestamp = now + 86400000L * 11,
                isOverdue = false,
                isPaid = false,
                autopaySupported = true,
                hiddenChargesProtection = true
            )
        )
    }

    private fun kotlinx.coroutines.CoroutineScope.launchSafe(block: suspend () -> Unit) {
        launch {
            try {
                block()
            } catch (e: Exception) {
                android.util.Log.e("BillsRepository", "Error in background task: ${e.message}", e)
            }
        }
    }

    companion object {
        private const val KEY_BILLS = "key_saved_bills_v1"
        private const val KEY_CREDENTIALS = "key_partner_credentials_v1"
        private const val KEY_COMMISSIONS = "key_partner_commissions_v1"

        @Volatile
        private var instance: BillsRepository? = null

        fun getInstance(context: Context): BillsRepository {
            return instance ?: synchronized(this) {
                instance ?: BillsRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
