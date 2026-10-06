package com.example

import com.example.data.model.PaymentRecord
import com.example.util.InterestCalculator
import com.example.util.ProofFile
import com.example.util.ProofStorageHelper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InterestAndLentBorrowTest {

    @Test
    fun testInterestAccrual_positive() {
        val principal = 10000.0
        val ratePercent = 2.0 // 2% per month
        val isMonthly = true
        val startTimestamp = System.currentTimeMillis() - (60L * 24 * 60 * 60 * 1000L) // 60 days ago

        val breakdown = InterestCalculator.calculate(
            principal = principal,
            ratePercent = ratePercent,
            isMonthly = isMonthly,
            startTimestamp = startTimestamp
        )

        // 60 days = 2 months. 2% pm on 10,000 = 200/mo * 2 = 400
        assertEquals(10000.0, breakdown.principalAmount, 0.01)
        assertEquals(400.0, breakdown.accruedInterest, 10.0)
        assertEquals(10400.0, breakdown.totalAmountWithInterest, 10.0)
        assertEquals(10400.0, breakdown.totalRemainingDue, 10.0)
    }

    @Test
    fun testClearInterestFirst_positive() {
        val principal = 10000.0
        val ratePercent = 2.0
        val startTimestamp = System.currentTimeMillis() - (60L * 24 * 60 * 60 * 1000L) // 60 days ago: accrued ~400

        val interestPayment = PaymentRecord(
            amount = 400.0,
            note = "Cleared 2 months interest",
            paymentTarget = "INTEREST",
            isApproved = true
        )

        val breakdown = InterestCalculator.calculate(
            principal = principal,
            ratePercent = ratePercent,
            isMonthly = true,
            startTimestamp = startTimestamp,
            payments = listOf(interestPayment)
        )

        assertEquals(400.0, breakdown.interestPaid, 1.0)
        assertEquals(0.0, breakdown.remainingInterestDue, 5.0)
        assertEquals(10000.0, breakdown.remainingPrincipalDue, 0.01)
        assertEquals(10000.0, breakdown.totalRemainingDue, 5.0)
    }

    @Test
    fun testClearPrincipalFirst_positive() {
        val principal = 10000.0
        val ratePercent = 2.0
        val startTimestamp = System.currentTimeMillis() - (60L * 24 * 60 * 60 * 1000L)

        val principalPayment = PaymentRecord(
            amount = 5000.0,
            note = "Half principal repayment",
            paymentTarget = "PRINCIPAL",
            isApproved = true
        )

        val breakdown = InterestCalculator.calculate(
            principal = principal,
            ratePercent = ratePercent,
            isMonthly = true,
            startTimestamp = startTimestamp,
            payments = listOf(principalPayment)
        )

        assertEquals(5000.0, breakdown.principalPaid, 0.01)
        assertEquals(5000.0, breakdown.remainingPrincipalDue, 0.01)
        assertTrue(breakdown.remainingInterestDue > 350.0)
    }

    @Test
    fun testZeroInterest_negative() {
        val breakdown = InterestCalculator.calculate(
            principal = 5000.0,
            ratePercent = 0.0,
            isMonthly = false,
            startTimestamp = System.currentTimeMillis() - 1000000L
        )
        assertEquals(0.0, breakdown.accruedInterest, 0.001)
        assertEquals(5000.0, breakdown.totalRemainingDue, 0.001)
    }

    @Test
    fun testMultiProofEncodingAndDecoding_positive() {
        val file1 = ProofFile(
            id = "f1",
            name = "receipt.jpg",
            mimeType = "image/jpeg",
            localPath = "/data/proofs/1.jpg",
            cloudBase64 = "data:image/jpeg;base64,QUJDRA==",
            sizeBytes = 1024L
        )
        val file2 = ProofFile(
            id = "f2",
            name = "agreement.pdf",
            mimeType = "application/pdf",
            localPath = "/data/proofs/2.pdf",
            cloudBase64 = "data:application/pdf;base64,UERGREFUQQ==",
            sizeBytes = 2048L
        )

        val json = ProofStorageHelper.encodeProofFiles(listOf(file1, file2))
        assertTrue(json.startsWith("["))

        val parsed = ProofStorageHelper.parseProofFiles(json)
        assertEquals(2, parsed.size)
        assertEquals("receipt.jpg", parsed[0].name)
        assertFalse(parsed[0].isPdf)
        assertEquals("agreement.pdf", parsed[1].name)
        assertTrue(parsed[1].isPdf)
    }

    @Test
    fun testProofParsingFallback_negative() {
        // Plain string fallback
        val parsedSingle = ProofStorageHelper.parseProofFiles("/storage/emulated/0/DCIM/pic.jpg")
        assertEquals(1, parsedSingle.size)
        assertEquals("/storage/emulated/0/DCIM/pic.jpg", parsedSingle[0].localPath)

        // Empty string fallback
        val parsedEmpty = ProofStorageHelper.parseProofFiles("")
        assertTrue(parsedEmpty.isEmpty())

        // Corrupted JSON fallback
        val parsedCorrupted = ProofStorageHelper.parseProofFiles("[invalid json")
        assertEquals(1, parsedCorrupted.size)
    }
}
