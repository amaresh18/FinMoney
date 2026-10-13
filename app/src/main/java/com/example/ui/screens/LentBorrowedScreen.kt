package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LoanApprovalStatus
import com.example.data.model.LoanTransaction
import com.example.data.model.LoanType
import com.example.ui.FinMoneyViewModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LentBorrowedScreen(
    viewModel: FinMoneyViewModel,
    onBack: () -> Unit = {}
) {
    BackHandler { onBack() }

    val loans by viewModel.allLoans.collectAsState()
    val currencySymbol by viewModel.currencySymbol.collectAsState()

    var showAddLoanDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("LENT") }
    var notesInput by remember { mutableStateOf("") }

    var editingLoan by remember { mutableStateOf<LoanTransaction?>(null) }
    var editAmountStr by remember { mutableStateOf("") }
    var editNotesStr by remember { mutableStateOf("") }

    val totalLent = remember(loans) {
        loans.filter { (it.type == LoanType.LENT || it.type == LoanType.GIVEN) && it.status != LoanApprovalStatus.REJECTED }.sumOf { it.totalAssetAmount() }
    }
    val totalBorrowed = remember(loans) {
        loans.filter { (it.type == LoanType.BORROWED || it.type == LoanType.TAKEN) && it.status != LoanApprovalStatus.REJECTED }.sumOf { it.totalAssetAmount() }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddLoanDialog = true },
                containerColor = IndBlue,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Record")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Record", fontWeight = FontWeight.Bold)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(IndBackground)
                .padding(16.dp)
        ) {
            // Summary Banner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = IndGreenLight,
                    border = androidx.compose.foundation.BorderStroke(1.dp, IndGreen.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Total Lent (Receivable)", fontSize = 11.sp, color = IndGreenDark, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("$currencySymbol${"%,.0f".format(totalLent)}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = IndGreenDark)
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = IndRed.copy(alpha = 0.1f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, IndRed.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Total Borrowed (Payable)", fontSize = 11.sp, color = IndRed, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("$currencySymbol${"%,.0f".format(totalBorrowed)}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = IndRed)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Lent & Borrowed Ledger (Mutual & Approval)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = IndNavyHeader)
            Spacer(modifier = Modifier.height(10.dp))

            if (loans.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(48.dp), tint = IndTextSecondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No records in ledger", fontWeight = FontWeight.Bold, color = IndTextPrimary)
                        Text("Tap + to record money lent or borrowed", fontSize = 12.sp, color = IndTextSecondary)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(loans, key = { it.id }) { loan ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = IndSurface,
                            shadowElevation = 1.dp,
                            border = androidx.compose.foundation.BorderStroke(1.dp, IndBorderSubtle)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                val counterparty = loan.personName.ifBlank { loan.counterpartyName.ifBlank { "Party #${loan.id}" } }
                                val isLent = loan.type == LoanType.LENT || loan.type == LoanType.GIVEN

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isLent) IndGreenLight else IndRed.copy(alpha = 0.1f)
                                        ) {
                                            Text(
                                                text = if (isLent) "LENT" else "BORROWED",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                color = if (isLent) IndGreenDark else IndRed,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }

                                        Text(counterparty, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = IndTextPrimary)
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(
                                            onClick = {
                                                editingLoan = loan
                                                editAmountStr = loan.totalAmount.toString()
                                                editNotesStr = loan.notes
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit Record", tint = IndBlue, modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                viewModel.requestDeleteApproval(loan.id)
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Record", tint = IndRed, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$currencySymbol${"%,.0f".format(loan.totalAmount)}",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp,
                                        color = if (isLent) IndGreenDark else IndRed
                                    )

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = when (loan.status) {
                                            LoanApprovalStatus.APPROVED -> IndGreenLight
                                            LoanApprovalStatus.SETTLED -> IndBlueLight
                                            LoanApprovalStatus.REJECTED -> IndRed.copy(alpha = 0.1f)
                                            else -> Color(0xFFFEF3C7)
                                        }
                                    ) {
                                        Text(
                                            text = loan.status.name,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (loan.status) {
                                                LoanApprovalStatus.APPROVED -> IndGreenDark
                                                LoanApprovalStatus.SETTLED -> IndBlue
                                                LoanApprovalStatus.REJECTED -> IndRed
                                                else -> Color(0xFFD97706)
                                            },
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                if (loan.notes.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Note: ${loan.notes}", fontSize = 12.sp, color = IndTextSecondary)
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Approval & Settlement Action Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (loan.counterpartyApproved) "✓ Mutually Approved" else "⏳ Pending Counterparty Approval",
                                        fontSize = 11.sp,
                                        color = if (loan.counterpartyApproved) IndGreenDark else Color(0xFFD97706),
                                        fontWeight = FontWeight.Medium
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (loan.status == LoanApprovalStatus.PENDING || loan.status == LoanApprovalStatus.PENDING_APPROVAL) {
                                            OutlinedButton(
                                                onClick = { viewModel.rejectLoan(loan.id) },
                                                modifier = Modifier.height(32.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text("Reject", fontSize = 11.sp)
                                            }
                                            Button(
                                                onClick = { viewModel.approveLoan(loan.id) },
                                                modifier = Modifier.height(32.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = IndGreen),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                            ) {
                                                Text("Approve", fontSize = 11.sp, color = Color.White)
                                            }
                                        } else if (loan.status == LoanApprovalStatus.APPROVED) {
                                            Button(
                                                onClick = { viewModel.settleLoan(loan.id) },
                                                modifier = Modifier.height(32.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = IndBlue),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                            ) {
                                                Text("Settle", fontSize = 11.sp, color = Color.White)
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
    }

    if (showAddLoanDialog) {
        AlertDialog(
            onDismissRequest = { showAddLoanDialog = false },
            title = { Text("Add Loan Record", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Counterparty Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text("Amount ($currencySymbol)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = notesInput,
                        onValueChange = { notesInput = it },
                        label = { Text("Notes / Purpose") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = type == "LENT",
                            onClick = { type = "LENT" },
                            label = { Text("Lent (Given)") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = type == "BORROWED",
                            onClick = { type = "BORROWED" },
                            label = { Text("Borrowed (Taken)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = amountStr.toDoubleOrNull() ?: 0.0
                        if (name.isNotBlank() && amt > 0.0) {
                            viewModel.addLoan(name, amt, type, notesInput)
                            showAddLoanDialog = false
                            name = ""
                            amountStr = ""
                            notesInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndBlue)
                ) {
                    Text("Save Record")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddLoanDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (editingLoan != null) {
        AlertDialog(
            onDismissRequest = { editingLoan = null },
            title = { Text("Edit Loan Record (Approval Request)", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = editAmountStr,
                        onValueChange = { editAmountStr = it },
                        label = { Text("Updated Amount ($currencySymbol)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editNotesStr,
                        onValueChange = { editNotesStr = it },
                        label = { Text("Updated Notes") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = editAmountStr.toDoubleOrNull() ?: 0.0
                        val loan = editingLoan
                        if (loan != null && amt > 0.0) {
                            viewModel.requestEditApproval(loan.id, amt, editNotesStr)
                            editingLoan = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndBlue)
                ) {
                    Text("Submit Edit Request")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingLoan = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun LoanTransaction.totalAssetAmount(): Double {
    return if (this.totalAmount > 0.0) this.totalAmount else 0.0
}
