@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CategorySummary
import com.example.data.model.DebitItem
import com.example.data.model.BankTransactionRecord
import com.example.data.model.MonthlyFinanceSummary
import com.example.data.model.IncomeSource
import com.example.data.model.isActualCreditCardTransaction
import com.example.ui.FinMoneyViewModel
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun DashboardScreen(
    viewModel: FinMoneyViewModel,
    onOpenAnalytics: () -> Unit = {},
    onOpenPending: () -> Unit = {},
    onOpenAddDebit: () -> Unit = {}
) {
    val month by viewModel.currentMonth.collectAsStateWithLifecycle()
    val year by viewModel.currentYear.collectAsStateWithLifecycle()
    val currencySymbol by viewModel.currencySymbol.collectAsStateWithLifecycle()
    val isFinancialSafetyVisible by viewModel.isFinancialSafetyVisible.collectAsStateWithLifecycle()
    val summaryRaw by viewModel.monthlySummary.collectAsStateWithLifecycle()
    val summary = summaryRaw ?: MonthlyFinanceSummary()
    val currentDebits by viewModel.currentDebits.collectAsStateWithLifecycle()
    val customCategories by viewModel.customCategories.collectAsStateWithLifecycle()
    val customExpenseTags by viewModel.customExpenseTags.collectAsStateWithLifecycle()
    val currentSalaryRecord by viewModel.currentSalaryRecord.collectAsStateWithLifecycle()
    val pendingBankTransactions by viewModel.pendingBankTransactions.collectAsStateWithLifecycle()

    val monthName = FinMoneyViewModel.getMonthName(month)

    var showAddCreditDialog by remember { mutableStateOf(false) }
    var showManageIncomeDialog by remember { mutableStateOf(false) }
    var showAddDebitDialog by remember { mutableStateOf(false) }
    var showImportStatementDialog by remember { mutableStateOf(false) }
    var showAddCustomCategoryDialog by remember { mutableStateOf(false) }
    var showManageCustomCategoriesDialog by remember { mutableStateOf(false) }
    var editingCustomCategory by remember { mutableStateOf<com.example.data.model.CustomCategory?>(null) }
    var pendingCustomCategoryName by remember { mutableStateOf<String?>(null) }
    var pendingCustomCategoryRowId by remember { mutableStateOf<String?>(null) }
    var lastAddedCustomCategoryForEntry by remember { mutableStateOf<String?>(null) }
    var expensePendingDelete by remember { mutableStateOf<DebitItem?>(null) }
    var creditPendingDelete by remember { mutableStateOf<IncomeSource?>(null) }
    var editingDebitItem by remember { mutableStateOf<DebitItem?>(null) }
    var editingCreditSource by remember { mutableStateOf<IncomeSource?>(null) }

    val selectedSpecificDay by viewModel.selectedSpecificDay.collectAsStateWithLifecycle()
    val selectedStartDay by viewModel.selectedStartDay.collectAsStateWithLifecycle()
    val selectedEndDay by viewModel.selectedEndDay.collectAsStateWithLifecycle()

    var mainLedgerTab by remember { mutableStateOf("DEBITS") } // "DEBITS" or "CREDITS"

    // Debits State
    var selectedStatusFilter by remember { mutableStateOf("ALL") } // "ALL", "YET_TO_PAY", "ALREADY_PAID"
    var creditCardOnlyFilter by remember { mutableStateOf(false) }
    var recurringOnlyFilter by remember { mutableStateOf(false) }
    var selectedCategoryFilter by remember { mutableStateOf("ALL") }
    var dashboardViewMode by remember { mutableStateOf("LIST") } // "GROUP_BY_CATEGORY" or "LIST"
    var expandedCategoryKeys by remember { mutableStateOf(setOf<String>()) }

    // Credits State
    var selectedCreditStatusFilter by remember { mutableStateOf("ALL") } // "ALL", "RECEIVED", "PENDING"
    var selectedCreditCategoryFilter by remember { mutableStateOf("ALL") }
    var creditsViewMode by remember { mutableStateOf("LIST") } // "GROUP_BY_CATEGORY" or "LIST"
    var expandedCreditCategoryKeys by remember { mutableStateOf(setOf<String>()) }

    val currentIncomeSources = summary.incomeSources
    val filteredCredits = remember(currentIncomeSources, selectedCreditStatusFilter, selectedCreditCategoryFilter, creditsViewMode) {
        val list = currentIncomeSources.filter { item ->
            val matchesStatus = when (selectedCreditStatusFilter) {
                "RECEIVED" -> item.isReceived
                "PENDING" -> !item.isReceived
                else -> true
            }
            val matchesCategory = if (selectedCreditCategoryFilter == "ALL") {
                true
            } else {
                item.category.equals(selectedCreditCategoryFilter, true)
            }
            matchesStatus && matchesCategory
        }

        if (creditsViewMode == "LIST") {
            list.sortedWith(compareCreditsByRecency())
        } else {
            list
        }
    }

    val totalFilteredCredits = remember(filteredCredits) { filteredCredits.sumOf { it.amount } }

    val creditsCategorySummaries = remember(filteredCredits, totalFilteredCredits) {
        filteredCredits
            .groupBy { it.category.ifBlank { "OTHER" } }
            .map { (catName, items) ->
                val catTotal = items.sumOf { it.amount }
                val catReceived = items.filter { it.isReceived }.sumOf { it.amount }
                val yetToReceive = catTotal - catReceived
                val pct = if (totalFilteredCredits > 0) (catTotal / totalFilteredCredits).toFloat() else 0f
                CategorySummary(
                    categoryName = catName,
                    categoryKey = catName.uppercase(),
                    iconKey = catName,
                    totalAmount = catTotal,
                    paidAmount = catReceived,
                    yetToPayAmount = yetToReceive,
                    itemCount = items.size,
                    paidCount = items.count { it.isReceived },
                    percentage = pct,
                    colorHex = "#16A34A"
                )
            }
            .sortedByDescending { it.totalAmount } // Highest total credit category at top
    }

    val filteredDebits = remember(
        currentDebits,
        selectedStatusFilter,
        creditCardOnlyFilter,
        recurringOnlyFilter,
        selectedCategoryFilter,
        selectedSpecificDay,
        selectedStartDay,
        selectedEndDay,
        dashboardViewMode,
        month,
        year
    ) {
        val list = currentDebits.filter { item ->
            val matchesStatus = when (selectedStatusFilter) {
                "YET_TO_PAY" -> !item.isPaid
                "ALREADY_PAID" -> item.isPaid
                else -> true
            }
            val matchesCreditCard = if (creditCardOnlyFilter) {
                item.isPaidViaCreditCard || item.category.equals("Credit Card", true)
            } else {
                true
            }
            val matchesRecurring = if (recurringOnlyFilter) {
                item.isRecurring ||
                item.category.contains("BILL", true) ||
                item.category.contains("SUBSCRIPTION", true) ||
                item.category.contains("UTILITY", true) ||
                item.notes.contains("RECURRING", true)
            } else {
                true
            }
            val matchesCategory = matchesDebitCategoryFilter(item, selectedCategoryFilter)
            val matchesDay = when {
                selectedStartDay != null && selectedEndDay != null -> {
                    val minD = minOf(selectedStartDay!!, selectedEndDay!!)
                    val maxD = maxOf(selectedStartDay!!, selectedEndDay!!)
                    item.dueDateDay in minD..maxD
                }
                selectedStartDay != null -> {
                    item.dueDateDay == selectedStartDay
                }
                selectedSpecificDay != null -> {
                    item.dueDateDay == selectedSpecificDay
                }
                else -> true
            }
            matchesStatus && matchesCreditCard && matchesRecurring && matchesCategory && matchesDay
        }

        if (dashboardViewMode == "LIST") {
            list.sortedWith(compareDebitsByRecency(month, year))
        } else {
            list
        }
    }

    val totalFilteredDebits = remember(filteredDebits) { filteredDebits.sumOf { it.amount } }
    val dashboardCategorySummaries = remember(filteredDebits, summary.categoryBreakdown, totalFilteredDebits) {
        filteredDebits
            .groupBy { debitCategoryFilterKey(it) }
            .map { (filterKey, items) ->
                val catName = debitCategoryDisplayLabel(items.first())
                val catTotal = items.sumOf { it.amount }
                val catPaid = items.filter { it.isPaid }.sumOf { it.amount }
                val yetToPay = catTotal - catPaid
                val pct = if (totalFilteredDebits > 0) (catTotal / totalFilteredDebits).toFloat() else 0f
                val matchingSummary = summary.categoryBreakdown.find {
                    it.categoryKey.equals(filterKey, ignoreCase = true) ||
                    it.categoryName.equals(catName, ignoreCase = true)
                }
                val colorHex = matchingSummary?.colorHex ?: "#00B386"
                val iconKey = matchingSummary?.iconKey.takeIf { !it.isNullOrBlank() } ?: filterKey
                CategorySummary(
                    categoryName = catName,
                    categoryKey = filterKey,
                    iconKey = iconKey,
                    totalAmount = catTotal,
                    paidAmount = catPaid,
                    yetToPayAmount = yetToPay,
                    itemCount = items.size,
                    paidCount = items.count { it.isPaid },
                    percentage = pct,
                    colorHex = colorHex
                )
            }
            .sortedByDescending { it.totalAmount } // Highest total amount at top for Group By view
    }

    val debitsByDate = remember(filteredDebits, month, year) {
        filteredDebits.groupBy { it.dueDateDay }
            .entries
            .sortedByDescending { it.key }
            .map { it.key to it.value.sortedWith(compareDebitsByRecency(month, year)) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppTheme.dimensions.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp)
    ) {
        item {
            var showCalendarPicker by remember { mutableStateOf(false) }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = IndSurface,
                border = BorderStroke(1.dp, IndBorderSubtle),
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    val dateHeaderLabel = remember(monthName, year, selectedStartDay, selectedEndDay, selectedSpecificDay) {
                        when {
                            selectedStartDay != null && selectedEndDay != null && selectedStartDay != selectedEndDay -> {
                                val minD = minOf(selectedStartDay!!, selectedEndDay!!)
                                val maxD = maxOf(selectedStartDay!!, selectedEndDay!!)
                                "$monthName $minD–$maxD, $year"
                            }
                            selectedStartDay != null -> {
                                "$monthName $selectedStartDay, $year"
                            }
                            selectedSpecificDay != null -> {
                                "$monthName $selectedSpecificDay, $year"
                            }
                            else -> "$monthName $year"
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showCalendarPicker = true }
                                .testTag("calendar_header_button"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(32.dp).clip(CircleShape).background(IndBlueLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.CalendarMonth, null, tint = IndBlue, modifier = Modifier.size(16.dp))
                            }
                            Text(
                                dateHeaderLabel,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = IndNavyHeader,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { viewModel.previousMonth() }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.ChevronLeft, "Prev", tint = IndTextSecondary, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { viewModel.nextMonth() }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.ChevronRight, "Next", tint = IndTextSecondary, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { viewModel.toggleFinancialSafetyVisible() }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                if (isFinancialSafetyVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                "Privacy",
                                tint = IndBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            CompactMetric(
                                "In",
                                if (!isFinancialSafetyVisible) "★★★★" else "$currencySymbol${"%,.0f".format(summary.totalIncome)}",
                                IndGreenDark,
                                Icons.Filled.SouthWest
                            ) { mainLedgerTab = "CREDITS" }
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            CompactMetric(
                                "Out",
                                if (!isFinancialSafetyVisible) "★★★★" else "$currencySymbol${"%,.0f".format(summary.totalDebits)}",
                                IndRed,
                                Icons.Filled.NorthEast
                            ) { mainLedgerTab = "DEBITS" }
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            CompactMetric(
                                "Net",
                                if (!isFinancialSafetyVisible) "★★★★" else "$currencySymbol${"%,.0f".format(summary.netFlexibleSpending)}",
                                if (summary.netFlexibleSpending >= 0) IndNavyHeader else IndRed,
                                Icons.Filled.Savings
                            )
                        }
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
                        viewModel.setMonthYear(m, y, d)
                        showCalendarPicker = false
                    },
                    onSelectDateRange = { m, y, s, e ->
                        viewModel.setDateRangeFilter(m, y, s, e)
                        showCalendarPicker = false
                    }
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MiniDashAction("Expense", Icons.Filled.Add, IndBlue, IndBlueLight, Modifier.weight(1f).testTag("add_debit_dashboard_button")) { onOpenAddDebit() }
                MiniDashAction("Income", Icons.Filled.Add, IndGreenDark, IndGreenLight, Modifier.weight(1f).testTag("add_credit_dashboard_button")) { showAddCreditDialog = true }
                MiniDashAction("Import", Icons.Filled.FileUpload, IndBlue, Color(0xFFEFF6FF), Modifier.weight(1f).testTag("import_statement_dashboard_button")) { showImportStatementDialog = true }
                MiniDashAction("Prefill", Icons.Filled.Repeat, Color(0xFF16A34A), Color(0xFFF0FDF4), Modifier.weight(1f).testTag("prefill_recurring_dashboard_button")) {
                    viewModel.prefillRecurringForMonth(month, year)
                }
            }
        }

        // Automatically Identified Payment Entries / Pending Bank Transactions Approval Section

        if (pendingBankTransactions.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                            Text(
                                text = "Auto-Identified Payments (${pendingBankTransactions.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = IndTextPrimary
                            )
                        }
                        TextButton(onClick = { viewModel.approveAllPendingTransactions() }) {
                            Text("Approve All", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = IndBlue)
                        }
                    }

                    pendingBankTransactions.forEach { tx ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFEF3C7),
                            border = BorderStroke(1.dp, Color(0xFFFCD34D)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = tx.merchant.ifBlank { "Detected Bank Alert" },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = IndNavyHeader
                                        )
                                        Text(
                                            text = "Category: ${tx.category} • $currencySymbol${"%,.0f".format(tx.amount)}",
                                            fontSize = 12.sp,
                                            color = IndTextSecondary
                                        )
                                        if (tx.originalText.isNotBlank()) {
                                            Text(
                                                text = "\"${tx.originalText}\"",
                                                fontSize = 10.sp,
                                                color = IndTextMuted,
                                                maxLines = 2
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.approvePendingTransaction(tx) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f).height(36.dp)
                                    ) {
                                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Approve", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = { viewModel.rejectPendingTransaction(tx) },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                                        border = BorderStroke(1.dp, Color(0xFFDC2626)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f).height(36.dp)
                                    ) {
                                        Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Reject", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Main Segmented Tab Switcher Bar: Debits / Expenses vs Credits / Income
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = IndSurface,
                border = BorderStroke(1.dp, IndBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(IndBackground),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val isDebits = mainLedgerTab == "DEBITS"
                    val isCredits = mainLedgerTab == "CREDITS"

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { mainLedgerTab = "DEBITS" }
                            .testTag("tab_debits_outflow"),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isDebits) IndRed else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 7.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ArrowUpward,
                                contentDescription = "Debits",
                                tint = if (isDebits) Color.White else IndRed,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Expenses",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDebits) Color.White else IndTextPrimary
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { mainLedgerTab = "CREDITS" }
                            .testTag("tab_credits_inflow"),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isCredits) Color(0xFF16A34A) else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 7.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ArrowDownward,
                                contentDescription = "Credits",
                                tint = if (isCredits) Color.White else Color(0xFF16A34A),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Income",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isCredits) Color.White else IndTextPrimary
                            )
                        }
                    }
                }
            }
        }

        if (mainLedgerTab == "DEBITS") {
            // Debit filters and category totals
            item {
            val categoryTotals = remember(filteredDebits, customCategories) {
                filteredDebits
                    .groupBy { debitCategoryFilterKey(it) }
                    .map { (filterKey, items) ->
                        val label = debitCategoryDisplayLabel(items.first())
                        val iconName = iconKeyForDebitFilter(filterKey, customCategories)
                        val storedIcon = iconName.takeIf {
                            filterKey.startsWith(CUSTOM_CATEGORY_FILTER_PREFIX, ignoreCase = true) && it != filterKey
                        }
                        val customMeta = customCategoryForFilterKey(filterKey, customCategories)
                        CategoryFilterChipData(
                            filterKey = filterKey,
                            label = label,
                            stylingKey = if (storedIcon != null) label else filterKey,
                            storedIconName = storedIcon,
                            colorHex = customMeta?.colorHex,
                            total = items.sumOf { it.amount },
                            count = items.size
                        )
                    }
                    .sortedByDescending { it.total }
            }
            val visibleTotal = filteredDebits.sumOf { it.amount }
            val filtersActive = selectedStatusFilter != "ALL" || creditCardOnlyFilter || recurringOnlyFilter ||
                selectedCategoryFilter != "ALL" || selectedSpecificDay != null || selectedStartDay != null || selectedEndDay != null

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Spending · ${filteredDebits.size} items",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndNavyHeader
                        )
                        Text(
                            text = if (dashboardViewMode == "LIST") {
                                "List view · newest first"
                            } else {
                                "Grouped by category"
                            },
                            fontSize = 10.sp,
                            color = IndTextMuted
                        )
                    }
                    Text(
                        text = "$currencySymbol${"%,.0f".format(visibleTotal)}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = IndNavyHeader
                    )
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = IndSurface,
                    border = BorderStroke(1.dp, IndBorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Filters",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndTextMuted,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            item(key = "unpaid") {
                                DashIconChip("Unpaid", Icons.Filled.HourglassTop, selectedStatusFilter == "YET_TO_PAY", Color(0xFFD97706)) {
                                    selectedStatusFilter = if (selectedStatusFilter == "YET_TO_PAY") "ALL" else "YET_TO_PAY"
                                }
                            }
                            item(key = "paid") {
                                DashIconChip("Paid", Icons.Filled.CheckCircle, selectedStatusFilter == "ALREADY_PAID", IndGreenDark) {
                                    selectedStatusFilter = if (selectedStatusFilter == "ALREADY_PAID") "ALL" else "ALREADY_PAID"
                                }
                            }
                            item(key = "cc") {
                                DashIconChip("Credit card", Icons.Filled.CreditCard, creditCardOnlyFilter, IndPurple) {
                                    creditCardOnlyFilter = !creditCardOnlyFilter
                                }
                            }
                            item(key = "repeat") {
                                DashIconChip("Repeat", Icons.Filled.Repeat, recurringOnlyFilter, Color(0xFF0284C7)) {
                                    recurringOnlyFilter = !recurringOnlyFilter
                                }
                            }
                            if (filtersActive) {
                                item(key = "clear") {
                                    DashIconChip("Clear", Icons.Filled.Close, false, IndRed) {
                                        selectedStatusFilter = "ALL"
                                        creditCardOnlyFilter = false
                                        recurringOnlyFilter = false
                                        selectedCategoryFilter = "ALL"
                                        viewModel.clearDateFilter()
                                    }
                                }
                            }
                            categoryTotals.forEach { chip ->
                                item(key = "cat_${chip.filterKey}") {
                                    val selected = selectedCategoryFilter.equals(chip.filterKey, true)
                                    CategoryFilterChip(
                                        category = chip.label,
                                        stylingKey = chip.stylingKey,
                                        storedIconName = chip.storedIconName,
                                        customColor = chip.colorHex?.let { customCategoryTint(it) },
                                        selected = selected,
                                        selectedColor = IndNavyHeader
                                    ) {
                                        selectedCategoryFilter = if (selected) "ALL" else chip.filterKey
                                    }
                                }
                            }
                        }

                        Text(
                            text = "View",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndTextMuted,
                            modifier = Modifier.padding(start = 2.dp, top = 4.dp)
                        )
                        DashboardViewModeSegmentedControl(
                            isListView = dashboardViewMode == "LIST",
                            accentColor = IndBlue,
                            groupLabel = "Group by category",
                            listLabel = "List view",
                            onGroupBy = { dashboardViewMode = "GROUP_BY_CATEGORY" },
                            onList = { dashboardViewMode = "LIST" }
                        )

                    }
                }
            }
        }

        if (filteredDebits.isEmpty()) {
            item {
                EmptyState(
                    title = "No Transactions Found for $monthName $year",
                    subtitle = "Add your first transaction manually or import bank statements (PhonePe, Google Pay, Super Money) to start tracking.",
                    primaryButtonText = "Add Your First Transaction",
                    secondaryButtonText = "Import Statement",
                    onPrimaryAction = { onOpenAddDebit() },
                    onSecondaryAction = { showImportStatementDialog = true }
                )
            }
        } else if (dashboardViewMode == "GROUP_BY_CATEGORY") {
            items(dashboardCategorySummaries, key = { "cat_${it.categoryKey}" }) { cat ->
                val categoryShare = if (totalFilteredDebits > 0) (cat.totalAmount / totalFilteredDebits).toFloat() else 0f
                val isExpanded = expandedCategoryKeys.contains(cat.categoryKey)
                val categoryItems = remember(filteredDebits, cat.categoryKey, month, year) {
                    filteredDebits.filter { debitMatchesCategorySummary(it, cat) }
                        .sortedWith(compareDebitsByRecency(month, year))
                }

                IndCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dashboard_category_${cat.categoryKey}"),
                    onClick = {
                        expandedCategoryKeys = if (isExpanded) {
                            expandedCategoryKeys - cat.categoryKey
                        } else {
                            expandedCategoryKeys + cat.categoryKey
                        }
                    }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                CategoryIconBadge(
                                    categoryKey = cat.categoryName,
                                    storedIconName = categorySummaryStoredIcon(cat),
                                    customColor = customCategoryTint(cat.colorHex)
                                )
                                Column {
                                    Text(
                                        prettyCategory(cat.categoryName),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = IndTextPrimary
                                    )
                                    Text(
                                        text = "${cat.itemCount} items • ${cat.paidCount} paid • ${(categoryShare * 100).toInt()}% of outflow",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = IndTextSecondary
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
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

                                IconButton(
                                    onClick = {
                                        expandedCategoryKeys = if (isExpanded) {
                                            expandedCategoryKeys - cat.categoryKey
                                        } else {
                                            expandedCategoryKeys + cat.categoryKey
                                        }
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                        contentDescription = if (isExpanded) "Collapse" else "Expand inline",
                                        tint = IndTextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        LinearProgressIndicator(
                            progress = { categoryShare.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = try { Color(android.graphics.Color.parseColor(cat.colorHex)) } catch (e: Exception) { IndBlue },
                            trackColor = IndCardSecondary
                        )

                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(4.dp))
                            HorizontalDivider(color = IndBorderSubtle, thickness = 0.5.dp)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                categoryItems.forEach { item ->
                                    DebitItemCard(
                                        item = item,
                                        currencySymbol = currencySymbol,
                                        onTogglePaid = { viewModel.toggleDebitPaid(item) },
                                        onToggleCreditCard = { viewModel.toggleDebitCreditCardStatus(item) },
                                        onToggleRecurring = { viewModel.toggleDebitRecurring(item) },
                                        onEdit = { editingDebitItem = item },
                                        onDelete = { expensePendingDelete = item },
                                        customCategories = customCategories,
                                        customExpenseTags = customExpenseTags
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            debitsByDate.forEach { (day, dayDebits) ->
                item(key = "date_header_$day") {
                    val dateStr = formatDayToDateString(day, month, year)
                    val dayTotal = dayDebits.sumOf { it.amount }
                    Surface(
                        color = IndBackground,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.5.dp, IndBorderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CalendarToday,
                                    contentDescription = null,
                                    tint = IndBlue,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Transaction Date: Day $day ($dateStr)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = IndNavyHeader
                                )
                            }
                            Text(
                                text = "$currencySymbol${"%,.0f".format(dayTotal)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = IndNavyHeader
                            )
                        }
                    }
                }
                items(dayDebits, key = { it.id }) { item ->
                    DebitItemCard(
                        item = item,
                        currencySymbol = currencySymbol,
                        onTogglePaid = { viewModel.toggleDebitPaid(item) },
                        onToggleCreditCard = { viewModel.toggleDebitCreditCardStatus(item) },
                        onToggleRecurring = { viewModel.toggleDebitRecurring(item) },
                        onEdit = { editingDebitItem = item },
                        onDelete = { expensePendingDelete = item },
                        customCategories = customCategories,
                        customExpenseTags = customExpenseTags
                    )
                }
            }
        }
    } else {
            // Credits / Income Section View (Same View Structure as Expenses)
            item {
                val creditCategoryTotals = remember(filteredCredits) {
                    filteredCredits
                        .groupBy { it.category.ifBlank { "OTHER" } }
                        .map { (category, items) ->
                            Triple(category, items.sumOf { it.amount }, items.size)
                        }
                        .sortedByDescending { it.second }
                }
                val visibleCreditTotal = filteredCredits.sumOf { it.amount }
                val creditFiltersActive = selectedCreditStatusFilter != "ALL" || selectedCreditCategoryFilter != "ALL"

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Income · ${filteredCredits.size} items",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = IndNavyHeader
                            )
                            Text(
                                text = if (creditsViewMode == "LIST") {
                                    "List view · newest first"
                                } else {
                                    "Grouped by category"
                                },
                                fontSize = 10.sp,
                                color = IndTextMuted
                            )
                        }
                        Text(
                            text = "+$currencySymbol${"%,.0f".format(visibleCreditTotal)}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = Color(0xFF16A34A)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = IndSurface,
                        border = BorderStroke(1.dp, IndBorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Filters",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = IndTextMuted,
                                modifier = Modifier.padding(start = 2.dp)
                            )
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp)
                            ) {
                                item(key = "received") {
                                    DashIconChip("Received", Icons.Filled.CheckCircle, selectedCreditStatusFilter == "RECEIVED", IndGreenDark) {
                                        selectedCreditStatusFilter = if (selectedCreditStatusFilter == "RECEIVED") "ALL" else "RECEIVED"
                                    }
                                }
                                item(key = "pending") {
                                    DashIconChip("Pending", Icons.Filled.HourglassTop, selectedCreditStatusFilter == "PENDING", Color(0xFFD97706)) {
                                        selectedCreditStatusFilter = if (selectedCreditStatusFilter == "PENDING") "ALL" else "PENDING"
                                    }
                                }
                                if (creditFiltersActive) {
                                    item(key = "clear") {
                                        DashIconChip("Clear", Icons.Filled.Close, false, IndRed) {
                                            selectedCreditStatusFilter = "ALL"
                                            selectedCreditCategoryFilter = "ALL"
                                        }
                                    }
                                }
                                creditCategoryTotals.forEach { (category, _, _) ->
                                    item(key = "cat_$category") {
                                        val selected = selectedCreditCategoryFilter.equals(category, true)
                                        CategoryFilterChip(
                                            category = category,
                                            stylingKey = category,
                                            selected = selected,
                                            selectedColor = Color(0xFF16A34A)
                                        ) {
                                            selectedCreditCategoryFilter = if (selected) "ALL" else category
                                        }
                                    }
                                }
                            }

                            Text(
                                text = "View",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = IndTextMuted,
                                modifier = Modifier.padding(start = 2.dp, top = 4.dp)
                            )
                            DashboardViewModeSegmentedControl(
                                isListView = creditsViewMode == "LIST",
                                accentColor = Color(0xFF16A34A),
                                groupLabel = "Group by category",
                                listLabel = "List view",
                                onGroupBy = { creditsViewMode = "GROUP_BY_CATEGORY" },
                                onList = { creditsViewMode = "LIST" }
                            )

                        }
                    }
                }
            }

            if (filteredCredits.isEmpty()) {
                item {
                    EmptyState(
                        title = "No Credits / Income Found for $monthName $year",
                        subtitle = "Add your primary salary, freelance earnings, or additional credit entries to track your inflows.",
                        primaryButtonText = "Add Credit",
                        secondaryButtonText = "Manage Base Salary",
                        onPrimaryAction = { showAddCreditDialog = true },
                        onSecondaryAction = { showManageIncomeDialog = true }
                    )
                }
            } else if (creditsViewMode == "GROUP_BY_CATEGORY") {
                items(creditsCategorySummaries, key = { "credit_cat_${it.categoryKey}" }) { cat ->
                    val isExpanded = expandedCreditCategoryKeys.contains(cat.categoryKey)
                    val categoryItems = remember(filteredCredits, cat.categoryKey) {
                        filteredCredits.filter {
                            it.category.equals(cat.categoryKey, ignoreCase = true) ||
                            it.category.equals(cat.categoryName, ignoreCase = true)
                        }.sortedWith(compareCreditsByRecency())
                    }

                    IndCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            expandedCreditCategoryKeys = if (isExpanded) {
                                expandedCreditCategoryKeys - cat.categoryKey
                            } else {
                                expandedCreditCategoryKeys + cat.categoryKey
                            }
                        }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    CategoryIconBadge(categoryKey = cat.iconKey)
                                    Column {
                                        Text(
                                            prettyCategory(cat.categoryName),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = IndTextPrimary
                                        )
                                        Text(
                                            text = "${cat.itemCount} items • ${cat.paidCount} received",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = IndTextSecondary
                                        )
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "+$currencySymbol${"%,.0f".format(cat.totalAmount)}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF16A34A)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            expandedCreditCategoryKeys = if (isExpanded) {
                                                expandedCreditCategoryKeys - cat.categoryKey
                                            } else {
                                                expandedCreditCategoryKeys + cat.categoryKey
                                            }
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                            contentDescription = "Expand",
                                            tint = IndTextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            if (isExpanded) {
                                Spacer(modifier = Modifier.height(4.dp))
                                HorizontalDivider(color = IndBorderSubtle, thickness = 0.5.dp)
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    categoryItems.forEach { item ->
                                        CreditItemCard(
                                            item = item,
                                            currencySymbol = currencySymbol,
                                            onToggleReceived = { viewModel.toggleIncomeReceived(item.id, month, year) },
                                            onToggleRecurring = { viewModel.toggleIncomeRecurring(item.id, month, year) },
                                            onEdit = { editingCreditSource = item },
                                            onDelete = { creditPendingDelete = item }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                items(filteredCredits, key = { "credit_${it.id}" }) { item ->
                    CreditItemCard(
                        item = item,
                        currencySymbol = currencySymbol,
                        onToggleReceived = { viewModel.toggleIncomeReceived(item.id, month, year) },
                        onToggleRecurring = { viewModel.toggleIncomeRecurring(item.id, month, year) },
                        onEdit = { editingCreditSource = item },
                        onDelete = { creditPendingDelete = item }
                    )
                }
            }
        }
    }

    // Manage Income & Credit Dialog
    if (showManageIncomeDialog || showAddCreditDialog || editingCreditSource != null) {
        ManageIncomeCreditDialog(
            incomeSources = summary.incomeSources,
            baseSalary = currentSalaryRecord?.baseSalary ?: (summary.incomeSources.find { it.category == "SALARY" || it.id == "1" }?.amount ?: 0.0),
            startInEditCredit = editingCreditSource,
            openOnAddCreditTab = showAddCreditDialog && editingCreditSource == null,
            onDismiss = {
                showManageIncomeDialog = false
                showAddCreditDialog = false
                editingCreditSource = null
            },
            onSaveNewCredit = { title, amount, category, dateString, isRecurring ->
                viewModel.addIncomeCredit(title, amount, category, dateString, month, year, isRecurring)
                showManageIncomeDialog = false
                showAddCreditDialog = false
            },
            onUpdateCredit = { id, title, amount, category, dateString, isRecurring ->
                viewModel.updateIncomeSource(id, title, amount, category, dateString, month, year, isRecurring)
                showManageIncomeDialog = false
                showAddCreditDialog = false
                editingCreditSource = null
            },
            onDeleteCredit = { id ->
                viewModel.deleteIncomeSource(id, month, year)
            },
            onUpdateBaseSalary = { newSalary ->
                viewModel.updateBaseSalary(newSalary, month, year)
                showManageIncomeDialog = false
                showAddCreditDialog = false
            }
        )
    }

    // Add Debit / Planned Entry Dialog -> Unified to QuickMultiAddSheet
    if (showAddDebitDialog) {
        val allDebits by viewModel.allDebits.collectAsStateWithLifecycle()
        QuickMultiAddSheet(
            currencySymbol = currencySymbol,
            todayDay = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH),
            month = month,
            year = year,
            customCategories = customCategories,
            customExpenseTags = customExpenseTags,
            allHistoricalDebits = allDebits,
            initialCategoryKey = lastAddedCustomCategoryForEntry,
            onSaveCustomExpenseTag = { name, icon, color ->
                viewModel.addCustomExpenseTag(name, icon, color)
            },
            pendingCustomCategoryName = pendingCustomCategoryName,
            pendingCustomCategoryRowId = pendingCustomCategoryRowId,
            onPendingCustomCategoryApplied = {
                pendingCustomCategoryName = null
                pendingCustomCategoryRowId = null
            },
            onDismiss = {
                showAddDebitDialog = false
                lastAddedCustomCategoryForEntry = null
            },
            onSaveBatch = { batch ->
                viewModel.insertDebits(batch)
                showAddDebitDialog = false
                lastAddedCustomCategoryForEntry = null
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
                    lastAddedCustomCategoryForEntry = name
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
            onDelete = { cat ->
                viewModel.deleteCustomCategory(cat)
            },
            expenseCountForCategory = { name -> viewModel.countExpensesUsingCustomCategory(name) }
        )
    }

    if (editingDebitItem != null) {
        val item = editingDebitItem!!
        val allDebits by viewModel.allDebits.collectAsStateWithLifecycle()
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
            pendingCustomCategoryName = pendingCustomCategoryName,
            pendingCustomCategoryRowId = pendingCustomCategoryRowId,
            onPendingCustomCategoryApplied = {
                pendingCustomCategoryName = null
                pendingCustomCategoryRowId = null
            },
            onDismiss = { editingDebitItem = null },
            onSaveBatch = { batch ->
                batch.firstOrNull()?.let { viewModel.updateDebit(it) }
                editingDebitItem = null
            },
            onDeleteDebit = { itemToDelete ->
                expensePendingDelete = itemToDelete
                editingDebitItem = null
            },
            onOpenAddCustomCategory = { rowId ->
                pendingCustomCategoryRowId = rowId
                showAddCustomCategoryDialog = true
            }
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

    creditPendingDelete?.let { src ->
        ConfirmDeleteDialog(
            title = "Delete credit?",
            message = "Remove \"${src.title.ifBlank { src.name }}\" from this month? This cannot be undone.",
            onDismiss = { creditPendingDelete = null },
            onConfirm = {
                viewModel.deleteIncomeSource(src.id, month, year)
                creditPendingDelete = null
                if (editingCreditSource?.id == src.id) editingCreditSource = null
            }
        )
    }

    // Statement Import Dialog (PhonePe, GPay, Bank Statement, Super Money)
    if (showImportStatementDialog) {
        StatementImportDialog(
            onDismiss = { showImportStatementDialog = false },
            onImportSuccess = { _ ->
                showImportStatementDialog = false
            }
        )
    }
}

@Composable
fun DebitItemCard(
    item: DebitItem,
    currencySymbol: String,
    onTogglePaid: () -> Unit,
    onToggleCreditCard: () -> Unit,
    onToggleRecurring: () -> Unit = {},
    onEdit: () -> Unit,
    onDelete: () -> Unit = {},
    customCategories: List<com.example.data.model.CustomCategory> = emptyList(),
    customExpenseTags: List<com.example.data.model.CustomExpenseTag> = emptyList()
) {
    val categoryLabel = debitCategoryDisplayLabel(item)
    val subTitle = debitSubcategoryTitle(item)
    val isChit = item.category.equals("CHIT", ignoreCase = true) || item.title.contains("CHIT", ignoreCase = true)
    val isLoan = com.example.ui.components.isLoanOrEmi(item.category) || item.principalAmount > 0.0 || item.category.contains("EMI", ignoreCase = true)
    val isCcItem = item.isActualCreditCardTransaction()
    val displayTitle = subTitle?.let { primaryTagLabel(it) } ?: item.title.ifBlank { categoryLabel }

    val paidMonths = if (item.isPaid) item.currentMonthTenure else (item.currentMonthTenure - 1).coerceAtLeast(0)

    // Calculate Amortization Outstanding Balance for Loans/EMIs
    val outstandingBalance = remember(item.principalAmount, item.interestRate, item.totalTenureMonths, item.currentMonthTenure, item.isPaid) {
        if (item.principalAmount <= 0.0 || item.totalTenureMonths <= 0) return@remember 0.0
        val p = item.principalAmount
        val n = item.totalTenureMonths
        val m = paidMonths
        val r = (item.interestRate / 12.0) / 100.0

        if (r > 0.0) {
            val powN = Math.pow(1.0 + r, n.toDouble())
            val powM = Math.pow(1.0 + r, m.toDouble())
            (p * (powN - powM) / (powN - 1.0)).coerceAtLeast(0.0)
        } else {
            (p - (p * m.toDouble() / n.toDouble())).coerceAtLeast(0.0)
        }
    }

    val subtitleText = remember(categoryLabel, isCcItem, item.isPaid, item.isRecurring, isChit, isLoan, paidMonths, item.currentMonthTenure, item.totalTenureMonths, outstandingBalance, currencySymbol) {
        buildString {
            val catPretty = prettyCategory(categoryLabel)
            if (isLoan && item.totalTenureMonths > 0) {
                append("Paid EMI: $paidMonths/${item.totalTenureMonths}")
                if (outstandingBalance > 0.0) {
                    append(" • O/S : ${formatShortAmount(outstandingBalance, currencySymbol)}")
                }
            } else if (isChit && item.totalTenureMonths > 0) {
                append("Paid ${item.currentMonthTenure} out of ${item.totalTenureMonths}")
            } else {
                append(catPretty)
            }
            if (isCcItem) {
                append(" • 💳 CC")
            }
            append(" • ")
            append(if (item.isPaid) "🟢 Paid" else "⏳ Due")
            if (item.isRecurring) {
                append(" • 🔁 Recurring")
            }
        }
    }

    IndCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .testTag("debit_card_item_${item.id}"),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Icon + Title & Subtitle details
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                DebitExpenseLeadingBadge(
                    item = item,
                    customCategories = customCategories,
                    customExpenseTags = customExpenseTags
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = displayTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = IndTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = subtitleText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = IndTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Right: Clean Amount Text
            Text(
                text = "-$currencySymbol${"%,.0f".format(item.amount)}",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = if (item.isPaid) IndGreenDark else IndRed
            )
        }
    }
}

private fun formatShortAmount(amount: Double, currencySymbol: String): String {
    return when {
        amount >= 10_000_000 -> "${currencySymbol}${"%.2f".format(amount / 10_000_000)}Cr"
        amount >= 100_000 -> "${currencySymbol}${"%.2f".format(amount / 100_000)}L"
        amount >= 1_000 -> "${currencySymbol}${"%.1f".format(amount / 1_000)}k"
        else -> "${currencySymbol}${"%,.0f".format(amount)}"
    }
}

private fun prettyCategory(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.equals("OTT", ignoreCase = true)) return "OTT"
    if (trimmed.equals("EMI", ignoreCase = true)) return "EMI"
    if (trimmed.equals("CC", ignoreCase = true)) return "CC"
    return raw.replace('_', ' ').lowercase().split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            when {
                word.equals("ott", ignoreCase = true) -> "OTT"
                word.equals("emi", ignoreCase = true) -> "EMI"
                word.equals("cc", ignoreCase = true) -> "CC"
                else -> word.replaceFirstChar { it.uppercase() }
            }
        }
}

private fun formatDayToDateString(day: Int, month: Int, year: Int): String {
    val cal = java.util.Calendar.getInstance().apply {
        set(year, month - 1, day.coerceIn(1, 31))
    }
    val sdf = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.ENGLISH)
    return sdf.format(cal.time)
}

@Composable
private fun CompactMetric(
    label: String,
    value: String,
    color: Color,
    icon: ImageVector,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
            Text(
                label,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = IndTextSecondary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        Text(
            value,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = color,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MiniDashAction(
    label: String,
    icon: ImageVector,
    tint: Color,
    background: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = background,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.22f)),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(16.dp))
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = tint, maxLines = 1)
        }
    }
}

/** Expenses/Income-style toggle for list vs group-by category layout. */
@Composable
private fun DashboardViewModeSegmentedControl(
    isListView: Boolean,
    accentColor: Color,
    groupLabel: String,
    listLabel: String,
    onGroupBy: () -> Unit,
    onList: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(IndBackground)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onGroupBy),
            shape = RoundedCornerShape(10.dp),
            color = if (!isListView) accentColor else Color.Transparent
        ) {
            Row(
                modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.GridView,
                    contentDescription = groupLabel,
                    tint = if (!isListView) Color.White else accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = groupLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (!isListView) Color.White else IndTextPrimary,
                    maxLines = 1
                )
            }
        }
        Surface(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onList),
            shape = RoundedCornerShape(10.dp),
            color = if (isListView) accentColor else Color.Transparent
        ) {
            Row(
                modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.ViewList,
                    contentDescription = listLabel,
                    tint = if (isListView) Color.White else accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = listLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isListView) Color.White else IndTextPrimary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun DashIconChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) accent else IndBackground,
        border = BorderStroke(1.dp, if (selected) accent else IndBorderSubtle),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, null, tint = if (selected) Color.White else accent, modifier = Modifier.size(13.dp))
            Text(
                label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) Color.White else IndTextPrimary
            )
        }
    }
}

private data class CategoryFilterChipData(
    val filterKey: String,
    val label: String,
    val stylingKey: String,
    val storedIconName: String?,
    val colorHex: String? = null,
    val total: Double,
    val count: Int
)

@Composable
private fun CategoryFilterChip(
    category: String,
    stylingKey: String = category,
    storedIconName: String? = null,
    customColor: Color? = null,
    selected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit
) {
    val styling = getCategoryStyling(stylingKey, customColor = customColor, storedIconName = storedIconName)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) selectedColor else IndBackground,
        border = BorderStroke(1.dp, if (selected) selectedColor else IndBorderSubtle),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) Color.White.copy(alpha = 0.2f) else styling.second),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    styling.first,
                    contentDescription = category,
                    tint = if (selected) Color.White else styling.third,
                    modifier = Modifier.size(12.dp)
                )
            }
            Text(
                text = prettyCategory(category),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) Color.White else IndTextPrimary,
                maxLines = 1
            )
        }
    }
}

@Composable
fun CreditItemCard(
    item: IncomeSource,
    currencySymbol: String,
    onToggleReceived: () -> Unit,
    onToggleRecurring: () -> Unit = {},
    onEdit: () -> Unit,
    onDelete: () -> Unit = {}
) {
    val subtitleText = remember(item.category, item.isReceived, item.isRecurring) {
        buildString {
            append(prettyCategory(item.category))
            append(" • ")
            append(if (item.isReceived) "🟢 Received ✓" else "⏳ Pending Inflow")
            if (item.isRecurring) {
                append(" • 🔁 Recurring")
            }
        }
    }

    IndCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .testTag("credit_card_item_${item.id}"),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Icon + Title & Subtitle details
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                CategoryIconBadge(categoryKey = item.category)

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = item.title.ifBlank { item.name },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        color = IndTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = subtitleText,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = IndTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right: Clean Amount Text + Delete Action
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "+$currencySymbol${"%,.0f".format(item.amount)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.5.sp,
                    color = Color(0xFF16A34A)
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("delete_credit_item_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete income source",
                        tint = IndRed.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun debitRecencyMillis(item: DebitItem, month: Int, year: Int): Long {
    if (item.createdAt > 0L) return item.createdAt
    item.paidDateTimestamp?.takeIf { it > 0L }?.let { return it }
    val day = item.dueDateDay.coerceIn(1, 28)
    return java.util.Calendar.getInstance().apply {
        set(year, month - 1, day, 12, 0, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun compareDebitsByRecency(month: Int, year: Int): Comparator<DebitItem> =
    compareByDescending<DebitItem> { debitRecencyMillis(it, month, year) }.thenByDescending { it.id }

private fun compareCreditsByRecency(): Comparator<IncomeSource> =
    compareByDescending<IncomeSource> { it.receivedDateTimestamp ?: 0L }.thenByDescending { it.id }
