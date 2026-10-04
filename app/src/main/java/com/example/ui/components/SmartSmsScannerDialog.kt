package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.DebitCategory
import com.example.data.model.LoanType
import com.example.data.sms.DetectedCategory
import com.example.data.sms.ParsedSmsTransaction
import com.example.data.sms.SmsTransactionType
import com.example.ui.FinMoneyViewModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartSmsScannerDialog(
    viewModel: FinMoneyViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currencySymbol by viewModel.currencySymbol.collectAsStateWithLifecycle()
    val detectedTransactions by viewModel.detectedSmsTransactions.collectAsStateWithLifecycle()
    val currentDebits by viewModel.currentDebits.collectAsStateWithLifecycle()

    var filterCategory by remember { mutableStateOf<String?>("ALL") }
    var showPasteCustomSms by remember { mutableStateOf(false) }
    var customSmsText by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isScanning = true
            viewModel.scanDeviceSms(context)
            isScanning = false
        } else {
            Toast.makeText(
                context,
                "SMS permission is required to read your bank & UPI messages.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val filteredList = remember(detectedTransactions, filterCategory) {
        when (filterCategory) {
            "PLANNED" -> detectedTransactions.filter { it.matchedPlannedDebitId != null }
            "PEER" -> detectedTransactions.filter { it.detectedCategory == DetectedCategory.PEER_TRANSFER }
            "SHOPPING" -> detectedTransactions.filter { it.detectedCategory == DetectedCategory.SHOPPING }
            "BILLS" -> detectedTransactions.filter {
                it.detectedCategory == DetectedCategory.ELECTRICITY ||
                it.detectedCategory == DetectedCategory.INTERNET_RECHARGE ||
                it.detectedCategory == DetectedCategory.RENT ||
                it.detectedCategory == DetectedCategory.LOAN_EMI
            }
            "CREDIT" -> detectedTransactions.filter { it.type == SmsTransactionType.CREDIT }
            else -> detectedTransactions
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = IndBackground,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(IndBlueLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = IndBlue, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text(
                                text = "Smart SMS Reader",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = IndTextPrimary
                            )
                            Text(
                                text = "Auto-categorize bank & UPI transactions",
                                style = MaterialTheme.typography.bodySmall,
                                color = IndTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = IndTextSecondary)
                    }
                }

                HorizontalDivider(color = IndBorderSubtle)

                // Sync & Actions Bar
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Primary Action: Scan Real Device SMS
                    Button(
                        onClick = {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.READ_SMS
                            ) == PackageManager.PERMISSION_GRANTED

                            if (hasPermission) {
                                isScanning = true
                                viewModel.scanDeviceSms(context)
                                isScanning = false
                            } else {
                                smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("scan_device_sms_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndNavyHeader, contentColor = Color.White)
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reading Device SMS...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        } else {
                            Icon(Icons.Filled.Sms, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scan Device SMS Inbox", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    // Secondary Tools
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.loadSampleSmsTransactions()
                            },
                            modifier = Modifier.weight(1.2f).height(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = IndTextPrimary),
                            border = androidx.compose.foundation.BorderStroke(1.dp, IndBorder)
                        ) {
                            Icon(Icons.Filled.Dataset, contentDescription = null, modifier = Modifier.size(15.dp), tint = IndBlue)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Load Sample SMS", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = { showPasteCustomSms = !showPasteCustomSms },
                            modifier = Modifier.weight(1f).height(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = IndBlue),
                            border = androidx.compose.foundation.BorderStroke(1.dp, IndBlue.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(15.dp), tint = IndBlue)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Paste SMS", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Paste Custom SMS Box
                AnimatedVisibility(visible = showPasteCustomSms) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = IndSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, IndBlue.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Paste Bank / UPI SMS text to test auto-categorization:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = IndTextPrimary
                            )

                            OutlinedTextField(
                                value = customSmsText,
                                onValueChange = { customSmsText = it },
                                placeholder = { Text("e.g. Debited Rs 1,499 for Shopping at Amazon...") },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 3,
                                shape = RoundedCornerShape(8.dp),
                                textStyle = MaterialTheme.typography.bodySmall
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { showPasteCustomSms = false }) {
                                    Text("Cancel", fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (customSmsText.isNotBlank()) {
                                            viewModel.parseAndAddCustomSms(customSmsText)
                                            customSmsText = ""
                                            showPasteCustomSms = false
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = IndBlue)
                                ) {
                                    Text("Categorize & Add", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Filter Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val filters = listOf(
                        "ALL" to "All (${detectedTransactions.size})",
                        "PLANNED" to "Planned Match 🎯",
                        "PEER" to "Peer Transfer 🤝",
                        "SHOPPING" to "Shopping 🛒",
                        "BILLS" to "Bills & EMI ⚡",
                        "CREDIT" to "Credits 💰"
                    )
                    items(filters) { (key, label) ->
                        FilterChip(
                            selected = filterCategory == key,
                            onClick = { filterCategory = key },
                            label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = IndBlueLight,
                                selectedLabelColor = IndBlue
                            )
                        )
                    }
                }

                // Transaction Cards
                if (filteredList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(Icons.Filled.MarkEmailRead, contentDescription = null, tint = IndTextMuted, modifier = Modifier.size(48.dp))
                            Text(
                                text = if (detectedTransactions.isEmpty()) "No Pending SMS to Review" else "No matching transactions in this filter",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = IndTextPrimary
                            )
                            Text(
                                text = "Tap 'Sample Bank SMS' or 'Scan Inbox' above to load simulated bank debits, peer transfers, Amazon shopping & utility bills.",
                                style = MaterialTheme.typography.bodySmall,
                                color = IndTextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredList, key = { it.id }) { item ->
                            DetectedSmsCard(
                                tx = item,
                                currencySymbol = currencySymbol,
                                onConfirm = { asPlanned, cat, peerType ->
                                    viewModel.confirmSmsTransaction(item, asPlanned, cat, peerType)
                                },
                                onReject = {
                                    viewModel.rejectSmsTransaction(item.id)
                                }
                            )
                        }
                    }
                }

                // Bottom Dismiss Action
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = IndNavyHeader)
                ) {
                    Text("Done Reviewing", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DetectedSmsCard(
    tx: ParsedSmsTransaction,
    currencySymbol: String,
    onConfirm: (asPlanned: Boolean, overrideCategory: String?, overridePeerType: LoanType?) -> Unit,
    onReject: () -> Unit
) {
    val isDebit = tx.type == SmsTransactionType.DEBIT
    val hasPlannedMatch = tx.matchedPlannedDebitId != null
    var showRawSms by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = IndSurface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (hasPlannedMatch) IndGreen.copy(alpha = 0.6f) else IndBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sms_card_${tx.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: Category Pill & Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isDebit) IndRedLight else IndGreenLight
                    ) {
                        Text(
                            text = if (isDebit) "DEBIT" else "CREDIT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isDebit) IndRedDark else IndGreenDark,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = IndBlueLight
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = tx.detectedCategory.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = IndBlue,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                Text(
                    text = "${if (isDebit) "-" else "+"} $currencySymbol${"%,.0f".format(tx.amount)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isDebit) IndRed else IndGreenDark
                )
            }

            // Party / Description
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = tx.merchantOrParty,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = IndTextPrimary
                    )
                    if (tx.phoneNumber.isNotBlank()) {
                        Text(
                            text = "Contact Phone: +91 ${tx.phoneNumber}",
                            style = MaterialTheme.typography.bodySmall,
                            color = IndTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                Text(
                    text = tx.sender,
                    style = MaterialTheme.typography.labelSmall,
                    color = IndTextMuted,
                    fontSize = 10.sp
                )
            }

            // Planned Match Indicator Banner
            if (hasPlannedMatch) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = IndGreenLight,
                    border = androidx.compose.foundation.BorderStroke(1.dp, IndGreen.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = IndGreenDark, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Matches Planned Commitment: '${tx.matchedPlannedDebitTitle}'",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = IndGreenDark,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Expandable raw SMS snippet
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showRawSms = !showRawSms },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (showRawSms) "Hide SMS Message" else "View original SMS text",
                    fontSize = 11.sp,
                    color = IndTextMuted,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    if (showRawSms) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = IndTextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }

            AnimatedVisibility(visible = showRawSms) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = IndCardSecondary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = tx.originalSms,
                        style = MaterialTheme.typography.bodySmall,
                        color = IndTextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            // Action Buttons (Confirm with user / Reject)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Reject Button
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f).height(38.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = IndRed)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reject ✕", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Confirm Action Button
                if (hasPlannedMatch) {
                    Button(
                        onClick = { onConfirm(true, null, null) },
                        modifier = Modifier.weight(1.8f).height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndGreenDark)
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Confirm & Mark PAID ✓", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else if (tx.detectedCategory == DetectedCategory.PEER_TRANSFER) {
                    Button(
                        onClick = { onConfirm(false, null, if (isDebit) LoanType.GIVEN else LoanType.TAKEN) },
                        modifier = Modifier.weight(1.8f).height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (isDebit) IndGreenDark else IndNavyHeader)
                    ) {
                        Icon(Icons.Filled.People, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isDebit) "Add to Lent Thread 🤝" else "Add to Borrowed 🤝", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else if (tx.detectedCategory == DetectedCategory.SALARY_CREDIT || (!isDebit && tx.amount > 5000)) {
                    Button(
                        onClick = { onConfirm(false, null, null) },
                        modifier = Modifier.weight(1.8f).height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndGreenDark)
                    ) {
                        Icon(Icons.Filled.Savings, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Confirm Salary Credit 💰", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = { onConfirm(false, null, null) },
                        modifier = Modifier.weight(1.8f).height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndNavyHeader)
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Confirm & Log Outflow ✓", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
