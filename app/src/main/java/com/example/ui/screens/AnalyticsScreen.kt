package com.example.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CategorySummary
import com.example.data.model.DebitItem
import com.example.data.model.MonthlyFinanceSummary
import com.example.data.model.SalaryRecord
import com.example.ui.FinMoneyViewModel
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun AnalyticsScreen(
    viewModel: FinMoneyViewModel,
    onBackToDashboard: () -> Unit
) {
    val month by viewModel.currentMonth.collectAsStateWithLifecycle()
    val year by viewModel.currentYear.collectAsStateWithLifecycle()
    val currencySymbol by viewModel.currencySymbol.collectAsStateWithLifecycle()
    val isFinancialSafetyVisible by viewModel.isFinancialSafetyVisible.collectAsStateWithLifecycle()
    val summaryRaw by viewModel.monthlySummary.collectAsStateWithLifecycle()
    val summary = summaryRaw ?: MonthlyFinanceSummary()
    val salaryRecord by viewModel.currentSalaryRecord.collectAsStateWithLifecycle()
    val allDebits by viewModel.allDebits.collectAsStateWithLifecycle()
    val selectedSpecificDay by viewModel.selectedSpecificDay.collectAsStateWithLifecycle()

    val monthName = FinMoneyViewModel.getMonthName(month)

    var selectedCategoryForDrillDown by remember { mutableStateOf<CategorySummary?>(null) }
    var showIncomeDrillDown by remember { mutableStateOf(false) }

    val currentMonthDebits = remember(allDebits, month, year) {
        allDebits.filter { it.month == month && it.year == year }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppTheme.dimensions.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(AppTheme.dimensions.medium),
        contentPadding = PaddingValues(top = AppTheme.dimensions.medium, bottom = 96.dp)
    ) {
        // Top Back & Header Bar
        item {
            var showCalendarPicker by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(IndSurface)
                    .border(1.dp, IndBorder, RoundedCornerShape(14.dp))
                    .clickable { showCalendarPicker = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackToDashboard) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Dashboard", tint = IndNavyHeader)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$monthName $year Analytics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = IndTextPrimary
                    )
                    Text(
                        text = "Google Calendar Date Filter ▾",
                        style = MaterialTheme.typography.labelSmall,
                        color = IndBlue,
                        fontSize = 10.sp
                    )
                }
                IconButton(onClick = { viewModel.nextMonth() }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Next Month", tint = IndTextSecondary)
                }
            }

            if (showCalendarPicker) {
                IndGoogleCalendarDatePicker(
                    currentMonth = month,
                    currentYear = year,
                    currentSelectedDay = selectedSpecificDay,
                    onDismiss = { showCalendarPicker = false },
                    onSelectDate = { m, y, d ->
                        viewModel.setMonthYear(m, y, d)
                        showCalendarPicker = false
                    }
                )
            }
        }

        // Summary Card
        item {
            IndCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ANALYTICS FINANCIAL HEALTH",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = IndBlueDark,
                        fontSize = 11.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = IndGreenLight,
                        modifier = Modifier.clickable { showIncomeDrillDown = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text("Income Breakdown ▾", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = IndGreenDark)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.clickable { showIncomeDrillDown = true }) {
                        Text("Total Income", style = MaterialTheme.typography.labelSmall, color = IndTextSecondary)
                        Text(
                            text = if (!isFinancialSafetyVisible) "$currencySymbol★★★★★★" else "$currencySymbol${"%,.0f".format(summary.totalIncome)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = IndGreenDark
                        )
                    }
                    Column {
                        Text("Total Outflow", style = MaterialTheme.typography.labelSmall, color = IndTextSecondary)
                        Text("$currencySymbol${"%,.0f".format(summary.totalDebits)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = IndRed)
                    }
                    Column {
                        Text("Net Savings", style = MaterialTheme.typography.labelSmall, color = IndTextSecondary)
                        Text(
                            text = if (!isFinancialSafetyVisible) "$currencySymbol★★★★★★" else "$currencySymbol${"%,.0f".format(summary.netFlexibleSpending)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (summary.netFlexibleSpending >= 0) IndTextPrimary else IndRed
                        )
                    }
                }
            }
        }

        // Interactive Trend Chart
        item {
            AnimatedIncomeVsExpenseAnalyticsCard(summary = summary, currencySymbol = currencySymbol, isPrivacyVisible = isFinancialSafetyVisible)
        }

        // Donut Chart Card
        item {
            DashboardPieChartCard(
                categorySummaries = summary.categoryBreakdown,
                currencySymbol = currencySymbol,
                isPrivacyVisible = isFinancialSafetyVisible
            )
        }

        // Spend by Category Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Spend by Category (${summary.categoryBreakdown.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = IndTextPrimary
                )
                Text(
                    text = "Tap to inspect items",
                    style = MaterialTheme.typography.labelSmall,
                    color = IndTextMuted
                )
            }
        }

        if (summary.categoryBreakdown.isEmpty()) {
            item {
                IndCard(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No category expenses logged for $monthName $year.", color = IndTextSecondary)
                    }
                }
            }
        } else {
            items(summary.categoryBreakdown) { cat ->
                val categoryShare = if (summary.totalDebits > 0) (cat.totalAmount / summary.totalDebits).toFloat() else 0f
                IndCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { selectedCategoryForDrillDown = cat }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                CategoryIconBadge(categoryKey = cat.iconKey)
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(cat.categoryName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = IndTextPrimary)
                                        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = "Drill down", tint = IndTextMuted, modifier = Modifier.size(12.dp))
                                    }
                                    Text(
                                        text = "${cat.itemCount} items • ${cat.paidCount} paid • ${(categoryShare * 100).toInt()}% of outflow",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = IndTextSecondary
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "$currencySymbol${"%,.0f".format(cat.totalAmount)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = IndNavyHeader
                                )
                                if (cat.yetToPayAmount > 0) {
                                    Text(
                                        text = "$currencySymbol${"%,.0f".format(cat.yetToPayAmount)} pending",
                                        fontSize = 10.sp,
                                        color = IndRed,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    Text(
                                        text = "All Paid ✓",
                                        fontSize = 10.sp,
                                        color = IndGreenDark,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        LinearProgressIndicator(
                            progress = { categoryShare.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = try { Color(android.graphics.Color.parseColor(cat.colorHex)) } catch (e: Exception) { IndBlue },
                            trackColor = IndCardSecondary
                        )
                    }
                }
            }
        }
    }

    // Category Drill-Down Dialog
    selectedCategoryForDrillDown?.let { cat ->
        val matchingDebits = remember(currentMonthDebits, cat) {
            currentMonthDebits.filter {
                it.category.equals(cat.categoryKey, ignoreCase = true) ||
                it.customCategoryName.equals(cat.categoryName, ignoreCase = true) ||
                it.category.equals(cat.categoryName, ignoreCase = true)
            }
        }

        CategoryDrillDownDialog(
            categorySummary = cat,
            debitItems = matchingDebits,
            monthName = monthName,
            year = year,
            currencySymbol = currencySymbol,
            onDismiss = { selectedCategoryForDrillDown = null }
        )
    }

    // Income Drill-Down Dialog
    if (showIncomeDrillDown) {
        IncomeDrillDownDialog(
            salaryRecord = salaryRecord,
            incomeSources = summary.incomeSources,
            totalIncome = summary.totalIncome,
            monthName = monthName,
            year = year,
            currencySymbol = currencySymbol,
            onDismiss = { showIncomeDrillDown = false }
        )
    }
}

@Composable
fun AnimatedIncomeVsExpenseAnalyticsCard(
    summary: MonthlyFinanceSummary,
    currencySymbol: String,
    isPrivacyVisible: Boolean
) {
    IndCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "INCOME VS EXPENSE TREND",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = IndBlueDark,
                    fontSize = 11.sp
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = IndBlueLight
                ) {
                    Text(
                        text = "LIVE TREND",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = IndBlue,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Income Inflow", fontSize = 11.sp, color = IndTextSecondary)
                    Text(
                        text = if (!isPrivacyVisible) "$currencySymbol★★★★★★" else "$currencySymbol${"%,.0f".format(summary.totalIncome)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = IndGreenDark
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Expense Outflow", fontSize = 11.sp, color = IndTextSecondary)
                    Text(
                        text = "$currencySymbol${"%,.0f".format(summary.totalDebits)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = IndRed
                    )
                }
            }

            val maxVal = maxOf(summary.totalIncome, summary.totalDebits, 1.0)
            val incomeRatio = (summary.totalIncome / maxVal).toFloat().coerceIn(0.1f, 1f)
            val expenseRatio = (summary.totalDebits / maxVal).toFloat().coerceIn(0.1f, 1f)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Income", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(60.dp))
                    LinearProgressIndicator(
                        progress = { incomeRatio },
                        modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = IndGreenDark,
                        trackColor = IndCardSecondary
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Expense", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(60.dp))
                    LinearProgressIndicator(
                        progress = { expenseRatio },
                        modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = IndRed,
                        trackColor = IndCardSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryDrillDownDialog(
    categorySummary: CategorySummary,
    debitItems: List<DebitItem>,
    monthName: String,
    year: Int,
    currencySymbol: String,
    onDismiss: () -> Unit
) {
    var filterMode by remember { mutableStateOf("ALL") }

    val filteredItems = remember(debitItems, filterMode) {
        when (filterMode) {
            "PAID" -> debitItems.filter { it.isPaid }
            "PENDING" -> debitItems.filter { !it.isPaid }
            else -> debitItems
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = IndSurface)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CategoryIconBadge(categoryKey = categorySummary.iconKey)
                            Column {
                                Text(
                                    text = categorySummary.categoryName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = IndTextPrimary
                                )
                                Text(
                                    text = "$monthName $year Drill-Down",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = IndTextSecondary
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = IndTextSecondary)
                        }
                    }
                }

                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = IndCardSecondary,
                        border = androidx.compose.foundation.BorderStroke(1.dp, IndBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("TOTAL CATEGORY SPEND", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = IndTextMuted)
                                Text(
                                    text = "$currencySymbol${"%,.0f".format(categorySummary.totalAmount)}",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = IndNavyHeader
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("STATUS BREAKDOWN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = IndTextMuted)
                                Text("Paid: $currencySymbol${"%,.0f".format(categorySummary.paidAmount)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IndGreenDark)
                                Text("Pending: $currencySymbol${"%,.0f".format(categorySummary.yetToPayAmount)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IndRed)
                            }
                        }
                    }
                }

                if (filteredItems.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No items found under this filter.", color = IndTextSecondary, fontSize = 12.sp)
                        }
                    }
                } else {
                    items(filteredItems, key = { it.id }) { item ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = IndSurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, IndBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = IndTextPrimary)
                                    Text(
                                        text = "Due Day ${item.dueDateDay}",
                                        fontSize = 11.sp,
                                        color = IndTextSecondary
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "$currencySymbol${"%,.0f".format(item.amount)}",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                        color = if (item.isPaid) IndGreenDark else IndRed
                                    )
                                    Text(
                                        text = if (item.isPaid) "✓ Paid" else "⏳ Pending",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (item.isPaid) IndGreenDark else Color(0xFFB45309)
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = IndNavyHeader),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun IncomeDrillDownDialog(
    salaryRecord: SalaryRecord?,
    incomeSources: List<com.example.data.model.IncomeSource>,
    totalIncome: Double,
    monthName: String,
    year: Int,
    currencySymbol: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = IndSurface)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Income & Credits Drill-Down", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = IndTextPrimary)
                            Text("$monthName $year Inflow Breakdown", style = MaterialTheme.typography.bodySmall, color = IndTextSecondary)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = IndTextSecondary)
                        }
                    }
                }

                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = IndGreenLight,
                        border = androidx.compose.foundation.BorderStroke(1.dp, IndGreen.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("TOTAL MONTHLY INFLOW", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = IndGreenDark)
                            Text("$currencySymbol${"%,.0f".format(totalIncome)}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = IndGreenDark)
                        }
                    }
                }

                items(incomeSources) { src ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = IndSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, IndBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(src.title.ifBlank { src.name }, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = IndTextPrimary)
                                Text(src.category, fontSize = 11.sp, color = IndTextSecondary)
                            }
                            Text(
                                text = "$currencySymbol${"%,.0f".format(src.amount)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = IndGreenDark
                            )
                        }
                    }
                }

                item {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = IndNavyHeader),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
