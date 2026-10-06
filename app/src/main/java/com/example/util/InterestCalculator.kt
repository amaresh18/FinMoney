package com.example.util

import com.example.data.model.PaymentRecord

data class LoanInterestBreakdown(
    val principal: Double,
    val accruedInterest: Double,
    val totalPaid: Double,
    val totalRemainingDue: Double,
    val principalRemaining: Double = 0.0,
    val interestRemaining: Double = 0.0,
    val elapsedMonths: Double = 0.0,
    val elapsedDays: Long = 0L
) {
    val daysElapsed: Long get() = elapsedDays
    val principalAmount: Double get() = principal
    val remainingPrincipalDue: Double get() = principalRemaining
    val remainingInterestDue: Double get() = interestRemaining
    val principalPaid: Double get() = (principal - principalRemaining).coerceAtLeast(0.0)
    val interestPaid: Double get() = (accruedInterest - interestRemaining).coerceAtLeast(0.0)
    val totalAmountWithInterest: Double get() = principal + accruedInterest
}

object InterestCalculator {

    fun calculate(
        principal: Double,
        ratePercent: Double,
        isMonthly: Boolean,
        startTimestamp: Long,
        payments: List<PaymentRecord> = emptyList(),
        endTimestamp: Long = System.currentTimeMillis()
    ): LoanInterestBreakdown {
        if (principal <= 0.0) {
            return LoanInterestBreakdown(0.0, 0.0, 0.0, 0.0)
        }

        val elapsedMillis = (endTimestamp - startTimestamp).coerceAtLeast(0L)
        val elapsedDays = elapsedMillis / (1000 * 60 * 60 * 24)
        val elapsedMonths = elapsedDays / 30.0

        val accruedInterest = if (ratePercent > 0.0) {
            if (isMonthly) {
                // Monthly simple interest: Principal * (Rate% / 100) * elapsedMonths
                principal * (ratePercent / 100.0) * elapsedMonths
            } else {
                // Annual simple interest: Principal * (Rate% / 100) * (elapsedDays / 365.0)
                principal * (ratePercent / 100.0) * (elapsedDays / 365.0)
            }
        } else 0.0

        val totalPayable = principal + accruedInterest
        val approved = payments.filter { it.isApproved }
        val approvedPayments = approved.sumOf { it.amount }
        val remainingDue = (totalPayable - approvedPayments).coerceAtLeast(0.0)

        val principalTargeted = approved.filter { it.paymentTarget == "PRINCIPAL" }.sumOf { it.amount }
        val interestTargeted = approved.filter { it.paymentTarget == "INTEREST" }.sumOf { it.amount }
        val combinedTargeted = approved.filter { it.paymentTarget == "COMBINED" || it.paymentTarget.isBlank() }.sumOf { it.amount }

        var remainingPrincipal = (principal - principalTargeted).coerceAtLeast(0.0)
        var remainingInterest = (accruedInterest - interestTargeted).coerceAtLeast(0.0)

        val interestCoveredByCombined = combinedTargeted.coerceAtMost(remainingInterest)
        remainingInterest = (remainingInterest - interestCoveredByCombined).coerceAtLeast(0.0)
        val excessCombined = combinedTargeted - interestCoveredByCombined
        remainingPrincipal = (remainingPrincipal - excessCombined).coerceAtLeast(0.0)

        return LoanInterestBreakdown(
            principal = principal,
            accruedInterest = accruedInterest,
            totalPaid = approvedPayments,
            totalRemainingDue = remainingDue,
            principalRemaining = remainingPrincipal,
            interestRemaining = remainingInterest,
            elapsedMonths = elapsedMonths,
            elapsedDays = elapsedDays
        )
    }
}
