package com.example.service.gemini

import android.util.Log
import com.example.BuildConfig
import com.example.data.bank.BankTxType
import com.example.data.model.TransactionClassificationResult
import com.example.data.model.TransactionType
import com.example.service.TransactionParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Robust classification engine using Google Gemini (gemini-3.5-flash) to parse
 * SMS and notification strings for financial transaction details.
 */
class GeminiClassificationEngine(
    private val customApiKey: String? = null
) {
    companion object {
        private const val TAG = "GeminiClassifier"
        private const val GEMINI_MODEL = "gemini-3.5-flash"
        private const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

        val SYSTEM_INSTRUCTION = """
            You are an expert financial transaction classification engine for an Indian personal finance app (FinMoney).
            Your job is to parse raw SMS or Android status bar notification strings and extract transaction details.
            
            You must return a JSON object with the following fields:
            - is_financial (boolean): Strictly true ONLY if the text represents an actual completed monetary debit or credit from a bank account, credit card, or UPI wallet. Set to FALSE for all promotional messages, offers, upcoming bill due reminders, recharge pack expiry alerts, loan advertisements, scratch cards, reward points, and collect/payment requests.
            - date_iso (string or null): ISO-8601 date string if a date is mentioned (e.g. "2026-10-25T14:30:00Z"), or null.
            - amount (number or null): Actual settled transaction monetary amount in INR without commas. Must be positive. Null if no transaction amount or if non-financial.
            - sender (string): The financial institution, bank, or payment app name (e.g. "HDFC Bank", "State Bank of India", "Google Pay", "PhonePe", "Paytm", "CRED", "Axis Bank", "ICICI Bank").
            - type (string): "CREDIT" or "DEBIT".
              * Use "CREDIT" ONLY for actual money received, settled refunds, settled cashback credited to account, salary deposits, transfers to you.
              * Use "DEBIT" ONLY for money paid, spent, sent, ATM withdrawals, auto-debits, bill payments already completed.
            - merchant (string): The counterparty, payee, or merchant involved (e.g. "Swiggy", "Zomato", "Uber", "Chai Point", "Ramesh Kumar", "Amazon").
            - category (string): One of ["OUTSIDE_FOOD", "GROCERIES", "SHOPPING", "RECHARGE", "ELECTRICITY", "INTERNET", "FUEL", "TRAVEL", "OTT", "PHARMACY", "INVESTMENT", "EMI", "INSURANCE", "RENT", "MAINTENANCE", "SALARY", "GENERAL"].
            - account_ending (string): Last 4 digits of the bank account or card (e.g. "1234"), or empty string if not mentioned.
            - reference_number (string): UTR, transaction ID, or reference number, or empty string.
            - available_balance (number or null): Available balance or credit limit after transaction if mentioned, else null.
            
            Strict Edge Cases (CRITICAL):
            1. PROMOTIONS & MARKETING (Set is_financial to FALSE):
               - Cashback offers ("Win up to ₹500 cashback", "Flat ₹50 cashback on next recharge", "Scratch card waiting").
               - Discount promos ("Flat 50% off", "Save up to ₹2,000", "Use code SAVE50", "Exclusive deal").
               - Loan offers ("Pre-approved personal loan of ₹5,00,000", "Instant loan up to ₹50,000", "Eligible for credit line").
               - Credit card marketing ("Apply for lifetime free credit card", "Credit limit increased to ₹2,00,000").
               - Referral rewards ("Refer a friend and earn ₹100").
            2. UPCOMING BILL DUE NOTICES (Set is_financial to FALSE):
               - "Your electricity/credit card bill of ₹1,450 is generated / is due on 30-Oct. Pay now." (NOT an actual debit yet).
            3. RECHARGE & DATA ALERTS (Set is_financial to FALSE):
               - "Daily data exhausted. Recharge with ₹299 now", "Plan expiring in 2 days".
            4. PAYMENT REQUESTS (Set is_financial to FALSE):
               - "Rahul has requested ₹500 on PhonePe", "Approve collect request".
            5. OTPs, LOGIN CODES, DELIVERY TRACKERS (Set is_financial to FALSE).
            6. NEVER confuse available balance or loan limit with the transaction amount.
        """.trimIndent()
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun getResolvedApiKey(): String {
        val custom = customApiKey?.trim()
        if (!custom.isNullOrBlank() && custom != "default_gemini_key") {
            return custom
        }
        val buildKey = BuildConfig.GEMINI_API_KEY.trim()
        if (buildKey.isNotBlank() && buildKey != "default_gemini_key") {
            return buildKey
        }
        return ""
    }

    /**
     * Parses the SMS/notification string using Gemini REST API.
     * Automatically falls back to the high-accuracy local regex/NLP heuristics engine
     * if the API key is not configured or network request fails.
     */
    suspend fun parseNotification(
        rawText: String,
        senderOrTitle: String = "",
        timestamp: Long = System.currentTimeMillis()
    ): TransactionClassificationResult = withContext(Dispatchers.IO) {
        val apiKey = getResolvedApiKey()
        if (apiKey.isBlank()) {
            Log.d(TAG, "No valid Gemini API key configured. Utilizing high-accuracy heuristic parser.")
            return@withContext fallbackLocalParse(rawText, senderOrTitle, timestamp)
        }

        try {
            val endpoint = "$GEMINI_BASE_URL/$GEMINI_MODEL:generateContent?key=$apiKey"
            val promptText = "Sender/Title: $senderOrTitle\nNotification/SMS Text: $rawText\nTimestamp: $timestamp"

            val requestJson = JSONObject().apply {
                val contents = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().put("text", promptText))
                        }
                        put("parts", parts)
                    }
                    put(contentObj)
                }
                put("contents", contents)

                val sysInstruction = JSONObject().apply {
                    val parts = JSONArray().apply {
                        put(JSONObject().put("text", SYSTEM_INSTRUCTION))
                    }
                    put("parts", parts)
                }
                put("systemInstruction", sysInstruction)

                val genConfig = JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.1)
                }
                put("generationConfig", genConfig)
            }

            val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val code = response.code
                val errBody = response.body?.string()
                Log.w(TAG, "Gemini API call failed (HTTP $code): $errBody. Falling back to heuristic parse.")
                return@withContext fallbackLocalParse(rawText, senderOrTitle, timestamp)
            }

            val responseStr = response.body?.string() ?: ""
            val jsonRoot = JSONObject(responseStr)
            val candidates = jsonRoot.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val textOutput = parts?.optJSONObject(0)?.optString("text", "") ?: ""

            if (textOutput.isBlank()) {
                Log.w(TAG, "Empty text from Gemini response. Falling back to heuristic parse.")
                return@withContext fallbackLocalParse(rawText, senderOrTitle, timestamp)
            }

            parseGeminiJsonResponse(textOutput, rawText, senderOrTitle, timestamp)
        } catch (e: Exception) {
            Log.w(TAG, "Exception during Gemini parsing: ${e.message}. Falling back to heuristic parse.")
            fallbackLocalParse(rawText, senderOrTitle, timestamp)
        }
    }

    private fun parseGeminiJsonResponse(
        jsonString: String,
        rawText: String,
        senderOrTitle: String,
        timestamp: Long
    ): TransactionClassificationResult {
        return try {
            val json = JSONObject(jsonString)
            val isFinancial = json.optBoolean("is_financial", true)
            val amount = json.optDouble("amount", 0.0)
            val sender = json.optString("sender", senderOrTitle.ifBlank { "Bank Account" })
            val typeStr = json.optString("type", "DEBIT").uppercase()
            val type = if (typeStr == "CREDIT" || typeStr == "INCOME") TransactionType.CREDIT else TransactionType.DEBIT
            val merchant = json.optString("merchant", "")
            val category = json.optString("category", "GENERAL")
            val accountEnding = json.optString("account_ending", "")
            val referenceNumber = json.optString("reference_number", "")
            val availableBalance = if (json.has("available_balance") && !json.isNull("available_balance")) {
                json.optDouble("available_balance")
            } else null

            val dateIso = json.optString("date_iso", "")
            val parsedDate = if (dateIso.isNotBlank()) {
                parseIsoDate(dateIso) ?: timestamp
            } else {
                timestamp
            }

            if (!isFinancial) {
                return TransactionClassificationResult(
                    date = parsedDate,
                    amount = 0.0,
                    sender = sender,
                    type = type,
                    merchant = merchant,
                    category = "NON_FINANCIAL",
                    accountEnding = accountEnding,
                    referenceNumber = referenceNumber,
                    availableBalance = availableBalance,
                    isFinancial = false,
                    rawText = rawText,
                    confidence = 0.99f,
                    source = "GEMINI_AI"
                )
            }

            if (amount <= 0.0) {
                // If Gemini marked as financial but couldn't detect amount, consult local heuristic parser
                val heuristic = fallbackLocalParse(rawText, senderOrTitle, timestamp)
                if (heuristic.isFinancial && heuristic.amount > 0.0) {
                    return heuristic.copy(
                        sender = sender.ifBlank { heuristic.sender },
                        merchant = merchant.ifBlank { heuristic.merchant },
                        category = if (category != "GENERAL") category else heuristic.category,
                        source = "GEMINI_HYBRID"
                    )
                } else {
                    return TransactionClassificationResult(
                        date = parsedDate,
                        amount = 0.0,
                        sender = sender,
                        type = type,
                        isFinancial = false,
                        rawText = rawText,
                        source = "GEMINI_AI"
                    )
                }
            }

            TransactionClassificationResult(
                date = parsedDate,
                amount = amount,
                sender = sender,
                type = type,
                merchant = merchant,
                category = category,
                accountEnding = accountEnding,
                referenceNumber = referenceNumber,
                availableBalance = availableBalance,
                isFinancial = isFinancial,
                rawText = rawText,
                confidence = 0.95f,
                source = "GEMINI_AI"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not deserialize Gemini JSON ($jsonString): ${e.message}")
            fallbackLocalParse(rawText, senderOrTitle, timestamp)
        }
    }

    /**
     * Local deterministic fallback parser utilizing the app's NLP and Regex engine.
     */
    fun fallbackLocalParse(
        rawText: String,
        senderOrTitle: String = "",
        timestamp: Long = System.currentTimeMillis()
    ): TransactionClassificationResult {
        val parsed = TransactionParser.parse(
            rawText = rawText,
            senderOrTitle = senderOrTitle,
            timestamp = timestamp
        )

        val txType = if (parsed.transactionType == BankTxType.CREDIT) TransactionType.CREDIT else TransactionType.DEBIT

        val detectedCategory = when {
            txType == TransactionType.CREDIT && (rawText.contains("salary", ignoreCase = true) || rawText.contains("stipend", ignoreCase = true)) -> "SALARY"
            txType == TransactionType.CREDIT && (rawText.contains("refund", ignoreCase = true) || rawText.contains("reversal", ignoreCase = true)) -> "REFUND"
            txType == TransactionType.CREDIT && rawText.contains("cashback", ignoreCase = true) -> "CASHBACK"
            else -> parsed.category
        }

        return TransactionClassificationResult(
            date = timestamp,
            amount = parsed.amount ?: 0.0,
            sender = parsed.bankName.ifBlank { senderOrTitle.ifBlank { "Bank Account" } },
            type = txType,
            merchant = parsed.merchantName,
            category = detectedCategory,
            accountEnding = parsed.accountEnding,
            referenceNumber = parsed.referenceNumber,
            availableBalance = parsed.availableBalance,
            isFinancial = parsed.isFinancialNotification,
            rawText = rawText,
            confidence = parsed.confidenceScore,
            source = "HEURISTIC_LOCAL"
        )
    }

    private fun parseIsoDate(iso: String): Long? {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            format.parse(iso)?.time
        } catch (e: Exception) {
            try {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso)?.time
            } catch (_: Exception) {
                null
            }
        }
    }
}
