package com.example

import com.example.model.DefaultDebitCategories
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
}
