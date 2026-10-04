package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.*
import com.example.ui.CategorySummary
import com.example.ui.FinMoneyViewModel
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun SalaryUtilityScreen(
    viewModel: FinMoneyViewModel,
    onOpenAddDebit: (initialCategory: String) -> Unit,
    onOpenAddCustomCategory: () -> Unit
) {
    val month by viewModel.currentMonth.collectAsStateWithLifecycle()
    val year by viewModel.currentYear.collectAsStateWithLifecycle()
    val currencySymbol by viewModel.currencySymbol.collectAsStateWithLifecycle()

    val salaryRecord by viewModel.currentSalaryRecord.collectAsStateWithLifecycle()
    val debits by viewModel.currentDebits.collectAsStateWithLifecycle()
    val customCategories by viewModel.customCategories.collectAsStateWithLifecycle()
    val summary by viewModel.monthlySummary.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var showSalaryDialog by remember { mutableStateOf(false) }
    var editingDebit by remember { mutableStateOf<DebitItem?>(null) }
    var debitToDelete by remember { mutableStateOf<DebitItem?>(null) }
    var selectedFilterCategory by remember { mutableStateOf<String?>("ALL") }

    val monthName = FinMoneyViewModel.getMonthName(month)

    // Listen to prefill messages
    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Debits sorted strictly from Day 1 to 31 upcoming
    val sortedDebits = remember(debits) {
        debits.sortedWith(compareBy<DebitItem> { it.dueDateDay }.thenBy { it.id })
    }

    val filteredDebits = remember(sortedDebits, selectedFilterCategory) {
        if (selectedFilterCategory == "ALL" || selectedFilterCategory == null) {
            sortedDebits
        } else {
            sortedDebits.filter { it.category == selectedFilterCategory }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
        ) {
            // 1. Month Navigation Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(IndSurface)
                        .border(1.dp, IndBorder, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.previousMonth() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous Month", tint = IndTextSecondary)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = IndBlue, modifier = Modifier.size(18.dp))
                        Text(
                            text = "$monthName $year",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = IndTextPrimary
                        )
                    }

                    IconButton(
                        onClick = { viewModel.nextMonth() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Next Month", tint = IndTextSecondary)
                    }
                }
            }

            // 2. Hero Available Disposable Cash & Financial Overview Card
            item {
                IndCard(
                    backgroundColor = IndSurface,
                    borderColor = IndBorder,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (summary.netFlexibleSpending >= 0) IndGreenLight else IndRedLight,
                                modifier = Modifier.padding(bottom = 6.dp)
                            ) {
                                Text(
                                    text = "AVAILABLE DISPOSABLE BALANCE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (summary.netFlexibleSpending >= 0) IndGreenDark else IndRedDark,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }

                            Text(
                                text = if (summary.totalIncome > 0) {
                                    "$currencySymbol${"%,.0f".format(summary.netFlexibleSpending)}"
                                } else "₹0 (Tap Enter Income)",
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (summary.netFlexibleSpending >= 0) IndTextPrimary else IndRed
                            )
                            Text(
                                text = "Remaining Cash After All Monthly Commitments",
                                style = MaterialTheme.typography.bodySmall,
                                color = IndTextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Income vs Total Debits row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(IndCardSecondary)
                            .border(1.dp, IndBorderSubtle, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(IndGreen))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Total Income & Credits", style = MaterialTheme.typography.labelSmall, color = IndTextSecondary)
                            }
                            Text(
                                text = "$currencySymbol${"%,.0f".format(summary.totalIncome)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = IndGreenDark
                            )
                        }

                        Box(modifier = Modifier.width(1.dp).height(36.dp).background(IndBorder))

                        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(IndRed))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Total Outflow Commitments", style = MaterialTheme.typography.labelSmall, color = IndTextSecondary)
                            }
                            Text(
                                text = "$currencySymbol${"%,.0f".format(summary.totalDebits)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = IndRed
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Paid vs Yet to Pay (Pending) Row (Requirement #5)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(IndBackground)
                            .border(1.dp, IndBorderSubtle, RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                shape = CircleShape,
                                color = IndGreenLight
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = IndGreenDark,
                                    modifier = Modifier.size(16.dp).padding(2.dp)
                                )
                            }
                            Column {
                                Text("Paid Debits", style = MaterialTheme.typography.labelSmall, color = IndTextSecondary, fontSize = 10.sp)
                                Text(
                                    text = "$currencySymbol${"%,.0f".format(summary.paidDebits)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = IndGreenDark
                                )
                            }
                        }

                        Box(modifier = Modifier.width(1.dp).height(28.dp).background(IndBorder))

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                shape = CircleShape,
                                color = IndAmberLight
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Schedule,
                                    contentDescription = null,
                                    tint = Color(0xFFB45309),
                                    modifier = Modifier.size(16.dp).padding(2.dp)
                                )
                            }
                            Column {
                                Text("Yet to Pay (Pending)", style = MaterialTheme.typography.labelSmall, color = IndTextSecondary, fontSize = 10.sp)
                                Text(
                                    text = "$currencySymbol${"%,.0f".format(summary.unpaidDebits)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (summary.unpaidDebits > 0) Color(0xFFB45309) else IndGreenDark
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Segmented Progress Bar
                    CategorySpendProgressBar(
                        totalIncome = summary.totalIncome,
                        breakdown = summary.categoryBreakdown
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Stats: Safe daily spend and savings rate
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Bolt, contentDescription = null, tint = IndAmber, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Daily Safe Spend: ",
                                style = MaterialTheme.typography.bodySmall,
                                color = IndTextSecondary
                            )
                            Text(
                                text = "$currencySymbol${"%,.0f".format(summary.dailySafeSpend)}/day",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = IndTextPrimary
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = IndBlueLight
                        ) {
                            Text(
                                text = "Wealth Rate: ${"%.0f".format(summary.savingsRatePercent)}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = IndBlue,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action Button for Salary
                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { showSalaryDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("update_salary_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IndGreen, contentColor = Color.White)
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (summary.totalIncome > 0) "Edit Income & Sources" else "Enter Monthly Income",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // 3. Category Filter Chips (Categorized neatly)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Header Section Title
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Monthly Debits (${debits.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = IndTextPrimary
                            )
                            Text(
                                text = "Ordered by Due Date (Day 1 to 31)",
                                style = MaterialTheme.typography.labelSmall,
                                color = IndTextSecondary
                            )
                        }
                    }

                    // Action Buttons Row (50/50 equal width split)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.prefillDebitsFromPreviousMonth() },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = IndNavyHeader),
                            border = androidx.compose.foundation.BorderStroke(1.dp, IndNavyHeader.copy(alpha = 0.4f)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("prefill_button_header")
                        ) {
                            Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(16.dp), tint = IndNavyHeader)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Prefill Prev Month", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                        }

                        Button(
                            onClick = {
                                val cat = if (!selectedFilterCategory.isNullOrBlank() && selectedFilterCategory != "ALL") selectedFilterCategory!! else DebitCategory.GENERAL.name
                                onOpenAddDebit(cat)
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IndNavyHeader, contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("add_debit_header_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Planned Debit", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                        }
                    }

                    val filterList = listOf(
                        "ALL" to "All (${debits.size})",
                        DebitCategory.RENT.name to "Rent",
                        DebitCategory.EMI.name to "EMIs",
                        DebitCategory.ELECTRICITY.name to "Electricity",
                        DebitCategory.INTERNET.name to "Internet",
                        DebitCategory.SHOPPING.name to "Shopping",
                        DebitCategory.INVESTMENT.name to "Investments",
                        DebitCategory.SAVING.name to "Savings",
                        DebitCategory.GENERAL.name to "General",
                        DebitCategory.CUSTOM.name to "Custom"
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        items(filterList) { (key, label) ->
                            val isSelected = selectedFilterCategory == key
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedFilterCategory = key },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = IndNavyHeader,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }

            // 4. Debits List (Sorted 1 to 31 by Due Day)
            if (filteredDebits.isEmpty()) {
                item {
                    IndCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ReceiptLong,
                                contentDescription = null,
                                tint = IndTextMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "No debits added for this filter",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = IndTextPrimary
                            )
                            Text(
                                text = "Use '+ Add Planned Debit' or 'Prefill Previous Month' in the header above to plan your expenses for this month.",
                                style = MaterialTheme.typography.bodySmall,
                                color = IndTextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(filteredDebits, key = { it.id }) { debit ->
                    DebitItemCard(
                        item = debit,
                        currencySymbol = currencySymbol,
                        onEdit = { editingDebit = debit },
                        onTogglePaid = { viewModel.toggleDebitPaid(debit.id, debit.isPaid) },
                        onDelete = { debitToDelete = debit }
                    )
                }
            }
        }

        // Snackbar host for prefill messages
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        )
    }

    // Salary & Credits Dialog
    if (showSalaryDialog) {
        SalaryInputDialog(
            currentSalary = salaryRecord?.salaryAmount ?: 0.0,
            currentAdditional = salaryRecord?.additionalIncome ?: 0.0,
            currentNotes = salaryRecord?.notes ?: "",
            currentIncomeSources = summary.incomeSources,
            monthName = monthName,
            year = year,
            currencySymbol = currencySymbol,
            onDismiss = { showSalaryDialog = false },
            onSave = { sal, extra, notes, sources ->
                viewModel.saveSalary(sal, extra, notes, sources)
                showSalaryDialog = false
            }
        )
    }

    // Edit Debit Dialog (Requirement #2)
    if (editingDebit != null) {
        AddEditDebitDialog(
            debitToEdit = editingDebit,
            customCategories = customCategories,
            monthName = monthName,
            year = year,
            currencySymbol = currencySymbol,
            initialCategory = editingDebit!!.category,
            onDismiss = { editingDebit = null },
            onAddCustomCategoryClick = onOpenAddCustomCategory,
            onSave = { debitId, category, customCatName, title, amount, dueDateDay, isRecurring, interestRate, totalTenure, currentTenure, notes ->
                viewModel.updateDebit(
                    editingDebit!!.copy(
                        category = category,
                        customCategoryName = customCatName,
                        title = title,
                        amount = amount,
                        dueDateDay = dueDateDay,
                        isRecurring = isRecurring,
                        interestRate = interestRate,
                        totalTenureMonths = totalTenure,
                        currentMonthTenure = currentTenure,
                        notes = notes
                    )
                )
                editingDebit = null
            }
        )
    }

    // Delete Confirmation Dialog for Debits (Requirement #7)
    if (debitToDelete != null) {
        DeleteConfirmationDialog(
            title = "Delete Monthly Debit?",
            message = "Are you sure you want to delete '${debitToDelete!!.title}' (${currencySymbol}${"%,.0f".format(debitToDelete!!.amount)})?",
            onConfirm = {
                viewModel.deleteDebit(debitToDelete!!.id)
                debitToDelete = null
            },
            onDismiss = { debitToDelete = null }
        )
    }
}

@Composable
fun DebitItemCard(
    item: DebitItem,
    currencySymbol: String,
    onEdit: () -> Unit,
    onTogglePaid: () -> Unit,
    onDelete: () -> Unit
) {
    IndCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .testTag("debit_card_${item.id}"),
        backgroundColor = if (item.isPaid) IndCardSecondary else IndSurface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CategoryIconBadge(
                    categoryKey = if (item.category == DebitCategory.CUSTOM.name && item.customCategoryName.isNotBlank()) item.customCategoryName else item.category
                )

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = IndTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Category Tag
                        val categoryBadgeLabel = when (item.category) {
                            DebitCategory.RENT.name -> "RENT"
                            DebitCategory.EMI.name -> "EMI"
                            DebitCategory.ELECTRICITY.name -> "POWER"
                            DebitCategory.INTERNET.name -> "WIFI"
                            DebitCategory.SHOPPING.name -> "SHOP"
                            DebitCategory.INVESTMENT.name -> "SIP"
                            DebitCategory.SAVING.name -> "SAVINGS"
                            DebitCategory.GENERAL.name -> "GENERAL"
                            else -> null
                        }

                        if (categoryBadgeLabel != null) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = IndNavyHeaderLight.copy(alpha = 0.12f),
                                modifier = Modifier.padding(2.dp)
                            ) {
                                Text(
                                    text = categoryBadgeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = IndNavyHeader,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    // Metadata details with Due Day (Requirement #6)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = IndBlueLight
                        ) {
                            Text(
                                text = "Due: Day ${item.dueDateDay}",
                                style = MaterialTheme.typography.labelSmall,
                                color = IndBlueDark,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }

                        if (item.totalTenureMonths > 0) {
                            Text(
                                text = "• Tenure: ${item.currentMonthTenure}/${item.totalTenureMonths}m",
                                style = MaterialTheme.typography.bodySmall,
                                color = IndBlue,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (item.interestRate > 0) {
                            Text(
                                text = "• ${item.interestRate}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFB45309),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (item.notes.isNotBlank()) {
                        Text(
                            text = item.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = IndTextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "$currencySymbol${"%,.0f".format(item.amount)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (item.isPaid) IndTextMuted else IndTextPrimary
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Paid toggle button
                    IconButton(
                        onClick = onTogglePaid,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (item.isPaid) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                            contentDescription = if (item.isPaid) "Mark unpaid" else "Mark paid",
                            tint = if (item.isPaid) IndGreen else IndTextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Edit button (Requirement #2)
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit debit",
                            tint = IndBlue,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    // Delete button
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Delete debit",
                            tint = IndTextMuted,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}
