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
import androidx.compose.material.icons.automirrored.filled.MenuBook
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
import androidx.compose.ui.graphics.vector.ImageVector
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
    backgroundColor: Color = IndSurface,
    borderColor: Color = IndBorder,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val cardShape = RoundedCornerShape(AppTheme.dimensions.cardCornerRadius)
    val actualShape = if (shape.topStart == androidx.compose.foundation.shape.CornerSize(16.dp)) cardShape else shape

    val clickableModifier = if (onClick != null) {
        modifier
            .clip(actualShape)
            .clickable(onClick = onClick)
    } else {
        modifier.clip(actualShape)
    }

    Surface(
        modifier = clickableModifier.border(1.dp, borderColor, actualShape),
        shape = actualShape,
        color = backgroundColor,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(AppTheme.dimensions.cardPadding),
            content = content
        )
    }
}

fun getCategoryStyling(categoryKey: String, customColor: Color? = null): Triple<ImageVector, Color, Color> {
    return when (categoryKey.lowercase()) {
        "rent", DebitCategory.RENT.name.lowercase(), "home", "house rent" -> Triple(Icons.Filled.Home, IndBlueLight, IndBlue)
        "emi", DebitCategory.EMI.name.lowercase(), "loan" -> Triple(Icons.Filled.CreditCard, IndRedLight, IndRed)
        "electricity", DebitCategory.ELECTRICITY.name.lowercase(), "flash", "utilities" -> Triple(Icons.Filled.Bolt, IndAmberLight, Color(0xFFD97706))
        "internet", DebitCategory.INTERNET.name.lowercase(), "wifi", "broadband" -> Triple(Icons.Filled.Wifi, IndCyanLight, IndCyan)
        "groceries", DebitCategory.GROCERIES.name.lowercase(), "grocery", "food", "supermarket" -> Triple(Icons.Filled.ShoppingCart, Color(0xFFD1FAE5), Color(0xFF059669))
        "recharge", DebitCategory.RECHARGE.name.lowercase(), "mobile", "phone", "dth" -> Triple(Icons.Filled.PhoneAndroid, Color(0xFFE0F2FE), Color(0xFF0284C7))
        "chit", DebitCategory.CHIT.name.lowercase(), "chit payments", "chitfund", "chits" -> Triple(Icons.Filled.Savings, Color(0xFFEDE9FE), Color(0xFF6D28D9))
        "fuel", DebitCategory.FUEL.name.lowercase(), "petrol", "diesel", "gas" -> Triple(Icons.Filled.LocalGasStation, Color(0xFFFFEDD5), Color(0xFFEA580C))
        "shopping", DebitCategory.SHOPPING.name.lowercase() -> Triple(Icons.Filled.ShoppingBag, Color(0xFFFCE7F3), Color(0xFFDB2777))
        "investment", DebitCategory.INVESTMENT.name.lowercase(), "sip" -> Triple(Icons.AutoMirrored.Filled.TrendingUp, IndGreenLight, IndGreenDark)
        "saving", DebitCategory.SAVING.name.lowercase(), "rd", "fd" -> Triple(Icons.Filled.Savings, Color(0xFFCCFBF1), Color(0xFF0F766E))
        "maid", DebitCategory.MAID.name.lowercase(), "cook", "maid & cook" -> Triple(Icons.Filled.CleaningServices, Color(0xFFF0FDF4), Color(0xFF16A34A))
        "milk", DebitCategory.MILK.name.lowercase(), "dairy", "milk & dairy" -> Triple(Icons.Filled.WaterDrop, Color(0xFFEFF6FF), Color(0xFF2563EB))
        "newspaper", DebitCategory.NEWSPAPER.name.lowercase(), "paper", "magazines" -> Triple(Icons.AutoMirrored.Filled.MenuBook, Color(0xFFF8FAFC), Color(0xFF475569))
        "ott", DebitCategory.OTT.name.lowercase(), "subscriptions", "subscription", "netflix", "prime" -> Triple(Icons.Filled.Tv, Color(0xFFEEF2FF), Color(0xFF4F46E5))
        "debt_repay", DebitCategory.DEBT_REPAY.name.lowercase(), "clearing borrowed money" -> Triple(Icons.Filled.CheckCircleOutline, Color(0xFFECFDF5), Color(0xFF059669))
        "lending", DebitCategory.LENDING.name.lowercase(), "lending to someone" -> Triple(Icons.Filled.Handshake, Color(0xFFEFF6FF), Color(0xFF3B82F6))
        "donation", DebitCategory.DONATION.name.lowercase(), "charity" -> Triple(Icons.Filled.VolunteerActivism, Color(0xFFFFF1F2), Color(0xFFE11D48))
        "insurance", DebitCategory.INSURANCE.name.lowercase(), "mediclaim", "policy" -> Triple(Icons.Filled.Shield, Color(0xFFFDF2F8), Color(0xFFBE185D))
        "pharmacy", DebitCategory.PHARMACY.name.lowercase(), "medicine", "medicines", "hospital" -> Triple(Icons.Filled.LocalHospital, Color(0xFFFEF2F2), Color(0xFFDC2626))
        "travel", DebitCategory.TRAVEL.name.lowercase(), "commute", "cab", "metro", "bus" -> Triple(Icons.Filled.DirectionsCar, Color(0xFFF0FDFA), Color(0xFF0D9488))
        "tours", DebitCategory.TOURS.name.lowercase(), "vacation", "trip", "flight" -> Triple(Icons.Filled.Flight, Color(0xFFFAF5FF), Color(0xFFA855F7))
        "dining", "outside_food", DebitCategory.OUTSIDE_FOOD.name.lowercase(), "food", "restaurant", "swiggy", "zomato" -> Triple(Icons.Filled.Restaurant, Color(0xFFFFF7ED), Color(0xFFEA580C))
        "school", "school_fees", DebitCategory.SCHOOL_FEES.name.lowercase(), "education", "tuition" -> Triple(Icons.Filled.School, IndPurpleLight, IndPurple)
        "hostel", "hostel_fees", DebitCategory.HOSTEL_FEES.name.lowercase(), "pg", "accommodation" -> Triple(Icons.Filled.Hotel, Color(0xFFF5F3FF), Color(0xFF8B5CF6))
        "maintenance", DebitCategory.MAINTENANCE.name.lowercase(), "apartment maintenance", "repair" -> Triple(Icons.Filled.HomeRepairService, Color(0xFFFEF3C7), Color(0xFFD97706))
        "general", DebitCategory.GENERAL.name.lowercase() -> Triple(Icons.Filled.Receipt, Color(0xFFF1F5F9), Color(0xFF475569))
        "all" -> Triple(Icons.Filled.AllInclusive, IndBlueLight, IndNavyHeader)
        "paid" -> Triple(Icons.Filled.CheckCircle, IndGreenLight, IndGreenDark)
        "unpaid" -> Triple(Icons.Filled.HourglassTop, IndAmberLight, Color(0xFFD97706))
        "recurring" -> Triple(Icons.Filled.Repeat, Color(0xFFE0F2FE), Color(0xFF0284C7))
        else -> Triple(Icons.Filled.Category, IndAmberLight, customColor ?: IndAmber)
    }
}

fun getCategoryIcon(categoryKey: String): ImageVector = getCategoryStyling(categoryKey).first

@Composable
fun CategoryIconBadge(
    categoryKey: String,
    modifier: Modifier = Modifier,
    badgeSize: androidx.compose.ui.unit.Dp = 40.dp,
    iconSize: androidx.compose.ui.unit.Dp = 22.dp,
    customColor: Color? = null
) {
    val (icon, bgColor, tintColor) = getCategoryStyling(categoryKey, customColor)

    Box(
        modifier = modifier
            .size(badgeSize)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, tintColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = categoryKey,
            tint = tintColor,
            modifier = Modifier.size(iconSize)
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
    breakdown: List<CategorySummary>,
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
