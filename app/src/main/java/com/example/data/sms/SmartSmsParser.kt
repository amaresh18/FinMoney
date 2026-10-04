package com.example.data.sms

import android.content.Context
import android.net.Uri
import com.example.data.model.DebitCategory
import com.example.data.model.DebitItem
import java.util.Locale
import java.util.regex.Pattern

enum class SmsTransactionType {
    DEBIT,
    CREDIT,
    UNKNOWN
}

enum class DetectedCategory(val displayName: String, val iconKey: String) {
    SHOPPING("Shopping / E-Commerce", "shopping"),
    PEER_TRANSFER("Peer-to-Peer Transfer (Lent/Borrowed)", "peer"),
    INTERNET_RECHARGE("Internet & Mobile Recharge", "internet"),
    ELECTRICITY("Electricity & Utility Bill", "flash"),
    RENT("House Rent", "home"),
    LOAN_EMI("Loan / EMI Repayment", "emi"),
    INVESTMENT_SIP("Investment / SIP / Stocks", "investment"),
    SALARY_CREDIT("Monthly Salary Credit", "salary"),
    SELF_TRANSFER("Self Account Transfer", "self_transfer"),
    GENERAL_EXPENSE("General Expense", "general")
}

data class ParsedSmsTransaction(
    val id: String = java.util.UUID.randomUUID().toString(),
    val originalSms: String,
    val sender: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: SmsTransactionType,
    val amount: Double,
    val merchantOrParty: String,
    val phoneNumber: String = "",
    val detectedCategory: DetectedCategory,
    val matchedPlannedDebitId: Long? = null,
    val matchedPlannedDebitTitle: String? = null,
    val isProcessed: Boolean = false,
    val isRejected: Boolean = false
)

object SmartSmsParser {

    private val amountRegex1 = Regex("""(?:rs\.?|inr|inr\.|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    private val amountRegex2 = Regex("""([0-9,]+(?:\.[0-9]{1,2})?)\s*(?:rs\.?|inr|₹)""", RegexOption.IGNORE_CASE)
    private val amountRegex3 = Regex("""(?:debited|credited|spent|paid|received|transfer|txn|withdrawn|added|sent)\s+(?:by|for|of|with)?\s*(?:rs\.?|inr|₹)?\s*([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)

    private val phoneRegex = Regex("""(?:\+91|91)?([6-9]\d{9})""")

    fun parseMessage(
        smsText: String,
        sender: String,
        timestamp: Long = System.currentTimeMillis(),
        plannedDebits: List<DebitItem> = emptyList()
    ): ParsedSmsTransaction? {
        val lowerText = smsText.lowercase(Locale.getDefault())

        // Ignore OTP messages or non-transactional SMS
        if (lowerText.contains("otp") || lowerText.contains("verification code") || lowerText.contains("secret code")) {
            return null
        }

        // Determine Debit vs Credit
        val type = when {
            lowerText.contains("debited") || lowerText.contains("spent") || lowerText.contains("paid") ||
            lowerText.contains("sent") || lowerText.contains("deducted") || lowerText.contains("withdrawn") ||
            lowerText.contains("transferred to") || lowerText.contains("charge") || lowerText.contains("txn of") -> SmsTransactionType.DEBIT

            lowerText.contains("credited") || lowerText.contains("received") || lowerText.contains("deposited") ||
            lowerText.contains("refund") || lowerText.contains("cashback") || lowerText.contains("salary") -> SmsTransactionType.CREDIT

            else -> return null // Not a financial debit/credit SMS
        }

        // Extract Amount with fallback regex patterns
        var amount = 0.0
        val m1 = amountRegex1.find(smsText)
        val m2 = if (m1 == null) amountRegex2.find(smsText) else null
        val m3 = if (m1 == null && m2 == null) amountRegex3.find(smsText) else null

        val numStr = m1?.groupValues?.getOrNull(1)
            ?: m2?.groupValues?.getOrNull(1)
            ?: m3?.groupValues?.getOrNull(1)

        if (numStr != null) {
            amount = numStr.replace(",", "").toDoubleOrNull() ?: 0.0
        }

        if (amount <= 0.0) return null

        // Extract Phone Number if mentioned (for UPI P2P transfers)
        var extractedPhone = ""
        val phoneMatch = phoneRegex.find(smsText)
        if (phoneMatch != null) {
            extractedPhone = phoneMatch.groupValues[1]
        }

        // Extract Party / Merchant Name
        var merchantOrParty = extractMerchantName(smsText, lowerText)

        // Categorize Transaction
        val category = detectCategory(lowerText, merchantOrParty, extractedPhone, type)

        // Check for matching planned debit commitment in the current month
        var matchedDebitId: Long? = null
        var matchedDebitTitle: String? = null

        if (type == SmsTransactionType.DEBIT && plannedDebits.isNotEmpty()) {
            val plannedMatch = plannedDebits.firstOrNull { debit ->
                val debitCat = try { DebitCategory.valueOf(debit.category) } catch (e: Exception) { null }
                val isCatMatch = when (category) {
                    DetectedCategory.RENT -> debitCat == DebitCategory.RENT || debit.title.contains("rent", ignoreCase = true)
                    DetectedCategory.ELECTRICITY -> debitCat == DebitCategory.ELECTRICITY || debit.title.contains("electric", ignoreCase = true) || debit.title.contains("power", ignoreCase = true)
                    DetectedCategory.INTERNET_RECHARGE -> debitCat == DebitCategory.INTERNET || debit.title.contains("jio", ignoreCase = true) || debit.title.contains("airtel", ignoreCase = true) || debit.title.contains("wifi", ignoreCase = true)
                    DetectedCategory.LOAN_EMI -> debitCat == DebitCategory.EMI || debit.title.contains("emi", ignoreCase = true) || debit.title.contains("loan", ignoreCase = true)
                    DetectedCategory.INVESTMENT_SIP -> debitCat == DebitCategory.INVESTMENT || debitCat == DebitCategory.SAVING || debit.title.contains("sip", ignoreCase = true)
                    DetectedCategory.SHOPPING -> debitCat == DebitCategory.SHOPPING
                    else -> false
                }
                val isTitleMatch = merchantOrParty.isNotBlank() && debit.title.contains(merchantOrParty, ignoreCase = true)
                val isAmountClose = Math.abs(debit.amount - amount) < (debit.amount * 0.15) // within 15% tolerance

                (isCatMatch || isTitleMatch) && isAmountClose && !debit.isPaid
            }

            if (plannedMatch != null) {
                matchedDebitId = plannedMatch.id
                matchedDebitTitle = plannedMatch.title
            }
        }

        return ParsedSmsTransaction(
            originalSms = smsText,
            sender = sender,
            timestamp = timestamp,
            type = type,
            amount = amount,
            merchantOrParty = merchantOrParty.ifBlank { if (category == DetectedCategory.PEER_TRANSFER) "Contact" else category.displayName },
            phoneNumber = extractedPhone,
            detectedCategory = category,
            matchedPlannedDebitId = matchedDebitId,
            matchedPlannedDebitTitle = matchedDebitTitle
        )
    }

    private fun detectCategory(
        lowerText: String,
        merchant: String,
        phone: String,
        type: SmsTransactionType
    ): DetectedCategory {
        // 1. Self transfer
        if (lowerText.contains("self transfer") || lowerText.contains("own account") || lowerText.contains("to your own a/c")) {
            return DetectedCategory.SELF_TRANSFER
        }

        // 2. Salary Credit
        if (type == SmsTransactionType.CREDIT && (lowerText.contains("salary") || lowerText.contains("payroll") || lowerText.contains("sal credit"))) {
            return DetectedCategory.SALARY_CREDIT
        }

        // 3. Rent
        if (lowerText.contains("rent") || lowerText.contains("nobroker") || lowerText.contains("magicbricks") || lowerText.contains("landlord")) {
            return DetectedCategory.RENT
        }

        // 4. Electricity & Utilities
        if (lowerText.contains("electricity") || lowerText.contains("bescom") || lowerText.contains("tneb") ||
            lowerText.contains("msedcl") || lowerText.contains("torrent") || lowerText.contains("adani electricity") ||
            lowerText.contains("power bill") || lowerText.contains("water bill") || lowerText.contains("gas bill") ||
            lowerText.contains("indane") || lowerText.contains("hp gas") || lowerText.contains("bharat gas")) {
            return DetectedCategory.ELECTRICITY
        }

        // 5. Internet & Recharge
        if (lowerText.contains("recharge") || lowerText.contains("jio") || lowerText.contains("airtel") ||
            lowerText.contains("vi ") || lowerText.contains("vodafone") || lowerText.contains("bsnl") ||
            lowerText.contains("act fibernet") || lowerText.contains("hathway") || lowerText.contains("tata play") ||
            lowerText.contains("broadband") || lowerText.contains("wifi") || lowerText.contains("dth")) {
            return DetectedCategory.INTERNET_RECHARGE
        }

        // 6. Loans & EMIs
        if (lowerText.contains("emi") || lowerText.contains("loan") || lowerText.contains("bajaj finserv") ||
            lowerText.contains("home loan") || lowerText.contains("car loan") || lowerText.contains("personal loan") ||
            lowerText.contains("credit card bill") || lowerText.contains("auto-debit towards loan")) {
            return DetectedCategory.LOAN_EMI
        }

        // 7. Investment & SIP
        if (lowerText.contains("zerodha") || lowerText.contains("groww") || lowerText.contains("indmoney") ||
            lowerText.contains("upstox") || lowerText.contains("kuvera") || lowerText.contains("mutual fund") ||
            lowerText.contains("sip") || lowerText.contains("ppf") || lowerText.contains("nps") ||
            lowerText.contains("nsdl") || lowerText.contains("cdsl") || lowerText.contains("angelone")) {
            return DetectedCategory.INVESTMENT_SIP
        }

        // 8. Shopping & E-Commerce & Food
        if (lowerText.contains("amazon") || lowerText.contains("flipkart") || lowerText.contains("myntra") ||
            lowerText.contains("swiggy") || lowerText.contains("zomato") || lowerText.contains("blinkit") ||
            lowerText.contains("zepto") || lowerText.contains("instamart") || lowerText.contains("bigbasket") ||
            lowerText.contains("dmart") || lowerText.contains("uber") || lowerText.contains("ola") ||
            lowerText.contains("rapido") || lowerText.contains("makemytrip") || lowerText.contains("bookmyshow") ||
            lowerText.contains("nykaa") || lowerText.contains("ajio") || lowerText.contains("zara") ||
            lowerText.contains("purchase") || lowerText.contains("pos") || lowerText.contains("store") ||
            lowerText.contains("retail") || lowerText.contains("e-com")) {
            return DetectedCategory.SHOPPING
        }

        // 9. Peer-to-Peer Transfer (Lent / Borrowed / Phone number level)
        if (phone.isNotBlank() || lowerText.contains("upi/p2p") || lowerText.contains("vpa") ||
            lowerText.contains("transferred to") || lowerText.contains("received from") || lowerText.contains("sent to")) {
            return DetectedCategory.PEER_TRANSFER
        }

        return DetectedCategory.GENERAL_EXPENSE
    }

    private fun extractMerchantName(smsText: String, lowerText: String): String {
        // Try matching "to [Merchant/Name]" or "at [Merchant]" or "info: [Merchant]"
        val patterns = listOf(
            Regex("""(?:to|at|towards|info\/?|vpa)\s+([A-Za-z0-9\s._\-&]+?)(?:\s+on|\s+ref|\s+upi|\s+avl|\s+bal|\.|\band\b|$)""", RegexOption.IGNORE_CASE),
            Regex("""(?:from)\s+([A-Za-z0-9\s._\-&]+?)(?:\s+on|\s+ref|\s+upi|\s+avl|\s+bal|\.|\band\b|$)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            val match = pattern.find(smsText)
            if (match != null) {
                val extracted = match.groupValues[1].trim()
                    .replace(Regex("""^(vpa|a\/c|account|m\/s)\s*""", RegexOption.IGNORE_CASE), "")
                    .take(30)
                    .trim()
                if (extracted.isNotBlank() && extracted.length > 2) {
                    return extracted
                }
            }
        }

        // Fallbacks for well known merchants
        val knowns = listOf(
            "Amazon", "Flipkart", "Swiggy", "Zomato", "Blinkit", "Zepto", "Jio", "Airtel",
            "Bescom", "Uber", "Ola", "Myntra", "DMart", "Zerodha", "Groww", "HDFC Bank", "SBI", "PhonePe"
        )
        for (k in knowns) {
            if (lowerText.contains(k.lowercase())) return k
        }

        return ""
    }

    // Realistic Sample Bank SMS list for testing and instant simulation
    fun getSampleBankSmsList(): List<Pair<String, String>> {
        return listOf(
            "VM-HDFCBK" to "Dear Customer, INR 15,000.00 debited from A/C **4912 on 01-OCT-26 towards House Rent to Landlord Ramesh Sharma. UPI Ref 38192831.",
            "AD-SBIINB" to "Your A/C **8102 debited by Rs 12,500.00 on 01-OCT-26 towards Auto-Debit for HDFC Car EMI. Avl Bal Rs 45,210.",
            "AX-PHONPE" to "Paid Rs. 5,000.00 to Suresh Kumar (9876543210) via PhonePe UPI on 01-OCT-26. Ref No: 82910293.",
            "VK-AIRTEL" to "Payment of Rs 999.00 received for Airtel WiFi Fiber broadband connection on 01-OCT-26. Thank you.",
            "VM-ICICIB" to "Your ICICI Card **2049 spent Rs 2,499.00 at Amazon India on 01-OCT-26. Avl Lmt Rs 1,45,000.",
            "AD-BESCOM" to "Payment of Rs 1,850.00 received towards BESCOM Electricity Bill for CA No 8291039.",
            "VM-ZERODH" to "Rs 10,000.00 debited from A/C **4912 towards monthly SIP investment in Zerodha Mutual Fund.",
            "AD-HDFCBK" to "Salary of Rs 85,000.00 credited to your A/C **4912 by TechCorp India Pvt Ltd on 01-OCT-26."
        )
    }

    // Read real device SMS inbox if permission is granted
    fun readDeviceSms(
        context: Context,
        limit: Int = 50,
        plannedDebits: List<DebitItem> = emptyList()
    ): List<ParsedSmsTransaction> {
        val list = mutableListOf<ParsedSmsTransaction>()
        try {
            val uri = Uri.parse("content://sms/inbox")
            val projection = arrayOf("_id", "address", "body", "date")
            val cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "date DESC"
            )

            cursor?.use {
                val addressIdx = it.getColumnIndex("address")
                val bodyIdx = it.getColumnIndex("body")
                val dateIdx = it.getColumnIndex("date")

                var count = 0
                while (it.moveToNext() && count < limit) {
                    val sender = if (addressIdx != -1) it.getString(addressIdx) ?: "SMS" else "SMS"
                    val body = if (bodyIdx != -1) it.getString(bodyIdx) ?: "" else ""
                    val date = if (dateIdx != -1) it.getLong(dateIdx) else System.currentTimeMillis()

                    val parsed = parseMessage(body, sender, date, plannedDebits)
                    if (parsed != null) {
                        list.add(parsed)
                    }
                    count++
                }
            }
        } catch (e: SecurityException) {
            android.util.Log.e("SmartSms", "READ_SMS permission not granted", e)
        } catch (e: Exception) {
            android.util.Log.e("SmartSms", "Error reading device SMS: ${e.message}", e)
        }
        return list
    }
}
