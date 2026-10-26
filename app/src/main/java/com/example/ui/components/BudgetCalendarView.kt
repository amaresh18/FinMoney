package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import com.example.data.model.DebitItem
import com.example.data.model.IncomeSource
import com.example.ui.theme.*
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

enum class BudgetDayStatus {
    UNDER_BUDGET, // Expenses < 90% of Daily Limit (Green)
    AROUND_LIMIT, // Expenses between 90% and 110% of Daily Limit (Yellow)
    OVER_BUDGET,  // Expenses > 110% of Daily Limit (Red)
    NO_EXPENSES   // 0 Expenses (Neutral Slate/Light)
}

/**
 * Enhanced INDmoney & Premium Styled Budget Daily Limit Calendar.
 * Computes net available income/budget divided equally by month days to get daily limit.
 * Precalculates expenses per day and styles date cells with Green (<90%), Yellow (±10%), Red (>110%).
 * Clean white background and premium INDmoney theme accents.
 */
@Composable
fun BudgetCalendarView(
    selectedMonth: Int, // 1..12
    selectedYear: Int,
    selectedDay: Int?,  // null or 1..31
    currencySymbol: String = "₹",
    isPrivacyVisible: Boolean = true,
    debits: List<DebitItem>,
    incomeSources: List<IncomeSource>,
    monthlyBudgetPreference: Double = 0.0,
    onSelectDate: (month: Int, year: Int, day: Int?) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onAddDebitForDate: ((day: Int) -> Unit)? = null,
    onEditDebit: ((DebitItem) -> Unit)? = null,
    onDeleteDebit: ((DebitItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf("TODAY") } // "TODAY", "WEEK", "MONTH", "YEAR"

    val todayCalendar = remember { Calendar.getInstance() }
    val todayDay = todayCalendar.get(Calendar.DAY_OF_MONTH)
    val todayMonth = todayCalendar.get(Calendar.MONTH) + 1
    val todayYear = todayCalendar.get(Calendar.YEAR)

    val monthNames = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
    val currentMonthName = monthNames.getOrElse(selectedMonth - 1) { "Month" }

    // Number of days in the month
    val daysInMonth = remember(selectedMonth, selectedYear) {
        val cal = Calendar.getInstance()
        cal.set(selectedYear, selectedMonth - 1, 1)
        cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    // Day of week offset for day 1 (0 = Sun, 1 = Mon, ..., 6 = Sat)
    val startDayOfWeek = remember(selectedMonth, selectedYear) {
        val cal = Calendar.getInstance()
        cal.set(selectedYear, selectedMonth - 1, 1)
        cal.get(Calendar.DAY_OF_WEEK) - 1
    }

    // Filter debits for selected month & year
    val monthDebits = remember(debits, selectedMonth, selectedYear) {
        debits.filter { it.month == selectedMonth && it.year == selectedYear }
    }

    // Map of day (1..daysInMonth) to list of debits
    val dayToDebitsMap = remember(monthDebits) {
        val map = mutableMapOf<Int, MutableList<DebitItem>>()
        monthDebits.forEach { debit ->
            val day = when {
                debit.dueDateDay in 1..31 -> debit.dueDateDay
                debit.paidDateTimestamp != null && debit.paidDateTimestamp > 0 -> {
                    val cal = Calendar.getInstance().apply { timeInMillis = debit.paidDateTimestamp }
                    cal.get(Calendar.DAY_OF_MONTH)
                }
                debit.createdAt > 0 -> {
                    val cal = Calendar.getInstance().apply { timeInMillis = debit.createdAt }
                    cal.get(Calendar.DAY_OF_MONTH)
                }
                else -> 1
            }
            map.getOrPut(day) { mutableListOf() }.add(debit)
        }
        map
    }

    // Total income for selected month
    val totalMonthIncome = remember(incomeSources) {
        incomeSources.sumOf { it.amount }
    }

    // Total debits for selected month
    val totalMonthDebits = remember(monthDebits) {
        monthDebits.sumOf { it.amount }
    }

    // Net Budget Pool
    val netBudgetPool = remember(totalMonthIncome, monthlyBudgetPreference, totalMonthDebits) {
        if (totalMonthIncome > 0) {
            totalMonthIncome
        } else if (monthlyBudgetPreference > 0) {
            monthlyBudgetPreference
        } else if (totalMonthDebits > 0) {
            totalMonthDebits
        } else {
            30000.0 // Default baseline pool
        }
    }

    // Daily Budget Limit
    val dailyBudgetLimit = remember(netBudgetPool, daysInMonth) {
        if (daysInMonth > 0) (netBudgetPool / daysInMonth).coerceAtLeast(10.0) else 1000.0
    }

    // Map of day (1..daysInMonth) to total expenses
    val dailyExpenseMap = remember(dayToDebitsMap, daysInMonth) {
        (1..daysInMonth).associateWith { day ->
            dayToDebitsMap[day]?.sumOf { it.amount } ?: 0.0
        }
    }

    // Win Rate / Health Statistics
    val daysWithExpensesCount = dailyExpenseMap.count { it.value > 0 }
    val underBudgetDaysCount = dailyExpenseMap.count { it.value > 0 && it.value < 0.90 * dailyBudgetLimit }
    val aroundLimitDaysCount = dailyExpenseMap.count { it.value in (0.90 * dailyBudgetLimit)..(1.10 * dailyBudgetLimit) }
    val overBudgetDaysCount = dailyExpenseMap.count { it.value > 1.10 * dailyBudgetLimit }

    val avgDailyExpense = if (daysWithExpensesCount > 0) {
        dailyExpenseMap.values.sum() / daysWithExpensesCount
    } else 0.0

    val savingsWinRatePercent = if (daysWithExpensesCount > 0) {
        ((underBudgetDaysCount + aroundLimitDaysCount).toDouble() / daysWithExpensesCount * 100.0)
    } else 100.0

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = IndSurface, // Pure White surface as per INDmoney theme
        border = androidx.compose.foundation.BorderStroke(1.dp, IndBorder),
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Top Navigation Bar - Title & Tabs (Today's, Week, Month, Year)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(IndBlueLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = IndBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Daily Budget Calendar",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = IndNavyHeader
                            )
                            Text(
                                text = "Equal Daily Limit Split: ${formatCurrency(dailyBudgetLimit, currencySymbol, isPrivacyVisible)}/day",
                                style = MaterialTheme.typography.labelSmall,
                                color = IndTextSecondary
                            )
                        }
                    }
                }

                // Top Tabs: Today's, Week, Month, Year
                TabRow(
                    selectedTabIndex = when (activeTab) {
                        "TODAY" -> 0
                        "WEEK" -> 1
                        "MONTH" -> 2
                        "YEAR" -> 3
                        else -> 0
                    },
                    containerColor = IndCardSecondary,
                    contentColor = IndBlue,
                    indicator = { tabPositions ->
                        if (tabPositions.isNotEmpty()) {
                            val pos = when (activeTab) {
                                "TODAY" -> 0
                                "WEEK" -> 1
                                "MONTH" -> 2
                                "YEAR" -> 3
                                else -> 0
                            }
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[pos]),
                                color = IndBlue,
                                height = 3.dp
                            )
                        }
                    },
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    listOf("TODAY" to "Today's", "WEEK" to "Week", "MONTH" to "Month", "YEAR" to "Year").forEach { (tabKey, tabTitle) ->
                        Tab(
                            selected = activeTab == tabKey,
                            onClick = { activeTab = tabKey },
                            text = {
                                Text(
                                    text = tabTitle,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (activeTab == tabKey) IndBlue else IndTextSecondary
                                )
                            }
                        )
                    }
                }
            }

            // 2. Month Selector Controls (< October 2026 >)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(IndBlueLight)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPreviousMonth,
                    modifier = Modifier.testTag("calendar_prev_month_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Previous Month",
                        tint = IndBlue
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$currentMonthName $selectedYear",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = IndNavyHeader
                    )
                    Text(
                        text = "*Daily limit calculated by dividing net monthly pool by $daysInMonth days*",
                        style = MaterialTheme.typography.labelSmall,
                        color = IndTextSecondary,
                        fontSize = 9.sp
                    )
                }

                IconButton(
                    onClick = onNextMonth,
                    modifier = Modifier.testTag("calendar_next_month_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Next Month",
                        tint = IndBlue
                    )
                }
            }

            // 3. Tab Content View
            when (activeTab) {
                "TODAY" -> {
                    DailyCalendarViewContent(
                        selectedMonth = selectedMonth,
                        selectedYear = selectedYear,
                        selectedDay = selectedDay,
                        todayDay = todayDay,
                        todayMonth = todayMonth,
                        todayYear = todayYear,
                        daysInMonth = daysInMonth,
                        startDayOfWeek = startDayOfWeek,
                        dailyBudgetLimit = dailyBudgetLimit,
                        dailyExpenseMap = dailyExpenseMap,
                        dayToDebitsMap = dayToDebitsMap,
                        currencySymbol = currencySymbol,
                        isPrivacyVisible = isPrivacyVisible,
                        netBudgetPool = netBudgetPool,
                        totalMonthDebits = totalMonthDebits,
                        avgDailyExpense = avgDailyExpense,
                        savingsWinRatePercent = savingsWinRatePercent,
                        underBudgetDaysCount = underBudgetDaysCount,
                        overBudgetDaysCount = overBudgetDaysCount,
                        onSelectDate = onSelectDate,
                        onAddDebitForDate = onAddDebitForDate,
                        onEditDebit = onEditDebit,
                        onDeleteDebit = onDeleteDebit
                    )
                }
                "WEEK" -> {
                    WeeklyBreakdownContent(
                        selectedMonth = selectedMonth,
                        selectedYear = selectedYear,
                        daysInMonth = daysInMonth,
                        dailyBudgetLimit = dailyBudgetLimit,
                        dailyExpenseMap = dailyExpenseMap,
                        currencySymbol = currencySymbol,
                        isPrivacyVisible = isPrivacyVisible
                    )
                }
                "MONTH" -> {
                    MonthlyBreakdownContent(
                        selectedYear = selectedYear,
                        debits = debits,
                        incomeSources = incomeSources,
                        monthlyBudgetPreference = monthlyBudgetPreference,
                        currencySymbol = currencySymbol,
                        isPrivacyVisible = isPrivacyVisible
                    )
                }
                "YEAR" -> {
                    YearlyBreakdownContent(
                        debits = debits,
                        incomeSources = incomeSources,
                        currencySymbol = currencySymbol,
                        isPrivacyVisible = isPrivacyVisible
                    )
                }
            }
        }
    }
}

/**
 * Daily Calendar Grid and Details for "Today's" / Daily Tab in White Theme.
 */
@Composable
private fun DailyCalendarViewContent(
    selectedMonth: Int,
    selectedYear: Int,
    selectedDay: Int?,
    todayDay: Int,
    todayMonth: Int,
    todayYear: Int,
    daysInMonth: Int,
    startDayOfWeek: Int,
    dailyBudgetLimit: Double,
    dailyExpenseMap: Map<Int, Double>,
    dayToDebitsMap: Map<Int, List<DebitItem>>,
    currencySymbol: String,
    isPrivacyVisible: Boolean = true,
    netBudgetPool: Double,
    totalMonthDebits: Double,
    avgDailyExpense: Double,
    savingsWinRatePercent: Double,
    underBudgetDaysCount: Int,
    overBudgetDaysCount: Int,
    onSelectDate: (month: Int, year: Int, day: Int?) -> Unit,
    onAddDebitForDate: ((day: Int) -> Unit)?,
    onEditDebit: ((DebitItem) -> Unit)?,
    onDeleteDebit: ((DebitItem) -> Unit)?
) {
    val monthNames = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
    val currentMonthName = monthNames.getOrElse(selectedMonth - 1) { "Month" }

    // Top Overview Banner Card
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = IndGreenLight, // Soft Emerald Green header card
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA7F3D0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total Month Budget Pool",
                    style = MaterialTheme.typography.labelSmall,
                    color = IndGreenDark,
                    fontWeight = FontWeight.SemiBold
                )
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFA7F3D0)
                ) {
                    Text(
                        text = "Win Rate ${String.format(Locale.US, "%.1f", savingsWinRatePercent)}% 📈",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = IndGreenDark,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 11.sp
                    )
                }
            }

            Text(
                text = formatCurrency(netBudgetPool - totalMonthDebits, currencySymbol, isPrivacyVisible),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (netBudgetPool - totalMonthDebits >= 0) IndGreenDark else IndRedDark
            )

            HorizontalDivider(color = Color(0xFFA7F3D0), thickness = 1.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Daily Target Limit", style = MaterialTheme.typography.labelSmall, color = IndTextSecondary, fontSize = 10.sp)
                    Text("${formatCurrency(dailyBudgetLimit, currencySymbol, isPrivacyVisible)} / day", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = IndNavyHeader)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("Avg Daily Spend", style = MaterialTheme.typography.labelSmall, color = IndTextSecondary, fontSize = 10.sp)
                    Text(formatCurrency(avgDailyExpense, currencySymbol, isPrivacyVisible), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = if (avgDailyExpense <= dailyBudgetLimit) IndGreenDark else IndRedDark)
                }
            }
        }
    }

    // Daily breakdown title
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Daily breakdown",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = IndNavyHeader
        )
        if (selectedDay != null) {
            TextButton(
                onClick = { onSelectDate(selectedMonth, selectedYear, null) },
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Clear Date Selection", fontSize = 11.sp, color = IndBlue, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    // Days of week header (S, M, T, W, TH, F, S)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        listOf("S", "M", "T", "W", "TH", "F", "S").forEach { dayHeader ->
            Text(
                text = dayHeader,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = IndTextSecondary,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
        }
    }

    // Notice card for recurring and one-time expense calculation
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = IndCardSecondary,
        border = androidx.compose.foundation.BorderStroke(1.dp, IndBorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = IndBlue,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Includes all recurring & one-time expenses in daily calculations. One-time expenses marked with *.",
                style = MaterialTheme.typography.labelSmall,
                color = IndTextSecondary,
                fontSize = 10.sp
            )
        }
    }

    // 7-Column Calendar Grid
    val totalCells = startDayOfWeek + daysInMonth
    val rows = (totalCells + 6) / 7

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        for (rowIndex in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (colIndex in 0 until 7) {
                    val cellIndex = rowIndex * 7 + colIndex
                    val dayNum = cellIndex - startDayOfWeek + 1

                    if (dayNum in 1..daysInMonth) {
                        val isToday = dayNum == todayDay && selectedMonth == todayMonth && selectedYear == todayYear
                        val isSelected = selectedDay == dayNum
                        val expenseAmount = dailyExpenseMap[dayNum] ?: 0.0
                        val dayDebits = dayToDebitsMap[dayNum] ?: emptyList()
                        val hasNonRecurring = dayDebits.any { !it.isRecurring }

                        // Determine status
                        val status = when {
                            expenseAmount == 0.0 -> BudgetDayStatus.NO_EXPENSES
                            expenseAmount < 0.90 * dailyBudgetLimit -> BudgetDayStatus.UNDER_BUDGET
                            expenseAmount <= 1.10 * dailyBudgetLimit -> BudgetDayStatus.AROUND_LIMIT
                            else -> BudgetDayStatus.OVER_BUDGET
                        }

                        DateCell(
                            dayNumber = dayNum,
                            expenseAmount = expenseAmount,
                            status = status,
                            isToday = isToday,
                            isSelected = isSelected,
                            hasNonRecurring = hasNonRecurring,
                            currencySymbol = currencySymbol,
                            isPrivacyVisible = isPrivacyVisible,
                            onClick = {
                                onSelectDate(selectedMonth, selectedYear, if (isSelected) null else dayNum)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    // Calendar Bottom Legend Bar
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendDot(color = Color(0xFF22C55E), label = "Under Limit")
        LegendDot(color = Color(0xFFF59E0B), label = "~Limit (±10%)")
        LegendDot(color = Color(0xFFEF4444), label = "Over Limit")
        LegendDot(color = IndBlue, label = "Today")
        LegendDot(color = Color(0xFF94A3B8), label = "No Expense")
    }

    // Interactive Selected Date Details Card
    if (selectedDay != null) {
        val selectedDayDebits = dayToDebitsMap[selectedDay] ?: emptyList()
        val dayTotal = dailyExpenseMap[selectedDay] ?: 0.0
        val dayDiff = dailyBudgetLimit - dayTotal

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = IndSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, IndBorder),
            shadowElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Date Details: $selectedDay $currentMonthName $selectedYear",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = IndNavyHeader
                        )
                        Text(
                            text = if (dayDiff >= 0)
                                "🟢 Under budget by ${formatCurrency(dayDiff, currencySymbol, isPrivacyVisible)}"
                            else
                                "🔴 Over limit by ${formatCurrency(-dayDiff, currencySymbol, isPrivacyVisible)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (dayDiff >= 0) IndGreenDark else IndRedDark,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (onAddDebitForDate != null) {
                        IconButton(
                            onClick = { onAddDebitForDate(selectedDay) },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(IndBlue)
                                .testTag("add_debit_for_date_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Debit", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                HorizontalDivider(color = IndBorderSubtle)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Day Expense Total:", style = MaterialTheme.typography.bodySmall, color = IndTextSecondary)
                    Text(
                        formatCurrency(dayTotal, currencySymbol, isPrivacyVisible),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = IndNavyHeader
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Daily Target Limit:", style = MaterialTheme.typography.bodySmall, color = IndTextSecondary)
                    Text(
                        formatCurrency(dailyBudgetLimit, currencySymbol, isPrivacyVisible),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = IndBlue
                    )
                }

                if (selectedDayDebits.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No recorded expenses for this day. All clear! 🎉",
                            style = MaterialTheme.typography.bodySmall,
                            color = IndTextSecondary
                        )
                    }
                } else {
                    Text(
                        text = "Itemized Expenses (${selectedDayDebits.size}):",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = IndNavyHeader
                    )

                    selectedDayDebits.forEach { debit ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(IndCardSecondary)
                                .border(1.dp, IndBorderSubtle, RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = debit.title.ifBlank { debit.category },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = IndTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (!debit.isRecurring) Color(0xFFF3E8FF) else IndBlueLight,
                                        border = androidx.compose.foundation.BorderStroke(0.5.dp, if (!debit.isRecurring) Color(0xFFC084FC) else IndBlue)
                                    ) {
                                        Text(
                                            text = if (!debit.isRecurring) "⭐ One-time (*)" else "🔄 Recurring",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (!debit.isRecurring) Color(0xFF7E22CE) else IndBlue,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${debit.category} • ${if (debit.isPaid) "Paid" else "Due"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = IndTextSecondary,
                                    fontSize = 10.sp
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = formatCurrency(debit.amount, currencySymbol, isPrivacyVisible),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = IndRedDark
                                )

                                if (onEditDebit != null) {
                                    IconButton(
                                        onClick = { onEditDebit(debit) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = IndBlue, modifier = Modifier.size(14.dp))
                                    }
                                }

                                if (onDeleteDebit != null) {
                                    IconButton(
                                        onClick = { onDeleteDebit(debit) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = IndRed, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }

                    if (selectedDayDebits.any { !it.isRecurring }) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFAF5FF),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE9D5FF)),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("⭐", fontSize = 12.sp)
                                Text(
                                    text = "One-time (non-recurring) expenses are calculated into this day's limit and marked with * on grid.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF6B21A8),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual Rounded Square Date Cell for Calendar Grid in INDmoney Theme.
 */
@Composable
private fun DateCell(
    dayNumber: Int,
    expenseAmount: Double,
    status: BudgetDayStatus,
    isToday: Boolean,
    isSelected: Boolean,
    hasNonRecurring: Boolean = false,
    currencySymbol: String,
    isPrivacyVisible: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        isToday -> Color(0xFFEFF6FF)
        status == BudgetDayStatus.UNDER_BUDGET -> Color(0xFFF0FDF4)
        status == BudgetDayStatus.AROUND_LIMIT -> Color(0xFFFFFBEB)
        status == BudgetDayStatus.OVER_BUDGET -> Color(0xFFFEF2F2)
        else -> Color(0xFFFAFAFA)
    }

    val textColor = when {
        status == BudgetDayStatus.UNDER_BUDGET -> Color(0xFF15803D)
        status == BudgetDayStatus.AROUND_LIMIT -> Color(0xFFB45309)
        status == BudgetDayStatus.OVER_BUDGET -> Color(0xFFDC2626)
        else -> Color(0xFF64748B)
    }

    val borderColor = when {
        isSelected -> IndBlue
        isToday -> IndBlue
        status == BudgetDayStatus.UNDER_BUDGET -> Color(0xFF86EFAC)
        status == BudgetDayStatus.AROUND_LIMIT -> Color(0xFFFDE68A)
        status == BudgetDayStatus.OVER_BUDGET -> Color(0xFFFCA5A5)
        else -> Color(0xFFE2E8F0)
    }

    val formattedAmount = remember(expenseAmount, currencySymbol, isPrivacyVisible, hasNonRecurring) {
        val baseStr = when {
            !isPrivacyVisible && expenseAmount > 0 -> "$currencySymbol★"
            expenseAmount == 0.0 -> ""
            expenseAmount >= 1000 -> "${currencySymbol}${String.format(Locale.US, "%.1f", expenseAmount / 1000)}K"
            else -> "${currencySymbol}${expenseAmount.toInt()}"
        }
        if (baseStr.isNotBlank() && expenseAmount > 0 && hasNonRecurring) "$baseStr*" else baseStr
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = backgroundColor,
        border = androidx.compose.foundation.BorderStroke(if (isSelected || isToday) 2.dp else 1.dp, borderColor),
        modifier = modifier
            .heightIn(min = 52.dp)
            .testTag("date_cell_$dayNumber")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 3.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Day Number & Today indicator dot
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$dayNumber",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isToday || isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                    color = if (isToday) IndBlue else IndNavyHeader,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 2.dp)
                )
                if (isToday) {
                    Box(
                        modifier = Modifier
                            .padding(end = 2.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(IndBlue)
                    )
                }
            }

            // Expense Amount pill container
            if (formattedAmount.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when {
                        status == BudgetDayStatus.UNDER_BUDGET -> Color(0xFFDCFCE7)
                        status == BudgetDayStatus.AROUND_LIMIT -> Color(0xFFFEF3C7)
                        status == BudgetDayStatus.OVER_BUDGET -> Color(0xFFFEE2E2)
                        else -> Color(0xFFF1F5F9)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = formattedAmount,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 2.dp, horizontal = 1.dp)
                    )
                }
            } else {
                Text(
                    text = "-",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

/**
 * Legend Dot item.
 */
@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = IndTextSecondary,
            fontSize = 8.sp
        )
    }
}

/**
 * Weekly Breakdown Content for "Week" Tab in White Theme.
 */
@Composable
private fun WeeklyBreakdownContent(
    selectedMonth: Int,
    selectedYear: Int,
    daysInMonth: Int,
    dailyBudgetLimit: Double,
    dailyExpenseMap: Map<Int, Double>,
    currencySymbol: String,
    isPrivacyVisible: Boolean = true
) {
    val weeks = remember(daysInMonth) {
        (1..daysInMonth).chunked(7)
    }

    val monthNamesShort = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )
    val monthLabel = monthNamesShort.getOrElse(selectedMonth - 1) { "" }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Weekly Breakdown ($monthLabel $selectedYear)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = IndNavyHeader
            )
            Text(
                text = "7-Day Target: ${formatCurrency(dailyBudgetLimit * 7.0, currencySymbol, isPrivacyVisible)}",
                style = MaterialTheme.typography.labelSmall,
                color = IndTextSecondary
            )
        }

        weeks.forEachIndexed { index, weekDays ->
            val weekTotal = weekDays.sumOf { dailyExpenseMap[it] ?: 0.0 }
            val weekTargetLimit = dailyBudgetLimit * weekDays.size.toDouble()
            val weekDiff = weekTargetLimit - weekTotal
            val startDay = weekDays.first()
            val endDay = weekDays.last()

            val status = when {
                weekTotal == 0.0 -> BudgetDayStatus.NO_EXPENSES
                weekTotal < 0.90 * weekTargetLimit -> BudgetDayStatus.UNDER_BUDGET
                weekTotal <= 1.10 * weekTargetLimit -> BudgetDayStatus.AROUND_LIMIT
                else -> BudgetDayStatus.OVER_BUDGET
            }

            val cardBg = when (status) {
                BudgetDayStatus.UNDER_BUDGET -> Color(0xFFDCFCE7)
                BudgetDayStatus.AROUND_LIMIT -> IndAmberLight
                BudgetDayStatus.OVER_BUDGET -> IndRedLight
                BudgetDayStatus.NO_EXPENSES -> IndCardSecondary
            }

            val borderColor = when (status) {
                BudgetDayStatus.UNDER_BUDGET -> Color(0xFF22C55E)
                BudgetDayStatus.AROUND_LIMIT -> IndAmber
                BudgetDayStatus.OVER_BUDGET -> IndRed
                BudgetDayStatus.NO_EXPENSES -> IndBorder
            }

            val textColor = when (status) {
                BudgetDayStatus.UNDER_BUDGET -> Color(0xFF15803D)
                BudgetDayStatus.AROUND_LIMIT -> Color(0xFFB45309)
                BudgetDayStatus.OVER_BUDGET -> IndRedDark
                BudgetDayStatus.NO_EXPENSES -> IndTextSecondary
            }

            val statusText = when (status) {
                BudgetDayStatus.UNDER_BUDGET -> "🟢 Under Budget"
                BudgetDayStatus.AROUND_LIMIT -> "🟡 Around Limit"
                BudgetDayStatus.OVER_BUDGET -> "🔴 Over Budget"
                BudgetDayStatus.NO_EXPENSES -> "⚪ No Expenses"
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = cardBg,
                border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Week ${index + 1} ($startDay - $endDay $monthLabel)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = IndNavyHeader
                            )
                            Text(
                                text = "Target Limit: ${formatCurrency(weekTargetLimit, currencySymbol, isPrivacyVisible)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = IndTextSecondary,
                                fontSize = 11.sp
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = borderColor.copy(alpha = 0.25f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
                        ) {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = 10.sp
                            )
                        }
                    }

                    HorizontalDivider(color = borderColor.copy(alpha = 0.3f), thickness = 1.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Weekly Spending",
                                style = MaterialTheme.typography.labelSmall,
                                color = IndTextSecondary,
                                fontSize = 10.sp
                            )
                            Text(
                                text = formatCurrency(weekTotal, currencySymbol, isPrivacyVisible),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = IndNavyHeader
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (weekDiff >= 0) "Remaining Budget" else "Over Budget By",
                                style = MaterialTheme.typography.labelSmall,
                                color = IndTextSecondary,
                                fontSize = 10.sp
                            )
                            Text(
                                text = formatCurrency(Math.abs(weekDiff), currencySymbol, isPrivacyVisible),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (weekDiff >= 0) Color(0xFF15803D) else IndRedDark
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Monthly Breakdown Content for "Month" Tab in White Theme.
 */
@Composable
private fun MonthlyBreakdownContent(
    selectedYear: Int,
    debits: List<DebitItem>,
    incomeSources: List<IncomeSource>,
    monthlyBudgetPreference: Double,
    currencySymbol: String,
    isPrivacyVisible: Boolean = true
) {
    val monthNames = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    val yearDebits = remember(debits, selectedYear) {
        debits.filter { it.year == selectedYear }
    }

    val monthlySpentMap = remember(yearDebits) {
        (1..12).associateWith { m ->
            yearDebits.filter { it.month == m }.sumOf { it.amount }
        }
    }

    val totalYearIncome = incomeSources.sumOf { it.amount }
    val monthlyTargetLimit = if (totalYearIncome > 0) totalYearIncome else if (monthlyBudgetPreference > 0) monthlyBudgetPreference else 30000.0

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Monthly Financial Health ($selectedYear)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = IndNavyHeader
        )

        val monthChunks = (1..12).chunked(3)
        monthChunks.forEach { chunk ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                chunk.forEach { m ->
                    val monthName = monthNames[m - 1]
                    val totalSpent = monthlySpentMap[m] ?: 0.0
                    val monthTargetLimit = monthlyTargetLimit

                    val status = when {
                        totalSpent == 0.0 -> BudgetDayStatus.NO_EXPENSES
                        totalSpent < 0.90 * monthTargetLimit -> BudgetDayStatus.UNDER_BUDGET
                        totalSpent <= 1.10 * monthTargetLimit -> BudgetDayStatus.AROUND_LIMIT
                        else -> BudgetDayStatus.OVER_BUDGET
                    }

                    val cardBg = when (status) {
                        BudgetDayStatus.UNDER_BUDGET -> Color(0xFFDCFCE7)
                        BudgetDayStatus.AROUND_LIMIT -> IndAmberLight
                        BudgetDayStatus.OVER_BUDGET -> IndRedLight
                        BudgetDayStatus.NO_EXPENSES -> IndCardSecondary
                    }

                    val borderColor = when (status) {
                        BudgetDayStatus.UNDER_BUDGET -> Color(0xFF22C55E)
                        BudgetDayStatus.AROUND_LIMIT -> IndAmber
                        BudgetDayStatus.OVER_BUDGET -> IndRed
                        BudgetDayStatus.NO_EXPENSES -> IndBorder
                    }

                    val textColor = when (status) {
                        BudgetDayStatus.UNDER_BUDGET -> Color(0xFF15803D)
                        BudgetDayStatus.AROUND_LIMIT -> Color(0xFFB45309)
                        BudgetDayStatus.OVER_BUDGET -> IndRedDark
                        BudgetDayStatus.NO_EXPENSES -> IndTextSecondary
                    }

                    val statusBadgeText = when (status) {
                        BudgetDayStatus.UNDER_BUDGET -> "Under"
                        BudgetDayStatus.AROUND_LIMIT -> "~Limit"
                        BudgetDayStatus.OVER_BUDGET -> "Over"
                        BudgetDayStatus.NO_EXPENSES -> "Clear"
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = cardBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(0.95f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = monthName,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = IndNavyHeader
                            )

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (totalSpent == 0.0) "-" else formatCurrency(totalSpent, currencySymbol, isPrivacyVisible),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = borderColor.copy(alpha = 0.25f),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Text(
                                        text = statusBadgeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Yearly Breakdown Content for "Year" Tab in White Theme.
 */
@Composable
private fun YearlyBreakdownContent(
    debits: List<DebitItem>,
    incomeSources: List<IncomeSource>,
    currencySymbol: String,
    isPrivacyVisible: Boolean = true
) {
    val totalIncome = incomeSources.sumOf { it.amount }
    val totalDebits = debits.sumOf { it.amount }
    val netSavings = totalIncome - totalDebits

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = IndSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, IndBorder),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Annual Financial Overview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = IndNavyHeader
            )

            HorizontalDivider(color = IndBorderSubtle)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Total Annual Income:", style = MaterialTheme.typography.bodyMedium, color = IndTextSecondary)
                Text(formatCurrency(totalIncome, currencySymbol, isPrivacyVisible), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = IndGreenDark)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Total Annual Expenses:", style = MaterialTheme.typography.bodyMedium, color = IndTextSecondary)
                Text(formatCurrency(totalDebits, currencySymbol, isPrivacyVisible), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = IndRedDark)
            }

            HorizontalDivider(color = IndBorderSubtle)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Net Savings / Remaining Balance:", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = IndNavyHeader)
                Text(
                    formatCurrency(netSavings, currencySymbol, isPrivacyVisible),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (netSavings >= 0) IndGreenDark else IndRedDark
                )
            }
        }
    }
}

/**
 * Utility to format currency values cleanly.
 */
private fun formatCurrency(amount: Double, symbol: String, isPrivacyVisible: Boolean = true): String {
    if (!isPrivacyVisible) return "$symbol★★★★"
    val formatter = NumberFormat.getNumberInstance(Locale.US)
    formatter.maximumFractionDigits = 2
    formatter.minimumFractionDigits = 0
    return "$symbol${formatter.format(amount)}"
}
