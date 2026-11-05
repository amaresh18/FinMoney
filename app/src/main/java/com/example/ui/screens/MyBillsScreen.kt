package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.*
import com.example.data.repository.BillsRepository
import com.example.ui.FinMoneyViewModel
import com.example.ui.components.BrandLogo
import com.example.ui.components.BrandType
import com.example.ui.components.IndCard
import com.example.ui.components.IndOutlinedTextField
import com.example.ui.theme.*

private val DarkNavyHeader = Color(0xFF0F172A)
private val CardNavyBg = Color(0xFF1E293B)
private val IndGreenButton = Color(0xFF00B386)
private val IndOverdueRed = Color(0xFFEF4444)
private val IndOverdueBg = Color(0xFFFEE2E2)
private val IndPayBlue = Color(0xFF2563EB)
private val IndSubBarBg = Color(0xFFF8FAFC)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyBillsScreen(
    viewModel: FinMoneyViewModel,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { BillsRepository.getInstance(context) }
    val bills by repository.bills.collectAsState()
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val currentMonth by viewModel.currentMonth.collectAsState()
    val currentYear by viewModel.currentYear.collectAsState()

    val commissions by repository.commissions.collectAsState()
    val credentials by repository.credentials.collectAsState()
    var showPartnerHubDialog by remember { mutableStateOf(false) }

    val totalCommissionEarned = remember(commissions) {
        commissions.filter { it.status == "Credited" }.sumOf { it.amount }
    }
    val pendingCommissionEarned = remember(commissions) {
        commissions.filter { it.status == "Pending Verification" }.sumOf { it.amount }
    }

    var selectedCategory by remember { mutableStateOf(BillCategory.ALL) }
    var showAddBillDialog by remember { mutableStateOf(false) }
    var payingBill by remember { mutableStateOf<BillItem?>(null) }
    var clearBillNotice by remember { mutableStateOf<BillItem?>(null) }
    var paidReceiptBill by remember { mutableStateOf<BillItem?>(null) }
    var managingBillSettings by remember { mutableStateOf<BillItem?>(null) }

    val filteredBills = remember(bills, selectedCategory) {
        when (selectedCategory) {
            BillCategory.ALL -> bills
            BillCategory.NEW_CARDS -> emptyList()
            else -> bills.filter { it.category == selectedCategory }
        }
    }

    Scaffold(
        containerColor = IndBackground,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkNavyHeader)
                    .statusBarsPadding()
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.1f))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "My Bills",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Surface(
                                    color = IndGreenButton,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "BHARAT CONNECT",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Credit cards, utilities & instant 1-click payments",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }

                    // Top-right GroMo & Eko Partner Hub Pill
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF047857).copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, Color(0xFF10B981)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showPartnerHubDialog = true }
                            .testTag("partner_hub_top_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AccountBalanceWallet,
                                contentDescription = null,
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "₹${"%,.0f".format(totalCommissionEarned)} Earned",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Filter Pill Tabs Row (All, Credit Card, Electricity, Insurance, Loans, New Credit Cards)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BillCategory.entries.forEach { cat ->
                        val isSelected = selectedCategory == cat
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) IndPayBlue else Color.White.copy(alpha = 0.12f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) IndPayBlue else Color.White.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { selectedCategory = cat }
                                .testTag("tab_bill_${cat.name.lowercase()}")
                        ) {
                            Text(
                                text = cat.displayName,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (selectedCategory != BillCategory.NEW_CARDS) {
                Button(
                    onClick = { showAddBillDialog = true },
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = IndGreenButton),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                    modifier = Modifier
                        .testTag("add_bill_fab_button")
                        .navigationBarsPadding()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "+ Add Bill",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Hero Cashback / Rewards Banner (Matches Screenshot 7 & 8)
            item {
                RewardsCashbackBanner()
            }

            // GroMo & Eko Partner Earnings Hub Banner
            item {
                GroMoEkoPartnerBanner(
                    totalEarned = totalCommissionEarned,
                    pendingAmount = pendingCommissionEarned,
                    partnerCode = credentials.gromoPartnerCode,
                    isProduction = credentials.isEkoProduction,
                    onOpenHub = { showPartnerHubDialog = true }
                )
            }

            if (selectedCategory == BillCategory.NEW_CARDS) {
                item {
                    Text(
                        text = "Pre-Approved Credit Cards & Loans For You",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = IndNavyHeader,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(repository.preApprovedCards, key = { it.id }) { card ->
                    PreApprovedCardItem(
                        card = card,
                        onApply = {
                            val trackingUrl = repository.recordGroMoLead(card)
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(trackingUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // ignore
                            }
                            Toast.makeText(
                                context,
                                "GroMo partner tracking activated! ${card.gromoCommissionPayout} payout added to your partner ledger.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            } else {
                if (filteredBills.isEmpty()) {
                    item {
                        EmptyBillsState(
                            category = selectedCategory,
                            onAddBill = { showAddBillDialog = true }
                        )
                    }
                } else {
                    items(filteredBills, key = { it.id }) { bill ->
                        INDMoneyBillCard(
                            bill = bill,
                            currencySymbol = currencySymbol,
                            onPayNow = { payingBill = bill },
                            onRefresh = {
                                val refreshed = repository.refreshBill(bill.id)
                                if (refreshed?.dueAmount == 0.0 || refreshed?.isPaid == true) {
                                    clearBillNotice = refreshed
                                } else {
                                    Toast.makeText(context, "Bill updated: Due ${currencySymbol}${"%,.2f".format(refreshed?.dueAmount ?: 0.0)}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onViewReceipt = { paidReceiptBill = bill },
                            onOpenSettings = { managingBillSettings = bill }
                        )
                    }
                }
            }

            // Bottom trust seal
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = IndGreenButton,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "100% safe transactions powered by INDpay by FinMoney",
                        fontSize = 11.5.sp,
                        color = IndTextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    // Single-Click Payment Bottom Sheet
    payingBill?.let { bill ->
        SingleClickPaymentSheet(
            bill = bill,
            currencySymbol = currencySymbol,
            onDismiss = { payingBill = null },
            onConfirmPayment = { method ->
                val paidBill = repository.payBill(
                    billId = bill.id,
                    amount = bill.dueAmount,
                    paymentMethod = method,
                    currentMonth = currentMonth,
                    currentYear = currentYear
                ) { debit ->
                    viewModel.insertDebit(debit)
                }
                payingBill = null
                if (paidBill != null) {
                    paidReceiptBill = paidBill
                }
            }
        )
    }

    // "All clear, No bill due !" Bottom Sheet (Matches Screenshot 2)
    clearBillNotice?.let { bill ->
        AllClearNoBillDueSheet(
            bill = bill,
            onDismiss = { clearBillNotice = null }
        )
    }

    // Payment Success Receipt Dialog
    paidReceiptBill?.let { bill ->
        BillPaymentSuccessDialog(
            bill = bill,
            currencySymbol = currencySymbol,
            onDismiss = { paidReceiptBill = null }
        )
    }

    // Bill Settings Dialog (Manage / Toggle Autopay / Delete)
    managingBillSettings?.let { bill ->
        BillSettingsDialog(
            bill = bill,
            onToggleAutopay = {
                val newState = repository.toggleAutopay(bill.id)
                Toast.makeText(context, if (newState) "Autopay enabled" else "Autopay paused", Toast.LENGTH_SHORT).show()
                managingBillSettings = null
            },
            onDelete = {
                repository.deleteBill(bill.id)
                Toast.makeText(context, "Bill removed", Toast.LENGTH_SHORT).show()
                managingBillSettings = null
            },
            onDismiss = { managingBillSettings = null }
        )
    }

    // Add Bill Dialog / Provider Selector (Matches Screenshots 1, 3, 6)
    if (showAddBillDialog) {
        AddBillProviderDialog(
            onDismiss = { showAddBillDialog = false },
            onBillAdded = { newBill ->
                repository.addBill(newBill)
                showAddBillDialog = false
                Toast.makeText(context, "Bill added successfully: ${newBill.providerName}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // GroMo & Eko Partner Hub Dialog
    if (showPartnerHubDialog) {
        PartnerHubDialog(
            credentials = credentials,
            commissions = commissions,
            onUpdateCredentials = { updated ->
                repository.updateCredentials(updated)
                Toast.makeText(context, "Partner settings updated successfully!", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showPartnerHubDialog = false }
        )
    }
}

/**
 * Hero Cashback / Rewards Banner matching INDmoney Screenshot 7 & 8
 */
@Composable
private fun RewardsCashbackBanner() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF0FDF4),
        border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Stars,
                        contentDescription = null,
                        tint = IndGreenButton,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Turn bills into rewards!",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF166534)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Earn up to ₹100 cashback on every bill payment & recharge via INDpay UPI",
                    fontSize = 11.5.sp,
                    color = Color(0xFF15803D),
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Surface(
                shape = CircleShape,
                color = Color(0xFFDCFCE7),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.CurrencyRupee,
                        contentDescription = null,
                        tint = IndGreenButton,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

/**
 * INDmoney Bill Card (Credit Cards & Utilities) matching Screenshots 4, 5, 7, 8
 */
@Composable
private fun INDMoneyBillCard(
    bill: BillItem,
    currencySymbol: String,
    onPayNow: () -> Unit,
    onRefresh: () -> Unit,
    onViewReceipt: () -> Unit,
    onOpenSettings: () -> Unit
) {
    IndCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Main Card Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                // Top Row: Logo + Provider & Masked Details + Right Action Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Provider / Bank Logo
                        BillProviderAvatar(providerCode = bill.providerCode, category = bill.category)

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = bill.providerName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = IndNavyHeader,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = bill.accountNumberMasked,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = IndTextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Right Action (Pay Now > / Refresh > / Paid ✓)
                    when {
                        bill.isPaid || bill.dueAmount <= 0.0 -> {
                            if (bill.category == BillCategory.UTILITY) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onRefresh() }
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Refresh",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = IndPayBlue
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.ChevronRight,
                                        contentDescription = null,
                                        tint = IndPayBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFDCFCE7),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onViewReceipt() }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF15803D),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "Paid ✓",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF15803D)
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onPayNow() }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                    .testTag("pay_bill_btn_${bill.id}")
                            ) {
                                Text(
                                    text = "Pay Now",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = IndPayBlue
                                )
                                Icon(
                                    imageVector = Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    tint = IndPayBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Status & Due Amount
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (bill.isOverdue) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = IndOverdueBg
                        ) {
                            Text(
                                text = "• Overdue",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = IndOverdueRed,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (bill.isPaid || bill.dueAmount <= 0.0) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFDCFCE7)
                        ) {
                            Text(
                                text = "• No bill due",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF15803D),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFFEF3C7)
                        ) {
                            Text(
                                text = "• Due soon",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD97706),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (bill.dueAmount > 0.0 && !bill.isPaid) {
                        Text(
                            text = "Due Amount : $currencySymbol${"%,.2f".format(bill.dueAmount)}",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndNavyHeader
                        )
                    }
                }
            }

            HorizontalDivider(color = IndBorderSubtle, thickness = 0.8.dp)

            // Sub-bar with Settings & Protection Info (Matches INDmoney screenshots)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(IndSubBarBg)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Bill Settings",
                        tint = IndTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = IndGreenButton,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = if (bill.category == BillCategory.UTILITY) "Autopay is not supported, Know more" else "No hidden charges this month",
                        fontSize = 11.sp,
                        color = IndTextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = IndTextSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BillProviderAvatar(providerCode: String, category: BillCategory) {
    val brandType = remember(providerCode) {
        when (providerCode.uppercase()) {
            "HDFC" -> BrandType.HDFC
            "ICICI" -> BrandType.ICICI
            "AXIS" -> BrandType.AXIS
            "SBI" -> BrandType.SBI
            "KOTAK" -> BrandType.KOTAK
            "PNB" -> BrandType.PNB
            "BOB" -> BrandType.BOB
            "CANARA" -> BrandType.CANARA
            "IDFC" -> BrandType.IDFC_FIRST
            "INDUSIND" -> BrandType.INDUSIND
            "YES" -> BrandType.YES_BANK
            "FEDERAL" -> BrandType.FEDERAL_BANK
            "RBL" -> BrandType.RBL_BANK
            else -> null
        }
    }

    if (brandType != null) {
        BrandLogo(
            name = providerCode,
            badgeSize = 38.dp
        )
    } else {
        Surface(
            shape = CircleShape,
            color = when (category) {
                BillCategory.UTILITY -> Color(0xFFFEF3C7)
                BillCategory.INSURANCE -> Color(0xFFE0F2FE)
                BillCategory.LOANS -> Color(0xFFF3E8FF)
                else -> Color(0xFFDCFCE7)
            },
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = when (category) {
                        BillCategory.UTILITY -> Icons.Filled.Bolt
                        BillCategory.INSURANCE -> Icons.Filled.HealthAndSafety
                        BillCategory.LOANS -> Icons.Filled.Home
                        else -> Icons.Filled.CreditCard
                    },
                    contentDescription = null,
                    tint = when (category) {
                        BillCategory.UTILITY -> Color(0xFFD97706)
                        BillCategory.INSURANCE -> Color(0xFF0284C7)
                        BillCategory.LOANS -> Color(0xFF9333EA)
                        else -> IndGreenButton
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Pre-Approved Card Item
 */
@Composable
private fun PreApprovedCardItem(
    card: PreApprovedCreditCard,
    onApply: () -> Unit
) {
    IndCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = card.bankName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = IndTextSecondary
                    )
                    Text(
                        text = card.cardName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = IndNavyHeader
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFEFF6FF),
                        border = BorderStroke(0.5.dp, Color(0xFF93C5FD))
                    ) {
                        Text(
                            text = card.productType.uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndPayBlue,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFDCFCE7),
                        border = BorderStroke(0.5.dp, Color(0xFF86EFAC))
                    ) {
                        Text(
                            text = "PRE-APPROVED",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF15803D),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = card.rewardHighlight,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = IndNavyHeader,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CardGiftcard,
                    contentDescription = null,
                    tint = IndGreenButton,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = card.joiningBonus,
                    fontSize = 11.sp,
                    color = Color(0xFF15803D),
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // GroMo Partner Commission Strip
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFECFDF5),
                border = BorderStroke(0.5.dp, Color(0xFFA7F3D0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MonetizationOn,
                            contentDescription = null,
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "GroMo Partner Commission",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF065F46)
                        )
                    }
                    Text(
                        text = "${card.gromoCommissionPayout} payout",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF047857)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = card.annualFee,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = IndTextSecondary
                    )
                    Text(
                        text = "No GST needed • Direct to bank",
                        fontSize = 10.sp,
                        color = Color(0xFF059669),
                        fontWeight = FontWeight.Medium
                    )
                }

                Button(
                    onClick = onApply,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = IndPayBlue),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.OpenInNew,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Apply via GroMo",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Single Click Instant Payment Bottom Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingleClickPaymentSheet(
    bill: BillItem,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onConfirmPayment: (String) -> Unit
) {
    var selectedMethod by remember { mutableStateOf("UPI (Google Pay)") }
    val cashbackDiscount = 25.0
    val totalPayable = maxOf(0.0, bill.dueAmount - cashbackDiscount)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Single-Click Bill Payment",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = IndNavyHeader
                    )
                    Text(
                        text = "Powered by Bharat Connect & INDpay",
                        fontSize = 11.5.sp,
                        color = IndTextSecondary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFDCFCE7)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = Color(0xFF15803D),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "256-bit Secure",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF15803D)
                        )
                    }
                }
            }

            // Bill Summary Box
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = IndSubBarBg,
                border = BorderStroke(1.dp, IndBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = bill.providerName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = IndNavyHeader)
                        Text(text = bill.accountNumberMasked, fontSize = 12.sp, color = IndTextSecondary)
                    }

                    HorizontalDivider(color = IndBorderSubtle)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Bill Amount", fontSize = 12.5.sp, color = IndTextSecondary)
                        Text(text = "$currencySymbol${"%,.2f".format(bill.dueAmount)}", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Platform / Convenience Fee", fontSize = 12.5.sp, color = IndTextSecondary)
                        Text(text = "FREE (₹0)", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Instant Cashback Applied", fontSize = 12.5.sp, color = Color(0xFF15803D), fontWeight = FontWeight.SemiBold)
                        Text(text = "-$currencySymbol${"%,.2f".format(cashbackDiscount)}", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                    }

                    HorizontalDivider(color = IndBorderSubtle)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Total Payable", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = IndNavyHeader)
                        Text(
                            text = "$currencySymbol${"%,.2f".format(totalPayable)}",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndPayBlue
                        )
                    }
                }
            }

            // Eko BBPS Notice & Retailer margin
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFECFDF5),
                border = BorderStroke(0.5.dp, Color(0xFFA7F3D0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF059669),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Processed via Eko BBPS Connect • Retailer commission of ₹8.50 auto-credited to your partner wallet.",
                        fontSize = 11.sp,
                        color = Color(0xFF065F46),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Payment Methods
            Text(
                text = "Select Payment App",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = IndTextSecondary
            )

            val methods = listOf("UPI (Google Pay)", "UPI (PhonePe)", "UPI (Paytm)", "Direct Bank Transfer")
            methods.forEach { method ->
                val isSelected = selectedMethod == method
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) Color(0xFFEFF6FF) else Color.White,
                    border = BorderStroke(1.dp, if (isSelected) IndPayBlue else IndBorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedMethod = method }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (method.contains("Google")) Icons.Filled.AccountBalanceWallet else Icons.Filled.Payment,
                                contentDescription = null,
                                tint = if (isSelected) IndPayBlue else IndTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = method,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = IndNavyHeader
                            )
                        }

                        RadioButton(
                            selected = isSelected,
                            onClick = { selectedMethod = method },
                            colors = RadioButtonDefaults.colors(selectedColor = IndPayBlue)
                        )
                    }
                }
            }

            // Single Click CTA
            Button(
                onClick = { onConfirmPayment(selectedMethod) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IndGreenButton),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("confirm_single_click_payment_button")
            ) {
                Icon(Icons.Filled.FlashOn, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Pay $currencySymbol${"%,.2f".format(totalPayable)} in 1-Click",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * "All clear, No bill due !" Bottom Sheet directly matching Screenshot 2
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllClearNoBillDueSheet(
    bill: BillItem,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Bill Illustration with Checkmark & Coin
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFFF0FDF4),
                border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                modifier = Modifier.size(76.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.TaskAlt,
                        contentDescription = null,
                        tint = IndGreenButton,
                        modifier = Modifier.size(46.dp)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "All clear, No bill due !",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF047857)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "We will notify you once new bill is generated",
                    fontSize = 13.sp,
                    color = IndTextSecondary,
                    textAlign = TextAlign.Center
                )
            }

            HorizontalDivider(color = IndBorderSubtle)

            // Provider Info
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = bill.providerName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = IndNavyHeader
                )
                Text(
                    text = "Unique Service Number: ${bill.accountNumberMasked}",
                    fontSize = 12.sp,
                    color = IndTextSecondary
                )
            }

            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IndGreenButton),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("okay_no_bill_due_button")
            ) {
                Text(
                    text = "Okay",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = null,
                    tint = IndGreenButton,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "100% safe transactions powered by INDpay by FinMoney",
                    fontSize = 10.5.sp,
                    color = IndTextSecondary
                )
            }
        }
    }
}

/**
 * Bill Payment Receipt Dialog with UTR & Confetti
 */
@Composable
private fun BillPaymentSuccessDialog(
    bill: BillItem,
    currencySymbol: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFDCFCE7),
                    modifier = Modifier.size(60.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF15803D),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Text(
                    text = "Payment Successful!",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = IndNavyHeader
                )

                Text(
                    text = "Your bill payment of $currencySymbol${"%,.2f".format(bill.dueAmount)} to ${bill.providerName} has been processed successfully.",
                    fontSize = 12.5.sp,
                    color = IndTextSecondary,
                    textAlign = TextAlign.Center
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = IndSubBarBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Transaction Ref (UTR):", fontSize = 11.5.sp, color = IndTextSecondary)
                            Text(bill.utrReference ?: "IND89240182", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = IndNavyHeader)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Bharat Connect ID:", fontSize = 11.5.sp, color = IndTextSecondary)
                            Text("BC-TXN-${(1000..9999).random()}", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Cashback Earned:", fontSize = 11.5.sp, color = Color(0xFF15803D), fontWeight = FontWeight.Bold)
                            Text("₹25 Instant Cash", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Added to Expenses:", fontSize = 11.5.sp, color = IndTextSecondary)
                            Text("Yes (Auto-logged)", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = IndGreenButton)
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = IndGreenButton),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

/**
 * Bill Settings Dialog
 */
@Composable
private fun BillSettingsDialog(
    bill: BillItem,
    onToggleAutopay: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color.White,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Manage ${bill.providerName}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = IndNavyHeader
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = IndSubBarBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Autopay On Due Date", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = IndNavyHeader)
                            Text(if (bill.autopaySupported) "Active" else "Disabled", fontSize = 11.5.sp, color = IndTextSecondary)
                        }
                        Switch(
                            checked = bill.autopaySupported,
                            onCheckedChange = { onToggleAutopay() }
                        )
                    }
                }

                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = IndOverdueRed),
                    border = BorderStroke(1.dp, IndOverdueRed.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Remove Bill From My Bills", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Close", color = IndTextSecondary)
                }
            }
        }
    }
}

/**
 * Add Bill & Provider Selector matching Screenshots 1, 3, 6
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBillProviderDialog(
    onDismiss: () -> Unit,
    onBillAdded: (BillItem) -> Unit
) {
    var selectedCategory by remember { mutableStateOf(BillCategory.CREDIT_CARD) }
    var searchQuery by remember { mutableStateOf("") }

    // Form inputs when a provider is picked
    var selectedBankOrProvider by remember { mutableStateOf<String?>(null) }
    var accountOrCardNumber by remember { mutableStateOf("") }
    var amountInput by remember { mutableStateOf("1500") }

    val popularBanks = listOf(
        "HDFC" to "HDFC Bank",
        "ICICI" to "ICICI Bank",
        "AXIS" to "Axis Bank",
        "SBI" to "SBI Card",
        "KOTAK" to "Kotak Bank",
        "IDFC" to "IDFC First",
        "INDUSIND" to "IndusInd Bank",
        "RBL_BANK" to "RBL Bank",
        "YES_BANK" to "Yes Bank",
        "AU" to "AU Bank",
        "FEDERAL_BANK" to "Federal Bank",
        "AMEX" to "American Express"
    )

    val otherBanks = listOf(
        "BANK OF BARODA",
        "BANK OF BARODA - ONE CARD",
        "CANARA BANK",
        "CUB BANK",
        "DCB BANK",
        "DHANALAKSHMI BANK",
        "ESAF Bank Credit Card",
        "HSBC",
        "IDBI",
        "INDIAN BANK",
        "INDIAN BANK - ONE CARD",
        "PNB",
        "SRCB",
        "SLICE",
        "SOUTH INDIAN BANK - ONE CARD",
        "SURYODAY SMALL FINANCE BANK",
        "UNION BANK OF INDIA"
    )

    val electricityProviders = listOf(
        "Northern Power Distribution Company of Telangana Limited",
        "Adani Electricity Mumbai Limited",
        "AEML SEEPZ Limited",
        "Ajmer Vidyut Vitran Nigam Limited",
        "Andhra Pradesh Central Power Distribution Corporation",
        "Assam Power Distribution Company Ltd",
        "Bangalore Electricity Supply Co. Ltd (BESCOM)",
        "Tata Power - Mumbai",
        "BSES Rajdhani Power Limited",
        "BSES Yamuna Power Limited",
        "CESC Limited - Kolkata",
        "Torrent Power"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header with Back / Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (selectedBankOrProvider != null) {
                            IconButton(onClick = { selectedBankOrProvider = null }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                        Text(
                            text = if (selectedBankOrProvider != null) "Enter Details" else "Select ${selectedCategory.displayName}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndNavyHeader
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (selectedBankOrProvider == null) {
                    // Category Selection Pills
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            BillCategory.CREDIT_CARD,
                            BillCategory.UTILITY,
                            BillCategory.INSURANCE,
                            BillCategory.LOANS
                        ).forEach { cat ->
                            val isSelected = selectedCategory == cat
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat.displayName, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = IndPayBlue,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Search field
                    IndOutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = "Search Provider or Bank",
                        placeholder = "e.g. HDFC, NPDCL, Tata Power...",
                        leadingIcon = Icons.Filled.Search
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Provider List (Matches Screenshot 1 & 6 for Cards, Screenshot 3 for Utilities)
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (selectedCategory == BillCategory.CREDIT_CARD) {
                            if (searchQuery.isBlank()) {
                                item {
                                    Text(
                                        text = "Popular banks",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = IndNavyHeader,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }

                                item {
                                    // 4-Column Grid matching Screenshot 6
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(4),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(210.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(popularBanks) { (code, name) ->
                                            Column(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { selectedBankOrProvider = name }
                                                    .padding(4.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                BillProviderAvatar(providerCode = code, category = BillCategory.CREDIT_CARD)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = name.replace(" Bank", "").replace(" Card", ""),
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = IndNavyHeader,
                                                    maxLines = 1,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }

                                item {
                                    Text(
                                        text = "Other banks",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = IndNavyHeader,
                                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                                    )
                                }
                            }

                            val filteredOtherBanks = otherBanks.filter {
                                searchQuery.isBlank() || it.contains(searchQuery, ignoreCase = true)
                            }
                            items(filteredOtherBanks) { bank ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White,
                                    border = BorderStroke(0.6.dp, IndBorderSubtle),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedBankOrProvider = bank }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = bank, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = IndNavyHeader)
                                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = IndTextSecondary, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        } else {
                            val list = electricityProviders.filter {
                                searchQuery.isBlank() || it.contains(searchQuery, ignoreCase = true)
                            }
                            item {
                                Text(
                                    text = "All Providers (${list.size})",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = IndNavyHeader,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(list) { prov ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White,
                                    border = BorderStroke(0.6.dp, IndBorderSubtle),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedBankOrProvider = prov }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = prov, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = IndNavyHeader, modifier = Modifier.weight(1f))
                                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = IndTextSecondary, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Enter details form for selected provider
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFEFF6FF),
                            border = BorderStroke(1.dp, IndPayBlue.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.AccountBalance, contentDescription = null, tint = IndPayBlue)
                                Text(
                                    text = selectedBankOrProvider ?: "",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = IndNavyHeader
                                )
                            }
                        }

                        IndOutlinedTextField(
                            value = accountOrCardNumber,
                            onValueChange = { accountOrCardNumber = it },
                            label = if (selectedCategory == BillCategory.CREDIT_CARD) "Last 4 Digits of Card" else "Consumer ID / Service Number",
                            placeholder = if (selectedCategory == BillCategory.CREDIT_CARD) "e.g. 8320" else "e.g. 16258886",
                            leadingIcon = Icons.Filled.Pin
                        )

                        IndOutlinedTextField(
                            value = amountInput,
                            onValueChange = { amountInput = it },
                            label = "Estimated Due Amount (₹)",
                            placeholder = "e.g. 1500",
                            leadingIcon = Icons.Filled.CurrencyRupee
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        Button(
                            onClick = {
                                val masked = if (selectedCategory == BillCategory.CREDIT_CARD) {
                                    "•• ${accountOrCardNumber.takeLast(4).ifBlank { "8320" }}"
                                } else {
                                    accountOrCardNumber.ifBlank { "16258886" }
                                }
                                val amountVal = amountInput.toDoubleOrNull() ?: 1000.0
                                val newBill = BillItem(
                                    category = selectedCategory,
                                    providerName = selectedBankOrProvider ?: "Provider",
                                    providerCode = (selectedBankOrProvider ?: "").split(" ").firstOrNull() ?: "BANK",
                                    subCategory = selectedCategory.displayName,
                                    accountNumberMasked = masked,
                                    dueAmount = amountVal,
                                    dueDateTimestamp = System.currentTimeMillis() + 86400000L * 7,
                                    isOverdue = false,
                                    isPaid = false
                                )
                                onBillAdded(newBill)
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IndGreenButton),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text("+ Link & Fetch Bill", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Empty Bills State
 */
@Composable
private fun EmptyBillsState(
    category: BillCategory,
    onAddBill: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, IndBorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = IndSubBarBg,
                modifier = Modifier.size(54.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Receipt,
                        contentDescription = null,
                        tint = IndPayBlue,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Text(
                text = "No ${category.displayName} Bills Added",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = IndNavyHeader
            )

            Text(
                text = "Link your ${category.displayName.lowercase()} bill to track due dates, get auto-reminders, and pay in a single click with cashback.",
                fontSize = 12.sp,
                color = IndTextSecondary,
                textAlign = TextAlign.Center
            )

            Button(
                onClick = onAddBill,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IndGreenButton),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("+ Link Bill", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

/**
 * GroMo & Eko Partner Earnings Hub Banner
 */
@Composable
private fun GroMoEkoPartnerBanner(
    totalEarned: Double,
    pendingAmount: Double,
    partnerCode: String,
    isProduction: Boolean,
    onOpenHub: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardNavyBg,
        border = BorderStroke(1.dp, Color(0xFF059669).copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onOpenHub() }
            .testTag("gromo_eko_partner_banner")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF047857)
                    ) {
                        Text(
                            text = "GROMO & EKO PARTNER",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.White.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "ZERO GST NEEDED",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF34D399),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isProduction) Color(0xFF15803D) else Color(0xFFD97706),
                ) {
                    Text(
                        text = if (isProduction) "PROD" else "SANDBOX",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            // Metrics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Commissions Earned",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "₹${"%,.2f".format(totalEarned)}",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF34D399)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Pending Payouts",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "₹${"%,.2f".format(pendingAmount)}",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFBBF24)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Code: $partnerCode • Eko BBPS & GroMo Affiliate",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    fontWeight = FontWeight.Medium
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Partner Hub",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF34D399)
                    )
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFF34D399),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

/**
 * GroMo & Eko Partner Hub Dialog
 */
@Composable
private fun PartnerHubDialog(
    credentials: PartnerCredentials,
    commissions: List<CommissionRecord>,
    onUpdateCredentials: (PartnerCredentials) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = Config, 1 = Ledger

    var gromoCode by remember { mutableStateOf(credentials.gromoPartnerCode) }
    var ekoDevKey by remember { mutableStateOf(credentials.ekoDeveloperKey) }
    var ekoAuthKey by remember { mutableStateOf(credentials.ekoAuthKey) }
    var ekoInitiator by remember { mutableStateOf(credentials.ekoInitiatorId) }
    var isProduction by remember { mutableStateOf(credentials.isEkoProduction) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Partner & Earnings Hub",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndNavyHeader
                        )
                        Text(
                            text = "GroMo Affiliates & Eko BBPS (No GST Required)",
                            fontSize = 11.sp,
                            color = Color(0xFF059669),
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = IndTextSecondary)
                    }
                }

                // Tabs: Config vs Ledger
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = IndSubBarBg,
                    contentColor = IndPayBlue,
                    indicator = {},
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                text = "API & Partner Config",
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                text = "Commission Ledger (${commissions.size})",
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    )
                }

                if (selectedTab == 0) {
                    // Config Form
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFECFDF5),
                            border = BorderStroke(0.5.dp, Color(0xFFA7F3D0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Individual developer model: Payouts are transferred directly into your personal bank/UPI with 5% standard TDS. No business incorporation or GST needed.",
                                fontSize = 11.sp,
                                color = Color(0xFF065F46),
                                lineHeight = 15.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }

                        // GroMo Partner Code
                        OutlinedTextField(
                            value = gromoCode,
                            onValueChange = { gromoCode = it },
                            label = { Text("GroMo Partner / Referral Code", fontSize = 11.5.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Eko Developer Key
                        OutlinedTextField(
                            value = ekoDevKey,
                            onValueChange = { ekoDevKey = it },
                            label = { Text("Eko Developer Key", fontSize = 11.5.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Eko Initiator ID
                        OutlinedTextField(
                            value = ekoInitiator,
                            onValueChange = { ekoInitiator = it },
                            label = { Text("Eko Initiator Mobile / User ID", fontSize = 11.5.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Environment Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (isProduction) "Production Live Mode" else "Sandbox Testing Mode",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isProduction) Color(0xFF15803D) else Color(0xFFD97706)
                                )
                                Text(
                                    text = if (isProduction) "Real payouts & live bill clearing" else "Test sandbox responses",
                                    fontSize = 11.sp,
                                    color = IndTextSecondary
                                )
                            }

                            Switch(
                                checked = isProduction,
                                onCheckedChange = { isProduction = it }
                            )
                        }

                        Button(
                            onClick = {
                                onUpdateCredentials(
                                    credentials.copy(
                                        gromoPartnerCode = gromoCode.trim(),
                                        ekoDeveloperKey = ekoDevKey.trim(),
                                        ekoAuthKey = ekoAuthKey.trim(),
                                        ekoInitiatorId = ekoInitiator.trim(),
                                        isEkoProduction = isProduction
                                    )
                                )
                                onDismiss()
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IndGreenButton),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Text("Save Partner Credentials", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                } else {
                    // Commission Ledger List
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(commissions, key = { it.id }) { comm ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = IndSubBarBg,
                                    border = BorderStroke(0.5.dp, IndBorderSubtle),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = comm.title,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = IndNavyHeader
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = comm.category,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = IndPayBlue
                                                )
                                                Text(
                                                    text = "• ${comm.referenceNumber}",
                                                    fontSize = 10.sp,
                                                    color = IndTextSecondary
                                                )
                                            }
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "+₹${"%,.2f".format(comm.amount)}",
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (comm.status == "Credited") Color(0xFF15803D) else Color(0xFFD97706)
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (comm.status == "Credited") Color(0xFFDCFCE7) else Color(0xFFFEF3C7)
                                            ) {
                                                Text(
                                                    text = comm.status,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (comm.status == "Credited") Color(0xFF15803D) else Color(0xFFD97706),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
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
}

