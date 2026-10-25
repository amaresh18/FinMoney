package com.example

import com.example.data.model.TransactionClassificationResult
import com.example.data.model.TransactionType
import com.example.service.gemini.GeminiClassificationEngine
import com.example.service.gemini.GeminiNotificationClassificationService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeminiTransactionClassifierTest {

    private val engine = GeminiClassificationEngine()

    @Test
    fun `data model properly distinguishes credit and debit`() {
        val debitResult = TransactionClassificationResult(
            amount = 500.0,
            sender = "HDFC Bank",
            type = TransactionType.DEBIT
        )
        assertTrue(debitResult.isDebit)
        assertFalse(debitResult.isCredit)

        val creditResult = TransactionClassificationResult(
            amount = 15000.0,
            sender = "State Bank of India",
            type = TransactionType.CREDIT
        )
        assertTrue(creditResult.isCredit)
        assertFalse(creditResult.isDebit)
    }

    @Test
    fun `fallback local parse extracts debit transaction from standard bank SMS`() {
        val sms = "Dear Customer, INR 450.00 debited from A/C **1234 to SWIGGY on 09-OCT-26. Avl Bal: INR 35,420.50."
        val result = engine.fallbackLocalParse(
            rawText = sms,
            senderOrTitle = "AD-HDFCBK",
            timestamp = 1791550000000L
        )

        assertTrue(result.isFinancial)
        assertEquals(450.0, result.amount, 0.01)
        assertEquals(TransactionType.DEBIT, result.type)
        assertEquals(35420.50, result.availableBalance ?: 0.0, 0.01)
        assertEquals("1234", result.accountEnding)
        assertEquals("OUTSIDE_FOOD", result.category)
    }

    @Test
    fun `fallback local parse extracts credit transaction from salary NEFT SMS`() {
        val sms = "INR 85,000.00 credited to A/C **9876 on 01-NOV-26 by NEFT salary from ACME TECH PVT LTD."
        val result = engine.fallbackLocalParse(
            rawText = sms,
            senderOrTitle = "VK-SBIN",
            timestamp = 1791550000000L
        )

        assertTrue(result.isFinancial)
        assertEquals(85000.0, result.amount, 0.01)
        assertEquals(TransactionType.CREDIT, result.type)
        assertEquals("9876", result.accountEnding)
        assertEquals("SALARY", result.category)
    }

    @Test
    fun `fallback local parse extracts UPI payment alert from Google Pay`() {
        val notif = "Paid ₹280 to Chai Point via UPI"
        val result = engine.fallbackLocalParse(
            rawText = notif,
            senderOrTitle = "Google Pay",
            timestamp = 1791550000000L
        )

        assertTrue(result.isFinancial)
        assertEquals(280.0, result.amount, 0.01)
        assertEquals(TransactionType.DEBIT, result.type)
        assertTrue(result.merchant.contains("Chai Point", ignoreCase = true))
    }

    @Test
    fun `fallback local parse filters non-financial OTP SMS`() {
        val sms = "584920 is your secret OTP for login to HDFC NetBanking. Valid for 10 minutes. Do not share."
        val result = engine.fallbackLocalParse(
            rawText = sms,
            senderOrTitle = "HDFCBK",
            timestamp = 1791550000000L
        )

        assertFalse(result.isFinancial)
    }

    @Test
    fun `service parseAndCategorize returns valid result for incoming notification`() = runBlocking {
        val notif = "Received ₹1,200 from Rahul Sharma on PhonePe"
        val result = GeminiNotificationClassificationService.parseAndCategorize(
            text = notif,
            senderOrTitle = "PhonePe",
            timestamp = System.currentTimeMillis()
        )

        assertNotNull(result)
        assertTrue(result.isFinancial)
        assertEquals(TransactionType.CREDIT, result.type)
        assertEquals(1200.0, result.amount, 0.01)
    }

    @Test
    fun `fallback local parse filters promotional loan offer`() {
        val sms = "Pre-approved loan of Rs 3,00,000 waiting for you. Zero documentation! Apply now."
        val result = engine.fallbackLocalParse(sms, "HDFCBK")
        assertFalse(result.isFinancial)
    }

    @Test
    fun `fallback local parse filters promotional cashback reward`() {
        val notif = "Win up to ₹500 cashback on your next recharge! Flat 10% off with PhonePe."
        val result = engine.fallbackLocalParse(notif, "PhonePe")
        assertFalse(result.isFinancial)
    }

    @Test
    fun `fallback local parse filters bill due notification`() {
        val sms = "Your electricity bill of Rs 850 is generated. Due date 28-OCT. Pay before due date."
        val result = engine.fallbackLocalParse(sms, "BESCOM")
        assertFalse(result.isFinancial)
    }

    @Test
    fun `fallback local parse filters recharge prompt`() {
        val notif = "Daily data exhausted. Recharge with ₹299 now for 1.5GB/day unlimited data."
        val result = engine.fallbackLocalParse(notif, "Airtel")
        assertFalse(result.isFinancial)
    }
}
