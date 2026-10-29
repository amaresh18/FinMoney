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
import androidx.compose.ui.text.style.TextOverflow
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
    val month by viewModel.analyticsMonth.collectAsStateWithLifecycle()
    val year by viewModel.analyticsYear.collectAsStateWithLifecycle()
    val currencySymbol by viewModel.currencySymbol.collectAsStateWithLifecycle()
    val isFinancialSafetyVisible by viewModel.isFinancialSafetyVisible.collectAsStateWithLifecycle()
    val summaryRaw by viewModel.analyticsMonthlySummary.collectAsStateWithLifecycle()
    val summary = summaryRaw ?: MonthlyFinanceSummary()
    val salaryRecord by viewModel.analyticsSalaryRecord.collectAsStateWithLifecycle()
    val allDebits by viewModel.allDebits.collectAsStateWithLifecycle()
    val selectedSpecificDay by viewModel.analyticsSelectedSpecificDay.collectAsStateWithLifecycle()
    val selectedStartDay by viewModel.analyticsSelectedStartDay.collectAsStateWithLifecycle()
    val selectedEndDay by viewModel.analyticsSelectedEndDay.collectAsStateWithLifecycle()
    val reconciliation by viewModel.analyticsReconciliation.collectAsStateWithLifecycle()

    val startingBankBalance = remember(reconciliation) {
        val recon = reconciliation
        if (recon != null && recon.bankBalancesJson.isNotBlank() && recon.bankBalancesJson != "[]") {
            com.example.data.repository.FinMoneyRepository.parseBankBalances(recon.bankBalancesJson).sumOf { it.startingBalance }
        } else {
            0.0
        }
    }

    val monthName = FinMoneyViewModel.getMonthName(month)

    var selectedCategoryForDrillDown by remember { mutableStateOf<CategorySummary?>(null) }
    var showIncomeDrillDown by remember { mutableStateOf(false) }

    var editingDebitItem by remember { mutableStateOf<DebitItem?>(null) }
    var expensePendingDelete by remember { mutableStateOf<DebitItem?>(null) }
    var showAddCustomCategoryDialog by remember { mutableStateOf(false) }
    var showManageCustomCategoriesDialog by remember { mutableStateOf(false) }
    var editingCustomCategory by remember { mutableStateOf<com.example.data.model.CustomCategory?>(null) }
    var pendingCustomCategoryName by remember { mutableStateOf<String?>(null) }
    var pendingCustomCategoryRowId by remember { mutableStateOf<String?>(null) }
    val customCategories by viewModel.customCategories.collectAsStateWithLifecycle()
    val customExpenseTags by viewModel.customExpenseTags.collectAsStateWithLifecycle()

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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f, fill = false).padding(horizontal = 4.dp)
                ) {
                    val dateLabel = remember(monthName, year, selectedStartDay, selectedEndDay, selectedSpecificDay) {
                        when {
                            selectedStartDay != null && selectedEndDay != null && selectedStartDay != selectedEndDay -> {
                                val minD = minOf(selectedStartDay!!, selectedEndDay!!)
                                val maxD = maxOf(selectedStartDay!!, selectedEndDay!!)
                                "$monthName $minD–$maxD, $year"
                            }
                            selectedStartDay != null -> "$monthName $selectedStartDay, $year"
                            selectedSpecificDay != null -> "$monthName $selectedSpecificDay, $year"
                            else -> "$monthName $year"
                        }
                    }
                    Text(
                        text = "$dateLabel Analytics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = IndTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.toggleFinancialSafetyVisible() }) {
                        Icon(
                            if (isFinancialSafetyVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            "Privacy",
                            tint = IndBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = { viewModel.previousAnalyticsMonth() }) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous Month", tint = IndTextSecondary)
                    }
                    IconButton(onClick = { viewModel.nextAnalyticsMonth() }) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Next Month", tint = IndTextSecondary)
                    }
                }
            }

            if (showCalendarPicker) {
                IndGoogleCalendarDatePicker(
                    currentMonth = month,
                    currentYear = year,
                    currentSelectedDay = selectedSpecificDay,
                    currentStartDay = selectedStartDay,
                    currentEndDay = selectedEndDay,
                    onDismiss = { showCalendarPicker = false },
                    onSelectDate = { m, y, d ->
                        viewModel.setAnalyticsMonthYear(m, y, d)
                        showCalendarPicker = false
                    },
                    onSelectDateRange = { m, y, s, e ->
                        viewModel.setAnalyticsDateRangeFilter(m, y, s, e)
                        showCalendarPicker = false
                    }
                )
            }
        }

        // Lemon Account P&L Style Budget Daily Limit Calendar View
        item {
            BudgetCalendarView(
                selectedMonth = month,
                selectedYear = year,
                selectedDay = selectedSpecificDay,
                currencySymbol = currencySymbol,
                isPrivacyVisible = isFinancialSafetyVisible,
                debits = allDebits,
                incomeSources = summary.incomeSources,
                monthlyBudgetPreference = summary.totalIncome,
                onSelectDate = { m, y, d -> viewModel.setAnalyticsMonthYear(m, y, d) },
                onPreviousMonth = { viewModel.previousAnalyticsMonth() },
                onNextMonth = { viewModel.nextAnalyticsMonth() },
                onAddDebitForDate = null,
                onEditDebit = null,
                onDeleteDebit = null,
                modifier = Modifier.fillMaxWidth()
            )
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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showIncomeDrillDown = true },
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text("Total Income", style = MaterialTheme.typography.labelSmall, color = IndTextSecondary)
                        Text(
                            text = if (!isFinancialSafetyVisible) "$currencySymbol★★★★" else "$currencySymbol${"%,.0f".format(summary.totalIncome)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = IndGreenDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Total Outflow", style = MaterialTheme.typography.labelSmall, color = IndTextSecondary)
                        Text(
                            text = if (!isFinancialSafetyVisible) "$currencySymbol★★★★" else "$currencySymbol${"%,.0f".format(summary.totalDebits)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = IndRed,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text("Net Savings", style = MaterialTheme.typography.labelSmall, color = IndTextSecondary)
                        Text(
                            text = if (!isFinancialSafetyVisible) "$currencySymbol★★★★" else "$currencySymbol${"%,.0f".format(summary.netFlexibleSpending)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (summary.netFlexibleSpending >= 0) IndNavyHeader else IndRed,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Day-Wise Closing Net Balance Graph
        item {
            DayWiseClosingNetBalanceChartCard(
                month = month,
                year = year,
                debits = allDebits,
                incomeSources = summary.incomeSources,
                startingBalance = startingBankBalance,
                currencySymbol = currencySymbol,
                isPrivacyVisible = isFinancialSafetyVisible,
                selectedDay = selectedSpecificDay,
                onSelectDay = { day ->
                    viewModel.setAnalyticsSelectedSpecificDay(day)
                }
            )
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
                            Row(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .padding(end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CategoryIconBadge(
                                    categoryKey = cat.categoryName,
                                    storedIconName = categorySummaryStoredIcon(cat),
                                    customColor = customCategoryTint(cat.colorHex)
                                )
                                Column {
                                    Text(
                                        text = cat.categoryName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = IndTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${cat.itemCount} items • ${cat.paidCount} paid • ${(categoryShare * 100).toInt()}% of outflow",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = IndTextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (!isFinancialSafetyVisible) "$currencySymbol★★★★" else "$currencySymbol${"%,.0f".format(cat.totalAmount)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = IndNavyHeader,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                if (cat.yetToPayAmount > 0) {
                                    Text(
                                        text = if (!isFinancialSafetyVisible) "★★★★ pending" else "$currencySymbol${"%,.0f".format(cat.yetToPayAmount)} pending",
                                        fontSize = 10.sp,
                                        color = IndRed,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                } else {
                                    Text(
                                        text = "All Paid ✓",
                                        fontSize = 10.sp,
                                        color = IndGreenDark,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
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
            currentMonthDebits.filter { debitMatchesCategorySummary(it, cat) }
        }

        CategoryDrillDownDialog(
            categorySummary = cat,
            debitItems = matchingDebits,
            monthName = monthName,
            year = year,
            month = month,
            currencySymbol = currencySymbol,
            onDismiss = { selectedCategoryForDrillDown = null },
            onEditItem = null,
            onDeleteItem = null,
            onTogglePaid = null,
            onToggleCreditCard = null,
            onToggleRecurring = null
        )
    }

    if (editingDebitItem != null) {
        val item = editingDebitItem!!
        QuickMultiAddSheet(
            currencySymbol = currencySymbol,
            todayDay = item.dueDateDay.coerceIn(1, 28),
            month = month,
            year = year,
            customCategories = customCategories,
            customExpenseTags = customExpenseTags,
            allHistoricalDebits = allDebits,
            editingDebit = item,
            onSaveCustomExpenseTag = { name, icon, color ->
                viewModel.addCustomExpenseTag(name, icon, color)
            },
            onDismiss = { editingDebitItem = null },
            onSaveBatch = { batch ->
                batch.firstOrNull()?.let { viewModel.updateDebit(it) }
                editingDebitItem = null
            },
            pendingCustomCategoryName = pendingCustomCategoryName,
            pendingCustomCategoryRowId = pendingCustomCategoryRowId,
            onPendingCustomCategoryApplied = {
                pendingCustomCategoryName = null
                pendingCustomCategoryRowId = null
            },
            onOpenAddCustomCategory = { rowId ->
                pendingCustomCategoryRowId = rowId
                showAddCustomCategoryDialog = true
            }
        )
    }

    if (showAddCustomCategoryDialog || editingCustomCategory != null) {
        AddCustomCategoryDialog(
            onDismiss = {
                showAddCustomCategoryDialog = false
                editingCustomCategory = null
            },
            editing = editingCustomCategory,
            onManageExisting = if (editingCustomCategory == null) {
                {
                    showAddCustomCategoryDialog = false
                    showManageCustomCategoriesDialog = true
                }
            } else null,
            onSave = { name, icon, color ->
                if (editingCustomCategory != null) {
                    viewModel.updateCustomCategory(editingCustomCategory!!.id, name, icon, color)
                    editingCustomCategory = null
                } else {
                    viewModel.addCustomCategory(name, icon, color)
                    pendingCustomCategoryName = name
                    showAddCustomCategoryDialog = false
                }
            }
        )
    }

    if (showManageCustomCategoriesDialog) {
        ManageCustomCategoriesDialog(
            categories = customCategories,
            onDismiss = { showManageCustomCategoriesDialog = false },
            onEdit = { cat ->
                showManageCustomCategoriesDialog = false
                editingCustomCategory = cat
            },
            onDelete = { cat -> viewModel.deleteCustomCategory(cat) },
            expenseCountForCategory = { name -> viewModel.countExpensesUsingCustomCategory(name) }
        )
    }

    expensePendingDelete?.let { item ->
        ConfirmDeleteDialog(
            title = "Delete expense?",
            message = "Remove \"${item.title.ifBlank { debitCategoryDisplayLabel(item) }}\"? This cannot be undone.",
            onDismiss = { expensePendingDelete = null },
            onConfirm = {
                viewModel.deleteDebit(item)
                expensePendingDelete = null
                if (editingDebitItem?.id == item.id) editingDebitItem = null
            }
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
                Column(modifier = Modifier.weight(1f)) {
                    Text("Income Inflow", fontSize = 11.sp, color = IndTextSecondary)
                    Text(
                        text = if (!isPrivacyVisible) "$currencySymbol★★★★" else "$currencySymbol${"%,.0f".format(summary.totalIncome)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = IndGreenDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text("Expense Outflow", fontSize = 11.sp, color = IndTextSecondary)
                    Text(
                        text = if (!isPrivacyVisible) "$currencySymbol★★★★" else "$currencySymbol${"%,.0f".format(summary.totalDebits)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = IndRed,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            val maxVal = maxOf(summary.totalIncome, summary.totalDebits, 1.0)
            val incomeRatio = (summary.totalIncome / maxVal).toFloat().coerceIn(0.1f, 1f)
            val expenseRatio = (summary.totalDebits / maxVal).toFloat().coerceIn(0.1f, 1f)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Income", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.widthIn(min = 54.dp, max = 80.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    LinearProgressIndicator(
                        progress = { incomeRatio },
                        modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = IndGreenDark,
                        trackColor = IndCardSecondary
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Expense", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.widthIn(min = 54.dp, max = 80.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    month: Int = 10,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onEditItem: ((DebitItem) -> Unit)? = null,
    onDeleteItem: ((DebitItem) -> Unit)? = null,
    onTogglePaid: ((DebitItem) -> Unit)? = null,
    onToggleCreditCard: ((DebitItem) -> Unit)? = null,
    onToggleRecurring: ((DebitItem) -> Unit)? = null
) {
    var filterMode by remember { mutableStateOf("ALL") }

    val liveTotalAmount = remember(debitItems) { debitItems.sumOf { it.amount } }
    val livePaidAmount = remember(debitItems) { debitItems.filter { it.isPaid }.sumOf { it.amount } }
    val livePendingAmount = remember(liveTotalAmount, livePaidAmount) { (liveTotalAmount - livePaidAmount).coerceAtLeast(0.0) }

    val filteredItems = remember(debitItems, filterMode, month, year) {
        val list = when (filterMode) {
            "PAID" -> debitItems.filter { it.isPaid }
            "PENDING" -> debitItems.filter { !it.isPaid }
            else -> debitItems
        }
        list.sortedWith(compareDebitsByRecency(month, year))
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(0.95f)
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
                            CategoryIconBadge(
                                categoryKey = categorySummary.categoryName,
                                storedIconName = categorySummaryStoredIcon(categorySummary),
                                customColor = customCategoryTint(categorySummary.colorHex)
                            )
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
                                    text = "$currencySymbol${"%,.0f".format(liveTotalAmount)}",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = IndNavyHeader
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("STATUS BREAKDOWN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = IndTextMuted)
                                Text("Paid: $currencySymbol${"%,.0f".format(livePaidAmount)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IndGreenDark)
                                Text("Pending: $currencySymbol${"%,.0f".format(livePendingAmount)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IndRed)
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "PENDING" to "Pending (${debitItems.count { !it.isPaid }})",
                            "PAID" to "Paid (${debitItems.count { it.isPaid }})"
                        ).forEach { (mode, label) ->
                            FilterChip(
                                selected = filterMode == mode,
                                onClick = {
                                    filterMode = if (filterMode == mode) "ALL" else mode
                                },
                                label = { Text(label, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
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
                            Column(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .then(if (onEditItem != null) Modifier.clickable { onEditItem(item) } else Modifier)
                                    ) {
                                        Text(item.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = IndTextPrimary)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "Transaction Date: Day ${item.dueDateDay}",
                                                fontSize = 11.sp,
                                                color = IndTextSecondary
                                            )
                                            if (item.isPaidViaCreditCard) {
                                                Text("• 💳 CC", fontSize = 11.sp, color = Color(0xFF7C3AED), fontWeight = FontWeight.SemiBold)
                                            }
                                            if (item.isRecurring) {
                                                Text("• 🔁 Recurring", fontSize = 11.sp, color = Color(0xFF0284C7), fontWeight = FontWeight.SemiBold)
                                            }
                                        }
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

                                if (onEditItem != null || onDeleteItem != null || onTogglePaid != null) {
                                    HorizontalDivider(color = IndBorderSubtle, thickness = 0.5.dp)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (onTogglePaid != null) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (item.isPaid) IndGreenLight else IndAmberLight,
                                                    modifier = Modifier.clickable { onTogglePaid(item) }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                    ) {
                                                        RadioButton(
                                                            selected = item.isPaid,
                                                            onClick = { onTogglePaid(item) },
                                                            colors = RadioButtonDefaults.colors(
                                                                selectedColor = IndGreenDark,
                                                                unselectedColor = Color(0xFFB45309)
                                                            ),
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Text(
                                                            text = if (item.isPaid) "Paid" else "Yet to Pay",
                                                            fontSize = 9.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (item.isPaid) IndGreenDark else Color(0xFFB45309)
                                                        )
                                                    }
                                                }
                                            }

                                            if (onToggleCreditCard != null) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (item.isPaidViaCreditCard) Color(0xFFEDE9FE) else IndSurface,
                                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, if (item.isPaidViaCreditCard) Color(0xFF7C3AED) else IndBorder),
                                                    modifier = Modifier.clickable { onToggleCreditCard(item) }
                                                ) {
                                                    Text(
                                                        text = if (item.isPaidViaCreditCard) "CC" else "No CC",
                                                        fontSize = 9.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (item.isPaidViaCreditCard) Color(0xFF7C3AED) else IndTextSecondary,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (onEditItem != null) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFFEFF6FF),
                                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, IndBlue),
                                                    modifier = Modifier
                                                        .clickable { onEditItem(item) }
                                                        .testTag("drilldown_edit_debit_${item.id}")
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Filled.Edit,
                                                            contentDescription = "Edit",
                                                            tint = IndBlue,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                        Text(
                                                            text = "Edit",
                                                            fontSize = 9.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = IndBlue
                                                        )
                                                    }
                                                }
                                            }

                                            if (onDeleteItem != null) {
                                                IconButton(
                                                    onClick = { onDeleteItem(item) },
                                                    modifier = Modifier.size(28.dp).testTag("drilldown_delete_debit_${item.id}")
                                                ) {
                                                    Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = IndTextMuted, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        }
                                    }
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
                .widthIn(max = 560.dp)
                .fillMaxWidth(0.95f)
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
