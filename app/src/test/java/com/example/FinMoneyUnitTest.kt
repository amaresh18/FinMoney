package com.example

import com.example.model.DefaultDebitCategories
import com.example.data.model.DebitItem
import com.example.data.model.BankTransactionRecord
import com.example.data.model.isActualCreditCardTransaction
import com.example.data.model.isCreditCardRecord
import com.example.data.model.getDisplayTitle
import com.example.data.model.getTagList
import com.example.data.model.LearnedTransactionRule
import com.example.learning.AdaptiveLearningEngine
import com.example.learning.DueItemType
import com.example.learning.DuePaymentMatchCandidate
import com.example.learning.DuePaymentMatchEngine
import com.example.learning.DuePaymentMatchPrompt
import com.example.learning.SimilarTransactionsManager
import com.example.learning.SimilarTransactionsPrompt
import com.example.data.model.LoanTransaction
import com.example.data.model.LoanType
import org.junit.Assert.*
import org.junit.Test

class FinMoneyUnitTest {

    @Test
    fun testDefaultCategoriesIncludeAllRequestedItems() {
        val categories = DefaultDebitCategories.list
        assertTrue(categories.contains("Groceries"))
        assertTrue(categories.contains("Recharge"))
        assertTrue(categories.contains("Chit Payments"))
        assertTrue(categories.contains("Fuel"))
    }

    @Test
    fun testCategoryDeduplication() {
        val defaultList = listOf("Chit Payments", "EMI", "Groceries", "Fuel")
        val customList = listOf("groceries", "Groceries", "Gym", "gym", "Fuel ")

        val merged = (defaultList + customList).distinctBy { it.trim().lowercase() }
        assertEquals(5, merged.size)
        assertTrue(merged.any { it.equals("Gym", ignoreCase = true) })
    }

    @Test
    fun testFlexibleCashCalculation() {
        val income = 75000.0
        val debits = 25000.0
        val flexibleCash = income - debits
        assertEquals(50000.0, flexibleCash, 0.001)

        val daysRemaining = 25
        val dailySafeSpend = flexibleCash / daysRemaining
        assertEquals(2000.0, dailySafeSpend, 0.001)
    }

    @Test
    fun testMerchantTransactionClassification() {
        // Swiggy
        val swiggySms = "Dear Customer, INR 450.00 debited from A/C **1234 to SWIGGY on 09-OCT-26. Avl Bal: INR 35,420.50."
        val swiggyLabel = com.example.data.bank.TransactionClassificationEngine.classifyLabel(
            text = swiggySms,
            senderOrTitle = "AD-HDFCBK",
            extractedMerchant = "Swiggy"
        )
        assertEquals("Merchant", swiggyLabel)

        // Zomato
        val zomatoSms = "Your A/C XX5678 debited by Rs.820.00 on 09-OCT-26 transfer to ZOMATO. Avail Bal: Rs.14,230.75."
        val zomatoLabel = com.example.data.bank.TransactionClassificationEngine.classifyLabel(
            text = zomatoSms,
            senderOrTitle = "VK-SBIINB",
            extractedMerchant = "Zomato"
        )
        assertEquals("Merchant", zomatoLabel)

        // Uber
        val uberSms = "Alert: INR 380.00 debited from Account XX9012 for ride at UBER. Avail Bal: INR 48,200.00."
        val uberLabel = com.example.data.bank.TransactionClassificationEngine.classifyLabel(
            text = uberSms,
            senderOrTitle = "BP-ICICIB",
            extractedMerchant = "Uber"
        )
        assertEquals("Merchant", uberLabel)
    }

    @Test
    fun testCreditCardTransactionClassification() {
        // HDFC Credit Card
        val cardSms = "Alert: INR 3,450.00 spent on your HDFC Bank Credit Card ending 9821. Available Limit: INR 1,45,000.00."
        val cardLabel = com.example.data.bank.TransactionClassificationEngine.classifyLabel(
            text = cardSms,
            senderOrTitle = "AD-HDFCCC"
        )
        assertEquals("Credit Card", cardLabel)

        // SBI Card
        val sbiCardSms = "Rs. 2,190.00 charged to your SBI Card ending 5432 on 09-OCT-26. Total Available Limit: Rs. 92,000. Min Due: Rs. 500."
        val sbiCardLabel = com.example.data.bank.TransactionClassificationEngine.classifyLabel(
            text = sbiCardSms,
            senderOrTitle = "VM-SBICRD"
        )
        assertEquals("Credit Card", sbiCardLabel)
    }

    @Test
    fun testBankTransactionClassification() {
        // Salary credit
        val salarySms = "Dear Customer, your Account **1234 credited with INR 65,000.00 on 01-OCT-26 by SALARY. Avl Bal: INR 95,000.00."
        val salaryLabel = com.example.data.bank.TransactionClassificationEngine.classifyLabel(
            text = salarySms,
            senderOrTitle = "AD-HDFCBK"
        )
        assertEquals("Bank", salaryLabel)

        // ATM Cash withdrawal
        val atmSms = "Dear SBI User, Cash withdrawal of Rs. 5,000.00 from ATM using A/C **5678 on 09-OCT-26. Avail Bal: Rs. 9,230.75."
        val atmLabel = com.example.data.bank.TransactionClassificationEngine.classifyLabel(
            text = atmSms,
            senderOrTitle = "VK-SBIINB"
        )
        assertEquals("Bank", atmLabel)
    }

    @Test
    fun testParserFullIntegration() {
        val parsedSwiggy = com.example.data.bank.BankTransactionParser.parse(
            text = "Dear Customer, INR 450.00 debited from A/C **1234 to SWIGGY on 09-OCT-26. Avl Bal: INR 35,420.50.",
            senderOrTitle = "AD-HDFCBK"
        )
        assertNotNull(parsedSwiggy)
        assertEquals(450.0, parsedSwiggy!!.amount, 0.01)
        assertEquals("Swiggy", parsedSwiggy.merchantOrParty)
        assertEquals("Merchant", parsedSwiggy.classification)
        assertEquals(35420.50, parsedSwiggy.availableBalance!!, 0.01)
        assertEquals(com.example.data.model.DebitCategory.OUTSIDE_FOOD.name, parsedSwiggy.category)
    }

    @Test
    fun testMerchantNotCarrierTitleWhenSimLabelIsAirtel() {
        val parsed = com.example.data.bank.BankTransactionParser.parse(
            text = "Dear Customer, INR 450.00 debited from A/C **1234 to SWIGGY on 09-OCT-26. Avl Bal: INR 35,420.50.",
            senderOrTitle = "Airtel"
        )
        assertNotNull(parsed)
        assertEquals("Swiggy", parsed!!.merchantOrParty)
    }

    @Test
    fun testAirtelRechargeAndBillStillIdentified() {
        val recharge = com.example.data.bank.BankTransactionParser.parse(
            text = "INR 299.00 debited from A/C **1234 to AIRTEL for mobile recharge. Avl Bal: INR 12,000.00.",
            senderOrTitle = "AD-HDFCBK"
        )
        assertNotNull(recharge)
        assertEquals("Airtel", recharge!!.merchantOrParty)
        assertEquals(com.example.data.model.DebitCategory.RECHARGE.name, recharge.category)

        val bill = com.example.data.bank.BankTransactionParser.parse(
            text = "Rs.799.00 paid to Bharti Airtel towards postpaid bill. Ref 998877.",
            senderOrTitle = "VK-SBIINB"
        )
        assertNotNull(bill)
        assertEquals("Airtel", bill!!.merchantOrParty)

        val simTitleRecharge = com.example.data.bank.BankTransactionParser.parse(
            text = "INR 199.00 debited from A/C **4321. Prepaid recharge successful. Validity extended. Avl Bal INR 8,200.00.",
            senderOrTitle = "Airtel"
        )
        assertNotNull(simTitleRecharge)
        assertEquals("Airtel", simTitleRecharge!!.merchantOrParty)
    }

    @Test
    fun testAutomaticCategoryKeywordMapping() {
        val parser = com.example.service.TransactionParser

        // Food & Dining
        assertEquals(
            com.example.data.model.DebitCategory.OUTSIDE_FOOD.name,
            parser.identifyCategoryNLP("Paid Rs 850 at Zomato Restaurant", "AD-HDFCBK", "Zomato")
        )

        // Groceries
        assertEquals(
            com.example.data.model.DebitCategory.GROCERIES.name,
            parser.identifyCategoryNLP("INR 1200 debited for Blinkit Grocery order", "VK-SBIINB", "Blinkit")
        )

        // Shopping
        assertEquals(
            com.example.data.model.DebitCategory.SHOPPING.name,
            parser.identifyCategoryNLP("INR 3499 spent on HDFC Credit Card at Amazon", "AD-HDFCCC", "Amazon")
        )

        // Electricity / Utilities
        assertEquals(
            com.example.data.model.DebitCategory.ELECTRICITY.name,
            parser.identifyCategoryNLP("Rs 3021.57 paid to BESCOM Electricity Bill", "BP-ICICIB", "BESCOM")
        )

        // Recharge
        assertEquals(
            com.example.data.model.DebitCategory.RECHARGE.name,
            parser.identifyCategoryNLP("Rs 479 charged for Airtel Prepaid Recharge", "AD-HDFCBK", "Airtel")
        )

        // Fuel
        assertEquals(
            com.example.data.model.DebitCategory.FUEL.name,
            parser.identifyCategoryNLP("Rs 2000 spent at HPCL Petrol Pump", "VK-SBIINB", "HPCL")
        )

        // Travel
        assertEquals(
            com.example.data.model.DebitCategory.TRAVEL.name,
            parser.identifyCategoryNLP("INR 420 paid for Uber ride", "AD-HDFCBK", "Uber")
        )

        // OTT / Subscriptions
        assertEquals(
            com.example.data.model.DebitCategory.OTT.name,
            parser.identifyCategoryNLP("Rs 649 debited for Netflix subscription", "AD-HDFCBK", "Netflix")
        )

        // Pharmacy
        assertEquals(
            com.example.data.model.DebitCategory.PHARMACY.name,
            parser.identifyCategoryNLP("Rs 560 paid at Apollo Pharmacy", "AD-HDFCBK", "Apollo Pharmacy")
        )

        // Investment
        assertEquals(
            com.example.data.model.DebitCategory.INVESTMENT.name,
            parser.identifyCategoryNLP("INR 5000 debited for Zerodha SIP Mutual Fund", "AD-HDFCBK", "Zerodha")
        )
    }

    @Test
    fun testJsonSchemaOutput() {
        val sms = "Dear Customer, INR 450.00 debited from A/C **1234 to SWIGGY on 09-OCT-26. Avl Bal: INR 35,420.50."
        val jsonStr = com.example.service.TransactionParser.parseToJson(sms, "AD-HDFCBK")
        assertNotNull(jsonStr)

        assertTrue(jsonStr.contains("\"is_financial_transaction\": true"))
        assertTrue(jsonStr.contains("\"transaction_type\": \"DEBIT\""))
        assertTrue(jsonStr.contains("\"transaction_amount\": 450.00"))
        assertTrue(jsonStr.contains("\"currency\": \"INR\""))
        assertTrue(jsonStr.contains("\"account_type\": \"ACCOUNT\""))
        assertTrue(jsonStr.contains("\"account_identifier\": \"XX1234\""))
        assertTrue(jsonStr.contains("\"merchant_name\": \"Swiggy\""))
        assertTrue(jsonStr.contains("\"available_balance\": 35420.50"))

        // Test Non-financial OTP SMS
        val otpSms = "Your OTP for login to FinMoney is 894210. Do not share with anyone."
        val otpJsonStr = com.example.service.TransactionParser.parseToJson(otpSms, "AD-FINMONEY")
        assertTrue(otpJsonStr.contains("\"is_financial_transaction\": false"))
    }

    @Test
    fun testNegativeFilteringRules() {
        val parser = com.example.service.TransactionParser

        // 1. Decline / Failure
        val failedSms = "Transaction failed! Your payment of Rs. 1500 to Swiggy was declined due to insufficient funds."
        val failedJson = parser.parseToJson(failedSms, "AD-HDFCBK")
        assertTrue(failedJson.contains("\"is_financial_transaction\": false"))

        // 2. Banking Alert / Password Expire
        val alertSms = "Alert: Your NetBanking password will expire in 3 days. Log in to change it."
        val alertJson = parser.parseToJson(alertSms, "AD-HDFCBK")
        assertTrue(alertJson.contains("\"is_financial_transaction\": false"))

        // 3. Credit Card Offer / Pre-approved
        val promoSms = "You are pre-approved for a Credit Card with a limit of Rs. 2,00,000. Apply now!"
        val promoJson = parser.parseToJson(promoSms, "VM-SBICRD")
        assertTrue(promoJson.contains("\"is_financial_transaction\": false"))

        // 4. Marketing / Engagement
        val marketingSms = "Spin to win exciting cashback rewards on your next UPI payment! Click here."
        val marketingJson = parser.parseToJson(marketingSms, "PHONEPE")
        assertTrue(marketingJson.contains("\"is_financial_transaction\": false"))

        // 5. Delivery & Order Status Ping
        val deliveryPing = "Your Swiggy order of Rs. 450 is on the way!"
        val deliveryJson = parser.parseToJson(deliveryPing, "Swiggy")
        assertTrue(deliveryJson.contains("\"is_financial_transaction\": false"))
        assertTrue(deliveryJson.contains("\"debug_reason\": \"Matches delivery/order status tracking pattern\""))

        // 6. Personal Chat Message
        val chatMsg = "Hey, I sent you 500 bucks yesterday check it"
        val chatJson = parser.parseToJson(chatMsg, "Rahul", sourcePackage = "com.whatsapp")
        assertTrue(chatJson.contains("\"is_financial_transaction\": false"))
        assertTrue(chatJson.contains("\"debug_reason\": \"Matches personal chat message pattern\""))

        // 7. Bill Generation / Late Payment Reminder
        val billMsg = "Your Airtel postpaid bill of Rs. 799 is generated"
        val billJson = parser.parseToJson(billMsg, "Airtel")
        assertTrue(billJson.contains("\"is_financial_transaction\": false"))
        assertTrue(billJson.contains("\"debug_reason\": \"Matches bill due/generated reminder pattern\""))

        // 8. Reward / Scratch Card Loop
        val rewardMsg = "You won a scratch card up to ₹500! Tap to claim"
        val rewardJson = parser.parseToJson(rewardMsg, "Google Pay")
        assertTrue(rewardJson.contains("\"is_financial_transaction\": false"))
        assertTrue(rewardJson.contains("\"debug_reason\": \"Matches scratch card/reward promotional pattern\""))
    }

    @Test
    fun testDebitCardNotClassifiedAsCreditCard() {
        // Negative Scenario 1: Explicit Debit Card spend
        val debitCardSms = "Dear Customer, INR 1,250.00 spent on your Debit Card ending 4321 at Reliance Fresh. Avail Bal: INR 24,000."
        val label1 = com.example.data.bank.TransactionClassificationEngine.classifyLabel(
            text = debitCardSms,
            senderOrTitle = "AD-HDFCBK"
        )
        assertNotEquals("Credit Card", label1)

        // Negative Scenario 2: Card swipe on bank account (card ending 9876 linked to A/c)
        val cardBankSms = "INR 450 debited from A/c XX1234 via Card ending 9876. Avail Bal: INR 15,200."
        val label2 = com.example.data.bank.TransactionClassificationEngine.classifyLabel(
            text = cardBankSms,
            senderOrTitle = "AD-HDFCBK"
        )
        assertNotEquals("Credit Card", label2)

        // Negative Scenario 3: ATM Cash withdrawal with card
        val atmSms = "INR 2,000 withdrawn at ATM using Card ending 1122. Avl Bal: INR 8,000."
        val label3 = com.example.data.bank.TransactionClassificationEngine.classifyLabel(
            text = atmSms,
            senderOrTitle = "VK-SBIINB"
        )
        assertEquals("Bank", label3)
        assertNotEquals("Credit Card", label3)
    }

    @Test
    fun testCreditCardFilterPositiveAndNegativeScenarios() {
        // POSITIVE 1: Explicitly marked isPaidViaCreditCard = true
        val ccExpense = DebitItem(
            id = 1,
            title = "Amazon Gadget Purchase",
            amount = 4500.0,
            category = "SHOPPING",
            isPaidViaCreditCard = true,
            isPaidViaDebitCard = false
        )
        assertTrue(ccExpense.isActualCreditCardTransaction())

        // POSITIVE 2: Explicit Credit Card in title
        val ccTitleExpense = DebitItem(
            id = 2,
            title = "HDFC Credit Card Bill",
            amount = 12000.0,
            category = "Credit Card",
            isPaidViaCreditCard = false,
            isPaidViaDebitCard = false
        )
        assertTrue(ccTitleExpense.isActualCreditCardTransaction())

        // NEGATIVE 1: Regular bank expense with "account" in notes (contains substring "cc"!)
        val bankExpenseWithAccountNote = DebitItem(
            id = 3,
            title = "Electricity Bill",
            amount = 1200.0,
            category = "UTILITIES",
            isPaidViaCreditCard = false,
            isPaidViaDebitCard = false,
            notes = "Debited from account ending in 1234. Success"
        )
        assertFalse("Should not match credit card just because notes contain 'account' or 'success'", bankExpenseWithAccountNote.isActualCreditCardTransaction())

        // NEGATIVE 2: Debit Card transaction
        val debitCardExpense = DebitItem(
            id = 4,
            title = "Debit Card Spend at DMART",
            amount = 3200.0,
            category = "GROCERIES",
            isPaidViaCreditCard = false,
            isPaidViaDebitCard = true
        )
        assertFalse("Debit card must never be a credit card", debitCardExpense.isActualCreditCardTransaction())

        // NEGATIVE 3: Category with "CARD" like "Card Payment" or "Gift Card"
        val giftCardExpense = DebitItem(
            id = 5,
            title = "Amazon Gift Card",
            amount = 1000.0,
            category = "CARD",
            isPaidViaCreditCard = false,
            isPaidViaDebitCard = false
        )
        assertFalse("Generic category CARD must not match Credit Card filter", giftCardExpense.isActualCreditCardTransaction())

        // NEGATIVE 4: ATM cash withdrawal
        val atmExpense = DebitItem(
            id = 6,
            title = "ATM Cash Withdrawal",
            amount = 5000.0,
            category = "GENERAL",
            isPaidViaCreditCard = false,
            isPaidViaDebitCard = false,
            notes = "Cash withdrawal from ATM"
        )
        assertFalse("ATM withdrawal must never match Credit Card filter", atmExpense.isActualCreditCardTransaction())

        // NEGATIVE 5: Salary or Loan Credit
        val loanCredit = DebitItem(
            id = 7,
            title = "Personal Loan Credit",
            amount = 50000.0,
            category = "LOAN",
            isPaidViaCreditCard = false,
            isPaidViaDebitCard = false
        )
        assertFalse("Loan credit must not match Credit Card filter", loanCredit.isActualCreditCardTransaction())
    }

    @Test
    fun testBankTransactionRecordCreditCardFiltering() {
        val ccRecord = BankTransactionRecord(
            id = 1,
            bankName = "HDFC Bank Credit Card",
            classification = "Credit Card",
            originalText = "Alert: Rs. 3,450.00 spent on your HDFC Bank Credit Card ending 9821. Available Limit: Rs. 1,45,000.00."
        )
        assertTrue(ccRecord.isCreditCardRecord())

        val debitRecord = BankTransactionRecord(
            id = 2,
            bankName = "HDFC Bank",
            classification = "Bank",
            originalText = "Dear Customer, INR 1,250.00 spent on your Debit Card ending 4321. Avail Bal: INR 24,000."
        )
        assertFalse(debitRecord.isCreditCardRecord())

        val atmRecord = BankTransactionRecord(
            id = 3,
            bankName = "SBI Bank",
            classification = "Bank",
            originalText = "Cash withdrawal of Rs. 5,000.00 from ATM using A/C **5678."
        )
        assertFalse(atmRecord.isCreditCardRecord())
    }

    @Test
    fun testParenthesesMerchantExtractionAndVpaMaskedDigitsFix() {
        val sms1 = "Rs.105.00 is debited from your account ending 0989 towards VPA ******8732@ybl (HONEY BAKERY) on 04-11-26"
        val parsed1 = com.example.service.TransactionParser.parse(sms1, "AD-HDFCBK")
        assertEquals(105.0, parsed1.amount!!, 0.01)
        assertTrue(parsed1.merchantName.equals("HONEY BAKERY", ignoreCase = true))
        assertEquals("OUTSIDE_FOOD", parsed1.category)

        val sms2 = "Rs.250.00 debited from A/C 1234 towards VPA xyz@apl (TORREWAR LAXMI) on 04-11-26"
        val parsed2 = com.example.service.TransactionParser.parse(sms2, "AD-HDFCBK")
        assertEquals(250.0, parsed2.amount!!, 0.01)
        assertEquals("TORREWAR LAXMI", parsed2.merchantName)
        assertEquals("GENERAL", parsed2.category)
    }

    @Test
    fun testIciciAccountNumberNotCapturedAsAmount() {
        val sms = "ICICI Bank Account XX501 credited:Rs. 15,000.00 on 04-Nov-26 by NEFT. Avail Bal: Rs. 45,000.00"
        val parsed = com.example.service.TransactionParser.parse(sms, "BP-ICICIB")
        assertEquals(15000.0, parsed.amount!!, 0.01)
        assertEquals(com.example.data.bank.BankTxType.CREDIT, parsed.transactionType)
    }

    @Test
    fun testStockMarketNewsAndOrderDealsIgnored() {
        val news1 = "🚀 Highway Infra’s ₹198 Cr Milestone! 🏗️"
        assertFalse(com.example.service.TransactionParser.isFinancialText(news1))

        val news2 = "🚢 GRSE Bags ₹32,000 Cr Navy Deal!"
        assertFalse(com.example.service.TransactionParser.isFinancialText(news2))

        val news3 = "PM-Kisan: ₹2,000 Credited Today! Over ₹18,680 Cr transferred via DBT"
        assertFalse(com.example.service.TransactionParser.isFinancialText(news3))
    }

    @Test
    fun testUtkarshHelplineNotExtractedAsMerchant() {
        val sms = "Dear Customer, INR 500.00 debited from A/C 1234 to SWIGGY. To dispute call 18003097986 - Utkarsh SFBL"
        val parsed = com.example.service.TransactionParser.parse(sms, "UTKARSH")
        assertEquals("Swiggy", parsed.merchantName)
        assertFalse(parsed.merchantName.contains("dispute", ignoreCase = true))
        assertFalse(parsed.merchantName.contains("1800", ignoreCase = true))
    }

    @Test
    fun testAuditLogMerchantCategories() {
        val catGroceries = com.example.data.bank.BankTransactionParser.identifyCategory(
            text = "Paid Rs 300 at Kamakshi Super Store",
            merchantName = "Kamakshi Super Store"
        )
        assertEquals("GROCERIES", catGroceries)

        val catPharmacy = com.example.data.bank.BankTransactionParser.identifyCategory(
            text = "Rs 450 debited at MedPlus Pharmacy",
            merchantName = "MedPlus"
        )
        assertEquals("PHARMACY", catPharmacy)

        val catGeneral = com.example.data.bank.BankTransactionParser.identifyCategory(
            text = "Rs 200 debited towards VPA user@upi on 04-11-26. To dispute click here.",
            merchantName = "RAMESH KUMAR"
        )
        assertEquals("GENERAL", catGeneral)
    }

    @Test
    fun testHistoricalExpenseTitleFallbackToTagsAndSubcategory() {
        // Case 1: Title is blank, but tags exist -> title resolves to first tag
        val itemWithTags = DebitItem(
            title = "",
            category = "OUTSIDE_FOOD",
            tags = "Swiggy,Dinner"
        )
        assertEquals("Swiggy", itemWithTags.getDisplayTitle())
        assertEquals(listOf("Swiggy", "Dinner"), itemWithTags.getTagList())

        // Case 2: Title is blank, no tags, but custom subcategory exists -> resolves to subcategory
        val itemWithSubcategory = DebitItem(
            title = "",
            category = "CUSTOM",
            customCategoryName = "Maid Salary"
        )
        assertEquals("Maid Salary", itemWithSubcategory.getDisplayTitle())

        // Case 3: Title is blank, hashtag in notes -> resolves to hashtag
        val itemWithNotesHashtag = DebitItem(
            title = "",
            category = "GROCERIES",
            notes = "Weekly shopping at store #Blinkit"
        )
        assertEquals("Blinkit", itemWithNotesHashtag.getDisplayTitle())
        assertEquals(listOf("Blinkit"), itemWithNotesHashtag.getTagList())

        // Case 4: Title is present and explicit -> preserves title
        val itemWithTitle = DebitItem(
            title = "Airtel Fiber Broadband",
            category = "INTERNET",
            tags = "WiFi,Work"
        )
        assertEquals("Airtel Fiber Broadband", itemWithTitle.getDisplayTitle())
        assertEquals(listOf("WiFi", "Work"), itemWithTitle.getTagList())

        // Case 5: Title is blank and no tags/notes -> falls back to category display
        val itemGeneral = DebitItem(
            title = "",
            category = "FUEL"
        )
        assertEquals("FUEL", itemGeneral.getDisplayTitle())
    }

    @Test
    fun testAdaptiveLearningPatternExtraction() {
        // Test normalization of merchant names and suffixes
        val keySwiggy = AdaptiveLearningEngine.extractPatternKey("SWIGGY BANGALORE", "Spent Rs 290 at SWIGGY")
        assertEquals("swiggy", keySwiggy)

        val keyNetflix = AdaptiveLearningEngine.extractPatternKey("Netflix Entertainment Services India Pvt Ltd", "Rs 649 debited for Netflix")
        assertEquals("netflix", keyNetflix)

        // Test UPI VPA pattern extraction
        val keyVpa = AdaptiveLearningEngine.extractPatternKey("", "Paid Rs 150 to rakesh.kumar@okhdfcbank using UPI")
        assertEquals("rakesh.kumar@okhdfcbank", keyVpa)

        // Test keyword recognition in text
        val keyUber = AdaptiveLearningEngine.extractPatternKey("UBER INDIA TECHNOLOGY", "INR 350 debited for Uber ride")
        assertEquals("uber", keyUber)
    }

    @Test
    fun testAdaptiveLearningRuleModelBehavior() {
        // Rule created from user editing an expense
        val learnedRule = LearnedTransactionRule(
            id = 1L,
            userId = "user1",
            patternKey = "swiggy",
            merchantDisplayName = "Swiggy Daily",
            matchType = "MERCHANT",
            learnedCategory = "OUTSIDE_FOOD",
            learnedTitle = "Swiggy Daily Food",
            learnedTags = "Lunch,Office",
            isAutoIgnored = false,
            rejectCount = 0,
            approveCount = 3,
            editCount = 1
        )

        assertFalse(learnedRule.isAutoIgnored)
        assertEquals("Swiggy Daily Food", learnedRule.learnedTitle)
        assertEquals("OUTSIDE_FOOD", learnedRule.learnedCategory)
        assertEquals("Lunch,Office", learnedRule.learnedTags)

        // Rule created from user repeatedly rejecting unwanted alerts (e.g., self-transfers or noise)
        val noiseRule = LearnedTransactionRule(
            id = 2L,
            userId = "user1",
            patternKey = "selftransfer",
            merchantDisplayName = "Self Transfer",
            matchType = "MERCHANT",
            learnedCategory = "TRANSFER",
            isAutoIgnored = true,
            rejectCount = 2,
            notes = "Auto-rejected based on 2 previous user rejections"
        )

        assertTrue(noiseRule.isAutoIgnored)
        assertTrue(noiseRule.rejectCount >= 2)
    }

    @Test
    fun testAxioStyleSimilarTransactionsIdentificationWhenOneOfTenIsEdited() {
        // Scenario from user:
        // Automatically identifies 10 transactions, user edits 1 transaction.
        // Immediately asks to update the other 9 similar transactions.
        val pendingList = (1..10).map { i ->
            BankTransactionRecord(
                id = i.toLong(),
                bankName = "HDFC Bank",
                merchant = if (i % 2 == 0) "SWIGGY BANGALORE" else "Swiggy",
                amount = 150.0 + (i * 20),
                category = "GENERAL",
                originalText = "Rs ${150 + i * 20} debited for VPA swiggy@hdfcbank on 05-Nov-26",
                status = "PENDING",
                isConfirmed = false
            )
        }

        val editedTx = pendingList.first() // Tx #1
        val newCategory = "OUTSIDE_FOOD"
        val newTitle = "Swiggy Food"

        val prompt = SimilarTransactionsManager.findSimilarForPendingTx(
            editedTx = editedTx,
            newCategory = newCategory,
            newTitle = newTitle,
            pendingList = pendingList,
            debitList = emptyList()
        )

        assertNotNull(prompt)
        assertEquals("swiggy", prompt!!.patternKey)
        assertEquals("OUTSIDE_FOOD", prompt.newCategory)
        assertEquals("Swiggy Food", prompt.newTitle)
        // Exactly 9 other similar transactions should be detected for bulk update!
        assertEquals(9, prompt.candidates.size)
        assertTrue(prompt.candidates.all { it.currentCategory == "GENERAL" })
    }

    @Test
    fun testAxioStyleSimilarDebitIdentification() {
        val historicalDebits = listOf(
            DebitItem(id = 1L, title = "Blinkit", category = "GENERAL", amount = 340.0),
            DebitItem(id = 2L, title = "Blinkit Commerce", category = "GENERAL", amount = 560.0),
            DebitItem(id = 3L, title = "Blinkit Quick", category = "GENERAL", amount = 120.0),
            DebitItem(id = 4L, title = "Uber Ride", category = "TRAVEL", amount = 220.0)
        )

        val oldItem = historicalDebits[0]
        val newItem = oldItem.copy(
            category = "GROCERIES",
            title = "Blinkit Groceries",
            tags = "Home,Kitchen"
        )

        val prompt = SimilarTransactionsManager.findSimilarForDebit(
            oldDebit = oldItem,
            newDebit = newItem,
            pendingList = emptyList(),
            debitList = historicalDebits
        )

        assertNotNull(prompt)
        assertEquals("blinkit", prompt!!.patternKey)
        assertEquals("GROCERIES", prompt.newCategory)
        assertEquals("Blinkit Groceries", prompt.newTitle)
        assertEquals("Home,Kitchen", prompt.newTags)
        // Only items 2 and 3 should match (item 4 is Uber)
        assertEquals(2, prompt.candidates.size)
    }

    @Test
    fun testAxioStyleDuePaymentMatchCandidateCreation() {
        // Test Candidate Model for Due Expense Match
        val candidateExpense = DuePaymentMatchCandidate(
            id = "debit_101",
            itemType = DueItemType.DUE_EXPENSE,
            title = "Airtel Fiber Broadband",
            personOrCategoryName = "RECHARGE",
            amount = 1199.0,
            dateInfo = "Due on 10 Nov",
            targetDebit = DebitItem(id = 101L, title = "Airtel Fiber Broadband", amount = 1199.0, isPaid = false)
        )

        val txDebit = BankTransactionRecord(
            id = 1001L,
            amount = 1199.0,
            type = "DEBIT",
            merchant = "Airtel Broadband",
            bankName = "HDFC Bank"
        )

        val promptExpense = DuePaymentMatchPrompt(transaction = txDebit, candidate = candidateExpense)
        assertEquals("debit_101", promptExpense.candidate.id)
        assertEquals(DueItemType.DUE_EXPENSE, promptExpense.candidate.itemType)
        assertEquals(1199.0, promptExpense.candidate.amount, 0.001)

        // Test Candidate Model for Lent Money Recovery (Credit received)
        val candidateLent = DuePaymentMatchCandidate(
            id = "loan_202",
            itemType = DueItemType.LENT_MONEY_RECOVERY,
            title = "Lent Money Recovery",
            personOrCategoryName = "Ramesh Kumar",
            amount = 5000.0,
            dateInfo = "Lent to Ramesh Kumar",
            targetLoan = LoanTransaction(id = 202L, personName = "Ramesh Kumar", totalAmount = 5000.0, type = LoanType.LENT)
        )

        val txCredit = BankTransactionRecord(
            id = 1002L,
            amount = 5000.0,
            type = "CREDIT",
            merchant = "Ramesh Kumar",
            bankName = "ICICI Bank"
        )

        val promptCredit = DuePaymentMatchPrompt(transaction = txCredit, candidate = candidateLent)
        assertEquals("loan_202", promptCredit.candidate.id)
        assertEquals(DueItemType.LENT_MONEY_RECOVERY, promptCredit.candidate.itemType)
        assertEquals("Ramesh Kumar", promptCredit.candidate.personOrCategoryName)
    }

    @Test
    fun testRecentMonthPrefillDeduplicationLogic() {
        // Recurring debits from August (older month) and September (recent month)
        val augMaid = DebitItem(id = 1L, month = 8, year = 2026, title = "Maid Salary", category = "GENERAL", amount = 3000.0, isRecurring = true)
        val sepMaid = DebitItem(id = 2L, month = 9, year = 2026, title = "Maid Salary", category = "GENERAL", amount = 3500.0, isRecurring = true)
        val sepWifi = DebitItem(id = 3L, month = 9, year = 2026, title = "Airtel Wifi", category = "INTERNET", amount = 999.0, isRecurring = true)

        val allRecurring = listOf(augMaid, sepMaid, sepWifi)
        val targetMonth = 10
        val targetYear = 2026
        val targetCode = targetYear * 100 + targetMonth

        // Find most recent month
        val recentPair = allRecurring
            .map { (it.year * 100 + it.month) to (it.year to it.month) }
            .filter { it.first < targetCode }
            .maxByOrNull { it.first }
            ?.second

        assertNotNull(recentPair)
        assertEquals(2026 to 9, recentPair)

        // Filter recurring items to only September
        val recentItems = allRecurring.filter { it.year == recentPair!!.first && it.month == recentPair.second }
        assertEquals(2, recentItems.size) // Maid Salary (₹3500) and Airtel Wifi (₹999)

        // October already has "Maid Salary" (entry already there)
        val octExisting = listOf(
            DebitItem(id = 10L, month = 10, year = 2026, title = "Maid Salary", category = "GENERAL", amount = 3500.0)
        )

        val newToInsert = mutableListOf<DebitItem>()
        for (template in recentItems) {
            val alreadyPresent = octExisting.any { item ->
                val titleMatches = item.title.trim().isNotBlank() && item.title.trim().equals(template.title.trim(), ignoreCase = true)
                val catMatches = item.category.trim().equals(template.category.trim(), ignoreCase = true)
                (titleMatches && catMatches) || (titleMatches && Math.abs(item.amount - template.amount) < 0.01)
            }
            if (!alreadyPresent) {
                newToInsert.add(template)
            }
        }

        // Only Airtel Wifi should be inserted since Maid Salary is ALREADY present
        assertEquals(1, newToInsert.size)
        assertEquals("Airtel Wifi", newToInsert.first().title)
    }
}
