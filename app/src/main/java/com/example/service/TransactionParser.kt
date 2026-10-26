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
        val txAmount = amount ?: return null
        if (txAmount <= 0.0) return null

        return ParsedBankTransaction(
            id = id,
            bankName = bankName,
            accountNumberLast4 = accountEnding,
            amount = txAmount,
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
            lowerRaw.contains("debit card") || lowerRaw.contains("debitcard") -> "ACCOUNT"
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
        val txAmount = extractTransactionAmount(normalizedText)
        val avlBalance = extractAvailableBalance(normalizedText)

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
            amount = txAmount,
            accountEnding = accountEnding,
            bankName = bankName,
            merchantName = merchantName,
            nlpKeywords = nlpKeywords,
            referenceNumber = refNumber,
            hasExplicitTxVerb = NotificationTransactionGate.hasExplicitTransactionVerb(lowerText)
        )

        return TransactionParseResult(
            amount = txAmount,
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
            lower.contains("track delivery") || lower.contains("driver is on") ||
            lower.contains("track your order") || lower.contains("order confirmed") ||
            lower.contains("item shipped") || lower.contains("package arriving") ||
            lower.contains("delivery update") || lower.contains("table booked")) {
            if (!lower.contains("debited") && !lower.contains("paid rs") && !lower.contains("paid ₹")) {
                return "Matches delivery/order status tracking pattern"
            }
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

        // 3. Upcoming Bill Due Reminders & Bill Generation (NOT yet paid)
        if (lower.contains("bill generated") || lower.contains("is generated") || 
            lower.contains("bill of rs") || lower.contains("bill of ₹") || 
            lower.contains("due of") || lower.contains("due date") || 
            lower.contains("bill due") || lower.contains("bill is due") ||
            lower.contains("pay before") || lower.contains("reminder: pay") ||
            lower.contains("minimum amount due") || lower.contains("total amount due") ||
            lower.contains("statement is ready") || lower.contains("statement generated") ||
            lower.contains("avoid late fee") || lower.contains("overdue")) {
            val isAlreadyPaid = lower.contains("has been debited") || lower.contains("debited from") ||
                lower.contains("successfully paid") || lower.contains("payment received") ||
                lower.contains("bill paid") || lower.contains("paid successfully")
            if (!isAlreadyPaid) {
                return "Matches bill due/generated reminder pattern"
            }
        }

        // 4. Recharge Prompts, Plan Expiry & Data Alerts
        if (lower.contains("recharge with") || lower.contains("recharge now") ||
            lower.contains("recharge of rs") || lower.contains("recharge of ₹") ||
            lower.contains("plan expiring") || lower.contains("plan expires") ||
            lower.contains("pack expiring") || lower.contains("pack expires") ||
            lower.contains("validity expiring") || lower.contains("validity expires") ||
            lower.contains("data exhausted") || lower.contains("daily data quota") ||
            lower.contains("100% daily data") || lower.contains("unlimited calls and") ||
            lower.contains("topup now")) {
            val isAlreadyRecharged = lower.contains("recharge successful") ||
                (lower.contains("recharge of rs") && lower.contains("successful")) ||
                (lower.contains("debited") && lower.contains("recharge"))
            if (!isAlreadyRecharged) {
                return "Matches recharge prompt / plan expiry alert"
            }
        }

        // 5. Promotional Offers, Cashback Hooks & Shopping Discounts
        if (lower.contains("scratch card") || lower.contains("tap to claim") ||
            lower.contains("win up to") || lower.contains("won a scratch") ||
            lower.contains("claim your") || lower.contains("cashback on your next") ||
            lower.contains("earn up to") || lower.contains("spin to win") ||
            lower.contains("refer and earn") || lower.contains("refer & earn") ||
            lower.contains("lucky draw") || lower.contains("earn cashback") ||
            lower.contains("get cashback") || lower.contains("cashback offer") ||
            lower.contains("cashback rewards") || lower.contains("rewards unlocked") ||
            lower.contains("coupon code") || lower.contains("voucher inside") ||
            lower.contains("use code") || lower.contains("flat 50% off") ||
            lower.contains("flat 40% off") || lower.contains("flat 30% off") ||
            lower.contains("flat 20% off") || lower.contains("flat 10% off") ||
            lower.contains("save up to") || lower.contains("save upto") ||
            lower.contains("get up to") || lower.contains("discount of") ||
            lower.contains("voucher of") || lower.contains("coupon of") ||
            lower.contains("off on your") || lower.contains("deals starting") ||
            lower.contains("hurry") || lower.contains("don't miss") ||
            lower.contains("limited period offer") || lower.contains("limited time offer") ||
            lower.contains("exclusive deal") || lower.contains("special offer") ||
            lower.contains("festival sale") || lower.contains("mega sale") ||
            lower.contains("stand a chance to win")) {
            val isActualCashbackSettled = lower.contains("cashback credited") ||
                lower.contains("cashback received") || lower.contains("cashback of inr") ||
                lower.contains("cashback of rs") || lower.contains("cashback of ₹")
            if (!isActualCashbackSettled) {
                return "Matches scratch card/reward promotional pattern"
            }
        }

        // 6. Loan Marketing, Credit Limit & Card Advertisements
        if (lower.contains("pre-approved") || lower.contains("preapproved") ||
            lower.contains("instant loan") || lower.contains("personal loan") ||
            lower.contains("loan offer") || lower.contains("apply for loan") ||
            lower.contains("apply for credit card") || lower.contains("eligible for loan") ||
            lower.contains("credit line of") || lower.contains("instant cash up to") ||
            lower.contains("credit limit increased") || lower.contains("limit increase to") ||
            lower.contains("lifetime free credit card") || lower.contains("upgrade your card") ||
            lower.contains("zero processing fee") || lower.contains("instant disbursement")) {
            val isActualLoanDisbursement = (lower.contains("loan disbursed") || lower.contains("disbursement of")) &&
                (lower.contains("credited to") || lower.contains("credited in"))
            if (!isActualLoanDisbursement) {
                return "Matches loan or credit card marketing advertisement"
            }
        }

        // 7. Collect / Payment Requests (Unsettled incoming requests)
        if (lower.contains("payment request") || lower.contains("collect request") ||
            lower.contains("has requested") || lower.contains("is requesting") ||
            lower.contains("requested rs") || lower.contains("requested ₹") ||
            lower.contains("request for rs") || lower.contains("request for ₹") ||
            lower.contains("money request") || lower.contains("upi request") ||
            lower.contains("awaiting your approval") || lower.contains("approve request") ||
            lower.contains("request pending") || lower.contains("tap to pay")) {
            return "Matches unsettled UPI/payment request"
        }

        // 8. One-Time Passwords (OTP) & Security codes
        if (lower.contains("otp") || lower.contains("verification code") || 
            lower.contains("login code") || lower.contains("do not share") || 
            lower.contains("one time password") || lower.contains("security code") ||
            lower.contains("secret code")) {
            if (!lower.contains("debited") && !lower.contains("credited") && !lower.contains("paid rs") && !lower.contains("spent")) {
                return "Matches OTP/verification code pattern"
            }
        }

        // 9. Transaction Failures / Declines / Reversals
        if (lower.contains("declined") || lower.contains("insufficient funds") || 
            lower.contains("transaction failed") || lower.contains("payment failed") || 
            lower.contains("failed due to") || lower.contains("could not be processed") || 
            lower.contains("timed out") || lower.contains("unsuccessful") || lower.contains("declined due to")) {
            return "Matches transaction failure/decline pattern"
        }

        // 10. Banking Alerts, KYC & Informational Reminders
        if (lower.contains("password expire") || lower.contains("password will expire") || 
            lower.contains("kyc update") || lower.contains("complete your kyc") ||
            lower.contains("login alert") || lower.contains("statement available") ||
            lower.contains("minimum balance charges") || lower.contains("maintain balance") ||
            lower.contains("rate us") || lower.contains("update available") ||
            lower.contains("security alert")) {
            return "Matches banking alert / reminder pattern"
        }

        val hasExplicitVerb = NotificationTransactionGate.hasExplicitTransactionVerb(lower)
        val hasRefHint = lower.contains("utr") || lower.contains("txn id") || lower.contains("transaction id") ||
            lower.contains("ref no") || lower.contains("reference no") || lower.contains("rrn")

        val hasBalanceOnlyHint = lower.contains("avl bal") || lower.contains("available bal") ||
            lower.contains("account balance") || lower.contains("balance in a/c") ||
            lower.contains("balance is rs") || lower.contains("balance is ₹")

        val hasCurrency = lower.contains("inr") || lower.contains("rs") ||
                lower.contains("₹") || lower.contains("r.s") || lower.contains("rupees") ||
                lower.contains("usd") || lower.contains("$")

        val isValid = (hasExplicitVerb && hasCurrency) ||
            (hasRefHint && (hasCurrency || hasExplicitVerb)) ||
            (hasBalanceOnlyHint && hasCurrency)

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
    fun extractAmount(text: String): Double? = extractTransactionAmount(text)

    /**
     * Extracts the transaction amount only — never treats available balance / limit lines as payment amount.
     */
    fun extractTransactionAmount(text: String): Double? {
        val normalizedText = text.replace("&nbsp;", " ").replace("\u00A0", " ")
        val lowerText = normalizedText.lowercase()

        val isOtpMsg = lowerText.contains("otp") || lowerText.contains("verification code") || lowerText.contains("login code")

        val regexes = listOf(
            // 1. Explicit payment phrases: payment of Rs 500, paid Rs 300, order payment of ₹450
            Regex("""(?:payment|order payment)\s+(?:of|for|amounting to)?\s*(?:inr|rs\.?|₹|usd|\$)?\s*([\d,]+(?:\.\d{1,2})?)(?:\/\-|\/\=)?""", RegexOption.IGNORE_CASE),
            // 2. Action verbs followed by prepositions/words and amount:
            // "Paid ₹150 to Swiggy", "Debited by INR 450", "Received Rs 500 from Amit", "Sent Rs 200", etc.
            Regex("""(?:debited|credited|paid|sent|spent|charged|withdrawn|transferred|transfer|withdrawal|done|received|recd|rec'd|added|deposited|refunded|refund|cashback)\s+(?:(?:by|with|for|of|to|from|is|was|has been)\s+)*(?:inr|rs\.?|₹|usd|\$)?\s*([\d,]+(?:\.\d{1,2})?)(?:\/\-|\/\=)?""", RegexOption.IGNORE_CASE),
            // 3. Amount followed by action verb: "Rs 500 debited", "₹150 paid", "INR 1,200 spent", "500.00 credited"
            Regex("""(?:inr|rs\.?|₹|usd|\$)?\s*([\d,]+(?:\.\d{1,2})?)(?:\/\-|\/\=)?\s*(?:(?:is|was|has been|done|transferred|transfer|paid|debited|credited|spent|sent|charged|received|deposited|refunded)\s+)*(?:debited|credited|paid|spent|sent|charged|received|deposited|refunded)""", RegexOption.IGNORE_CASE),
            // 4. Mandate / Autopay / ATM / SIP / UPI / Salary / Deposit / Refund
            Regex("""(?:autopay|mandate|sip|atm|upi|salary|deposit|refund)\s+(?:of|for|by)?\s*(?:inr|rs\.?|₹|usd|\$)?\s*([\d,]+(?:\.\d{1,2})?)(?:\/\-|\/\=)?""", RegexOption.IGNORE_CASE),
            // 5. UPI / IMPS / NEFT format: UPI/DR/1234/500.00 or UPI:Rs.500
            Regex("""(?:upi|imps|neft|rtgs)[\s\:\/\-]+(?:dr|cr)?[\s\:\/\-]*(?:inr|rs\.?|₹|usd|\$)?\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            // 6. Currency prefix before amount, ignoring if it's explicitly an available balance/credit limit
            Regex("""(?:inr|rs\.?|₹|usd|\$)\s*([\d,]+(?:\.\d{1,2})?)(?:\/\-|\/\=)?""", RegexOption.IGNORE_CASE)
        )

        for (regex in regexes) {
            val matches = regex.findAll(normalizedText)
            for (match in matches) {
                // Ensure this match is not explicitly the available balance or credit limit
                val prefixStart = (match.range.first - 30).coerceAtLeast(0)
                val prefix = normalizedText.substring(prefixStart, match.range.first).lowercase()
                if (isBalanceOrLimitPrefix(prefix) || isPromotionalAmountPrefix(prefix)) {
                    continue
                }
                val rawValStr = match.groupValues[1]
                val num = cleanNumber(rawValStr) ?: continue

                // OTP Safeguard: If message is OTP and amount is 6-digit integer, skip unless currency symbol directly attached
                if (isOtpMsg && rawValStr.length == 6 && !rawValStr.contains(".") &&
                    !match.value.lowercase().contains("rs") && !match.value.contains("₹") && !match.value.lowercase().contains("inr")) {
                    continue
                }

                if (num > 0.0) return num
            }
        }
        return null
    }

    private fun isPromotionalAmountPrefix(lowerPrefix: String): Boolean {
        return lowerPrefix.contains("flat") || lowerPrefix.contains("up to") ||
            lowerPrefix.contains("upto") || lowerPrefix.contains("save") ||
            lowerPrefix.contains("discount") || lowerPrefix.contains("win") ||
            lowerPrefix.contains("earn") || lowerPrefix.contains("voucher of") ||
            lowerPrefix.contains("coupon of")
    }

    private fun isBalanceOrLimitPrefix(lowerPrefix: String): Boolean {
        return lowerPrefix.contains("avl bal") || lowerPrefix.contains("avail bal") ||
            lowerPrefix.contains("available bal") || lowerPrefix.contains("account balance") ||
            lowerPrefix.contains("balance in") || lowerPrefix.contains("balance is") ||
            lowerPrefix.contains("balance:") || lowerPrefix.contains("bal:") ||
            lowerPrefix.contains("credit limit") || lowerPrefix.contains("available limit") ||
            lowerPrefix.contains("total limit") || lowerPrefix.contains("clear bal")
    }

    private fun isBalanceOrLimitLine(lowerLine: String): Boolean {
        return lowerLine.contains("avl bal") || (lowerLine.contains("avail") && lowerLine.contains("bal")) ||
            lowerLine.contains("available balance") || lowerLine.contains("account balance") ||
            lowerLine.contains("balance in") || lowerLine.contains("balance is") ||
            (lowerLine.contains("balance") && lowerLine.contains("is")) ||
            lowerLine.contains("credit limit") || lowerLine.contains("available limit")
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

    fun determineTransactionType(lowerText: String): BankTxType {
        val hasExplicitDebit = lowerText.contains("debited") || lowerText.contains("debit of") ||
            lowerText.contains("is debited") || lowerText.contains("was debited") ||
            lowerText.contains("has been debited") || lowerText.contains("debited from") ||
            lowerText.contains("debited by") || lowerText.contains("debited with") ||
            lowerText.contains("paid") || lowerText.contains("you paid") ||
            lowerText.contains("payment to") || lowerText.contains("payment at") ||
            lowerText.contains("spent") || lowerText.contains("spent on") || lowerText.contains("spent at") ||
            lowerText.contains("sent to") || lowerText.contains("money sent") ||
            lowerText.contains("withdrawn") || lowerText.contains("withdrawal") ||
            lowerText.contains("charged") || lowerText.contains("purchased at") || lowerText.contains("purchase of") ||
            lowerText.contains("autopay") || lowerText.contains("mandate") ||
            Regex("""\bdr[:.]|\bdr\s""").containsMatchIn(lowerText) ||
            Regex("""(?:upi|imps|neft)[/-]dr[/-]""").containsMatchIn(lowerText)

        val hasExplicitCredit = lowerText.contains("credited to your") ||
            lowerText.contains("credited to a/c") || lowerText.contains("credited to account") ||
            lowerText.contains("credited in your") || lowerText.contains("credited in a/c") ||
            lowerText.contains("credited with") || lowerText.contains("credited by") ||
            lowerText.contains("is credited to your") || lowerText.contains("was credited to your") ||
            lowerText.contains("has been credited to your") || lowerText.contains("inward credit") ||
            lowerText.contains("received from") || lowerText.contains("received in") ||
            lowerText.contains("recd from") || lowerText.contains("money received from") ||
            lowerText.contains("payment received from") || lowerText.contains("you received") ||
            lowerText.contains("you have received") || lowerText.contains("deposited to") ||
            lowerText.contains("deposited in") || lowerText.contains("refund credited") ||
            lowerText.contains("refund of") || lowerText.contains("refunded to") ||
            lowerText.contains("reversal credited") || lowerText.contains("cashback credited") ||
            lowerText.contains("salary credited") || lowerText.contains("salary of") ||
            lowerText.contains("stipend credited") || lowerText.contains("transferred to your") ||
            lowerText.contains("transferred to you") || lowerText.contains("sent you") ||
            lowerText.contains("paid you") || lowerText.contains("added to account") ||
            lowerText.contains("added to wallet") ||
            Regex("""\bcr[:.]|\bcr\s""").containsMatchIn(lowerText) ||
            Regex("""(?:upi|imps|neft)[/-]cr[/-]""").containsMatchIn(lowerText)

        if (hasExplicitDebit && !hasExplicitCredit) {
            return BankTxType.DEBIT
        }
        if (hasExplicitCredit && !hasExplicitDebit) {
            return BankTxType.CREDIT
        }
        if (hasExplicitDebit && hasExplicitCredit) {
            val userDebited = lowerText.contains("debited from") || lowerText.contains("debited by") ||
                lowerText.contains("debited with") || lowerText.contains("spent on") ||
                lowerText.contains("spent at") || lowerText.contains("paid to") ||
                lowerText.contains("you paid") || lowerText.contains("withdrawn from")
            val userCredited = lowerText.contains("credited to your") || lowerText.contains("credited to account") ||
                lowerText.contains("credited to a/c") || lowerText.contains("refund credited") ||
                lowerText.contains("cashback credited") || lowerText.contains("salary credited")
            if (userDebited && !userCredited) {
                return BankTxType.DEBIT
            }
            if (userCredited && !userDebited) {
                return BankTxType.CREDIT
            }
        }

        val generalCredit = lowerText.contains("credited") || lowerText.contains("credit") || lowerText.contains("received") || lowerText.contains("refund")
        if (generalCredit && !hasExplicitDebit) return BankTxType.CREDIT

        return BankTxType.DEBIT
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
        nlpKeywords: List<String>,
        referenceNumber: String = "",
        hasExplicitTxVerb: Boolean = false
    ): Float {
        if (!isFinancial) return 0.0f

        var score = 0.2f
        if (amount != null && amount > 0.0) score += 0.25f
        if (hasExplicitTxVerb) score += 0.2f
        if (referenceNumber.isNotBlank()) score += 0.15f
        if (accountEnding.isNotBlank()) score += 0.1f
        if (bankName != "Bank Account" && bankName.isNotBlank()) score += 0.05f
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
