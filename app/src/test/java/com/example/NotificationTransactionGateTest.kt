package com.example

import com.example.data.bank.BankTxType
import com.example.service.NotificationTransactionGate
import com.example.service.TransactionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationTransactionGateTest {

    @Test
    fun `balance only alert updates silently without approval`() {
        val sms = "Dear Customer, Balance in A/C XX1234 is Rs. 12,450.75 as on 09-OCT-26."
        val parsed = TransactionParser.parse(sms, "AD-HDFCBK")
        val decision = NotificationTransactionGate.evaluate("HDFC Bank", sms, parsed, parsed.toParsedBankTransaction())

        assertEquals(NotificationTransactionGate.Action.BALANCE_UPDATE_ONLY, decision.action)
        assertEquals(12450.75, decision.balanceToApply!!, 0.01)
        assertNull(parsed.toParsedBankTransaction())
    }

    @Test
    fun `promotional cashback offer is ignored`() {
        val sms = "Get 5% cashback on your next UPI payment! Recharge now and save."
        val parsed = TransactionParser.parse(sms, "PHONEPE")
        val decision = NotificationTransactionGate.evaluate("PhonePe", sms, parsed, parsed.toParsedBankTransaction())

        assertEquals(NotificationTransactionGate.Action.IGNORE, decision.action)
    }

    @Test
    fun `real debit still requires approval`() {
        val sms = "Dear Customer, INR 450.00 debited from A/C **1234 to SWIGGY on 09-OCT-26. Avl Bal: INR 35,420.50."
        val parsed = TransactionParser.parse(sms, "AD-HDFCBK")
        val tx = parsed.toParsedBankTransaction()
        val decision = NotificationTransactionGate.evaluate("HDFC Bank", sms, parsed, tx)

        assertEquals(NotificationTransactionGate.Action.REQUEST_USER_APPROVAL, decision.action)
        assertEquals(450.0, tx!!.amount, 0.01)
        assertEquals(BankTxType.DEBIT, tx.type)
        assertEquals(35420.50, tx.availableBalance!!, 0.01)
    }

    @Test
    fun `upi collect request is ignored`() {
        val sms = "Rahul has requested Rs. 500 on PhonePe. Tap to pay."
        val parsed = TransactionParser.parse(sms, "PhonePe")
        val decision = NotificationTransactionGate.evaluate("PhonePe", sms, parsed, parsed.toParsedBankTransaction())

        assertEquals(NotificationTransactionGate.Action.IGNORE, decision.action)
    }

    @Test
    fun `credit settlement requires approval`() {
        val sms = "INR 2,500.00 credited to A/C **5678 by NEFT from ACME CORP. UTR HDFCN12345678901"
        val parsed = TransactionParser.parse(sms, "VK-HDFCBK")
        val tx = parsed.toParsedBankTransaction()
        val decision = NotificationTransactionGate.evaluate("HDFC Bank", sms, parsed, tx)

        assertEquals(NotificationTransactionGate.Action.REQUEST_USER_APPROVAL, decision.action)
        assertEquals(BankTxType.CREDIT, tx!!.type)
    }
}
