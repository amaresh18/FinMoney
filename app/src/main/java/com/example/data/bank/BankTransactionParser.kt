package com.example.data.bank

import com.example.data.model.DebitCategory
import com.example.data.model.DebitItem
import java.util.UUID

enum class BankTxType {
    DEBIT,
    CREDIT
}

data class ParsedBankTransaction(
    val id: String = UUID.randomUUID().toString(),
    val bankName: String,
    val accountNumberLast4: String = "",
    val amount: Double,
    val type: BankTxType,
    val classification: String = TransactionClassification.BANK.label,
    val availableBalance: Double? = null,
    val merchantOrParty: String = "",
    val category: String = DebitCategory.GENERAL.name,
    val referenceNumber: String = "",
    val sourcePackage: String = "",
    val rawText: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val matchedDebitId: Long? = null,
    val matchedDebitTitle: String? = null
)

object BankTransactionParser {

    private val KNOWN_BANKS = listOf(
        "HDFC" to "HDFC Bank",
        "SBI" to "State Bank of India",
        "STATE BANK" to "State Bank of India",
        "ICICI" to "ICICI Bank",
        "AXIS" to "Axis Bank",
        "KOTAK" to "Kotak Mahindra Bank",
        "PNB" to "Punjab National Bank",
        "PUNJAB NATIONAL" to "Punjab National Bank",
        "BOB" to "Bank of Baroda",
        "BARODA" to "Bank of Baroda",
        "CANARA" to "Canara Bank",
        "UNION" to "Union Bank of India",
        "INDUSIND" to "IndusInd Bank",
        "IDFC" to "IDFC FIRST Bank",
        "FEDERAL" to "Federal Bank",
        "YES BANK" to "Yes Bank",
        "YESBNK" to "Yes Bank",
        "RBL" to "RBL Bank",
        "PAYTM" to "Paytm Payments Bank",
        "AIRTEL" to "Airtel Payments Bank",
        "AU SMALL" to "AU Small Finance Bank",
        "AUBANK" to "AU Small Finance Bank",
        "JUPITER" to "Jupiter (Federal Bank)",
        "FI MONEY" to "Fi (Federal Bank)",
        "ONECARD" to "OneCard",
        "ONECRD" to "OneCard",
        "SLICE" to "Slice Card",
        "AMEX" to "American Express",
        "AMERICAN EXPRESS" to "American Express",
        "SCAPIA" to "Scapia Card",
        "HSBC" to "HSBC Bank",
        "STANDARD CHARTERED" to "Standard Chartered",
        "SCB" to "Standard Chartered",
        "CITI" to "Citi Bank",
        "EQUITAS" to "Equitas Small Finance Bank",
        "UJJIVAN" to "Ujjivan Small Finance Bank",
        "BANDHAN" to "Bandhan Bank",
        "BANK OF INDIA" to "Bank of India",
        "BOI" to "Bank of India",
        "CENTRAL BANK" to "Central Bank of India",
        "IOB" to "Indian Overseas Bank",
        "INDIAN OVERSEAS" to "Indian Overseas Bank",
        "PSB" to "Punjab & Sind Bank",
        "KVB" to "Karur Vysya Bank",
        "SOUTH INDIAN" to "South Indian Bank",
        "KARNATAKA BANK" to "Karnataka Bank",
        "CITY UNION" to "City Union Bank",
        "CUB" to "City Union Bank",
        "DHANLAXMI" to "Dhanlaxmi Bank",
        "JAMMU & KASHMIR" to "J&K Bank",
        "JKBANK" to "J&K Bank",
        "BAJAJ" to "Bajaj Finserv",
        "MOBIKWIK" to "MobiKwik",
        "FREECHARGE" to "Freecharge"
    )

    private val UPI_APPS = listOf(
        "GPAY" to "Google Pay",
        "GOOGLE PAY" to "Google Pay",
        "PHONEPE" to "PhonePe",
        "PAYTM" to "Paytm",
        "CRED" to "CRED",
        "BHIM" to "BHIM UPI",
        "PLUXEE" to "Pluxee",
        "SODEXO" to "Pluxee",
        "AMAZON" to "Amazon Pay"
    )

    fun isFinancialMessage(text: String, title: String = ""): Boolean {
        return com.example.service.TransactionParser.isFinancialText(text, title)
    }

    fun parse(
        text: String,
        senderOrTitle: String,
        sourcePackage: String = "",
        timestamp: Long = System.currentTimeMillis(),
        plannedDebits: List<DebitItem> = emptyList()
    ): ParsedBankTransaction? {
        val parseResult = com.example.service.TransactionParser.parse(
            rawText = text,
            senderOrTitle = senderOrTitle,
            sourcePackage = sourcePackage,
            timestamp = timestamp
        )

        val parsed = parseResult.toParsedBankTransaction() ?: return null

        // Planned Debit Matching
        val matchedDebit = if (parsed.type == BankTxType.DEBIT) {
            plannedDebits.firstOrNull { debit ->
                !debit.isPaid && Math.abs(debit.amount - parsed.amount) < 2.0 &&
                        (debit.title.contains(parsed.merchantOrParty, ignoreCase = true) || parsed.merchantOrParty.contains(debit.title, ignoreCase = true))
            }
        } else null

        return parsed.copy(
            matchedDebitId = matchedDebit?.id,
            matchedDebitTitle = matchedDebit?.title
        )
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

    fun extractAmount(text: String): Double? {
        val normalizedText = text.replace("&nbsp;", " ").replace("\u00A0", " ")

        val regexes = listOf(
            // 1. Currency prefix: INR 1,500.00 / Rs. 1500 / ₹1,500/- / Rs1500
            Regex("""(?:inr|rs\.?|₹|usd)\s*([\d,]+(?:\.\d{1,2})?)(?:\/\-|\/\=)?""", RegexOption.IGNORE_CASE),
            // 2. Verb followed by currency or amount: debited by 1,500.00 / paid Rs. 500 / charged 1200 / spent 450
            Regex("""(?:debited|credited|paid|sent|spent|charged|withdrawn|transferred|transfer|withdrawal|done|received)\s+(?:by|with|for|of)?\s*(?:inr|rs\.?|₹|usd)?\s*([\d,]+(?:\.\d{1,2})?)(?:\/\-|\/\=)?""", RegexOption.IGNORE_CASE),
            // 3. Amount followed by verb or currency: 1,500.00 INR / 1500 Rs / 1,500.00 debited / 500.00 paid
            Regex("""([\d,]+(?:\.\d{1,2})?)(?:\/\-|\/\=)?\s*(?:inr|rs\.?|₹|usd|debited|credited|paid|spent|charged)""", RegexOption.IGNORE_CASE),
            // 4. Mandate / Autopay / ATM / SIP: autopay of 1200 / mandate of 1500 / atm 5000
            Regex("""(?:autopay|mandate|sip|atm|upi)\s+(?:of|for)?\s*(?:inr|rs\.?|₹|usd)?\s*([\d,]+(?:\.\d{1,2})?)(?:\/\-|\/\=)?""", RegexOption.IGNORE_CASE),
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

    fun extractAvailableBalance(text: String): Double? {
        val normalizedText = text.replace("&nbsp;", " ").replace("\u00A0", " ")

        val balanceRegexes = listOf(
            // 1. Balance in account no. ending with 0989 is Rs. 3,021.57 / Balance in A/C XX0989 is Rs. 3,021.57
            Regex("""(?:bal|balance)\s+(?:in|for|of)?\s*(?:a\/c|account|acct|card)?\s*(?:no\.?|num\.?)?\s*(?:ending\s+with\s+|ending\s+|XX|\*\*|\*)*\s*\d*\s*(?:is|:|-)?\s*(?:inr|rs\.?|₹|usd)?\s*([\d,]+(?:\.\d{1,2})?)(?:\/\-|\/\=)?""", RegexOption.IGNORE_CASE),
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

    fun extractAccountLast4(text: String): String {
        return com.example.service.TransactionParser.extractAccountEnding(text)
    }

    fun identifyBank(senderOrTitle: String, text: String): String {
        val combined = "$senderOrTitle $text".uppercase()

        for ((key, name) in KNOWN_BANKS) {
            if (combined.contains(key)) return name
        }

        for ((key, name) in UPI_APPS) {
            if (combined.contains(key)) return name
        }

        // Clean sender ID fallback (e.g. "AD-HDFCBK" -> "HDFC Bank")
        val cleanSender = senderOrTitle.replace(Regex("""^[A-Z]{2}-"""), "")
        if (cleanSender.isNotBlank() && cleanSender.length >= 3) {
            return cleanSender
        }

        return "Bank Account"
    }

    fun identifyCategory(text: String, title: String = "", merchantName: String = ""): String {
        return CategoryMappingEngine.categorize(text, title, merchantName)
    }

    object CategoryMappingEngine {
        data class CategoryRule(
            val categoryKey: String,
            val displayName: String,
            val merchantKeywords: List<String>,
            val textKeywords: List<String>,
            val priority: Int = 10
        )

        val RULES = listOf(
            // Food & Dining
            CategoryRule(
                categoryKey = DebitCategory.OUTSIDE_FOOD.name,
                displayName = "Food & Dining",
                merchantKeywords = listOf(
                    "swiggy", "zomato", "mcdonald", "mcdonalds", "kfc", "domino", "dominos", "pizza",
                    "starbucks", "third wave", "chaayos", "chai point", "haldiram", "eatclub", "eatfit",
                    "box8", "faasos", "behrouz", "burger king", "taco bell", "subway", "baskin", "dineout",
                    "magicpin", "barbeque nation", "mainland china", "bistro", "cafe", "restaurant", "bakery"
                ),
                textKeywords = listOf(
                    "swiggy", "zomato", "restaurant", "dining", "food", "cafe", "bistro", "eatery",
                    "dhaba", "dhabha", "bakery", "biryani", "coffee", "tea", "breakfast", "lunch", "dinner",
                    "pizza", "burger", "sweets", "juice", "snack"
                ),
                priority = 20
            ),

            // Groceries & Essentials
            CategoryRule(
                categoryKey = DebitCategory.GROCERIES.name,
                displayName = "Groceries & Essentials",
                merchantKeywords = listOf(
                    "blinkit", "zepto", "instamart", "bigbasket", "bb daily", "country delight", "jiomart",
                    "dmart", "avenue supermarts", "more retail", "spencer", "spencers", "nature basket",
                    "star bazaar", "reliance fresh", "reliance smart", "easyday", "milkbasket"
                ),
                textKeywords = listOf(
                    "groceries", "grocery", "supermarket", "hypermarket", "kirana", "provision",
                    "vegetables", "fruits", "instamart", "blinkit", "zepto", "bigbasket", "mart"
                ),
                priority = 20
            ),

            // Shopping & Lifestyle
            CategoryRule(
                categoryKey = DebitCategory.SHOPPING.name,
                displayName = "Shopping & Lifestyle",
                merchantKeywords = listOf(
                    "amazon", "flipkart", "myntra", "ajio", "meesho", "tatacliq", "nykaa", "croma",
                    "reliance digital", "trends", "pantaloons", "zara", "h&m", "uniqlo", "decathlon",
                    "shoppers stop", "westside", "lifestyle", "urbanic", "lenskart", "snitch", "boat",
                    "noise", "fireboltt", "vijay sales"
                ),
                textKeywords = listOf(
                    "shopping", "retail", "fashion", "clothing", "apparel", "footwear", "shoes",
                    "electronics", "gadgets", "lifestyle", "mall", "boutique", "bazaar", "amazon", "flipkart"
                ),
                priority = 18
            ),

            // Recharge & Mobile/DTH
            CategoryRule(
                categoryKey = DebitCategory.RECHARGE.name,
                displayName = "Recharge & Bills",
                merchantKeywords = listOf(
                    "jio", "airtel", "vi ", "vodafone", "idea", "bsnl", "tata play", "tatasky",
                    "dish tv", "sun direct", "d2h"
                ),
                textKeywords = listOf(
                    "recharge", "prepaid", "postpaid", "mobile bill", "dth", "topup", "talktime", "airtel", "jio"
                ),
                priority = 18
            ),

            // Electricity & Water / Piped Gas
            CategoryRule(
                categoryKey = DebitCategory.ELECTRICITY.name,
                displayName = "Electricity & Utilities",
                merchantKeywords = listOf(
                    "bescom", "cesc", "tata power", "torrent power", "mseb", "tangedco", "uppcl",
                    "bses", "bses rajdhani", "bses yamuna", "mgvcl", "indane", "hp gas", "bharat gas", "mahanagar gas"
                ),
                textKeywords = listOf(
                    "electricity", "electric", "power bill", "eb bill", "water bill", "piped gas", "gas bill", "lpg", "utility"
                ),
                priority = 19
            ),

            // Internet & Broadband
            CategoryRule(
                categoryKey = DebitCategory.INTERNET.name,
                displayName = "Internet & Broadband",
                merchantKeywords = listOf(
                    "act fibernet", "acttv", "jiofiber", "airtel xstream", "excitel", "hathway", "tikona", "spectra"
                ),
                textKeywords = listOf(
                    "broadband", "internet", "wifi", "fiber", "fibernet"
                ),
                priority = 19
            ),

            // Fuel
            CategoryRule(
                categoryKey = DebitCategory.FUEL.name,
                displayName = "Fuel & Petrol",
                merchantKeywords = listOf(
                    "hpcl", "iocl", "bpcl", "hindustan petroleum", "indian oil", "bharat petroleum", "shell", "nayara"
                ),
                textKeywords = listOf(
                    "fuel", "petrol", "diesel", "cng", "gas station", "fuel station", "filling station"
                ),
                priority = 20
            ),

            // Travel & Commute
            CategoryRule(
                categoryKey = DebitCategory.TRAVEL.name,
                displayName = "Travel & Commute",
                merchantKeywords = listOf(
                    "uber", "ola", "rapido", "irctc", "redbus", "abhibus", "makemytrip", "goibibo",
                    "yatra", "cleartrip", "indigo", "air india", "spicejet", "akasa", "fastag", "nhai",
                    "ihmcl", "namma metro", "dmrc"
                ),
                textKeywords = listOf(
                    "uber", "ola", "rapido", "cab", "taxi", "ride", "commute", "metro", "train",
                    "railway", "flight", "airline", "bus", "ticket", "toll", "fastag", "parking"
                ),
                priority = 19
            ),

            // Entertainment & Subscriptions
            CategoryRule(
                categoryKey = DebitCategory.OTT.name,
                displayName = "OTT & Media",
                merchantKeywords = listOf(
                    "netflix", "prime video", "prime", "hotstar", "disney", "spotify", "youtube",
                    "yt premium", "apple music", "itunes", "bookmyshow", "pvr", "pvrs", "inox", "cinepolis", "playstation", "xbox", "steam"
                ),
                textKeywords = listOf(
                    "ott", "movie", "cinema", "theatre", "subscription", "streaming", "music", "gaming", "netflix", "hotstar", "spotify"
                ),
                priority = 18
            ),

            // Health & Pharmacy
            CategoryRule(
                categoryKey = DebitCategory.PHARMACY.name,
                displayName = "Pharmacy & Health",
                merchantKeywords = listOf(
                    "apollo", "apollo pharmacy", "1mg", "tata 1mg", "pharmeasy", "netmeds", "medplus",
                    "practo", "truemeds", "max healthcare", "fortis", "manipal"
                ),
                textKeywords = listOf(
                    "pharmacy", "chemist", "medicine", "meds", "hospital", "clinic", "doctor", "diagnostics", "lab", "pathology", "scan", "health"
                ),
                priority = 18
            ),

            // Investment & Savings
            CategoryRule(
                categoryKey = DebitCategory.INVESTMENT.name,
                displayName = "Investments & SIP",
                merchantKeywords = listOf(
                    "zerodha", "groww", "upstox", "angelone", "paytm money", "kuvera", "coin",
                    "smallcase", "indmoney", "navi", "nippon", "sbi mutual fund", "hdfc mutual fund"
                ),
                textKeywords = listOf(
                    "sip", "mutual fund", "stocks", "shares", "equities", "nps", "ppf", "fixed deposit", "recurring deposit"
                ),
                priority = 18
            ),

            // EMI & Loans
            CategoryRule(
                categoryKey = DebitCategory.EMI.name,
                displayName = "Loan / EMI",
                merchantKeywords = listOf(
                    "bajaj finserv", "home credit", "zestmoney", "lazypay", "simpl", "capital float",
                    "l&t finance", "tata capital", "fullerton"
                ),
                textKeywords = listOf(
                    "emi", "loan", "repayment", "credit card bill", "cc payment", "autodebit", "bajaj"
                ),
                priority = 18
            ),

            // Insurance
            CategoryRule(
                categoryKey = DebitCategory.INSURANCE.name,
                displayName = "Insurance",
                merchantKeywords = listOf(
                    "lic", "life insurance", "policybazaar", "star health", "care health", "hdfc ergo",
                    "icici lombard", "bajaj allianz", "digit insurance", "acko"
                ),
                textKeywords = listOf(
                    "insurance", "premium", "policy", "mediclaim", "lic"
                ),
                priority = 18
            ),

            // Housing & Rent
            CategoryRule(
                categoryKey = DebitCategory.RENT.name,
                displayName = "Rent / Housing",
                merchantKeywords = listOf("nobroker", "housing"),
                textKeywords = listOf("rent", "landlord", "house rent", "office rent"),
                priority = 18
            ),

            // Apartment Maintenance
            CategoryRule(
                categoryKey = DebitCategory.MAINTENANCE.name,
                displayName = "Apartment Maintenance",
                merchantKeywords = listOf("mygate", "nobrokerhood"),
                textKeywords = listOf("maintenance", "society maintenance", "apartment maintenance"),
                priority = 18
            ),

            // Education & Tuition
            CategoryRule(
                categoryKey = DebitCategory.SCHOOL_FEES.name,
                displayName = "School & Education",
                merchantKeywords = listOf("byju", "unacademy", "udemy", "coursera"),
                textKeywords = listOf("school", "tuition", "coaching", "college", "university", "exam fee"),
                priority = 18
            ),

            // Milk & Daily Dairy
            CategoryRule(
                categoryKey = DebitCategory.MILK.name,
                displayName = "Milk & Dairy",
                merchantKeywords = listOf("country delight", "nandini", "amul", "mother dairy"),
                textKeywords = listOf("milk", "dairy"),
                priority = 18
            ),

            // Maid & Cook
            CategoryRule(
                categoryKey = DebitCategory.MAID.name,
                displayName = "Maid & Cook",
                merchantKeywords = emptyList(),
                textKeywords = listOf("maid", "cook", "housekeeping"),
                priority = 18
            ),

            // Newspaper & Magazines
            CategoryRule(
                categoryKey = DebitCategory.NEWSPAPER.name,
                displayName = "Newspaper & Magazines",
                merchantKeywords = emptyList(),
                textKeywords = listOf("newspaper", "magazine"),
                priority = 18
            ),

            // Chit Payments
            CategoryRule(
                categoryKey = DebitCategory.CHIT.name,
                displayName = "Chit Payments",
                merchantKeywords = emptyList(),
                textKeywords = listOf("chit", "chits", "chit fund"),
                priority = 18
            )
        )

        fun categorize(rawText: String, title: String = "", merchantName: String = ""): String {
            val lowerText = rawText.lowercase()
            val lowerTitle = title.lowercase()
            val lowerMerchant = merchantName.lowercase()
            val combined = "$lowerTitle $lowerMerchant $lowerText"

            var bestRule: CategoryRule? = null
            var bestScore = 0

            for (rule in RULES) {
                var currentScore = 0

                // Check merchant keywords (Highest weight)
                if (lowerMerchant.isNotBlank()) {
                    if (rule.merchantKeywords.any { lowerMerchant.contains(it) || it.contains(lowerMerchant) }) {
                        currentScore += 30
                    }
                }

                // Check if rule's merchant keywords appear anywhere in combined text
                for (mKeyword in rule.merchantKeywords) {
                    if (mKeyword.isNotBlank() && combined.contains(mKeyword)) {
                        currentScore += 20
                        break
                    }
                }

                // Check text keywords
                for (tKeyword in rule.textKeywords) {
                    if (tKeyword.isNotBlank() && combined.contains(tKeyword)) {
                        currentScore += 10
                        break
                    }
                }

                if (currentScore > bestScore) {
                    bestScore = currentScore
                    bestRule = rule
                }
            }

            return bestRule?.categoryKey ?: DebitCategory.GENERAL.name
        }
    }

    private val POPULAR_MERCHANTS = listOf(
        "SWIGGY" to "Swiggy",
        "ZOMATO" to "Zomato",
        "UBER" to "Uber",
        "OLA" to "Ola",
        "RAPIDO" to "Rapido",
        "BLINKIT" to "Blinkit",
        "ZEPTO" to "Zepto",
        "BIGBASKET" to "BigBasket",
        "INSTAMART" to "Instamart",
        "AMAZON" to "Amazon",
        "FLIPKART" to "Flipkart",
        "MYNTRA" to "Myntra",
        "AJIO" to "Ajio",
        "NETFLIX" to "Netflix",
        "SPOTIFY" to "Spotify",
        "AIRTEL" to "Airtel",
        "JIO" to "Jio",
        "MCDONALD" to "McDonald's",
        "DOMINO" to "Domino's",
        "STARBUCKS" to "Starbucks",
        "HPCL" to "HPCL Fuel",
        "IOCL" to "IndianOil",
        "BPCL" to "Bharat Petroleum",
        "IRCTC" to "IRCTC",
        "SMART SUPERMARKET" to "Smart Supermarket",
        "SMART BAZAAR" to "Smart Supermarket",
        "SMART POINT" to "Smart Supermarket",
        "RELIANCE SMART" to "Smart Supermarket",
        "PLUXEE" to "Pluxee",
        "SODEXO" to "Pluxee"
    )

    private val TELECOM_POPULAR_KEYS = setOf("AIRTEL", "JIO")

    private val MERCHANT_STOPWORDS = setOf(
        "a/c", "ac", "account", "acct", "inr", "rs", "avl", "bal", "balance", "ref", "upi",
        "customer", "dear", "hdfc", "sbi", "icici", "axis", "kotak", "bank", "card", "limit"
    )

    /**
     * Pulls counterparty name from bank/UPI alert body (before using notification title).
     */
    fun extractCounterpartyFromTransactionBody(text: String): String {
        val normalized = text.replace("\n", " ").trim()
        if (normalized.isBlank()) return ""

        val patterns = listOf(
            Regex(
                """(?:debited|credited|transfer(?:red)?|sent|paid|spent|charged)\s+(?:from|for)?\s+.*?\s+to\s+([A-Za-z0-9][A-Za-z0-9\s.'&/_-]{1,45}?)(?:\s+on|\s+dated|\s+ref|\s+upi|\s+avl|\s+bal|\.|,|$)""",
                RegexOption.IGNORE_CASE
            ),
            Regex(
                """(?:spent|charged)\s+(?:on|at)\s+(?:your\s+)?(?:\w+\s+)?(?:card|a/c)?\s*(?:ending\s+\d+)?\s*(?:at|on)?\s*([A-Za-z0-9][A-Za-z0-9\s.'&/_-]{1,40}?)(?:\s+on|\s+dated|\s+avl|\.|,|$)""",
                RegexOption.IGNORE_CASE
            ),
            Regex(
                """(?:paid|payment)\s+to\s+([A-Za-z0-9][A-Za-z0-9\s._@&'-]{1,35}?)(?:\s+using|\s+via|\s+on|\s+ref|\.|,|$)""",
                RegexOption.IGNORE_CASE
            ),
            Regex(
                """(?:received|received\s+from)\s+([A-Za-z0-9][A-Za-z0-9\s._@&'-]{1,35}?)(?:\s+in|\s+on|\s+ref|\.|,|$)""",
                RegexOption.IGNORE_CASE
            ),
            Regex(
                """(?:towards|to|at|vpa|info:)\s+([A-Za-z0-9][A-Za-z0-9\s._@&'-]{2,35}?)(?:\s+on|\s+ref|\s+txn|\s+upi|\s+bal|\.|,|$)""",
                RegexOption.IGNORE_CASE
            ),
            Regex("""\b([a-z0-9][a-z0-9._-]{1,40}@[a-z]{2,16})\b""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            val match = pattern.find(normalized) ?: continue
            val candidate = match.groupValues[1].trim().trimEnd('.', ',')
            if (isValidMerchantCandidate(candidate)) {
                return candidate
            }
        }
        return ""
    }

    private fun isValidMerchantCandidate(candidate: String): Boolean {
        if (candidate.length < 2) return false
        val lower = candidate.lowercase()
        if (MERCHANT_STOPWORDS.any { lower == it || lower.startsWith("$it ") }) return false
        if (lower.matches(Regex("""^\*+\d+$"""))) return false
        if (lower.matches(Regex("""^x{2,}\d+$""", RegexOption.IGNORE_CASE))) return false
        return true
    }

    private fun resolveMerchantDisplayName(candidate: String, bodyText: String): String {
        val enriched = MerchantEnrichmentEngine.enrich(
            rawMerchant = candidate,
            rawText = bodyText,
            senderOrTitle = ""
        )
        if (enriched.isVerifiedBrand) return enriched.cleanName
        val cleaned = MerchantEnrichmentEngine.cleanRawMerchantString(candidate)
        return cleaned.ifBlank { candidate }
    }

    fun extractMerchant(text: String, fallback: String): String {
        val allowTelecom = MerchantEnrichmentEngine.isTelecomRechargeOrBillContext(text)
        val safeTitle = if (MerchantEnrichmentEngine.isCarrierNotificationTitle(fallback)) {
            ""
        } else {
            fallback.replace(Regex("""^[A-Z]{2}-"""), "").trim()
        }

        // 1. Structured counterparty from alert body (highest priority)
        val fromBody = extractCounterpartyFromTransactionBody(text)
        if (fromBody.isNotBlank()) {
            // Payee named in the SMS (e.g. "to AIRTEL", "paid to Bharti Airtel") is a real telecom txn
            MerchantEnrichmentEngine.telecomBrandFromPayee(fromBody)?.let { return it }
            return resolveMerchantDisplayName(fromBody, text)
        }

        // 1b. SIM title is Airtel/Jio only when the body is actually a recharge or bill
        if (allowTelecom) {
            MerchantEnrichmentEngine.telecomBrandFromCarrierTitle(fallback)?.let { return it }
        }

        // 2. Known merchants mentioned in body only (ignore telecom unless recharge/bill)
        val upperText = text.uppercase()
        for ((key, displayName) in POPULAR_MERCHANTS) {
            if (key in TELECOM_POPULAR_KEYS && !allowTelecom) continue
            if (upperText.contains(key)) return displayName
        }

        // 3. Brand enrichment from body text (not notification title)
        val bodyBrand = MerchantEnrichmentEngine.enrich(rawMerchant = "", rawText = text, senderOrTitle = "")
        if (bodyBrand.isVerifiedBrand) return bodyBrand.cleanName

        // 4. Regex fallbacks on body
        val patterns = listOf(
            Regex("""(?:towards|to|at|vpa|info:)\s+([A-Za-z0-9\s._@&-]{2,30}?)(?:\s+on|\s+ref|\s+txn|\s+upi|\s+bal|\.|\s*$)""", RegexOption.IGNORE_CASE),
            Regex("""paid\s+(?:to\s+)?([A-Za-z0-9\s._@&-]{2,25}?)(?:\s+using|\s+on|\s+ref|\.|\s*$)""", RegexOption.IGNORE_CASE),
            Regex("""received\s+(?:from\s+)?([A-Za-z0-9\s._@&-]{2,25}?)(?:\s+in|\s+on|\s+ref|\.|\s*$)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                val candidate = match.groupValues[1].trim()
                if (isValidMerchantCandidate(candidate)) {
                    return resolveMerchantDisplayName(candidate, text)
                }
            }
        }

        // 5. Non-carrier notification title as last resort
        if (safeTitle.isNotBlank()) {
            return resolveMerchantDisplayName(safeTitle, text)
        }

        return "Bank Transaction"
    }

    fun extractReference(text: String): String {
        val refRegex = Regex("""(?:upi\s*ref|ref\s*no|ref|utr|txn\s*id)[\s\:\-]+([A-Za-z0-9]{6,22})""", RegexOption.IGNORE_CASE)
        val match = refRegex.find(text)
        return match?.groupValues?.getOrNull(1)?.trim() ?: ""
    }

    fun getSampleNotifications(): List<Pair<String, String>> {
        return listOf(
            // 1. HDFC Bank & Credit Cards
            "HDFC Bank" to "Hi! Thanks for reaching out to HDFC Bank!\n\nYour account balance for account ending with 0989 is ₹3,021.57\n\nExplore our WhatsApp Banking for 220+ transactions all at your fingertips!",
            "AD-HDFCBK" to "Dear Customer,\nYour Balance in account no. ending with 0989 is Rs. 3,021.57\n-HDFC Bank",
            "AD-HDFCBK" to "Dear Customer, INR 450.00 debited from A/C **1234 to SWIGGY on 09-OCT-26. Avl Bal: INR 35,420.50. Ref: 428391823.",
            "AD-HDFCCC" to "Alert: INR 3,450.00 spent on your HDFC Bank Credit Card ending 9821 at Amazon. Available Limit: INR 1,45,000.00.",
            "AD-HDFCBK" to "Dear Customer, available balance in your HDFC Bank A/C ending 1234 as on 09-OCT-26 is INR 45,200.00.",

            // 2. State Bank of India (SBI & SBI Card)
            "VK-SBIINB" to "Your A/C XX5678 debited by Rs.820.00 on 09-OCT-26 transfer to ZOMATO. Avail Bal: Rs.14,230.75. UPI Ref 428192837123.",
            "VM-SBICRD" to "Rs. 2,190.00 spent on your SBI Credit Card ending 5432 at Flipkart on 09-OCT-26. Total Available Limit: Rs. 92,000.",
            "VK-SBIINB" to "Dear SBI User, your A/C XX5678 balance is Rs. 34,200.50 as of 09-10-26.",

            // 3. ICICI Bank & Credit Card
            "BP-ICICIB" to "Alert: INR 380.00 debited from Account XX9012 for ride at UBER on 09-OCT-26. Avail Bal: INR 48,200.00.",
            "BP-ICICIC" to "INR 1,850.00 spent using ICICI Bank Card XX6712 at Zomato on 09-OCT-26. Avl Limit: INR 85,000.00.",
            "BP-ICICIB" to "INR 12,000.00 debited from Account XX9012 towards Rent via IMPS. Avail Bal: INR 36,200.00.",

            // 4. Axis Bank & Credit Card
            "VM-AXISBK" to "Alert: INR 1,299.00 debited from A/C **4321 at BLINKIT on 09-OCT-26. Avl Bal: INR 32,150.00.",
            "VM-AXISCR" to "Rs. 4,200.00 spent on your Axis Bank Credit Card ending 3214 at Myntra. Available limit: Rs. 1,20,000.00.",

            // 5. Kotak Mahindra Bank & Card
            "JM-KOTAKB" to "Sent Rs. 650.00 from Kotak A/C 7890 to ZEPTO. Avl Bal: Rs. 54,100.00.",
            "JM-KOTAKC" to "Rs 1,250.00 spent on Kotak Credit Card ending 8765 at Swiggy. Available limit: Rs 68,000.",

            // 6. IDFC FIRST, OneCard, Slice & FinTech Cards
            "IDFCFB" to "INR 4,500.00 debited from IDFC FIRST Bank A/C ending 8901 to Amazon Pay. Avl Bal: INR 28,900.00.",
            "ONECRD" to "Rs. 890.00 spent on your OneCard ending 4321 at Zepto. Available limit: Rs. 45,000.00.",
            "SLICE" to "Rs. 650.00 spent on Slice Card ending 1234 at Starbucks.",

            // 7. Salary & Income Credits
            "AD-HDFCBK" to "Dear Customer, your Account **1234 credited with INR 65,000.00 on 01-OCT-26 by SALARY. Avl Bal: INR 95,000.00.",
            "PLUXEE" to "Your Pluxee Meal Card ending 9912 credited with Rs. 2,200.00 for October allowance."
        )
    }
}
