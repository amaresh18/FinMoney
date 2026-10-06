package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.DebitCategory
import com.example.ui.FinMoneyViewModel
import com.example.ui.components.CategoryIconBadge
import com.example.ui.components.IndCard
import com.example.ui.theme.*

@Composable
fun HistoryAnalyticsScreen(
    viewModel: FinMoneyViewModel
) {
    val month by viewModel.currentMonth.collectAsStateWithLifecycle()
    val year by viewModel.currentYear.collectAsStateWithLifecycle()
    val currencySymbol by viewModel.currencySymbol.collectAsStateWithLifecycle()
    val allDebits by viewModel.allDebits.collectAsStateWithLifecycle()
    val allSalaryRecords by viewModel.allSalaryRecords.collectAsStateWithLifecycle()
    val summary by viewModel.monthlySummary.collectAsStateWithLifecycle()

    val monthName = FinMoneyViewModel.getMonthName(month)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
    ) {
        // Month Selector Header
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
                IconButton(onClick = { viewModel.previousMonth() }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Prev", tint = IndTextSecondary)
                }
                Text(
                    text = "$monthName $year Analytics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = IndTextPrimary
                )
                IconButton(onClick = { viewModel.nextMonth() }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Next", tint = IndTextSecondary)
                }
            }
        }

        // Summary Card
        item {
            IndCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "MONTHLY FINANCIAL HEALTH",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = IndBlueDark,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Total Income", style = MaterialTheme.typography.labelSmall, color = IndTextSecondary)
                        Text("$currencySymbol${"%,.0f".format(summary.totalIncome)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = IndGreenDark)
                    }
                    Column {
                        Text("Total Debits", style = MaterialTheme.typography.labelSmall, color = IndTextSecondary)
                        Text("$currencySymbol${"%,.0f".format(summary.totalDebits)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = IndRed)
                    }
                    Column {
                        Text("Net Balance", style = MaterialTheme.typography.labelSmall, color = IndTextSecondary)
                        Text("$currencySymbol${"%,.0f".format(summary.netFlexibleSpending)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if (summary.netFlexibleSpending >= 0) IndTextPrimary else IndRed)
                    }
                }
            }
        }

        // Category Breakdown Section
        item {
            Text(
                text = "Spend by Category",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = IndTextPrimary
            )
        }

        if (summary.categoryBreakdown.isEmpty()) {
            item {
                IndCard(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No expenses logged for $monthName $year.", color = IndTextSecondary)
                    }
                }
            }
        } else {
            items(summary.categoryBreakdown) { cat ->
                IndCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CategoryIconBadge(categoryKey = cat.iconKey)
                            Column {
                                Text(cat.categoryName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = IndTextPrimary)
                                Text("${cat.itemCount} items", style = MaterialTheme.typography.labelSmall, color = IndTextSecondary)
                            }
                        }
                        Text(
                            text = "$currencySymbol${"%,.0f".format(cat.totalAmount)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = IndNavyHeader
                        )
                    }
                }
            }
        }
    }
}
