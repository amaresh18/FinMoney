package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.bank.MerchantEnrichmentEngine
import com.example.data.model.AppNotification
import com.example.data.model.BankTransactionRecord
import com.example.service.BankNotificationListenerService
import com.example.service.BankSyncManager
import com.example.service.EntryAuditResult
import com.example.service.EntryIntegrityEngine
import com.example.ui.FinMoneyViewModel
import com.example.ui.components.BrandLogoView
import com.example.ui.components.IndCard
import com.example.ui.components.NotificationAccessRequestDialog
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingTransactionsScreen(
    viewModel: FinMoneyViewModel,
    onBack: () -> Unit,
    onOpenLoan: (loanId: Long, cloudId: String, notificationId: Long) -> Unit = { _, _, _ -> }
) {
    BackHandler { onBack() }

    val currencySymbol by viewModel.currencySymbol.collectAsStateWithLifecycle()
    val pendingTransactions by viewModel.pendingBankTransactions.collectAsStateWithLifecycle()
    val allTransactions by viewModel.bankTransactions.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val unreadNotifications = remember(notifications) {
        notifications.filter { !it.isRead }
    }
    val loanActions = remember(unreadNotifications) {
        unreadNotifications.filter { it.type == "LOAN_ACTION" }
    }
    val otherUnreadNotifications = remember(unreadNotifications) {
        unreadNotifications.filter { it.type != "LOAN_ACTION" }
    }

    val context = LocalContext.current
    val isListenerConnected by BankSyncManager.isListenerConnected.collectAsStateWithLifecycle()
    var isPermissionGranted by remember { mutableStateOf(BankSyncManager.isNotificationAccessGranted(context)) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showAuditLogsDialog by remember { mutableStateOf(false) }

    var selectedTab by remember { mutableStateOf("PENDING") } // PENDING, DUPLICATES, INCORRECT
    var searchQuery by remember { mutableStateOf("") }
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    // Automated deep audit for duplicate and incorrect entries
    val unconfirmedTransactions = remember(allTransactions) {
        allTransactions.filter { !it.isConfirmed }
    }
    val auditMap = remember(unconfirmedTransactions) {
        EntryIntegrityEngine.auditPendingTransactions(unconfirmedTransactions)
    }

    val duplicateList = remember(unconfirmedTransactions, auditMap) {
        unconfirmedTransactions.filter { tx ->
            tx.isDuplicate || auditMap[tx.id]?.isDuplicate == true
        }
    }

    val incorrectList = remember(unconfirmedTransactions, auditMap) {
        unconfirmedTransactions.filter { tx ->
            !duplicateList.contains(tx) && (tx.isIncorrect || auditMap[tx.id]?.isIncorrect == true)
        }
    }

    val standardPendingList = remember(unconfirmedTransactions, duplicateList, incorrectList) {
        unconfirmedTransactions.filter { tx ->
            !duplicateList.contains(tx) && !incorrectList.contains(tx)
        }
    }

    val filteredList = remember(standardPendingList, duplicateList, incorrectList, selectedTab, searchQuery) {
        val baseList = when (selectedTab) {
            "DUPLICATES" -> duplicateList
            "INCORRECT" -> incorrectList
            else -> standardPendingList
        }

        baseList.filter { tx ->
            searchQuery.isBlank() ||
                    tx.bankName.contains(searchQuery, ignoreCase = true) ||
                    tx.merchant.contains(searchQuery, ignoreCase = true) ||
                    tx.originalText.contains(searchQuery, ignoreCase = true) ||
                    tx.amount.toString().contains(searchQuery)
        }
    }

    val duplicateCount = duplicateList.size
    val incorrectCount = incorrectList.size

    Scaffold(
        containerColor = AppTheme.colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Pending Transactions",
                            style = AppTheme.typography.titleLarge,
                            color = AppTheme.colors.secondary
                        )
                        Text(
                            text = "Review, approve or reject extracted financial SMS/alerts",
                            style = AppTheme.typography.caption,
                            color = AppTheme.colors.textSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("pending_tx_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AppTheme.colors.secondary
                        )
                    }
                },
                actions = {
                    if (unreadNotifications.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.markAllNotificationsRead() },
                            modifier = Modifier.testTag("mark_all_notifications_read_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DoneAll,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = AppTheme.colors.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Mark all read",
                                style = AppTheme.typography.labelSmall,
                                color = AppTheme.colors.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (pendingTransactions.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.batchRefineWithGemini() },
                            modifier = Modifier
                                .padding(end = AppTheme.dimensions.extraSmall)
                                .testTag("gemini_refine_all_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Refine all with Gemini AI",
                                tint = AppTheme.colors.primary
                            )
                        }

                        Button(
                            onClick = { viewModel.approveAllPendingTransactions() },
                            colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primary),
                            contentPadding = PaddingValues(horizontal = AppTheme.dimensions.paddingMedium, vertical = AppTheme.dimensions.paddingExtraSmall),
                            shape = RoundedCornerShape(AppTheme.dimensions.chipCornerRadius),
                            modifier = Modifier
                                .padding(end = AppTheme.dimensions.small)
                                .testTag("approve_all_pending_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = null,
                                modifier = Modifier.size(AppTheme.dimensions.iconSmall),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(AppTheme.dimensions.extraSmall))
                            Text(
                                text = "Approve All (${pendingTransactions.size})",
                                style = AppTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                    IconButton(
                        onClick = { showAuditLogsDialog = true },
                        modifier = Modifier.testTag("open_audit_logs_action_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.HistoryEdu,
                            contentDescription = "AI Studio Action Logs",
                            tint = AppTheme.colors.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppTheme.colors.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = AppTheme.dimensions.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(AppTheme.dimensions.medium)
        ) {
            Spacer(modifier = Modifier.height(AppTheme.dimensions.extraSmall))

            if (otherUnreadNotifications.isNotEmpty()) {
                Text(
                    "Notifications",
                    fontWeight = FontWeight.Bold,
                    color = IndNavyHeader,
                    fontSize = 14.sp
                )
                otherUnreadNotifications.forEach { note ->
                    AppNotificationCard(
                        notification = note,
                        dateFormatted = dateFormatter.format(Date(note.timestamp)),
                        onOpen = {
                            viewModel.markNotificationRead(note.id)
                        }
                    )
                }
            }

            if (loanActions.isNotEmpty()) {
                Text(
                    "Lent & borrowed actions",
                    fontWeight = FontWeight.Bold,
                    color = IndNavyHeader,
                    fontSize = 14.sp
                )
                loanActions.forEach { note ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.markNotificationRead(note.id)
                                onOpenLoan(note.relatedLoanId ?: 0L, "", note.id)
                            },
                        colors = CardDefaults.cardColors(containerColor = IndBlueLight),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(note.title, fontWeight = FontWeight.Bold, color = IndBlue, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(note.message, fontSize = 12.sp, color = IndTextSecondary, maxLines = 3)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Open record", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = IndBlue)
                        }
                    }
                }
            }

            // Listener Connection & Self-Healing Status Banner
            if (!isPermissionGranted) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                    shape = RoundedCornerShape(AppTheme.dimensions.cardCornerRadius),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPermissionDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color(0xFFB45309))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Notification Listener Inactive", fontWeight = FontWeight.Bold, color = Color(0xFFB45309), fontSize = 13.sp)
                            Text("Grant notification access to auto-capture bank transactions.", color = Color(0xFF92400E), fontSize = 11.sp)
                        }
                        TextButton(onClick = { showPermissionDialog = true }) {
                            Text("Enable", fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                        }
                    }
                }
            } else if (!isListenerConnected) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2FE)),
                    shape = RoundedCornerShape(AppTheme.dimensions.cardCornerRadius),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, tint = Color(0xFF0369A1))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Listener Reconnecting", fontWeight = FontWeight.Bold, color = Color(0xFF0369A1), fontSize = 13.sp)
                            Text("Self-healing background sync active. Tap to refresh.", color = Color(0xFF0284C7), fontSize = 11.sp)
                        }
                        TextButton(onClick = { BankNotificationListenerService.requestRebindService(context) }) {
                            Text("Rebind", fontWeight = FontWeight.Bold, color = Color(0xFF0369A1))
                        }
                    }
                }
            }

            // Search Bar & Filter Tabs
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search merchant, bank, amount...", style = AppTheme.typography.bodyMedium) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AppTheme.colors.textMuted) },
                singleLine = true,
                shape = RoundedCornerShape(AppTheme.dimensions.cardCornerRadius),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppTheme.colors.primary,
                    unfocusedBorderColor = AppTheme.colors.border,
                    focusedContainerColor = AppTheme.colors.surface,
                    unfocusedContainerColor = AppTheme.colors.surface
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Tabs Row with Horizontal Scroll
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(AppTheme.dimensions.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectedTab == "PENDING",
                    onClick = { selectedTab = "PENDING" },
                    label = { Text("Pending (${standardPendingList.size})", style = AppTheme.typography.labelMedium) },
                    leadingIcon = { Icon(Icons.Outlined.HourglassEmpty, contentDescription = null, modifier = Modifier.size(AppTheme.dimensions.iconSmall)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AppTheme.colors.primaryLight,
                        selectedLabelColor = AppTheme.colors.primary
                    )
                )

                FilterChip(
                    selected = selectedTab == "DUPLICATES",
                    onClick = { selectedTab = "DUPLICATES" },
                    label = { Text("Duplicates ($duplicateCount)", style = AppTheme.typography.labelMedium) },
                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(AppTheme.dimensions.iconSmall)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFEF3C7),
                        selectedLabelColor = Color(0xFFB45309)
                    )
                )

                FilterChip(
                    selected = selectedTab == "INCORRECT",
                    onClick = { selectedTab = "INCORRECT" },
                    label = { Text("Incorrect ($incorrectCount)", style = AppTheme.typography.labelMedium) },
                    leadingIcon = { Icon(Icons.Outlined.ErrorOutline, contentDescription = null, modifier = Modifier.size(AppTheme.dimensions.iconSmall)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFFE4E6),
                        selectedLabelColor = AppTheme.colors.error
                    )
                )
            }

            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    IndCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(AppTheme.dimensions.huge),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(AppTheme.dimensions.medium)
                        ) {
                            Icon(
                                imageVector = when (selectedTab) {
                                    "DUPLICATES" -> Icons.Outlined.CheckCircle
                                    "INCORRECT" -> Icons.Outlined.Verified
                                    else -> Icons.Outlined.Inbox
                                },
                                contentDescription = null,
                                tint = AppTheme.colors.accent,
                                modifier = Modifier.size(56.dp)
                            )
                            Text(
                                text = when (selectedTab) {
                                    "DUPLICATES" -> "No duplicate transactions detected"
                                    "INCORRECT" -> "No incorrect entries detected"
                                    else -> "All caught up! No pending transactions"
                                },
                                style = AppTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.textPrimary,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = when (selectedTab) {
                                    "DUPLICATES" -> "Identical reference numbers, rapid broadcast alerts, and repeated transactions are automatically caught and flagged here."
                                    "INCORRECT" -> "Transactions with inverted debit/credit types, OTP false positives, delivery updates, or corrupted amounts are automatically isolated here."
                                    else -> "Extracted financial notifications and SMS alerts requiring your approval will appear here before being permanently added to your financial ledger."
                                },
                                style = AppTheme.typography.bodySmall,
                                color = AppTheme.colors.textSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AppTheme.dimensions.medium),
                    contentPadding = PaddingValues(bottom = AppTheme.dimensions.huge)
                ) {
                    items(filteredList, key = { it.id }) { tx ->
                        PendingTransactionCard(
                            transaction = tx,
                            currencySymbol = currencySymbol,
                            dateFormatted = dateFormatter.format(Date(tx.timestamp)),
                            auditResult = auditMap[tx.id],
                            onApprove = { category ->
                                viewModel.approvePendingTransaction(tx.id, category ?: "GENERAL")
                            },
                            onReject = { viewModel.rejectPendingTransaction(tx.id) },
                            onRefineWithGemini = { viewModel.reclassifyTransactionWithGemini(tx.id) }
                        )
                    }
                }
            }
        }
    }

    if (showPermissionDialog) {
        NotificationAccessRequestDialog(
            onDismiss = {
                showPermissionDialog = false
                isPermissionGranted = BankSyncManager.isNotificationAccessGranted(context)
            },
            onGrantClicked = {
                showPermissionDialog = false
                isPermissionGranted = BankSyncManager.isNotificationAccessGranted(context)
            }
        )
    }

    if (showAuditLogsDialog) {
        com.example.ui.components.ActionAuditLogsDialog(
            viewModel = viewModel,
            onDismiss = { showAuditLogsDialog = false }
        )
    }
}

@Composable
private fun AppNotificationCard(
    notification: AppNotification,
    dateFormatted: String,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = IndSurface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, IndBorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(IndBlue)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notification.title.ifBlank { "Notification" },
                    fontWeight = FontWeight.Bold,
                    color = IndNavyHeader,
                    fontSize = 14.sp
                )
                if (notification.message.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = notification.message,
                        fontSize = 12.sp,
                        color = IndTextSecondary,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = dateFormatted,
                    fontSize = 11.sp,
                    color = IndTextMuted
                )
            }
            Icon(
                imageVector = Icons.Outlined.MarkEmailRead,
                contentDescription = "Mark read",
                tint = IndBlue,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingTransactionCard(
    transaction: BankTransactionRecord,
    currencySymbol: String,
    dateFormatted: String,
    auditResult: EntryAuditResult? = null,
    onApprove: (selectedCategory: String?) -> Unit,
    onReject: () -> Unit,
    onRefineWithGemini: () -> Unit = {}
) {
    val isDup = transaction.isDuplicate || auditResult?.isDuplicate == true
    val dupReason = if (!transaction.duplicateReason.isNullOrBlank()) transaction.duplicateReason!! else (auditResult?.duplicateReason ?: "Potential duplicate transaction detected.")

    val isInc = transaction.isIncorrect || auditResult?.isIncorrect == true
    val incReason = if (!transaction.incorrectReason.isNullOrBlank()) transaction.incorrectReason!! else (auditResult?.incorrectReason ?: "Potential parsing anomaly or non-financial alert.")

    val isCredit = transaction.type.equals("CREDIT", ignoreCase = true)
    var selectedCategory by remember { mutableStateOf(transaction.category.ifBlank { "GENERAL" }) }
    var showCategoryMenu by remember { mutableStateOf(false) }

    val categories = remember {
        listOf(
            "OUTSIDE_FOOD" to "Dining & Food",
            "GROCERIES" to "Groceries",
            "SHOPPING" to "Shopping",
            "FUEL" to "Fuel & Commute",
            "TRAVEL" to "Travel",
            "RECHARGE" to "Recharge & Utilities",
            "ELECTRICITY" to "Electricity & Bills",
            "PHARMACY" to "Medicines",
            "EMI" to "Loan / EMI",
            "RENT" to "Rent & Housing",
            "GENERAL" to "General Expense"
        )
    }

    val enrichedMerchant = remember(transaction.merchant, transaction.bankName, transaction.originalText) {
        MerchantEnrichmentEngine.enrich(
            rawMerchant = transaction.merchant.ifBlank { transaction.bankName },
            rawText = transaction.originalText,
            senderOrTitle = transaction.bankName
        )
    }

    IndCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pending_tx_card_${transaction.id}"),
        backgroundColor = when {
            isDup -> Color(0xFFFEF3C7).copy(alpha = 0.5f)
            isInc -> Color(0xFFFFE4E6).copy(alpha = 0.5f)
            else -> AppTheme.colors.surface
        },
        borderColor = when {
            isDup -> Color(0xFFF59E0B)
            isInc -> AppTheme.colors.error
            else -> AppTheme.colors.border
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AppTheme.dimensions.small + 2.dp)
        ) {
            // Header Row: Authentic Brand Logo / Bank & Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppTheme.dimensions.medium)
                ) {
                    // Recognizable brand logo badge or bank insignia
                    BrandLogoView(
                        name = if (transaction.merchant.isNotBlank()) transaction.merchant else transaction.bankName,
                        badgeSize = 40.dp,
                        shape = RoundedCornerShape(AppTheme.dimensions.chipCornerRadius + 2.dp),
                        fallbackCategory = selectedCategory
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppTheme.dimensions.extraSmall)
                        ) {
                            Text(
                                text = if (transaction.merchant.isNotBlank()) transaction.merchant else transaction.bankName,
                                style = AppTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (enrichedMerchant.isVerifiedBrand) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Verified Merchant",
                                    tint = AppTheme.colors.accent,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                        Text(
                            text = "${enrichedMerchant.subtitle} • ${transaction.bankName}${if (transaction.accountNumberLast4.isNotBlank()) " (••${transaction.accountNumberLast4})" else ""} • $dateFormatted",
                            style = AppTheme.typography.caption,
                            color = AppTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(AppTheme.dimensions.small))

                Text(
                    text = "${if (isCredit) "+" else "-"}$currencySymbol${"%,.2f".format(transaction.amount)}",
                    style = AppTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isCredit) AppTheme.colors.accent else AppTheme.colors.error
                )
            }

            // Merchant / Category / Ref Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (transaction.merchant.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(AppTheme.dimensions.extraSmall + 2.dp),
                        color = Color(enrichedMerchant.brandColorHex).copy(alpha = 0.12f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = AppTheme.dimensions.paddingSmall, vertical = AppTheme.dimensions.extraSmall - 1.dp),
                            horizontalArrangement = Arrangement.spacedBy(AppTheme.dimensions.extraSmall)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(enrichedMerchant.brandColorHex))
                            )
                            Text(
                                text = enrichedMerchant.cleanName,
                                style = AppTheme.typography.labelSmall,
                                color = Color(enrichedMerchant.brandColorHex)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppTheme.dimensions.extraSmall)
                ) {
                    IconButton(
                        onClick = onRefineWithGemini,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("gemini_refine_btn_${transaction.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Refine with Gemini AI",
                            tint = AppTheme.colors.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Category selector pill
                    Box {
                        Surface(
                            shape = RoundedCornerShape(AppTheme.dimensions.extraSmall + 2.dp),
                            color = AppTheme.colors.cardSecondary,
                            modifier = Modifier.clickable { showCategoryMenu = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = AppTheme.dimensions.paddingSmall, vertical = AppTheme.dimensions.extraSmall - 1.dp),
                                horizontalArrangement = Arrangement.spacedBy(AppTheme.dimensions.extraSmall)
                            ) {
                                Text(
                                    text = "Category: ${categories.find { it.first == selectedCategory }?.second ?: selectedCategory}",
                                    style = AppTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = AppTheme.colors.secondary
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(14.dp), tint = AppTheme.colors.textSecondary)
                            }
                        }

                        DropdownMenu(
                            expanded = showCategoryMenu,
                            onDismissRequest = { showCategoryMenu = false }
                        ) {
                            categories.forEach { (catKey, catLabel) ->
                                DropdownMenuItem(
                                    text = { Text(catLabel, style = AppTheme.typography.bodyMedium) },
                                    onClick = {
                                        selectedCategory = catKey
                                        showCategoryMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Reference Number
            if (transaction.referenceNumber.isNotBlank()) {
                Text(
                    text = "Ref/UTR: ${transaction.referenceNumber}",
                    style = AppTheme.typography.caption,
                    color = AppTheme.colors.textMuted
                )
            }

            // Matched planned debit banner if present
            if (transaction.matchedDebitId != null) {
                Surface(
                    shape = RoundedCornerShape(AppTheme.dimensions.chipCornerRadius),
                    color = AppTheme.colors.primaryLight,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(AppTheme.dimensions.paddingSmall),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppTheme.dimensions.extraSmall + 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = AppTheme.colors.primary,
                            modifier = Modifier.size(AppTheme.dimensions.iconSmall)
                        )
                        Text(
                            text = "Matches Planned Debit: '${transaction.matchedDebitTitle ?: "Planned Expense"}'",
                            style = AppTheme.typography.labelSmall,
                            color = AppTheme.colors.primary
                        )
                    }
                }
            }

            // Original SMS/Notification Text
            if (transaction.originalText.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(AppTheme.dimensions.chipCornerRadius),
                    color = AppTheme.colors.background,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = transaction.originalText,
                        style = AppTheme.typography.caption,
                        color = AppTheme.colors.textSecondary,
                        modifier = Modifier.padding(AppTheme.dimensions.paddingSmall)
                    )
                }
            }

            // Auto-Identified Duplicate Warning Banner
            if (isDup) {
                Surface(
                    shape = RoundedCornerShape(AppTheme.dimensions.chipCornerRadius),
                    color = Color(0xFFFEF3C7),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(AppTheme.dimensions.paddingSmall),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppTheme.dimensions.extraSmall + 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFB45309),
                            modifier = Modifier.size(AppTheme.dimensions.iconSmall)
                        )
                        Text(
                            text = dupReason.ifBlank { "Auto-identified duplicate transaction detected." },
                            style = AppTheme.typography.caption,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF92400E)
                        )
                    }
                }
            }

            // Auto-Identified Incorrect Entry Warning Banner
            if (isInc) {
                Surface(
                    shape = RoundedCornerShape(AppTheme.dimensions.chipCornerRadius),
                    color = Color(0xFFFFE4E6),
                    border = BorderStroke(1.dp, AppTheme.colors.error.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(AppTheme.dimensions.paddingSmall),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppTheme.dimensions.extraSmall + 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = AppTheme.colors.error,
                            modifier = Modifier.size(AppTheme.dimensions.iconSmall)
                        )
                        Text(
                            text = incReason.ifBlank { "Auto-identified incorrect entry or non-financial alert." },
                            style = AppTheme.typography.caption,
                            fontWeight = FontWeight.Medium,
                            color = AppTheme.colors.error
                        )
                    }
                }
            }

            // Action Buttons: Approve & Reject
            val approveButtonText = when {
                isDup -> "Approve Anyway"
                isInc -> if (isCredit) "Approve Income" else "Approve Expense"
                transaction.matchedDebitId != null -> "Mark as Paid"
                isCredit -> "Approve Income"
                else -> "Approve Expense"
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AppTheme.dimensions.extraSmall),
                horizontalArrangement = Arrangement.spacedBy(AppTheme.dimensions.small)
            ) {
                OutlinedButton(
                    onClick = onReject,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppTheme.colors.error),
                    shape = RoundedCornerShape(AppTheme.dimensions.chipCornerRadius),
                    contentPadding = PaddingValues(horizontal = AppTheme.dimensions.paddingSmall, vertical = AppTheme.dimensions.paddingExtraSmall),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = AppTheme.dimensions.minTouchTarget)
                        .testTag("reject_tx_${transaction.id}")
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(AppTheme.dimensions.iconSmall))
                    Spacer(modifier = Modifier.width(AppTheme.dimensions.extraSmall))
                    Text(
                        text = if (isDup) "Dismiss Duplicate" else if (isInc) "Reject Incorrect" else "Reject",
                        style = AppTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = { onApprove(selectedCategory) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDup) Color(0xFFD97706) else if (isInc) AppTheme.colors.primary else AppTheme.colors.accent
                    ),
                    shape = RoundedCornerShape(AppTheme.dimensions.chipCornerRadius),
                    contentPadding = PaddingValues(horizontal = AppTheme.dimensions.paddingSmall, vertical = AppTheme.dimensions.paddingExtraSmall),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = AppTheme.dimensions.minTouchTarget)
                        .testTag("approve_tx_${transaction.id}")
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(AppTheme.dimensions.iconSmall), tint = Color.White)
                    Spacer(modifier = Modifier.width(AppTheme.dimensions.extraSmall))
                    Text(
                        text = approveButtonText,
                        style = AppTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
