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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.DebitItem
import com.example.data.model.BankTransactionRecord
import com.example.data.model.MonthlyFinanceSummary
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

    val selectedSpecificDay by viewModel.selectedSpecificDay.collectAsStateWithLifecycle()

    var selectedStatusFilter by remember { mutableStateOf("ALL") } // "ALL", "YET_TO_PAY", "ALREADY_PAID"
    var creditCardOnlyFilter by remember { mutableStateOf(false) }
    var selectedCategoryFilter by remember { mutableStateOf("ALL") }
    var dashboardViewMode by remember { mutableStateOf("GROUP_BY_CATEGORY") } // "GROUP_BY_CATEGORY" or "LIST"

    val filteredDebits = remember(currentDebits, selectedStatusFilter, creditCardOnlyFilter, selectedCategoryFilter, selectedSpecificDay) {
        currentDebits.filter { item ->
            val matchesStatus = when (selectedStatusFilter) {
                "YET_TO_PAY" -> !item.isPaid
                "ALREADY_PAID" -> item.isPaid
                else -> true
            }
            val matchesCreditCard = if (creditCardOnlyFilter) {
                item.isPaidViaCreditCard || 
                item.category.contains("CREDIT", true) || 
                item.title.contains("CREDIT", true) || 
                item.category.contains("BILL", true) || 
                item.category.contains("CARD", true) ||
                item.notes.contains("CREDIT", true) ||
                item.notes.contains("CC", true)
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
            matchesStatus && matchesCreditCard && matchesCategory && matchesDay
        }
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
                            .clickable { showManageIncomeDialog = true }
                            .testTag("edit_income_section")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Total Income", style = MaterialTheme.typography.labelSmall, color = IndTextSecondary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Edit Income",
                                tint = IndBlue,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Text(
                            text = if (!isFinancialSafetyVisible) "$currencySymbol★★★★★★" else "$currencySymbol${"%,.0f".format(summary.totalIncome)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = IndGreenDark
                        )
                    }
                    Column {
                        Text("Total Outflow", style = MaterialTheme.typography.labelSmall, color = IndTextSecondary)
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
            val filtersActive = selectedStatusFilter != "ALL" || creditCardOnlyFilter ||
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

                        FilterChip(
                            selected = creditCardOnlyFilter,
                            onClick = { creditCardOnlyFilter = !creditCardOnlyFilter },
                            label = { Text("Credit card & bills", fontSize = 12.sp) }
                        )

                        if (filtersActive) {
                            TextButton(onClick = {
                                selectedStatusFilter = "ALL"
                                creditCardOnlyFilter = false
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
        } else {
            val dashboardRows = if (dashboardViewMode == "GROUP_BY_CATEGORY") {
                val list = mutableListOf<DashboardRow>()
                filteredDebits.groupBy { it.category }.forEach { (category, itemsList) ->
                    list.add(DashboardRow.CategoryHeader(category, itemsList.sumOf { it.amount }, itemsList.size))
                    itemsList.forEach { debit ->
                        list.add(DashboardRow.Transaction(debit))
                    }
                }
                list
            } else {
                filteredDebits.map { DashboardRow.Transaction(it) }
            }

            items(dashboardRows, key = { row ->
                when (row) {
                    is DashboardRow.CategoryHeader -> "header_${row.category}"
                    is DashboardRow.Transaction -> "txn_${row.item.id}"
                }
            }) { row ->
                when (row) {
                    is DashboardRow.CategoryHeader -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = IndBlueLight,
                            border = BorderStroke(1.dp, IndBlue.copy(alpha = 0.18f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CategoryIconBadge(categoryKey = row.category, badgeSize = 28.dp, iconSize = 16.dp)
                                    Column {
                                        Text(
                                            text = prettyCategory(row.category),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = IndNavyHeader
                                        )
                                        Text(
                                            text = "${row.count} items",
                                            fontSize = 11.sp,
                                            color = IndTextSecondary
                                        )
                                    }
                                }
                                Text(
                                    text = "$currencySymbol${"%,.0f".format(row.total)}",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = IndBlueDark
                                )
                            }
                        }
                    }
                    is DashboardRow.Transaction -> {
                        DebitItemCard(
                            item = row.item,
                            currencySymbol = currencySymbol,
                            onTogglePaid = { viewModel.toggleDebitPaid(row.item) },
                            onToggleCreditCard = { viewModel.toggleDebitCreditCardStatus(row.item) },
                            onToggleRecurring = { viewModel.toggleDebitRecurring(row.item) },
                            onEdit = { editingDebitItem = row.item },
                            onDelete = { viewModel.deleteDebit(row.item) }
                        )
                    }

                }
            }
        }
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
                            Text(
                                text = "${item.category} • Due Day ${item.dueDateDay}" + if (item.isPaidViaCreditCard) " • 💳 CC" else "",
                                fontSize = 11.sp,
                                color = if (item.isPaidViaCreditCard) Color(0xFF7C3AED) else IndTextSecondary
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

                    // Prominent Top-Right Edit Icon
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("edit_debit_top_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit Debit",
                            tint = IndBlue,
                            modifier = Modifier.size(16.dp)
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
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (item.isPaidViaCreditCard) Color(0xFFEDE9FE) else IndSurface,
                        border = BorderStroke(0.5.dp, if (item.isPaidViaCreditCard) Color(0xFF7C3AED) else IndBorder),
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
                                tint = if (item.isPaidViaCreditCard) Color(0xFF7C3AED) else IndTextSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (item.isPaidViaCreditCard) "CC" else "No CC",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (item.isPaidViaCreditCard) Color(0xFF7C3AED) else IndTextSecondary
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

private sealed class DashboardRow {
    data class CategoryHeader(val category: String, val total: Double, val count: Int) : DashboardRow()
    data class Transaction(val item: DebitItem) : DashboardRow()
}
