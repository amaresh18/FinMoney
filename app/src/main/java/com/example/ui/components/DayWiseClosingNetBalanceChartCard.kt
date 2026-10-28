package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DebitItem
import com.example.data.model.IncomeSource
import com.example.ui.FinMoneyViewModel
import com.example.ui.theme.*
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class DayNetBalancePoint(
    val day: Int,
    val dateLabel: String,
    val dailyInflow: Double,
    val dailyExpense: Double,
    val netDailyChange: Double,
    val closingNetBalance: Double,
    val transactionCount: Int
)

enum class NetBalanceGraphMode {
    CLOSING_BALANCE, // Cumulative day-wise closing balance curve
    DAILY_FLOW       // Day-by-day net inflow vs expense flow
}

enum class NetBalanceRangeFilter {
    MONTH_TO_DATE, // Day 1 to Today / Selected End Day
    FULL_MONTH     // Day 1 to DaysInMonth
}

/**
 * Premium Day-Wise Closing Net Balance Graph.
 * Visualizes day-by-day closing net balance progression across the month.
 * Provides interactive scrubbing/touch inspection, range filtering, privacy support,
 * and high/low balance metrics conforming to INDmoney M3 design standards.
 */
@Composable
fun DayWiseClosingNetBalanceChartCard(
    month: Int,
    year: Int,
    debits: List<DebitItem>,
    incomeSources: List<IncomeSource>,
    startingBalance: Double = 0.0,
    currencySymbol: String = "₹",
    isPrivacyVisible: Boolean = true,
    selectedDay: Int? = null,
    onSelectDay: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var graphMode by remember { mutableStateOf(NetBalanceGraphMode.CLOSING_BALANCE) }
    var rangeFilter by remember { mutableStateOf(NetBalanceRangeFilter.MONTH_TO_DATE) }
    var scrubbedDayIndex by remember { mutableStateOf<Int?>(null) }

    val monthName = remember(month) { FinMoneyViewModel.getMonthName(month) }
    val shortMonthName = remember(monthName) { monthName.take(3) }

    // Calendar maximum days
    val daysInMonth = remember(month, year) {
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, 1)
        cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    // Determine current day for MTD bounds
    val todayCalendar = remember { Calendar.getInstance() }
    val isCurrentMonthAndYear = remember(month, year) {
        todayCalendar.get(Calendar.MONTH) + 1 == month && todayCalendar.get(Calendar.YEAR) == year
    }
    val todayDay = if (isCurrentMonthAndYear) todayCalendar.get(Calendar.DAY_OF_MONTH).coerceIn(1, daysInMonth) else daysInMonth

    // Map daily inflows
    val dayInflows = remember(incomeSources, month, year, daysInMonth) {
        val map = mutableMapOf<Int, Double>()
        incomeSources.forEach { inc ->
            val day = if (inc.receivedDateTimestamp != null && inc.receivedDateTimestamp > 0) {
                val cal = Calendar.getInstance().apply { timeInMillis = inc.receivedDateTimestamp }
                val incMonth = cal.get(Calendar.MONTH) + 1
                val incYear = cal.get(Calendar.YEAR)
                if (incMonth == month && incYear == year) {
                    cal.get(Calendar.DAY_OF_MONTH)
                } else {
                    1
                }
            } else {
                1 // Default salary / main credit day
            }
            val validDay = day.coerceIn(1, daysInMonth)
            map[validDay] = (map[validDay] ?: 0.0) + inc.amount
        }
        map
    }

    // Map daily expenses (debits)
    val dayDebits = remember(debits, month, year, daysInMonth) {
        val amountMap = mutableMapOf<Int, Double>()
        val countMap = mutableMapOf<Int, Int>()
        debits.filter { it.month == month && it.year == year }.forEach { debit ->
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
            val validDay = day.coerceIn(1, daysInMonth)
            amountMap[validDay] = (amountMap[validDay] ?: 0.0) + debit.amount
            countMap[validDay] = (countMap[validDay] ?: 0) + 1
        }
        Pair(amountMap, countMap)
    }

    // Full series of daily points across the entire month
    val allMonthPoints = remember(dayInflows, dayDebits, startingBalance, daysInMonth, shortMonthName) {
        var runningBalance = startingBalance
        (1..daysInMonth).map { d ->
            val inflow = dayInflows[d] ?: 0.0
            val expense = dayDebits.first[d] ?: 0.0
            val count = (if (inflow > 0) 1 else 0) + (dayDebits.second[d] ?: 0)
            val netChange = inflow - expense
            runningBalance += netChange
            DayNetBalancePoint(
                day = d,
                dateLabel = "$shortMonthName $d",
                dailyInflow = inflow,
                dailyExpense = expense,
                netDailyChange = netChange,
                closingNetBalance = runningBalance,
                transactionCount = count
            )
        }
    }

    // Filter points based on MTD vs Full Month
    val activePoints = remember(allMonthPoints, rangeFilter, todayDay, isCurrentMonthAndYear) {
        if (rangeFilter == NetBalanceRangeFilter.MONTH_TO_DATE && isCurrentMonthAndYear) {
            allMonthPoints.take(todayDay.coerceAtLeast(1))
        } else {
            allMonthPoints
        }
    }

    // Keep scrubbed index in sync if selectedDay changes externally
    LaunchedEffect(selectedDay) {
        if (selectedDay != null && selectedDay in 1..activePoints.size) {
            scrubbedDayIndex = selectedDay - 1
        }
    }

    // Active inspection point
    val currentInspectionPoint = remember(activePoints, scrubbedDayIndex) {
        if (scrubbedDayIndex != null && scrubbedDayIndex in activePoints.indices) {
            activePoints[scrubbedDayIndex!!]
        } else {
            activePoints.lastOrNull() ?: DayNetBalancePoint(1, "$shortMonthName 1", 0.0, 0.0, 0.0, startingBalance, 0)
        }
    }

    // High & Low closing balances
    val peakPoint = remember(activePoints) { activePoints.maxByOrNull { it.closingNetBalance } }
    val lowPoint = remember(activePoints) { activePoints.minByOrNull { it.closingNetBalance } }
    val latestClosingBalance = remember(activePoints) { activePoints.lastOrNull()?.closingNetBalance ?: startingBalance }
    val isSurplus = latestClosingBalance >= 0

    // Animation progress
    val animProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "net_balance_chart_anim"
    )

    IndCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("day_wise_closing_net_balance_card")
    ) {
        // --- 1. Header Row (Responsive & Auto-adjusting for all screen sizes) ---
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isCompact = maxWidth < 420.dp
            if (isCompact) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(IndBlueLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = "Day-Wise Closing Balance",
                                tint = IndBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "DAY-WISE CLOSING NET BALANCE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = IndBlueDark,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$monthName $year daily liquidity tracking",
                                style = MaterialTheme.typography.bodySmall,
                                color = IndTextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Mode Toggle Chips (Closing Balance vs Daily Flow) - given dedicated breathing room
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = IndCardSecondary,
                            modifier = Modifier.padding(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val isClosing = graphMode == NetBalanceGraphMode.CLOSING_BALANCE
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isClosing) IndSurface else Color.Transparent)
                                        .clickable { graphMode = NetBalanceGraphMode.CLOSING_BALANCE }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "Balance",
                                        fontSize = 10.sp,
                                        fontWeight = if (isClosing) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isClosing) IndBlue else IndTextSecondary,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (!isClosing) IndSurface else Color.Transparent)
                                        .clickable { graphMode = NetBalanceGraphMode.DAILY_FLOW }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "Daily Flow",
                                        fontSize = 10.sp,
                                        fontWeight = if (!isClosing) FontWeight.Bold else FontWeight.Medium,
                                        color = if (!isClosing) IndBlue else IndTextSecondary,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(IndBlueLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = "Day-Wise Closing Balance",
                                tint = IndBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "DAY-WISE CLOSING NET BALANCE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = IndBlueDark,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$monthName $year daily liquidity tracking",
                                style = MaterialTheme.typography.bodySmall,
                                color = IndTextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Mode Toggle Chips (Closing Balance vs Daily Flow)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = IndCardSecondary,
                        modifier = Modifier.padding(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isClosing = graphMode == NetBalanceGraphMode.CLOSING_BALANCE
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isClosing) IndSurface else Color.Transparent)
                                    .clickable { graphMode = NetBalanceGraphMode.CLOSING_BALANCE }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "Balance",
                                    fontSize = 10.sp,
                                    fontWeight = if (isClosing) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isClosing) IndBlue else IndTextSecondary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (!isClosing) IndSurface else Color.Transparent)
                                    .clickable { graphMode = NetBalanceGraphMode.DAILY_FLOW }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "Daily Flow",
                                        fontSize = 10.sp,
                                        fontWeight = if (!isClosing) FontWeight.Bold else FontWeight.Medium,
                                        color = if (!isClosing) IndBlue else IndTextSecondary,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }

        Spacer(modifier = Modifier.height(14.dp))

        // --- 2. Prominent Key Metric & Status (Adaptive layout) ---
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isNarrowMetricRow = maxWidth < 340.dp
            if (isNarrowMetricRow) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val labelText = if (scrubbedDayIndex != null) {
                        "Closing Balance on ${currentInspectionPoint.dateLabel}"
                    } else if (rangeFilter == NetBalanceRangeFilter.MONTH_TO_DATE && isCurrentMonthAndYear) {
                        "Current Closing Net Balance (Day $todayDay)"
                    } else {
                        "Month-End Closing Net Balance"
                    }

                    Text(
                        text = labelText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = IndTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val displayAmount = if (!isPrivacyVisible) {
                        "$currencySymbol★★★★"
                    } else {
                        formatCurrencyWithSymbol(currentInspectionPoint.closingNetBalance, currencySymbol)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = displayAmount,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (currentInspectionPoint.closingNetBalance >= 0) IndNavyHeader else IndRed,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (currentInspectionPoint.closingNetBalance >= 0) IndGreenLight else IndRedLight
                        ) {
                            Text(
                                text = if (currentInspectionPoint.closingNetBalance >= 0) "SURPLUS" else "DEFICIT",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (currentInspectionPoint.closingNetBalance >= 0) IndGreenDark else IndRedDark,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    // Range Selector Pills (MTD vs Full Month)
                    if (isCurrentMonthAndYear) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = IndCardSecondary,
                            border = BorderStroke(1.dp, IndBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val isMtd = rangeFilter == NetBalanceRangeFilter.MONTH_TO_DATE
                                Text(
                                    text = "MTD (1–$todayDay)",
                                    fontSize = 9.sp,
                                    fontWeight = if (isMtd) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isMtd) Color.White else IndTextSecondary,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isMtd) IndBlue else Color.Transparent)
                                        .clickable {
                                            rangeFilter = NetBalanceRangeFilter.MONTH_TO_DATE
                                            scrubbedDayIndex = null
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                                Text(
                                    text = "Full ($daysInMonth d)",
                                    fontSize = 9.sp,
                                    fontWeight = if (!isMtd) FontWeight.Bold else FontWeight.Medium,
                                    color = if (!isMtd) Color.White else IndTextSecondary,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (!isMtd) IndBlue else Color.Transparent)
                                        .clickable {
                                            rangeFilter = NetBalanceRangeFilter.FULL_MONTH
                                            scrubbedDayIndex = null
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(end = 8.dp)
                    ) {
                        val labelText = if (scrubbedDayIndex != null) {
                            "Closing Balance on ${currentInspectionPoint.dateLabel}"
                        } else if (rangeFilter == NetBalanceRangeFilter.MONTH_TO_DATE && isCurrentMonthAndYear) {
                            "Current Closing Net Balance (Day $todayDay)"
                        } else {
                            "Month-End Closing Net Balance"
                        }

                        Text(
                            text = labelText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = IndTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val displayAmount = if (!isPrivacyVisible) {
                            "$currencySymbol★★★★"
                        } else {
                            formatCurrencyWithSymbol(currentInspectionPoint.closingNetBalance, currencySymbol)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = displayAmount,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (currentInspectionPoint.closingNetBalance >= 0) IndNavyHeader else IndRed,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (currentInspectionPoint.closingNetBalance >= 0) IndGreenLight else IndRedLight
                            ) {
                                Text(
                                    text = if (currentInspectionPoint.closingNetBalance >= 0) "SURPLUS" else "DEFICIT",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (currentInspectionPoint.closingNetBalance >= 0) IndGreenDark else IndRedDark,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    // Range Selector Pills (MTD vs Full Month)
                    if (isCurrentMonthAndYear) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = IndCardSecondary,
                            border = BorderStroke(1.dp, IndBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val isMtd = rangeFilter == NetBalanceRangeFilter.MONTH_TO_DATE
                                Text(
                                    text = "MTD (1–$todayDay)",
                                    fontSize = 9.sp,
                                    fontWeight = if (isMtd) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isMtd) Color.White else IndTextSecondary,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isMtd) IndBlue else Color.Transparent)
                                        .clickable {
                                            rangeFilter = NetBalanceRangeFilter.MONTH_TO_DATE
                                            scrubbedDayIndex = null
                                        }
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                                Text(
                                    text = "Full ($daysInMonth d)",
                                    fontSize = 9.sp,
                                    fontWeight = if (!isMtd) FontWeight.Bold else FontWeight.Medium,
                                    color = if (!isMtd) Color.White else IndTextSecondary,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (!isMtd) IndBlue else Color.Transparent)
                                        .clickable {
                                            rangeFilter = NetBalanceRangeFilter.FULL_MONTH
                                            scrubbedDayIndex = null
                                        }
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- 3. High & Low Stats Chips ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                color = IndCardSecondary,
                border = BorderStroke(0.5.dp, IndBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(end = 4.dp)
                    ) {
                        Text(
                            text = "Month High",
                            fontSize = 9.sp,
                            color = IndTextMuted,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                        val highText = if (!isPrivacyVisible) "$currencySymbol★★★★" else formatCurrencyWithSymbol(peakPoint?.closingNetBalance ?: 0.0, currencySymbol)
                        Text(
                            text = highText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndGreenDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (peakPoint != null) {
                        Surface(shape = RoundedCornerShape(4.dp), color = IndGreenLight) {
                            Text("D${peakPoint.day}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = IndGreenDark, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                color = IndCardSecondary,
                border = BorderStroke(0.5.dp, IndBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(end = 4.dp)
                    ) {
                        Text(
                            text = "Month Low",
                            fontSize = 9.sp,
                            color = IndTextMuted,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                        val lowText = if (!isPrivacyVisible) "$currencySymbol★★★★" else formatCurrencyWithSymbol(lowPoint?.closingNetBalance ?: 0.0, currencySymbol)
                        Text(
                            text = lowText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndRedDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (lowPoint != null) {
                        Surface(shape = RoundedCornerShape(4.dp), color = IndRedLight) {
                            Text("D${lowPoint.day}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = IndRedDark, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- 4. Interactive Canvas Chart ---
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFFAFCFF))
                .border(1.dp, IndBorderSubtle, RoundedCornerShape(12.dp))
                .testTag("day_wise_closing_net_balance_canvas")
        ) {
            val density = LocalDensity.current
            val totalWidthPx = constraints.maxWidth.toFloat()
            val totalHeightPx = constraints.maxHeight.toFloat()

            val leftPadPx = with(density) { 12.dp.toPx() }
            val rightPadPx = with(density) { 52.dp.toPx() } // Room for Y-axis currency marks
            val topPadPx = with(density) { 20.dp.toPx() }
            val bottomPadPx = with(density) { 32.dp.toPx() } // Room for X-axis day marks

            val chartPlotWidth = (totalWidthPx - leftPadPx - rightPadPx).coerceAtLeast(1f)
            val chartPlotHeight = (totalHeightPx - topPadPx - bottomPadPx).coerceAtLeast(1f)

            // Pointer input for dragging / tapping scrubber
            val pointerModifier = Modifier.pointerInput(activePoints.size, chartPlotWidth, leftPadPx) {
                detectTapGestures(
                    onPress = { offset ->
                        val touchX = offset.x
                        if (activePoints.isNotEmpty() && touchX >= leftPadPx) {
                            val fraction = ((touchX - leftPadPx) / chartPlotWidth).coerceIn(0f, 1f)
                            val idx = (fraction * (activePoints.size - 1)).roundToInt().coerceIn(0, activePoints.size - 1)
                            scrubbedDayIndex = idx
                        }
                    }
                )
            }.pointerInput(activePoints.size, chartPlotWidth, leftPadPx) {
                detectDragGestures(
                    onDrag = { change, _ ->
                        change.consume()
                        val touchX = change.position.x
                        if (activePoints.isNotEmpty() && touchX >= leftPadPx) {
                            val fraction = ((touchX - leftPadPx) / chartPlotWidth).coerceIn(0f, 1f)
                            val idx = (fraction * (activePoints.size - 1)).roundToInt().coerceIn(0, activePoints.size - 1)
                            scrubbedDayIndex = idx
                        }
                    }
                )
            }

            // Min and Max values for Y-axis
            val values = remember(activePoints, graphMode) {
                if (graphMode == NetBalanceGraphMode.CLOSING_BALANCE) {
                    activePoints.map { it.closingNetBalance }
                } else {
                    activePoints.map { it.netDailyChange }
                }
            }

            val rawMin = values.minOrNull() ?: 0.0
            val rawMax = values.maxOrNull() ?: 0.0
            val spread = (rawMax - rawMin).let { if (it <= 0.0) 1000.0 else it }
            val yMin = rawMin - spread * 0.12
            val yMax = rawMax + spread * 0.12
            val yRange = (yMax - yMin).coerceAtLeast(1.0)

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .then(pointerModifier)
            ) {
                if (activePoints.isEmpty()) return@Canvas

                val pointsCount = activePoints.size
                val stepX = if (pointsCount > 1) chartPlotWidth / (pointsCount - 1) else chartPlotWidth

                // Helper to map (index, value) to Canvas Offset
                fun getOffset(index: Int, value: Double): Offset {
                    val x = leftPadPx + index * stepX
                    val yNormalized = ((yMax - value) / yRange).toFloat().coerceIn(0f, 1f)
                    val y = topPadPx + (yNormalized * chartPlotHeight)
                    return Offset(x, y)
                }

                // --- A. Subtle Horizontal Gridlines & Y-Axis Labels ---
                val gridLevels = listOf(0.1f to yMax - spread * 0.12, 0.5f to (rawMin + rawMax) / 2.0, 0.9f to yMin + spread * 0.12)
                gridLevels.forEach { (frac, valAtLevel) ->
                    val yPos = topPadPx + (frac * chartPlotHeight)
                    drawLine(
                        color = Color(0xFFE2E8F0),
                        start = Offset(leftPadPx, yPos),
                        end = Offset(leftPadPx + chartPlotWidth, yPos),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )

                    // Draw Y-axis text label on right
                    val labelStr = if (!isPrivacyVisible) "★★" else formatCompactAxis(valAtLevel, currencySymbol)
                    drawIntoCanvas { canvas ->
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.parseColor("#94A3B8")
                            textSize = 24f
                            textAlign = android.graphics.Paint.Align.LEFT
                            isAntiAlias = true
                        }
                        canvas.nativeCanvas.drawText(labelStr, leftPadPx + chartPlotWidth + 10f, yPos + 8f, paint)
                    }
                }

                // --- B. Zero Baseline (if in range) ---
                if (yMin < 0.0 && yMax > 0.0) {
                    val zeroY = topPadPx + (((yMax - 0.0) / yRange).toFloat() * chartPlotHeight)
                    drawLine(
                        color = IndRed.copy(alpha = 0.5f),
                        start = Offset(leftPadPx, zeroY),
                        end = Offset(leftPadPx + chartPlotWidth, zeroY),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                    )
                }

                // --- C. Graph Content based on Mode ---
                if (graphMode == NetBalanceGraphMode.CLOSING_BALANCE) {
                    // Compute line coordinates
                    val coords = activePoints.mapIndexed { i, p ->
                        getOffset(i, p.closingNetBalance)
                    }

                    // Build smooth cubic bezier curve
                    val strokePath = Path()
                    val fillPath = Path()

                    val baselineY = topPadPx + chartPlotHeight

                    if (coords.isNotEmpty()) {
                        val firstY = baselineY + (coords.first().y - baselineY) * animProgress
                        strokePath.moveTo(coords.first().x, firstY)
                        fillPath.moveTo(coords.first().x, baselineY)
                        fillPath.lineTo(coords.first().x, firstY)

                        for (i in 0 until coords.size - 1) {
                            val p0 = coords[i]
                            val p1 = coords[i + 1]
                            val p0Y = baselineY + (p0.y - baselineY) * animProgress
                            val p1Y = baselineY + (p1.y - baselineY) * animProgress

                            val cx = (p0.x + p1.x) / 2f
                            strokePath.cubicTo(cx, p0Y, cx, p1Y, p1.x, p1Y)
                            fillPath.cubicTo(cx, p0Y, cx, p1Y, p1.x, p1Y)
                        }

                        val lastCoord = coords.last()
                        fillPath.lineTo(lastCoord.x, baselineY)
                        fillPath.close()

                        // Gradient Area Fill
                        val isCurvePositive = (activePoints.lastOrNull()?.closingNetBalance ?: 0.0) >= 0.0
                        val gradientBrush = Brush.verticalGradient(
                            colors = if (isCurvePositive) {
                                listOf(
                                    IndGreen.copy(alpha = 0.35f),
                                    IndGreenLight.copy(alpha = 0.15f),
                                    Color.Transparent
                                )
                            } else {
                                listOf(
                                    IndRed.copy(alpha = 0.32f),
                                    IndRedLight.copy(alpha = 0.12f),
                                    Color.Transparent
                                )
                            },
                            startY = topPadPx,
                            endY = baselineY
                        )
                        drawPath(path = fillPath, brush = gradientBrush)

                        // Main Stroke Line
                        val strokeColor = if (isCurvePositive) IndGreenDark else IndRedDark
                        drawPath(
                            path = strokePath,
                            color = strokeColor,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )

                        // Transaction Node Dots on active days
                        activePoints.forEachIndexed { i, pt ->
                            if (pt.transactionCount > 0) {
                                val dotCoord = coords[i]
                                val animatedY = baselineY + (dotCoord.y - baselineY) * animProgress
                                drawCircle(
                                    color = Color.White,
                                    radius = 3.5.dp.toPx(),
                                    center = Offset(dotCoord.x, animatedY)
                                )
                                drawCircle(
                                    color = strokeColor,
                                    radius = 2.dp.toPx(),
                                    center = Offset(dotCoord.x, animatedY)
                                )
                            }
                        }
                    }
                } else {
                    // DAILY FLOW MODE (Inflow & Expense bars/stems)
                    val baselineY = topPadPx + (((yMax - 0.0) / yRange).toFloat() * chartPlotHeight).coerceIn(topPadPx, topPadPx + chartPlotHeight)
                    val barWidth = (stepX * 0.5f).coerceIn(4f, 20f)

                    activePoints.forEachIndexed { i, pt ->
                        val x = leftPadPx + i * stepX
                        val netChange = pt.netDailyChange
                        if (abs(netChange) > 0.01) {
                            val targetY = getOffset(i, netChange).y
                            val animatedTargetY = baselineY + (targetY - baselineY) * animProgress
                            val barTop = minOf(baselineY, animatedTargetY)
                            val barHeight = abs(baselineY - animatedTargetY).coerceAtLeast(2.dp.toPx())
                            val isPos = netChange >= 0
                            val barColor = if (isPos) IndGreen else IndRed

                            drawRoundRect(
                                color = barColor,
                                topLeft = Offset(x - barWidth / 2f, barTop),
                                size = Size(barWidth, barHeight),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                            )
                        } else {
                            // Small neutral marker
                            drawCircle(
                                color = Color(0xFFCBD5E1),
                                radius = 2f,
                                center = Offset(x, baselineY)
                            )
                        }
                    }
                }

                // --- D. Scrubbing Indicator & Halo Dot ---
                val activeIdx = scrubbedDayIndex ?: if (activePoints.isNotEmpty()) activePoints.size - 1 else null
                if (activeIdx != null && activeIdx in activePoints.indices) {
                    val scrubPoint = activePoints[activeIdx]
                    val scrubValue = if (graphMode == NetBalanceGraphMode.CLOSING_BALANCE) scrubPoint.closingNetBalance else scrubPoint.netDailyChange
                    val coord = getOffset(activeIdx, scrubValue)

                    // Vertical dashed guideline
                    drawLine(
                        color = IndBlue.copy(alpha = 0.7f),
                        start = Offset(coord.x, topPadPx),
                        end = Offset(coord.x, topPadPx + chartPlotHeight),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    )

                    // Outer halo
                    drawCircle(
                        color = IndBlue.copy(alpha = 0.25f),
                        radius = 8.dp.toPx(),
                        center = coord
                    )
                    // Inner solid dot
                    drawCircle(
                        color = Color.White,
                        radius = 4.5.dp.toPx(),
                        center = coord
                    )
                    drawCircle(
                        color = IndBlue,
                        radius = 3.dp.toPx(),
                        center = coord
                    )
                }

                // --- E. X-Axis Day Labels ---
                val labelInterval = when {
                    pointsCount <= 10 -> 1
                    pointsCount <= 20 -> 3
                    else -> 5
                }

                for (i in 0 until pointsCount step labelInterval) {
                    val pt = activePoints[i]
                    val x = leftPadPx + i * stepX
                    drawIntoCanvas { canvas ->
                        val isHighlighted = activeIdx == i
                        val paint = android.graphics.Paint().apply {
                            color = if (isHighlighted) android.graphics.Color.parseColor("#1A56DB") else android.graphics.Color.parseColor("#64748B")
                            textSize = if (isHighlighted) 26f else 22f
                            isFakeBoldText = isHighlighted
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                        }
                        canvas.nativeCanvas.drawText("D${pt.day}", x, topPadPx + chartPlotHeight + with(density) { 20.dp.toPx() }, paint)
                    }
                }

                // Always ensure the last day is labeled if not already drawn
                if ((pointsCount - 1) % labelInterval != 0 && pointsCount > 0) {
                    val lastPt = activePoints.last()
                    val x = leftPadPx + (pointsCount - 1) * stepX
                    drawIntoCanvas { canvas ->
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.parseColor("#64748B")
                            textSize = 22f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                        }
                        canvas.nativeCanvas.drawText("D${lastPt.day}", x, topPadPx + chartPlotHeight + with(density) { 20.dp.toPx() }, paint)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- 5. Interactive Inspection Details Card ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = if (scrubbedDayIndex != null) IndBlueLight.copy(alpha = 0.5f) else IndCardSecondary,
            border = BorderStroke(1.dp, if (scrubbedDayIndex != null) IndBlue.copy(alpha = 0.3f) else IndBorderSubtle)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (scrubbedDayIndex != null) IndBlue else IndTextSecondary
                        ) {
                            Text(
                                text = "D${currentInspectionPoint.day}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = "${currentInspectionPoint.dateLabel}, $year",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = IndNavyHeader
                        )
                        if (currentInspectionPoint.transactionCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = IndSurface,
                                border = BorderStroke(0.5.dp, IndBorder)
                            ) {
                                Text(
                                    text = "${currentInspectionPoint.transactionCount} txn",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = IndTextSecondary,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    if (scrubbedDayIndex != null) {
                        Text(
                            text = "Reset ✕",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndBlue,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { scrubbedDayIndex = null }
                                .padding(4.dp)
                        )
                    } else {
                        Text(
                            text = "Tap curve to scrub",
                            fontSize = 10.sp,
                            color = IndTextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Day Inflow
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Day Inflow", fontSize = 10.sp, color = IndTextSecondary)
                        val inText = if (!isPrivacyVisible) "$currencySymbol★★★★" else "+${formatCurrencyWithSymbol(currentInspectionPoint.dailyInflow, currencySymbol)}"
                        Text(
                            text = inText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (currentInspectionPoint.dailyInflow > 0) IndGreenDark else IndTextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Day Expenses
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Day Outflow", fontSize = 10.sp, color = IndTextSecondary)
                        val outText = if (!isPrivacyVisible) "$currencySymbol★★★★" else "-${formatCurrencyWithSymbol(currentInspectionPoint.dailyExpense, currencySymbol)}"
                        Text(
                            text = outText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (currentInspectionPoint.dailyExpense > 0) IndRed else IndTextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Day Net Change
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("Net Daily Change", fontSize = 10.sp, color = IndTextSecondary)
                        val netSign = if (currentInspectionPoint.netDailyChange > 0) "+" else ""
                        val netText = if (!isPrivacyVisible) {
                            "$currencySymbol★★★★"
                        } else {
                            "$netSign${formatCurrencyWithSymbol(currentInspectionPoint.netDailyChange, currencySymbol)}"
                        }
                        Text(
                            text = netText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = when {
                                currentInspectionPoint.netDailyChange > 0 -> IndGreenDark
                                currentInspectionPoint.netDailyChange < 0 -> IndRed
                                else -> IndTextMuted
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // If tapped and has callback
                if (onSelectDay != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(IndSurface)
                            .border(1.dp, IndBorder, RoundedCornerShape(6.dp))
                            .clickable { onSelectDay(currentInspectionPoint.day) }
                            .padding(vertical = 6.dp, horizontal = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null, tint = IndBlue, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Filter Ledger to Day ${currentInspectionPoint.day}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndBlue
                        )
                    }
                }
            }
        }
    }
}

private fun formatCurrencyWithSymbol(amount: Double, symbol: String): String {
    val isNegative = amount < 0
    val absVal = abs(amount)
    val formatted = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        maximumFractionDigits = 0
        minimumFractionDigits = 0
    }.format(absVal)
    return if (isNegative) "-$symbol$formatted" else "$symbol$formatted"
}

private fun formatCompactAxis(amount: Double, symbol: String): String {
    val isNegative = amount < 0
    val absVal = abs(amount)
    val text = when {
        absVal >= 10_000_000 -> "%.1fCr".format(Locale.US, absVal / 10_000_000.0)
        absVal >= 100_000 -> "%.1fL".format(Locale.US, absVal / 100_000.0)
        absVal >= 1_000 -> "%.0fk".format(Locale.US, absVal / 1_000.0)
        else -> "%.0f".format(Locale.US, absVal)
    }
    return if (isNegative) "-$symbol$text" else "$symbol$text"
}
