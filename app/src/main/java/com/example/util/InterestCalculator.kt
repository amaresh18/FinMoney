package com.example.util

data class PaymentRecord(
    val amount: Double = 0.0,
    val isApproved: Boolean = true,
    val paymentTarget: String = "COMBINED"
)

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

data class EmiCalculationResult(
    val principal: Double,
    val annualInterestRate: Double,
    val tenureMonths: Int,
    val startMonth: Int,
    val startYear: Int,
    val monthlyEmi: Double,
    val totalPayable: Double,
    val totalInterestPayable: Double,
    val monthsCompleted: Int,
    val monthsRemaining: Int,
    val paidSoFar: Double,
    val outstandingTotalBalance: Double,
    val outstandingPrincipal: Double,
    val outstandingInterest: Double
)

object EmiCalculator {

    fun calculateEmiAndOutstanding(
        principal: Double,
        annualInterestRatePercent: Double,
        tenureMonths: Int,
        startMonth: Int = 0,
        startYear: Int = 0,
        currentMonth: Int = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1,
        currentYear: Int = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    ): EmiCalculationResult {
        if (principal <= 0.0 || tenureMonths <= 0) {
            return EmiCalculationResult(
                principal = principal.coerceAtLeast(0.0),
                annualInterestRate = annualInterestRatePercent,
                tenureMonths = tenureMonths.coerceAtLeast(0),
                startMonth = startMonth,
                startYear = startYear,
                monthlyEmi = 0.0,
                totalPayable = 0.0,
                totalInterestPayable = 0.0,
                monthsCompleted = 0,
                monthsRemaining = tenureMonths.coerceAtLeast(0),
                paidSoFar = 0.0,
                outstandingTotalBalance = 0.0,
                outstandingPrincipal = 0.0,
                outstandingInterest = 0.0
            )
        }

        val monthlyRate = (annualInterestRatePercent / 12.0) / 100.0

        val monthlyEmi = if (monthlyRate > 0.0) {
            val powFactor = Math.pow(1.0 + monthlyRate, tenureMonths.toDouble())
            (principal * monthlyRate * powFactor) / (powFactor - 1.0)
        } else {
            principal / tenureMonths.toDouble()
        }

        val totalPayable = monthlyEmi * tenureMonths
        val totalInterestPayable = (totalPayable - principal).coerceAtLeast(0.0)

        val monthsCompleted = if (startMonth in 1..12 && startYear > 1900) {
            val elapsed = (currentYear - startYear) * 12 + (currentMonth - startMonth) + 1
            elapsed.coerceIn(0, tenureMonths)
        } else {
            0
        }

        val monthsRemaining = (tenureMonths - monthsCompleted).coerceAtLeast(0)
        val paidSoFar = (monthlyEmi * monthsCompleted).coerceAtMost(totalPayable)
        val outstandingTotalBalance = (totalPayable - paidSoFar).coerceAtLeast(0.0)

        val outstandingPrincipal = if (monthlyRate > 0.0) {
            val powN = Math.pow(1.0 + monthlyRate, tenureMonths.toDouble())
            val powM = Math.pow(1.0 + monthlyRate, monthsCompleted.toDouble())
            val remP = principal * (powN - powM) / (powN - 1.0)
            remP.coerceIn(0.0, principal)
        } else {
            (principal * (tenureMonths - monthsCompleted) / tenureMonths.toDouble()).coerceAtLeast(0.0)
        }

        val outstandingInterest = (outstandingTotalBalance - outstandingPrincipal).coerceAtLeast(0.0)

        return EmiCalculationResult(
            principal = principal,
            annualInterestRate = annualInterestRatePercent,
            tenureMonths = tenureMonths,
            startMonth = startMonth,
            startYear = startYear,
            monthlyEmi = monthlyEmi,
            totalPayable = totalPayable,
            totalInterestPayable = totalInterestPayable,
            monthsCompleted = monthsCompleted,
            monthsRemaining = monthsRemaining,
            paidSoFar = paidSoFar,
            outstandingTotalBalance = outstandingTotalBalance,
            outstandingPrincipal = outstandingPrincipal,
            outstandingInterest = outstandingInterest
        )
    }
}
