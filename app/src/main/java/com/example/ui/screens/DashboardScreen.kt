@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
    val currentSalaryRecord by viewModel.currentSalaryRecord.collectAsStateWithLifecycle()
    val pendingBankTransactions by viewModel.pendingBankTransactions.collectAsStateWithLifecycle()

    val monthName = FinMoneyViewModel.getMonthName(month)

    var showAddCreditDialog by remember { mutableStateOf(false) }
    var showManageIncomeDialog by remember { mutableStateOf(false) }
    var showAddDebitDialog by remember { mutableStateOf(false) }
    var showImportStatementDialog by remember { mutableStateOf(false) }
    var showAddCustomCategoryDialog by remember { mutableStateOf(false) }
    var expensePendingDelete by remember { mutableStateOf<DebitItem?>(null) }
    var creditPendingDelete by remember { mutableStateOf<IncomeSource?>(null) }
    var editingDebitItem by remember { mutableStateOf<DebitItem?>(null) }
    var editingCreditSource by remember { mutableStateOf<IncomeSource?>(null) }

    val selectedSpecificDay by viewModel.selectedSpecificDay.collectAsStateWithLifecycle()

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
            val matchesDay = if (selectedSpecificDay != null) {
                item.dueDateDay == selectedSpecificDay
            } else {
                true
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppTheme.dimensions.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)
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
                            Text("$monthName $year", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = IndNavyHeader)
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
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        CompactMetric(
                            "In",
                            if (!isFinancialSafetyVisible) "★★★★" else "$currencySymbol${"%,.0f".format(summary.totalIncome)}",
                            IndGreenDark,
                            Icons.Filled.SouthWest
                        ) { mainLedgerTab = "CREDITS" }
                        CompactMetric(
                            "Out",
                            if (!isFinancialSafetyVisible) "★★★★" else "$currencySymbol${"%,.0f".format(summary.totalDebits)}",
                            IndRed,
                            Icons.Filled.NorthEast
                        ) { mainLedgerTab = "DEBITS" }
                        CompactMetric(
                            "Net",
                            if (!isFinancialSafetyVisible) "★★★★" else "$currencySymbol${"%,.0f".format(summary.netFlexibleSpending)}",
                            if (summary.netFlexibleSpending >= 0) IndNavyHeader else IndRed,
                            Icons.Filled.Savings
                        )
                    }
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
                        CategoryFilterChipData(
                            filterKey = filterKey,
                            label = label,
                            stylingKey = if (storedIcon != null) label else filterKey,
                            storedIconName = storedIcon,
                            total = items.sumOf { it.amount },
                            count = items.size
                        )
                    }
                    .sortedByDescending { it.total }
            }
            val visibleTotal = filteredDebits.sumOf { it.amount }
            val filtersActive = selectedStatusFilter != "ALL" || creditCardOnlyFilter || recurringOnlyFilter ||
                selectedCategoryFilter != "ALL" || selectedSpecificDay != null

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
                        if (dashboardViewMode == "LIST") {
                            Text(
                                text = "List view · newest first",
                                fontSize = 10.sp,
                                color = IndTextMuted
                            )
                        }
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
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DashIconChip("Category", Icons.Filled.GridView, dashboardViewMode == "GROUP_BY_CATEGORY", IndBlue) {
                                dashboardViewMode = "GROUP_BY_CATEGORY"
                            }
                            DashIconChip("List", Icons.Filled.ViewList, dashboardViewMode == "LIST", IndBlue) {
                                dashboardViewMode = "LIST"
                            }
                            DashIconChip("All", Icons.Filled.Apps, selectedStatusFilter == "ALL", IndNavyHeader) {
                                selectedStatusFilter = "ALL"
                            }
                            DashIconChip("Unpaid", Icons.Filled.HourglassTop, selectedStatusFilter == "YET_TO_PAY", Color(0xFFD97706)) {
                                selectedStatusFilter = "YET_TO_PAY"
                            }
                            DashIconChip("Paid", Icons.Filled.CheckCircle, selectedStatusFilter == "ALREADY_PAID", IndGreenDark) {
                                selectedStatusFilter = "ALREADY_PAID"
                            }
                            DashIconChip("Card", Icons.Filled.CreditCard, creditCardOnlyFilter, IndPurple) {
                                creditCardOnlyFilter = !creditCardOnlyFilter
                            }
                            DashIconChip("Repeat", Icons.Filled.Repeat, recurringOnlyFilter, Color(0xFF0284C7)) {
                                recurringOnlyFilter = !recurringOnlyFilter
                            }
                            if (filtersActive) {
                                DashIconChip("Clear", Icons.Filled.Close, false, IndRed) {
                                    selectedStatusFilter = "ALL"
                                    creditCardOnlyFilter = false
                                    recurringOnlyFilter = false
                                    selectedCategoryFilter = "ALL"
                                    viewModel.setMonthYear(month, year, null)
                                }
                            }
                        }

                        if (categoryTotals.isNotEmpty()) {
                            Text(
                                text = "By category",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = IndTextMuted,
                                modifier = Modifier.padding(start = 2.dp, top = 2.dp)
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                categoryTotals.forEach { chip ->
                                    val selected = selectedCategoryFilter.equals(chip.filterKey, true)
                                    CategoryTotalPill(
                                        category = chip.label,
                                        stylingKey = chip.stylingKey,
                                        storedIconName = chip.storedIconName,
                                        totalLabel = "$currencySymbol${"%,.0f".format(chip.total)}",
                                        count = chip.count,
                                        selected = selected,
                                        selectedColor = IndNavyHeader
                                    ) {
                                        selectedCategoryFilter = if (selected) "ALL" else chip.filterKey
                                    }
                                }
                            }
                        }
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
                                        customCategories = customCategories
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            items(filteredDebits, key = { it.id }) { item ->
                DebitItemCard(
                    item = item,
                    currencySymbol = currencySymbol,
                    onTogglePaid = { viewModel.toggleDebitPaid(item) },
                    onToggleCreditCard = { viewModel.toggleDebitCreditCardStatus(item) },
                    onToggleRecurring = { viewModel.toggleDebitRecurring(item) },
                    onEdit = { editingDebitItem = item },
                    onDelete = { expensePendingDelete = item },
                    customCategories = customCategories
                )
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
                            if (creditsViewMode == "LIST") {
                                Text(
                                    text = "List view · newest first",
                                    fontSize = 10.sp,
                                    color = IndTextMuted
                                )
                            }
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
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                DashIconChip("Category", Icons.Filled.GridView, creditsViewMode == "GROUP_BY_CATEGORY", Color(0xFF16A34A)) {
                                    creditsViewMode = "GROUP_BY_CATEGORY"
                                }
                                DashIconChip("List", Icons.Filled.ViewList, creditsViewMode == "LIST", Color(0xFF16A34A)) {
                                    creditsViewMode = "LIST"
                                }
                                DashIconChip("All", Icons.Filled.Apps, selectedCreditStatusFilter == "ALL", IndNavyHeader) {
                                    selectedCreditStatusFilter = "ALL"
                                }
                                DashIconChip("Received", Icons.Filled.CheckCircle, selectedCreditStatusFilter == "RECEIVED", IndGreenDark) {
                                    selectedCreditStatusFilter = "RECEIVED"
                                }
                                DashIconChip("Pending", Icons.Filled.HourglassTop, selectedCreditStatusFilter == "PENDING", Color(0xFFD97706)) {
                                    selectedCreditStatusFilter = "PENDING"
                                }
                                if (creditFiltersActive) {
                                    DashIconChip("Clear", Icons.Filled.Close, false, IndRed) {
                                        selectedCreditStatusFilter = "ALL"
                                        selectedCreditCategoryFilter = "ALL"
                                    }
                                }
                            }

                            if (creditCategoryTotals.isNotEmpty()) {
                                Text(
                                    text = "By category",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = IndTextMuted,
                                    modifier = Modifier.padding(start = 2.dp, top = 2.dp)
                                )
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    creditCategoryTotals.forEach { (category, total, count) ->
                                        val selected = selectedCreditCategoryFilter.equals(category, true)
                                        CategoryTotalPill(
                                            category = category,
                                            totalLabel = "+$currencySymbol${"%,.0f".format(total)}",
                                            count = count,
                                            selected = selected,
                                            selectedColor = Color(0xFF16A34A)
                                        ) {
                                            selectedCreditCategoryFilter = if (selected) "ALL" else category
                                        }
                                    }
                                }
                            }
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
            allHistoricalDebits = allDebits,
            onDismiss = { showAddDebitDialog = false },
            onSaveBatch = { batch ->
                viewModel.insertDebits(batch)
                showAddDebitDialog = false
            },
            onOpenAddCustomCategory = { showAddCustomCategoryDialog = true }
        )
    }

    if (showAddCustomCategoryDialog) {
        AddCustomCategoryDialog(
            onDismiss = { showAddCustomCategoryDialog = false },
            onSave = { name, icon, color ->
                viewModel.addCustomCategory(name, icon, color)
                showAddCustomCategoryDialog = false
            }
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
            allHistoricalDebits = allDebits,
            editingDebit = item,
            onDismiss = { editingDebitItem = null },
            onSaveBatch = { batch ->
                batch.firstOrNull()?.let { viewModel.updateDebit(it) }
                editingDebitItem = null
            },
            onOpenAddCustomCategory = { showAddCustomCategoryDialog = true }
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
    onDelete: () -> Unit,
    customCategories: List<com.example.data.model.CustomCategory> = emptyList()
) {
    val categoryLabel = debitCategoryDisplayLabel(item)
    val storedIcon = debitItemStoredIcon(item, customCategories)
    val isChit = item.category.equals("CHIT", ignoreCase = true) || item.title.contains("CHIT", ignoreCase = true)
    val isLoan = com.example.ui.components.isLoanOrEmi(item.category) || item.principalAmount > 0.0

    // Calculate Amortization Outstanding Balance for Loans
    val outstandingBalance = remember(item.principalAmount, item.interestRate, item.totalTenureMonths, item.currentMonthTenure, item.isPaid) {
        if (item.principalAmount <= 0.0 || item.totalTenureMonths <= 0) return@remember 0.0
        val p = item.principalAmount
        val n = item.totalTenureMonths
        val m = if (item.isPaid) item.currentMonthTenure else (item.currentMonthTenure - 1).coerceAtLeast(0)
        val r = (item.interestRate / 12.0) / 100.0

        if (r > 0.0) {
            val powN = Math.pow(1.0 + r, n.toDouble())
            val powM = Math.pow(1.0 + r, m.toDouble())
            (p * (powN - powM) / (powN - 1.0)).coerceAtLeast(0.0)
        } else {
            (p - (p * m.toDouble() / n.toDouble())).coerceAtLeast(0.0)
        }
    }

    IndCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    CategoryIconBadge(
                        categoryKey = categoryLabel,
                        storedIconName = storedIcon
                    )
                    Column(modifier = Modifier.clickable { onEdit() }) {
                        Text(item.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = IndTextPrimary)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val isCcItem = item.isActualCreditCardTransaction()
                            Text(
                                text = "$categoryLabel • Due Day ${item.dueDateDay}" + if (isCcItem) " • 💳 CC" else "",
                                fontSize = 11.sp,
                                color = if (isCcItem) Color(0xFF7C3AED) else IndTextSecondary
                            )
                            if (item.isRecurring) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFFE0F2FE)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Repeat,
                                            contentDescription = "Recurring",
                                            tint = Color(0xFF0284C7),
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Text(
                                            text = "Recurring",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0284C7)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "$currencySymbol${"%,.0f".format(item.amount)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (item.isPaid) IndGreenDark else IndRed
                        )
                    }
                }
            }

            // Render Special Chit / Loan Status Badges
            if (isChit && item.totalTenureMonths > 0) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFFEF3C7),
                    border = BorderStroke(0.5.dp, Color(0xFFD97706))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Filled.GroupWork, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(12.dp))
                        Text(
                            text = "Chit Installment: Month ${item.currentMonthTenure} of ${item.totalTenureMonths}",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB45309)
                        )
                    }
                }
            }

            if (isLoan && item.principalAmount > 0.0) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFECFDF5),
                    border = BorderStroke(0.5.dp, IndGreen)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Filled.AccountBalance, contentDescription = null, tint = IndGreenDark, modifier = Modifier.size(12.dp))
                        Text(
                            text = "Outstanding Loan Balance: $currencySymbol${"%,.0f".format(outstandingBalance)}" +
                                   if (item.totalTenureMonths > 0) " (Month ${item.currentMonthTenure}/${item.totalTenureMonths})" else "",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndGreenDark
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Quick Recurring Toggle Button
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (item.isRecurring) Color(0xFFE0F2FE) else IndSurface,
                        border = BorderStroke(0.5.dp, if (item.isRecurring) Color(0xFF0284C7) else IndBorder),
                        modifier = Modifier
                            .clickable(onClick = onToggleRecurring)
                            .testTag("toggle_recurring_${item.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Repeat,
                                contentDescription = "Recurring Toggle",
                                tint = if (item.isRecurring) Color(0xFF0284C7) else IndTextSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    // Quick Credit Card Toggle Button
                    val isCc = item.isActualCreditCardTransaction()
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isCc) Color(0xFFEDE9FE) else IndSurface,
                        border = BorderStroke(0.5.dp, if (isCc) Color(0xFF7C3AED) else IndBorder),
                        modifier = Modifier
                            .clickable(onClick = onToggleCreditCard)
                            .testTag("toggle_cc_${item.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CreditCard,
                                contentDescription = "CC Toggle",
                                tint = if (isCc) Color(0xFF7C3AED) else IndTextSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (isCc) "CC" else "No CC",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isCc) Color(0xFF7C3AED) else IndTextSecondary
                            )
                        }
                    }

                    // Paid Radio / Toggle Button
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (item.isPaid) IndGreenLight else IndAmberLight,
                        modifier = Modifier
                            .clickable(onClick = onTogglePaid)
                            .testTag("toggle_paid_${item.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            RadioButton(
                                selected = item.isPaid,
                                onClick = onTogglePaid,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = IndGreenDark,
                                    unselectedColor = Color(0xFFB45309)
                                ),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (item.isPaid) "Paid" else "Yet to Pay",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (item.isPaid) IndGreenDark else Color(0xFFB45309)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Explicit Labeled Edit Chip
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFEFF6FF),
                        border = BorderStroke(0.5.dp, IndBlue),
                        modifier = Modifier
                            .clickable(onClick = onEdit)
                            .testTag("edit_debit_chip_${item.id}")
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

                    // Delete Button
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp).testTag("delete_debit_${item.id}")
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = IndTextMuted, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

private fun prettyCategory(raw: String): String {
    return raw.replace('_', ' ').lowercase().split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
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
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = IndTextSecondary)
        }
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = color)
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
    val total: Double,
    val count: Int
)

@Composable
private fun CategoryTotalPill(
    category: String,
    stylingKey: String = category,
    storedIconName: String? = null,
    totalLabel: String,
    count: Int,
    selected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit
) {
    val styling = getCategoryStyling(stylingKey, storedIconName = storedIconName)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) selectedColor else IndBackground,
        border = BorderStroke(1.dp, if (selected) selectedColor else IndBorderSubtle),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (selected) Color.White.copy(alpha = 0.18f) else styling.second),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    styling.first,
                    null,
                    tint = if (selected) Color.White else styling.third,
                    modifier = Modifier.size(14.dp)
                )
            }
            Column {
                Text(
                    prettyCategory(category),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) Color.White.copy(alpha = 0.85f) else IndTextSecondary,
                    maxLines = 1
                )
                Text(
                    totalLabel,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (selected) Color.White else IndNavyHeader
                )
                Text(
                    "$count",
                    fontSize = 9.sp,
                    color = if (selected) Color.White.copy(alpha = 0.7f) else IndTextMuted
                )
            }
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
    onDelete: () -> Unit
) {
    IndCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    CategoryIconBadge(categoryKey = item.category)
                    Column(modifier = Modifier.clickable { onEdit() }) {
                        Text(item.title.ifBlank { item.name }, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = IndTextPrimary)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = item.category,
                                fontSize = 11.sp,
                                color = IndTextSecondary
                            )
                            if (item.isRecurring) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = IndGreenLight
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Repeat,
                                            contentDescription = "Recurring",
                                            tint = IndGreenDark,
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Text(
                                            text = "Recurring",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = IndGreenDark
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "+$currencySymbol${"%,.0f".format(item.amount)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF16A34A)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Received Radio / Toggle Button
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (item.isReceived) IndGreenLight else IndAmberLight,
                        modifier = Modifier
                            .clickable(onClick = onToggleReceived)
                            .testTag("toggle_received_${item.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            RadioButton(
                                selected = item.isReceived,
                                onClick = onToggleReceived,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = IndGreenDark,
                                    unselectedColor = Color(0xFFB45309)
                                ),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (item.isReceived) "Received ✓" else "Pending Inflow",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (item.isReceived) IndGreenDark else Color(0xFFB45309)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (item.isRecurring) IndGreenLight else IndSurface,
                        border = BorderStroke(0.5.dp, if (item.isRecurring) IndGreen else IndBorder),
                        modifier = Modifier
                            .clickable(onClick = onToggleRecurring)
                            .testTag("toggle_recurring_credit_${item.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Repeat,
                                contentDescription = "Recurring Toggle",
                                tint = if (item.isRecurring) IndGreenDark else IndTextSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Explicit Labeled Edit Chip
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFEFF6FF),
                        border = BorderStroke(0.5.dp, IndBlue),
                        modifier = Modifier
                            .clickable(onClick = onEdit)
                            .testTag("edit_credit_chip_${item.id}")
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

                    // Delete Button
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp).testTag("delete_credit_${item.id}")
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = IndTextMuted, modifier = Modifier.size(14.dp))
                    }
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
