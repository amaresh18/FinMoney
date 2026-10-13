package com.example.domain.usecase

import java.util.Calendar

/**
 * Pure Kotlin Use Case for computing monthly budget health and burn rate.
 * Zero Android dependencies or side-effects for maximum unit testability.
 */
class CalculateBudgetHealthUseCase {

    data class BudgetMetrics(
        val totalIncome: Double,
        val totalDebits: Double,
        val totalPaid: Double,
        val totalYetToPay: Double,
        val flexibleCash: Double,
        val dailySafeSpend: Double,
        val daysRemainingInMonth: Int,
        val savingsRatePercent: Double
    )

    operator fun invoke(
        income: Double,
        debits: Double,
        paidDebits: Double,
        calendar: Calendar = Calendar.getInstance()
    ): BudgetMetrics {
        val safeIncome = if (income < 0.0) 0.0 else income
        val safeDebits = if (debits < 0.0) 0.0 else debits
        val safePaid = if (paidDebits < 0.0) 0.0 else paidDebits
        val yetToPay = (safeDebits - safePaid).coerceAtLeast(0.0)

        val flexibleCash = (safeIncome - safeDebits).coerceAtLeast(0.0)

        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
        val maxDays = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val daysRemaining = (maxDays - currentDay + 1).coerceAtLeast(1)

        val dailySafeSpend = if (daysRemaining > 0 && flexibleCash > 0) {
            flexibleCash / daysRemaining
        } else {
            0.0
        }

        val savingsRate = if (safeIncome > 0.0) {
            ((flexibleCash / safeIncome) * 100.0).coerceIn(0.0, 100.0)
        } else {
            0.0
        }

        return BudgetMetrics(
            totalIncome = safeIncome,
            totalDebits = safeDebits,
            totalPaid = safePaid,
            totalYetToPay = yetToPay,
            flexibleCash = flexibleCash,
            dailySafeSpend = dailySafeSpend,
            daysRemainingInMonth = daysRemaining,
            savingsRatePercent = savingsRate
        )
    }
}
