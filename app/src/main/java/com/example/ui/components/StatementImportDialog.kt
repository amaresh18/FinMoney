package com.example.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
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
import androidx.compose.ui.window.DialogProperties
import com.example.data.bank.BankTxType
import com.example.service.statement.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Universal Statement Import Dialog.
 * Allows importing bank statements, GPay, PhonePe, Super Money, HDFC, ICICI, SBI, Axis, etc.
 * from PDF, Excel (.xlsx), CSV, and text files.
 * Strictly considers ONLY successful transaction statuses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatementImportDialog(
    onDismiss: () -> Unit,
    onImportSuccess: (ImportCommitSummary) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isProcessing by remember { mutableStateOf(false) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var rawTextContent by remember { mutableStateOf("") }
    var parseResult by remember { mutableStateOf<StatementParseResult?>(null) }
    var showPastedTextSection by remember { mutableStateOf(false) }
    var activePreviewTab by remember { mutableStateOf("SUCCESS") } // SUCCESS, SKIPPED

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isProcessing = true
                try {
                    val (fileName, text) = StatementImportManager.readStatementFromUri(context, uri)
                    selectedFileName = fileName
                    rawTextContent = text
                    if (text.isNotBlank()) {
                        val parsed = StatementImportManager.parseStatementText(text, fileName)
                        parseResult = parsed
                    } else {
                        Toast.makeText(context, "Could not extract text from selected file.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error reading file: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .testTag("statement_import_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = IndSurface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = IndBlueLight,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.FileUpload,
                                    contentDescription = null,
                                    tint = IndBlue,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Import Statement",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = IndNavyHeader
                            )
                            Text(
                                text = "PDF, Excel (.xlsx), CSV • Only successful txns",
                                style = MaterialTheme.typography.bodySmall,
                                color = IndTextSecondary
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = IndTextSecondary)
                    }
                }

                HorizontalDivider(color = IndBorder.copy(alpha = 0.6f))

                // File Upload & Quick Presets Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            filePickerLauncher.launch(
                                arrayOf(
                                    "*/*",
                                    "application/pdf",
                                    "text/csv",
                                    "text/plain",
                                    "application/vnd.ms-excel",
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                )
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("select_statement_file_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndNavyHeader)
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Select File (PDF/Excel/CSV)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { showPastedTextSection = !showPastedTextSection },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(46.dp)
                    ) {
                        Icon(
                            imageVector = if (showPastedTextSection) Icons.Default.VisibilityOff else Icons.Default.EditNote,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (showPastedTextSection) "Hide Text" else "Paste Text", fontSize = 12.sp)
                    }
                }

                // Collapsible Paste Text & Quick Samples Section
                AnimatedVisibility(visible = showPastedTextSection) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(IndBackground, RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Or paste statement text / pick a sample format:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndNavyHeader
                        )

                        // Sample format quick chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SampleChip("Super Money History") {
                                rawTextContent = SAMPLE_SUPER_MONEY_HISTORY
                                selectedFileName = "SuperMoney_History.pdf"
                                parseResult = StatementImportManager.parseStatementText(SAMPLE_SUPER_MONEY_HISTORY, "SuperMoney_History.pdf")
                            }
                            SampleChip("PhonePe CSV") {
                                rawTextContent = SAMPLE_PHONEPE_CSV
                                selectedFileName = "PhonePe_Statement.csv"
                                parseResult = StatementImportManager.parseStatementText(SAMPLE_PHONEPE_CSV, "PhonePe_Statement.csv")
                            }
                            SampleChip("Google Pay") {
                                rawTextContent = SAMPLE_GPAY_STATEMENT
                                selectedFileName = "GooglePay_Statement.pdf"
                                parseResult = StatementImportManager.parseStatementText(SAMPLE_GPAY_STATEMENT, "GooglePay_Statement.pdf")
                            }
                        }

                        OutlinedTextField(
                            value = rawTextContent,
                            onValueChange = {
                                rawTextContent = it
                                if (it.isNotBlank()) {
                                    parseResult = StatementImportManager.parseStatementText(it, selectedFileName ?: "Pasted Statement")
                                }
                            },
                            placeholder = { Text("Paste CSV, OCR, or statement text here...", fontSize = 11.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp),
                            textStyle = LocalTextStyle.current.copy(fontSize = 11.sp),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                // Processing indicator
                if (isProcessing) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = IndBlue)
                            Text("Analyzing statement & extracting valid transactions...", fontSize = 12.sp, color = IndNavyHeader)
                        }
                    }
                }

                // Parsed Results Preview
                val currentResult = parseResult
                if (currentResult != null && !isProcessing) {
                    // System Info & Summary Strip
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = IndBackground,
                        border = BorderStroke(1.dp, IndBorder),
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.AccountBalance, contentDescription = null, tint = IndBlue, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = currentResult.detectedSystem,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = IndNavyHeader
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = IndGreenLight
                                ) {
                                    Text(
                                        text = "✓ ${currentResult.successfulTransactions.size} SUCCESSFUL",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 10.sp,
                                        color = IndGreenDark,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Debits: -₹${"%,.2f".format(currentResult.totalDebitAmount)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = IndRed
                                )
                                Text(
                                    text = "Credits: +₹${"%,.2f".format(currentResult.totalCreditAmount)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = IndGreenDark
                                )
                                if (currentResult.failedOrSkippedTransactions.isNotEmpty()) {
                                    Text(
                                        text = "Ignored: ${currentResult.failedOrSkippedTransactions.size} FAILED",
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 11.sp,
                                        color = Color(0xFFDC2626)
                                    )
                                }
                            }
                        }
                    }

                    // Tabs: Successful vs Skipped
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = activePreviewTab == "SUCCESS",
                            onClick = { activePreviewTab = "SUCCESS" },
                            label = { Text("Successful to Import (${currentResult.successfulTransactions.size})") },
                            leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = IndGreenLight,
                                selectedLabelColor = IndGreenDark
                            )
                        )

                        if (currentResult.failedOrSkippedTransactions.isNotEmpty()) {
                            FilterChip(
                                selected = activePreviewTab == "SKIPPED",
                                onClick = { activePreviewTab = "SKIPPED" },
                                label = { Text("Ignored Failed (${currentResult.failedOrSkippedTransactions.size})") },
                                leadingIcon = { Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFFFE4E6),
                                    selectedLabelColor = Color(0xFFDC2626)
                                )
                            )
                        }
                    }

                    // Transactions List
                    val displayedList = if (activePreviewTab == "SUCCESS") currentResult.successfulTransactions else currentResult.failedOrSkippedTransactions

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(displayedList, key = { it.id }) { tx ->
                            StatementTransactionPreviewItem(tx)
                        }
                    }

                    // Bottom Action Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel", fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isProcessing = true
                                    try {
                                        val summary = StatementImportManager.commitTransactions(
                                            context = context,
                                            transactions = currentResult.successfulTransactions
                                        )
                                        onImportSuccess(summary)
                                        onDismiss()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Failed to commit imports: ${e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isProcessing = false
                                    }
                                }
                            },
                            enabled = currentResult.successfulTransactions.isNotEmpty() && !isProcessing,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IndGreenDark),
                            modifier = Modifier
                                .weight(2f)
                                .height(46.dp)
                                .testTag("confirm_import_statement_button")
                        ) {
                            Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Confirm & Import (${currentResult.successfulTransactions.size} Txns)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                        }
                    }
                } else if (!isProcessing) {
                    // Empty / Prompt State
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ReceiptLong,
                                contentDescription = null,
                                tint = IndTextMuted,
                                modifier = Modifier.size(56.dp)
                            )
                            Text(
                                text = "Ready to Import Statement",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = IndNavyHeader
                            )
                            Text(
                                text = "Choose a file (PDF, CSV, Excel) or paste text above. Only successful transactions will be added as credit and debit entries into your ledger.",
                                textAlign = TextAlign.Center,
                                fontSize = 12.sp,
                                color = IndTextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SampleChip(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = IndSurface,
        border = BorderStroke(1.dp, IndBlue.copy(alpha = 0.4f)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = IndBlue,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun StatementTransactionPreviewItem(tx: ImportedStatementTransaction) {
    val isDebit = tx.type == BankTxType.DEBIT

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (tx.isSuccess) IndSurface else Color(0xFFFFE4E6).copy(alpha = 0.4f),
        border = BorderStroke(0.8.dp, if (tx.isSuccess) IndBorder else Color(0xFFDC2626).copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                BrandLogoView(
                    name = tx.merchantOrName,
                    badgeSize = 36.dp,
                    shape = RoundedCornerShape(8.dp),
                    fallbackCategory = tx.category
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tx.merchantOrName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = IndNavyHeader,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${tx.bankName}${if (tx.accountLast4.isNotBlank()) " (••${tx.accountLast4})" else ""} • ${tx.dateText}",
                        fontSize = 10.sp,
                        color = IndTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (isDebit) "-" else "+"}₹${"%,.2f".format(tx.amount)}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    color = if (!tx.isSuccess) Color(0xFFDC2626) else if (isDebit) IndRed else IndGreenDark
                )

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (tx.isSuccess) IndGreenLight else Color(0xFFFFE4E6)
                ) {
                    Text(
                        text = tx.status,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (tx.isSuccess) IndGreenDark else Color(0xFFDC2626),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

// Built-in presets from the user's prompt for instant 1-tap testing
private const val SAMPLE_SUPER_MONEY_HISTORY = """Name Bank Amount Date Status
MVS ENTERPRISES HDFC 0989 -60.00 10 October 2026 SUCCESS
Mr BANTU HDFC 0989 -20.00 10 October 2026 SUCCESS
Duddela Sailu HDFC 0989 -80.00 10 October 2026 SUCCESS
Smart Point FR Kamareddy FR7K Utkarsh XX29 -176.23 9 October 2026 SUCCESS
GAJAWADA AMARESH HDFC 0989 20.00 9 October 2026 FAILED
GAJAWADA AMARESH HDFC 0989 10.00 9 October 2026 FAILED
Mohammed Nayeem Pasha HDFC 0989 -500.00 8 October 2026 SUCCESS
SRI BALAJI PANNALA MITHAI Utkarsh XX29 -5.00 8 October 2026 SUCCESS
SRI BALAJI PANNALA MITHAI Utkarsh XX29 5.00 8 October 2026 FAILED
GANGAVVA VEGETABLES Utkarsh XX29 -50.00 7 October 2026 SUCCESS
BANALA BHASKAR REDDY HDFC 0989 -40.00 7 October 2026 SUCCESS
MR CHAKALI RANJITH KUMAR Utkarsh XX29 -80.00 6 October 2026 SUCCESS
TELANGANA STATE ROAD TRANSPORT CORPORATION HDFC 0989 -250.00 6 October 2026 SUCCESS
BIRADAR NANDINI Utkarsh XX29 -20.00 6 October 2026 SUCCESS
SHADABTAKEAWAY HDFC 0989 -142.00 6 October 2026 SUCCESS
SHADABCASUALDINING Utkarsh XX29 -1056.00 6 October 2026 SUCCESS
UBER INDIA SYSTEMS PRIVATE LIMITED HDFC 0989 -86.65 6 October 2026 SUCCESS
MR JARIKOTE SAI KRISHNA HDFC 0989 -480.00 6 October 2026 SUCCESS
APOLLO PHARMACY Utkarsh XX29 -140.79 5 October 2026 SUCCESS
Arati Ratna Kumar Reddy Utkarsh XX29 -60.00 5 October 2026 SUCCESS
SAHITHI KIRANAM Utkarsh XX29 -10.00 4 October 2026 SUCCESS
The Chennai Shopping MallKamareddy Utkarsh XX29 -1796.00 4 October 2026 SUCCESS
Mrs Duddela Sujatha HDFC 0989 -20.00 4 October 2026 SUCCESS
SAHITHI KIRANAM Utkarsh XX29 -60.00 3 October 2026 SUCCESS
NANDIKANTI RANI HDFC 0989 -20.00 3 October 2026 SUCCESS
Testers Community Utkarsh XX29 -999.00 3 October 2026 SUCCESS
BALAJI RAMDEV BAKERY AND C Utkarsh XX29 -40.00 2 October 2026 SUCCESS
SAHITHI KIRANAM Utkarsh XX29 -180.00 2 October 2026 SUCCESS
KAMAKSHI SUPER STORE HDFC 0989 -424.00 2 October 2026 SUCCESS
Autumn cafe HDFC 0989 -120.00 1 October 2026 SUCCESS
KAMAKSHI SUPER STORE HDFC 0989 -903.00 1 October 2026 SUCCESS
MAITHRY FILLING STATION Utkarsh XX29 -300.00 1 October 2026 SUCCESS"""

private const val SAMPLE_PHONEPE_CSV = """Date,Time,Transaction Details,Transaction ID,UTR,Transaction Type,Credit/debit instrument,Amount
"Oct 08, 2026","11:44 AM","Paid to VOORE ANAND KUMAR","T2610081144007692819738","566646548861","DEBIT","Paid by XXXXXX0989","50000"
"Oct 06, 2026","10:05 PM","Paid to pradheep adars","T2610062205242996234224","479103446932","DEBIT","Paid by XXXXXX0989","18000"
"Oct 06, 2026","10:04 PM","Paid to pradheep adars","T2610062204268108132975","170818907615","DEBIT","Paid by XXXXXX0989","18000"
"Oct 06, 2026","03:45 PM","Received from Sunil Paysafe","T2610061544536894881874","109266130455","CREDIT","Credited to XXXXXX0989","350"
"Oct 06, 2026","03:44 PM","Received from MAHESH CHINNALA","T2610061544254469524081","664513475438","CREDIT","Credited to XXXXXX0989","350"
"Oct 05, 2026","09:54 PM","Paid to My Brother","T2610052154509260378163","768950752658","DEBIT","Paid by XXXXXX0989","24550"
"Oct 05, 2026","09:09 PM","Received from GAJAWADA BHARGAVI","T2610052109082627083779","627841859364","CREDIT","Credited to XXXXXX0989","50000"
"Oct 05, 2026","07:04 PM","Received from Google Cloud","T2610051904255098969056","664449894891","CREDIT","Credited to XXXXXX0989","2"
"Oct 05, 2026","05:54 PM","Received from Google Cloud","T2610051754361521279861","664449584196","CREDIT","Credited to XXXXXX0989","2"
"Oct 04, 2026","12:21 PM","FASTag Recharge for TS08EV2288","NB26100412205987435870042","584391112014","DEBIT","Paid by HDFC CREDIT","1010.80"
"Oct 03, 2026","09:12 PM","Paid to Rajesh Adarsha","T2610032112515024964607","310160766230","DEBIT","Paid by XXXXXX0989","2000"
"Oct 03, 2026","11:38 AM","Paid to Praveen ADP","T2610031138410136011312","183797958950","DEBIT","Paid by XXXXXX0989","4400"
"Oct 01, 2026","07:26 PM","Received from Vishnu G Bachupally Rent","T2610011926225074703410","316252028896","CREDIT","Credited to XXXXXX0989","14500"
"Oct 01, 2026","04:06 PM","Received from Nishanth adars","T2610011606071771527293","133107902518","CREDIT","Credited to XXXXXX0989","2000""""

private const val SAMPLE_GPAY_STATEMENT = """Transaction statement
Transaction statement period: 01 September 2026 - 30 September 2026
Sent: ₹30,000 | Received: ₹0

01 Sep, 2026
10:14 AM
Paid to Sai Kishore
UPI Transaction ID: 624445564610
Paid by ICICI Bank 5501
₹15,000

02 Sep, 2026
10:41 AM
Paid to Sai Kishore
UPI Transaction ID: 128889777050
Paid by HDFC Bank 0989
₹15,000"""
