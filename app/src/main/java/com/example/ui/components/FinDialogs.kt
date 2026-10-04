@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.model.*
import com.example.ui.FinMoneyViewModel
import com.example.ui.theme.*
import com.example.util.InterestCalculator
import com.example.util.LoanInterestBreakdown
import com.example.util.ProofFile
import com.example.util.ProofStorageHelper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalaryInputDialog(
    currentSalary: Double,
    currentAdditional: Double,
    currentNotes: String,
    currentIncomeSources: List<IncomeSource> = emptyList(),
    monthName: String,
    year: Int,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onSave: (salary: Double, additional: Double, notes: String, sources: List<IncomeSource>) -> Unit
) {
    var salaryText by remember { mutableStateOf(if (currentSalary > 0) currentSalary.toInt().toString() else "") }
    var notesText by remember { mutableStateOf(currentNotes) }
    var incomeStreams by remember { mutableStateOf(currentIncomeSources.toMutableList()) }
    var errorText by remember { mutableStateOf<String?>(null) }

    // State for adding a new stream
    var newStreamName by remember { mutableStateOf("") }
    var newStreamAmount by remember { mutableStateOf("") }
    var newStreamCategory by remember { mutableStateOf(IncomeCategory.RENT.name) }
    var showAddStreamFields by remember { mutableStateOf(false) }

    val baseSalary = salaryText.toDoubleOrNull() ?: 0.0
    val streamsTotal = incomeStreams.sumOf { it.amount }
    val totalIncome = baseSalary + streamsTotal

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = IndSurface)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Monthly Income & Credits",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = IndTextPrimary
                            )
                            Text(
                                text = "Budget for $monthName $year",
                                style = MaterialTheme.typography.bodySmall,
                                color = IndTextSecondary
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = IndTextSecondary)
                        }
                    }
                }

                // Total calculated income preview pill
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = IndGreenLight,
                        border = androidx.compose.foundation.BorderStroke(1.dp, IndGreen.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("TOTAL CALCULATED INCOME", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = IndGreenDark)
                                Text("Salary + Additional Credits", style = MaterialTheme.typography.bodySmall, color = IndTextSecondary, fontSize = 11.sp)
                            }
                            Text(
                                text = "$currencySymbol${"%,.0f".format(totalIncome)}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = IndGreenDark
                            )
                        }
                    }
                }

                // Primary Monthly Salary input
                item {
                    OutlinedTextField(
                        value = salaryText,
                        onValueChange = {
                            salaryText = it
                            errorText = null
                        },
                        label = { Text("Primary Monthly Salary ($currencySymbol)") },
                        placeholder = { Text("e.g. 85000") },
                        leadingIcon = { Text(currencySymbol, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp), color = IndGreenDark) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("salary_input_field"),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Other Income & Credit Streams Section
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Other Income & Credits", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = IndTextPrimary)
                            Text("Rental, interest, freelance, etc.", style = MaterialTheme.typography.bodySmall, color = IndTextSecondary)
                        }
                        if (!showAddStreamFields) {
                            TextButton(onClick = { showAddStreamFields = true }) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = IndBlue)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Credit", style = MaterialTheme.typography.labelMedium, color = IndBlue)
                            }
                        }
                    }
                }

                // Existing custom income streams list
                items(incomeStreams) { stream ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = IndCardSecondary,
                        border = androidx.compose.foundation.BorderStroke(1.dp, IndBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier.size(28.dp).clip(CircleShape).background(IndGreenLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when (stream.category) {
                                            IncomeCategory.RENT.name -> Icons.Filled.Home
                                            IncomeCategory.INTEREST.name -> Icons.Filled.Savings
                                            IncomeCategory.FREELANCE.name -> Icons.Filled.Work
                                            else -> Icons.Filled.Payments
                                        },
                                        contentDescription = null,
                                        tint = IndGreenDark,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Column {
                                    Text(stream.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = IndTextPrimary)
                                    Text(
                                        when (stream.category) {
                                            IncomeCategory.RENT.name -> "Rental Income"
                                            IncomeCategory.INTEREST.name -> "Interest / Dividend"
                                            IncomeCategory.FREELANCE.name -> "Business / Side"
                                            else -> "Credit Stream"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = IndTextSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("$currencySymbol${"%,.0f".format(stream.amount)}", fontWeight = FontWeight.Bold, color = IndGreenDark, style = MaterialTheme.typography.titleSmall)
                                IconButton(
                                    onClick = {
                                        incomeStreams = incomeStreams.filter { it != stream }.toMutableList()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Remove", tint = IndRed, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                // Add New Stream Form
                if (showAddStreamFields) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = IndCardHighlight,
                            border = androidx.compose.foundation.BorderStroke(1.dp, IndBlue.copy(alpha = 0.3f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("Add Credit / Other Income Stream", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = IndBlueDark)

                                // Category quick picker
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(
                                        IncomeCategory.RENT.name to "Rent",
                                        IncomeCategory.INTEREST.name to "Interest",
                                        IncomeCategory.FREELANCE.name to "Freelance",
                                        IncomeCategory.OTHER.name to "Custom"
                                    ).forEach { (catKey, label) ->
                                        val isSel = newStreamCategory == catKey
                                        FilterChip(
                                            selected = isSel,
                                            onClick = {
                                                newStreamCategory = catKey
                                                if (newStreamName.isBlank()) {
                                                    newStreamName = when (catKey) {
                                                        IncomeCategory.RENT.name -> "Flat Rental"
                                                        IncomeCategory.INTEREST.name -> "Savings Interest"
                                                        IncomeCategory.FREELANCE.name -> "Consulting Fee"
                                                        else -> ""
                                                    }
                                                }
                                            },
                                            label = { Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = IndBlue,
                                                selectedLabelColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = newStreamName,
                                    onValueChange = { newStreamName = it },
                                    label = { Text("Income Source Name") },
                                    placeholder = { Text("e.g. 2BHK Rent, Fixed Deposit Interest") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                )

                                OutlinedTextField(
                                    value = newStreamAmount,
                                    onValueChange = { newStreamAmount = it },
                                    label = { Text("Amount ($currencySymbol)") },
                                    placeholder = { Text("e.g. 15000") },
                                    leadingIcon = { Text(currencySymbol, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp), color = IndGreenDark) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = { showAddStreamFields = false }) {
                                        Text("Cancel", color = IndTextSecondary)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            val amt = newStreamAmount.toDoubleOrNull()
                                            if (newStreamName.isNotBlank() && amt != null && amt > 0) {
                                                incomeStreams = (incomeStreams + IncomeSource(newStreamName.trim(), amt, newStreamCategory)).toMutableList()
                                                newStreamName = ""
                                                newStreamAmount = ""
                                                showAddStreamFields = false
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = IndBlue, contentColor = Color.White)
                                    ) {
                                        Text("Add Stream", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        label = { Text("Notes / Remarks (Optional)") },
                        placeholder = { Text("e.g. Primary salary credited on 1st") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                if (errorText != null) {
                    item {
                        Text(text = errorText!!, color = IndRed, style = MaterialTheme.typography.bodySmall)
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cancel", color = IndTextSecondary)
                        }
                        Button(
                            onClick = {
                                val sal = salaryText.toDoubleOrNull() ?: 0.0
                                if (sal <= 0 && incomeStreams.isEmpty()) {
                                    errorText = "Please enter salary or add at least one income source"
                                } else {
                                    onSave(sal, streamsTotal, notesText, incomeStreams)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_salary_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IndGreen, contentColor = Color.White)
                        ) {
                            Text("Save & Calculate", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditDebitDialog(
    debitToEdit: DebitItem? = null,
    customCategories: List<CustomCategory>,
    monthName: String,
    year: Int,
    currencySymbol: String,
    initialCategory: String = DebitCategory.EMI.name,
    onDismiss: () -> Unit,
    onAddCustomCategoryClick: () -> Unit,
    onSave: (
        debitId: Long,
        category: String,
        customCatName: String,
        title: String,
        amount: Double,
        dueDateDay: Int,
        isRecurring: Boolean,
        interestRate: Double,
        totalTenure: Int,
        currentTenure: Int,
        notes: String
    ) -> Unit
) {
    val isEditMode = debitToEdit != null

    var selectedCategory by remember { mutableStateOf(debitToEdit?.category ?: initialCategory) }
    var selectedCustomCategory by remember { mutableStateOf(debitToEdit?.customCategoryName ?: customCategories.firstOrNull()?.name ?: "") }
    var title by remember { mutableStateOf(debitToEdit?.title ?: "") }
    var amountText by remember { mutableStateOf(if (debitToEdit != null) debitToEdit.amount.toInt().toString() else "") }
    var dueDateDayText by remember { mutableStateOf(if (debitToEdit != null) debitToEdit.dueDateDay.toString() else "5") }
    var isRecurring by remember { mutableStateOf(debitToEdit?.isRecurring ?: true) }
    var interestRateText by remember { mutableStateOf(if (debitToEdit != null && debitToEdit.interestRate > 0) debitToEdit.interestRate.toString() else "") }
    var totalTenureText by remember { mutableStateOf(if (debitToEdit != null && debitToEdit.totalTenureMonths > 0) debitToEdit.totalTenureMonths.toString() else "") }
    var currentTenureText by remember { mutableStateOf(if (debitToEdit != null && debitToEdit.currentMonthTenure > 0) debitToEdit.currentMonthTenure.toString() else "") }
    var notes by remember { mutableStateOf(debitToEdit?.notes ?: "") }
    var errorText by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = IndSurface)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (isEditMode) "Edit Monthly Debit" else "Add Debit Commitment",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = IndTextPrimary
                            )
                            Text(
                                text = "Monthly Outflow for $monthName $year",
                                style = MaterialTheme.typography.bodySmall,
                                color = IndTextSecondary
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = IndTextSecondary)
                        }
                    }
                }

                // Category Selector Chips
                item {
                    Text(
                        text = "Category Type",
                        style = MaterialTheme.typography.labelMedium,
                        color = IndTextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    val categoriesList = listOf(
                        DebitCategory.RENT.name to "Rent",
                        DebitCategory.EMI.name to "EMI / Loan",
                        DebitCategory.ELECTRICITY.name to "Electricity",
                        DebitCategory.INTERNET.name to "Internet",
                        DebitCategory.SHOPPING.name to "Shopping",
                        DebitCategory.INVESTMENT.name to "Investment",
                        DebitCategory.SAVING.name to "Saving",
                        DebitCategory.GENERAL.name to "General",
                        DebitCategory.CUSTOM.name to "Custom"
                    )

                    // FlowRow chips for Category Type
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categoriesList.forEach { (catKey, label) ->
                            val isSelected = selectedCategory == catKey
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedCategory = catKey
                                    if (title.isBlank()) {
                                        title = when (catKey) {
                                            DebitCategory.RENT.name -> "House Rent"
                                            DebitCategory.EMI.name -> "Loan EMI"
                                            DebitCategory.ELECTRICITY.name -> "Electricity Bill"
                                            DebitCategory.INTERNET.name -> "WiFi Broadband"
                                            DebitCategory.SHOPPING.name -> "Monthly Shopping"
                                            DebitCategory.INVESTMENT.name -> "Mutual Fund SIP"
                                            DebitCategory.SAVING.name -> "Emergency Fund RD"
                                            DebitCategory.GENERAL.name -> "Groceries & Daily"
                                            else -> ""
                                        }
                                    }
                                },
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = IndNavyHeader,
                                    selectedLabelColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }

                if (selectedCategory == DebitCategory.CUSTOM.name) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Select Custom Category",
                                style = MaterialTheme.typography.labelMedium,
                                color = IndTextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                            TextButton(onClick = onAddCustomCategoryClick) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = IndBlue)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("New Category", style = MaterialTheme.typography.labelSmall, color = IndBlue)
                            }
                        }

                        if (customCategories.isNotEmpty()) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                customCategories.forEach { cat ->
                                    val isSelected = selectedCustomCategory == cat.name
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedCustomCategory = cat.name },
                                        label = { Text(cat.name, style = MaterialTheme.typography.labelMedium, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = IndBlue,
                                            selectedLabelColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = selectedCustomCategory,
                                onValueChange = { selectedCustomCategory = it },
                                label = { Text("Category Name (e.g. Gym, Maid, Gas)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = {
                            title = it
                            errorText = null
                        },
                        label = { Text("Title / Purpose") },
                        placeholder = {
                            Text(
                                when (selectedCategory) {
                                    DebitCategory.RENT.name -> "e.g. 2BHK Apartment Rent"
                                    DebitCategory.EMI.name -> "e.g. HDFC Home Loan"
                                    DebitCategory.ELECTRICITY.name -> "e.g. State Electricity Board"
                                    DebitCategory.INTERNET.name -> "e.g. Airtel Fiber WiFi"
                                    DebitCategory.SHOPPING.name -> "e.g. Amazon & Clothing"
                                    DebitCategory.INVESTMENT.name -> "e.g. Nifty 50 Index SIP"
                                    DebitCategory.SAVING.name -> "e.g. Emergency Fund RD"
                                    DebitCategory.GENERAL.name -> "e.g. Daily Groceries & Milk"
                                    else -> "e.g. Monthly Maintenance"
                                }
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("debit_title_field"),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                item {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = {
                            amountText = it
                            errorText = null
                        },
                        label = { Text("Monthly Debit Amount ($currencySymbol)") },
                        placeholder = { Text("e.g. 10000") },
                        leadingIcon = { Text(currencySymbol, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp), color = IndGreenDark) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("debit_amount_field"),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = dueDateDayText,
                            onValueChange = { dueDateDayText = it },
                            label = { Text("Due Day (1-31)") },
                            placeholder = { Text("5") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )

                        if (selectedCategory == DebitCategory.EMI.name) {
                            OutlinedTextField(
                                value = interestRateText,
                                onValueChange = { interestRateText = it },
                                label = { Text("Interest %") },
                                placeholder = { Text("8.5") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }

                if (selectedCategory == DebitCategory.EMI.name) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = currentTenureText,
                                onValueChange = { currentTenureText = it },
                                label = { Text("Current Month #") },
                                placeholder = { Text("12") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = totalTenureText,
                                onValueChange = { totalTenureText = it },
                                label = { Text("Total Tenure Months") },
                                placeholder = { Text("24") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes / Details (Optional)") },
                        placeholder = { Text("e.g. Account number, auto-debit note") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Recurring Every Month", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Automatically prefill next month", style = MaterialTheme.typography.bodySmall, color = IndTextSecondary)
                        }
                        Switch(
                            checked = isRecurring,
                            onCheckedChange = { isRecurring = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = IndGreen, checkedTrackColor = IndGreenLight)
                        )
                    }
                }

                if (errorText != null) {
                    item {
                        Text(
                            text = errorText!!,
                            color = IndRed,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cancel", color = IndTextSecondary)
                        }
                        Button(
                            onClick = {
                                val amt = amountText.toDoubleOrNull()
                                if (title.isBlank()) {
                                    errorText = "Please enter a title for the debit"
                                } else if (amt == null || amt <= 0) {
                                    errorText = "Please enter a valid amount"
                                } else {
                                    val dueDay = dueDateDayText.toIntOrNull()?.coerceIn(1, 31) ?: 1
                                    val interest = interestRateText.toDoubleOrNull() ?: 0.0
                                    val totalT = totalTenureText.toIntOrNull() ?: 0
                                    val currT = currentTenureText.toIntOrNull() ?: 0
                                    val finalCustomCat = if (selectedCategory == DebitCategory.CUSTOM.name) selectedCustomCategory else ""

                                    onSave(
                                        debitToEdit?.id ?: 0L,
                                        selectedCategory,
                                        finalCustomCat,
                                        title.trim(),
                                        amt,
                                        dueDay,
                                        isRecurring,
                                        interest,
                                        totalT,
                                        currT,
                                        notes.trim()
                                    )
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_debit_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IndGreen, contentColor = Color.White)
                        ) {
                            Text(if (isEditMode) "Save Changes" else "Add Debit", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddCustomCategoryDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, colorHex: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf("#00B377") }
    var errorText by remember { mutableStateOf<String?>(null) }

    val colors = listOf("#00B377", "#0066FF", "#7C3AED", "#F59E0B", "#DB2777", "#4F46E5", "#0284C7", "#DC2626")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = IndSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Add Custom Monthly Category",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = IndTextPrimary
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorText = null
                    },
                    label = { Text("Category Name") },
                    placeholder = { Text("e.g. Maid & Cook, School Fees, Subscriptions") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Text(
                    text = "Color Accent",
                    style = MaterialTheme.typography.labelMedium,
                    color = IndTextSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    colors.forEach { hex ->
                        val isSelected = selectedColor == hex
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(parseHexColor(hex))
                                .border(
                                    if (isSelected) 2.5.dp else 0.dp,
                                    if (isSelected) IndNavyHeader else Color.Transparent,
                                    CircleShape
                                )
                                .clickable { selectedColor = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        color = IndRed,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel", color = IndTextSecondary)
                    }
                    Button(
                        onClick = {
                            if (name.isBlank()) {
                                errorText = "Please enter a category name"
                            } else {
                                onSave(name.trim(), selectedColor)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndGreen, contentColor = Color.White)
                    ) {
                        Text("Create", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditLoanDialog(
    loanToEdit: LoanTransaction? = null,
    currencySymbol: String,
    initialType: LoanType = LoanType.GIVEN,
    existingLoans: List<LoanTransaction> = emptyList(),
    onSavePayment: ((loanId: Long, amount: Double, note: String, proofUri: String, isDirectApproval: Boolean) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (
        loanId: Long,
        type: LoanType,
        amount: Double,
        counterpartyName: String,
        counterpartyContact: String,
        interestRatePercent: Double,
        isMonthlyInterest: Boolean,
        startDateTimestamp: Long,
        dueDateTimestamp: Long?,
        note: String,
        proofUri: String
    ) -> Unit
) {
    val isEditMode = loanToEdit != null
    val context = androidx.compose.ui.platform.LocalContext.current

    val activeLoans = remember(existingLoans) {
        existingLoans.filter { (it.totalAmount - it.settledAmount) > 0 }
    }
    var isClearanceMode by remember { mutableStateOf(false) }
    var selectedClearanceLoanId by remember { mutableStateOf(activeLoans.firstOrNull()?.id) }
    val selectedClearanceLoan = remember(activeLoans, selectedClearanceLoanId) {
        activeLoans.firstOrNull { it.id == selectedClearanceLoanId } ?: activeLoans.firstOrNull()
    }
    var clearanceAmountText by remember(selectedClearanceLoanId) {
        val rem = selectedClearanceLoan?.let { (it.totalAmount - it.settledAmount).coerceAtLeast(0.0) } ?: 0.0
        mutableStateOf(if (rem > 0) rem.toInt().toString() else "")
    }
    var clearanceNote by remember { mutableStateOf("UPI payment / partial clearance") }
    var clearanceProofUri by remember { mutableStateOf("") }
    var clearanceRole by remember { mutableStateOf("PAID") }

    var loanType by remember { mutableStateOf(if (isEditMode) (if (loanToEdit?.type == LoanType.TAKEN.name) LoanType.TAKEN else LoanType.GIVEN) else initialType) }
    var totalAmountText by remember { mutableStateOf(if (isEditMode) loanToEdit?.totalAmount?.toInt()?.toString() ?: "" else "") }
    var counterpartyName by remember { mutableStateOf(if (isEditMode) loanToEdit?.counterpartyName ?: "" else "") }
    var counterpartyContact by remember { mutableStateOf(if (isEditMode) loanToEdit?.counterpartyContact ?: "" else "") }
    var interestRateText by remember { mutableStateOf(if (isEditMode && (loanToEdit?.interestRatePercent ?: 0.0) > 0) loanToEdit?.interestRatePercent?.toString() ?: "" else "") }
    var isMonthlyInterest by remember { mutableStateOf(loanToEdit?.isMonthlyInterest ?: true) }
    var note by remember { mutableStateOf(loanToEdit?.note ?: "") }
    var attachedProofs by remember {
        val initialList = com.example.util.ProofStorageHelper.parseProofFiles(loanToEdit?.proofFilesJson ?: loanToEdit?.proofUri ?: "")
        mutableStateOf<List<com.example.util.ProofFile>>(initialList)
    }
    var viewingProofDialogUri by remember { mutableStateOf<String?>(null) }
    var proofUri by remember { mutableStateOf(loanToEdit?.proofUri ?: "") }

    // Due date mode: 0 = In Days, 1 = Pick Exact Date
    var dueDateMode by remember { mutableStateOf(0) }
    var dueInDaysText by remember { mutableStateOf("30") }
    var selectedDueDateTimestamp by remember {
        mutableStateOf(loanToEdit?.dueDateTimestamp ?: (System.currentTimeMillis() + 30L * 86400000L))
    }
    var startDateTimestamp by remember {
        mutableStateOf(loanToEdit?.startDateTimestamp ?: System.currentTimeMillis())
    }

    var showDatePickerForDue by remember { mutableStateOf(false) }
    var showDatePickerForStart by remember { mutableStateOf(false) }
    var showProofPickerSheet by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    // Registration status check for counterparty
    var isCheckingRegistration by remember { mutableStateOf(false) }
    var isCounterpartyRegistered by remember { mutableStateOf<Boolean?>(null) }
    var registeredCounterpartyName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(counterpartyContact) {
        val clean = counterpartyContact.filter { it.isDigit() }
        if (clean.length >= 10) {
            isCheckingRegistration = true
            try {
                val db = FirebaseFirestore.getInstance()
                val doc = db.collection("users").document(clean.takeLast(10)).get().await()
                if (doc.exists()) {
                    isCounterpartyRegistered = true
                    registeredCounterpartyName = doc.getString("displayName") ?: doc.getString("name")
                } else {
                    isCounterpartyRegistered = false
                    registeredCounterpartyName = null
                }
            } catch (e: Exception) {
                isCounterpartyRegistered = null
            } finally {
                isCheckingRegistration = false
            }
        } else {
            isCounterpartyRegistered = null
            registeredCounterpartyName = null
        }
    }

    // Android Contact Picker (Fetches BOTH Display Name AND Phone Number)
    val contactPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val contactUri = result.data?.data
            if (contactUri != null) {
                try {
                    val projection = arrayOf(
                        android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
                    )
                    val cursor = context.contentResolver.query(contactUri, projection, null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val nameIndex = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                            val numberIndex = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                            if (nameIndex != -1) {
                                val pickedName = it.getString(nameIndex)
                                if (!pickedName.isNullOrBlank()) {
                                    counterpartyName = pickedName.trim()
                                }
                            }
                            if (numberIndex != -1) {
                                val rawNumber = it.getString(numberIndex)
                                if (!rawNumber.isNullOrBlank()) {
                                    val digitsOnly = rawNumber.filter { c -> c.isDigit() }
                                    counterpartyContact = if (digitsOnly.length > 10 && digitsOnly.startsWith("91")) {
                                        digitsOnly.substring(digitsOnly.length - 10)
                                    } else if (digitsOnly.length > 10 && digitsOnly.startsWith("0")) {
                                        digitsOnly.substring(digitsOnly.length - 10)
                                    } else if (digitsOnly.length >= 10) {
                                        digitsOnly.takeLast(10)
                                    } else {
                                        rawNumber.trim()
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Fallback
                }
            }
        }
    }

    // Android Photo Picker for Proof
    val photoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val localPath = com.example.util.ProofStorageHelper.persistProofImage(context, uri)
            if (isClearanceMode) {
                clearanceProofUri = localPath
            } else {
                proofUri = localPath
            }
        }
    }

    if (viewingProofDialogUri != null) {
        FullScreenImageViewerDialog(
            proofUri = viewingProofDialogUri!!,
            title = "Attachment Preview",
            onDismiss = { viewingProofDialogUri = null }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = IndSurface)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (isEditMode) "Edit Loan / Khaata" else if (isClearanceMode) "Clearance / Repayment" else "Lend / Borrow Money",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = IndTextPrimary
                            )
                            Text(
                                text = if (isClearanceMode) "Pay down an existing pending agreement"
                                       else if (loanType == LoanType.GIVEN) "Money Given (Lent to friend)"
                                       else "Money Taken (Borrowed from friend)",
                                style = MaterialTheme.typography.bodySmall,
                                color = IndTextSecondary
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = IndTextSecondary)
                        }
                    }
                }

                // Entry Mode Selector (New Loan vs Clearance of existing partial payment - Requirement #6)
                if (!isEditMode && activeLoans.isNotEmpty() && onSavePayment != null) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = IndBackground,
                            border = androidx.compose.foundation.BorderStroke(1.dp, IndBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Select Transaction Purpose:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = IndNavyHeader
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = !isClearanceMode,
                                        onClick = { isClearanceMode = false },
                                        label = { Text("New Loan Agreement", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = isClearanceMode,
                                        onClick = { isClearanceMode = true },
                                        label = { Text("Clearance of Partial Payment", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                if (isClearanceMode && activeLoans.isNotEmpty() && onSavePayment != null) {
                    // Clearance Mode: Select which loan this payment is for
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Select Which Pending Agreement to Clear:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = IndTextPrimary
                            )

                            activeLoans.forEach { loanItem ->
                                val isSelected = loanItem.id == (selectedClearanceLoan?.id ?: -1L)
                                val remainingDue = (loanItem.totalAmount - loanItem.settledAmount).coerceAtLeast(0.0)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) IndNavyHeader.copy(alpha = 0.08f) else IndSurface,
                                    border = androidx.compose.foundation.BorderStroke(
                                        if (isSelected) 1.5.dp else 1.dp,
                                        if (isSelected) IndNavyHeader else IndBorder
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedClearanceLoanId = loanItem.id
                                            clearanceAmountText = remainingDue.toInt().toString()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "${loanItem.counterpartyName} (${if (loanItem.type == LoanType.GIVEN.name) "Lent" else "Borrowed"})",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = IndTextPrimary
                                            )
                                            Text(
                                                text = "Total: $currencySymbol${"%,.0f".format(loanItem.totalAmount)} • Paid: $currencySymbol${"%,.0f".format(loanItem.settledAmount)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = IndTextSecondary
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "Due: $currencySymbol${"%,.0f".format(remainingDue)}",
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (loanItem.type == LoanType.GIVEN.name) IndGreenDark else IndRed,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            if (isSelected) {
                                                Text("Selected ✓", fontSize = 10.sp, color = IndNavyHeader, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Clearance Amount
                    item {
                        OutlinedTextField(
                            value = clearanceAmountText,
                            onValueChange = { clearanceAmountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                            label = { Text("Clearance Amount ($currencySymbol)") },
                            leadingIcon = { Text(currencySymbol, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp)) },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    // Clearance Role
                    item {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = IndBackground,
                            border = androidx.compose.foundation.BorderStroke(1.dp, IndBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Payment Status:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = IndTextSecondary)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = clearanceRole == "PAID",
                                        onClick = { clearanceRole = "PAID" },
                                        label = { Text("I Paid (Needs Verification)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = clearanceRole == "RECEIVED",
                                        onClick = { clearanceRole = "RECEIVED" },
                                        label = { Text("I Received (Direct Clear)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // Clearance Note
                    item {
                        OutlinedTextField(
                            value = clearanceNote,
                            onValueChange = { clearanceNote = it },
                            label = { Text("Note / Transaction Reference") },
                            placeholder = { Text("e.g. PhonePe UPI Txn #1234") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    // Clearance Proof (Multiple Files & Types)
                    item {
                        MultiProofAttachmentSection(
                            proofFiles = attachedProofs,
                            onFilesChanged = { attachedProofs = it },
                            onViewFile = { f ->
                                viewingProofDialogUri = com.example.util.ProofStorageHelper.encodeProofFiles(listOf(f))
                            }
                        )
                    }

                    // Submit Clearance
                    item {
                        Button(
                            onClick = {
                                val amt = clearanceAmountText.toDoubleOrNull() ?: 0.0
                                if (amt <= 0.0) {
                                    errorText = "Please enter a valid amount."
                                    return@Button
                                }
                                val target = selectedClearanceLoan ?: return@Button
                                val encodedProofJson = com.example.util.ProofStorageHelper.encodeProofFiles(attachedProofs)
                                val primaryLocal = attachedProofs.firstOrNull()?.let { com.example.util.ProofStorageHelper.ensureLocalPath(context, it) } ?: ""
                                val proofPayload = if (encodedProofJson.isNotBlank()) encodedProofJson else if (primaryLocal.isNotBlank()) primaryLocal else clearanceProofUri
                                onSavePayment(target.id, amt, clearanceNote, proofPayload, clearanceRole == "RECEIVED")
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IndNavyHeader)
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Confirm & Record Clearance", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(IndBackground)
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (loanType == LoanType.GIVEN) IndGreen else Color.Transparent)
                                .clickable { loanType = LoanType.GIVEN }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = if (loanType == LoanType.GIVEN) Color.White else IndTextSecondary, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "Money Given (Lent)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (loanType == LoanType.GIVEN) Color.White else IndTextSecondary
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (loanType == LoanType.TAKEN) IndRed else Color.Transparent)
                                .clickable { loanType = LoanType.TAKEN }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.ArrowDownward, contentDescription = null, tint = if (loanType == LoanType.TAKEN) Color.White else IndTextSecondary, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "Money Taken (Borrowed)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (loanType == LoanType.TAKEN) Color.White else IndTextSecondary
                                )
                            }
                        }
                    }
                }

                // Counterparty info + Contacts button (Requirement #1)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (loanType == LoanType.GIVEN) "Borrower Details" else "Lender Details",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = IndTextPrimary
                            )
                            OutlinedButton(
                                onClick = {
                                    try {
                                        val intent = Intent(
                                            Intent.ACTION_PICK,
                                            android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                                        )
                                        contactPickerLauncher.launch(intent)
                                    } catch (e: Exception) {
                                        // Fallback
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Filled.Contacts, contentDescription = null, modifier = Modifier.size(14.dp), tint = IndBlue)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pick Contact", fontSize = 11.sp, color = IndBlue, fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedTextField(
                            value = counterpartyName,
                            onValueChange = {
                                counterpartyName = it
                                errorText = null
                            },
                            label = { Text(if (loanType == LoanType.GIVEN) "Borrower Name" else "Lender Name") },
                            placeholder = { Text("e.g. Rahul Sharma") },
                            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = IndBlue) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("counterparty_name_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = counterpartyContact,
                            onValueChange = { counterpartyContact = it },
                            label = { Text("Phone Number or Email") },
                            placeholder = { Text("+91 98765 43210 / rahul@gmail.com") },
                            leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null, tint = IndTextSecondary) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Registration Status Badge
                        if (counterpartyContact.isNotBlank() && counterpartyContact.filter { it.isDigit() }.length >= 10) {
                            if (isCheckingRegistration) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = IndBlue)
                                    Text("Checking FinMoney cloud registry...", fontSize = 11.sp, color = IndTextSecondary)
                                }
                            } else if (isCounterpartyRegistered == true) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = IndGreenLight,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, IndGreenDark.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = IndGreenDark, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = "Registered on FinMoney (${registeredCounterpartyName ?: "Active User"}) • Direct Cloud Sync",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = IndGreenDark
                                        )
                                    }
                                }
                            } else if (isCounterpartyRegistered == false) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = IndAmberLight,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, IndAmber.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Filled.Info, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(16.dp))
                                        Text(
                                            text = "Not Yet Registered • You can invite them via WhatsApp / SMS to install & sign",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFFB45309)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Amount
                item {
                    OutlinedTextField(
                        value = totalAmountText,
                        onValueChange = {
                            totalAmountText = it
                            errorText = null
                        },
                        label = { Text("Principal Amount ($currencySymbol)") },
                        placeholder = { Text("e.g. 25000") },
                        leadingIcon = { Text(currencySymbol, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp), color = IndGreenDark) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("loan_amount_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Transaction Start Date (Requirement #9)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, IndBorder, RoundedCornerShape(12.dp))
                            .clickable { showDatePickerForStart = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Event, contentDescription = null, tint = IndBlue, modifier = Modifier.size(20.dp))
                            Column {
                                Text("Transaction Date", style = MaterialTheme.typography.labelSmall, color = IndTextSecondary)
                                Text(dateFormatter.format(Date(startDateTimestamp)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = IndTextPrimary)
                            }
                        }
                        Text("Change", style = MaterialTheme.typography.labelSmall, color = IndBlue, fontWeight = FontWeight.Bold)
                    }
                }

                // Due Date Mode: Number of Days OR Future Date (Requirement #2)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Due Date Options", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = IndTextPrimary)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(IndBackground)
                                .padding(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (dueDateMode == 0) IndNavyHeader else Color.Transparent)
                                    .clickable { dueDateMode = 0 }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "In Number of Days",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (dueDateMode == 0) Color.White else IndTextSecondary
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (dueDateMode == 1) IndNavyHeader else Color.Transparent)
                                    .clickable { dueDateMode = 1 }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Pick Future Date",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (dueDateMode == 1) Color.White else IndTextSecondary
                                )
                            }
                        }

                        if (dueDateMode == 0) {
                            // Quick Day Chips + Custom Input
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("7", "15", "30", "60", "90").forEach { d ->
                                    val isSelected = dueInDaysText == d
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            dueInDaysText = d
                                            selectedDueDateTimestamp = startDateTimestamp + (d.toLong() * 86400000L)
                                        },
                                        label = { Text("${d}d", fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = IndBlue,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = dueInDaysText,
                                onValueChange = {
                                    dueInDaysText = it
                                    val days = it.toLongOrNull() ?: 30L
                                    selectedDueDateTimestamp = startDateTimestamp + (days * 86400000L)
                                },
                                label = { Text("Custom Due Duration (Days)") },
                                placeholder = { Text("e.g. 45") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        } else {
                            // Future Date selector
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, IndBorder, RoundedCornerShape(12.dp))
                                    .clickable { showDatePickerForDue = true }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = IndBlue, modifier = Modifier.size(20.dp))
                                    Column {
                                        Text("Target Due Date", style = MaterialTheme.typography.labelSmall, color = IndTextSecondary)
                                        Text(dateFormatter.format(Date(selectedDueDateTimestamp)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = IndTextPrimary)
                                    }
                                }
                                Text("Select Date", style = MaterialTheme.typography.labelSmall, color = IndBlue, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Interest details
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = interestRateText,
                            onValueChange = { interestRateText = it },
                            label = { Text("Interest Rate % (0 for free)") },
                            placeholder = { Text("1.5") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(IndBackground)
                                .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isMonthlyInterest) IndNavyHeader else Color.Transparent)
                                    .clickable { isMonthlyInterest = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("% pm", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isMonthlyInterest) Color.White else IndTextSecondary)
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (!isMonthlyInterest) IndNavyHeader else Color.Transparent)
                                    .clickable { isMonthlyInterest = false },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("% pa", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (!isMonthlyInterest) Color.White else IndTextSecondary)
                            }
                        }
                    }
                }

                // Note / Purpose
                item {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Purpose / Note") },
                        placeholder = { Text("e.g. Emergency loan, Rent advance, Business credit") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Payment Proof Upload / Multiple Proofs & Documents (Requirement #2)
                item {
                    MultiProofAttachmentSection(
                        proofFiles = attachedProofs,
                        onFilesChanged = { attachedProofs = it },
                        onViewFile = { f ->
                            viewingProofDialogUri = com.example.util.ProofStorageHelper.encodeProofFiles(listOf(f))
                        }
                    )
                }

                // Dual verification notice (Requirement #6)
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = IndAmberLight,
                        border = androidx.compose.foundation.BorderStroke(1.dp, IndAmber.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Shield, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(20.dp))
                            Text(
                                text = "Two-Sided Approval: Once submitted, counterparty must verify & approve this record before it is finalized.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF92400E)
                            )
                        }
                    }
                }

                if (errorText != null) {
                    item {
                        Text(
                            text = errorText!!,
                            color = IndRed,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cancel", color = IndTextSecondary)
                        }
                        Button(
                            onClick = {
                                val amt = totalAmountText.toDoubleOrNull()
                                if (amt == null || amt <= 0) {
                                    errorText = "Please enter a valid amount"
                                } else if (counterpartyName.isBlank()) {
                                    errorText = "Please enter the other party's name"
                                } else {
                                    val interest = interestRateText.toDoubleOrNull() ?: 0.0
                                    val finalDueDate = if (dueDateMode == 0) {
                                        val days = dueInDaysText.toLongOrNull() ?: 30L
                                        startDateTimestamp + (days * 86400000L)
                                    } else {
                                        selectedDueDateTimestamp
                                    }

                                    val proofJson = com.example.util.ProofStorageHelper.encodeProofFiles(attachedProofs)
                                    val primaryUri = attachedProofs.firstOrNull()?.let { com.example.util.ProofStorageHelper.ensureLocalPath(context, it) } ?: ""
                                    val proofPayload = if (proofJson.isNotBlank()) proofJson else if (primaryUri.isNotBlank()) primaryUri else proofUri

                                    onSave(
                                        loanToEdit?.id ?: 0L,
                                        loanType,
                                        amt,
                                        counterpartyName.trim(),
                                        counterpartyContact.trim(),
                                        interest,
                                        isMonthlyInterest,
                                        startDateTimestamp,
                                        finalDueDate,
                                        note.trim(),
                                        proofPayload
                                    )
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("submit_loan_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IndGreen, contentColor = Color.White)
                        ) {
                            Text(if (isEditMode) "Save Changes" else "Submit & Record", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                }
            }
        }
    }

    // Date Picker Dialog for Due Date
    if (showDatePickerForDue) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDueDateTimestamp)
        DatePickerDialog(
            onDismissRequest = { showDatePickerForDue = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            selectedDueDateTimestamp = it
                        }
                        showDatePickerForDue = false
                    }
                ) {
                    Text("OK", fontWeight = FontWeight.Bold, color = IndGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerForDue = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Date Picker Dialog for Start Date
    if (showDatePickerForStart) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = startDateTimestamp)
        DatePickerDialog(
            onDismissRequest = { showDatePickerForStart = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            startDateTimestamp = it
                        }
                        showDatePickerForStart = false
                    }
                ) {
                    Text("OK", fontWeight = FontWeight.Bold, color = IndGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerForStart = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun MultiProofAttachmentSection(
    proofFiles: List<com.example.util.ProofFile>,
    onFilesChanged: (List<com.example.util.ProofFile>) -> Unit,
    onViewFile: (com.example.util.ProofFile) -> Unit
) {
    val context = LocalContext.current

    // Multi-Image Picker
    val photoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(5)
    ) { uris ->
        if (uris.isNotEmpty()) {
            val newFiles = uris.map { uri ->
                com.example.util.ProofStorageHelper.persistFileFromUri(context, uri)
            }
            onFilesChanged(proofFiles + newFiles)
        }
    }

    // Document / PDF Picker
    val docPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val newFiles = uris.map { uri ->
                com.example.util.ProofStorageHelper.persistFileFromUri(context, uri)
            }
            onFilesChanged(proofFiles + newFiles)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Proof & Documents (${proofFiles.size})",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = IndTextSecondary
            )
            Text(
                text = "Images, PDFs, UPI receipts",
                fontSize = 10.sp,
                color = IndTextMuted
            )
        }

        // Action Buttons to Add Photos or Documents
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    try {
                        photoPickerLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    } catch (_: Exception) {}
                },
                modifier = Modifier.weight(1f).height(38.dp),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, IndBorder)
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(15.dp), tint = IndNavyHeader)
                Spacer(modifier = Modifier.width(4.dp))
                Text("+ Photos", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IndNavyHeader)
            }

            OutlinedButton(
                onClick = {
                    try {
                        docPickerLauncher.launch(arrayOf("application/pdf", "image/*"))
                    } catch (_: Exception) {}
                },
                modifier = Modifier.weight(1f).height(38.dp),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, IndBorder)
            ) {
                Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(15.dp), tint = IndNavyHeader)
                Spacer(modifier = Modifier.width(4.dp))
                Text("+ PDF / Doc", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IndNavyHeader)
            }
        }

        // Thumbnails preview row
        if (proofFiles.isNotEmpty()) {
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(proofFiles.size) { index ->
                    val file = proofFiles[index]
                    val localPath = com.example.util.ProofStorageHelper.ensureLocalPath(context, file)

                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(IndBackground)
                            .border(1.dp, IndBorder, RoundedCornerShape(10.dp))
                            .clickable { onViewFile(file) }
                    ) {
                        if (file.isPdf) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Filled.PictureAsPdf, contentDescription = null, tint = IndRed, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = file.name.take(9),
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    color = IndTextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("PDF", fontSize = 8.sp, color = IndRed, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            AsyncImage(
                                model = if (localPath.isNotBlank()) localPath else file.cloudBase64,
                                contentDescription = file.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        // Remove chip button
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.65f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(3.dp)
                                .size(18.dp)
                                .clickable {
                                    onFilesChanged(proofFiles.filterIndexed { i, _ -> i != index })
                                }
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FullScreenImageViewerDialog(
    proofUri: String,
    title: String = "Transaction Proof",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val proofFiles = remember(proofUri) {
        val parsed = com.example.util.ProofStorageHelper.parseProofFiles(proofUri)
        if (parsed.isNotEmpty()) parsed else listOf(com.example.util.ProofFile(localPath = proofUri))
    }
    var selectedFileIndex by remember { mutableStateOf(0) }
    val currentFile = proofFiles.getOrNull(selectedFileIndex) ?: proofFiles.firstOrNull() ?: com.example.util.ProofFile()
    val localDisplayPath = remember(currentFile) {
        com.example.util.ProofStorageHelper.ensureLocalPath(context, currentFile)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = IndSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = IndTextPrimary)
                        if (proofFiles.size > 1) {
                            Text("File ${selectedFileIndex + 1} of ${proofFiles.size} • ${currentFile.name}", fontSize = 10.sp, color = IndTextSecondary)
                        } else {
                            Text(currentFile.name, fontSize = 10.sp, color = IndTextSecondary)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = IndTextSecondary)
                    }
                }

                // File Selector Tabs if multiple files
                if (proofFiles.size > 1) {
                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(proofFiles.size) { idx ->
                            val f = proofFiles[idx]
                            val isSel = idx == selectedFileIndex
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) IndNavyHeader else IndBackground,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) IndNavyHeader else IndBorder),
                                modifier = Modifier.clickable { selectedFileIndex = idx }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        if (f.isPdf) Icons.Filled.PictureAsPdf else Icons.Filled.Image,
                                        contentDescription = null,
                                        tint = if (isSel) Color.White else IndTextSecondary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "${idx + 1}. ${f.name.take(10)}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSel) Color.White else IndTextPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                // Genuine Image / PDF Preview Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, IndBorder, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (currentFile.isPdf) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = null, tint = IndRed, modifier = Modifier.size(56.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(currentFile.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "PDF Document • ${if (currentFile.sizeBytes > 0) "${currentFile.sizeBytes / 1024} KB" else "Available"}",
                                color = Color.LightGray,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    com.example.util.ProofStorageHelper.openInExternalViewer(context, currentFile)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = IndNavyHeaderLight),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open in PDF Reader", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else if (localDisplayPath.isNotBlank() || currentFile.cloudBase64.isNotBlank()) {
                        AsyncImage(
                            model = if (localDisplayPath.isNotBlank()) localDisplayPath else currentFile.cloudBase64,
                            contentDescription = "Transaction Proof Image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Filled.BrokenImage, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No image data available", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }

                // Download & Open in Gallery Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Open in External Gallery / Viewer
                    OutlinedButton(
                        onClick = {
                            try {
                                com.example.util.ProofStorageHelper.openInExternalViewer(context, currentFile)
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Could not open viewer: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = IndNavyHeader),
                        border = androidx.compose.foundation.BorderStroke(1.dp, IndBorder)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Open Full", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Download / Save to Gallery / Downloads
                    Button(
                        onClick = {
                            val saved = com.example.util.ProofStorageHelper.saveToGallery(context, currentFile)
                            if (saved) {
                                android.widget.Toast.makeText(context, "Proof saved to Pictures/FinMoney! 📥", android.widget.Toast.LENGTH_LONG).show()
                            } else {
                                android.widget.Toast.makeText(context, "Could not save to device storage.", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndNavyHeader, contentColor = Color.White)
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (currentFile.isPdf) "Save PDF" else "Save Image", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun RecordPaymentDialog(
    loan: LoanTransaction,
    currencySymbol: String,
    userProfile: UserProfile? = null,
    onDismiss: () -> Unit,
    onSavePayment: (amount: Double, note: String, proofUri: String, isDirectApproval: Boolean) -> Unit
) {
    val context = LocalContext.current

    val existingPayments = remember(loan.paymentHistoryJson) {
        com.example.data.repository.FinMoneyRepository.parsePaymentHistory(loan.paymentHistoryJson)
    }

    val interestBreakdown = remember(loan, existingPayments) {
        com.example.util.InterestCalculator.calculate(
            principal = loan.totalAmount,
            ratePercent = loan.interestRatePercent,
            isMonthly = loan.isMonthlyInterest,
            startTimestamp = loan.startDateTimestamp,
            payments = existingPayments
        )
    }

    var selectedTarget by remember { mutableStateOf("COMBINED") }
    var amountText by remember {
        val initialAmt = if (loan.interestRatePercent > 0.0 && interestBreakdown.totalRemainingDue > 0) {
            interestBreakdown.totalRemainingDue.toInt().toString()
        } else {
            val remaining = (loan.totalAmount - loan.settledAmount).coerceAtLeast(0.0)
            if (remaining > 0) remaining.toInt().toString() else ""
        }
        mutableStateOf(initialAmt)
    }

    var note by remember { mutableStateOf("UPI Payment via PhonePe") }
    var attachedProofs by remember { mutableStateOf<List<com.example.util.ProofFile>>(emptyList()) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var paymentRole by remember { mutableStateOf("PAID") } // "PAID" (Requires verification) or "RECEIVED" (Direct confirmation)
    var viewingProofJson by remember { mutableStateOf<String?>(null) }

    if (viewingProofJson != null) {
        FullScreenImageViewerDialog(
            proofUri = viewingProofJson!!,
            title = "Payment Proof Preview",
            onDismiss = { viewingProofJson = null }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = IndSurface)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Log Payment / Installment", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = IndTextPrimary)
                            Text("Party: ${loan.counterpartyName}", style = MaterialTheme.typography.bodySmall, color = IndTextSecondary)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = IndTextSecondary)
                        }
                    }
                }

                // Interest Calculation Breakdown Card (Requirement #6)
                if (loan.interestRatePercent > 0.0) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = IndCardSecondary,
                            border = androidx.compose.foundation.BorderStroke(1.dp, IndBlue.copy(alpha = 0.5f)),
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
                                    Text(
                                        text = "INTEREST & PRINCIPAL BREAKDOWN",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = IndTextMuted,
                                        fontSize = 9.sp
                                    )
                                    Text(
                                        text = "${loan.interestRatePercent}% ${if (loan.isMonthlyInterest) "pm" else "pa"} (${interestBreakdown.daysElapsed} days)",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = IndNavyHeader
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Principal Due", fontSize = 10.sp, color = IndTextSecondary)
                                        Text(
                                            text = "$currencySymbol${"%,.0f".format(interestBreakdown.remainingPrincipalDue)}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = IndTextPrimary
                                        )
                                        Text("Paid: $currencySymbol${"%,.0f".format(interestBreakdown.principalPaid)}", fontSize = 9.sp, color = IndTextMuted)
                                    }

                                    Column {
                                        Text("Accrued Interest Due", fontSize = 10.sp, color = IndTextSecondary)
                                        Text(
                                            text = "$currencySymbol${"%,.0f".format(interestBreakdown.remainingInterestDue)}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = if (interestBreakdown.remainingInterestDue > 0) IndAmber else IndGreenDark
                                        )
                                        Text("Paid: $currencySymbol${"%,.0f".format(interestBreakdown.interestPaid)}", fontSize = 9.sp, color = IndTextMuted)
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Total Outstanding", fontSize = 10.sp, color = IndTextSecondary)
                                        Text(
                                            text = "$currencySymbol${"%,.0f".format(interestBreakdown.totalRemainingDue)}",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 13.sp,
                                            color = IndNavyHeader
                                        )
                                        Text("Total + Int: $currencySymbol${"%,.0f".format(interestBreakdown.totalAmountWithInterest)}", fontSize = 9.sp, color = IndTextMuted)
                                    }
                                }

                                // Quick Clearance Chips: Interest First vs Principal
                                Text("Select Payment Allocation:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = IndTextSecondary)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilterChip(
                                        selected = selectedTarget == "COMBINED",
                                        onClick = {
                                            selectedTarget = "COMBINED"
                                            amountText = interestBreakdown.totalRemainingDue.toInt().toString()
                                            note = "Installment Payment"
                                        },
                                        label = { Text("Full Due ($currencySymbol${interestBreakdown.totalRemainingDue.toInt()})", fontSize = 10.sp) },
                                        modifier = Modifier.weight(1f)
                                    )

                                    if (interestBreakdown.remainingInterestDue > 0) {
                                        FilterChip(
                                            selected = selectedTarget == "INTEREST",
                                            onClick = {
                                                selectedTarget = "INTEREST"
                                                amountText = interestBreakdown.remainingInterestDue.toInt().toString()
                                                note = "Interest Settlement (${loan.interestRatePercent}%)"
                                            },
                                            label = { Text("Interest ($currencySymbol${interestBreakdown.remainingInterestDue.toInt()})", fontSize = 10.sp) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    FilterChip(
                                        selected = selectedTarget == "PRINCIPAL",
                                        onClick = {
                                            selectedTarget = "PRINCIPAL"
                                            amountText = interestBreakdown.remainingPrincipalDue.toInt().toString()
                                            note = "Principal Clearance"
                                        },
                                        label = { Text("Principal ($currencySymbol${interestBreakdown.remainingPrincipalDue.toInt()})", fontSize = 10.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Payment Type & Verification Flow Selector
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = IndBackground,
                        border = androidx.compose.foundation.BorderStroke(1.dp, IndBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Who is logging this payment?", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = IndTextSecondary)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = paymentRole == "PAID",
                                    onClick = { paymentRole = "PAID" },
                                    label = { Text("I Paid (Needs Approval)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = paymentRole == "RECEIVED",
                                    onClick = { paymentRole = "RECEIVED" },
                                    label = { Text("I Received (Direct)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Text(
                                text = if (paymentRole == "PAID")
                                    "• Awaiting ${loan.counterpartyName}'s approval with proof. Deducted once verified."
                                    else "• You confirm receiving ₹. Deducted from balance immediately.",
                                fontSize = 10.sp,
                                color = if (paymentRole == "PAID") Color(0xFFB45309) else IndGreenDark
                            )
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = {
                            amountText = it
                            errorText = null
                        },
                        label = { Text("Payment Amount ($currencySymbol)") },
                        leadingIcon = { Text(currencySymbol, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp), color = IndGreenDark) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                item {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Payment Note / Mode") },
                        placeholder = { Text("e.g. PhonePe UPI, Cash, Bank IMPS") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Multi-file Proof Attachment Section (Requirement #2)
                item {
                    MultiProofAttachmentSection(
                        proofFiles = attachedProofs,
                        onFilesChanged = { attachedProofs = it },
                        onViewFile = { file ->
                            viewingProofJson = com.example.util.ProofStorageHelper.encodeProofFiles(listOf(file))
                        }
                    )
                }

                if (errorText != null) {
                    item {
                        Text(errorText!!, color = IndRed, style = MaterialTheme.typography.bodySmall)
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                val amt = amountText.toDoubleOrNull()
                                if (amt == null || amt <= 0) {
                                    errorText = "Please enter a valid amount"
                                } else {
                                    val encodedProofJson = com.example.util.ProofStorageHelper.encodeProofFiles(attachedProofs)
                                    val primaryLocal = attachedProofs.firstOrNull()?.let { com.example.util.ProofStorageHelper.ensureLocalPath(context, it) } ?: ""
                                    val proofPayload = if (encodedProofJson.isNotBlank()) encodedProofJson else primaryLocal
                                    val finalNote = if (selectedTarget != "COMBINED") "[$selectedTarget] ${note.trim()}" else note.trim()
                                    onSavePayment(amt, finalNote, proofPayload, paymentRole == "RECEIVED")
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IndGreen, contentColor = Color.White)
                        ) {
                            Text("Record Payment", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeleteConfirmationDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = IndRed, modifier = Modifier.size(24.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Text(message, style = MaterialTheme.typography.bodyMedium, color = IndTextSecondary)
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = IndRed, contentColor = Color.White),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Delete Record", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Cancel", color = IndTextSecondary)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanDetailAndLedgerSheet(
    loan: LoanTransaction,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onEditLoan: () -> Unit,
    onApproveLoan: (Boolean) -> Unit,
    onRequestSettlement: (proofUri: String) -> Unit,
    onConfirmSettlement: (Boolean) -> Unit,
    onAddPayment: (amount: Double, note: String, proofUri: String) -> Unit,
    onSendReminder: () -> Unit,
    onDeleteLoan: () -> Unit,
    onDeletePayment: ((loanId: Long, paymentId: String) -> Unit)? = null,
    userProfile: UserProfile? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isGiven = loan.type == LoanType.GIVEN.name
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val remainingBalance = (loan.totalAmount - loan.settledAmount).coerceAtLeast(0.0)

    val payments = remember(loan.paymentHistoryJson) {
        com.example.data.repository.FinMoneyRepository.parsePaymentHistory(loan.paymentHistoryJson)
    }

    var showPaymentDialog by remember { mutableStateOf(false) }
    var showFullProofDialog by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showSimulateCounterpartyApproval by remember { mutableStateOf(false) }
    var paymentToDelete by remember { mutableStateOf<PaymentRecord?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = IndSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Header Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isGiven) "Money Lent (Given)" else "Money Borrowed (Taken)",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = IndTextPrimary
                        )
                        Text(
                            text = "Counterparty: ${loan.counterpartyName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = IndTextSecondary
                        )
                    }

                    ApprovalStatusBadge(status = loan.status)
                }
            }

            // Summary Card
            item {
                IndCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = IndCardSecondary
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Principal Amount", style = MaterialTheme.typography.labelSmall, color = IndTextSecondary)
                                Text(
                                    text = "$currencySymbol${"%,.0f".format(loan.totalAmount)}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isGiven) IndGreenDark else IndRed
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text("Remaining Due", style = MaterialTheme.typography.labelSmall, color = IndTextSecondary)
                                Text(
                                    text = "$currencySymbol${"%,.0f".format(remainingBalance)}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (remainingBalance > 0) Color(0xFFB45309) else IndGreenDark
                                )
                            }
                        }

                        // Dates & Details
                        HorizontalDivider(color = IndBorderSubtle)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Start Date", style = MaterialTheme.typography.labelSmall, color = IndTextSecondary)
                                Text(dateFormatter.format(Date(loan.startDateTimestamp)), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }

                            if (loan.dueDateTimestamp != null) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Due Date", style = MaterialTheme.typography.labelSmall, color = IndTextSecondary)
                                    Text(dateFormatter.format(Date(loan.dueDateTimestamp)), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = IndBlue)
                                }
                            }
                        }

                        if (loan.interestRatePercent > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Interest Rate", style = MaterialTheme.typography.labelSmall, color = IndTextSecondary)
                                Text("${loan.interestRatePercent}% ${if (loan.isMonthlyInterest) "pm" else "pa"}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (loan.note.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Purpose", style = MaterialTheme.typography.labelSmall, color = IndTextSecondary)
                                Text(loan.note, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        // Proof Attachment Thumbnail if available (Requirement #9)
                        if (loan.proofUri.isNotBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(IndBackground)
                                    .clickable { showFullProofDialog = loan.proofUri }
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Filled.Image, contentDescription = null, tint = IndGreen, modifier = Modifier.size(16.dp))
                                    Text("Payment Receipt / Proof Attached", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = IndGreenDark)
                                }
                                Text("View Proof", style = MaterialTheme.typography.labelSmall, color = IndBlue, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Mutual Party Status & Counterparty Verification Section (Requirements #5 & #6)
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = if (loan.status == ApprovalStatus.PENDING_APPROVAL.name || loan.status == ApprovalStatus.PENDING_SETTLEMENT.name) IndAmberLight else IndCardSecondary,
                    border = androidx.compose.foundation.BorderStroke(1.dp, IndBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (loan.status == ApprovalStatus.APPROVED.name) Icons.Filled.CheckCircle else Icons.Filled.Shield,
                                contentDescription = null,
                                tint = if (loan.status == ApprovalStatus.APPROVED.name) IndGreen else Color(0xFFB45309),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = when (loan.status) {
                                    ApprovalStatus.PENDING_APPROVAL.name -> "Two-Sided Approval Pending"
                                    ApprovalStatus.APPROVED.name -> "Active • Mutually Approved"
                                    ApprovalStatus.PENDING_SETTLEMENT.name -> "Settlement Approval Pending"
                                    ApprovalStatus.SETTLED.name -> "Full Settlement Confirmed"
                                    else -> "Status: ${loan.status}"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (loan.status == ApprovalStatus.APPROVED.name) IndGreenDark else Color(0xFF92400E)
                            )
                        }

                        if (loan.status == ApprovalStatus.PENDING_APPROVAL.name) {
                            Text(
                                text = "Awaiting verification from ${loan.counterpartyName}. Once approved, this record is permanently active and locked from tampering.",
                                style = MaterialTheme.typography.bodySmall,
                                color = IndTextSecondary
                            )

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = IndAmberLight,
                                border = androidx.compose.foundation.BorderStroke(1.dp, IndAmber.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Filled.Schedule, contentDescription = null, tint = IndAmber, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = "Awaiting ${loan.counterpartyName}'s Approval",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color(0xFFB45309)
                                        )
                                    }
                                    Text(
                                        text = if (loan.counterpartyContact.isNotBlank()) "Target party (+91 ${loan.counterpartyContact}) must approve on their registered FinMoney app." else "Target party must approve on their app.",
                                        fontSize = 11.sp,
                                        color = IndTextSecondary
                                    )
                                    Button(
                                        onClick = onSendReminder,
                                        modifier = Modifier.fillMaxWidth().height(36.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = IndNavyHeader),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Remind ${loan.counterpartyName} to Verify", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else if (loan.status == ApprovalStatus.PENDING_SETTLEMENT.name) {
                            val currentName = userProfile?.name?.trim() ?: "You"
                            val isRequester = loan.settlementRequestedBy.equals(currentName, ignoreCase = true) ||
                                              (loan.settlementRequestedBy == "You" && currentName == "You")

                            if (isRequester) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Settlement requested by You. Awaiting counterparty confirmation.",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFB45309)
                                    )
                                    Text("Both parties must mutually confirm before this record is marked settled.", fontSize = 11.sp, color = IndTextSecondary)
                                    OutlinedButton(
                                        onClick = { onConfirmSettlement(false) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = IndTextSecondary)
                                    ) {
                                        Text("Cancel Settlement Request")
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Settlement requested by ${loan.settlementRequestedBy.ifBlank { "Party" }}. Do you confirm all dues are cleared?",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = IndTextPrimary
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { onConfirmSettlement(false) },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = IndRed)
                                        ) {
                                            Text("Decline")
                                        }
                                        Button(
                                            onClick = { onConfirmSettlement(true) },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = IndGreen, contentColor = Color.White)
                                        ) {
                                            Text("Approve & Settle", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Quick Actions: Remind, Log Payment, Settle, Edit (Requirement #1, #3, #8)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Actions & Reminders", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = IndTextPrimary)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Notify / Remind button (Requirement #1 & #3)
                        Button(
                            onClick = {
                                onSendReminder()
                                if (loan.counterpartyContact.isNotBlank()) {
                                    val msg = "Hi ${loan.counterpartyName}, friendly reminder regarding the ${if (isGiven) "loan of $currencySymbol${loan.totalAmount.toInt()}" else "borrowed amount"} on FinMoney."
                                    val sendIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                        data = android.net.Uri.parse("sms:${loan.counterpartyContact}?body=${android.net.Uri.encode(msg)}")
                                    }
                                    try {
                                        context.startActivity(sendIntent)
                                    } catch (e: Exception) {
                                        // Ignore
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IndAmber, contentColor = Color.White)
                        ) {
                            Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Send Reminder", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        // Edit Button (Requirement #8: only until approved)
                        if (loan.status == ApprovalStatus.PENDING_APPROVAL.name) {
                            OutlinedButton(
                                onClick = onEditLoan,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = IndBlue)
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit Record", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = IndBackground,
                                modifier = Modifier.weight(1f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, IndBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Filled.Lock, contentDescription = null, tint = IndTextMuted, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Locked (Approved)", fontSize = 11.sp, color = IndTextMuted, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Log Payment and Settle Buttons (if approved and active)
                    if (loan.status == ApprovalStatus.APPROVED.name) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showPaymentDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = IndGreen, contentColor = Color.White)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Log Payment", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            Button(
                                onClick = { onRequestSettlement("") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = IndBlue, contentColor = Color.White)
                            ) {
                                Icon(Icons.Filled.Handshake, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Settle Up", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Historical Payment Ledger Section (Requirement #4)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Historical Payment Ledger (${payments.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = IndTextPrimary
                        )
                        Text(
                            text = "Total Paid: $currencySymbol${"%,.0f".format(loan.settledAmount)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = IndGreenDark
                        )
                    }

                    if (payments.isEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = IndBackground,
                            border = androidx.compose.foundation.BorderStroke(1.dp, IndBorderSubtle)
                        ) {
                            Text(
                                text = "No payments logged yet. Tap 'Log Payment' above when a partial repayment or installment is made.",
                                style = MaterialTheme.typography.bodySmall,
                                color = IndTextMuted,
                                modifier = Modifier.padding(14.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            payments.forEachIndexed { idx, p ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    color = IndSurface,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, IndBorder)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(IndGreenLight),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Filled.Check, contentDescription = null, tint = IndGreenDark, modifier = Modifier.size(16.dp))
                                            }
                                            Column {
                                                Text(
                                                    text = "$currencySymbol${"%,.0f".format(p.amount)}",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = IndGreenDark
                                                )
                                                Text(
                                                    text = "${p.note.ifBlank { "Installment" }} • ${dateFormatter.format(Date(p.paymentDateTimestamp))}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = IndTextSecondary,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            val proofPayload = if (p.proofFilesJson.isNotBlank()) p.proofFilesJson else p.proofUri
                                            if (proofPayload.isNotBlank()) {
                                                val proofFiles = com.example.util.ProofStorageHelper.parseProofFiles(proofPayload)
                                                val firstFile = proofFiles.firstOrNull()
                                                val localThumb = firstFile?.let { com.example.util.ProofStorageHelper.ensureLocalPath(context, it) } ?: ""

                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFF0F172A),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, IndBorder),
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .clickable { showFullProofDialog = proofPayload }
                                                ) {
                                                    if (firstFile?.isPdf == true) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(Icons.Filled.PictureAsPdf, contentDescription = null, tint = IndRed, modifier = Modifier.size(18.dp))
                                                        }
                                                    } else {
                                                        AsyncImage(
                                                            model = if (localThumb.isNotBlank()) localThumb else firstFile?.cloudBase64 ?: "",
                                                            contentDescription = "Proof",
                                                            modifier = Modifier.fillMaxSize(),
                                                            contentScale = ContentScale.Crop
                                                        )
                                                    }
                                                }
                                            }

                                            if (onDeletePayment != null) {
                                                IconButton(
                                                    onClick = { paymentToDelete = p },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete Payment Record", tint = IndRed, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Delete Record Button with Confirmation (Requirement #7)
            item {
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = IndRed),
                    border = androidx.compose.foundation.BorderStroke(1.dp, IndRed.copy(alpha = 0.4f))
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete Transaction Record", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Record Payment Dialog
    if (showPaymentDialog) {
        RecordPaymentDialog(
            loan = loan,
            currencySymbol = currencySymbol,
            onDismiss = { showPaymentDialog = false },
            onSavePayment = { amount, note, proofUri, _ ->
                onAddPayment(amount, note, proofUri)
                showPaymentDialog = false
            }
        )
    }

    // Full Proof Viewer Dialog
    if (showFullProofDialog != null) {
        FullScreenImageViewerDialog(
            proofUri = showFullProofDialog!!,
            onDismiss = { showFullProofDialog = null }
        )
    }

    // Payment Deletion Dialog
    if (paymentToDelete != null) {
        val target = paymentToDelete!!
        DeleteConfirmationDialog(
            title = "Delete Payment Record?",
            message = "Are you sure you want to delete this payment of $currencySymbol${"%,.0f".format(target.amount)}? The total settled amount will be recalculated.",
            onConfirm = {
                onDeletePayment?.invoke(loan.id, target.id)
                paymentToDelete = null
            },
            onDismiss = { paymentToDelete = null }
        )
    }

    // Delete Confirmation Dialog (Requirement #7)
    if (showDeleteConfirm) {
        DeleteConfirmationDialog(
            title = "Delete Khaata Record?",
            message = "Are you sure you want to delete this record with ${loan.counterpartyName} of $currencySymbol${loan.totalAmount.toInt()}? All historical ledger entries will be permanently removed.",
            onConfirm = {
                showDeleteConfirm = false
                onDeleteLoan()
                onDismiss()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    // Counterparty Simulator / Verification Dialog
    if (showSimulateCounterpartyApproval) {
        AlertDialog(
            onDismissRequest = { showSimulateCounterpartyApproval = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = IndNavyHeader)
                    Text("Verify as ${loan.counterpartyName}")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Reviewing ${if (isGiven) "loan of $currencySymbol${loan.totalAmount.toInt()} received from You" else "borrow request of $currencySymbol${loan.totalAmount.toInt()} given to You"}.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Does ${loan.counterpartyName} confirm this record?",
                        style = MaterialTheme.typography.bodySmall,
                        color = IndTextSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSimulateCounterpartyApproval = false
                        onApproveLoan(true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndGreen, contentColor = Color.White)
                ) {
                    Text("Approve & Confirm")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showSimulateCounterpartyApproval = false
                        onApproveLoan(false)
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = IndRed)
                ) {
                    Text("Decline / Dispute")
                }
            }
        )
    }
}

@Composable
fun ProfileDialog(
    userProfile: UserProfile?,
    onDismiss: () -> Unit,
    onSaveProfile: (firstName: String, lastName: String, phoneNumber: String, email: String, profilePicUri: String) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val initialFirst = remember(userProfile) {
        if (!userProfile?.firstName.isNullOrBlank()) userProfile!!.firstName
        else userProfile?.name?.split(" ")?.getOrNull(0) ?: ""
    }
    val initialLast = remember(userProfile) {
        if (!userProfile?.lastName.isNullOrBlank()) userProfile!!.lastName
        else userProfile?.name?.split(" ")?.drop(1)?.joinToString(" ") ?: ""
    }

    var firstName by remember { mutableStateOf(initialFirst) }
    var lastName by remember { mutableStateOf(initialLast) }
    var phoneNumber by remember { mutableStateOf(userProfile?.phoneNumber ?: "") }
    var email by remember { mutableStateOf(userProfile?.email ?: "") }
    var profilePicUri by remember { mutableStateOf(userProfile?.profilePicUri ?: "") }
    var errorText by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val localPath = com.example.util.ProofStorageHelper.persistProofImage(context, uri)
            profilePicUri = localPath
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = IndSurface)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "User Profile",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = IndTextPrimary
                            )
                            Text(
                                text = "Manage your Khaata identity & contact details",
                                style = MaterialTheme.typography.bodySmall,
                                color = IndTextSecondary
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = IndTextSecondary)
                        }
                    }
                }

                // Profile Photo & Avatar Selector
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(IndNavyHeader)
                                .border(2.dp, IndBlue, CircleShape)
                                .clickable {
                                    photoPickerLauncher.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (profilePicUri.isNotBlank()) {
                                AsyncImage(
                                    model = profilePicUri,
                                    contentDescription = "Profile Photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                val initials = (firstName.take(1) + lastName.take(1)).ifBlank { "U" }.uppercase()
                                Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(14.dp), tint = IndBlue)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Upload Photo", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IndBlue)
                            }

                            if (profilePicUri.isNotBlank()) {
                                TextButton(
                                    onClick = { profilePicUri = "" },
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("Remove", fontSize = 11.sp, color = IndRed)
                                }
                            }
                        }

                        // Sample Avatars
                        Text("Or choose avatar:", fontSize = 10.sp, color = IndTextMuted)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150",
                                "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150",
                                "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=150",
                                "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=150"
                            ).forEach { sampleUrl ->
                                Surface(
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clickable { profilePicUri = sampleUrl }
                                        .border(
                                            if (profilePicUri == sampleUrl) 2.dp else 1.dp,
                                            if (profilePicUri == sampleUrl) IndBlue else IndBorder,
                                            CircleShape
                                        )
                                ) {
                                    AsyncImage(model = sampleUrl, contentDescription = null, contentScale = ContentScale.Crop)
                                }
                            }
                        }
                    }
                }

                // First and Last Name
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = firstName,
                            onValueChange = { firstName = it; errorText = null },
                            label = { Text("First Name *") },
                            placeholder = { Text("e.g. Rahul") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = lastName,
                            onValueChange = { lastName = it },
                            label = { Text("Last Name") },
                            placeholder = { Text("e.g. Sharma") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                // Phone Number (Required for Peer Khaata)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it.filter { ch -> ch.isDigit() || ch == '+' || ch == ' ' }; errorText = null },
                            label = { Text("Phone Number * (Required)") },
                            placeholder = { Text("10-digit mobile number") },
                            leadingIcon = {
                                Text("+91 ", fontWeight = FontWeight.Bold, color = IndTextPrimary, modifier = Modifier.padding(start = 12.dp))
                            },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        Text(
                            text = "Used to match and sync mutual agreements with your contacts.",
                            fontSize = 11.sp,
                            color = IndTextSecondary
                        )
                    }
                }

                // Email Address with Verified Badge
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email Address") },
                            placeholder = { Text("name@example.com") },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = IndGreenLight,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Filled.Verified, contentDescription = null, tint = IndGreenDark, modifier = Modifier.size(14.dp))
                                Text(
                                    text = "Email Verified ✓",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = IndGreenDark
                                )
                            }
                        }
                    }
                }

                if (errorText != null) {
                    item {
                        Text(errorText!!, color = IndRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Actions: Save Profile & Sign Out
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (firstName.isBlank()) {
                                    errorText = "Please enter your first name."
                                    return@Button
                                }
                                val cleanPhone = phoneNumber.filter { it.isDigit() }
                                if (cleanPhone.length < 10) {
                                    errorText = "Please enter a valid 10-digit phone number."
                                    return@Button
                                }
                                onSaveProfile(firstName.trim(), lastName.trim(), cleanPhone, email.trim(), profilePicUri.trim())
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IndNavyHeader)
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Profile Changes", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onSignOut()
                            },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = IndRed),
                            border = androidx.compose.foundation.BorderStroke(1.dp, IndRed.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp), tint = IndRed)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sign Out", fontWeight = FontWeight.Bold, color = IndRed)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InviteCounterpartyDialog(
    counterpartyName: String,
    counterpartyPhone: String,
    amount: Double,
    isGiven: Boolean,
    currencySymbol: String,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val formattedAmount = "$currencySymbol${"%,.0f".format(amount)}"
    val appLink = "https://ais-pre-4yckxjiotgyjrlldfblzwu-928901214211.asia-southeast1.run.app"
    val actionVerb = if (isGiven) "lent you" else "borrowed from you"
    val shareMessage = "Hi $counterpartyName, I've recorded a transaction on FinMoney: I $actionVerb $formattedAmount. Please install the FinMoney app to view the shared ledger and digitally sign: $appLink"

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = IndSurface),
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(IndBlueLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = null,
                        tint = IndBlue,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = "Invite $counterpartyName to FinMoney",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = IndTextPrimary,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "$counterpartyName (+91 $counterpartyPhone) is not yet registered. Invite them via WhatsApp, SMS, or any social app so they can install, review, and confirm this $formattedAmount agreement.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = IndTextSecondary,
                    textAlign = TextAlign.Center
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = IndBackground,
                    border = androidx.compose.foundation.BorderStroke(1.dp, IndBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Invitation Message Preview:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = IndTextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = shareMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = IndNavyHeader
                        )
                    }
                }

                // WhatsApp Button
                Button(
                    onClick = {
                        try {
                            val cleanPhone = counterpartyPhone.filter { it.isDigit() }
                            val fullPhone = if (cleanPhone.length == 10) "91$cleanPhone" else cleanPhone
                            val uri = Uri.parse("https://api.whatsapp.com/send?phone=$fullPhone&text=${Uri.encode(shareMessage)}")
                            val intent = Intent(Intent.ACTION_VIEW, uri)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareMessage)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share via"))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = IndGreenDark)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Invite via WhatsApp", fontWeight = FontWeight.Bold)
                }

                // SMS Button
                OutlinedButton(
                    onClick = {
                        try {
                            val uri = Uri.parse("sms:$counterpartyPhone?body=${Uri.encode(shareMessage)}")
                            val intent = Intent(Intent.ACTION_VIEW, uri)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareMessage)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share via"))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, IndBlue)
                ) {
                    Icon(Icons.Filled.Sms, contentDescription = null, tint = IndBlue, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Send SMS Message", color = IndBlue, fontWeight = FontWeight.Bold)
                }

                // Social / Share sheet
                OutlinedButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareMessage)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Invite via Social / Messaging"))
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share via Other Apps", fontWeight = FontWeight.Bold)
                }

                // Close
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done / Invite Later", color = IndTextSecondary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
