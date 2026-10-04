package com.example.util

import com.example.data.model.PaymentRecord
import kotlin.math.max

data class LoanInterestBreakdown(
    val principalAmount: Double,
    val interestRatePercent: Double,
    val isMonthlyInterest: Boolean,
    val daysElapsed: Long,
    val accruedInterest: Double,
    val interestPaid: Double,
    val principalPaid: Double,
    val remainingInterestDue: Double,
    val remainingPrincipalDue: Double,
    val totalAmountWithInterest: Double,
    val totalRemainingDue: Double
)

object InterestCalculator {

    /**
     * Calculates the complete interest and principal breakdown based on:
     * - principal: original amount lent or borrowed
     * - ratePercent: interest rate (e.g. 2.0% pm or 12.0% pa)
     * - isMonthly: whether the rate is monthly (true) or per annum (false)
     * - startTimestamp: timestamp when payment was taken or given
     * - asOfTimestamp: calculation timestamp (defaults to now)
     * - payments: list of approved PaymentRecords
     */
    fun calculate(
        principal: Double,
        ratePercent: Double,
        isMonthly: Boolean,
        startTimestamp: Long,
        asOfTimestamp: Long = System.currentTimeMillis(),
        payments: List<PaymentRecord> = emptyList()
    ): LoanInterestBreakdown {
        if (principal <= 0.0) {
            return LoanInterestBreakdown(
                principalAmount = 0.0,
                interestRatePercent = ratePercent,
                isMonthlyInterest = isMonthly,
                daysElapsed = 0,
                accruedInterest = 0.0,
                interestPaid = 0.0,
                principalPaid = 0.0,
                remainingInterestDue = 0.0,
                remainingPrincipalDue = 0.0,
                totalAmountWithInterest = 0.0,
                totalRemainingDue = 0.0
            )
        }

        val millisElapsed = max(0L, asOfTimestamp - startTimestamp)
        val daysElapsed = millisElapsed / (1000L * 60 * 60 * 24)

        // 1. Accrue Interest
        val accruedInterest = if (ratePercent > 0.0) {
            if (isMonthly) {
                // Monthly interest: rate applied over 30-day month pro-rata
                principal * (ratePercent / 100.0) * (daysElapsed / 30.0)
            } else {
                // Annual interest: rate applied over 365-day year pro-rata
                principal * (ratePercent / 100.0) * (daysElapsed / 365.0)
            }
        } else {
            0.0
        }

        // 2. Track approved payments
        val approvedPayments = payments.filter { it.isApproved }
        var interestPaid = 0.0
        var principalPaid = 0.0

        for (pay in approvedPayments) {
            when (pay.paymentTarget.uppercase()) {
                "INTEREST" -> {
                    interestPaid += pay.amount
                }
                "PRINCIPAL" -> {
                    principalPaid += pay.amount
                }
                else -> {
                    // Combined: standard practice pays accrued interest first, balance to principal
                    val unallocatedToInterest = max(0.0, accruedInterest - interestPaid)
                    if (pay.amount <= unallocatedToInterest) {
                        interestPaid += pay.amount
                    } else {
                        interestPaid += unallocatedToInterest
                        principalPaid += (pay.amount - unallocatedToInterest)
                    }
                }
            }
        }

        val remainingInterestDue = max(0.0, accruedInterest - interestPaid)
        val remainingPrincipalDue = max(0.0, principal - principalPaid)
        val totalAmountWithInterest = principal + accruedInterest
        val totalRemainingDue = remainingPrincipalDue + remainingInterestDue

        return LoanInterestBreakdown(
            principalAmount = principal,
            interestRatePercent = ratePercent,
            isMonthlyInterest = isMonthly,
            daysElapsed = daysElapsed,
            accruedInterest = accruedInterest,
            interestPaid = interestPaid,
            principalPaid = principalPaid,
            remainingInterestDue = remainingInterestDue,
            remainingPrincipalDue = remainingPrincipalDue,
            totalAmountWithInterest = totalAmountWithInterest,
            totalRemainingDue = totalRemainingDue
        )
    }
}
