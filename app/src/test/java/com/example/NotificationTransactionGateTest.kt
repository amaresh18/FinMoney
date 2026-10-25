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

    @Test
    fun `upi expense from google pay requires approval and identifies as debit`() {
        val notif = "Paid ₹150 to Chai Point"
        val parsed = TransactionParser.parse(notif, "Google Pay")
        val tx = parsed.toParsedBankTransaction()
        val decision = NotificationTransactionGate.evaluate("Google Pay", notif, parsed, tx)

        assertEquals(NotificationTransactionGate.Action.REQUEST_USER_APPROVAL, decision.action)
        assertEquals(150.0, tx!!.amount, 0.01)
        assertEquals(BankTxType.DEBIT, tx.type)
    }

    @Test
    fun `upi income from phonepe requires approval and identifies as credit`() {
        val notif = "Received ₹500 from Amit"
        val parsed = TransactionParser.parse(notif, "PhonePe")
        val tx = parsed.toParsedBankTransaction()
        val decision = NotificationTransactionGate.evaluate("PhonePe", notif, parsed, tx)

        assertEquals(NotificationTransactionGate.Action.REQUEST_USER_APPROVAL, decision.action)
        assertEquals(500.0, tx!!.amount, 0.01)
        assertEquals(BankTxType.CREDIT, tx.type)
    }

    @Test
    fun `salary credit requires approval and identifies as credit`() {
        val sms = "Dear Customer, your Account **1234 credited with INR 65,000.00 on 01-OCT-26 by SALARY. Avl Bal: INR 95,000.00."
        val parsed = TransactionParser.parse(sms, "AD-HDFCBK")
        val tx = parsed.toParsedBankTransaction()
        val decision = NotificationTransactionGate.evaluate("HDFC Bank", sms, parsed, tx)

        assertEquals(NotificationTransactionGate.Action.REQUEST_USER_APPROVAL, decision.action)
        assertEquals(65000.0, tx!!.amount, 0.01)
        assertEquals(BankTxType.CREDIT, tx.type)
        assertEquals(95000.0, tx.availableBalance!!, 0.01)
    }

    @Test
    fun `refund settlement requires approval and identifies as credit`() {
        val notif = "Refund of ₹250 for canceled order credited to your account"
        val parsed = TransactionParser.parse(notif, "Swiggy")
        val tx = parsed.toParsedBankTransaction()
        val decision = NotificationTransactionGate.evaluate("Swiggy", notif, parsed, tx)

        assertEquals(NotificationTransactionGate.Action.REQUEST_USER_APPROVAL, decision.action)
        assertEquals(250.0, tx!!.amount, 0.01)
        assertEquals(BankTxType.CREDIT, tx.type)
    }

    @Test
    fun `sbi debit alert requires approval and identifies as debit`() {
        val sms = "Your A/C XX5678 debited by Rs.820.00 on 09-OCT-26 transfer to ZOMATO. Avail Bal: Rs.14,230.75. UPI Ref 428192837123."
        val parsed = TransactionParser.parse(sms, "VK-SBIINB")
        val tx = parsed.toParsedBankTransaction()
        val decision = NotificationTransactionGate.evaluate("SBI", sms, parsed, tx)

        assertEquals(NotificationTransactionGate.Action.REQUEST_USER_APPROVAL, decision.action)
        assertEquals(820.0, tx!!.amount, 0.01)
        assertEquals(BankTxType.DEBIT, tx.type)
        assertEquals(14230.75, tx.availableBalance!!, 0.01)
    }

    @Test
    fun `loan promotion advertisement is ignored`() {
        val sms = "Congratulations! You have pre-approved personal loan of Rs 5,00,000 at low interest. Apply now in 2 mins."
        val parsed = TransactionParser.parse(sms, "HDFCBK")
        val decision = NotificationTransactionGate.evaluate("HDFC Bank", sms, parsed, parsed.toParsedBankTransaction())

        assertEquals(NotificationTransactionGate.Action.IGNORE, decision.action)
    }

    @Test
    fun `upcoming electricity bill due notice is ignored`() {
        val sms = "Dear Customer, your electricity bill of Rs 1,450.00 is generated. Due date is 30-OCT-26. Pay before due date to avoid late fee."
        val parsed = TransactionParser.parse(sms, "BESCOM")
        val decision = NotificationTransactionGate.evaluate("BESCOM", sms, parsed, parsed.toParsedBankTransaction())

        assertEquals(NotificationTransactionGate.Action.IGNORE, decision.action)
    }

    @Test
    fun `recharge prompt alert is ignored`() {
        val notif = "Daily data 100% exhausted. Recharge with ₹299 now for 2GB/day and unlimited calls."
        val parsed = TransactionParser.parse(notif, "Jio")
        val decision = NotificationTransactionGate.evaluate("Jio", notif, parsed, parsed.toParsedBankTransaction())

        assertEquals(NotificationTransactionGate.Action.IGNORE, decision.action)
    }

    @Test
    fun `scratch card and reward promo is ignored`() {
        val notif = "You won a scratch card! Win up to ₹1,000 cashback on Google Pay today. Tap to claim."
        val parsed = TransactionParser.parse(notif, "Google Pay")
        val decision = NotificationTransactionGate.evaluate("Google Pay", notif, parsed, parsed.toParsedBankTransaction())

        assertEquals(NotificationTransactionGate.Action.IGNORE, decision.action)
    }

    @Test
    fun `discount coupon code offer is ignored`() {
        val notif = "Flat 50% off on Swiggy! Use code SWIGGY50 to save up to ₹150 on your order."
        val parsed = TransactionParser.parse(notif, "Swiggy")
        val decision = NotificationTransactionGate.evaluate("Swiggy", notif, parsed, parsed.toParsedBankTransaction())

        assertEquals(NotificationTransactionGate.Action.IGNORE, decision.action)
    }

    @Test
    fun `credit limit enhancement marketing is ignored`() {
        val sms = "Dear Customer, good news! You are eligible for credit limit increase up to Rs. 2,00,000 on your card ending 1234. Avail offer now."
        val parsed = TransactionParser.parse(sms, "ICICIB")
        val decision = NotificationTransactionGate.evaluate("ICICI Bank", sms, parsed, parsed.toParsedBankTransaction())

        assertEquals(NotificationTransactionGate.Action.IGNORE, decision.action)
    }

    @Test
    fun `referral invite loop is ignored`() {
        val notif = "Refer your friend and earn ₹100 directly in your bank account! Invite now."
        val parsed = TransactionParser.parse(notif, "PhonePe")
        val decision = NotificationTransactionGate.evaluate("PhonePe", notif, parsed, parsed.toParsedBankTransaction())

        assertEquals(NotificationTransactionGate.Action.IGNORE, decision.action)
    }
}
