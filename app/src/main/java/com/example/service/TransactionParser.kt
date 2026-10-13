package com.example.service

import com.example.data.bank.BankTransactionParser
import com.example.data.bank.BankTxType
import com.example.data.bank.ParsedBankTransaction
import com.example.data.bank.TransactionClassificationEngine
import com.example.data.model.DebitCategory
import java.util.UUID

/**
 * Result model representing extracted fields from raw notification/SMS text.
 */
data class TransactionParseResult(
    val id: String = UUID.randomUUID().toString(),
    val amount: Double?,
    val accountEnding: String,
    val availableBalance: Double?,
    val bankName: String,
    val transactionType: BankTxType,
    val merchantName: String,
    val category: String,
    val referenceNumber: String,
    val classification: String,
    val nlpKeywords: List<String>,
    val confidenceScore: Float,
    val isFinancialNotification: Boolean,
    val debugReason: String? = null,
    val rawText: String,
    val senderOrTitle: String,
    val sourcePackage: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Converts result to [ParsedBankTransaction] if valid financial transaction.
     */
    fun toParsedBankTransaction(): ParsedBankTransaction? {
        if (!isFinancialNotification) return null
        val finalAmount = amount ?: availableBalance ?: return null
        if (finalAmount <= 0.0) return null

        return ParsedBankTransaction(
            id = id,
            bankName = bankName,
            accountNumberLast4 = accountEnding,
            amount = finalAmount,
            type = transactionType,
            classification = classification,
            availableBalance = availableBalance,
            merchantOrParty = merchantName,
            category = category,
            referenceNumber = referenceNumber,
            sourcePackage = sourcePackage,
            rawText = rawText,
            timestamp = timestamp
        )
    }

    /**
     * Formats result to standard FinMoney JSON Schema with debug_reason.
     */
    fun toJsonString(): String {
        val isoTimestamp = try {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(java.util.Date(timestamp))
        } catch (e: Exception) {
            null
        }

        val lowerRaw = rawText.lowercase()

        val txTypeStr = when {
            !isFinancialNotification -> "UNKNOWN"
            (lowerRaw.contains("balance") || lowerRaw.contains("bal")) && amount == null -> "BALANCE_CHECK"
            transactionType == BankTxType.CREDIT -> "CREDIT"
            transactionType == BankTxType.DEBIT -> "DEBIT"
            else -> "UNKNOWN"
        }

        val accTypeStr = when {
            classification == "Credit Card" || lowerRaw.contains("credit card") -> "CREDIT_CARD"
            lowerRaw.contains("upi") -> "UPI"
            sourcePackage.contains("paytm") || sourcePackage.contains("phonepe") -> "WALLETS"
            else -> "ACCOUNT"
        }

        val modeStr = when {
            lowerRaw.contains("upi") || lowerRaw.contains("@") -> "UPI"
            lowerRaw.contains("card") || classification == "Credit Card" -> "CARD"
            lowerRaw.contains("atm") -> "ATM"
            lowerRaw.contains("netbanking") || lowerRaw.contains("neft") || lowerRaw.contains("rtgs") || lowerRaw.contains("imps") -> "NET_BANKING"
            else -> "UNKNOWN"
        }

        val cleanMerchant = if (merchantName.isNotBlank() && merchantName != bankName) "\"${merchantName.replace("\"", "\\\"")}\"" else "null"
        val cleanAccId = if (accountEnding.isNotBlank()) "XX$accountEnding" else bankName
        val cleanBal = availableBalance?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "null"
        val cleanTimestamp = isoTimestamp?.let { "\"$it\"" } ?: "null"
        val cleanAmt = amount?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "0.00"
        val cleanDebugReason = debugReason?.let { "\"${it.replace("\"", "\\\"")}\"" } ?: "null"

        return """
{
  "is_financial_transaction": $isFinancialNotification,
  "transaction_type": "$txTypeStr",
  "transaction_amount": $cleanAmt,
  "currency": "INR",
  "account_type": "$accTypeStr",
  "account_identifier": "$cleanAccId",
  "payment_mode": "$modeStr",
  "merchant_name": $cleanMerchant,
  "available_balance": $cleanBal,
  "timestamp": $cleanTimestamp,
  "debug_reason": $cleanDebugReason
}
""".trimIndent()
    }
}

/**
 * Service that uses advanced Regex patterns and Natural Language Processing (NLP) keyword extraction
 * to intercept, parse, and identify bank transaction details, account endings, amounts, merchants,
 * and available balances from raw text intercepted by [NotificationListenerService].
 */
object TransactionParser {

    const val GEMINI_PARSER_SYSTEM_PROMPT = """
You are a highly accurate financial transaction parser for the FinMoney app. Your sole task is to extract structured transaction data from raw Android/iOS notification text or SMS.

### Processing Instructions:
1. Analyze the input notification text carefully.
2. Determine if the text represents a valid financial transaction (Debit, Credit, or Balance Enquiry/Alert).
3. Ignore promotional messages, OTPs, login alerts, spam, or failed transaction notifications by setting "is_financial_transaction" to false.
4. Clean and normalize the data: Remove commas from amounts, format dates to ISO 8601 (YYYY-MM-DD), and extract the clean merchant/sender name.

### Advanced Edge-Case & Spam Elimination Rules:
You must strictly classify "is_financial_transaction": false for the following specific patterns. If a notification matches any of these, discard it immediately:

1. DELIVERY & ORDER STATUS PINGS (NOT TRANSACTIONS):
   - Messages from food/grocery/e-commerce apps tracking delivery status, even if mentioning an amount paid.
   - Examples: "Your Swiggy order of Rs. 450 is on the way!", "Zomato: Valet has picked up your order worth ₹320."
   - Set: "debug_reason": "Matches delivery/order status tracking pattern"

2. PERSONAL CHATS & PEER MESSAGES:
   - Notifications from WhatsApp, Telegram, or SMS where a human friend is typing a message about money.
   - Examples: "Hey, I sent you 500 bucks yesterday check it", "Can you GPAY me Rs 1200 for dinner?"
   - Set: "debug_reason": "Matches personal chat message pattern"

3. LATE PAYMENT REMINDERS & BILL GENERATION ALERTS:
   - Notifications stating a bill is due or has been generated, rather than an actual debit taking place.
   - Examples: "Your Airtel postpaid bill of Rs. 799 is generated", "Reminder: Pay your HDFC credit card due of ₹15,000 before tomorrow."
   - Set: "debug_reason": "Matches bill due/generated reminder pattern"

4. CASHBACK ELIGIBILITY & REWARD LOOPS:
   - Promotional text trying to get you to spend or claim rewards.
   - Examples: "You won a scratch card up to ₹500! Tap to claim", "Get 5% cashback on your next transaction."
   - Set: "debug_reason": "Matches scratch card/reward promotional pattern"

5. TRANSACTION FAILURES/DECLINES:
   - Any notification mentioning "declined", "insufficient funds", "failed", "reversed", "timed out", "unsuccessful", "could not be processed".
   - Set: "debug_reason": "Matches transaction failure/decline pattern"

6. BANKING ALERTS & REMINDERS:
   - Password expirations, KYC updates, login alerts, statement availability, balance minimum alerts.
   - Set: "debug_reason": "Matches banking alert/reminder pattern"

7. ONE-TIME PASSWORDS (OTP):
   - Any message containing "OTP", "verification code", "Do not share", "login code".
   - Set: "debug_reason": "Matches OTP/verification code pattern"

### Field Rules:
- transaction_type: Must be exactly "DEBIT", "CREDIT", "BALANCE_CHECK", or "UNKNOWN".
- account_type: Identify if it is "ACCOUNT" (Savings/Current), "CREDIT_CARD", "WALLETS", or "UPI".
- payment_mode: Deduce if it is "UPI", "CARD", "NET_BANKING", "ATM", or "UNKNOWN".
- transaction_amount & available_balance: Must be absolute floating-point numbers. Extract only if explicitly mentioned.
- debug_reason: If "is_financial_transaction" is false, provide the reason string explaining why it was dropped. If true, set to null.

### Output Format:
Return ONLY a valid JSON object matching the schema below. Do not include markdown formatting (like ```json), conversational filler, or explanations.

{
  "is_financial_transaction": true/false,
  "transaction_type": "DEBIT" | "CREDIT" | "BALANCE_CHECK" | "UNKNOWN",
  "transaction_amount": 0.00,
  "currency": "INR",
  "account_type": "ACCOUNT" | "CREDIT_CARD" | "WALLETS" | "UPI" | "UNKNOWN",
  "account_identifier": "String (e.g., XX1234 or HDFC Card)",
  "payment_mode": "UPI" | "CARD" | "NET_BANKING" | "ATM" | "UNKNOWN",
  "merchant_name": "String or null",
  "available_balance": 0.00 or null,
  "timestamp": "ISO Date String or null",
  "debug_reason": "String or null"
}
"""

    private val STOPWORDS = setOf(
        "a", "an", "the", "in", "on", "at", "to", "for", "of", "with", "by", "is", "are", "was",
        "were", "be", "been", "your", "my", "our", "this", "that", "dear", "customer", "user",
        "has", "have", "had", "and", "or", "from", "as", "no", "num", "number", "id"
    )

    private val FINANCIAL_VERBS = mapOf(
        "debited" to BankTxType.DEBIT,
        "debit" to BankTxType.DEBIT,
        "spent" to BankTxType.DEBIT,
        "paid" to BankTxType.DEBIT,
        "sent" to BankTxType.DEBIT,
        "withdrawn" to BankTxType.DEBIT,
        "charged" to BankTxType.DEBIT,
        "transferred" to BankTxType.DEBIT,
        "transfer" to BankTxType.DEBIT,
        "purchased" to BankTxType.DEBIT,
        "credited" to BankTxType.CREDIT,
        "credit" to BankTxType.CREDIT,
        "received" to BankTxType.CREDIT,
        "recd" to BankTxType.CREDIT,
        "rec'd" to BankTxType.CREDIT,
        "cr" to BankTxType.CREDIT,
        "cr." to BankTxType.CREDIT,
        "cr:" to BankTxType.CREDIT,
        "deposited" to BankTxType.CREDIT,
        "refunded" to BankTxType.CREDIT,
        "added" to BankTxType.CREDIT,
        "inward" to BankTxType.CREDIT,
        "incoming" to BankTxType.CREDIT,
        "salary" to BankTxType.CREDIT,
        "cashback" to BankTxType.CREDIT,
        "got" to BankTxType.CREDIT,
        "remittance" to BankTxType.CREDIT
    )

    /**
     * Core NLP & Regex parser method to evaluate raw text from notification tray or SMS.
     */
    fun parse(
        rawText: String,
        senderOrTitle: String,
        sourcePackage: String = "",
        timestamp: Long = System.currentTimeMillis()
    ): TransactionParseResult {
        val normalizedText = rawText.replace("&nbsp;", " ").replace("\u00A0", " ").trim()
        val combined = "$senderOrTitle $normalizedText"
        val lowerText = combined.lowercase()

        val nonFinancialReason = determineNonFinancialReason(normalizedText, senderOrTitle, sourcePackage)
        val isFinancial = (nonFinancialReason == null)

        // 1. Amount & Available Balance Extraction using multi-stage Regex
        val txAmount = extractAmount(normalizedText)
        val avlBalance = extractAvailableBalance(normalizedText)

        val finalAmount = txAmount ?: if (avlBalance != null && avlBalance > 0.0) avlBalance else null

        // 2. Account / Card Last 4 Digits Extraction
        val accountEnding = extractAccountEnding(normalizedText)

        // 3. Bank Identification
        val bankName = identifyBankName(senderOrTitle, normalizedText)

        // 4. Transaction Type Determination
        val txType = determineTransactionType(lowerText)

        // 5. Merchant & Counterparty Extraction with Deep Brand Enrichment
        val rawMerchant = extractMerchantName(normalizedText, senderOrTitle, bankName)
        val enriched = com.example.data.bank.MerchantEnrichmentEngine.enrich(rawMerchant, normalizedText, senderOrTitle)
        val merchantName = if (enriched.isVerifiedBrand) enriched.cleanName else rawMerchant.ifBlank { enriched.cleanName }

        // 6. Category Identification (Prioritize verified brand category, fallback to NLP)
        val category = if (enriched.isVerifiedBrand && enriched.categoryKey != DebitCategory.GENERAL.name) {
            enriched.categoryKey
        } else {
            identifyCategoryNLP(normalizedText, senderOrTitle, merchantName)
        }

        // 7. Reference Number Extraction
        val refNumber = extractReferenceNumber(normalizedText)

        // 8. Classification (Bank, Credit Card, Merchant)
        val classification = TransactionClassificationEngine.classifyLabel(
            text = normalizedText,
            senderOrTitle = senderOrTitle,
            extractedMerchant = merchantName,
            bankName = bankName
        )

        // 9. NLP Keyword Tokenization & Extraction
        val nlpKeywords = extractKeywordsNLP(combined)

        // 10. Confidence Score Calculation
        val confidence = calculateConfidenceScore(
            isFinancial = isFinancial,
            amount = finalAmount,
            accountEnding = accountEnding,
            bankName = bankName,
            merchantName = merchantName,
            nlpKeywords = nlpKeywords
        )

        return TransactionParseResult(
            amount = finalAmount,
            accountEnding = accountEnding,
            availableBalance = avlBalance,
            bankName = bankName,
            transactionType = txType,
            merchantName = merchantName,
            category = category,
            referenceNumber = refNumber,
            classification = classification,
            nlpKeywords = nlpKeywords,
            confidenceScore = confidence,
            isFinancialNotification = isFinancial,
            debugReason = nonFinancialReason,
            rawText = rawText,
            senderOrTitle = senderOrTitle,
            sourcePackage = sourcePackage,
            timestamp = timestamp
        )
    }

    /**
     * Determines why a notification is classified as non-financial. Returns null if valid financial.
     */
    fun determineNonFinancialReason(text: String, title: String = "", sourcePackage: String = ""): String? {
        val lower = "$title $text".lowercase()

        // 1. Delivery & Order Status Pings
        if (lower.contains("is on the way") || lower.contains("picked up your order") || 
            lower.contains("out for delivery") || lower.contains("valet has picked") || 
            lower.contains("order placed") || lower.contains("preparing your order") || 
            lower.contains("dispatched") || lower.contains("arriving soon") || 
            lower.contains("track delivery") || lower.contains("driver is on")) {
            return "Matches delivery/order status tracking pattern"
        }

        // 2. Personal Chats & Peer Messages
        if (sourcePackage.contains("whatsapp") || sourcePackage.contains("telegram") || sourcePackage.contains("signal")) {
            if (!lower.contains("debited") && !lower.contains("credited") && !lower.contains("avl bal")) {
                return "Matches personal chat message pattern"
            }
        }
        if (lower.contains("bucks") || lower.contains("check it") || lower.contains("send me") || 
            lower.contains("gpay me") || lower.contains("phonepe me") || lower.contains("pay me")) {
            if (!lower.contains("debited") && !lower.contains("credited") && !lower.contains("avl bal")) {
                return "Matches personal chat message pattern"
            }
        }

        // 3. Late Payment Reminders & Bill Generation
        if (lower.contains("bill generated") || lower.contains("is generated") || 
            lower.contains("bill of rs") || lower.contains("bill of ₹") || 
            lower.contains("due of") || lower.contains("due date") || 
            lower.contains("bill due") || lower.contains("pay before") || 
            lower.contains("reminder: pay") || lower.contains("bill is due")) {
            if (!lower.contains("debited") && !lower.contains("credited") && !lower.contains("paid rs") && !lower.contains("spent")) {
                return "Matches bill due/generated reminder pattern"
            }
        }

        // 4. Cashback Eligibility & Reward Loops
        if (lower.contains("scratch card") || lower.contains("tap to claim") || 
            lower.contains("win up to") || lower.contains("won a scratch") || 
            lower.contains("claim your") || lower.contains("cashback on your next") || 
            lower.contains("earn up to")) {
            return "Matches scratch card/reward promotional pattern"
        }

        // 5. One-Time Passwords (OTP) & Security codes
        if (lower.contains("otp") || lower.contains("verification code") || 
            lower.contains("login code") || lower.contains("do not share") || 
            lower.contains("one time password") || lower.contains("security code")) {
            if (!lower.contains("debited") && !lower.contains("credited") && !lower.contains("paid rs") && !lower.contains("spent")) {
                return "Matches OTP/verification code pattern"
            }
        }

        // 6. Transaction Failures / Declines / Reversals
        if (lower.contains("declined") || lower.contains("insufficient funds") || 
            lower.contains("transaction failed") || lower.contains("payment failed") || 
            lower.contains("failed due to") || lower.contains("could not be processed") || 
            lower.contains("timed out") || lower.contains("unsuccessful") || lower.contains("declined due to")) {
            return "Matches transaction failure/decline pattern"
        }

        // 7. Banking Alerts & Informational Reminders
        if (lower.contains("password expire") || lower.contains("password will expire") || 
            lower.contains("kyc update") || lower.contains("login alert") || 
            lower.contains("statement available") || lower.contains("minimum balance") || 
            lower.contains("maintain balance") || lower.contains("security alert")) {
            return "Matches banking alert/reminder pattern"
        }

        // 8. Credit Card Promotions & Marketing Offers
        if (lower.contains("eligible for") || lower.contains("pre-approved") || 
            lower.contains("pre approved") || lower.contains("limit increase") || 
            lower.contains("apply now") || lower.contains("10% off") || 
            lower.contains("discount on") || lower.contains("cashback offer") || 
            lower.contains("personal loan") || lower.contains("credit card offer")) {
            return "Matches promotional/marketing offer pattern"
        }

        // 9. Engagement Pings & App Marketing Spam
        if (lower.contains("update available") || lower.contains("rate us") || 
            lower.contains("spin to win") || lower.contains("claim your reward") || 
            lower.contains("exclusive offer")) {
            return "Matches app marketing/engagement spam pattern"
        }

        val hasTxKeyword = lower.contains("debited") || lower.contains("spent") ||
                lower.contains("paid") || lower.contains("credited") ||
                lower.contains("received") || lower.contains("sent") ||
                lower.contains("withdrawn") || lower.contains("refund") ||
                lower.contains("charged") || lower.contains("transferred") ||
                lower.contains("recd") || lower.contains("cr") ||
                lower.contains("deposit") || lower.contains("added") ||
                lower.contains("inward") || lower.contains("incoming") ||
                lower.contains("salary") || lower.contains("remittance") ||
                lower.contains("cashback") || lower.contains("got") ||
                lower.contains("avl bal") || lower.contains("available bal") ||
                lower.contains("bal") || lower.contains("balance") ||
                lower.contains("limit") || lower.contains("a/c") || lower.contains("account") ||
                lower.contains("chatbanking") || lower.contains("reaching out") || lower.contains("upi")

        val hasCurrency = lower.contains("inr") || lower.contains("rs") ||
                lower.contains("₹") || lower.contains("r.s") || lower.contains("rupees") || lower.contains("usd")

        val isKnownBank = lower.contains("hdfc") || lower.contains("sbi") ||
                lower.contains("icici") || lower.contains("axis") || lower.contains("kotak") ||
                lower.contains("pnb") || lower.contains("canara") || lower.contains("union") ||
                lower.contains("idfc") || lower.contains("onecard") || lower.contains("slice") ||
                lower.contains("amex") || lower.contains("bank") || lower.contains("card") ||
                lower.contains("paytm") || lower.contains("phonepe") || lower.contains("gpay")

        val isValid = (hasTxKeyword && hasCurrency) || (isKnownBank && (hasCurrency || lower.contains("bal") || lower.contains("balance") || lower.contains("limit")))

        return if (!isValid) "Non-financial message content" else null
    }

    /**
     * Determines whether text represents a financial alert or balance update.
     */
    fun isFinancialText(text: String, title: String = ""): Boolean {
        return determineNonFinancialReason(text, title) == null
    }

    /**
     * Robust Regex patterns to extract transaction amount.
     */
    fun extractAmount(text: String): Double? {
        val normalizedText = text.replace("&nbsp;", " ").replace("\u00A0", " ")

        val regexes = listOf(
            // 1. Currency prefix: INR 1,500.00 / Rs. 3,021.57 / ₹1,500/- / Rs 1500 / INR 450.00 / ₹ 500
            Regex("""(?:inr|rs\.?|₹|usd)\s*([\d,]+(?:\.\d{1,2})?)(?:\/\-|\/\=)?""", RegexOption.IGNORE_CASE),
            // 2. Verb followed by currency or amount: debited by 1,500.00 / paid Rs. 500 / received Rs 500 / recd 500 / cr 500 / added 500
            Regex("""(?:debited|credited|paid|sent|spent|charged|withdrawn|transferred|transfer|withdrawal|done|received|recd|rec'd|cr|added|deposited|got)\s+(?:by|with|for|of)?\s*(?:inr|rs\.?|₹|usd)?\s*([\d,]+(?:\.\d{1,2})?)(?:\/\-|\/\=)?""", RegexOption.IGNORE_CASE),
            // 3. Amount followed by verb or currency: 1,500.00 INR / 1500 Rs / 1,500.00 debited / 500.00 paid / 500 recd / 500 credited
            Regex("""([\d,]+(?:\.\d{1,2})?)(?:\/\-|\/\=)?\s*(?:inr|rs\.?|₹|usd|debited|credited|paid|spent|charged|received|recd|rec'd|cr|added)""", RegexOption.IGNORE_CASE),
            // 4. Mandate / Autopay / ATM / SIP / UPI / Salary / Deposit: autopay of 1200 / mandate of 1500 / upi 500 / salary 50000
            Regex("""(?:autopay|mandate|sip|atm|upi|salary|deposit|refund|cashback|credit|remediation)\s+(?:of|for|by)?\s*(?:inr|rs\.?|₹|usd)?\s*([\d,]+(?:\.\d{1,2})?)(?:\/\-|\/\=)?""", RegexOption.IGNORE_CASE),
            // 5. Fallback balance/limit amount if no transaction amount is specified
            Regex("""(?:balance|bal|limit)\s*(?:is|:|-)?\s*(?:inr|rs\.?|₹|usd)?\s*([\d,]+(?:\.\d{1,2})?)(?:\/\-|\/\=)?""", RegexOption.IGNORE_CASE)
        )

        for (regex in regexes) {
            val match = regex.find(normalizedText)
            if (match != null) {
                val num = cleanNumber(match.groupValues[1])
                if (num != null && num > 0.0) return num
            }
        }
        return null
    }

    /**
     * Robust Regex patterns to extract available balance.
     */
    fun extractAvailableBalance(text: String): Double? {
        val normalizedText = text.replace("&nbsp;", " ").replace("\u00A0", " ")

        val balanceRegexes = listOf(
            // 1. Balance in account no. ending with 0989 is Rs. 3,021.57 / Balance in A/C XX0989 is Rs. 3,021.57
            Regex("""(?:bal|balance)\s+(?:in|for|of)?\s*(?:a\/c|account|acct|card)?\s*(?:no\.?|num\.?)?\s*(?:ending\s+with\s+|ending\s+in\s+|ending\s+|XX|\*\*|\*)*\s*\d*\s*(?:is|:|-)?\s*(?:inr|rs\.?|₹|usd)?\s*([\d,]+(?:\.\d{1,2})?)(?:\/\-|\/\=)?""", RegexOption.IGNORE_CASE),
            // 2. Available Limit / Credit Limit / Avl Limit / Total Limit
            Regex("""(?:avail|available|total|net|card)?\s*(?:credit\s*)?limit\s*(?:is|:|-)?\s*(?:inr|rs\.?|₹|usd)?\s*([\d,]+(?:\.\d{1,2})?)(?:\/\-|\/\=)?""", RegexOption.IGNORE_CASE),
            // 3. Avl Bal: INR 35,420.50 or Available Balance: Rs. 12,000 or A/C ending 1234 balance is Rs. 45,000.00
            Regex("""(?:avl|avail|available|updated|total|net|clg|clear|eff|effective|cur|current|a\/c|account)?\s*(?:bal|balance)\s*(?:is|:|-|\s+in\s+your\s+account|\s+as\s+on\s+[A-Za-z0-9\-]+)?\s*(?:inr|rs\.?|₹|usd)?\s*([\d,]+(?:\.\d{1,2})?)(?:\/\-|\/\=)?""", RegexOption.IGNORE_CASE),
            // 4. Currency followed by balance: INR 35,000 is available balance
            Regex("""(?:inr|rs\.?|₹|usd)\s*([\d,]+(?:\.\d{1,2})?)(?:\/\-|\/\=)?\s*(?:is\s*)?(?:avail|available|avl)?\s*(?:bal|balance|limit)""", RegexOption.IGNORE_CASE),
            // 5. Amount followed by bal/balance: 35,000.00 is your balance / 35000 Avl Bal
            Regex("""([\d,]+(?:\.\d{1,2})?)(?:\/\-|\/\=)?\s*(?:inr|rs\.?|₹|usd)?\s*(?:is\s*)?(?:avail|available|avl)?\s*(?:bal|balance)""", RegexOption.IGNORE_CASE),
            // 6. Short bal: Rs 45000
            Regex("""\bbal(?:\.|\:|\s+is)?\s*(?:inr|rs\.?|₹|usd)?\s*([\d,]+(?:\.\d{1,2})?)(?:\/\-|\/\=)?""", RegexOption.IGNORE_CASE)
        )

        for (regex in balanceRegexes) {
            val match = regex.find(normalizedText)
            if (match != null) {
                val num = cleanNumber(match.groupValues[1])
                if (num != null && num >= 0.0) return num
            }
        }
        return null
    }

    /**
     * Extracts account or credit card last 4 digits using specialized Regex patterns.
     */
    fun extractAccountEnding(text: String): String {
        val regexes = listOf(
            // 1. "account no. ending with 0989", "A/C ending with 1234", "account ending 0989", "card ending 9821"
            Regex("""(?:a\/c|account|acct|card|ending|with)\s*(?:no\.?|num\.?)?\s*(?:ending\s+with\s+|ending\s+in\s+|ending\s+|with\s+)?[\*xX]{0,6}(\d{4})\b""", RegexOption.IGNORE_CASE),
            // 2. "XX5678", "**1234", "*9012"
            Regex("""[\*xX]{2,}(\d{4})\b"""),
            // 3. "x1234"
            Regex("""x(\d{4})\b""", RegexOption.IGNORE_CASE),
            // 4. "1234 is debited" / "5678 debited"
            Regex("""\b(\d{4})\s*(?:is debited|is credited|spent|charged|debited|credited|balance)""", RegexOption.IGNORE_CASE)
        )

        for (regex in regexes) {
            val match = regex.find(text)
            if (match != null) {
                val digits = match.groupValues[1]
                if (digits.length == 4) return digits
            }
        }
        return ""
    }

    /**
     * Identifies the issuing bank or financial institution.
     */
    fun identifyBankName(senderOrTitle: String, text: String): String {
        return BankTransactionParser.identifyBank(senderOrTitle, text)
    }

    /**
     * NLP-assisted merchant/party name extraction.
     */
    fun extractMerchantName(text: String, title: String, bankName: String): String {
        return BankTransactionParser.extractMerchant(text, title)
    }

    /**
     * Extracts reference / UTR number if present.
     */
    fun extractReferenceNumber(text: String): String {
        return BankTransactionParser.extractReference(text)
    }

    /**
     * NLP keyword extraction: tokenizes, filters stopwords, extracts financial terms, merchant, amount tokens.
     */
    fun extractKeywordsNLP(text: String): List<String> {
        val cleanText = text.lowercase()
            .replace(Regex("""[^a-z0-9\s₹\$\.\,]"""), " ")

        val tokens = cleanText.split(Regex("""\s+""")).filter { it.length > 1 }
        val keywords = mutableListOf<String>()

        for (token in tokens) {
            if (STOPWORDS.contains(token)) continue

            if (FINANCIAL_VERBS.containsKey(token)) {
                keywords.add("action:$token")
            } else if (token.contains("inr") || token.contains("rs") || token.startsWith("₹")) {
                keywords.add("currency:$token")
            } else if (token.matches(Regex("""\d{4}"""))) {
                keywords.add("ending:$token")
            } else if (token.matches(Regex("""\d+"""))) {
                keywords.add("num:$token")
            } else if (token.length >= 3) {
                keywords.add("term:$token")
            }
        }
        return keywords.distinct().take(15)
    }

    private fun determineTransactionType(lowerText: String): BankTxType {
        val isCredit = lowerText.contains("credited") || lowerText.contains("received") ||
                lowerText.contains("recd") || lowerText.contains("rec'd") || lowerText.contains("recd.") ||
                lowerText.contains("cr:") || lowerText.contains("cr.") || lowerText.contains(" cr ") ||
                lowerText.contains("cr of") || lowerText.contains("cr in") || lowerText.contains("cr to") ||
                lowerText.contains("deposited") || lowerText.contains("refunded") ||
                lowerText.contains("added to") || lowerText.contains("added") ||
                lowerText.contains("payment of rs") || lowerText.contains("payment received") ||
                lowerText.contains("money received") || lowerText.contains("credit") ||
                lowerText.contains("inward") || lowerText.contains("incoming") ||
                lowerText.contains("transferred to your") || lowerText.contains("salary") ||
                lowerText.contains("cashback") || lowerText.contains("remittance") ||
                lowerText.contains("got ₹") || lowerText.contains("got rs") ||
                lowerText.contains("account balance") || lowerText.contains("balance for account") ||
                lowerText.contains("chatbanking")

        return if (isCredit) BankTxType.CREDIT else BankTxType.DEBIT
    }

    fun identifyCategoryNLP(text: String, title: String = "", merchantName: String = ""): String {
        return BankTransactionParser.identifyCategory(text, title, merchantName)
    }

    private fun calculateConfidenceScore(
        isFinancial: Boolean,
        amount: Double?,
        accountEnding: String,
        bankName: String,
        merchantName: String,
        nlpKeywords: List<String>
    ): Float {
        if (!isFinancial) return 0.0f

        var score = 0.3f
        if (amount != null && amount > 0.0) score += 0.3f
        if (accountEnding.isNotBlank()) score += 0.2f
        if (bankName != "Bank Account") score += 0.1f
        if (merchantName.isNotBlank() && merchantName != bankName) score += 0.05f
        if (nlpKeywords.any { it.startsWith("action:") }) score += 0.05f

        return score.coerceAtMost(1.0f)
    }

    private fun cleanNumber(str: String): Double? {
        val clean = str.replace(",", "")
            .replace("/-", "")
            .replace("/=", "")
            .replace("₹", "")
            .replace("\u00A0", "")
            .trim()
        return clean.toDoubleOrNull()
    }

    /**
     * Directly parses raw notification text and returns standard JSON string adhering to FinMoney schema.
     */
    fun parseToJson(
        rawText: String,
        senderOrTitle: String = "",
        sourcePackage: String = "",
        timestamp: Long = System.currentTimeMillis()
    ): String {
        return parse(
            rawText = rawText,
            senderOrTitle = senderOrTitle,
            sourcePackage = sourcePackage,
            timestamp = timestamp
        ).toJsonString()
    }
}
