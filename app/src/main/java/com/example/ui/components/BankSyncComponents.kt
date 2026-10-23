package com.example.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.BankTransactionRecord
import com.example.data.model.isCreditCardRecord
import com.example.service.BankSyncManager
import com.example.ui.FinMoneyViewModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Prominent In-App Disclosure Dialog strictly compliant with Google Play User Data & Notification Access policies.
 */
@Composable
fun BankSyncDisclosureDialog(
    onDismiss: () -> Unit,
    onGrantPermission: () -> Unit,
    onTestSimulation: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(16.dp)
                .testTag("bank_sync_disclosure_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(IndBlueLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.AccountBalance,
                        contentDescription = "Bank Auto-Sync",
                        tint = IndBlue,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Bank Balance & Transaction Auto-Sync",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = IndNavyHeader
                )

                Text(
                    text = "Prominent Privacy & Data Disclosure",
                    fontSize = 11.sp,
                    color = IndTextSecondary,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Policy bullet points
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(IndBackground)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DisclosureItem(
                        icon = Icons.Filled.CheckCircle,
                        iconTint = IndGreen,
                        title = "Automatic Balance Extraction",
                        desc = "Reads incoming transaction alerts from your verified banks and UPI apps (GPay, PhonePe, Paytm) to update your bank balance automatically."
                    )
                    DisclosureItem(
                        icon = Icons.Filled.Security,
                        iconTint = IndBlue,
                        title = "100% On-Device Processing",
                        desc = "Parsing runs entirely on your phone. Personal messages and chat apps (WhatsApp, Contacts) are strictly ignored."
                    )
                    DisclosureItem(
                        icon = Icons.Filled.Lock,
                        iconTint = IndBlue,
                        title = "Zero OTP / Password Access",
                        desc = "One-time passwords (OTPs) and security codes are automatically shielded and discarded."
                    )
                    DisclosureItem(
                        icon = Icons.Filled.Settings,
                        iconTint = Color(0xFF64748B),
                        title = "Revocable Anytime",
                        desc = "Notification Access can be toggled on or off at any time in Android Device Settings."
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions
                Button(
                    onClick = onGrantPermission,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("grant_notification_access_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = IndBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Grant Notification Access", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onTestSimulation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("test_simulate_bank_alert_btn"),
                    shape = RoundedCornerShape(12.dp),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(brush = androidx.compose.ui.graphics.SolidColor(IndBlue))
                ) {
                    Icon(Icons.Filled.Bolt, contentDescription = null, tint = IndBlue, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Simulate Bank Alert (Test)", color = IndBlue, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(6.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Not Now", color = IndTextSecondary)
                }
            }
        }
    }
}

@Composable
private fun DisclosureItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier
                .size(18.dp)
                .padding(top = 2.dp)
        )
        Column {
            Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = IndNavyHeader)
            Text(text = desc, fontSize = 11.sp, color = IndTextSecondary, lineHeight = 15.sp)
        }
    }
}

/**
 * Modern Status Card shown on Reconciliation & Salary screens.
 */
@Composable
fun BankAutoSyncStatusBanner(
    isAccessGranted: Boolean,
    recentCount: Int,
    latestBalanceDetected: Double?,
    onOpenDisclosure: () -> Unit,
    onViewTransactions: () -> Unit,
    onSimulateAlert: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bank_auto_sync_banner"),
        shape = RoundedCornerShape(14.dp),
        color = if (isAccessGranted) Color(0xFFF0FDF4) else Color(0xFFF8FAFC),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isAccessGranted) Color(0xFFBBF7D0) else Color(0xFFE2E8F0)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (isAccessGranted) Color(0xFFDCFCE7) else Color(0xFFE2E8F0)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isAccessGranted) Icons.Filled.Sensors else Icons.Filled.NotificationsOff,
                            contentDescription = null,
                            tint = if (isAccessGranted) Color(0xFF15803D) else Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = if (isAccessGranted) "Bank Alert Auto-Sync Active" else "Auto-Sync Bank Balances",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = IndNavyHeader
                        )
                        Text(
                            text = if (isAccessGranted)
                                "Listening for incoming bank & UPI transactions"
                            else
                                "Detects debits, credits & balances automatically",
                            fontSize = 11.sp,
                            color = IndTextSecondary
                        )
                    }
                }

                // Status chip / action button
                if (isAccessGranted) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFDCFCE7)
                    ) {
                        Text(
                            text = "LIVE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF15803D),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                } else {
                    Button(
                        onClick = onOpenDisclosure,
                        colors = ButtonDefaults.buttonColors(containerColor = IndBlue),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Enable", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Quick Info & Actions Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (recentCount > 0) {
                    Text(
                        text = "⚡ $recentCount alerts captured",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = IndBlue,
                        modifier = Modifier
                            .clickable { onViewTransactions() }
                            .padding(vertical = 2.dp)
                    )
                } else {
                    Text(
                        text = "Zero manual typing needed",
                        fontSize = 11.sp,
                        color = IndTextSecondary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Test Simulation button
                    OutlinedButton(
                        onClick = onSimulateAlert,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                            brush = androidx.compose.ui.graphics.SolidColor(if (isAccessGranted) IndBlue else Color(0xFF94A3B8))
                        )
                    ) {
                        Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(13.dp), tint = IndBlue)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Test Alert", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = IndBlue)
                    }

                    if (recentCount > 0) {
                        TextButton(
                            onClick = onViewTransactions,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("View All", fontSize = 11.sp, color = IndBlue, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Bottom Sheet / Dialog showing all auto-detected bank transactions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankTransactionsHistorySheet(
    transactions: List<BankTransactionRecord>,
    onDismiss: () -> Unit,
    onDelete: (Long) -> Unit,
    onEdit: ((BankTransactionRecord) -> Unit)? = null,
    onClearAll: () -> Unit,
    onSimulate: () -> Unit,
    onScanInbox: (() -> Unit)? = null,
    onParseCustomAlert: ((String, String) -> Unit)? = null
) {
    var editingTx by remember { mutableStateOf<BankTransactionRecord?>(null) }
    var showPasteBox by remember { mutableStateOf(false) }
    var customSmsInput by remember { mutableStateOf("") }
    var customSenderInput by remember { mutableStateOf("AD-HDFCBK") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        tonalElevation = 6.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-Synced Bank Alerts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = IndNavyHeader
                    )
                    Text(
                        text = "${transactions.size} records classified & synced with Firestore",
                        fontSize = 11.sp,
                        color = IndTextSecondary
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onScanInbox != null) {
                        IconButton(
                            onClick = { onScanInbox() },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Filled.Sms, contentDescription = "Scan SMS Inbox", tint = IndBlue, modifier = Modifier.size(20.dp))
                        }
                    }
                    IconButton(
                        onClick = { showPasteBox = !showPasteBox },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Filled.ContentPaste, contentDescription = "Paste SMS", tint = IndNavyHeader, modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = onSimulate,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Filled.Bolt, contentDescription = "Simulate", tint = IndBlue, modifier = Modifier.size(20.dp))
                    }
                    if (transactions.isNotEmpty()) {
                        IconButton(
                            onClick = onClearAll,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear All", tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // Quick Paste Box
            AnimatedVisibility(visible = showPasteBox) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(IndSurface)
                        .border(1.dp, IndBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Paste & Parse Bank Message / SMS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = IndNavyHeader
                    )
                    OutlinedTextField(
                        value = customSmsInput,
                        onValueChange = { customSmsInput = it },
                        placeholder = { Text("e.g. Dear Customer, INR 45,200.00 is available balance in HDFC Bank A/C **1234") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        singleLine = false
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customSenderInput,
                            onValueChange = { customSenderInput = it },
                            label = { Text("Sender ID") },
                            modifier = Modifier.width(130.dp),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                if (customSmsInput.isNotBlank()) {
                                    onParseCustomAlert?.invoke(customSmsInput, customSenderInput)
                                    customSmsInput = ""
                                    showPasteBox = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = IndBlue),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Parse & Sync", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Classification Filter Chips: All, Bank, Credit Card, Merchant
            var selectedFilter by remember { mutableStateOf("All") }
            val filterOptions = listOf("All", "Bank", "Credit Card", "Merchant")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filterOptions.forEach { filter ->
                    val isSelected = selectedFilter == filter
                    val count = remember(transactions, filter) {
                        when (filter) {
                            "All" -> transactions.size
                            "Credit Card" -> transactions.count { it.isCreditCardRecord() }
                            "Bank" -> transactions.count { !it.isCreditCardRecord() && (it.classification.equals("Bank", true) || !it.classification.equals("Merchant", true)) }
                            "Merchant" -> transactions.count { !it.isCreditCardRecord() && it.classification.equals("Merchant", true) }
                            else -> transactions.count { it.classification.equals(filter, ignoreCase = true) }
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) IndNavyHeader else IndSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) IndNavyHeader else IndBorder),
                        modifier = Modifier.clickable { selectedFilter = filter }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = filter,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else IndTextPrimary
                            )
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) Color.White.copy(alpha = 0.2f) else Color(0xFFE2E8F0)
                            ) {
                                Text(
                                    text = "$count",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else Color(0xFF475569),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val filteredTransactions = remember(transactions, selectedFilter) {
                when (selectedFilter) {
                    "All" -> transactions
                    "Credit Card" -> transactions.filter { it.isCreditCardRecord() }
                    "Bank" -> transactions.filter { !it.isCreditCardRecord() && (it.classification.equals("Bank", true) || !it.classification.equals("Merchant", true)) }
                    "Merchant" -> transactions.filter { !it.isCreditCardRecord() && it.classification.equals("Merchant", true) }
                    else -> transactions.filter { it.classification.equals(selectedFilter, ignoreCase = true) }
                }
            }

            if (filteredTransactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(48.dp))
                        Text(
                            if (transactions.isEmpty()) "No bank notifications received yet."
                            else "No '$selectedFilter' transactions found.",
                            color = IndTextSecondary,
                            fontSize = 13.sp
                        )
                        Button(
                            onClick = onSimulate,
                            colors = ButtonDefaults.buttonColors(containerColor = IndBlue),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Simulate Sample Alert")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredTransactions, key = { it.id }) { tx ->
                        BankTransactionCard(
                            tx = tx,
                            onDelete = { onDelete(tx.id) },
                            onEdit = { editingTx = tx }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (editingTx != null) {
        val target = editingTx!!
        var merchantStr by remember { mutableStateOf(target.merchant.ifBlank { target.bankName }) }
        var amountStr by remember { mutableStateOf(if (target.amount > 0) target.amount.toString() else "") }
        var categoryStr by remember { mutableStateOf(target.category) }
        var typeStr by remember { mutableStateOf(target.type) }

        AlertDialog(
            onDismissRequest = { editingTx = null },
            title = { Text("Edit Bank Transaction", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = merchantStr,
                        onValueChange = { merchantStr = it },
                        label = { Text("Merchant / Title") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text("Amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = categoryStr,
                        onValueChange = { categoryStr = it },
                        label = { Text("Category") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = typeStr == "DEBIT",
                            onClick = { typeStr = "DEBIT" },
                            label = { Text("Debit (-)") }
                        )
                        FilterChip(
                            selected = typeStr == "CREDIT",
                            onClick = { typeStr = "CREDIT" },
                            label = { Text("Credit (+)") }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = amountStr.toDoubleOrNull() ?: target.amount
                        val updated = target.copy(
                            merchant = merchantStr,
                            amount = amt,
                            category = categoryStr,
                            type = typeStr
                        )
                        onEdit?.invoke(updated)
                        editingTx = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndBlue)
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingTx = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun BankTransactionCard(
    tx: BankTransactionRecord,
    onDelete: () -> Unit,
    onEdit: (() -> Unit)? = null
) {
    val isCredit = tx.type == "CREDIT"
    val dateStr = remember(tx.timestamp) {
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        sdf.format(Date(tx.timestamp))
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = IndBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, IndBorder)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Official Brand Logo (Swiggy, Zomato, Uber, HDFC, ICICI, etc.) with credit/debit badge
                Box(
                    modifier = Modifier.size(42.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    BrandLogo(
                        name = tx.merchant.ifBlank { tx.bankName },
                        badgeSize = 40.dp,
                        fallbackCategory = tx.category,
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Tiny indicator on corner showing Credit (+) vs Debit (-)
                    Surface(
                        shape = CircleShape,
                        color = if (isCredit) Color(0xFF15803D) else Color(0xFFDC2626),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White),
                        modifier = Modifier.size(16.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isCredit) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(9.dp)
                            )
                        }
                    }
                }

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = tx.merchant.ifBlank { tx.bankName },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = IndNavyHeader,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // Auto-classified label ('Bank', 'Credit Card', 'Merchant')
                        val classLabel = if (tx.isCreditCardRecord()) "Credit Card" else if (tx.classification.equals("Credit Card", true)) "Bank" else tx.classification.ifBlank { "Bank" }
                        val (classBg, classFg) = when (classLabel) {
                            "Credit Card" -> Color(0xFFF3E8FF) to Color(0xFF7E22CE)
                            "Merchant" -> Color(0xFFFFEDD5) to Color(0xFFC2410C)
                            else -> Color(0xFFDBEAFE) to Color(0xFF1D4ED8)
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = classBg
                        ) {
                            Text(
                                text = classLabel,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = classFg,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                            )
                        }
                        if (tx.accountNumberLast4.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFE2E8F0)
                            ) {
                                Text(
                                    text = "••${tx.accountNumberLast4}",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF475569),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "${tx.bankName} • $dateStr",
                        fontSize = 11.sp,
                        color = IndTextSecondary
                    )

                    if (tx.availableBalance > 0.0) {
                        Text(
                            text = "Updated Balance: ₹${"%,.2f".format(tx.availableBalance)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0369A1)
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${if (isCredit) "+" else "-"} ₹${"%,.2f".format(tx.amount)}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = if (isCredit) Color(0xFF15803D) else Color(0xFFDC2626)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onEdit != null) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.size(28.dp).testTag("edit_bank_tx_${tx.id}")
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit Transaction", tint = IndBlue, modifier = Modifier.size(14.dp))
                        }
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp).testTag("delete_bank_tx_${tx.id}")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Delete Transaction", tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}
