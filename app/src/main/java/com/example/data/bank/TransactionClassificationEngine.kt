package com.example.data.bank

enum class TransactionClassification(val label: String, val badgeColorHex: Long) {
    BANK("Bank", 0xFF1E40AF),           // Navy / Classic Bank Blue
    CREDIT_CARD("Credit Card", 0xFF7C3AED), // Premium Credit Card Purple
    MERCHANT("Merchant", 0xFFEA580C);       // Commercial Merchant Orange

    companion object {
        fun fromLabel(label: String): TransactionClassification {
            return entries.firstOrNull { it.label.equals(label, ignoreCase = true) } ?: BANK
        }
    }
}

/**
 * Intelligent classification engine that parses SMS notification bodies using keywords and regex
 * to automatically label transactions as 'Bank', 'Credit Card', or 'Merchant' (like Swiggy/Zomato).
 */
object TransactionClassificationEngine {

    // 1. Credit Card Regex and Keywords (Strict: Never matches Debit Card or ATM)
    private val CREDIT_CARD_REGEXES = listOf(
        Regex("""\b(?:credit\s*card|creditcard)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:credit|avail(?:able)?|total)\s+limit\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:min(?:imum)?\s+(?:amt|amount)?\s*due|total\s+(?:amt|amount)?\s*due)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:statement\s+(?:for|generated)|card\s*statement|billing\s*cycle)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:sbi\s*card|onecard|amex|american\s+express|diners\s+club|rupay\s+credit|mastercard\s+credit|visa\s+credit|regalia|millennia|infinia|magnus|sapphiro|rubyx|coral\s+credit)\b""", RegexOption.IGNORE_CASE)
    )

    // Dedicated Credit Card Sender Codes (Strict prefixes from Indian Banks/Card Issuers)
    private val CREDIT_CARD_SENDER_REGEX = Regex(
        """^[A-Za-z]{2}-(?:HDFCCC|SBICRD|ICICIC|AXISCR|AXISCC|BOBCRD|KOTAKC|CITICR|AMEXIN|RBLCRD|ONECRD|AUCRD|YESCRD|INDUSC|STANCR|SCBCRD)\b""",
        RegexOption.IGNORE_CASE
    )

    // Explicit non-credit card patterns (Debit card, ATM withdrawals, bank transfers)
    private val EXPLICIT_NON_CREDIT_PATTERNS = listOf(
        Regex("""\b(?:debit\s*card|debitcard)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:atm\s*cash|cash\s*withdrawn?|atm\s*withdrawal)\b""", RegexOption.IGNORE_CASE)
    )

    // 2. High-Priority Merchants (like Swiggy, Zomato, Uber, Blinkit, etc.)
    private val KNOWN_MERCHANTS = listOf(
        "SWIGGY", "ZOMATO", "UBER", "OLA", "RAPIDO", "BLINKIT", "ZEPTO", "BIGBASKET",
        "INSTAMART", "AMAZON", "FLIPKART", "MYNTRA", "AJIO", "NETFLIX", "SPOTIFY",
        "AIRTEL", "JIO", "MCDONALD", "DOMINO", "STARBUCKS", "KFC", "PIZZA HUT",
        "BURGER KING", "BOOKMYSHOW", "PVR", "INOX", "MAKEMYTRIP", "CLEARTRIP",
        "IRCTC", "NYKAA", "TATA CLIQ", "PHARMEASY", "APOLLO", "1MG", "HPCL",
        "IOCL", "BPCL", "SHELL", "DMART", "SMART SUPERMARKET", "SMART BAZAAR", "PLUXEE", "SODEXO",
        "RELIANCE", "CROMA", "DECATHLON", "IKEA",
        "LENSKART", "URBAN COMPANY", "CULT.FIT", "DUNZO", "EATSURE", "BEWAKOOF"
    )

    private val MERCHANT_PATTERNS = listOf(
        Regex("""\b(?:at|towards|to|vpa|paid\s+to|spent\s+at|info:)\s+([A-Za-z0-9&._-]{2,30})\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:store|restaurant|cafe|mall|mart|retail|outlet|pos|online|e-comm|merchant)\b""", RegexOption.IGNORE_CASE),
        Regex("""@[a-zA-Z]{3,}\b""") // UPI VPA merchant handle
    )

    // 3. Bank Patterns
    private val BANK_PATTERNS = listOf(
        Regex("""\b(?:a\/c|account|acct|savings|current|salary|neft|rtgs|imps|atm|cash\s+withdrawn?|cheque|fund\s+transfer|nach|ecs|interest\s+credited)\b""", RegexOption.IGNORE_CASE)
    )

    /**
     * Automatically classifies the notification into 'Bank', 'Credit Card', or 'Merchant'.
     */
    fun classify(
        text: String,
        senderOrTitle: String = "",
        extractedMerchant: String = "",
        bankName: String = ""
    ): TransactionClassification {
        val upperText = text.uppercase()
        val upperSender = senderOrTitle.uppercase()
        val upperMerchant = extractedMerchant.uppercase()

        // Step 1: Detect Credit Card Transactions first (if it's a credit card spend, CC classification takes precedence)
        if (isCreditCardTransaction(text, senderOrTitle)) {
            return TransactionClassification.CREDIT_CARD
        }

        // Step 2: Detect Merchant Transactions (e.g. Swiggy, Zomato, Uber, Blinkit direct receipts)
        if (isMerchantTransaction(upperText, upperSender, upperMerchant, bankName)) {
            return TransactionClassification.MERCHANT
        }

        // Step 3: Default to Bank (Savings/Current account transfers, ATM, NEFT/IMPS, debit card, salary)
        return TransactionClassification.BANK
    }

    /**
     * Convenience method returning the string label: 'Bank', 'Credit Card', or 'Merchant'.
     */
    fun classifyLabel(
        text: String,
        senderOrTitle: String = "",
        extractedMerchant: String = "",
        bankName: String = ""
    ): String {
        return classify(text, senderOrTitle, extractedMerchant, bankName).label
    }

    private fun isMerchantTransaction(
        upperText: String,
        upperSender: String,
        upperMerchant: String,
        bankName: String
    ): Boolean {
        // Explicit match against known popular merchants (Swiggy, Zomato, Uber, etc.)
        for (merchant in KNOWN_MERCHANTS) {
            if (upperMerchant.contains(merchant) || upperText.contains(merchant) || upperSender.contains(merchant)) {
                return true
            }
        }

        // If an explicit merchant was extracted and it's not a generic bank name
        if (upperMerchant.isNotBlank() &&
            upperMerchant != "BANK TRANSACTION" &&
            upperMerchant != "BANK ACCOUNT" &&
            !upperMerchant.equals(bankName, ignoreCase = true) &&
            !upperMerchant.startsWith("A/C") &&
            !upperMerchant.startsWith("ACCOUNT")
        ) {
            // Check if text has merchant purchase phrasing
            for (pattern in MERCHANT_PATTERNS) {
                if (pattern.containsMatchIn(upperText)) {
                    return true
                }
            }
        }

        return false
    }

    private fun isCreditCardTransaction(text: String, senderOrTitle: String): Boolean {
        // Disqualify immediately if it explicitly mentions Debit Card or ATM
        for (nonCcPattern in EXPLICIT_NON_CREDIT_PATTERNS) {
            if (nonCcPattern.containsMatchIn(text)) {
                return false
            }
        }

        val hasCreditCardSender = CREDIT_CARD_SENDER_REGEX.containsMatchIn(senderOrTitle)

        for (regex in CREDIT_CARD_REGEXES) {
            if (regex.containsMatchIn(text)) {
                return true
            }
        }

        val lowerCombined = "$senderOrTitle $text".lowercase()
        if (lowerCombined.contains("credit card") || lowerCombined.contains("cc alert") || lowerCombined.contains("creditcard")) {
            return true
        }

        if (hasCreditCardSender && (lowerCombined.contains("spent") || lowerCombined.contains("charged") || lowerCombined.contains("limit") || lowerCombined.contains("card"))) {
            return true
        }

        return false
    }
}
