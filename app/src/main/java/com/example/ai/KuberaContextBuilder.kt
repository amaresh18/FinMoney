package com.example.ai

import com.example.data.model.*
import com.example.util.InterestCalculator
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

object KuberaContextBuilder {

    fun buildFinancialContextJson(
        month: Int,
        year: Int,
        salaryRecord: SalaryRecord?,
        debits: List<DebitItem>,
        loans: List<LoanTransaction>,
        userProfile: UserProfile?,
        currencySymbol: String = "₹"
    ): String {
        val root = JSONObject()

        val cal = Calendar.getInstance()
        val currentDay = cal.get(Calendar.DAY_OF_MONTH)
        val maxDaysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val remainingDaysInMonth = (maxDaysInMonth - currentDay + 1).coerceAtLeast(1)

        // Metadata
        root.put("timestamp", System.currentTimeMillis())
        root.put("auditPeriod", "$month/$year")
        root.put("currency", currencySymbol)
        root.put("userProfile", JSONObject().apply {
            put("name", userProfile?.name?.ifBlank { "Seeker of Wealth" } ?: "Seeker of Wealth")
            put("email", userProfile?.email ?: "")
            put("phone", userProfile?.phoneNumber ?: "")
        })

        // ==========================================
        // 1. INCOME LEDGER: (Sources, amounts, frequency, stability)
        // ==========================================
        val incomeLedger = JSONObject()
        val incomeSourcesArray = JSONArray()

        val baseSalary = salaryRecord?.salaryAmount ?: 0.0
        val additionalIncome = salaryRecord?.additionalIncome ?: 0.0

        if (baseSalary > 0.0) {
            incomeSourcesArray.put(JSONObject().apply {
                put("source", "Primary Employment Salary")
                put("amount", baseSalary)
                put("frequency", "Monthly")
                put("stability", "High / Fixed Guaranteed")
                put("category", "SALARY")
            })
        }

        if (additionalIncome > 0.0) {
            incomeSourcesArray.put(JSONObject().apply {
                put("source", "Additional / Side Income")
                put("amount", additionalIncome)
                put("frequency", "Variable")
                put("stability", "Moderate / Discretionary")
                put("category", "ADDITIONAL")
            })
        }

        // Custom Income Sources from JSON if present
        var customIncomesTotal = 0.0
        if (!salaryRecord?.incomeSourcesJson.isNullOrBlank()) {
            try {
                val jsonArr = JSONArray(salaryRecord!!.incomeSourcesJson)
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    val amt = obj.optDouble("amount", 0.0)
                    customIncomesTotal += amt
                    incomeSourcesArray.put(JSONObject().apply {
                        put("source", obj.optString("name", "Custom Income Source"))
                        put("amount", amt)
                        put("frequency", if (obj.optBoolean("isRecurring", true)) "Monthly Recurring" else "One-Time")
                        put("stability", "Active Inflow")
                        put("category", obj.optString("category", "OTHER"))
                    })
                }
            } catch (_: Exception) {}
        }

        val totalIncome = baseSalary + additionalIncome + customIncomesTotal
        incomeLedger.put("sources", incomeSourcesArray)
        incomeLedger.put("totalMonthlyInflow", totalIncome)
        incomeLedger.put("stabilityScore", if (baseSalary > 0) "85% (Solid Base)" else "40% (Irregular)")
        incomeLedger.put("frequencyProfile", "Predominantly Monthly Inflow Cycle")
        root.put("incomeLedger", incomeLedger)

        // ==========================================
        // 2. EXPENSE LEDGER: (Categories, daily transaction velocity, variable vs. fixed ratios)
        // ==========================================
        val expenseLedger = JSONObject()
        val categoriesBreakdown = JSONArray()

        val fixedCategoryKeys = setOf(
            DebitCategory.RENT.name,
            DebitCategory.EMI.name,
            DebitCategory.CHIT.name,
            DebitCategory.INSURANCE.name,
            DebitCategory.SCHOOL_FEES.name,
            DebitCategory.HOSTEL_FEES.name,
            DebitCategory.MAID.name,
            DebitCategory.MAINTENANCE.name,
            DebitCategory.ELECTRICITY.name,
            DebitCategory.INTERNET.name,
            DebitCategory.RECHARGE.name
        )

        var totalDebits = 0.0
        var paidDebits = 0.0
        var unpaidDebits = 0.0
        var fixedExpenses = 0.0
        var variableExpenses = 0.0

        val groupedByCategory = debits.groupBy { it.category }
        for ((catKey, items) in groupedByCategory) {
            val catTotal = items.sumOf { it.amount }
            val catPaid = items.filter { it.isPaid }.sumOf { it.amount }
            val catUnpaid = items.filter { !it.isPaid }.sumOf { it.amount }
            val isFixed = fixedCategoryKeys.contains(catKey)

            if (isFixed) {
                fixedExpenses += catTotal
            } else {
                variableExpenses += catTotal
            }

            totalDebits += catTotal
            paidDebits += catPaid
            unpaidDebits += catUnpaid

            categoriesBreakdown.put(JSONObject().apply {
                put("categoryKey", catKey)
                put("categoryName", items.firstOrNull()?.customCategoryName?.ifBlank { catKey } ?: catKey)
                put("totalAmount", catTotal)
                put("paidAmount", catPaid)
                put("yetToPayAmount", catUnpaid)
                put("itemCount", items.size)
                put("nature", if (isFixed) "Fixed / Essential Obligation" else "Variable / Lifestyle Discretionary")
            })
        }

        val dailyVelocity = if (currentDay > 0) paidDebits / currentDay else 0.0
        val fixedRatio = if (totalDebits > 0) (fixedExpenses / totalDebits) * 100 else 0.0
        val variableRatio = if (totalDebits > 0) (variableExpenses / totalDebits) * 100 else 0.0

        expenseLedger.put("categories", categoriesBreakdown)
        expenseLedger.put("totalExpenseBudget", totalDebits)
        expenseLedger.put("disbursedSoFar", paidDebits)
        expenseLedger.put("upcomingObligationsDue", unpaidDebits)
        expenseLedger.put("dailyTransactionVelocity", String.format("%.2f %s/day", dailyVelocity, currencySymbol))
        expenseLedger.put("fixedVsVariableRatio", JSONObject().apply {
            put("fixedExpensesAmount", fixedExpenses)
            put("variableExpensesAmount", variableExpenses)
            put("fixedPercent", String.format("%.1f%%", fixedRatio))
            put("variablePercent", String.format("%.1f%%", variableRatio))
        })

        // Flag potential anomalous spikes
        val diningTotal = groupedByCategory[DebitCategory.OUTSIDE_FOOD.name]?.sumOf { it.amount } ?: 0.0
        val shoppingTotal = groupedByCategory[DebitCategory.SHOPPING.name]?.sumOf { it.amount } ?: 0.0
        val anomalies = JSONArray()
        if (diningTotal > (totalIncome * 0.12).coerceAtLeast(3000.0)) {
            anomalies.put("Outside Food & Dining (₹$diningTotal) accounts for >12% of total inflow, signaling a primary fiscal leak.")
        }
        if (shoppingTotal > (totalIncome * 0.15).coerceAtLeast(4000.0)) {
            anomalies.put("Shopping & Lifestyle (₹$shoppingTotal) is running heavy against discretionary targets.")
        }
        expenseLedger.put("anomalousSpendingAlerts", anomalies)
        root.put("expenseLedger", expenseLedger)

        // ==========================================
        // 3. RECURRING OBLIGATIONS: (Active EMIs, interest structures, subscription commitments, due dates)
        // ==========================================
        val recurringObligations = JSONObject()
        val emiList = JSONArray()
        val subscriptionList = JSONArray()
        val chitList = JSONArray()

        val emiItems = debits.filter { it.category == DebitCategory.EMI.name }
        for (emi in emiItems) {
            emiList.put(JSONObject().apply {
                put("loanTitle", emi.title)
                put("monthlyInstallment", emi.amount)
                put("principalAmount", emi.principalAmount)
                put("annualInterestRatePercent", emi.interestRate)
                put("totalTenureMonths", emi.totalTenureMonths)
                put("completedTenureMonths", emi.currentMonthTenure)
                put("remainingTenureMonths", (emi.totalTenureMonths - emi.currentMonthTenure).coerceAtLeast(0))
                put("dueDateDay", emi.dueDateDay)
                put("status", if (emi.isPaid) "PAID_THIS_MONTH" else "PENDING_DUE")
            })
        }

        val chitItems = debits.filter { it.category == DebitCategory.CHIT.name }
        for (chit in chitItems) {
            chitList.put(JSONObject().apply {
                put("chitTitle", chit.title)
                put("monthlyInstallment", chit.amount)
                put("totalTenureMonths", chit.totalTenureMonths)
                put("currentTenureMonth", chit.currentMonthTenure)
                put("dueDateDay", chit.dueDateDay)
                put("status", if (chit.isPaid) "PAID" else "PENDING")
            })
        }

        val subCategories = setOf(
            DebitCategory.OTT.name,
            DebitCategory.RECHARGE.name,
            DebitCategory.INTERNET.name,
            DebitCategory.MAID.name,
            DebitCategory.MAINTENANCE.name
        )
        val subscriptions = debits.filter { subCategories.contains(it.category) || (it.isRecurring && it.category != DebitCategory.EMI.name && it.category != DebitCategory.CHIT.name) }
        for (sub in subscriptions) {
            subscriptionList.put(JSONObject().apply {
                put("commitment", sub.title)
                put("category", sub.category)
                put("monthlyAmount", sub.amount)
                put("dueDateDay", sub.dueDateDay)
                put("isPaid", sub.isPaid)
            })
        }

        recurringObligations.put("activeEmis", emiList)
        recurringObligations.put("chitObligations", chitList)
        recurringObligations.put("subscriptionCommitments", subscriptionList)
        recurringObligations.put("totalMonthlyRecurringBurden", (emiItems + chitItems + subscriptions).sumOf { it.amount })
        root.put("recurringObligations", recurringObligations)

        // ==========================================
        // 4. DEBT LEDGER: (Borrowings/Liabilities to pay off, Lendings/Assets to recover, per-contact balances)
        // ==========================================
        val debtLedger = JSONObject()
        val borrowingsArray = JSONArray()
        val lendingsArray = JSONArray()
        val perContactBalances = JSONObject()

        var totalBorrowingsRemaining = 0.0
        var totalLendingsRecoverable = 0.0

        for (loan in loans) {
            if (loan.status == ApprovalStatus.REJECTED) continue

            val breakdown = InterestCalculator.calculate(
                principal = loan.totalAmount,
                ratePercent = loan.interestRatePercent,
                isMonthly = loan.isMonthlyInterest,
                startTimestamp = loan.startDateTimestamp
            )

            val isBorrowing = loan.type == LoanType.TAKEN || loan.type == LoanType.BORROWED
            val remainingDue = breakdown.totalRemainingDue

            val contactKey = loan.counterpartyName.ifBlank { "Anonymous Counterparty" }
            val existingBalance = perContactBalances.optDouble(contactKey, 0.0)
            // positive means counterparty owes user; negative means user owes counterparty
            val updatedBalance = if (isBorrowing) existingBalance - remainingDue else existingBalance + remainingDue
            perContactBalances.put(contactKey, updatedBalance)

            val loanObj = JSONObject().apply {
                put("counterpartyName", contactKey)
                put("contactPhone", loan.counterpartyContact)
                put("originalPrincipal", loan.totalAmount)
                put("accruedInterest", breakdown.accruedInterest)
                put("remainingDue", remainingDue)
                put("interestRatePercent", loan.interestRatePercent)
                put("interestPeriod", if (loan.isMonthlyInterest) "Monthly" else "Annual")
                put("daysElapsed", breakdown.daysElapsed)
                put("dueDateTimestamp", loan.dueDateTimestamp ?: 0L)
                put("approvalStatus", loan.status)
            }

            if (isBorrowing) {
                totalBorrowingsRemaining += remainingDue
                borrowingsArray.put(loanObj)
            } else {
                totalLendingsRecoverable += remainingDue
                lendingsArray.put(loanObj)
            }
        }

        debtLedger.put("borrowingsToPayOff", borrowingsArray)
        debtLedger.put("lendingsToRecover", lendingsArray)
        debtLedger.put("totalLiabilitiesToPayOff", totalBorrowingsRemaining)
        debtLedger.put("totalAssetsToRecover", totalLendingsRecoverable)
        debtLedger.put("netDebtPosition", totalLendingsRecoverable - totalBorrowingsRemaining)
        debtLedger.put("perContactBalances", perContactBalances)

        // Suggest Highest Interest vs Smallest Debt strategy rankings
        val sortedForHighestInterest = loans.filter { it.type == LoanType.TAKEN || it.type == LoanType.BORROWED }
            .sortedByDescending { it.interestRatePercent }
            .map { "${it.counterpartyName}: ₹${it.totalAmount} @ ${it.interestRatePercent}%" }
        val sortedForSmallestBalance = loans.filter { it.type == LoanType.TAKEN || it.type == LoanType.BORROWED }
            .sortedBy { it.totalAmount }
            .map { "${it.counterpartyName}: ₹${it.totalAmount}" }

        debtLedger.put("highestInterestRankings", JSONArray(sortedForHighestInterest))
        debtLedger.put("smallestBalanceRankings", JSONArray(sortedForSmallestBalance))
        root.put("debtLedger", debtLedger)

        // ==========================================
        // 5. MASTER WEALTH SURPLUS & SAFE-TO-SPEND
        // ==========================================
        val surplusMetrics = JSONObject()
        val netFlexibleSurplus = (totalIncome - totalDebits).coerceAtLeast(0.0)
        val dailySafeSpend = if (remainingDaysInMonth > 0) netFlexibleSurplus / remainingDaysInMonth else 0.0
        val savingsRatePercent = if (totalIncome > 0) ((totalIncome - totalDebits) / totalIncome) * 100 else 0.0

        surplusMetrics.put("totalMonthlyInflow", totalIncome)
        surplusMetrics.put("totalDebitsObligated", totalDebits)
        surplusMetrics.put("netFlexibleCashSurplus", netFlexibleSurplus)
        surplusMetrics.put("remainingDaysInMonth", remainingDaysInMonth)
        surplusMetrics.put("dailySafeToSpendAllowance", String.format("%.2f %s/day", dailySafeSpend, currencySymbol))
        surplusMetrics.put("weeklySafeToSpendAllowance", String.format("%.2f %s/week", dailySafeSpend * 7, currencySymbol))
        surplusMetrics.put("savingsRatePercent", String.format("%.1f%%", savingsRatePercent))
        surplusMetrics.put("wealthPotStatus", when {
            savingsRatePercent >= 40.0 -> "Abundant & Thriving (Golden Path)"
            savingsRatePercent >= 20.0 -> "Solid Discipline (Steady Accumulation)"
            savingsRatePercent >= 5.0 -> "Tense Margin (Vulnerable to Shock)"
            else -> "Deficit / High Risk of Leakage"
        })
        root.put("surplusAndSafeToSpend", surplusMetrics)

        return root.toString(2)
    }
}
