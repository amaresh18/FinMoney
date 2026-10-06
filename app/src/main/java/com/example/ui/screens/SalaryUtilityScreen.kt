package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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

    var showSalaryDialog by remember { mutableStateOf(false) }
    var editingDebit by remember { mutableStateOf<DebitItem?>(null) }
    var debitToDelete by remember { mutableStateOf<DebitItem?>(null) }
    var selectedFilterCategory by remember { mutableStateOf<String?>("ALL") }
    var isAggregatedView by remember { mutableStateOf(true) }

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
        when (selectedFilterCategory) {
            "ALL", null -> sortedDebits
            "PAID" -> sortedDebits.filter { it.isPaid }
            "UNPAID" -> sortedDebits.filter { !it.isPaid }
            DebitCategory.RENT.name,
            DebitCategory.EMI.name,
            DebitCategory.ELECTRICITY.name,
            DebitCategory.INTERNET.name,
            DebitCategory.GROCERIES.name,
            DebitCategory.RECHARGE.name,
            DebitCategory.CHIT.name,
            DebitCategory.FUEL.name,
            DebitCategory.SHOPPING.name,
            DebitCategory.INVESTMENT.name,
            DebitCategory.SAVING.name,
            DebitCategory.GENERAL.name -> sortedDebits.filter { it.category == selectedFilterCategory }
            DebitCategory.CUSTOM.name -> sortedDebits.filter { it.category == DebitCategory.CUSTOM.name }
            else -> sortedDebits.filter {
                (it.category == DebitCategory.CUSTOM.name && it.customCategoryName.equals(selectedFilterCategory, ignoreCase = true)) ||
                it.category.equals(selectedFilterCategory, ignoreCase = true) ||
                it.customCategoryName.equals(selectedFilterCategory, ignoreCase = true)
            }
        }
    }

    val totalOutflow = summary.totalDebits
    val totalPaid = summary.paidDebits
    val totalUnpaid = summary.unpaidDebits
    val completionFraction = if (totalOutflow > 0) (totalPaid / totalOutflow).toFloat().coerceIn(0f, 1f) else 0f
    val animatedCompletion by animateFloatAsState(targetValue = completionFraction, label = "outflowProgress")

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

                    Spacer(modifier = Modifier.height(12.dp))

                    // Outflow Status Progress Bar (Overall payment completion)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(IndBackground)
                            .border(1.dp, IndBorderSubtle, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.PieChart, contentDescription = null, tint = IndNavyHeader, modifier = Modifier.size(15.dp))
                                Text(
                                    text = "Payment Completion",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = IndTextPrimary
                                )
                            }
                            Text(
                                text = "${(completionFraction * 100).toInt()}% Paid • $currencySymbol${"%,.0f".format(totalPaid)} / $currencySymbol${"%,.0f".format(totalOutflow)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (completionFraction >= 1f) IndGreenDark else IndNavyHeader
                            )
                        }

                        // Progress track
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(IndBorderSubtle)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction = animatedCompletion)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (completionFraction >= 1f) IndGreen else IndNavyHeader)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Stats: Safe daily spend and wealth rate
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

                    Spacer(modifier = Modifier.height(12.dp))

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
                            colors = ButtonDefaults.buttonColors(containerColor = IndNavyHeader, contentColor = Color.White)
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

            // 3. View Switcher Pill (Aggregated View as Default vs List View)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(IndSurface)
                        .border(1.dp, IndBorder, RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val aggBg by animateColorAsState(if (isAggregatedView) IndNavyHeader else Color.Transparent, label = "aggBg")
                    val aggText by animateColorAsState(if (isAggregatedView) Color.White else IndTextSecondary, label = "aggText")
                    val listBg by animateColorAsState(if (!isAggregatedView) IndNavyHeader else Color.Transparent, label = "listBg")
                    val listText by animateColorAsState(if (!isAggregatedView) Color.White else IndTextSecondary, label = "listText")

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .clickable { isAggregatedView = true },
                        shape = RoundedCornerShape(9.dp),
                        color = aggBg
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Dashboard, contentDescription = null, tint = aggText, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Aggregated View", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = aggText)
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .clickable { isAggregatedView = false },
                        shape = RoundedCornerShape(9.dp),
                        color = listBg
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.FormatListBulleted, contentDescription = null, tint = listText, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("List View", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = listText)
                        }
                    }
                }
            }

            // 4. Aggregated View Components
            if (isAggregatedView) {
                // Interactive Paid vs. Yet To Pay Cards (Side by Side)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val isPaidSelected = selectedFilterCategory == "PAID"
                        val isUnpaidSelected = selectedFilterCategory == "UNPAID"

                        // Paid Outflows Card
                        IndCard(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedFilterCategory = if (isPaidSelected) "ALL" else "PAID"
                                },
                            backgroundColor = if (isPaidSelected) IndGreenLight.copy(alpha = 0.5f) else IndSurface,
                            borderColor = if (isPaidSelected) IndGreen else IndBorder,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(IndGreenLight),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = IndGreenDark, modifier = Modifier.size(17.dp))
                                    }
                                    if (isPaidSelected) {
                                        Surface(shape = RoundedCornerShape(4.dp), color = IndGreen) {
                                            Text("ACTIVE", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                        }
                                    }
                                }
                                Text("Paid Outflows", style = MaterialTheme.typography.labelSmall, color = IndTextSecondary, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "$currencySymbol${"%,.0f".format(totalPaid)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = IndGreenDark
                                )
                                Text(
                                    text = "${debits.count { it.isPaid }} items cleared",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = IndTextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Yet To Pay (Pending) Card
                        IndCard(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedFilterCategory = if (isUnpaidSelected) "ALL" else "UNPAID"
                                },
                            backgroundColor = if (isUnpaidSelected) IndAmberLight.copy(alpha = 0.5f) else IndSurface,
                            borderColor = if (isUnpaidSelected) IndAmber else IndBorder,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(IndAmberLight),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Filled.Schedule, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(17.dp))
                                    }
                                    if (isUnpaidSelected) {
                                        Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFD97706)) {
                                            Text("ACTIVE", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                        }
                                    }
                                }
                                Text("Yet to Pay (Pending)", style = MaterialTheme.typography.labelSmall, color = IndTextSecondary, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "$currencySymbol${"%,.0f".format(totalUnpaid)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (totalUnpaid > 0) Color(0xFFB45309) else IndGreenDark
                                )
                                Text(
                                    text = "${debits.count { !it.isPaid }} items due",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = IndTextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // Interactive Category Carousel
                item {
                    val allCategoryCards = remember(summary.categoryBreakdown, customCategories) {
                        val existingKeys = summary.categoryBreakdown.map { it.categoryKey.lowercase() }.toSet()
                        val defaultList = listOf(
                            Triple(DebitCategory.GROCERIES.name, "Groceries", "#059669"),
                            Triple(DebitCategory.RECHARGE.name, "Recharge", "#0284C7"),
                            Triple(DebitCategory.CHIT.name, "Chit Payments", "#6D28D9"),
                            Triple(DebitCategory.FUEL.name, "Fuel", "#EA580C"),
                            Triple(DebitCategory.RENT.name, "Rent / Housing", "#0066FF"),
                            Triple(DebitCategory.EMI.name, "Loan / EMI", "#F43F5E"),
                            Triple(DebitCategory.ELECTRICITY.name, "Electricity", "#F59E0B"),
                            Triple(DebitCategory.INTERNET.name, "Internet & WiFi", "#0284C7"),
                            Triple(DebitCategory.SHOPPING.name, "Shopping", "#DB2777"),
                            Triple(DebitCategory.INVESTMENT.name, "Investments / SIP", "#00B377"),
                            Triple(DebitCategory.SAVING.name, "Savings & RD", "#0F766E"),
                            Triple(DebitCategory.GENERAL.name, "General Expenses", "#64748B")
                        )
                        val customList = customCategories.distinctBy { it.name.trim().lowercase() }.map {
                            Triple(it.name, it.name, it.colorHex)
                        }

                        val fullList = mutableListOf<CategorySummary>()
                        // First add active categories with expenses
                        fullList.addAll(summary.categoryBreakdown)
                        // Then add remaining default & custom categories
                        (defaultList + customList).forEach { (catKey, displayName, colorHex) ->
                            if (!existingKeys.contains(catKey.lowercase())) {
                                fullList.add(
                                    CategorySummary(
                                        categoryName = displayName,
                                        categoryKey = catKey,
                                        totalAmount = 0.0,
                                        paidAmount = 0.0,
                                        yetToPayAmount = 0.0,
                                        itemCount = 0,
                                        paidCount = 0,
                                        colorHex = colorHex
                                    )
                                )
                            }
                        }
                        fullList
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Aggregate by Category (${allCategoryCards.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = IndTextPrimary
                            )
                            if (selectedFilterCategory != "ALL" && selectedFilterCategory != "PAID" && selectedFilterCategory != "UNPAID") {
                                TextButton(
                                    onClick = { selectedFilterCategory = "ALL" },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("Clear Category Filter", fontSize = 11.sp, color = IndBlue)
                                }
                            }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            items(allCategoryCards, key = { it.categoryKey }) { cat ->
                                val isCatSelected = selectedFilterCategory == cat.categoryKey || selectedFilterCategory.equals(cat.categoryName, ignoreCase = true)
                                val catPaidProgress = if (cat.totalAmount > 0) (cat.paidAmount / cat.totalAmount).toFloat().coerceIn(0f, 1f) else 0f

                                IndCard(
                                    modifier = Modifier
                                        .width(180.dp)
                                        .clickable {
                                            if (cat.totalAmount == 0.0) {
                                                onOpenAddDebit(cat.categoryKey)
                                            } else {
                                                selectedFilterCategory = if (isCatSelected) "ALL" else cat.categoryKey
                                            }
                                        },
                                    backgroundColor = if (isCatSelected) IndCardHighlight else IndSurface,
                                    borderColor = if (isCatSelected) IndNavyHeader else IndBorder,
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CategoryIconBadge(
                                                categoryKey = cat.categoryKey,
                                                modifier = Modifier.size(36.dp)
                                            )
                                            if (cat.totalAmount > 0) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = if (cat.yetToPayAmount == 0.0) IndGreenLight else IndCardSecondary,
                                                    border = androidx.compose.foundation.BorderStroke(0.8.dp, IndBorderSubtle)
                                                ) {
                                                    Text(
                                                        text = "${cat.paidCount}/${cat.itemCount} paid",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (cat.yetToPayAmount == 0.0) IndGreenDark else IndTextSecondary,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                    )
                                                }
                                            } else {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = IndBlueLight.copy(alpha = 0.5f)
                                                ) {
                                                    Text(
                                                        text = "+ Add",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = IndBlue,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Column {
                                            Text(
                                                text = cat.categoryName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = IndTextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = if (cat.totalAmount > 0) "$currencySymbol${"%,.0f".format(cat.totalAmount)}" else "₹0 Planned",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (cat.totalAmount > 0) IndNavyHeader else IndTextMuted
                                            )
                                        }

                                        // Progress bar or prompt
                                        if (cat.totalAmount > 0) {
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(5.dp)
                                                        .clip(RoundedCornerShape(3.dp))
                                                        .background(IndBorderSubtle)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth(fraction = catPaidProgress)
                                                            .fillMaxHeight()
                                                            .clip(RoundedCornerShape(3.dp))
                                                            .background(if (catPaidProgress >= 1f) IndGreen else parseHexColor(cat.colorHex))
                                                    )
                                                }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text("Paid: $currencySymbol${"%,.0f".format(cat.paidAmount)}", fontSize = 10.sp, color = IndGreenDark, fontWeight = FontWeight.SemiBold)
                                                    Text("Due: $currencySymbol${"%,.0f".format(cat.yetToPayAmount)}", fontSize = 10.sp, color = if (cat.yetToPayAmount > 0) Color(0xFFB45309) else IndTextMuted)
                                                }
                                            }
                                        } else {
                                            Text(
                                                text = "Tap to add debit",
                                                fontSize = 10.sp,
                                                color = IndBlue,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Header for the Entries List (with Action Buttons)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Header Section Title
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val filterDescription = when (selectedFilterCategory) {
                                "ALL", null -> "All Commitments"
                                "PAID" -> "Paid Debits"
                                "UNPAID" -> "Yet to Pay (Pending)"
                                else -> "Filtered: $selectedFilterCategory"
                            }
                            Text(
                                text = "$filterDescription (${filteredDebits.size})",
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

                        if (selectedFilterCategory != "ALL" && selectedFilterCategory != null) {
                            TextButton(
                                onClick = { selectedFilterCategory = "ALL" },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Reset Filter", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = IndNavyHeader)
                            }
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
                                val cat = if (!selectedFilterCategory.isNullOrBlank() && selectedFilterCategory != "ALL" && selectedFilterCategory != "PAID" && selectedFilterCategory != "UNPAID") {
                                    selectedFilterCategory!!
                                } else DebitCategory.GENERAL.name
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

                    // In List View, render the horizontal category filter chips
                    if (!isAggregatedView) {
                        val filterList = remember(debits, customCategories) {
                            val list = mutableListOf<Pair<String, String>>()
                            list.add("ALL" to "All (${debits.size})")
                            list.add("PAID" to "Paid (${debits.count { it.isPaid }})")
                            list.add("UNPAID" to "Yet to Pay (${debits.count { !it.isPaid }})")
                            
                            list.add(DebitCategory.RENT.name to "Rent")
                            list.add(DebitCategory.EMI.name to "EMIs")
                            list.add(DebitCategory.ELECTRICITY.name to "Electricity")
                            list.add(DebitCategory.INTERNET.name to "Internet")
                            list.add(DebitCategory.GROCERIES.name to "Groceries")
                            list.add(DebitCategory.RECHARGE.name to "Recharge")
                            list.add(DebitCategory.CHIT.name to "Chit Payments")
                            list.add(DebitCategory.FUEL.name to "Fuel")
                            list.add(DebitCategory.SHOPPING.name to "Shopping")
                            list.add(DebitCategory.INVESTMENT.name to "Investments")
                            list.add(DebitCategory.SAVING.name to "Savings")
                            list.add(DebitCategory.GENERAL.name to "General")
                            list.add(DebitCategory.CUSTOM.name to "All Custom")

                            val activeCustomNames = (customCategories.map { it.name } + debits.filter { it.category == DebitCategory.CUSTOM.name && it.customCategoryName.isNotBlank() }.map { it.customCategoryName })
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                                .distinctBy { it.lowercase() }

                            activeCustomNames.forEach { customName ->
                                val count = debits.count { it.category == DebitCategory.CUSTOM.name && it.customCategoryName.equals(customName, ignoreCase = true) }
                                list.add(customName to if (count > 0) "$customName ($count)" else customName)
                            }

                            list
                        }

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
            }

            // 6. Debits List (Sorted 1 to 31 by Due Day)
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
                                text = "No debits found for this filter",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = IndTextPrimary
                            )
                            Text(
                                text = "Use '+ Add Planned Debit' or 'Prefill Prev Month' above to manage your monthly budget.",
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

    // Edit Debit Dialog
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

    // Delete Confirmation Dialog for Debits
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
                            DebitCategory.GROCERIES.name -> "GROCERY"
                            DebitCategory.RECHARGE.name -> "RECHARGE"
                            DebitCategory.CHIT.name -> "CHIT"
                            DebitCategory.FUEL.name -> "FUEL"
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

                    // Metadata details with Due Day, Tenure Auto-Tracking, and Interest Rate
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
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
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }

                        // Tenure Progress Badge: Month X of Y • Z months remaining
                        if (item.totalTenureMonths > 0) {
                            val remaining = (item.totalTenureMonths - item.currentMonthTenure).coerceAtLeast(0)
                            val tenureText = if (item.currentMonthTenure >= item.totalTenureMonths) {
                                "Tenure: Month ${item.currentMonthTenure} of ${item.totalTenureMonths} • Completed 🎉"
                            } else {
                                "Tenure: Month ${item.currentMonthTenure} of ${item.totalTenureMonths} • $remaining months remaining"
                            }

                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (item.isPaid) IndGreenLight else IndCardSecondary,
                                border = androidx.compose.foundation.BorderStroke(0.8.dp, if (item.isPaid) IndGreen.copy(alpha = 0.4f) else IndBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = if (item.category == DebitCategory.CHIT.name) Icons.Filled.Savings else Icons.Filled.Timeline,
                                        contentDescription = null,
                                        tint = if (item.isPaid) IndGreenDark else IndBlue,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = tenureText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (item.isPaid) IndGreenDark else IndNavyHeader,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }

                        if (item.interestRate > 0) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFFEF3C7),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD97706).copy(alpha = 0.8f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Percent,
                                        contentDescription = null,
                                        tint = Color(0xFF92400E),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = "${item.interestRate}% Interest",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF92400E),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
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

                    // Edit button
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

                    // Delete button matching Edit button style (clean icon button, no circular highlight)
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Delete debit",
                            tint = IndRed,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}
