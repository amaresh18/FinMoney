package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
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
    val currentSalaryRecord by viewModel.currentSalaryRecord.collectAsStateWithLifecycle()
    val pendingBankTransactions by viewModel.pendingBankTransactions.collectAsStateWithLifecycle()

    val monthName = FinMoneyViewModel.getMonthName(month)

    var showAddCreditDialog by remember { mutableStateOf(false) }
    var showManageIncomeDialog by remember { mutableStateOf(false) }
    var showAddDebitDialog by remember { mutableStateOf(false) }
    var showImportStatementDialog by remember { mutableStateOf(false) }
    var editingDebitItem by remember { mutableStateOf<DebitItem?>(null) }
    var editingCreditSource by remember { mutableStateOf<IncomeSource?>(null) }

    val selectedSpecificDay by viewModel.selectedSpecificDay.collectAsStateWithLifecycle()

    var mainLedgerTab by remember { mutableStateOf("DEBITS") } // "DEBITS" or "CREDITS"

    // Debits State
    var selectedStatusFilter by remember { mutableStateOf("ALL") } // "ALL", "YET_TO_PAY", "ALREADY_PAID"
    var creditCardOnlyFilter by remember { mutableStateOf(false) }
    var recurringOnlyFilter by remember { mutableStateOf(false) }
    var selectedCategoryFilter by remember { mutableStateOf("ALL") }
    var dashboardViewMode by remember { mutableStateOf("GROUP_BY_CATEGORY") } // "GROUP_BY_CATEGORY" or "LIST"
    var selectedCategoryForDrillDown by remember { mutableStateOf<CategorySummary?>(null) }
    var expandedCategoryKeys by remember { mutableStateOf(setOf<String>()) }

    // Credits State
    var selectedCreditStatusFilter by remember { mutableStateOf("ALL") } // "ALL", "RECEIVED", "PENDING"
    var selectedCreditCategoryFilter by remember { mutableStateOf("ALL") }
    var creditsViewMode by remember { mutableStateOf("GROUP_BY_CATEGORY") } // "GROUP_BY_CATEGORY" or "LIST"
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
            // Most recent credit first
            list.sortedByDescending { it.receivedDateTimestamp ?: 0L }
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

    val filteredDebits = remember(currentDebits, selectedStatusFilter, creditCardOnlyFilter, recurringOnlyFilter, selectedCategoryFilter, selectedSpecificDay, dashboardViewMode) {
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
            val matchesCategory = if (selectedCategoryFilter == "ALL") {
                true
            } else {
                item.category.equals(selectedCategoryFilter, true)
            }
            val matchesDay = if (selectedSpecificDay != null) {
                item.dueDateDay == selectedSpecificDay
            } else {
                true
            }
            matchesStatus && matchesCreditCard && matchesRecurring && matchesCategory && matchesDay
        }

        if (dashboardViewMode == "LIST") {
            // Most recent transaction at the top for List view
            list.sortedWith(compareByDescending<DebitItem> { it.createdAt }.thenByDescending { it.id })
        } else {
            list
        }
    }

    val totalFilteredDebits = remember(filteredDebits) { filteredDebits.sumOf { it.amount } }
    val dashboardCategorySummaries = remember(filteredDebits, summary.categoryBreakdown, totalFilteredDebits) {
        filteredDebits
            .groupBy { it.category.ifBlank { "GENERAL" } }
            .map { (catName, items) ->
                val catTotal = items.sumOf { it.amount }
                val catPaid = items.filter { it.isPaid }.sumOf { it.amount }
                val yetToPay = catTotal - catPaid
                val pct = if (totalFilteredDebits > 0) (catTotal / totalFilteredDebits).toFloat() else 0f
                val matchingSummary = summary.categoryBreakdown.find {
                    it.categoryKey.equals(catName, ignoreCase = true) ||
                    it.categoryName.equals(catName, ignoreCase = true)
                }
                val colorHex = matchingSummary?.colorHex ?: "#00B386"
                val iconKey = matchingSummary?.iconKey.takeIf { !it.isNullOrBlank() } ?: catName
                CategorySummary(
                    categoryName = catName,
                    categoryKey = catName.uppercase(),
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
        verticalArrangement = Arrangement.spacedBy(AppTheme.dimensions.medium),
        contentPadding = PaddingValues(top = AppTheme.dimensions.medium, bottom = 96.dp)
    ) {
        // Google Calendar Month Selector Header
        item {
            var showCalendarPicker by remember { mutableStateOf(false) }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = IndSurface,
                border = BorderStroke(1.dp, IndBlue.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showCalendarPicker = true }
                    .testTag("calendar_header_button")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = IndBlueLight,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.CalendarMonth,
                                    contentDescription = null,
                                    tint = IndBlue,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "$monthName $year Dashboard",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = IndNavyHeader
                            )
                            Text(
                                text = "Google Calendar Date Filter ▾",
                                style = MaterialTheme.typography.labelSmall,
                                color = IndBlue,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = { viewModel.previousMonth() }) {
                            Icon(Icons.Filled.ChevronLeft, contentDescription = "Prev Month", tint = IndTextSecondary)
                        }
                        IconButton(onClick = { viewModel.nextMonth() }) {
                            Icon(Icons.Filled.ChevronRight, contentDescription = "Next Month", tint = IndTextSecondary)
                        }
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

        // Summary Card (Aggregate View)
        item {
            IndCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MONTHLY AGGREGATE SUMMARY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = IndBlueDark,
                        fontSize = 11.sp
                    )
                    IconButton(
                        onClick = { viewModel.toggleFinancialSafetyVisible() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isFinancialSafetyVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            contentDescription = "Toggle Privacy",
                            tint = if (isFinancialSafetyVisible) IndBlue else IndTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .clickable { mainLedgerTab = "CREDITS" }
                            .testTag("edit_income_section")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Total Income ▾", style = MaterialTheme.typography.labelSmall, color = IndGreenDark, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = if (!isFinancialSafetyVisible) "$currencySymbol★★★★★★" else "$currencySymbol${"%,.0f".format(summary.totalIncome)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = IndGreenDark
                        )
                    }
                    Column(
                        modifier = Modifier.clickable { mainLedgerTab = "DEBITS" }
                    ) {
                        Text("Total Outflow ▾", style = MaterialTheme.typography.labelSmall, color = IndRed, fontWeight = FontWeight.Bold)
                        Text(
                            text = "$currencySymbol${"%,.0f".format(summary.totalDebits)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = IndRed
                        )
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

        // Quick Action Buttons: Add Credit & Add Debit
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IndPrimaryButton(
                    text = "Add Credit",
                    onClick = { showAddCreditDialog = true },
                    icon = Icons.Filled.Add,
                    backgroundColor = Color(0xFF16A34A),
                    testTag = "add_credit_dashboard_button",
                    modifier = Modifier.weight(1f)
                )

                IndPrimaryButton(
                    text = "Add Debit",
                    onClick = { onOpenAddDebit() },
                    icon = Icons.Filled.Add,
                    backgroundColor = IndBlue,
                    testTag = "add_debit_dashboard_button",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Secondary Utility Bar: Import Statement & Prefill Recurring Bills
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFEFF6FF),
                    border = BorderStroke(1.dp, IndBlue.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showImportStatementDialog = true }
                        .testTag("import_statement_dashboard_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = "Import Statement",
                            tint = IndBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Import Statement",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndBlue
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF0FDF4),
                    border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            viewModel.prefillRecurringDebitsForMonth(month, year)
                        }
                        .testTag("prefill_recurring_dashboard_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = "Prefill Recurring",
                            tint = Color(0xFF16A34A),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Prefill Recurring",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF16A34A)
                        )
                    }
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
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ArrowUpward,
                                contentDescription = "Debits",
                                tint = if (isDebits) Color.White else IndRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Expenses / Debits",
                                fontSize = 13.sp,
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
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ArrowDownward,
                                contentDescription = "Credits",
                                tint = if (isCredits) Color.White else Color(0xFF16A34A),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Credits / Income",
                                fontSize = 13.sp,
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
            val categoryTotals = remember(filteredDebits) {
                filteredDebits
                    .groupBy { it.category.ifBlank { "GENERAL" } }
                    .map { (category, items) ->
                        Triple(category, items.sumOf { it.amount }, items.size)
                    }
                    .sortedByDescending { it.second }
            }
            val visibleTotal = filteredDebits.sumOf { it.amount }
            val filtersActive = selectedStatusFilter != "ALL" || creditCardOnlyFilter || recurringOnlyFilter ||
                selectedCategoryFilter != "ALL" || selectedSpecificDay != null

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Spending",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = IndNavyHeader
                        )
                        Text(
                            text = "${filteredDebits.size} of ${currentDebits.size} items",
                            style = MaterialTheme.typography.labelSmall,
                            color = IndTextSecondary
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "$currencySymbol${"%,.0f".format(visibleTotal)}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = IndNavyHeader
                        )
                        Text("Filtered total", fontSize = 11.sp, color = IndTextSecondary)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = IndSurface,
                    border = BorderStroke(1.dp, IndBorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("View", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IndTextSecondary)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(IndBackground)
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                "GROUP_BY_CATEGORY" to "By category",
                                "LIST" to "All items"
                            ).forEach { (mode, label) ->
                                val selected = dashboardViewMode == mode
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { dashboardViewMode = mode },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) IndBlue else Color.Transparent
                                ) {
                                    Text(
                                        text = label,
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (selected) Color.White else IndTextSecondary
                                    )
                                }
                            }
                        }

                        Text("Payment", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IndTextSecondary)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "ALL" to "All",
                                "YET_TO_PAY" to "Unpaid",
                                "ALREADY_PAID" to "Paid"
                            ).forEach { (key, label) ->
                                FilterChip(
                                    selected = selectedStatusFilter == key,
                                    onClick = { selectedStatusFilter = key },
                                    label = { Text(label, fontSize = 12.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Text("Type Filters", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IndTextSecondary)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = creditCardOnlyFilter,
                                onClick = { creditCardOnlyFilter = !creditCardOnlyFilter },
                                label = { Text("Credit Card", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )

                            FilterChip(
                                selected = recurringOnlyFilter,
                                onClick = { recurringOnlyFilter = !recurringOnlyFilter },
                                label = { Text("Recurring", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (filtersActive) {
                            TextButton(onClick = {
                                selectedStatusFilter = "ALL"
                                creditCardOnlyFilter = false
                                recurringOnlyFilter = false
                                selectedCategoryFilter = "ALL"
                                viewModel.setMonthYear(month, year, null)
                            }) {
                                Text("Clear filters")
                            }
                        }
                    }
                }

                if (categoryTotals.isNotEmpty()) {
                    Text(
                        "Totals by category",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = IndTextSecondary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        categoryTotals.forEach { (category, total, count) ->
                            val selected = selectedCategoryFilter.equals(category, true)
                            Surface(
                                modifier = Modifier
                                    .width(148.dp)
                                    .clickable {
                                        selectedCategoryFilter = if (selected) "ALL" else category
                                    },
                                shape = RoundedCornerShape(16.dp),
                                color = if (selected) IndNavyHeader else IndSurface,
                                border = BorderStroke(1.dp, if (selected) IndNavyHeader else IndBorderSubtle)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        prettyCategory(category),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (selected) Color.White.copy(alpha = 0.8f) else IndTextSecondary,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "$currencySymbol${"%,.0f".format(total)}",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (selected) Color.White else IndNavyHeader
                                    )
                                    Text(
                                        "$count items",
                                        fontSize = 11.sp,
                                        color = if (selected) IndBlueLight else IndTextMuted
                                    )
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
            // Spend by Category Header identical to Analytics
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Spend by Category (${dashboardCategorySummaries.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = IndTextPrimary
                    )
                    Text(
                        text = "Tap to inspect & edit items",
                        style = MaterialTheme.typography.labelSmall,
                        color = IndTextMuted
                    )
                }
            }

            items(dashboardCategorySummaries, key = { "cat_${it.categoryKey}" }) { cat ->
                val categoryShare = if (totalFilteredDebits > 0) (cat.totalAmount / totalFilteredDebits).toFloat() else 0f
                val isExpanded = expandedCategoryKeys.contains(cat.categoryKey)
                val categoryItems = remember(filteredDebits, cat.categoryKey) {
                    filteredDebits.filter {
                        it.category.equals(cat.categoryKey, ignoreCase = true) ||
                        it.category.equals(cat.categoryName, ignoreCase = true) ||
                        it.customCategoryName.equals(cat.categoryName, ignoreCase = true)
                    }
                }

                IndCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dashboard_category_${cat.categoryKey}"),
                    onClick = { selectedCategoryForDrillDown = cat }
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
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            prettyCategory(cat.categoryName),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = IndTextPrimary
                                        )
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowForwardIos,
                                            contentDescription = "Drill down",
                                            tint = IndTextMuted,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
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
                                        onDelete = { viewModel.deleteDebit(item) }
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
                    onDelete = { viewModel.deleteDebit(item) }
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Income & Credits Inflow",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = IndNavyHeader
                            )
                            Text(
                                text = "${filteredCredits.size} of ${currentIncomeSources.size} credit sources",
                                style = MaterialTheme.typography.labelSmall,
                                color = IndTextSecondary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "+$currencySymbol${"%,.0f".format(visibleCreditTotal)}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                color = Color(0xFF16A34A)
                            )
                            Text("Filtered total", fontSize = 11.sp, color = IndTextSecondary)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = IndSurface,
                        border = BorderStroke(1.dp, IndBorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("View", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IndTextSecondary)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(IndBackground)
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(
                                    "GROUP_BY_CATEGORY" to "By category",
                                    "LIST" to "All items"
                                ).forEach { (mode, label) ->
                                    val selected = creditsViewMode == mode
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { creditsViewMode = mode },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (selected) Color(0xFF16A34A) else Color.Transparent
                                    ) {
                                        Text(
                                            text = label,
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (selected) Color.White else IndTextSecondary
                                        )
                                    }
                                }
                            }

                            Text("Status", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IndTextSecondary)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "ALL" to "All",
                                    "RECEIVED" to "Received",
                                    "PENDING" to "Pending"
                                ).forEach { (key, label) ->
                                    FilterChip(
                                        selected = selectedCreditStatusFilter == key,
                                        onClick = { selectedCreditStatusFilter = key },
                                        label = { Text(label, fontSize = 12.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            if (creditFiltersActive) {
                                TextButton(onClick = {
                                    selectedCreditStatusFilter = "ALL"
                                    selectedCreditCategoryFilter = "ALL"
                                }) {
                                    Text("Clear filters")
                                }
                            }
                        }
                    }

                    if (creditCategoryTotals.isNotEmpty()) {
                        Text(
                            "Totals by category",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndTextSecondary
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            creditCategoryTotals.forEach { (category, total, count) ->
                                val selected = selectedCreditCategoryFilter.equals(category, true)
                                Surface(
                                    modifier = Modifier
                                        .width(148.dp)
                                        .clickable {
                                            selectedCreditCategoryFilter = if (selected) "ALL" else category
                                        },
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (selected) Color(0xFF16A34A) else IndSurface,
                                    border = BorderStroke(1.dp, if (selected) Color(0xFF16A34A) else IndBorderSubtle)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            prettyCategory(category),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (selected) Color.White.copy(alpha = 0.8f) else IndTextSecondary,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            "+$currencySymbol${"%,.0f".format(total)}",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (selected) Color.White else Color(0xFF16A34A)
                                        )
                                        Text(
                                            "$count items",
                                            fontSize = 11.sp,
                                            color = if (selected) Color.White.copy(alpha = 0.8f) else IndTextMuted
                                        )
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
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Inflow by Category (${creditsCategorySummaries.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = IndTextPrimary
                        )
                        Text(
                            text = "Tap to expand category items",
                            style = MaterialTheme.typography.labelSmall,
                            color = IndTextMuted
                        )
                    }
                }

                items(creditsCategorySummaries, key = { "credit_cat_${it.categoryKey}" }) { cat ->
                    val isExpanded = expandedCreditCategoryKeys.contains(cat.categoryKey)
                    val categoryItems = remember(filteredCredits, cat.categoryKey) {
                        filteredCredits.filter {
                            it.category.equals(cat.categoryKey, ignoreCase = true) ||
                            it.category.equals(cat.categoryName, ignoreCase = true)
                        }
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
                                    categoryItems.sortedByDescending { it.receivedDateTimestamp ?: 0L }.forEach { item ->
                                        CreditItemCard(
                                            item = item,
                                            currencySymbol = currencySymbol,
                                            onToggleReceived = { viewModel.toggleIncomeReceived(item.id, month, year) },
                                            onEdit = { editingCreditSource = item },
                                            onDelete = { viewModel.deleteIncomeSource(item.id, month, year) }
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
                        onEdit = { editingCreditSource = item },
                        onDelete = { viewModel.deleteIncomeSource(item.id, month, year) }
                    )
                }
            }
        }
    }

    // Category Drill-Down Dialog with full edit, delete, and payment toggle support
    selectedCategoryForDrillDown?.let { cat ->
        val matchingDebits = remember(currentDebits, cat, selectedStatusFilter, creditCardOnlyFilter, selectedSpecificDay) {
            filteredDebits.filter {
                it.category.equals(cat.categoryKey, ignoreCase = true) ||
                it.category.equals(cat.categoryName, ignoreCase = true) ||
                it.customCategoryName.equals(cat.categoryName, ignoreCase = true)
            }
        }

        val updatedCat = remember(matchingDebits, cat) {
            val total = matchingDebits.sumOf { it.amount }
            val paid = matchingDebits.filter { it.isPaid }.sumOf { it.amount }
            cat.copy(
                totalAmount = total,
                paidAmount = paid,
                yetToPayAmount = total - paid,
                itemCount = matchingDebits.size,
                paidCount = matchingDebits.count { it.isPaid }
            )
        }

        CategoryDrillDownDialog(
            categorySummary = updatedCat,
            debitItems = matchingDebits,
            monthName = monthName,
            year = year,
            currencySymbol = currencySymbol,
            onDismiss = { selectedCategoryForDrillDown = null },
            onEditItem = { item -> editingDebitItem = item },
            onDeleteItem = { item -> viewModel.deleteDebit(item) },
            onTogglePaid = { item -> viewModel.toggleDebitPaid(item) },
            onToggleCreditCard = { item -> viewModel.toggleDebitCreditCardStatus(item) },
            onToggleRecurring = { item -> viewModel.toggleDebitRecurring(item) }
        )
    }

    // Manage Income & Credit Dialog
    if (showManageIncomeDialog || showAddCreditDialog) {
        ManageIncomeCreditDialog(
            incomeSources = summary.incomeSources,
            baseSalary = currentSalaryRecord?.baseSalary ?: (summary.incomeSources.find { it.category == "SALARY" || it.id == "1" }?.amount ?: 85000.0),
            onDismiss = {
                showManageIncomeDialog = false
                showAddCreditDialog = false
            },
            onSaveNewCredit = { title, amount, category, dateString ->
                viewModel.addIncomeCredit(title, amount, category, dateString, month, year)
                showManageIncomeDialog = false
                showAddCreditDialog = false
            },
            onUpdateCredit = { id, title, amount, category, dateString ->
                viewModel.updateIncomeSource(id, title, amount, category, dateString, month, year)
                showManageIncomeDialog = false
                showAddCreditDialog = false
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
            customCategories = emptyList(),
            allHistoricalDebits = allDebits,
            onDismiss = { showAddDebitDialog = false },
            onSaveBatch = { batch ->
                viewModel.insertDebits(batch)
                showAddDebitDialog = false
            },
            onOpenAddCustomCategory = {}
        )
    }

    // Edit Debit Dialog
    if (editingDebitItem != null) {
        val item = editingDebitItem!!
        val amtStr = if (item.amount > 0) (if (item.amount % 1.0 == 0.0) item.amount.toInt().toString() else item.amount.toString()) else ""
        val principalStr = if (item.principalAmount > 0) (if (item.principalAmount % 1.0 == 0.0) item.principalAmount.toInt().toString() else item.principalAmount.toString()) else ""
        val rateStr = if (item.interestRate > 0) item.interestRate.toString() else ""
        val totalTenureStr = if (item.totalTenureMonths > 0) item.totalTenureMonths.toString() else ""
        val currentTenureStr = if (item.currentMonthTenure > 0) item.currentMonthTenure.toString() else "1"

        AddEditDebitDialog(
            initialTitle = item.title,
            initialAmount = amtStr,
            initialCategory = item.category,
            initialDueDate = item.dueDateDay.toString(),
            initialNotes = item.notes,
            initialPrincipalAmount = principalStr,
            initialInterestRate = rateStr,
            initialTotalTenureMonths = totalTenureStr,
            initialCurrentMonthTenure = currentTenureStr,
            initialIsPaidViaCreditCard = item.isPaidViaCreditCard,
            initialIsRecurring = item.isRecurring,
            onDismiss = { editingDebitItem = null },
            onSave = { title, amount, category, dueDate, notes, principalAmount, interestRate, totalTenureMonths, currentMonthTenure, isPaidViaCreditCard, isRecurring ->
                val dayInt = dueDate.filter { it.isDigit() }.toIntOrNull() ?: item.dueDateDay
                viewModel.updateDebit(
                    item.copy(
                        title = title,
                        amount = amount,
                        category = category,
                        dueDateDay = dayInt,
                        notes = notes,
                        isPaidViaCreditCard = isPaidViaCreditCard,
                        isRecurring = isRecurring,
                        principalAmount = principalAmount,
                        interestRate = interestRate,
                        totalTenureMonths = totalTenureMonths,
                        currentMonthTenure = currentMonthTenure
                    )
                )
                editingDebitItem = null
            }
        )
    }

    // Edit Credit Dialog
    if (editingCreditSource != null) {
        val src = editingCreditSource!!
        var editTitle by remember { mutableStateOf(src.title.ifBlank { src.name }) }
        var editAmountStr by remember { mutableStateOf(if (src.amount > 0) (if (src.amount % 1.0 == 0.0) src.amount.toInt().toString() else src.amount.toString()) else "") }
        var editCategory by remember { mutableStateOf(src.category) }

        IndDialog(
            onDismiss = { editingCreditSource = null },
            title = "Edit Credit Entry",
            subtitle = "Modify income or credit details",
            icon = Icons.Filled.Payments,
            iconTint = Color(0xFF16A34A),
            confirmButtonText = "Save Changes",
            onConfirm = {
                val amt = editAmountStr.toDoubleOrNull() ?: src.amount
                viewModel.updateIncomeSource(src.id, editTitle, amt, editCategory, null, month, year)
                editingCreditSource = null
            }
        ) {
            IndOutlinedTextField(
                value = editTitle,
                onValueChange = { editTitle = it },
                label = "Title / Source Payee",
                leadingIcon = Icons.Filled.Payments
            )
            IndOutlinedTextField(
                value = editAmountStr,
                onValueChange = { editAmountStr = it },
                label = "Amount (₹)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                leadingIcon = Icons.Filled.CurrencyRupee
            )
            IndCategoryDropdown(
                selectedCategory = editCategory,
                onCategorySelected = { editCategory = it },
                categories = com.example.data.model.IncomeCategory.entries.map { it.name },
                label = "Category"
            )
        }
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
    onDelete: () -> Unit
) {
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
                    CategoryIconBadge(categoryKey = item.category)
                    Column(modifier = Modifier.clickable { onEdit() }) {
                        Text(item.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = IndTextPrimary)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val isCcItem = item.isActualCreditCardTransaction()
                            Text(
                                text = "${item.category} • Due Day ${item.dueDateDay}" + if (isCcItem) " • 💳 CC" else "",
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
fun CreditItemCard(
    item: IncomeSource,
    currencySymbol: String,
    onToggleReceived: () -> Unit,
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
                        Text(
                            text = item.category,
                            fontSize = 11.sp,
                            color = IndTextSecondary
                        )
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
