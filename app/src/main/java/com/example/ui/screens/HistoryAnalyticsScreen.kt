package com.example.ui.screens

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.*
import com.example.ui.CategorySummary
import com.example.ui.FinMoneyViewModel
import com.example.ui.components.*
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryAnalyticsScreen(
    viewModel: FinMoneyViewModel,
    onSelectMonth: (month: Int, year: Int) -> Unit
) {
    val currencySymbol by viewModel.currencySymbol.collectAsStateWithLifecycle()
    val allSalaries by viewModel.allSalaryRecords.collectAsStateWithLifecycle()
    val allDebits by viewModel.allDebits.collectAsStateWithLifecycle()
    val currentDebits by viewModel.currentDebits.collectAsStateWithLifecycle()
    val summary by viewModel.monthlySummary.collectAsStateWithLifecycle()
    val currentMonth by viewModel.currentMonth.collectAsStateWithLifecycle()
    val currentYear by viewModel.currentYear.collectAsStateWithLifecycle()

    var selectedCategoryForDetail by remember { mutableStateOf<CategorySummary?>(null) }
    var selectedMonthForDetail by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val historicalMonths = remember(allSalaries, allDebits) {
        val set = mutableSetOf<Pair<Int, Int>>()
        allSalaries.forEach { set.add(it.month to it.year) }
        allDebits.forEach { set.add(it.month to it.year) }
        set.sortedWith(compareByDescending<Pair<Int, Int>> { it.second }.thenByDescending { it.first })
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
    ) {
        // 1. Analytics Title Header Card
        item {
            IndCard(
                backgroundColor = IndSurface,
                borderColor = IndBorder,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = IndBlueLight,
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Text(
                                text = "ANALYTICS & COMPARISON",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = IndBlue,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = "Monthly Spending Audit",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = IndTextPrimary
                        )
                        Text(
                            text = "Tap any category to inspect individual line items",
                            style = MaterialTheme.typography.bodySmall,
                            color = IndTextSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(IndGreenLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.PieChart, contentDescription = null, tint = IndGreenDark)
                    }
                }
            }
        }

        // 2. Current Month Category Distribution Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${FinMoneyViewModel.getMonthName(currentMonth)} $currentYear Outflows",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = IndTextPrimary
                )
                Text(
                    text = "Tap to view items →",
                    style = MaterialTheme.typography.labelSmall,
                    color = IndBlue,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (summary.categoryBreakdown.isEmpty()) {
            item {
                IndCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "No debit commitments recorded for ${FinMoneyViewModel.getMonthName(currentMonth)} $currentYear yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = IndTextSecondary
                    )
                }
            }
        } else {
            items(summary.categoryBreakdown) { cat ->
                val fraction = if (summary.totalIncome > 0) (cat.totalAmount / summary.totalIncome) * 100 else 0.0
                IndCard(
                    onClick = { selectedCategoryForDetail = cat },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("category_breakdown_card_${cat.iconKey}"),
                    backgroundColor = IndSurface,
                    borderColor = IndBorder
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CategoryIconBadge(categoryKey = cat.iconKey, customColor = parseHexColor(cat.colorHex))
                            Column {
                                Text(
                                    text = cat.categoryName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = IndTextPrimary
                                )
                                Text(
                                    text = "${cat.itemCount} item${if (cat.itemCount > 1) "s" else ""} • ${"%.1f".format(fraction)}% of monthly salary",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = IndTextSecondary
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "$currencySymbol${"%,.0f".format(cat.totalAmount)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = parseHexColor(cat.colorHex)
                            )
                            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = IndTextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // 3. Month by Month Historical Log
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Historical Month Ledger",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = IndTextPrimary
            )
        }

        items(historicalMonths) { (m, y) ->
            val sal = allSalaries.firstOrNull { it.month == m && it.year == y }
            val debitsForM = allDebits.filter { it.month == m && it.year == y }
            val customStreamsTotal = FinMoneyViewModel.parseIncomeSources(sal?.incomeSourcesJson ?: "").sumOf { it.amount }
            val totSal = (sal?.salaryAmount ?: 0.0) + (sal?.additionalIncome ?: 0.0) + customStreamsTotal
            val totDeb = debitsForM.sumOf { it.amount }
            val netDisp = totSal - totDeb
            val isCurrent = m == currentMonth && y == currentYear

            IndCard(
                onClick = { onSelectMonth(m, y) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("history_item_${m}_${y}"),
                borderColor = if (isCurrent) IndBlue else IndBorder,
                backgroundColor = if (isCurrent) IndCardHighlight else IndSurface
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "${FinMoneyViewModel.getMonthName(m)} $y",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = IndTextPrimary
                            )
                            if (isCurrent) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = IndBlue
                                ) {
                                    Text(
                                        text = "CURRENT",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Income: $currencySymbol${"%,.0f".format(totSal)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = IndTextSecondary
                            )
                            Text(
                                text = "Debits (${debitsForM.size}): $currencySymbol${"%,.0f".format(totDeb)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = IndRed
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "$currencySymbol${"%,.0f".format(netDisp)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (netDisp >= 0) IndGreenDark else IndRed
                        )
                        Text(
                            text = "Available Balance",
                            style = MaterialTheme.typography.labelSmall,
                            color = IndTextMuted
                        )
                    }
                }
            }
        }
    }

    // --- Category Line Items Drill-Down Bottom Sheet ---
    if (selectedCategoryForDetail != null) {
        val cat = selectedCategoryForDetail!!
        val matchingDebits = currentDebits.filter {
            if (it.category == DebitCategory.CUSTOM.name) {
                it.customCategoryName.equals(cat.categoryName, ignoreCase = true) || cat.categoryName.equals("Custom", ignoreCase = true)
            } else {
                val catEnum = try { DebitCategory.valueOf(it.category) } catch (e: Exception) { null }
                catEnum?.displayName.equals(cat.categoryName, ignoreCase = true) || it.category.equals(cat.categoryName, ignoreCase = true)
            }
        }

        ModalBottomSheet(
            onDismissRequest = { selectedCategoryForDetail = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = IndSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Sheet Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CategoryIconBadge(categoryKey = cat.iconKey, customColor = parseHexColor(cat.colorHex))
                        Column {
                            Text(
                                text = cat.categoryName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = IndTextPrimary
                            )
                            Text(
                                text = "${matchingDebits.size} Individual Line Items • ${FinMoneyViewModel.getMonthName(currentMonth)} $currentYear",
                                style = MaterialTheme.typography.bodySmall,
                                color = IndTextSecondary
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = IndBackground,
                        border = androidx.compose.foundation.BorderStroke(1.dp, IndBorder)
                    ) {
                        Text(
                            text = "$currencySymbol${"%,.0f".format(cat.totalAmount)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = parseHexColor(cat.colorHex),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                HorizontalDivider(color = IndBorderSubtle)

                if (matchingDebits.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No line items found for this category.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = IndTextSecondary
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(matchingDebits) { item ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = IndBackground,
                                border = androidx.compose.foundation.BorderStroke(1.dp, IndBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = IndTextPrimary
                                            )
                                            Text(
                                                text = "Due by ${item.dueDateDay}${getDaySuffix(item.dueDateDay)} of month",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = IndTextSecondary
                                            )
                                        }

                                        Text(
                                            text = "$currencySymbol${"%,.0f".format(item.amount)}",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = IndRed
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (item.isPaid) IndGreenLight else IndAmberLight
                                        ) {
                                            Text(
                                                text = if (item.isPaid) "PAID / CLEARED" else "PAYMENT DUE",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (item.isPaid) IndGreenDark else IndAmber,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }

                                        if (item.totalTenureMonths > 0) {
                                            Text(
                                                text = "Tenure: Month ${item.currentMonthTenure} of ${item.totalTenureMonths}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = IndTextSecondary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    if (item.notes.isNotBlank()) {
                                        Text(
                                            text = "Note: ${item.notes}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = IndTextMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = { selectedCategoryForDetail = null },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = IndNavyHeader)
                ) {
                    Text("Close Line Items", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun getDaySuffix(day: Int): String {
    return when {
        day in 11..13 -> "th"
        day % 10 == 1 -> "st"
        day % 10 == 2 -> "nd"
        day % 10 == 3 -> "rd"
        else -> "th"
    }
}
