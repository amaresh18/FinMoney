package com.example

import com.example.data.bank.BankTxType
import com.example.data.bank.ParsedBankTransaction
import com.example.data.model.BankTransactionRecord
import com.example.data.model.DebitItem
import com.example.service.BankTransactionDeduplicator
import com.example.service.DuplicateConfidence
import org.junit.Assert.*
import org.junit.Test

class BankTransactionDeduplicatorTest {

    private val baseTimestamp = 1775820000000L // arbitrary fixed timestamp

    @Test
    fun `test exact SHA-256 fingerprint match detects duplicate`() {
        val fingerprint = BankTransactionDeduplicator.computeFingerprint(
            bankName = "Axis Bank",
            amount = 999.0,
            transactionType = "DEBIT",
            merchant = "Swiggy",
            accountLast4 = "5555",
            referenceNumber = "AXIS9876543210"
        )

        val existing = listOf(
            BankTransactionRecord(
                id = 10L,
                bankName = "Axis Bank",
                accountNumberLast4 = "5555",
                amount = 999.0,
                type = "DEBIT",
                merchant = "Swiggy",
                referenceNumber = "AXIS9876543210",
                fingerprint = fingerprint,
                timestamp = baseTimestamp
            )
        )

        val incoming = ParsedBankTransaction(
            bankName = "Axis Bank",
            accountNumberLast4 = "5555",
            amount = 999.0,
            type = BankTxType.DEBIT,
            merchantOrParty = "Swiggy",
            referenceNumber = "AXIS9876543210",
            timestamp = baseTimestamp + 10_000L
        )

        val result = BankTransactionDeduplicator.evaluate(incoming, existing)
        assertTrue("Exact SHA-256 fingerprint match must be flagged as duplicate", result.isDuplicate)
        assertEquals(DuplicateConfidence.EXACT_FINGERPRINT_MATCH, result.confidence)
        assertEquals(10L, result.matchedTxId)
    }

    @Test
    fun `test exact UTR reference match detects duplicate with 100 percent confidence`() {
        val existing = listOf(
            BankTransactionRecord(
                id = 1L,
                bankName = "HDFC Bank",
                amount = 450.0,
                type = "DEBIT",
                referenceNumber = "UPI428192837192",
                timestamp = baseTimestamp
            )
        )

        val incoming = ParsedBankTransaction(
            bankName = "HDFC Bank",
            amount = 450.0,
            type = BankTxType.DEBIT,
            referenceNumber = "UPI428192837192",
            timestamp = baseTimestamp + 120_000L // 2 minutes later
        )

        val result = BankTransactionDeduplicator.evaluate(incoming, existing)
        assertTrue("Identical UTR must be flagged as duplicate", result.isDuplicate)
        assertEquals(DuplicateConfidence.EXACT_REF_MATCH, result.confidence)
        assertEquals(1L, result.matchedTxId)
    }

    @Test
    fun `test different UTR reference numbers are NOT duplicates even with same amount and bank`() {
        val existing = listOf(
            BankTransactionRecord(
                id = 1L,
                bankName = "HDFC Bank",
                amount = 50.0,
                type = "DEBIT",
                referenceNumber = "REF111111111111",
                timestamp = baseTimestamp
            )
        )

        val incoming = ParsedBankTransaction(
            bankName = "HDFC Bank",
            amount = 50.0,
            type = BankTxType.DEBIT,
            referenceNumber = "REF222222222222",
            timestamp = baseTimestamp + 600_000L // 10 minutes later with different UTR
        )

        val result = BankTransactionDeduplicator.evaluate(incoming, existing)
        assertFalse("Different reference numbers indicate distinct transactions", result.isDuplicate)
        assertEquals(DuplicateConfidence.UNIQUE, result.confidence)
    }

    @Test
    fun `test rapid broadcast dual-alert without UTR within 3 minutes is detected as duplicate`() {
        val existing = listOf(
            BankTransactionRecord(
                id = 2L,
                bankName = "SBI",
                accountNumberLast4 = "1234",
                amount = 1200.0,
                type = "DEBIT",
                timestamp = baseTimestamp
            )
        )

        val incoming = ParsedBankTransaction(
            bankName = "SBI",
            accountNumberLast4 = "1234",
            amount = 1200.0,
            type = BankTxType.DEBIT,
            timestamp = baseTimestamp + 15_000L // 15 seconds later
        )

        val result = BankTransactionDeduplicator.evaluate(incoming, existing)
        assertTrue("Rapid broadcast within 3m must be flagged as duplicate", result.isDuplicate)
        assertEquals(DuplicateConfidence.RAPID_BROADCAST_MATCH, result.confidence)
    }

    @Test
    fun `test sms and upi app alerts for one payment are duplicates even with different refs`() {
        val existing = listOf(
            BankTransactionRecord(
                id = 8L,
                bankName = "HDFC Bank",
                accountNumberLast4 = "1234",
                amount = 250.0,
                type = "DEBIT",
                merchant = "Swiggy",
                referenceNumber = "UTR111111111111",
                timestamp = baseTimestamp
            )
        )
        val incoming = ParsedBankTransaction(
            bankName = "Google Pay",
            accountNumberLast4 = "1234",
            amount = 250.0,
            type = BankTxType.DEBIT,
            merchantOrParty = "Swiggy",
            referenceNumber = "GPAY999888777",
            timestamp = baseTimestamp + 20_000L
        )
        val result = BankTransactionDeduplicator.evaluate(incoming, existing)
        assertTrue(result.isDuplicate)
        assertEquals(DuplicateConfidence.RAPID_BROADCAST_MATCH, result.confidence)
    }

    @Test
    fun `test debit and credit of same amount are NOT duplicates`() {
        val existing = listOf(
            BankTransactionRecord(
                id = 3L,
                bankName = "ICICI Bank",
                amount = 500.0,
                type = "DEBIT",
                timestamp = baseTimestamp
            )
        )

        val incoming = ParsedBankTransaction(
            bankName = "ICICI Bank",
            amount = 500.0,
            type = BankTxType.CREDIT, // Refund
            timestamp = baseTimestamp + 30_000L
        )

        val result = BankTransactionDeduplicator.evaluate(incoming, existing)
        assertFalse("Debit and Credit of same amount must never match as duplicate", result.isDuplicate)
    }

    @Test
    fun `test different bank accounts at same bank are NOT duplicates`() {
        val existing = listOf(
            BankTransactionRecord(
                id = 4L,
                bankName = "Kotak Bank",
                accountNumberLast4 = "1111",
                amount = 1000.0,
                type = "DEBIT",
                timestamp = baseTimestamp
            )
        )

        val incoming = ParsedBankTransaction(
            bankName = "Kotak Bank",
            accountNumberLast4 = "2222",
            amount = 1000.0,
            type = BankTxType.DEBIT,
            timestamp = baseTimestamp + 20_000L
        )

        val result = BankTransactionDeduplicator.evaluate(incoming, existing)
        assertFalse("Different account endings are distinct accounts", result.isDuplicate)
    }

    @Test
    fun `test already recorded confirmed debit is detected and discarded`() {
        val debits = listOf(
            DebitItem(
                id = 99L,
                title = "Starbucks Coffee",
                amount = 350.0,
                notes = "Approved from HDFC alert. Ref: UTR7788990011",
                createdAt = baseTimestamp
            )
        )

        val incoming = ParsedBankTransaction(
            bankName = "HDFC Bank",
            amount = 350.0,
            type = BankTxType.DEBIT,
            referenceNumber = "UTR7788990011",
            merchantOrParty = "Starbucks",
            timestamp = baseTimestamp + 50_000L
        )

        val result = BankTransactionDeduplicator.evaluate(incoming, emptyList(), debits)
        assertTrue("Already recorded confirmed debit must be detected", result.isDuplicate)
        assertEquals(DuplicateConfidence.ALREADY_RECORDED_DEBIT, result.confidence)
        assertEquals(99L, result.matchedTxId)
    }

    @Test
    fun `test previously rejected transaction is discarded to prevent re-alerting`() {
        val existing = listOf(
            BankTransactionRecord(
                id = 55L,
                bankName = "HDFC Bank",
                amount = 2500.0,
                type = "DEBIT",
                referenceNumber = "REJ1234567890",
                status = "REJECTED",
                isConfirmed = true,
                timestamp = baseTimestamp
            )
        )

        val incoming = ParsedBankTransaction(
            bankName = "HDFC Bank",
            amount = 2500.0,
            type = BankTxType.DEBIT,
            referenceNumber = "REJ1234567890",
            timestamp = baseTimestamp + 60_000L
        )

        val result = BankTransactionDeduplicator.evaluate(incoming, existing)
        assertTrue("Previously rejected transaction must be discarded", result.isDuplicate)
        assertEquals(DuplicateConfidence.REJECTED_TX_DISCARD, result.confidence)
        assertEquals(55L, result.matchedTxId)
    }
}
