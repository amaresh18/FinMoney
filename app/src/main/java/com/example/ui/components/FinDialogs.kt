package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DebitCategory
import com.example.data.model.IncomeCategory
import com.example.data.model.IncomeSource
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun isLoanOrEmi(category: String): Boolean {
    return category.equals(DebitCategory.LOAN.name, ignoreCase = true) ||
           category.equals(DebitCategory.EMI.name, ignoreCase = true) ||
           category.equals("LOAN", ignoreCase = true) ||
           category.equals("EMI", ignoreCase = true) ||
           category.equals("DEBT_REPAY", ignoreCase = true)
}

/**
 * Premium INDmoney Styled Add / Edit Planned Debit / EMI Dialog.
 * Uses centralized IndDialog, IndOutlinedTextField, IndCategoryDropdown components.
 */
@Composable
fun AddEditDebitDialog(
    initialTitle: String = "",
    initialAmount: String = "",
    initialCategory: String = DebitCategory.GROCERIES.name,
    initialDueDate: String = "28th of month",
    initialNotes: String = "",
    initialPrincipalAmount: String = "",
    initialInterestRate: String = "",
    initialTotalTenureMonths: String = "",
    initialCurrentMonthTenure: String = "",
    initialIsPaidViaCreditCard: Boolean = false,
    initialIsRecurring: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        amount: Double,
        category: String,
        dueDate: String,
        notes: String,
        principalAmount: Double,
        interestRate: Double,
        totalTenureMonths: Int,
        currentMonthTenure: Int,
        isPaidViaCreditCard: Boolean,
        isRecurring: Boolean
    ) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var amountStr by remember { mutableStateOf(initialAmount) }
    var category by remember { mutableStateOf(initialCategory) }
    var dueDate by remember { mutableStateOf(initialDueDate) }
    var notes by remember { mutableStateOf(initialNotes) }
    var isPaidViaCreditCard by remember { mutableStateOf(initialIsPaidViaCreditCard) }
    var isRecurring by remember { mutableStateOf(initialIsRecurring) }
    
    var principalAmountStr by remember { mutableStateOf(initialPrincipalAmount) }
    var interestRateStr by remember { mutableStateOf(initialInterestRate) }
    var totalTenureMonthsStr by remember { mutableStateOf(initialTotalTenureMonths) }
    var currentMonthTenureStr by remember { mutableStateOf(initialCurrentMonthTenure) }

    val debitCategories = remember { DebitCategory.entries.map { it.name } }

    val isFormValid = title.isNotBlank() && (amountStr.toDoubleOrNull() ?: 0.0) > 0.0

    val isChit = category.equals("CHIT", ignoreCase = true) || title.contains("CHIT", ignoreCase = true)
    val isLoan = isLoanOrEmi(category)

    IndDialog(
        onDismiss = onDismiss,
        title = if (isChit) "Add Chit Fund Payment" else if (isLoan) "Add Loan / EMI Payment" else "Add Planned Debit / Bill",
        subtitle = if (isChit) "Track chit fund installment and month progression" else if (isLoan) "Calculate EMI amortization and outstanding balance" else "Schedule monthly debits or bills",
        icon = if (isChit) Icons.Filled.GroupWork else if (isLoan) Icons.Filled.Calculate else Icons.Filled.CreditCard,
        iconTint = IndRed,
        headerGradient = listOf(Color(0xFFFEE2E2), AppTheme.colors.surface),
        testTag = "add_debit_dialog",
        confirmButtonText = "Save Entry",
        confirmButtonTag = "submit_debit_button",
        isConfirmEnabled = isFormValid,
        onConfirm = {
            val amount = amountStr.toDoubleOrNull() ?: 0.0
            val principalAmount = principalAmountStr.toDoubleOrNull() ?: 0.0
            val interestRate = interestRateStr.toDoubleOrNull() ?: 0.0
            val totalTenure = totalTenureMonthsStr.toIntOrNull() ?: 0
            val currentTenure = currentMonthTenureStr.toIntOrNull() ?: 1
            if (isFormValid) {
                onSave(
                    title,
                    amount,
                    category,
                    dueDate,
                    notes,
                    principalAmount,
                    interestRate,
                    totalTenure,
                    currentTenure,
                    isPaidViaCreditCard,
                    isRecurring
                )
            }
        }
    ) {
        IndOutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = "Title / Payee",
            placeholder = if (isChit) "e.g. Monthly Chit Fund" else if (isLoan) "e.g. Car Loan EMI" else "e.g. Grocery Store, Rent",
            leadingIcon = Icons.Filled.Storefront,
            testTag = "debit_title_input"
        )

        IndOutlinedTextField(
            value = amountStr,
            onValueChange = { amountStr = it },
            label = "Monthly Amount (₹)",
            placeholder = "0.00",
            leadingIcon = Icons.Filled.CurrencyRupee,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            testTag = "debit_amount_input"
        )

        IndCategoryDropdown(
            selectedCategory = category,
            onCategorySelected = { category = it },
            categories = debitCategories,
            label = "Category (Select from list)",
            testTag = "debit_category_input"
        )

        IndOutlinedTextField(
            value = dueDate,
            onValueChange = { dueDate = it },
            label = "Due Date",
            placeholder = "e.g. 5th of month",
            leadingIcon = Icons.Filled.Event,
            testTag = "debit_duedate_input"
        )

        IndOutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = "Notes / Description (Optional)",
            placeholder = "e.g. Reference No, Account details, Bill notes",
            leadingIcon = Icons.Outlined.EditNote,
            testTag = "debit_notes_input"
        )

        // Chit Fund Specific Tenure & Month Tracking
        if (isChit) {
            IndSectionCard(
                title = "Chit Fund Installment Tracker",
                icon = Icons.Filled.GroupWork
            ) {
                IndOutlinedTextField(
                    value = totalTenureMonthsStr,
                    onValueChange = { totalTenureMonthsStr = it },
                    label = "Total Chit Duration (Months)",
                    placeholder = "e.g. 20 or 25",
                    leadingIcon = Icons.Filled.Schedule,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                IndOutlinedTextField(
                    value = currentMonthTenureStr,
                    onValueChange = { currentMonthTenureStr = it },
                    label = "Current Installment Month",
                    placeholder = "e.g. 3 (Month 3 of 20)",
                    leadingIcon = Icons.Filled.Countertops,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }

        // Loan / EMI Specific Calculator Inputs
        if (isLoan) {
            IndSectionCard(
                title = "Loan Amortization & Outstanding Balance",
                icon = Icons.Filled.Calculate
            ) {
                IndOutlinedTextField(
                    value = principalAmountStr,
                    onValueChange = { principalAmountStr = it },
                    label = "Original Principal Loan Amount (₹)",
                    placeholder = "e.g. 500000",
                    leadingIcon = Icons.Filled.AccountBalance,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                IndOutlinedTextField(
                    value = interestRateStr,
                    onValueChange = { interestRateStr = it },
                    label = "Annual Interest Rate (%)",
                    placeholder = "e.g. 8.5",
                    leadingIcon = Icons.Filled.Percent,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                IndOutlinedTextField(
                    value = totalTenureMonthsStr,
                    onValueChange = { totalTenureMonthsStr = it },
                    label = "Total Loan Tenure (Months)",
                    placeholder = "e.g. 60",
                    leadingIcon = Icons.Filled.Schedule,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                IndOutlinedTextField(
                    value = currentMonthTenureStr,
                    onValueChange = { currentMonthTenureStr = it },
                    label = "Current Paid Installment Month",
                    placeholder = "e.g. 12 (Month 12 of 60)",
                    leadingIcon = Icons.Filled.EventAvailable,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }

        // Recurring Expense Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (isRecurring) Color(0xFFE0F2FE) else AppTheme.colors.surface)
                .border(1.dp, if (isRecurring) Color(0xFF0284C7) else IndBorder, RoundedCornerShape(12.dp))
                .clickable { isRecurring = !isRecurring }
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Repeat,
                    contentDescription = null,
                    tint = if (isRecurring) Color(0xFF0284C7) else IndTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = "Recurring Monthly Expense",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (isRecurring) Color(0xFF0284C7) else IndTextPrimary
                    )
                    Text(
                        text = "Auto-advance month counter & prefill in next month",
                        fontSize = 10.sp,
                        color = IndTextSecondary
                    )
                }
            }
            Switch(
                checked = isRecurring,
                onCheckedChange = { isRecurring = it },
                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF0284C7))
            )
        }

        // Credit Card Paid Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (isPaidViaCreditCard) Color(0xFFEDE9FE) else AppTheme.colors.surface)
                .border(1.dp, if (isPaidViaCreditCard) Color(0xFF7C3AED) else IndBorder, RoundedCornerShape(12.dp))
                .clickable { isPaidViaCreditCard = !isPaidViaCreditCard }
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CreditCard,
                    contentDescription = null,
                    tint = if (isPaidViaCreditCard) Color(0xFF7C3AED) else IndTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = "Paid via Credit Card",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (isPaidViaCreditCard) Color(0xFF7C3AED) else IndTextPrimary
                    )
                    Text(
                        text = "Tag this expense for credit card filter",
                        fontSize = 10.sp,
                        color = IndTextSecondary
                    )
                }
            }
            Switch(
                checked = isPaidViaCreditCard,
                onCheckedChange = { isPaidViaCreditCard = it },
                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF7C3AED))
            )
        }
    }
}

@Composable
fun IndGoogleCalendarDatePicker(
    currentMonth: Int,
    currentYear: Int,
    currentSelectedDay: Int?,
    onDismiss: () -> Unit,
    onSelectDate: (month: Int, year: Int, day: Int?) -> Unit
) {
    var selectedYear by remember { mutableStateOf(currentYear) }
    var selectedMonth by remember { mutableStateOf(currentMonth) } // 1..12
    var selectedDay by remember { mutableStateOf(currentSelectedDay) } // null or 1..31
    var viewMode by remember { mutableStateOf(if (currentSelectedDay != null) "DAY" else "MONTH") }

    val months = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    val daysInMonth = remember(selectedMonth, selectedYear) {
        val cal = java.util.Calendar.getInstance()
        cal.set(selectedYear, selectedMonth - 1, 1)
        cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
    }

    val startDayOfWeek = remember(selectedMonth, selectedYear) {
        val cal = java.util.Calendar.getInstance()
        cal.set(selectedYear, selectedMonth - 1, 1)
        cal.get(java.util.Calendar.DAY_OF_WEEK) - 1
    }

    IndDialog(
        onDismiss = onDismiss,
        title = "Google Calendar Date Filter",
        subtitle = "Select month or exact calendar date",
        icon = Icons.Filled.CalendarMonth,
        iconTint = IndBlue,
        headerGradient = listOf(IndBlueLight, AppTheme.colors.surface),
        testTag = "google_calendar_picker_dialog",
        confirmButtonText = "Apply Filter",
        confirmButtonTag = "apply_calendar_filter_button",
        onConfirm = {
            onSelectDate(selectedMonth, selectedYear, selectedDay)
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = viewMode == "MONTH",
                onClick = { viewMode = "MONTH"; selectedDay = null },
                label = { Text("📅 Month View", fontSize = 11.sp) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = viewMode == "DAY",
                onClick = { viewMode = "DAY" },
                label = { Text("🗓️ Specific Date", fontSize = 11.sp) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(IndBlueLight)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (selectedMonth == 1) {
                    selectedMonth = 12
                    selectedYear--
                } else {
                    selectedMonth--
                }
                selectedDay = null
            }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Prev", tint = IndBlue)
            }
            Text(
                text = "${months[selectedMonth - 1]} $selectedYear" + if (selectedDay != null) " (Day $selectedDay)" else "",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = IndNavyHeader
            )
            IconButton(onClick = {
                if (selectedMonth == 12) {
                    selectedMonth = 1
                    selectedYear++
                } else {
                    selectedMonth++
                }
                selectedDay = null
            }) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Next", tint = IndBlue)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (viewMode == "MONTH") {
            val monthRows = months.chunked(3)
            monthRows.forEachIndexed { rowIndex, rowMonths ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowMonths.forEachIndexed { colIndex, monthName ->
                        val monthIndex = rowIndex * 3 + colIndex + 1
                        val isSelected = monthIndex == selectedMonth
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) IndBlue else IndSurface,
                            border = BorderStroke(1.dp, if (isSelected) IndBlue else IndBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clickable { selectedMonth = monthIndex; selectedDay = null }
                                .testTag("month_grid_$monthIndex")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = monthName.take(3),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                    color = if (isSelected) Color.White else IndTextPrimary
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa").forEach { dayLabel ->
                    Text(
                        text = dayLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = IndTextSecondary,
                        modifier = Modifier.width(32.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val totalCells = startDayOfWeek + daysInMonth
            val weeks = if (totalCells % 7 == 0) totalCells / 7 else (totalCells / 7) + 1

            for (week in 0 until weeks) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    for (dayOfWeek in 0 until 7) {
                        val cellIndex = week * 7 + dayOfWeek
                        val dayNum = cellIndex - startDayOfWeek + 1
                        val isValidDay = dayNum in 1..daysInMonth

                        if (isValidDay) {
                            val isSelected = selectedDay == dayNum
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) IndBlue else Color.Transparent,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable { selectedDay = dayNum }
                                    .testTag("day_cell_$dayNum")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "$dayNum",
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp,
                                        color = if (isSelected) Color.White else IndTextPrimary
                                    )
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.size(36.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

/**
 * Comprehensive Income & Credit Manager Dialog.
 * Allows viewing all added credits, editing or deleting individual income/credit entries,
 * adding new credits, and modifying primary base salary.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageIncomeCreditDialog(
    incomeSources: List<IncomeSource> = emptyList(),
    baseSalary: Double = 0.0,
    startInEditCredit: IncomeSource? = null,
    openOnAddCreditTab: Boolean = false,
    onDismiss: () -> Unit,
    onSaveNewCredit: (title: String, amount: Double, category: String, dateString: String, isRecurring: Boolean) -> Unit,
    onUpdateCredit: (id: String, title: String, amount: Double, category: String, dateString: String, isRecurring: Boolean) -> Unit,
    onDeleteCredit: (id: String) -> Unit,
    onUpdateBaseSalary: (amount: Double) -> Unit
) {
    val defaultDateStr = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    var selectedTabIndex by remember { mutableIntStateOf(if (openOnAddCreditTab) 1 else 0) }
    var editingSourceId by remember { mutableStateOf<String?>(null) }
    var pendingDeleteCreditId by remember { mutableStateOf<String?>(null) }
    var pendingDeleteCreditTitle by remember { mutableStateOf("") }

    // Form states
    var title by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(IncomeCategory.FREELANCE.name) }
    var dateString by remember { mutableStateOf(defaultDateStr) }
    var isRecurringCredit by remember { mutableStateOf(false) }
    var salaryStr by remember { mutableStateOf(if (baseSalary > 0) baseSalary.toInt().toString() else "") }

    LaunchedEffect(startInEditCredit?.id) {
        val src = startInEditCredit
        if (src != null) {
            editingSourceId = src.id
            title = src.title.ifBlank { src.name }
            amountStr = if (src.amount > 0) {
                if (src.amount % 1.0 == 0.0) src.amount.toInt().toString() else src.amount.toString()
            } else ""
            category = src.category
            isRecurringCredit = src.isRecurring
            val ts = src.receivedDateTimestamp ?: 0L
            dateString = if (ts > 0) {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(ts))
            } else defaultDateStr
        }
    }

    val creditCategories = remember { IncomeCategory.entries.map { it.name } }

    val isFormValid = title.isNotBlank() && (amountStr.toDoubleOrNull() ?: 0.0) > 0.0

    IndDialog(
        onDismiss = onDismiss,
        title = if (editingSourceId != null) "Edit credit / income" else "Manage Income & Credits",
        subtitle = "View, edit base salary, or modify added credits/income",
        icon = Icons.Filled.Payments,
        iconTint = IndGreen,
        headerGradient = listOf(IndGreenLight, AppTheme.colors.surface),
        testTag = "manage_income_dialog",
        confirmButtonText = when {
            editingSourceId != null -> "Update Credit"
            selectedTabIndex == 1 -> "Save Credit"
            selectedTabIndex == 2 -> "Update Base Salary"
            else -> "Close"
        },
        confirmButtonTag = "submit_manage_income_button",
        isConfirmEnabled = when {
            editingSourceId != null -> isFormValid
            selectedTabIndex == 1 -> isFormValid
            selectedTabIndex == 2 -> (salaryStr.toDoubleOrNull() ?: 0.0) > 0.0
            else -> true
        },
        onConfirm = {
            when {
                editingSourceId != null -> {
                    val amt = amountStr.toDoubleOrNull() ?: 0.0
                    if (isFormValid) {
                        onUpdateCredit(editingSourceId!!, title, amt, category, dateString, isRecurringCredit)
                        editingSourceId = null
                        title = ""
                        amountStr = ""
                        isRecurringCredit = false
                    }
                }
                selectedTabIndex == 1 -> {
                    val amt = amountStr.toDoubleOrNull() ?: 0.0
                    if (isFormValid) {
                        onSaveNewCredit(title, amt, category, dateString, isRecurringCredit)
                        title = ""
                        amountStr = ""
                        isRecurringCredit = false
                    }
                }
                selectedTabIndex == 2 -> {
                    val sal = salaryStr.toDoubleOrNull() ?: 0.0
                    if (sal > 0) {
                        onUpdateBaseSalary(sal)
                    }
                }
                else -> onDismiss()
            }
        }
    ) {
        if (editingSourceId == null) {
            // Tab Selector Row
            SecondaryTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color.Transparent,
                contentColor = IndBlue,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("All Inflows (${incomeSources.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("+ Add Credit", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    text = { Text("Base Salary", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
            }
        }

        when {
            // Mode A: Currently Editing a Specific Income Source
            editingSourceId != null -> {
                Text(
                    text = "Modifying Selected Income Source",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = IndTextSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                IndOutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = "Title / Payee Name",
                    placeholder = "e.g. Freelance Client, Bonus",
                    leadingIcon = Icons.Filled.Payments,
                    testTag = "edit_credit_title_input"
                )

                IndOutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = "Amount (₹)",
                    placeholder = "0.00",
                    leadingIcon = Icons.Filled.CurrencyRupee,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    testTag = "edit_credit_amount_input"
                )

                IndOutlinedTextField(
                    value = dateString,
                    onValueChange = { dateString = it },
                    label = "Date (YYYY-MM-DD)",
                    placeholder = "e.g. 2026-10-12",
                    leadingIcon = Icons.Filled.CalendarToday,
                    testTag = "edit_credit_date_input"
                )

                IndCategoryDropdown(
                    selectedCategory = category,
                    onCategorySelected = { category = it },
                    categories = creditCategories,
                    label = "Category",
                    testTag = "edit_credit_category_input"
                )

                RecurringMonthlyIncomeToggle(
                    isRecurring = isRecurringCredit,
                    onRecurringChange = { isRecurringCredit = it }
                )

                TextButton(
                    onClick = {
                        editingSourceId = null
                        title = ""
                        amountStr = ""
                        isRecurringCredit = false
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cancel Edit", color = IndRed)
                }
            }

            // Mode B: Tab 0 - List of All Inflows with Edit and Delete Buttons
            selectedTabIndex == 0 -> {
                if (incomeSources.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No income sources found for this month.", fontSize = 13.sp, color = IndTextSecondary)
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        incomeSources.forEach { src ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AppTheme.colors.surface,
                                border = BorderStroke(1.dp, IndBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = IndGreenLight,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Filled.Payments,
                                                    contentDescription = null,
                                                    tint = IndGreenDark,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        Column {
                                            Text(
                                                text = src.title.ifBlank { src.name },
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = IndNavyHeader
                                            )
                                            Text(
                                                text = buildString {
                                                    append(src.category)
                                                    append(" • ₹${"%,.0f".format(src.amount)}")
                                                    if (src.isRecurring) append(" • ↻ Recurring")
                                                },
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = IndGreenDark
                                            )
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(
                                            onClick = {
                                                editingSourceId = src.id
                                                title = src.title.ifBlank { src.name }
                                                amountStr = src.amount.toString()
                                                category = src.category
                                                isRecurringCredit = src.isRecurring
                                                val ts = src.receivedDateTimestamp ?: 0L
                                                dateString = if (ts > 0) {
                                                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(ts))
                                                } else defaultDateStr
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Edit,
                                                contentDescription = "Edit Credit",
                                                tint = IndBlue,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                pendingDeleteCreditId = src.id
                                                pendingDeleteCreditTitle = src.title.ifBlank { src.name }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = "Delete Credit",
                                                tint = IndRed,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Mode C: Tab 1 - Add New Credit Form
            selectedTabIndex == 1 -> {
                IndOutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = "Source Title / Payee",
                    placeholder = "e.g. Client Payment, Bonus, Rental Income",
                    leadingIcon = Icons.Filled.Payments,
                    testTag = "credit_title_input"
                )

                IndOutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = "Amount (₹)",
                    placeholder = "0.00",
                    leadingIcon = Icons.Filled.CurrencyRupee,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    testTag = "credit_amount_input"
                )

                IndOutlinedTextField(
                    value = dateString,
                    onValueChange = { dateString = it },
                    label = "Date (YYYY-MM-DD)",
                    placeholder = "e.g. 2026-10-12",
                    leadingIcon = Icons.Filled.CalendarToday,
                    testTag = "credit_date_input"
                )

                IndCategoryDropdown(
                    selectedCategory = category,
                    onCategorySelected = { category = it },
                    categories = creditCategories,
                    label = "Category",
                    testTag = "credit_category_input"
                )

                RecurringMonthlyIncomeToggle(
                    isRecurring = isRecurringCredit,
                    onRecurringChange = { isRecurringCredit = it }
                )
            }

            // Mode D: Tab 2 - Base Salary Update
            selectedTabIndex == 2 -> {
                Text(
                    text = "Update Primary Monthly Salary",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = IndTextSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                IndOutlinedTextField(
                    value = salaryStr,
                    onValueChange = { salaryStr = it },
                    label = "Primary Base Salary (₹)",
                    placeholder = "e.g. 50000",
                    leadingIcon = Icons.Filled.AccountBalance,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    testTag = "base_salary_input"
                )
            }
        }
    }

    pendingDeleteCreditId?.let { creditId ->
        ConfirmDeleteDialog(
            title = "Delete credit?",
            message = "Remove \"${pendingDeleteCreditTitle.ifBlank { "this credit" }}\" from this month? This cannot be undone.",
            onDismiss = {
                pendingDeleteCreditId = null
                pendingDeleteCreditTitle = ""
            },
            onConfirm = {
                onDeleteCredit(creditId)
                pendingDeleteCreditId = null
                pendingDeleteCreditTitle = ""
                if (editingSourceId == creditId) {
                    editingSourceId = null
                    title = ""
                    amountStr = ""
                    isRecurringCredit = false
                }
            }
        )
    }
}

/**
 * Backwards compatible wrapper for AddCreditDialog.
 */
@Composable
fun AddCreditDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, amount: Double, category: String, dateString: String, isRecurring: Boolean) -> Unit
) {
    ManageIncomeCreditDialog(
        incomeSources = emptyList(),
        baseSalary = 0.0,
        onDismiss = onDismiss,
        onSaveNewCredit = onSave,
        onUpdateCredit = { _, title, amount, category, dateString, isRecurring ->
            onSave(title, amount, category, dateString, isRecurring)
        },
        onDeleteCredit = {},
        onUpdateBaseSalary = {}
    )
}

@Composable
private fun RecurringMonthlyIncomeToggle(
    isRecurring: Boolean,
    onRecurringChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isRecurring) IndGreenLight else AppTheme.colors.surface)
            .border(1.dp, if (isRecurring) IndGreen else IndBorder, RoundedCornerShape(12.dp))
            .clickable { onRecurringChange(!isRecurring) }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Repeat,
                contentDescription = null,
                tint = if (isRecurring) IndGreenDark else IndTextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = "Recurring monthly income",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (isRecurring) IndGreenDark else IndTextPrimary
                )
                Text(
                    text = "Auto-copy to future months via Recurring on dashboard",
                    fontSize = 10.sp,
                    color = IndTextSecondary
                )
            }
        }
        Switch(
            checked = isRecurring,
            onCheckedChange = onRecurringChange,
            colors = SwitchDefaults.colors(checkedThumbColor = IndGreenDark, checkedTrackColor = IndGreenLight)
        )
    }
}

data class CustomCategoryIconChoice(val iconName: String, val label: String, val icon: ImageVector)

val customCategoryIconChoices: List<CustomCategoryIconChoice> = listOf(
    CustomCategoryIconChoice("Category", "General", Icons.Filled.Category),
    CustomCategoryIconChoice("Restaurant", "Food", Icons.Filled.Restaurant),
    CustomCategoryIconChoice("ShoppingCart", "Groceries", Icons.Filled.ShoppingCart),
    CustomCategoryIconChoice("ShoppingBag", "Shopping", Icons.Filled.ShoppingBag),
    CustomCategoryIconChoice("LocalGasStation", "Fuel", Icons.Filled.LocalGasStation),
    CustomCategoryIconChoice("DirectionsCar", "Travel", Icons.Filled.DirectionsCar),
    CustomCategoryIconChoice("Flight", "Trips", Icons.Filled.Flight),
    CustomCategoryIconChoice("Home", "Home", Icons.Filled.Home),
    CustomCategoryIconChoice("Bolt", "Bills", Icons.Filled.Bolt),
    CustomCategoryIconChoice("Wifi", "Internet", Icons.Filled.Wifi),
    CustomCategoryIconChoice("PhoneAndroid", "Mobile", Icons.Filled.PhoneAndroid),
    CustomCategoryIconChoice("Tv", "OTT", Icons.Filled.Tv),
    CustomCategoryIconChoice("LocalHospital", "Health", Icons.Filled.LocalHospital),
    CustomCategoryIconChoice("School", "Education", Icons.Filled.School),
    CustomCategoryIconChoice("Savings", "Savings", Icons.Filled.Savings),
    CustomCategoryIconChoice("Work", "Work", Icons.Filled.Work),
    CustomCategoryIconChoice("Pets", "Pets", Icons.Filled.Pets),
    CustomCategoryIconChoice("FitnessCenter", "Fitness", Icons.Filled.FitnessCenter),
    CustomCategoryIconChoice("ChildCare", "Kids", Icons.Filled.ChildCare),
    CustomCategoryIconChoice("Build", "Repairs", Icons.Filled.Build),
    CustomCategoryIconChoice("Coffee", "Cafe", Icons.Filled.Coffee),
    CustomCategoryIconChoice("SportsEsports", "Gaming", Icons.Filled.SportsEsports),
    CustomCategoryIconChoice("MusicNote", "Music", Icons.Filled.MusicNote),
    CustomCategoryIconChoice("CardGiftcard", "Gifts", Icons.Filled.CardGiftcard),
    CustomCategoryIconChoice("VolunteerActivism", "Charity", Icons.Filled.VolunteerActivism),
)

@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    confirmLabel: String = "Delete",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = IndRed)
        },
        title = {
            Text(title, fontWeight = FontWeight.Bold, color = IndNavyHeader)
        },
        text = {
            Text(message, color = IndTextSecondary, fontSize = 14.sp)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = IndRed, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = IndTextMuted)
            }
        }
    )
}

private val customCategoryColorChoices = listOf(
    "#00B386", "#0066FF", "#DB2777", "#EA580C", "#6D28D9", "#0284C7", "#16A34A", "#DC2626"
)

@Composable
fun AddCustomCategoryDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, iconName: String, colorHex: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf(customCategoryIconChoices.first().iconName) }
    var selectedColor by remember { mutableStateOf(customCategoryColorChoices.first()) }
    var nameError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("New custom category", fontWeight = FontWeight.Bold, color = IndNavyHeader)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = false
                    },
                    label = { Text("Category name") },
                    singleLine = true,
                    isError = nameError,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                Text("Icon", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = IndTextMuted)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.heightIn(max = 160.dp)
                ) {
                    items(customCategoryIconChoices) { choice ->
                        val picked = selectedIcon == choice.iconName
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (picked) IndBlueLight else IndCardSecondary,
                            border = BorderStroke(1.dp, if (picked) IndBlue else IndBorderSubtle),
                            modifier = Modifier
                                .size(44.dp)
                                .clickable { selectedIcon = choice.iconName }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(choice.icon, choice.label, tint = if (picked) IndBlue else IndTextMuted, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
                Text("Color", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = IndTextMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    customCategoryColorChoices.forEach { hex ->
                        val picked = selectedColor == hex
                        val c = try {
                            Color(android.graphics.Color.parseColor(hex))
                        } catch (_: Throwable) {
                            IndBlue
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    width = if (picked) 2.dp else 1.dp,
                                    color = if (picked) IndNavyHeader else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = hex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.trim().isBlank()) {
                        nameError = true
                    } else {
                        onSave(name.trim(), selectedIcon, selectedColor)
                    }
                }
            ) {
                Text("Save", fontWeight = FontWeight.Bold, color = IndBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = IndTextMuted)
            }
        }
    )
}
