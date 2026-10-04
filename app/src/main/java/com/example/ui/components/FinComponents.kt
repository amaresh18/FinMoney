package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.theme.*

@Composable
fun IndCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    backgroundColor: Color = IndCard,
    borderColor: Color = IndBorder,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val clickableModifier = if (onClick != null) {
        modifier
            .clip(shape)
            .clickable(onClick = onClick)
    } else {
        modifier.clip(shape)
    }

    Surface(
        modifier = clickableModifier.border(1.dp, borderColor, shape),
        shape = shape,
        color = backgroundColor,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun CategoryIconBadge(
    categoryKey: String,
    modifier: Modifier = Modifier,
    customColor: Color? = null
) {
    val (icon, bgColor, tintColor) = when (categoryKey.lowercase()) {
        "rent", DebitCategory.RENT.name.lowercase(), "home", "house rent" -> Triple(Icons.Filled.Home, IndBlueLight, IndBlue)
        "emi", DebitCategory.EMI.name.lowercase(), "loan" -> Triple(Icons.Filled.CreditCard, IndRedLight, IndRed)
        "electricity", DebitCategory.ELECTRICITY.name.lowercase(), "flash", "utilities" -> Triple(Icons.Filled.Bolt, IndAmberLight, Color(0xFFD97706))
        "internet", DebitCategory.INTERNET.name.lowercase(), "wifi", "broadband" -> Triple(Icons.Filled.Wifi, IndCyanLight, IndCyan)
        "shopping", DebitCategory.SHOPPING.name.lowercase() -> Triple(Icons.Filled.ShoppingBag, Color(0xFFFCE7F3), Color(0xFFDB2777))
        "investment", DebitCategory.INVESTMENT.name.lowercase(), "sip" -> Triple(Icons.AutoMirrored.Filled.TrendingUp, IndGreenLight, IndGreenDark)
        "saving", DebitCategory.SAVING.name.lowercase(), "rd", "fd" -> Triple(Icons.Filled.Savings, Color(0xFFCCFBF1), Color(0xFF0F766E))
        "general", DebitCategory.GENERAL.name.lowercase(), "groceries" -> Triple(Icons.Filled.Receipt, Color(0xFFF1F5F9), Color(0xFF475569))
        "shield", "insurance" -> Triple(Icons.Filled.Shield, Color(0xFFFCE7F3), Color(0xFFDB2777))
        "school", "education" -> Triple(Icons.Filled.School, IndPurpleLight, IndPurple)
        "tv", "subscription", "subscriptions" -> Triple(Icons.Filled.Tv, Color(0xFFEEF2FF), Color(0xFF4F46E5))
        else -> Triple(Icons.Filled.Category, IndAmberLight, customColor ?: IndAmber)
    }

    Box(
        modifier = modifier
            .size(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, tintColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = categoryKey,
            tint = tintColor,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun ApprovalStatusBadge(status: String) {
    val (text, bgColor, textColor, icon) = when (status) {
        ApprovalStatus.APPROVED.name -> Quad(
            "Mutual Approved",
            StatusApprovedBg,
            StatusApprovedText,
            Icons.Filled.CheckCircle
        )
        ApprovalStatus.PENDING_APPROVAL.name -> Quad(
            "Approval Pending",
            StatusPendingBg,
            StatusPendingText,
            Icons.Filled.Schedule
        )
        ApprovalStatus.PENDING_SETTLEMENT.name -> Quad(
            "Settlement Pending",
            IndAmberLight,
            Color(0xFFB45309),
            Icons.Filled.HourglassTop
        )
        ApprovalStatus.SETTLED.name -> Quad(
            "Settled Up",
            StatusSettledBg,
            StatusSettledText,
            Icons.Filled.DoneAll
        )
        ApprovalStatus.REJECTED.name -> Quad(
            "Disputed / Rejected",
            StatusRejectedBg,
            StatusRejectedText,
            Icons.Filled.Cancel
        )
        else -> Quad(status, IndCardSecondary, IndTextSecondary, Icons.Filled.Info)
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .border(0.5.dp, textColor.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = textColor,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun CategorySpendProgressBar(
    totalIncome: Double,
    breakdown: List<com.example.ui.CategorySummary>,
    modifier: Modifier = Modifier
) {
    if (totalIncome <= 0 || breakdown.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(IndBorderSubtle)
        )
        return
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(IndBorderSubtle)
    ) {
        breakdown.forEach { cat ->
            val fraction = (cat.totalAmount / totalIncome).toFloat().coerceIn(0f, 1f)
            if (fraction > 0.01f) {
                val color = parseHexColor(cat.colorHex)
                Box(
                    modifier = Modifier
                        .weight(fraction)
                        .fillMaxHeight()
                        .background(color)
                )
                Spacer(modifier = Modifier.width(1.dp))
            }
        }
    }
}

fun parseHexColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        IndGreen
    }
}
