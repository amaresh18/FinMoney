package com.example.data.sms

import android.content.Context
import android.net.Uri
import com.example.data.model.DebitCategory
import com.example.data.model.DebitItem
import java.util.UUID

enum class SmsTransactionType {
    DEBIT,
    CREDIT
}

enum class DetectedCategory(val displayName: String) {
    SHOPPING("Shopping"),
    INTERNET_RECHARGE("Recharge & WiFi"),
    ELECTRICITY("Electricity Bill"),
    RENT("House Rent"),
    LOAN_EMI("Loan EMI"),
    INVESTMENT_SIP("Investment / SIP"),
    PEER_TRANSFER("Peer Transfer"),
    SALARY_CREDIT("Salary Credit"),
    SELF_TRANSFER("Self Transfer"),
    GENERAL("General Expense")
}

data class ParsedSmsTransaction(
    val id: String = UUID.randomUUID().toString(),
    val sender: String,
    val amount: Double,
    val type: SmsTransactionType,
    val detectedCategory: DetectedCategory,
    val merchantOrParty: String = "",
    val phoneNumber: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val originalSms: String = "",
    val matchedPlannedDebitId: Long? = null,
    val matchedPlannedDebitTitle: String? = null
)

object SmartSmsParser {

    fun parseMessage(
        text: String,
        sender: String,
        timestamp: Long = System.currentTimeMillis(),
        plannedDebits: List<DebitItem> = emptyList()
    ): ParsedSmsTransaction? {
        val lower = text.lowercase()
        val isDebit = lower.contains("debited") || lower.contains("spent") || lower.contains("paid") || lower.contains("sent")
        val isCredit = lower.contains("credited") || lower.contains("received") || lower.contains("deposited")

        if (!isDebit && !isCredit) return null

        val type = if (isCredit) SmsTransactionType.CREDIT else SmsTransactionType.DEBIT

        // Extract amount (e.g. Rs. 500, INR 5,000.00, Rs 1200)
        val amountRegex = Regex("""(?:rs\.?|inr)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
        val match = amountRegex.find(text) ?: return null
        val amountStr = match.groupValues[1].replace(",", "")
        val amount = amountStr.toDoubleOrNull() ?: return null

        if (amount <= 0.0) return null

        // Detect category
        val category = when {
            lower.contains("salary") -> DetectedCategory.SALARY_CREDIT
            lower.contains("rent") -> DetectedCategory.RENT
            lower.contains("electricity") || lower.contains("power") || lower.contains("bescom") -> DetectedCategory.ELECTRICITY
            lower.contains("jio") || lower.contains("airtel") || lower.contains("broadband") || lower.contains("recharge") -> DetectedCategory.INTERNET_RECHARGE
            lower.contains("emi") || lower.contains("loan") -> DetectedCategory.LOAN_EMI
            lower.contains("sip") || lower.contains("mutual fund") || lower.contains("zerodha") || lower.contains("groww") -> DetectedCategory.INVESTMENT_SIP
            lower.contains("amazon") || lower.contains("flipkart") || lower.contains("myntra") || lower.contains("swiggy") || lower.contains("zomato") -> DetectedCategory.SHOPPING
            lower.contains("transfer to") || lower.contains("vpa") || lower.contains("upi") -> DetectedCategory.PEER_TRANSFER
            else -> DetectedCategory.GENERAL
        }

        // Extract merchant or party
        val toMatch = Regex("""(?:towards|to|vpa|at)\s+([A-Za-z0-9\s._@-]+?)(?:\s+on|\s+ref|\s+txn|\s+upi|\s*\.)""", RegexOption.IGNORE_CASE).find(text)
        val merchant = toMatch?.groupValues?.getOrNull(1)?.trim() ?: sender

        // Extract 10-digit phone number if present
        val phoneMatch = Regex("""\b(\d{10})\b""").find(text)
        val phone = phoneMatch?.groupValues?.getOrNull(1) ?: ""

        // Match planned debit
        val matchedDebit = plannedDebits.firstOrNull { debit ->
            !debit.isPaid && Math.abs(debit.amount - amount) < 2.0 &&
            (debit.title.contains(merchant, ignoreCase = true) || merchant.contains(debit.title, ignoreCase = true))
        }

        return ParsedSmsTransaction(
            sender = sender,
            amount = amount,
            type = type,
            detectedCategory = category,
            merchantOrParty = merchant,
            phoneNumber = phone,
            timestamp = timestamp,
            originalSms = text,
            matchedPlannedDebitId = matchedDebit?.id,
            matchedPlannedDebitTitle = matchedDebit?.title
        )
    }

    fun readDeviceSms(
        context: Context,
        limit: Int = 50,
        plannedDebits: List<DebitItem> = emptyList()
    ): List<ParsedSmsTransaction> {
        val list = mutableListOf<ParsedSmsTransaction>()
        try {
            val uri = Uri.parse("content://sms/inbox")
            val projection = arrayOf("_id", "address", "body", "date")
            val cursor = context.contentResolver.query(uri, projection, null, null, "date DESC LIMIT $limit")
            cursor?.use {
                val bodyIdx = it.getColumnIndex("body")
                val addrIdx = it.getColumnIndex("address")
                val dateIdx = it.getColumnIndex("date")

                while (it.moveToNext()) {
                    val body = if (bodyIdx >= 0) it.getString(bodyIdx) ?: "" else ""
                    val addr = if (addrIdx >= 0) it.getString(addrIdx) ?: "" else ""
                    val date = if (dateIdx >= 0) it.getLong(dateIdx) else System.currentTimeMillis()

                    val parsed = parseMessage(body, addr, date, plannedDebits)
                    if (parsed != null) {
                        list.add(parsed)
                    }
                }
            }
        } catch (e: Exception) {
            // SMS permission might not be granted
        }
        return list
    }

    fun getSampleBankSmsList(): List<Pair<String, String>> {
        return listOf(
            "HDFC-Bank" to "Dear Customer, INR 15,000.00 debited from A/C **1234 to HDFC Home Loan EMI on 05-OCT-26. Ref: EMI98212.",
            "SBI-Bank" to "Your A/C **5678 debited by Rs 1,499.00 on 04-OCT-26 transfer to Airtel Fiber WiFi. UPI Ref 3829102.",
            "ICICI-Bank" to "Dear Customer, INR 5,000.00 credited to A/C **9012 towards Zerodha Mutual Fund SIP dividend.",
            "AXIS-Bank" to "Alert: INR 3,250.00 debited from A/C **4321 at Amazon Shopping on 03-OCT-26. Avail Bal: INR 48,200.00."
        )
    }
}
