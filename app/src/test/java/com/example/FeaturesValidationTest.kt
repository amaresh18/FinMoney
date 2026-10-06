package com.example

import com.example.data.model.*
import com.example.data.repository.FinMoneyRepository
import com.example.data.sms.DetectedCategory
import com.example.data.sms.SmartSmsParser
import com.example.data.sms.SmsTransactionType
import com.example.ui.FinMoneyViewModel
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
class FeaturesValidationTest {

    // ==========================================
    // 1. SALARY & INCOME CALCULATIONS
    // ==========================================

    @Test
    fun testIncomeEncodingAndDecoding_positive() {
        val sources = listOf(
            IncomeSource("Freelance Project \"Alpha\"", 25000.0, IncomeCategory.FREELANCE.name),
            IncomeSource("Apartment Rental", 18000.0, IncomeCategory.RENT.name),
            IncomeSource("Stock Dividends", 3500.0, IncomeCategory.INTEREST.name)
        )

        val encoded = FinMoneyViewModel.encodeIncomeSources(sources)
        assertTrue(encoded.startsWith("["))
        assertTrue(encoded.contains("Freelance Project \\\"Alpha\\\""))

        val decoded = FinMoneyViewModel.parseIncomeSources(encoded)
        assertEquals(3, decoded.size)
        assertEquals("Freelance Project \"Alpha\"", decoded[0].name)
        assertEquals(25000.0, decoded[0].amount, 0.01)
        assertEquals(IncomeCategory.FREELANCE.name, decoded[0].category)
        assertEquals("Apartment Rental", decoded[1].name)
        assertEquals(18000.0, decoded[1].amount, 0.01)
    }

    @Test
    fun testIncomeParsingFallback_negative() {
        val emptySources = FinMoneyViewModel.parseIncomeSources("")
        assertTrue(emptySources.isEmpty())

        val invalidSources = FinMoneyViewModel.parseIncomeSources("random non-json text")
        assertTrue(invalidSources.isEmpty())

        val zeroAmountSources = FinMoneyViewModel.parseIncomeSources("""[{"name":"Zero Pay","amount":0.0}]""")
        assertTrue(zeroAmountSources.isEmpty())
    }

    @Test
    fun testDaysInMonthCalculation() {
        // Leap year 2024, 2028
        assertEquals(29, FinMoneyViewModel.getDaysInMonth(2, 2024))
        assertEquals(29, FinMoneyViewModel.getDaysInMonth(2, 2028))

        // Non leap years
        assertEquals(28, FinMoneyViewModel.getDaysInMonth(2, 2025))
        assertEquals(28, FinMoneyViewModel.getDaysInMonth(2, 2026))

        // 30 day months
        assertEquals(30, FinMoneyViewModel.getDaysInMonth(4, 2026))
        assertEquals(30, FinMoneyViewModel.getDaysInMonth(6, 2026))
        assertEquals(30, FinMoneyViewModel.getDaysInMonth(9, 2026))
        assertEquals(30, FinMoneyViewModel.getDaysInMonth(11, 2026))

        // 31 day months
        assertEquals(31, FinMoneyViewModel.getDaysInMonth(1, 2026))
        assertEquals(31, FinMoneyViewModel.getDaysInMonth(3, 2026))
        assertEquals(31, FinMoneyViewModel.getDaysInMonth(12, 2026))
    }

    // ==========================================
    // 2. PHONE NORMALIZATION & MATCHING
    // ==========================================

    @Test
    fun testPhoneNormalization_positiveAndNegative() {
        assertEquals("9876543210", FinMoneyRepository.normalizePhone("+91 9876543210"))
        assertEquals("9876543210", FinMoneyRepository.normalizePhone("+91-98765-43210"))
        assertEquals("9876543210", FinMoneyRepository.normalizePhone("09876543210"))
        assertEquals("9876543210", FinMoneyRepository.normalizePhone("9876543210"))
        assertEquals("9876543210", FinMoneyRepository.normalizePhone("+91 (987) 654-3210"))

        // Negatives / Edge cases
        assertEquals("", FinMoneyRepository.normalizePhone(""))
        assertEquals("", FinMoneyRepository.normalizePhone(null))
        assertEquals("123", FinMoneyRepository.normalizePhone("123"))
    }

    // ==========================================
    // 3. INTEREST & LOAN CALCULATIONS
    // ==========================================

    @Test
    fun testInterestBreakdown_monthlyRate() {
        val principal = 20000.0
        val ratePercent = 1.5 // 1.5% per month
        val startTimestamp = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000L) // 30 days ago

        val breakdown = InterestCalculator.calculate(
            principal = principal,
            ratePercent = ratePercent,
            isMonthly = true,
            startTimestamp = startTimestamp
        )

        // 1 month of 1.5% on 20000 = 300
        assertEquals(20000.0, breakdown.principalAmount, 0.01)
        assertEquals(300.0, breakdown.accruedInterest, 10.0)
        assertEquals(20300.0, breakdown.totalAmountWithInterest, 10.0)
        assertEquals(20300.0, breakdown.totalRemainingDue, 10.0)
    }

    @Test
    fun testInterestBreakdown_annualRate() {
        val principal = 50000.0
        val ratePercent = 12.0 // 12% per annum
        val startTimestamp = System.currentTimeMillis() - (365L * 24 * 60 * 60 * 1000L) // 365 days ago

        val breakdown = InterestCalculator.calculate(
            principal = principal,
            ratePercent = ratePercent,
            isMonthly = false,
            startTimestamp = startTimestamp
        )

        // 1 year of 12% on 50000 = 6000
        assertEquals(50000.0, breakdown.principalAmount, 0.01)
        assertEquals(6000.0, breakdown.accruedInterest, 50.0)
        assertEquals(56000.0, breakdown.totalRemainingDue, 50.0)
    }

    @Test
    fun testInterestBreakdown_negativeValues() {
        val breakdown = InterestCalculator.calculate(
            principal = 0.0,
            ratePercent = 5.0,
            isMonthly = true,
            startTimestamp = System.currentTimeMillis()
        )
        assertEquals(0.0, breakdown.principalAmount, 0.0)
        assertEquals(0.0, breakdown.totalRemainingDue, 0.0)
    }

    // ==========================================
    // 4. SMART SMS PARSER
    // ==========================================

    @Test
    fun testParseBankDebitSms_positive() {
        val sms = "Rs. 2,450.00 debited from A/c XX4892 on 04-Oct-26 towards Swiggy UPI Ref 8291038"
        val parsed = SmartSmsParser.parseMessage(sms, "HDFCBK")

        assertNotNull(parsed)
        assertEquals(SmsTransactionType.DEBIT, parsed!!.type)
        assertEquals(2450.0, parsed.amount, 0.01)
        assertEquals("Swiggy", parsed.merchantOrParty)
        assertEquals(DetectedCategory.SHOPPING, parsed.detectedCategory)
    }

    @Test
    fun testParseSalaryCreditSms_positive() {
        val sms = "Your A/c 9821 is credited with Rs 85,000.00 on 01-Oct-26 by Salary NEFT TechCorp"
        val parsed = SmartSmsParser.parseMessage(sms, "SBIINB")

        assertNotNull(parsed)
        assertEquals(SmsTransactionType.CREDIT, parsed!!.type)
        assertEquals(85000.0, parsed.amount, 0.01)
        assertEquals(DetectedCategory.SALARY_CREDIT, parsed.detectedCategory)
    }

    @Test
    fun testParseElectricityDebitSms_positive() {
        val sms = "Debited INR 1,850.00 from A/c 3211 for BESCOM Electricity bill payment"
        val parsed = SmartSmsParser.parseMessage(sms, "ICICIB")

        assertNotNull(parsed)
        assertEquals(SmsTransactionType.DEBIT, parsed!!.type)
        assertEquals(1850.0, parsed.amount, 0.01)
        assertEquals(DetectedCategory.ELECTRICITY, parsed.detectedCategory)
    }

    @Test
    fun testParsePeerTransferSms_positive() {
        val sms = "Sent Rs. 5000 to Amit Sharma on 9876543210 via PhonePe UPI Ref 928374"
        val parsed = SmartSmsParser.parseMessage(sms, "AXISBK")

        assertNotNull(parsed)
        assertEquals(SmsTransactionType.DEBIT, parsed!!.type)
        assertEquals(5000.0, parsed.amount, 0.01)
        assertEquals("9876543210", parsed.phoneNumber)
        assertEquals(DetectedCategory.PEER_TRANSFER, parsed.detectedCategory)
    }

    @Test
    fun testParseOtpAndSpam_negative() {
        // OTP should be ignored
        val otpSms = "Your OTP for login to HDFC NetBanking is 592810. Do not share with anyone."
        val parsedOtp = SmartSmsParser.parseMessage(otpSms, "HDFCBK")
        assertNull(parsedOtp)

        // Non-transaction promotional message should be ignored
        val promoSms = "Get pre-approved personal loan of Rs 500000 at lowest interest rates. Apply now."
        val parsedPromo = SmartSmsParser.parseMessage(promoSms, "LOANBK")
        assertNull(parsedPromo)
    }

    // ==========================================
    // 5. PAYMENT HISTORY JSON ENCODE / DECODE
    // ==========================================

    @Test
    fun testPaymentHistoryEncoding_positive() {
        val payments = listOf(
            PaymentRecord(
                id = "p1",
                amount = 3000.0,
                note = "First installment",
                recordedBy = "Amaresh",
                isApproved = true,
                paymentTarget = "PRINCIPAL"
            ),
            PaymentRecord(
                id = "p2",
                amount = 500.0,
                note = "Interest installment",
                recordedBy = "Counterparty",
                isApproved = false,
                paymentTarget = "INTEREST"
            )
        )

        val json = FinMoneyRepository.encodePaymentHistory(payments)
        assertTrue(json.startsWith("["))

        val parsed = FinMoneyRepository.parsePaymentHistory(json)
        assertEquals(2, parsed.size)
        assertEquals("p1", parsed[0].id)
        assertEquals(3000.0, parsed[0].amount, 0.01)
        assertEquals("PRINCIPAL", parsed[0].paymentTarget)
        assertEquals("p2", parsed[1].id)
        assertEquals(500.0, parsed[1].amount, 0.01)
        assertFalse(parsed[1].isApproved)
    }

    // ==========================================
    // 6. SETTLEMENT & MUTUAL APPROVAL SCENARIOS
    // ==========================================

    @Test
    fun testUnapprovedPaymentsDoNotReduceBalance() {
        val principal = 10000.0
        val unapprovedPayment = PaymentRecord(
            id = "unapproved_1",
            amount = 4000.0,
            note = "Logged payment awaiting approval",
            recordedBy = "Borrower",
            isApproved = false // Pending counterparty approval
        )

        val breakdown = InterestCalculator.calculate(
            principal = principal,
            ratePercent = 0.0,
            isMonthly = true,
            startTimestamp = System.currentTimeMillis(),
            payments = listOf(unapprovedPayment)
        )

        // Balance should still be 10000 because payment is NOT approved yet
        assertEquals(0.0, breakdown.principalPaid, 0.01)
        assertEquals(10000.0, breakdown.totalRemainingDue, 0.01)

        // Now test once approved
        val approvedPayment = unapprovedPayment.copy(isApproved = true, approvedBy = "Lender")
        val breakdownApproved = InterestCalculator.calculate(
            principal = principal,
            ratePercent = 0.0,
            isMonthly = true,
            startTimestamp = System.currentTimeMillis(),
            payments = listOf(approvedPayment)
        )
        assertEquals(4000.0, breakdownApproved.principalPaid, 0.01)
        assertEquals(6000.0, breakdownApproved.totalRemainingDue, 0.01)
    }

    @Test
    fun testSettlementRequesterRoleIdentification_positiveAndNegative() {
        val loan = LoanTransaction(
            id = 101,
            type = LoanType.GIVEN.name,
            totalAmount = 5000.0,
            counterpartyName = "Rahul Sharma",
            counterpartyContact = "9876543210",
            creatorContact = "9123456780",
            createdBy = "Amaresh",
            status = ApprovalStatus.PENDING_SETTLEMENT.name,
            settlementRequestedBy = "Amaresh"
        )

        // Scenario 1: Current user is Amaresh (Lender who requested settlement)
        val userAmaresh = UserProfile(name = "Amaresh", phoneNumber = "9123456780")
        val currentPhoneA = userAmaresh.phoneNumber.trim()
        val currentNameA = userAmaresh.name.trim()
        val isCreatorA = (loan.creatorContact.isNotBlank() && currentPhoneA.isNotBlank() && currentPhoneA.takeLast(10) == loan.creatorContact.takeLast(10)) ||
                         (loan.creatorContact.isBlank() && loan.createdBy.equals(currentNameA, ignoreCase = true))

        val isSettlementRequesterA = if (loan.settlementRequestedBy.isBlank()) false else {
            loan.settlementRequestedBy.equals(currentNameA, ignoreCase = true) ||
            (isCreatorA && (loan.settlementRequestedBy == loan.createdBy || loan.settlementRequestedBy == "You"))
        }

        // Amaresh is the requester -> should NOT see approve/decline buttons
        assertTrue(isSettlementRequesterA)

        // Scenario 2: Current user is Rahul Sharma (Borrower receiving the settlement request)
        val userRahul = UserProfile(name = "Rahul Sharma", phoneNumber = "9876543210")
        val currentPhoneR = userRahul.phoneNumber.trim()
        val currentNameR = userRahul.name.trim()
        val isCreatorR = (loan.creatorContact.isNotBlank() && currentPhoneR.isNotBlank() && currentPhoneR.takeLast(10) == loan.creatorContact.takeLast(10))

        val isSettlementRequesterR = if (loan.settlementRequestedBy.isBlank()) false else {
            loan.settlementRequestedBy.equals(currentNameR, ignoreCase = true) ||
            (isCreatorR && (loan.settlementRequestedBy == loan.createdBy || loan.settlementRequestedBy == "You"))
        }

        // Rahul is the recipient -> isSettlementRequester is FALSE -> Rahul sees Approve/Decline buttons!
        assertFalse(isSettlementRequesterR)
    }

    @Test
    fun testSettlementRequesterRole_borrowerInitiated() {
        val loan = LoanTransaction(
            id = 102,
            type = LoanType.GIVEN.name,
            totalAmount = 8000.0,
            counterpartyName = "Rahul Sharma",
            counterpartyContact = "9876543210",
            creatorContact = "9123456780",
            createdBy = "Amaresh",
            status = ApprovalStatus.PENDING_SETTLEMENT.name,
            settlementRequestedBy = "Rahul Sharma"
        )

        // Scenario 1: Rahul (Borrower) initiated settlement
        val userRahul = UserProfile(name = "Rahul Sharma", phoneNumber = "9876543210")
        val currentPhoneR = userRahul.phoneNumber.trim()
        val currentNameR = userRahul.name.trim()
        val isCreatorR = (loan.creatorContact.isNotBlank() && currentPhoneR.isNotBlank() && currentPhoneR.takeLast(10) == loan.creatorContact.takeLast(10))

        val req = loan.settlementRequestedBy.trim()
        val isReqCreatorR = req.equals(loan.createdBy.trim(), ignoreCase = true) || req.equals("You", ignoreCase = true) || req.equals("CREATOR", ignoreCase = true)
        val isReqCounterpartyR = req.equals(loan.counterpartyName.trim(), ignoreCase = true) || req.equals("COUNTERPARTY", ignoreCase = true)

        val isSettlementRequesterR = if (loan.settlementRequestedBy.isBlank()) false else {
            if (isCreatorR) isReqCreatorR || req.equals(currentNameR, ignoreCase = true)
            else isReqCounterpartyR || req.equals(currentNameR, ignoreCase = true)
        }

        // Rahul is requester -> true (cannot approve self)
        assertTrue(isSettlementRequesterR)

        // Scenario 2: Amaresh (Lender) views Rahul's settlement request
        val userAmaresh = UserProfile(name = "Amaresh", phoneNumber = "9123456780")
        val currentPhoneA = userAmaresh.phoneNumber.trim()
        val currentNameA = userAmaresh.name.trim()
        val isCreatorA = (loan.creatorContact.isNotBlank() && currentPhoneA.isNotBlank() && currentPhoneA.takeLast(10) == loan.creatorContact.takeLast(10))

        val isSettlementRequesterA = if (loan.settlementRequestedBy.isBlank()) false else {
            if (isCreatorA) isReqCreatorR || req.equals(currentNameA, ignoreCase = true)
            else isReqCounterpartyR || req.equals(currentNameA, ignoreCase = true)
        }

        // Amaresh is counterparty -> false (Amaresh sees Approve / Decline buttons)
        assertFalse(isSettlementRequesterA)
    }

    @Test
    fun testPaymentRecordDeletionRecalculation() {
        val pay1 = PaymentRecord(id = "p1", amount = 3000.0, isApproved = true)
        val pay2 = PaymentRecord(id = "p2", amount = 2000.0, isApproved = true)
        val pay3 = PaymentRecord(id = "p3", amount = 1500.0, isApproved = false)

        val initialList = listOf(pay1, pay2, pay3)
        val initialApprovedTotal = initialList.filter { it.isApproved }.sumOf { it.amount }
        assertEquals(5000.0, initialApprovedTotal, 0.01)

        // Delete approved payment p1
        val afterDeleteP1 = initialList.filterNot { it.id == "p1" }
        val updatedApprovedTotal = afterDeleteP1.filter { it.isApproved }.sumOf { it.amount }
        assertEquals(2000.0, updatedApprovedTotal, 0.01)

        // Remaining due on 10,000 loan
        val remainingDue = 10000.0 - updatedApprovedTotal
        assertEquals(8000.0, remainingDue, 0.01)
    }
}
