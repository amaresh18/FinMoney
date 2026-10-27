package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.BankBalanceItem
import com.example.data.model.MonthlyFinanceSummary
import com.example.data.model.isActualCreditCardTransaction
import com.example.data.repository.FinMoneyRepository
import com.example.service.BankSyncManager
import com.example.ui.FinMoneyViewModel
import com.example.ui.components.BankAutoSyncStatusBanner
import com.example.ui.components.BankSyncDisclosureDialog
import com.example.ui.components.BankTransactionsHistorySheet
import com.example.ui.components.BrandLogo
import com.example.ui.components.IndCard
import com.example.ui.components.IndDialog
import com.example.ui.components.IndOutlinedTextField
import com.example.ui.components.NotificationAccessRequestDialog
import com.example.ui.theme.*
import java.util.UUID
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountReconciliationScreen(
    viewModel: FinMoneyViewModel,
    onBack: () -> Unit
) {
    val currentMonth by viewModel.currentMonth.collectAsStateWithLifecycle()
    val currentYear by viewModel.currentYear.collectAsStateWithLifecycle()
    val currencySymbol by viewModel.currencySymbol.collectAsStateWithLifecycle()
    val currentSalaryRecord by viewModel.currentSalaryRecord.collectAsStateWithLifecycle()
    val currentDebits by viewModel.currentDebits.collectAsStateWithLifecycle()
    val currentReconciliation by viewModel.currentReconciliation.collectAsStateWithLifecycle()
    val monthlySummary by viewModel.monthlySummary.collectAsStateWithLifecycle()
    val reconciliationSummary by viewModel.reconciliationSummary.collectAsStateWithLifecycle()

    val monthName = remember(currentMonth) { FinMoneyViewModel.getMonthName(currentMonth) }

    val summary = monthlySummary ?: MonthlyFinanceSummary()

    // Read income sources and credits live from ViewModel to align with Dashboard Screen exactly
    val incomeSources = summary.incomeSources
    val totalCredits = reconciliationSummary.totalInflows

    // Read debits live
    val bankDebits = remember(currentDebits) { currentDebits.filter { !it.isActualCreditCardTransaction() } }
    val creditCardDebits = remember(currentDebits) { currentDebits.filter { it.isActualCreditCardTransaction() } }

    val paidDebits = remember(currentDebits) { currentDebits.filter { it.isPaid } }
    val unpaidDebits = remember(currentDebits) { currentDebits.filter { !it.isPaid } }

    val paidBankDebits = remember(paidDebits) { paidDebits.filter { !it.isActualCreditCardTransaction() } }
    val paidCcDebits = remember(paidDebits) { paidDebits.filter { it.isActualCreditCardTransaction() } }

    var countOnlyPaidDebits by remember { mutableStateOf(false) }

    // Direct Bank Outflows (Physically deducted from bank accounts)
    val totalBankDebits = if (countOnlyPaidDebits) reconciliationSummary.totalPaidBankDebits else reconciliationSummary.totalBankDebits

    // Credit Card Spends (Charged to card, but cash still physically in bank!)
    val totalCcDebits = if (countOnlyPaidDebits) reconciliationSummary.totalPaidCcDebits else reconciliationSummary.totalCcDebits

    // Combined all outflows (Matches Dashboard total debits)
    val totalAllDebits = if (countOnlyPaidDebits) reconciliationSummary.totalPaidOutflows else reconciliationSummary.totalOutflows

    // Bank list state initialized with default bank item so UI renders immediately on first frame
    var bankBalances by remember {
        mutableStateOf(
            listOf(
                BankBalanceItem(
                    id = "default_bank_1",
                    bankName = "Primary Salary Account (HDFC/SBI/ICICI)",
                    accountNumberLast4 = "1234",
                    startingBalance = 0.0,
                    endingBalance = 0.0
                )
            )
        )
    }
    var manualAdjustmentText by remember { mutableStateOf("") }
    var adjustmentNote by remember { mutableStateOf("") }
    var showAddBankDialog by remember { mutableStateOf(false) }

    var showBreakdownSection by remember { mutableStateOf(false) }
    var showDisclosureDialog by remember { mutableStateOf(false) }
    var showTransactionsSheet by remember { mutableStateOf(false) }

    val bankTransactions by viewModel.bankTransactions.collectAsStateWithLifecycle()
    val isNotificationAccessGranted by viewModel.isNotificationAccessGranted.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.checkNotificationAccess(context)
    }

    // Load persisted reconciliation from database if available
    LaunchedEffect(currentReconciliation) {
        val rec = currentReconciliation
        if (rec != null && rec.bankBalancesJson.isNotBlank() && rec.bankBalancesJson != "[]") {
            val parsed = FinMoneyRepository.parseBankBalances(rec.bankBalancesJson)
            if (parsed.isNotEmpty()) {
                bankBalances = parsed
                manualAdjustmentText = if (rec.manualAdjustment != 0.0) rec.manualAdjustment.toString() else ""
                adjustmentNote = rec.adjustmentNote
            }
        }
    }


    // Calculations
    val totalStartingBalance = remember(bankBalances) { bankBalances.sumOf { it.startingBalance } }
    val totalEndingBalance = remember(bankBalances) { bankBalances.sumOf { it.endingBalance } }
    val manualAdjustment = manualAdjustmentText.toDoubleOrNull() ?: 0.0

    // Core Reconciliation Equation for Physical Bank Accounts:
    // Expected Bank Ending = Starting Balance + Credits - Direct Bank Debits (excl. Credit Card) + Adjustment
    // NOTE: Credit Card charges are NOT subtracted here because the money is still physically in the bank account!
    val expectedEndingBalance = totalStartingBalance + totalCredits - totalBankDebits + manualAdjustment
    val difference = totalEndingBalance - expectedEndingBalance
    val isPerfectMatch = abs(difference) < 1.0

    // Net Financial Position after settling upcoming Credit Card dues:
    val expectedNetEndingBalance = expectedEndingBalance - totalCcDebits
    val actualNetEndingBalance = totalEndingBalance - totalCcDebits

    Scaffold(
        containerColor = IndBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Account Reconciliation",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = IndNavyHeader
                        )
                        Text(
                            text = "Account Verification • $monthName $currentYear",
                            style = MaterialTheme.typography.labelSmall,
                            color = IndTextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("reconcile_back_btn")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = IndNavyHeader
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            android.widget.Toast.makeText(context, "Cancelled", android.widget.Toast.LENGTH_SHORT).show()
                            onBack()
                        },
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .testTag("reconcile_cancel_btn")
                    ) {
                        Text(
                            text = "Cancel",
                            fontWeight = FontWeight.SemiBold,
                            color = IndNavyHeader,
                            fontSize = 13.sp
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.saveReconciliation(
                                month = currentMonth,
                                year = currentYear,
                                bankBalances = bankBalances,
                                manualAdjustment = manualAdjustment,
                                adjustmentNote = adjustmentNote,
                                isReconciled = isPerfectMatch
                            )
                            val message = if (isPerfectMatch) "Account balanced & reconciled! ✓" else "Reconciliation progress saved"
                            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                            onBack()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPerfectMatch) IndGreenDark else IndNavyHeader
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .testTag("save_reconcile_top_btn")
                    ) {
                        Icon(
                            imageVector = if (isPerfectMatch) Icons.Filled.CheckCircle else Icons.Filled.Save,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isPerfectMatch) "Reconciled" else "Save",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = IndSurface)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = AppTheme.dimensions.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(AppTheme.dimensions.medium),
            contentPadding = PaddingValues(top = AppTheme.dimensions.medium, bottom = 48.dp)
        ) {
            // Quick Status Banner at the TOP (Clean status indicator without duplicate action buttons)
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isPerfectMatch) Color(0xFFF0FDF4) else Color(0xFFF8FAFC),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isPerfectMatch) Color(0xFF86EFAC) else IndBorderSubtle),
                    modifier = Modifier.fillMaxWidth().testTag("reconcile_top_actions_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = if (isPerfectMatch) "STATUS: BALANCED ✓" else "DISCREPANCY: $currencySymbol${"%,.0f".format(abs(difference))}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isPerfectMatch) IndGreenDark else IndNavyHeader
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (isPerfectMatch) "All entries match actual balances." else if (difference > 0) "Surplus (+₹${"%,.0f".format(difference)}) unrecorded" else "Deficit (-₹${"%,.0f".format(abs(difference))}) unrecorded",
                                fontSize = 10.5.sp,
                                color = IndTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isPerfectMatch) IndGreenLight else IndAmberLight
                        ) {
                            Text(
                                text = if (isPerfectMatch) "Balanced ✓" else "Needs Action",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isPerfectMatch) IndGreenDark else Color(0xFFB45309),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
            // 0. Top At-A-Glance Status of Account Accuracy Summary Card (Comprehensive Green Card)
            item {
                ReconciliationAtAGlanceSummaryCard(
                    currencySymbol = currencySymbol,
                    clearedCredits = totalCredits,
                    clearedCreditsCount = incomeSources.size,
                    clearedBankDebits = totalBankDebits,
                    clearedBankDebitsCount = if (countOnlyPaidDebits) paidBankDebits.size else bankDebits.size,
                    creditCardDebits = totalCcDebits,
                    creditCardDebitsCount = if (countOnlyPaidDebits) paidCcDebits.size else creditCardDebits.size,
                    netDifference = difference,
                    isPerfectMatch = isPerfectMatch,
                    totalStarting = totalStartingBalance,
                    actualEnding = totalEndingBalance,
                    expectedEnding = expectedEndingBalance,
                    actualNetEnding = actualNetEndingBalance,
                    countOnlyPaid = countOnlyPaidDebits,
                    onTogglePaidOnly = { countOnlyPaidDebits = !countOnlyPaidDebits }
                )
            }

            // Real-Time Bank Alert Auto-Sync Banner
            item {
                val latestBalance = bankTransactions.firstOrNull { it.availableBalance > 0.0 }?.availableBalance
                BankAutoSyncStatusBanner(
                    isAccessGranted = isNotificationAccessGranted,
                    recentCount = bankTransactions.size,
                    latestBalanceDetected = latestBalance,
                    onOpenDisclosure = { showDisclosureDialog = true },
                    onViewTransactions = { showTransactionsSheet = true },
                    onSimulateAlert = { viewModel.simulateBankAlert((0..4).random(), context) }
                )
            }

            // 1. Multiple Banks Section Header & List
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Bank Accounts & Wallets",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = IndNavyHeader
                        )
                        Text(
                            text = "Enter start & end statement balance per bank",
                            style = MaterialTheme.typography.labelSmall,
                            color = IndTextSecondary
                        )
                    }

                    OutlinedButton(
                        onClick = { showAddBankDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("add_bank_account_btn")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = IndBlue)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Bank", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = IndBlue)
                    }
                }
            }

            // Bank Cards
            itemsIndexed(bankBalances, key = { _, b -> b.id }) { index, bank ->
                BankBalanceCard(
                    bank = bank,
                    currencySymbol = currencySymbol,
                    onStartingBalanceChange = { newAmt ->
                        bankBalances = bankBalances.toMutableList().also {
                            it[index] = it[index].copy(startingBalance = newAmt)
                        }
                    },
                    onEndingBalanceChange = { newAmt ->
                        bankBalances = bankBalances.toMutableList().also {
                            it[index] = it[index].copy(endingBalance = newAmt)
                        }
                    },
                    onDelete = {
                        if (bankBalances.size > 1) {
                            bankBalances = bankBalances.filterIndexed { i, _ -> i != index }
                        }
                    }
                )
            }

            // 4. Zero-Duplicate Read-Only Data Inspector (Expandable)
            item {
                IndCard(
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                    backgroundColor = IndSurface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showBreakdownSection = !showBreakdownSection },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = IndBlueLight,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.SyncAlt, contentDescription = null, tint = IndBlue, modifier = Modifier.size(18.dp))
                                }
                            }
                            Column {
                                Text(
                                    text = "Auto-Synced Salary & Debits",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = IndNavyHeader
                                )
                                Text(
                                    text = "Zero duplication • Pulled live from Salary page",
                                    fontSize = 11.sp,
                                    color = IndTextSecondary
                                )
                            }
                        }

                        Icon(
                            imageVector = if (showBreakdownSection) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = "Toggle",
                            tint = IndNavyHeader
                        )
                    }

                    AnimatedVisibility(visible = showBreakdownSection) {
                        Column(
                            modifier = Modifier.padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HorizontalDivider(color = IndBorderSubtle)

                            // Credits summary
                            if (incomeSources.isNotEmpty()) {
                                incomeSources.forEach { src ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("• ${src.name} (${if (src.isReceived) "Received" else "Pending"}):", fontSize = 12.sp, color = IndTextSecondary)
                                        Text("$currencySymbol${"%,.0f".format(src.amount)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (src.isReceived) IndGreenDark else IndTextMuted)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total Inflows / Credits:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = IndNavyHeader)
                                Text("$currencySymbol${"%,.0f".format(totalCredits)}", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = IndGreenDark)
                            }

                            HorizontalDivider(color = IndBorderSubtle)

                            // Debits summary: Direct Bank Outflows vs Credit Card Charges
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Direct Bank Debits (${paidBankDebits.size} paid):", fontSize = 12.sp, color = IndTextSecondary)
                                Text("$currencySymbol${"%,.0f".format(totalBankDebits)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = IndNavyHeader)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Filled.CreditCard, contentDescription = null, tint = IndPurple, modifier = Modifier.size(13.dp))
                                    Text("Credit Card Debits (${paidCcDebits.size} charged):", fontSize = 12.sp, color = IndPurple)
                                }
                                Text("$currencySymbol${"%,.0f".format(totalCcDebits)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = IndPurple)
                            }

                            if (unpaidDebits.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Unpaid Pending Debits (${unpaidDebits.size} items):", fontSize = 12.sp, color = IndTextSecondary)
                                    Text("$currencySymbol${"%,.0f".format(unpaidDebits.sumOf { it.amount })}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = IndAmber)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Debits Deducted in Bank Reconcile:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = IndNavyHeader)
                                Text("$currencySymbol${"%,.0f".format(totalBankDebits)}", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = IndNavyHeader)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Combined All Outflows (Bank + CC):", fontSize = 11.5.sp, color = IndTextMuted)
                                Text("$currencySymbol${"%,.0f".format(totalAllDebits)}", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = IndTextPrimary)
                            }
                        }
                    }
                }
            }

            // 5. Manual Adjustment (e.g. Bank charges / Untracked rounding)
            item {
                IndCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = IndSurface
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Filled.Tune, contentDescription = null, tint = IndNavyHeader, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Manual Reconciliation Adjustment (Optional)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = IndNavyHeader
                            )
                        }
                        Text(
                            text = "Use this for bank interest, SMS charges, or rounding difference without altering salary records.",
                            fontSize = 11.sp,
                            color = IndTextSecondary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = manualAdjustmentText,
                                onValueChange = { manualAdjustmentText = it },
                                label = { Text("Adjustment Amount ($currencySymbol)") },
                                placeholder = { Text("e.g. -25 or 150") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = adjustmentNote,
                                onValueChange = { adjustmentNote = it },
                                label = { Text("Reason / Note") },
                                placeholder = { Text("e.g. Bank SMS Charge") },
                                modifier = Modifier.weight(1.5f),
                                singleLine = true
                            )
                        }
                    }
                }
            }

            // 6. Bottom Completion Card (scrollable, generous padding well above system navigation bar)
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = IndSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isPerfectMatch) Color(0xFF86EFAC) else IndBorderSubtle),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isPerfectMatch) "Account Balance Reconciled ✓" else "Discrepancy: $currencySymbol${"%,.0f".format(abs(difference))}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (isPerfectMatch) IndGreenDark else IndNavyHeader
                                )
                                Text(
                                    text = if (isPerfectMatch) "Every rupee accounted for across bank statement and recorded debits." else if (difference > 0) "Surplus (+₹${"%,.0f".format(difference)}) unrecorded" else "Deficit (-₹${"%,.0f".format(abs(difference))}) unrecorded",
                                    fontSize = 11.5.sp,
                                    color = IndTextSecondary
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    android.widget.Toast.makeText(context, "Cancelled", android.widget.Toast.LENGTH_SHORT).show()
                                    onBack()
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel", color = IndNavyHeader, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    viewModel.saveReconciliation(
                                        month = currentMonth,
                                        year = currentYear,
                                        bankBalances = bankBalances,
                                        manualAdjustment = manualAdjustment,
                                        adjustmentNote = adjustmentNote,
                                        isReconciled = isPerfectMatch
                                    )
                                    val message = if (isPerfectMatch) "Account balanced & reconciled! ✓" else "Reconciliation progress saved"
                                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                                    onBack()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isPerfectMatch) IndGreenDark else IndNavyHeader
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1.5f).testTag("save_reconcile_bottom_scroll_btn")
                            ) {
                                Icon(
                                    imageVector = if (isPerfectMatch) Icons.Filled.CheckCircle else Icons.Filled.Save,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isPerfectMatch) "Mark Reconciled" else "Save Progress",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Bank Account Dialog
    if (showAddBankDialog) {
        AddBankDialog(
            onDismiss = { showAddBankDialog = false },
            onAdd = { newName, last4 ->
                bankBalances = bankBalances + BankBalanceItem(
                    id = UUID.randomUUID().toString(),
                    bankName = newName,
                    accountNumberLast4 = last4,
                    startingBalance = 0.0,
                    endingBalance = 0.0
                )
                showAddBankDialog = false
            }
        )
    }

    // User-Friendly 'Notification Access' Permission Request & Settings Guidance Dialog
    if (showDisclosureDialog) {
        NotificationAccessRequestDialog(
            onDismiss = { showDisclosureDialog = false },
            onGrantClicked = {
                showDisclosureDialog = false
            },
            onSimulateTest = {
                showDisclosureDialog = false
                viewModel.simulateBankAlert(0, context)
            }
        )
    }

    // Auto-Synced Bank Transactions History Sheet
    if (showTransactionsSheet) {
        BankTransactionsHistorySheet(
            transactions = bankTransactions,
            onDismiss = { showTransactionsSheet = false },
            onDelete = { txId -> viewModel.deleteBankTransaction(txId) },
            onEdit = { tx -> viewModel.updateBankTransaction(tx) },
            onClearAll = { viewModel.clearAllBankTransactions() },
            onSimulate = { viewModel.simulateBankAlert((0..4).random(), context) },
            onScanInbox = { viewModel.scanDeviceSms(context) },
            onParseCustomAlert = { text, title -> viewModel.parseAndProcessCustomAlert(text, title, context) }
        )
    }
}

@Composable
fun MathRowItem(
    label: String,
    amountStr: String,
    icon: ImageVector,
    color: Color,
    isBold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                color = if (isBold) IndNavyHeader else IndTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = amountStr,
            fontSize = if (isBold) 13.sp else 12.sp,
            fontWeight = if (isBold) FontWeight.ExtraBold else FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun BankBalanceCard(
    bank: BankBalanceItem,
    currencySymbol: String,
    onStartingBalanceChange: (Double) -> Unit,
    onEndingBalanceChange: (Double) -> Unit,
    onDelete: () -> Unit
) {
    var startingText by remember(bank.startingBalance) {
        mutableStateOf(if (bank.startingBalance > 0) bank.startingBalance.toString().removeSuffix(".0") else "")
    }
    var endingText by remember(bank.endingBalance) {
        mutableStateOf(if (bank.endingBalance > 0) bank.endingBalance.toString().removeSuffix(".0") else "")
    }

    val netChange = bank.endingBalance - bank.startingBalance

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = IndSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, IndBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bank_card_${bank.bankName}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Bank Name, Last 4 digits, Net Change & Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Authentic Bank Logo (HDFC, SBI, ICICI, Axis, Kotak, etc.)
                    BrandLogo(
                        name = bank.bankName,
                        badgeSize = 36.dp,
                        shape = RoundedCornerShape(8.dp)
                    )

                    Column {
                        Text(
                            text = bank.bankName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = IndNavyHeader,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (bank.accountNumberLast4.isNotBlank()) {
                            Text(
                                text = "A/C ending •• ${bank.accountNumberLast4}",
                                style = MaterialTheme.typography.labelSmall,
                                color = IndTextSecondary,
                                fontSize = 10.sp
                            )
                        }
                        if (bank.lastUpdatedTimestamp > 0) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFDCFCE7),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Bolt,
                                        contentDescription = null,
                                        tint = Color(0xFF15803D),
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Text(
                                        text = "Auto-Synced",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF15803D)
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (bank.startingBalance > 0 || bank.endingBalance > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (netChange >= 0) IndGreenLight else Color(0xFFF1F5F9)
                        ) {
                            Text(
                                text = "${if (netChange >= 0) "+" else ""}$currencySymbol${"%,.0f".format(netChange)}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (netChange >= 0) IndGreenDark else IndNavyHeader,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove Bank", tint = IndTextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Input Fields Side by Side: Starting Balance & Ending Balance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = startingText,
                    onValueChange = {
                        startingText = it
                        onStartingBalanceChange(it.toDoubleOrNull() ?: 0.0)
                    },
                    label = { Text("Starting Balance ($currencySymbol)", fontSize = 11.sp) },
                    placeholder = { Text("0.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                OutlinedTextField(
                    value = endingText,
                    onValueChange = {
                        endingText = it
                        onEndingBalanceChange(it.toDoubleOrNull() ?: 0.0)
                    },
                    label = { Text("Ending Balance ($currencySymbol)", fontSize = 11.sp) },
                    placeholder = { Text("0.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
        }
    }
}

@Composable
fun AddBankDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, last4: String) -> Unit
) {
    var selectedPreset by remember { mutableStateOf("HDFC Bank") }
    var customName by remember { mutableStateOf("") }
    var last4Digits by remember { mutableStateOf("") }

    val presets = listOf(
        "HDFC Bank",
        "State Bank of India (SBI)",
        "ICICI Bank",
        "Axis Bank",
        "Kotak Mahindra Bank",
        "Punjab National Bank",
        "Bank of Baroda",
        "IndusInd Bank",
        "Cash in Hand / Wallet",
        "Other Bank"
    )

    IndDialog(
        onDismiss = onDismiss,
        title = "Add Bank Account or Wallet",
        subtitle = "Link bank account or cash wallet for balance reconciliation",
        icon = Icons.Filled.AccountBalance,
        iconTint = IndBlue,
        headerGradient = listOf(IndBlueLight, AppTheme.colors.surface),
        testTag = "add_bank_dialog",
        confirmButtonText = "Add Account",
        confirmButtonTag = "submit_bank_button",
        onConfirm = {
            val finalName = if (selectedPreset == "Other Bank") customName.ifBlank { "Custom Bank" } else selectedPreset
            onAdd(finalName, last4Digits)
        }
    ) {
        Text("Select Bank or Preset:", style = AppTheme.typography.labelSmall, color = AppTheme.colors.textSecondary)

        // Presets Grid
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            presets.chunked(2).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { preset ->
                        val isSel = selectedPreset == preset
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSel) IndNavyHeader else AppTheme.colors.cardSecondary,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) IndNavyHeader else AppTheme.colors.border),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedPreset = preset }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                BrandLogo(
                                    name = preset,
                                    badgeSize = 20.dp,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                Text(
                                    text = preset,
                                    style = AppTheme.typography.bodySmall,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSel) Color.White else AppTheme.colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        if (selectedPreset == "Other Bank") {
            IndOutlinedTextField(
                value = customName,
                onValueChange = { customName = it },
                label = "Bank / Wallet Name",
                placeholder = "e.g. Federal Bank",
                leadingIcon = Icons.Filled.AccountBalanceWallet
            )
        }

        IndOutlinedTextField(
            value = last4Digits,
            onValueChange = { inputStr -> if (inputStr.length <= 4 && inputStr.all { c -> c.isDigit() }) last4Digits = inputStr },
            label = "Last 4 digits of A/C (optional)",
            placeholder = "e.g. 4821",
            leadingIcon = Icons.Filled.Pin,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }
}

@Composable
fun ReconciliationAtAGlanceSummaryCard(
    currencySymbol: String,
    clearedCredits: Double,
    clearedCreditsCount: Int,
    clearedBankDebits: Double,
    clearedBankDebitsCount: Int,
    creditCardDebits: Double,
    creditCardDebitsCount: Int,
    netDifference: Double,
    isPerfectMatch: Boolean,
    totalStarting: Double,
    actualEnding: Double,
    expectedEnding: Double,
    actualNetEnding: Double,
    countOnlyPaid: Boolean,
    onTogglePaidOnly: () -> Unit
) {
    val statusColor = if (isPerfectMatch) IndGreenDark else IndNavyHeader
    val statusBg = if (isPerfectMatch) Color(0xFFDCFCE7) else Color(0xFFE2E8F0)
    val cardBg = Color(0xFFF0FDF4)
    val cardBorder = Color(0xFF86EFAC)

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reconciliation_accuracy_summary_card")
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Header Row: Title & At-A-Glance Accuracy Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                        color = if (isPerfectMatch) Color(0xFFDCFCE7) else Color(0xFFE6F4EA),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF86EFAC)),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPerfectMatch) Icons.Filled.Verified else Icons.Filled.Balance,
                                contentDescription = null,
                                tint = IndGreenDark,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "ACCOUNT RECONCILIATION ACCURACY",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            color = IndNavyHeader,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = if (isPerfectMatch) "100% Balanced • Everything Cleared ✓" else "Variance: ${if (netDifference > 0) "+" else ""}$currencySymbol${"%,.0f".format(netDifference)} • Action Needed",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPerfectMatch) IndGreenDark else statusColor
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isPerfectMatch) Icons.Filled.CheckCircle else Icons.Filled.Info,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (isPerfectMatch) "BALANCED ✓" else if (netDifference > 0) "SURPLUS" else "DEFICIT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = statusColor
                        )
                    }
                }
            }

            HorizontalDivider(color = cardBorder)

            // 2. Primary 3-Metric Tiles: Cleared Credits | Bank Debits (Excl CC) | Net Difference
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Metric 1: Total Cleared Credits
                MetricTile(
                    modifier = Modifier.weight(1f),
                    title = "CLEARED CREDITS",
                    amount = "+ $currencySymbol${"%,.0f".format(clearedCredits)}",
                    subtitle = "$clearedCreditsCount items (Inflows)",
                    icon = Icons.Filled.ArrowUpward,
                    iconTint = IndGreenDark,
                    bgColor = Color(0xFFF0FDF4),
                    borderColor = Color(0xFFBBF7D0),
                    amountColor = IndGreenDark
                )

                // Metric 2: Bank Account Debits (Direct Outflows, Excludes CC)
                MetricTile(
                    modifier = Modifier.weight(1f),
                    title = "BANK DEBITS",
                    amount = "- $currencySymbol${"%,.0f".format(clearedBankDebits)}",
                    subtitle = "$clearedBankDebitsCount items (excl CC)",
                    icon = Icons.Filled.ArrowDownward,
                    iconTint = IndNavyHeader,
                    bgColor = Color(0xFFF8FAFC),
                    borderColor = Color(0xFFE2E8F0),
                    amountColor = IndNavyHeader
                )

                // Metric 3: Calculated Net Variance
                MetricTile(
                    modifier = Modifier.weight(1f),
                    title = "NET VARIANCE",
                    amount = "${if (netDifference > 0) "+" else ""}$currencySymbol${"%,.0f".format(netDifference)}",
                    subtitle = if (isPerfectMatch) "0.0 perfect match" else if (netDifference > 0) "Bank surplus" else "Bank deficit",
                    icon = if (isPerfectMatch) Icons.Filled.CheckCircle else Icons.Filled.Scale,
                    iconTint = if (isPerfectMatch) IndGreenDark else IndNavyHeader,
                    bgColor = if (isPerfectMatch) Color(0xFFF0FDF4) else Color(0xFFF8FAFC),
                    borderColor = if (isPerfectMatch) Color(0xFF86EFAC) else Color(0xFFCBD5E1),
                    amountColor = if (isPerfectMatch) IndGreenDark else IndNavyHeader
                )
            }

            // 3. Step-by-Step Mathematical Flow (Complete Verification)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = IndSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, IndBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "RECONCILIATION EQUATION BREAKDOWN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = IndNavyHeader,
                        letterSpacing = 0.5.sp
                    )

                    HorizontalDivider(color = IndBorderSubtle)

                    MathRowItem(
                        label = "Total Starting Balance (All Banks)",
                        amountStr = "$currencySymbol${"%,.0f".format(totalStarting)}",
                        icon = Icons.Filled.AccountBalance,
                        color = IndNavyHeader
                    )
                    MathRowItem(
                        label = "(+) Total Inflows / Credits (Salary + Extra)",
                        amountStr = "+ $currencySymbol${"%,.0f".format(clearedCredits)}",
                        icon = Icons.Filled.ArrowUpward,
                        color = IndGreenDark
                    )
                    MathRowItem(
                        label = "(-) Direct Bank Debits (${if (countOnlyPaid) "Paid Only" else "All Debits"} • Excl. Credit Cards)",
                        amountStr = "- $currencySymbol${"%,.0f".format(clearedBankDebits)}",
                        icon = Icons.Filled.ArrowDownward,
                        color = IndNavyHeader
                    )

                    HorizontalDivider(color = IndBorderSubtle)

                    MathRowItem(
                        label = "(=) Expected Bank Ending Balance",
                        amountStr = "$currencySymbol${"%,.0f".format(expectedEnding)}",
                        icon = Icons.Filled.Calculate,
                        color = IndNavyHeader,
                        isBold = true
                    )
                    MathRowItem(
                        label = "(vs) Actual Bank Ending Balance (Passbook)",
                        amountStr = "$currencySymbol${"%,.0f".format(actualEnding)}",
                        icon = Icons.Filled.AccountBalanceWallet,
                        color = if (isPerfectMatch) IndGreenDark else IndNavyHeader,
                        isBold = true
                    )

                    // Difference status banner
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isPerfectMatch) Color(0xFFDCFCE7) else Color(0xFFE6F4EA),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF86EFAC)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isPerfectMatch) "BANK VARIANCE: ₹0 (MATCHED ✓)" else "BANK VARIANCE: ${if (netDifference > 0) "+" else ""}$currencySymbol${"%,.0f".format(netDifference)}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                color = if (isPerfectMatch) IndGreenDark else IndNavyHeader
                            )
                            Text(
                                text = if (isPerfectMatch) "100% Balanced ✓" else if (netDifference > 0) "Bank Has More" else "Bank Has Less",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = if (isPerfectMatch) IndGreenDark else IndNavyHeader
                            )
                        }
                    }
                }
            }

            // 4. Dual Position Banner: Bank Balance vs Net Balance (After CC)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = IndCardSecondary,
                border = androidx.compose.foundation.BorderStroke(1.dp, IndBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.AccountBalance, contentDescription = null, tint = IndNavyHeader, modifier = Modifier.size(15.dp))
                            Text("Bank Balance:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IndNavyHeader)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$currencySymbol${"%,.0f".format(actualEnding)}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = IndNavyHeader
                        )
                        Text(
                            text = "Passbook balance • excludes $currencySymbol${"%,.0f".format(creditCardDebits)} CC charges",
                            fontSize = 9.5.sp,
                            color = IndTextMuted
                        )
                    }

                    Box(modifier = Modifier.width(1.dp).height(42.dp).background(IndBorderSubtle))

                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.CreditCard, contentDescription = null, tint = IndPurple, modifier = Modifier.size(15.dp))
                            Text("Net Balance (After CC):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IndPurple)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$currencySymbol${"%,.0f".format(actualNetEnding)}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (actualNetEnding >= 0) IndPurple else IndNavyHeader
                        )
                        Text(
                            text = "Effective balance after upcoming CC bill",
                            fontSize = 9.5.sp,
                            color = IndTextMuted
                        )
                    }
                }
            }

            // 5. Debits Filter & Quick Information Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(IndBackground)
                    .border(1.dp, IndBorderSubtle, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.FilterList,
                        contentDescription = null,
                        tint = IndNavyHeader,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (countOnlyPaid) "Counting Paid Debits Only (${clearedBankDebitsCount} items)" else "Counting All Debits (${clearedBankDebitsCount} items)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = IndTextPrimary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (countOnlyPaid) IndNavyHeader else IndCardSecondary,
                    modifier = Modifier.clickable { onTogglePaidOnly() }
                ) {
                    Text(
                        text = if (countOnlyPaid) "Switch to All" else "Switch to Paid Only",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (countOnlyPaid) Color.White else IndNavyHeader,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MetricTile(
    modifier: Modifier = Modifier,
    title: String,
    amount: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    bgColor: Color,
    borderColor: Color,
    amountColor: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(12.dp))
                Text(
                    text = title,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = IndTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = amount,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = amountColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                fontSize = 9.sp,
                color = IndTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun BankReconciliationScreen(
    viewModel: FinMoneyViewModel,
    onBack: () -> Unit
) = AccountReconciliationScreen(viewModel = viewModel, onBack = onBack)
