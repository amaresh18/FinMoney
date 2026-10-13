package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CategorySummary
import com.example.ui.theme.*

@Composable
fun DashboardPieChartCard(
    categorySummaries: List<CategorySummary>,
    currencySymbol: String,
    isPrivacyVisible: Boolean
) {
    var selectedCategoryIndex by remember { mutableStateOf<Int?>(null) }

    val topCategories = remember(categorySummaries) {
        categorySummaries.sortedByDescending { it.totalAmount }.take(6)
    }

    val totalSpent = remember(topCategories) {
        topCategories.sumOf { it.totalAmount }.toFloat().let { if (it > 0f) it else 1f }
    }

    val colors = listOf(
        IndBlue,
        IndGreenDark,
        IndRed,
        Color(0xFF8B5CF6), // Purple
        Color(0xFFD97706), // Amber
        Color(0xFF0D9488)  // Teal
    )

    var animationProgress by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        animationProgress = 0f
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing)
        ) { value, _ ->
            animationProgress = value
        }
    }

    IndCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(IndBlueLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.PieChart, contentDescription = null, tint = IndBlue, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text(
                            text = "Spend Distribution Donut",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = IndTextPrimary
                        )
                        Text(
                            text = "Interactive category breakdown",
                            style = MaterialTheme.typography.labelSmall,
                            color = IndTextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }

                Text(
                    text = "${topCategories.size} Categories",
                    style = MaterialTheme.typography.labelMedium,
                    color = IndBlue,
                    fontWeight = FontWeight.Bold
                )
            }

            // Donut Chart & Center Info
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(170.dp)) {
                    var startAngle = -90f
                    topCategories.forEachIndexed { index, cat ->
                        val sweepAngle = (cat.totalAmount.toFloat() / totalSpent) * 360f * animationProgress
                        val color = colors[index % colors.size]
                        val isSelected = selectedCategoryIndex == index

                        drawArc(
                            color = color,
                            startAngle = startAngle,
                            sweepAngle = sweepAngle.coerceAtLeast(1f),
                            useCenter = false,
                            style = Stroke(width = if (isSelected) 42f else 34f, cap = StrokeCap.Round)
                        )
                        startAngle += sweepAngle
                    }
                }

                // Center Text
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val activeCat = selectedCategoryIndex?.let { topCategories.getOrNull(it) }
                    if (activeCat != null) {
                        Text(
                            text = activeCat.categoryName,
                            fontSize = 11.sp,
                            color = IndTextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (!isPrivacyVisible) "★★★★" else "$currencySymbol${"%,.0f".format(activeCat.totalAmount)}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndTextPrimary
                        )
                        val pct = ((activeCat.totalAmount.toFloat() / totalSpent) * 100).toInt()
                        Text(
                            text = "$pct% of total",
                            fontSize = 10.sp,
                            color = IndBlue
                        )
                    } else {
                        Text(
                            text = "Total Spend",
                            fontSize = 11.sp,
                            color = IndTextSecondary
                        )
                        Text(
                            text = if (!isPrivacyVisible) "★★★★" else "$currencySymbol${"%,.0f".format(totalSpent)}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndTextPrimary
                        )
                        Text(
                            text = "Tap slice for details",
                            fontSize = 10.sp,
                            color = IndTextSecondary
                        )
                    }
                }
            }

            // Legend / Category Chips
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                topCategories.forEachIndexed { index, cat ->
                    val color = colors[index % colors.size]
                    val pct = ((cat.totalAmount.toFloat() / totalSpent) * 100).toInt()
                    val isSelected = selectedCategoryIndex == index

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) IndBlueLight else IndCardSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedCategoryIndex = if (selectedCategoryIndex == index) null else index
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                                Text(
                                    text = cat.categoryName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = IndTextPrimary
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$pct%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = IndTextSecondary
                                )
                                Text(
                                    text = if (!isPrivacyVisible) "★★★★" else "$currencySymbol${"%,.0f".format(cat.totalAmount)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = IndTextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
