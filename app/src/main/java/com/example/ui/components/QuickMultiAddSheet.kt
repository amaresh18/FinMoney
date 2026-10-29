@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CustomCategory
import com.example.data.model.CustomExpenseTag
import com.example.data.model.DebitCategory
import com.example.data.model.DebitItem
import com.example.ui.theme.*
import java.util.UUID

/**
 * Observable Mutable State for an individual Expense Row in Multi-Add.
 * Using Compose MutableState for every field ensures instant reactivity on user input.
 */
class MultiAddRowState(
    val id: String = UUID.randomUUID().toString(),
    initialCategoryKey: String = "",
    initialCustomCategoryName: String = "",
    initialAmountText: String = "",
    initialIsPaid: Boolean = true,
    initialIsPaidViaCreditCard: Boolean = false,
    initialTag: String = "",
    initialNotes: String = "",
    initialDay: Int = 1,
    initialShowNotesField: Boolean = false,
    initialPrincipalAmountText: String = "",
    initialInterestRateText: String = "",
    initialTotalTenureMonthsText: String = "",
    initialCurrentMonthTenureText: String = "1",
    initialIsRecurring: Boolean = false,
    initialSelectedDateMillis: Long = System.currentTimeMillis()
) {
    var categoryKey by mutableStateOf(initialCategoryKey)
    var customCategoryName by mutableStateOf(initialCustomCategoryName)
    var amountText by mutableStateOf(initialAmountText)
    var isPaid by mutableStateOf(initialIsPaid)
    var isPaidViaCreditCard by mutableStateOf(initialIsPaidViaCreditCard)
    var tag by mutableStateOf(initialTag)
    var notes by mutableStateOf(initialNotes)
    var day by mutableStateOf(initialDay)
    var showNotesField by mutableStateOf(initialShowNotesField)

    var principalAmountText by mutableStateOf(initialPrincipalAmountText)
    var interestRateText by mutableStateOf(initialInterestRateText)
    var totalTenureMonthsText by mutableStateOf(initialTotalTenureMonthsText)
    var currentMonthTenureText by mutableStateOf(initialCurrentMonthTenureText)
    var isRecurring by mutableStateOf(initialIsRecurring)
    var selectedDateMillis by mutableStateOf(initialSelectedDateMillis)
}

/**
 * Checks if category is strictly LOAN/EMI, Electricity & Water Bill, or CHIT
 * for which "Due Date" applies. All other categories use "Transaction Date".
 */
fun isDueDateExpenseCategory(categoryKey: String, customCategoryName: String = "", titleOrTag: String = ""): Boolean {
    val upperCat = categoryKey.uppercase().trim()
    val upperCustom = customCategoryName.uppercase().trim()
    val upperTag = titleOrTag.uppercase().trim()

    val isLoanEmi = isLoanOrEmi(upperCat) ||
            upperCat in listOf("LOAN", "EMI", "DEBT_REPAY") ||
            upperTag.contains("LOAN") || upperTag.contains("EMI")
    val isElectricityWater = upperCat == DebitCategory.ELECTRICITY.name ||
            upperCat.contains("ELECTRIC") || upperCat.contains("WATER") ||
            upperCustom.contains("ELECTRIC") || upperCustom.contains("WATER") ||
            upperTag.contains("ELECTRIC") || upperTag.contains("WATER")
    val isChit = upperCat == "CHIT" || upperCat == DebitCategory.CHIT.name ||
            upperCustom.contains("CHIT") || upperTag.contains("CHIT")

    return isLoanEmi || isElectricityWater || isChit
}

data class CategoryMeta(
    val key: String,
    val displayName: String,
    val colorHex: String,
    val group: String = "DAILY", // ESSENTIALS, DAILY, BILLS, LIFESTYLE, CUSTOM
    val isCustom: Boolean = false,
    val customName: String = "",
    val iconName: String = ""
)

/**
 * Returns contextual smart subcategory tags aligned to each specific category
 */
private fun formatAmountForRowInput(amount: Double): String {
    if (amount <= 0) return ""
    return if (amount % 1.0 == 0.0) amount.toInt().toString() else amount.toString()
}

fun debitItemToMultiAddRowState(item: DebitItem, todayDay: Int): MultiAddRowState {
    val isCustom = item.category.equals(DebitCategory.CUSTOM.name, ignoreCase = true)
    val categoryKey = when {
        isCustom -> DebitCategory.CUSTOM.name
        item.category.isNotBlank() -> item.category
        else -> DebitCategory.GENERAL.name
    }
    val customName = if (isCustom) item.customCategoryName else ""
    val defaultLabel = if (isCustom) customName.ifBlank { "Custom" } else categoryKey
    val tagForRow = if (
        item.title.isNotBlank() &&
            !item.title.equals(defaultLabel, ignoreCase = true) &&
            !item.title.equals(categoryKey, ignoreCase = true)
    ) {
        item.title
    } else {
        ""
    }
    val itemMillis = item.dueDate?.takeIf { it > 0L }
        ?: item.paidDateTimestamp?.takeIf { it > 0L }
        ?: item.createdAt.takeIf { it > 0L }
        ?: java.util.Calendar.getInstance().apply {
            set(item.year, (item.month - 1).coerceIn(0, 11), item.dueDateDay.coerceIn(1, 31), 12, 0, 0)
        }.timeInMillis

    return MultiAddRowState(
        initialCategoryKey = categoryKey,
        initialCustomCategoryName = customName,
        initialAmountText = formatAmountForRowInput(item.amount),
        initialIsPaid = item.isPaid,
        initialIsPaidViaCreditCard = item.isPaidViaCreditCard,
        initialTag = tagForRow,
        initialNotes = item.notes,
        initialDay = item.dueDateDay.coerceIn(1, 31).takeIf { it > 0 } ?: todayDay,
        initialShowNotesField = item.notes.isNotBlank(),
        initialPrincipalAmountText = formatAmountForRowInput(item.principalAmount),
        initialInterestRateText = if (item.interestRate > 0) item.interestRate.toString() else "",
        initialTotalTenureMonthsText = if (item.totalTenureMonths > 0) item.totalTenureMonths.toString() else "",
        initialCurrentMonthTenureText = if (item.currentMonthTenure > 0) item.currentMonthTenure.toString() else "1",
        initialIsRecurring = item.isRecurring,
        initialSelectedDateMillis = itemMillis
    )
}

fun getAlignedTagsForCategory(categoryKey: String): List<String> {
    return when (categoryKey.uppercase()) {
        DebitCategory.OUTSIDE_FOOD.name -> listOf("Dinner", "Lunch", "Breakfast", "Snacks", "Party", "Drinks", "Cafe")
        DebitCategory.GROCERIES.name -> listOf("Supermarket", "Vegetables", "Fruits", "Dairy & Milk", "Provisions", "Blinkit/Zepto", "Organic")
        DebitCategory.FUEL.name -> listOf("Petrol", "Diesel", "EV Charging", "Car Fuel", "Bike Fuel", "Highway Toll")
        DebitCategory.SHOPPING.name -> listOf("Clothing", "Electronics", "Footwear", "Home Decor", "Gifts", "Personal Care")
        DebitCategory.TRAVEL.name -> listOf("Cab", "Auto", "Parking", "Toll", "Flight", "Bus Ticket")
        DebitCategory.TOURS.name -> listOf("Hotel Stay", "Sightseeing", "Flight/Train", "Holiday Package", "Resort")
        DebitCategory.PHARMACY.name -> listOf("Monthly Medicines", "Doctor Consultation", "Lab Tests", "First Aid", "Supplements")
        DebitCategory.ELECTRICITY.name -> listOf("Power Bill", "Water Bill", "Gas Cylinder")
        DebitCategory.INTERNET.name -> listOf("WiFi Broadband", "Fiber Bill", "Annual Plan")
        DebitCategory.RECHARGE.name -> listOf("Mobile Prepaid", "Mobile Postpaid", "DTH / Cable", "Data Pack")
        DebitCategory.EMI.name -> listOf("Home Loan EMI", "Personal Loan", "Car / Auto Loan", "Education Loan", "Gadget Loan", "Gold Loan", "Credit Card EMI", "Other Loan")
        DebitCategory.CHIT.name -> listOf("Monthly Installment", "Chit Dividend", "Auction Payment")
        DebitCategory.OTT.name -> listOf("Netflix", "Amazon Prime", "Disney+ Hotstar", "Spotify/Apple Music", "YouTube Premium")
        DebitCategory.MAID.name -> listOf("Maid Salary", "Cook Salary", "Deep Cleaning", "Bonus")
        DebitCategory.MILK.name -> listOf("Daily Milk", "Milk Card", "Paneer & Dairy")
        DebitCategory.RENT.name -> listOf("House Rent", "Maintenance", "Advance")
        DebitCategory.SCHOOL_FEES.name -> listOf("Tuition Fees", "School Bus", "Books & Uniform", "Exam Fees")
        DebitCategory.HOSTEL_FEES.name -> listOf("Room Rent", "Mess Charges", "Hostel Dues")
        DebitCategory.MAINTENANCE.name -> listOf("Apartment Maintenance", "Society Dues", "Repair Work")
        DebitCategory.INVESTMENT.name -> listOf("Mutual Fund SIP", "Stock Investment", "Gold SIP", "PPF")
        DebitCategory.SAVING.name -> listOf("Recurring Deposit", "Emergency Fund", "Fixed Deposit")
        DebitCategory.INSURANCE.name -> listOf("Health Insurance", "Term Life", "Car/Bike Insurance")
        DebitCategory.DEBT_REPAY.name -> listOf("Friend Repayment", "Borrowed Clear", "Credit Card Bill")
        DebitCategory.LENDING.name -> listOf("Given to Friend", "Given to Family", "Emergency Loan")
        DebitCategory.DONATION.name -> listOf("Charity", "Temple / Puja", "Social Cause")
        else -> listOf("Personal", "Family", "Office", "Weekend", "Cash", "Online", "Emergency", "Miscellaneous")
    }
}

private fun prependSessionCustomCategories(
    base: List<CategoryMeta>,
    sessionNames: List<String>,
    allCategoryMetas: List<CategoryMeta>
): List<CategoryMeta> {
    if (sessionNames.isEmpty()) return base
    val pinned = sessionNames.mapNotNull { name ->
        allCategoryMetas.find { it.isCustom && it.customName.equals(name, ignoreCase = true) }
    }
    return (pinned + base).distinctBy { if (it.isCustom) it.customName.lowercase() else it.key }.take(10)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickMultiAddSheet(
    currencySymbol: String,
    todayDay: Int,
    month: Int,
    year: Int,
    customCategories: List<CustomCategory>,
    customExpenseTags: List<CustomExpenseTag> = emptyList(),
    allHistoricalDebits: List<DebitItem> = emptyList(),
    initialCategoryKey: String? = null,
    pendingCustomCategoryName: String? = null,
    pendingCustomCategoryRowId: String? = null,
    onPendingCustomCategoryApplied: () -> Unit = {},
    editingDebit: DebitItem? = null,
    onDismiss: () -> Unit,
    onSaveBatch: (List<DebitItem>) -> Unit,
    onDeleteDebit: ((DebitItem) -> Unit)? = null,
    onOpenAddCustomCategory: (rowIdForSelection: String?) -> Unit = { _ -> },
    onSaveCustomExpenseTag: (name: String, iconName: String, colorHex: String) -> Unit = { _, _, _ -> }
) {
    val isEditMode = editingDebit != null
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current

    // Build comprehensive list of all standard + custom categories
    val allCategoryMetas = remember(customCategories) {
        val standard = listOf(
            CategoryMeta(DebitCategory.GROCERIES.name, "Groceries & Market", "#059669", "DAILY"),
            CategoryMeta(DebitCategory.OUTSIDE_FOOD.name, "Outside Food / Dining", "#F59E0B", "DAILY"),
            CategoryMeta(DebitCategory.FUEL.name, "Fuel & Petrol", "#EA580C", "DAILY"),
            CategoryMeta(DebitCategory.MILK.name, "Milk & Daily Dairy", "#0284C7", "ESSENTIALS"),
            CategoryMeta(DebitCategory.MAID.name, "Maid & Cook", "#D97706", "ESSENTIALS"),
            CategoryMeta(DebitCategory.RENT.name, "Rent / Housing", "#0066FF", "ESSENTIALS"),
            CategoryMeta(DebitCategory.EMI.name, "Loan / EMI", "#F43F5E", "ESSENTIALS"),
            CategoryMeta(DebitCategory.CHIT.name, "Chit Payments", "#6D28D9", "ESSENTIALS"),
            CategoryMeta(DebitCategory.ELECTRICITY.name, "Electricity & Water", "#F59E0B", "BILLS"),
            CategoryMeta(DebitCategory.INTERNET.name, "Internet & WiFi", "#0284C7", "BILLS"),
            CategoryMeta(DebitCategory.RECHARGE.name, "Recharge / Mobile", "#0284C7", "BILLS"),
            CategoryMeta(DebitCategory.MAINTENANCE.name, "Maintenance", "#64748B", "BILLS"),
            CategoryMeta(DebitCategory.NEWSPAPER.name, "Newspaper", "#64748B", "BILLS"),
            CategoryMeta(DebitCategory.SHOPPING.name, "Shopping & Lifestyle", "#DB2777", "LIFESTYLE"),
            CategoryMeta(DebitCategory.TRAVEL.name, "Travel & Commute", "#F97316", "LIFESTYLE"),
            CategoryMeta(DebitCategory.TOURS.name, "Tours & Vacation", "#06B6D4", "LIFESTYLE"),
            CategoryMeta(DebitCategory.OTT.name, "OTT Subscriptions", "#8B5CF6", "LIFESTYLE"),
            CategoryMeta(DebitCategory.PHARMACY.name, "Pharmacy & Medicines", "#14B8A6", "ESSENTIALS"),
            CategoryMeta(DebitCategory.INSURANCE.name, "Insurance & Mediclaim", "#3B82F6", "ESSENTIALS"),
            CategoryMeta(DebitCategory.SCHOOL_FEES.name, "School Fees", "#6366F1", "ESSENTIALS"),
            CategoryMeta(DebitCategory.HOSTEL_FEES.name, "Hostel Fees", "#4F46E5", "ESSENTIALS"),
            CategoryMeta(DebitCategory.INVESTMENT.name, "Investment / SIP", "#00B377", "LIFESTYLE"),
            CategoryMeta(DebitCategory.SAVING.name, "Saving / RD", "#0F766E", "LIFESTYLE"),
            CategoryMeta(DebitCategory.DEBT_REPAY.name, "Clearing Debt", "#EF4444", "ESSENTIALS"),
            CategoryMeta(DebitCategory.LENDING.name, "Lending to Someone", "#F97316", "ESSENTIALS"),
            CategoryMeta(DebitCategory.DONATION.name, "Donation & Charity", "#EC4899", "LIFESTYLE"),
            CategoryMeta(DebitCategory.GENERAL.name, "General Expenses", "#64748B", "DAILY")
        )
        val custom = customCategories.map {
            CategoryMeta(
                key = DebitCategory.CUSTOM.name,
                displayName = it.name,
                colorHex = it.colorHex,
                group = "CUSTOM",
                isCustom = true,
                customName = it.name,
                iconName = it.iconName
            )
        }
        standard + custom
    }

    var sessionNewCustomNames by remember { mutableStateOf(listOf<String>()) }

    // Compute frequent categories used in the last 30 days
    val frequentCategories = remember(allHistoricalDebits, allCategoryMetas, sessionNewCustomNames) {
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000L)
        val recentDebits = allHistoricalDebits.filter { it.createdAt >= thirtyDaysAgo }

        if (recentDebits.isNotEmpty()) {
            val counts = recentDebits.groupBy { debit ->
                if (debit.category == DebitCategory.CUSTOM.name) "CUSTOM_${debit.customCategoryName.trim().lowercase()}"
                else debit.category
            }.mapValues { it.value.size }

            val sorted = counts.entries.sortedByDescending { it.value }.map { it.key }

            val resolved = sorted.mapNotNull { key ->
                if (key.startsWith("CUSTOM_")) {
                    val customName = key.removePrefix("CUSTOM_")
                    allCategoryMetas.find { it.isCustom && it.customName.trim().equals(customName, ignoreCase = true) }
                } else {
                    allCategoryMetas.find { !it.isCustom && it.key == key }
                }
            }

            val base = if (resolved.size >= 4) {
                resolved.take(8)
            } else {
                val defaults = listOf(
                    DebitCategory.GROCERIES.name,
                    DebitCategory.OUTSIDE_FOOD.name,
                    DebitCategory.FUEL.name,
                    DebitCategory.MILK.name,
                    DebitCategory.SHOPPING.name,
                    DebitCategory.ELECTRICITY.name,
                    DebitCategory.EMI.name
                ).mapNotNull { k -> allCategoryMetas.find { it.key == k } }
                (resolved + defaults).distinctBy { if (it.isCustom) it.customName else it.key }.take(8)
            }
            prependSessionCustomCategories(base, sessionNewCustomNames, allCategoryMetas)
        } else {
            val defaults = listOf(
                DebitCategory.GROCERIES.name,
                DebitCategory.OUTSIDE_FOOD.name,
                DebitCategory.FUEL.name,
                DebitCategory.MILK.name,
                DebitCategory.SHOPPING.name,
                DebitCategory.ELECTRICITY.name,
                DebitCategory.EMI.name,
                DebitCategory.RENT.name
            ).mapNotNull { k -> allCategoryMetas.find { it.key == k } }
            prependSessionCustomCategories(defaults, sessionNewCustomNames, allCategoryMetas)
        }
    }

    // Dynamic list of vertical entry rows using reactive MultiAddRowState
    val resolvedInitialCat = remember(initialCategoryKey, allCategoryMetas) {
        if (!initialCategoryKey.isNullOrBlank()) {
            allCategoryMetas.find { it.key == initialCategoryKey || (it.isCustom && it.customName.equals(initialCategoryKey, ignoreCase = true)) }
        } else null
    }

    val entryRows = remember(editingDebit?.id, initialCategoryKey) {
        when {
            editingDebit != null -> mutableStateListOf(debitItemToMultiAddRowState(editingDebit, todayDay))
            resolvedInitialCat != null -> mutableStateListOf(
                MultiAddRowState(
                    initialCategoryKey = resolvedInitialCat.key,
                    initialCustomCategoryName = resolvedInitialCat.customName,
                    initialDay = todayDay
                )
            )
            else -> mutableStateListOf()
        }
    }

    var validationError by remember { mutableStateOf<String?>(null) }
    var activeTagPickerRow by remember { mutableStateOf<MultiAddRowState?>(null) }
    var activeCategoryPickerRow by remember { mutableStateOf<MultiAddRowState?>(null) }
    var showAddCustomExpenseTagDialog by remember { mutableStateOf(false) }
    var pendingNewTagForRow by remember { mutableStateOf<MultiAddRowState?>(null) }
    var pendingCustomTagName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pendingCustomCategoryName, customCategories) {
        val name = pendingCustomCategoryName?.trim()?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val meta = customCategories.find { it.name.equals(name, ignoreCase = true) } ?: return@LaunchedEffect
        sessionNewCustomNames = (listOf(meta.name) + sessionNewCustomNames).distinct().take(5)
        val row = pendingCustomCategoryRowId?.let { id -> entryRows.find { it.id == id } }
            ?: activeCategoryPickerRow
            ?: entryRows.lastOrNull()
        if (row != null) {
            row.categoryKey = DebitCategory.CUSTOM.name
            row.customCategoryName = meta.name
        } else if (!isEditMode) {
            entryRows.add(
                MultiAddRowState(
                    initialCategoryKey = DebitCategory.CUSTOM.name,
                    initialCustomCategoryName = meta.name,
                    initialDay = todayDay
                )
            )
        }
        validationError = null
        onPendingCustomCategoryApplied()
    }

    // Real-time calculation of total amount and valid item count
    val totalAmount = entryRows.sumOf { it.amountText.toDoubleOrNull() ?: 0.0 }
    val totalCount = entryRows.count { (it.amountText.toDoubleOrNull() ?: 0.0) > 0.0 }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = IndSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.fillMaxHeight(0.92f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp)
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(IndBlueLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isEditMode) Icons.Filled.Edit else Icons.Filled.Bolt,
                            contentDescription = null,
                            tint = IndBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = if (isEditMode) "Edit expense" else "⚡ Add Planned & Quick Entries",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = IndTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isEditMode) {
                                "Update category, amount, paid status, EMI fields & tags"
                            } else {
                                "Add single or multiple entries • CC, EMI, Paid Status & Category Tags"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = IndTextSecondary,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isEditMode && editingDebit != null && onDeleteDebit != null) {
                        IconButton(
                            onClick = {
                                onDeleteDebit(editingDebit)
                                onDismiss()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete expense", tint = IndRed, modifier = Modifier.size(20.dp))
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = IndTextMuted, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (!isEditMode) {
            // Frequent Categories (30-day frequency quick-add bar)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(IndCardSecondary.copy(alpha = 0.7f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🔥 Frequent (Past 30 Days) • Tap to Add Row:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = IndNavyHeader
                    )
                    Text(
                        text = "+ Row",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = IndBlue,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                entryRows.add(MultiAddRowState(initialDay = todayDay))
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(frequentCategories) { cat ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = IndSurface,
                            border = androidx.compose.foundation.BorderStroke(0.6.dp, IndBorderSubtle),
                            modifier = Modifier.clickable {
                                entryRows.add(
                                    MultiAddRowState(
                                        initialCategoryKey = cat.key,
                                        initialCustomCategoryName = cat.customName,
                                        initialDay = todayDay
                                    )
                                )
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                CategoryIconBadge(
                                    categoryKey = if (cat.isCustom) cat.customName else cat.key,
                                    badgeSize = 22.dp,
                                    iconSize = 13.dp,
                                    customColor = customCategoryTint(cat.colorHex),
                                    storedIconName = cat.iconName.takeIf { it.isNotBlank() }
                                )
                                Text(
                                    text = cat.displayName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = IndTextPrimary
                                )
                                Text(
                                    text = "+",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = IndBlue
                                )
                            }
                        }
                    }
                }
            }
            }

            // Error banner if any
            if (validationError != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = IndRedLight,
                    border = androidx.compose.foundation.BorderStroke(0.6.dp, IndRed.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = IndRed, modifier = Modifier.size(16.dp))
                        Text(
                            text = validationError ?: "",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndRedDark,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Vertical List of Expenses (Auto-fit, cleanly aligned)
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 90.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (entryRows.isEmpty()) {
                        item(key = "empty_add_hint") {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = IndCardSecondary.copy(alpha = 0.6f),
                                border = androidx.compose.foundation.BorderStroke(0.6.dp, IndBorderSubtle),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Category,
                                        contentDescription = null,
                                        tint = IndTextMuted,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Text(
                                        text = "No entries yet",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = IndTextPrimary
                                    )
                                    Text(
                                        text = "Tap a category above to add an expense row, or use + Row for a blank entry.",
                                        fontSize = 12.sp,
                                        color = IndTextSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    itemsIndexed(entryRows, key = { _, row -> row.id }) { index, rowState ->
                        MultiAddRowCard(
                            index = index + 1,
                            rowState = rowState,
                            currencySymbol = currencySymbol,
                            allCategories = allCategoryMetas,
                            customExpenseTags = customExpenseTags,
                            onOpenCategoryPicker = {
                                activeCategoryPickerRow = rowState
                            },
                            onOpenTagPicker = {
                                activeTagPickerRow = rowState
                            },
                            onDelete = {
                                if (activeCategoryPickerRow?.id == rowState.id) {
                                    activeCategoryPickerRow = null
                                }
                                if (activeTagPickerRow?.id == rowState.id) {
                                    activeTagPickerRow = null
                                }
                                entryRows.removeAt(index)
                            },
                            focusManager = focusManager,
                            allowDeleteRow = !isEditMode
                        )
                    }

                    // Add Row Button at bottom of list
                    if (!isEditMode) item {
                        OutlinedButton(
                            onClick = {
                                entryRows.add(
                                    MultiAddRowState(
                                        initialCategoryKey = resolvedInitialCat?.key ?: "",
                                        initialCustomCategoryName = resolvedInitialCat?.customName ?: "",
                                        initialDay = todayDay
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = IndBlue
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, IndBlue.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Filled.AddCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Add Another Debit Row",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Sticky Bottom 1-Tap Save All Bar
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = IndNavyHeader,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = when {
                                    isEditMode && totalCount > 0 -> "Ready to update"
                                    isEditMode -> "Fill category & amount"
                                    totalCount > 0 -> "$totalCount ${if (totalCount == 1) "Debit" else "Debits"} ready"
                                    else -> "Fill category & amount"
                                },
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.8f),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "$currencySymbol${"%,.2f".format(totalAmount).trimEnd('0').trimEnd('.')}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }

                        Button(
                            onClick = {
                                val itemsToSave = mutableListOf<DebitItem>()
                                var hasInvalidRow = false

                                for ((idx, row) in entryRows.withIndex()) {
                                    val amt = row.amountText.toDoubleOrNull() ?: 0.0
                                    if (row.amountText.isNotBlank() && amt <= 0.0) {
                                        validationError = "Row #${idx + 1} has an invalid amount. Enter a valid number."
                                        hasInvalidRow = true
                                        break
                                    }
                                    if (amt > 0.0) {
                                        val categoryReady = row.categoryKey.isNotBlank() &&
                                            (row.categoryKey != DebitCategory.CUSTOM.name || row.customCategoryName.isNotBlank())
                                        if (!categoryReady) {
                                            validationError = "Row #${idx + 1} is missing a category. Category is mandatory."
                                            hasInvalidRow = true
                                            break
                                        }
                                        val displayCategoryName = if (row.categoryKey == DebitCategory.CUSTOM.name) {
                                            row.customCategoryName
                                        } else {
                                            DebitCategory.entries.find { it.name == row.categoryKey }?.name ?: row.categoryKey
                                        }

                                        val finalTitle = if (row.tag.isNotBlank()) {
                                            row.tag.removePrefix("#").trim()
                                        } else {
                                            displayCategoryName
                                        }

                                        val isChitCat = row.categoryKey.equals("CHIT", ignoreCase = true) || finalTitle.contains("CHIT", ignoreCase = true)
                                        val isLoanCat = isLoanOrEmi(row.categoryKey)

                                        val base = editingDebit
                                        val rowCal = java.util.Calendar.getInstance().apply { timeInMillis = row.selectedDateMillis }
                                        val rowDay = rowCal.get(java.util.Calendar.DAY_OF_MONTH)
                                        val rowMonth = rowCal.get(java.util.Calendar.MONTH) + 1
                                        val rowYear = rowCal.get(java.util.Calendar.YEAR)

                                        itemsToSave.add(
                                            DebitItem(
                                                id = base?.id ?: 0L,
                                                userId = base?.userId ?: "",
                                                cloudId = base?.cloudId ?: "",
                                                month = rowMonth,
                                                year = rowYear,
                                                monthYear = "$rowYear-${if (rowMonth < 10) "0$rowMonth" else "$rowMonth"}",
                                                category = row.categoryKey,
                                                customCategoryName = row.customCategoryName,
                                                title = finalTitle,
                                                amount = amt,
                                                dueDateDay = rowDay,
                                                dueDate = row.selectedDateMillis,
                                                isPaid = row.isPaid,
                                                isPaidViaCreditCard = row.isPaidViaCreditCard,
                                                paidDateTimestamp = if (row.isPaid) (base?.paidDateTimestamp ?: row.selectedDateMillis) else null,
                                                paidDate = if (row.isPaid) (base?.paidDate ?: row.selectedDateMillis) else null,
                                                notes = row.notes.trim(),
                                                principalAmount = row.principalAmountText.toDoubleOrNull() ?: 0.0,
                                                interestRate = row.interestRateText.toDoubleOrNull() ?: 0.0,
                                                interestRatePercent = base?.interestRatePercent ?: 0.0,
                                                totalTenureMonths = row.totalTenureMonthsText.toIntOrNull() ?: 0,
                                                currentMonthTenure = row.currentMonthTenureText.toIntOrNull() ?: 1,
                                                isRecurring = row.isRecurring || isChitCat || isLoanCat,
                                                createdAt = base?.createdAt ?: System.currentTimeMillis()
                                            )
                                        )
                                    }
                                }

                                if (!hasInvalidRow) {
                                    if (itemsToSave.isEmpty()) {
                                        validationError = "Please enter an amount (> 0) for at least one debit."
                                    } else if (isEditMode && itemsToSave.size != 1) {
                                        validationError = "Edit mode supports one expense at a time."
                                    } else {
                                        onSaveBatch(itemsToSave)
                                        onDismiss()
                                    }
                                }
                            },
                            enabled = totalCount > 0,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = IndGreen,
                                contentColor = Color.White,
                                disabledContainerColor = Color.White.copy(alpha = 0.2f),
                                disabledContentColor = Color.White.copy(alpha = 0.5f)
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when {
                                    isEditMode -> "Save changes"
                                    totalCount > 0 -> "⚡ Save $totalCount (1-Tap)"
                                    else -> "Save All"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // Category Picker Dialog / Sheet
    if (activeCategoryPickerRow != null) {
        val targetRow = activeCategoryPickerRow!!
        CategorySelectionModalDialog(
            currentCategoryKey = targetRow.categoryKey,
            currentCustomName = targetRow.customCategoryName,
            allCategories = allCategoryMetas,
            onDismiss = { activeCategoryPickerRow = null },
            onCategorySelected = { key, customName ->
                targetRow.categoryKey = key
                targetRow.customCategoryName = customName
                validationError = null
                activeCategoryPickerRow = null
            },
            onOpenAddCustomCategory = {
                onOpenAddCustomCategory(targetRow.id)
            }
        )
    }

    // Subcategory / Tag Selection Dialog with contextual aligned suggestions
    if (activeTagPickerRow != null) {
        val targetRow = activeTagPickerRow!!
        SubcategoryTagPickerDialog(
            categoryKey = targetRow.categoryKey,
            currentTag = targetRow.tag,
            customExpenseTags = customExpenseTags,
            onDismiss = { activeTagPickerRow = null },
            onTagSelected = { selectedTag ->
                targetRow.tag = selectedTag
                activeTagPickerRow = null
            },
            onOpenAddCustomTag = {
                pendingNewTagForRow = targetRow
                showAddCustomExpenseTagDialog = true
            }
        )
    }

    LaunchedEffect(pendingCustomTagName, customExpenseTags) {
        val name = pendingCustomTagName?.trim()?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (customExpenseTags.none { it.name.equals(name, ignoreCase = true) }) return@LaunchedEffect
        pendingNewTagForRow?.tag = name
        pendingCustomTagName = null
        pendingNewTagForRow = null
        showAddCustomExpenseTagDialog = false
    }

    if (showAddCustomExpenseTagDialog) {
        AddCustomExpenseTagDialog(
            onDismiss = {
                showAddCustomExpenseTagDialog = false
                pendingNewTagForRow = null
            },
            onSave = { name, icon, color ->
                pendingCustomTagName = name
                onSaveCustomExpenseTag(name, icon, color)
            }
        )
    }
}

/**
 * Individual Expense Item Row in Vertical View.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiAddRowCard(
    index: Int,
    rowState: MultiAddRowState,
    currencySymbol: String,
    allCategories: List<CategoryMeta>,
    customExpenseTags: List<CustomExpenseTag> = emptyList(),
    onOpenCategoryPicker: () -> Unit,
    onOpenTagPicker: () -> Unit,
    onDelete: () -> Unit,
    focusManager: androidx.compose.ui.focus.FocusManager,
    allowDeleteRow: Boolean = true
) {
    val hasCategorySelected = rowState.categoryKey.isNotBlank() ||
        (rowState.categoryKey == DebitCategory.CUSTOM.name && rowState.customCategoryName.isNotBlank())

    val currentMeta = remember(rowState.categoryKey, rowState.customCategoryName, allCategories) {
        if (!hasCategorySelected) {
            CategoryMeta("", "Select category", "#94A3B8", "DAILY")
        } else if (rowState.categoryKey == DebitCategory.CUSTOM.name) {
            allCategories.find { it.isCustom && it.customName.equals(rowState.customCategoryName, ignoreCase = true) }
                ?: CategoryMeta(DebitCategory.CUSTOM.name, rowState.customCategoryName.ifBlank { "Custom" }, "#00D09C", "CUSTOM", true, rowState.customCategoryName)
        } else {
            allCategories.find { !it.isCustom && it.key == rowState.categoryKey }
                ?: CategoryMeta(rowState.categoryKey, rowState.categoryKey, "#0066FF")
        }
    }

    val isAmountValid = (rowState.amountText.toDoubleOrNull() ?: 0.0) > 0.0

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isAmountValid) IndCardHighlight.copy(alpha = 0.5f) else IndSurface,
        border = androidx.compose.foundation.BorderStroke(
            if (isAmountValid) 1.dp else 0.6.dp,
            if (isAmountValid) IndNavyHeader.copy(alpha = 0.5f) else IndBorder
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: Index Badge, Category Selector Dropdown Button (Mandatory), Amount Input (Mandatory), Delete Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Index badge
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(IndNavyHeader),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$index",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Category Selector Button (Mandatory)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = IndCardSecondary,
                    border = androidx.compose.foundation.BorderStroke(0.6.dp, IndBorderSubtle),
                    modifier = Modifier
                        .weight(1.3f)
                        .height(48.dp)
                        .clickable { onOpenCategoryPicker() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (hasCategorySelected) {
                                CategoryIconBadge(
                                    categoryKey = if (currentMeta.isCustom) currentMeta.customName else currentMeta.key,
                                    badgeSize = 28.dp,
                                    iconSize = 17.dp,
                                    customColor = customCategoryTint(currentMeta.colorHex),
                                    storedIconName = currentMeta.iconName.takeIf { it.isNotBlank() }
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(IndBorderSubtle),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Category,
                                        contentDescription = null,
                                        tint = IndTextMuted,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                            Text(
                                text = currentMeta.displayName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (hasCategorySelected) IndTextPrimary else IndTextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = "Select Category",
                            tint = IndNavyHeader,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Amount Field (Mandatory - Exact value entry, no rounded constraints, vertically centered)
                OutlinedTextField(
                    value = rowState.amountText,
                    onValueChange = { input ->
                        val clean = input.filter { it.isDigit() || it == '.' }
                        rowState.amountText = clean
                    },
                    placeholder = { Text("0.00", fontSize = 13.sp, color = IndTextMuted) },
                    prefix = { Text(currencySymbol, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = IndNavyHeader) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = IndNavyHeader,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Start
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1.1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = IndCardSecondary,
                        unfocusedContainerColor = if (isAmountValid) IndCardSecondary else IndCardSecondary.copy(alpha = 0.5f),
                        focusedBorderColor = IndNavyHeader,
                        unfocusedBorderColor = if (isAmountValid) IndNavyHeader else IndBorderSubtle
                    )
                )

                if (allowDeleteRow) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Remove Row",
                            tint = IndRed.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Row 2: Control Chips (Credit Card Toggle, Paid Status Toggle, Subcategory Tag, Notes toggle)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 💳 Credit Card Toggle (Renamed from Cash/Debit to Credit Card)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (rowState.isPaidViaCreditCard) IndPurple else IndSurface,
                    border = androidx.compose.foundation.BorderStroke(
                        0.8.dp,
                        if (rowState.isPaidViaCreditCard) IndPurple else IndBorder
                    ),
                    modifier = Modifier.clickable {
                        rowState.isPaidViaCreditCard = !rowState.isPaidViaCreditCard
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (rowState.isPaidViaCreditCard) "💳 Credit Card: YES" else "💳 Credit Card",
                            fontSize = 10.sp,
                            fontWeight = if (rowState.isPaidViaCreditCard) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (rowState.isPaidViaCreditCard) Color.White else IndTextSecondary
                        )
                    }
                }

                // Paid / Due Toggle (Individual per row)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (rowState.isPaid) IndGreen else IndAmber,
                    modifier = Modifier.clickable {
                        rowState.isPaid = !rowState.isPaid
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = if (rowState.isPaid) "✅ Paid" else "⏳ Due",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // 🔁 Recurring Toggle
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (rowState.isRecurring) Color(0xFFE0F2FE) else IndSurface,
                    border = androidx.compose.foundation.BorderStroke(
                        0.8.dp,
                        if (rowState.isRecurring) Color(0xFF0284C7) else IndBorder
                    ),
                    modifier = Modifier.clickable {
                        rowState.isRecurring = !rowState.isRecurring
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Repeat,
                            contentDescription = "Toggle Recurring",
                            tint = if (rowState.isRecurring) Color(0xFF0284C7) else IndTextMuted,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = if (rowState.isRecurring) "🔁 Recurring: YES" else "🔁 Recurring",
                            fontSize = 10.sp,
                            fontWeight = if (rowState.isRecurring) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (rowState.isRecurring) Color(0xFF0284C7) else IndTextSecondary
                        )
                    }
                }

                // 📅 Transaction Date Chip (Prefilled with current date, customizable for any past or future date)
                val context = androidx.compose.ui.platform.LocalContext.current
                val formattedDate = remember(rowState.selectedDateMillis) {
                    java.text.SimpleDateFormat("dd MMM", java.util.Locale.getDefault()).format(java.util.Date(rowState.selectedDateMillis))
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = IndCardSecondary,
                    border = androidx.compose.foundation.BorderStroke(
                        0.8.dp,
                        IndBorderSubtle
                    ),
                    modifier = Modifier.clickable {
                        val cal = java.util.Calendar.getInstance().apply { timeInMillis = rowState.selectedDateMillis }
                        android.app.DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                val newCal = java.util.Calendar.getInstance().apply {
                                    set(y, m, d, 12, 0, 0)
                                }
                                rowState.selectedDateMillis = newCal.timeInMillis
                                rowState.day = d
                            },
                            cal.get(java.util.Calendar.YEAR),
                            cal.get(java.util.Calendar.MONTH),
                            cal.get(java.util.Calendar.DAY_OF_MONTH)
                        ).show()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CalendarMonth,
                            contentDescription = "Pick Transaction Date",
                            tint = IndNavyHeader,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "📅 Transaction Date: $formattedDate",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndNavyHeader
                        )
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                            tint = IndNavyHeader,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                // 🏷️ Subcategory / Tag Selector (Customizable per user wish with 1-tap remove)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (rowState.tag.isNotBlank()) IndBlueLight else IndSurface,
                    border = androidx.compose.foundation.BorderStroke(
                        0.6.dp,
                        if (rowState.tag.isNotBlank()) IndBlue else IndBorder
                    ),
                    modifier = Modifier.clickable { onOpenTagPicker() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val tagLabel = rowState.tag.removePrefix("#").trim()
                        val tagVisual = if (tagLabel.isNotBlank()) {
                            resolveExpenseTagVisual(tagLabel, rowState.categoryKey, customExpenseTags)
                        } else null
                        if (tagVisual != null) {
                            ExpenseTagBadge(
                                visual = tagVisual,
                                badgeSize = 18.dp,
                                monogramSize = 8.sp,
                                cornerRadius = 5.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.LocalOffer,
                                contentDescription = null,
                                tint = if (rowState.tag.isNotBlank()) IndBlue else IndTextMuted,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                        Text(
                            text = if (rowState.tag.isNotBlank()) tagLabel else "+ Tag / Subcategory",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (rowState.tag.isNotBlank()) IndBlueDark else IndTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (rowState.tag.isNotBlank()) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Clear Tag",
                                tint = IndBlueDark,
                                modifier = Modifier
                                    .size(12.dp)
                                    .clickable { rowState.tag = "" }
                            )
                        }
                    }
                }

                // Note toggle button
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (rowState.notes.isNotBlank() || rowState.showNotesField) IndCardSecondary else IndSurface,
                    border = androidx.compose.foundation.BorderStroke(0.6.dp, IndBorder),
                    modifier = Modifier.clickable {
                        rowState.showNotesField = !rowState.showNotesField
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.EditNote,
                            contentDescription = null,
                            tint = if (rowState.notes.isNotBlank()) IndNavyHeader else IndTextMuted,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (rowState.notes.isNotBlank()) "Notes: ${rowState.notes.take(12)}..." else "+ Notes (Optional)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (rowState.notes.isNotBlank()) IndNavyHeader else IndTextSecondary
                        )
                    }
                }
            }

            // Expandable Optional Note Input
            AnimatedVisibility(visible = rowState.showNotesField || rowState.notes.isNotBlank()) {
                OutlinedTextField(
                    value = rowState.notes,
                    onValueChange = { rowState.notes = it },
                    label = { Text("Notes (Optional)", fontSize = 11.sp) },
                    placeholder = { Text("Add specific notes, details, bill ref (optional)", fontSize = 11.sp, color = IndTextMuted) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.EditNote,
                            contentDescription = null,
                            tint = IndNavyHeader,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, color = IndNavyHeader, fontWeight = FontWeight.Medium),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = IndCardSecondary,
                        unfocusedContainerColor = IndCardSecondary.copy(alpha = 0.5f),
                        focusedBorderColor = IndNavyHeader,
                        unfocusedBorderColor = IndBorderSubtle
                    )
                )
            }

            // Chit & Loan EMI Specific Inline Section
            val isChitRow = rowState.categoryKey.equals("CHIT", ignoreCase = true) || rowState.tag.contains("CHIT", ignoreCase = true)
            val isLoanRow = isLoanOrEmi(rowState.categoryKey)

            if (isChitRow) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFEF3C7),
                    border = androidx.compose.foundation.BorderStroke(0.6.dp, Color(0xFFD97706)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.GroupWork, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(14.dp))
                            Text("Chit Fund Installment Settings", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = rowState.totalTenureMonthsText,
                                onValueChange = { rowState.totalTenureMonthsText = it.filter { c -> c.isDigit() } },
                                label = { Text("Total Duration (Months)", fontSize = 10.sp) },
                                placeholder = { Text("e.g. 20", fontSize = 10.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            OutlinedTextField(
                                value = rowState.currentMonthTenureText,
                                onValueChange = { rowState.currentMonthTenureText = it.filter { c -> c.isDigit() } },
                                label = { Text("Current Month", fontSize = 10.sp) },
                                placeholder = { Text("e.g. 1", fontSize = 10.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }
                    }
                }
            }

            if (isLoanRow) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFECFDF5),
                    border = androidx.compose.foundation.BorderStroke(0.6.dp, IndGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.AccountBalance, contentDescription = null, tint = IndGreenDark, modifier = Modifier.size(14.dp))
                            Text("Loan Amortization & Balance Settings", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IndGreenDark)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = rowState.principalAmountText,
                                onValueChange = { rowState.principalAmountText = it.filter { c -> c.isDigit() || c == '.' } },
                                label = { Text("Principal (₹)", fontSize = 10.sp) },
                                placeholder = { Text("500000", fontSize = 10.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1.2f),
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            OutlinedTextField(
                                value = rowState.interestRateText,
                                onValueChange = { rowState.interestRateText = it.filter { c -> c.isDigit() || c == '.' } },
                                label = { Text("Rate %", fontSize = 10.sp) },
                                placeholder = { Text("8.5", fontSize = 10.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(0.8f),
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = rowState.totalTenureMonthsText,
                                onValueChange = { rowState.totalTenureMonthsText = it.filter { c -> c.isDigit() } },
                                label = { Text("Tenure (Months)", fontSize = 10.sp) },
                                placeholder = { Text("60", fontSize = 10.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            OutlinedTextField(
                                value = rowState.currentMonthTenureText,
                                onValueChange = { rowState.currentMonthTenureText = it.filter { c -> c.isDigit() } },
                                label = { Text("Current Month", fontSize = 10.sp) },
                                placeholder = { Text("1", fontSize = 10.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Category Selection Modal Dialog showing grouped categories with quick search & custom category option
 */
@Composable
private fun CategorySelectionModalDialog(
    currentCategoryKey: String,
    currentCustomName: String,
    allCategories: List<CategoryMeta>,
    onDismiss: () -> Unit,
    onCategorySelected: (categoryKey: String, customName: String) -> Unit,
    onOpenAddCustomCategory: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf("ALL") }

    val filteredList = remember(allCategories, searchQuery, selectedGroup) {
        allCategories.filter { cat ->
            val matchesSearch = searchQuery.isBlank() ||
                    cat.displayName.contains(searchQuery, ignoreCase = true) ||
                    cat.key.contains(searchQuery, ignoreCase = true)

            val matchesGroup = selectedGroup == "ALL" || cat.group.equals(selectedGroup, ignoreCase = true)
            matchesSearch && matchesGroup
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding(),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select Category",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = IndNavyHeader
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = IndTextMuted)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Search Input
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search category...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = IndTextMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = IndCardSecondary,
                        unfocusedContainerColor = IndCardSecondary.copy(alpha = 0.5f)
                    )
                )

                // Category Group Filter Chips
                val groups = listOf(
                    "ALL" to "All",
                    "DAILY" to "Daily",
                    "ESSENTIALS" to "Essentials",
                    "BILLS" to "Bills",
                    "LIFESTYLE" to "Lifestyle",
                    "CUSTOM" to "Custom"
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(groups) { (key, label) ->
                        val isSelected = selectedGroup == key
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) IndNavyHeader else IndCardSecondary,
                            modifier = Modifier.clickable { selectedGroup = key }
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else IndTextPrimary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // Category List
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredList) { cat ->
                        val isSelected = if (cat.isCustom) {
                            currentCategoryKey == DebitCategory.CUSTOM.name && currentCustomName.equals(cat.customName, ignoreCase = true)
                        } else {
                            currentCategoryKey == cat.key
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) IndBlueLight else IndSurface,
                            border = androidx.compose.foundation.BorderStroke(
                                0.6.dp,
                                if (isSelected) IndBlue else IndBorderSubtle
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onCategorySelected(cat.key, cat.customName)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    CategoryIconBadge(
                                        categoryKey = if (cat.isCustom) cat.customName else cat.key,
                                        badgeSize = 32.dp,
                                        iconSize = 19.dp,
                                        customColor = customCategoryTint(cat.colorHex),
                                        storedIconName = cat.iconName.takeIf { it.isNotBlank() }
                                    )
                                    Text(
                                        text = cat.displayName,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) IndBlueDark else IndTextPrimary
                                    )
                                }

                                if (isSelected) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = "Selected",
                                        tint = IndBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenAddCustomCategory) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = IndBlue)
                Spacer(modifier = Modifier.width(4.dp))
                Text("+ New Custom Category", color = IndBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = IndTextMuted, fontSize = 12.sp)
            }
        }
    )
}

/**
 * Subcategory / Tag Picker Dialog with Category-Aligned Suggestions + Custom Tag entry
 */
@Composable
private fun SubcategoryTagPickerDialog(
    categoryKey: String,
    currentTag: String,
    customExpenseTags: List<CustomExpenseTag> = emptyList(),
    onDismiss: () -> Unit,
    onTagSelected: (String) -> Unit,
    onOpenAddCustomTag: () -> Unit = {}
) {
    var customTagInput by remember { mutableStateOf(currentTag.removePrefix("#")) }
    val alignedTags = remember(categoryKey, customExpenseTags) {
        val curated = curatedTagSuggestionsForCategory(categoryKey)
        val generic = getAlignedTagsForCategory(categoryKey)
        val custom = customExpenseTags.map { it.name }
        (curated + generic + custom).distinctBy { it.lowercase() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding(),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Filled.LocalOffer, contentDescription = null, tint = IndBlue, modifier = Modifier.size(20.dp))
                Text(
                    text = "Tag / Subcategory",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = IndNavyHeader
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Pick a suggestion or type a custom tag / subcategory:",
                    fontSize = 12.sp,
                    color = IndTextSecondary
                )

                // Custom Tag text input
                OutlinedTextField(
                    value = customTagInput,
                    onValueChange = { customTagInput = it },
                    placeholder = { Text("e.g. Swiggy, Birthday Treat, Snacks", fontSize = 12.sp, color = IndTextMuted) },
                    prefix = { Text("#", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = IndBlue) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = IndNavyHeader),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Text(
                    text = "Popular brands & tags:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = IndNavyHeader
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    alignedTags.forEach { tag ->
                        val isSelected = customTagInput.equals(tag, ignoreCase = true)
                        val visual = resolveExpenseTagVisual(tag, categoryKey, customExpenseTags)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) IndBlueLight else IndSurface,
                            border = androidx.compose.foundation.BorderStroke(
                                0.8.dp,
                                if (isSelected) IndBlue else IndBorderSubtle
                            ),
                            modifier = Modifier.clickable { customTagInput = tag }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (visual != null) {
                                    ExpenseTagBadge(
                                        visual = visual,
                                        badgeSize = 24.dp,
                                        monogramSize = 9.sp,
                                        cornerRadius = 7.dp
                                    )
                                }
                                Text(
                                    text = tag,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) IndBlueDark else IndTextPrimary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onOpenAddCustomTag) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp), tint = IndBlue)
                    Spacer(Modifier.width(4.dp))
                    Text("Custom tag", color = IndBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Button(
                    onClick = { onTagSelected(customTagInput.trim()) },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = IndNavyHeader)
                ) {
                    Text("Apply", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { onTagSelected("") }) {
                Text("Clear", color = IndRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    )
}
