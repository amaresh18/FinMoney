package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.*
import com.example.data.repository.FinMoneyRepository
import com.example.ui.FinMoneyViewModel
import com.example.ui.PeerThread
import com.example.ui.components.*
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LentBorrowedScreen(
    viewModel: FinMoneyViewModel,
    onOpenAddLoan: (type: LoanType) -> Unit
) {
    val currencySymbol by viewModel.currencySymbol.collectAsStateWithLifecycle()
    val allLoans by viewModel.allLoans.collectAsStateWithLifecycle()
    val peerThreads by viewModel.peerThreads.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val summary by viewModel.lentBorrowedSummary.collectAsStateWithLifecycle()

    var selectedThreadContact by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") }

    var loanToEdit by remember { mutableStateOf<LoanTransaction?>(null) }
    var loanToDelete by remember { mutableStateOf<LoanTransaction?>(null) }
    var paymentLoan by remember { mutableStateOf<LoanTransaction?>(null) }
    var paymentToDelete by remember { mutableStateOf<Pair<Long, PaymentRecord>?>(null) }
    var viewingProofUri by remember { mutableStateOf<String?>(null) }
    var showAddDialogForThread by remember { mutableStateOf<Pair<LoanType, String>?>(null) }
    var showProfileEditor by remember { mutableStateOf(false) }
    var inviteDialogData by remember { mutableStateOf<InviteDialogData?>(null) }

    // If a thread is selected, show the Single Thread Conversation / Ledger View
    val currentThread = remember(peerThreads, selectedThreadContact) {
        if (selectedThreadContact == null) null
        else peerThreads.firstOrNull { 
            (if (it.counterpartyContact.isNotBlank()) it.counterpartyContact else it.counterpartyName) == selectedThreadContact 
        }
    }

    if (currentThread != null) {
        BackHandler {
            selectedThreadContact = null
        }

        SinglePeerThreadConversationView(
            thread = currentThread,
            userProfile = userProfile,
            currencySymbol = currencySymbol,
            onBack = { selectedThreadContact = null },
            onAddTransaction = { type ->
                if (userProfile?.phoneNumber.isNullOrBlank()) {
                    showProfileEditor = true
                } else {
                    showAddDialogForThread = type to currentThread.counterpartyName
                }
            },
            onEditLoan = { loan -> loanToEdit = loan },
            onDeleteLoan = { loan -> loanToDelete = loan },
            onSignLoan = { loanId, isUser, signerName ->
                viewModel.signAgreement(loanId, isUser, signerName)
            },
            onRecordPayment = { loan -> paymentLoan = loan },
            onRequestSettlement = { loanId ->
                viewModel.requestSettlement(loanId, userProfile?.name ?: "You")
            },
            onConfirmSettlement = { loanId, approve ->
                viewModel.confirmSettlement(loanId, approve, userProfile?.name ?: "You")
            },
            onApprovePayment = { loanId, paymentId ->
                viewModel.approvePartialPayment(loanId, paymentId, userProfile?.name ?: "You")
            },
            onRejectPayment = { loanId, paymentId ->
                viewModel.rejectPartialPayment(loanId, paymentId, userProfile?.name ?: "You")
            },
            onDeletePayment = { loanId, payment ->
                paymentToDelete = loanId to payment
            },
            onRequestPaymentDeletion = { loanId, paymentId ->
                viewModel.requestPaymentDeletion(loanId, paymentId, userProfile?.name ?: "You")
            },
            onConfirmPaymentDeletion = { loanId, paymentId, approve ->
                viewModel.confirmPaymentDeletion(loanId, paymentId, approve, userProfile?.name ?: "You")
            },
            onRequestLoanDeletion = { loanId ->
                viewModel.requestLoanDeletion(loanId, userProfile?.name ?: "You")
            },
            onConfirmLoanDeletion = { loanId, approve ->
                viewModel.confirmLoanDeletion(loanId, approve, userProfile?.name ?: "You")
            },
            onViewProof = { uri -> viewingProofUri = uri },
            onSendReminder = { loanId ->
                val targetLoan = allLoans.firstOrNull { it.id == loanId }
                if (targetLoan != null && targetLoan.counterpartyContact.isNotBlank()) {
                    viewModel.checkUserRegistered(targetLoan.counterpartyContact) { isRegistered ->
                        if (!isRegistered) {
                            inviteDialogData = InviteDialogData(
                                name = targetLoan.counterpartyName,
                                phone = targetLoan.counterpartyContact,
                                amount = targetLoan.totalAmount,
                                isGiven = targetLoan.type == LoanType.GIVEN.name
                            )
                        }
                    }
                }
                viewModel.sendReminder(loanId)
            }
        )
    } else {
        // --- THREAD LIST / OVERVIEW VIEW (Contact Level Khaata) ---
        val filteredThreads = remember(peerThreads, searchQuery, selectedFilter) {
            var list = peerThreads
            if (searchQuery.isNotBlank()) {
                list = list.filter {
                    it.counterpartyName.contains(searchQuery, ignoreCase = true) ||
                    it.counterpartyContact.contains(searchQuery, ignoreCase = true)
                }
            }
            when (selectedFilter) {
                "GIVEN" -> list.filter { it.netBalance > 0 }
                "TAKEN" -> list.filter { it.netBalance < 0 }
                "PENDING" -> list.filter { it.pendingActionCount > 0 }
                "SETTLED" -> list.filter { it.netBalance == 0.0 && it.transactions.isNotEmpty() }
                else -> list
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
        ) {
            // 0. Phone Number Required Banner (Requirement #1)
            if (userProfile?.phoneNumber.isNullOrBlank()) {
                item {
                    IndCard(
                        backgroundColor = Color(0xFFFEF2F2),
                        borderColor = IndRed.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth().testTag("phone_required_banner")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = IndRed, modifier = Modifier.size(20.dp))
                                Text(
                                    text = "Phone Number Required for Borrow / Lent",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = IndRed
                                )
                            }
                            Text(
                                text = "Lending and borrowing are tracked securely based on verified mobile phone numbers. Please set up your phone number in your profile to send agreements, verify repayments, and manage peer records.",
                                style = MaterialTheme.typography.bodySmall,
                                color = IndTextPrimary
                            )
                            Button(
                                onClick = { showProfileEditor = true },
                                colors = ButtonDefaults.buttonColors(containerColor = IndNavyHeader),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().height(40.dp)
                            ) {
                                Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Complete Profile & Add Phone Number", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // 1. Overview Khaata Balance Card
            item {
                IndCard(
                    backgroundColor = IndSurface,
                    borderColor = IndBorder,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (summary.netBalance >= 0) IndGreenLight else IndRedLight,
                                modifier = Modifier.padding(bottom = 6.dp)
                            ) {
                                Text(
                                    text = "NET KHAATA / PEER BALANCE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (summary.netBalance >= 0) IndGreenDark else IndRedDark,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }

                            Text(
                                text = "$currencySymbol${"%,.0f".format(Math.abs(summary.netBalance))}",
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (summary.netBalance >= 0) IndGreenDark else IndRed
                            )
                            Text(
                                text = if (summary.netBalance > 0) "Overall: People owe you in net" else if (summary.netBalance < 0) "Overall: You owe people in net" else "All accounts balanced out",
                                style = MaterialTheme.typography.bodySmall,
                                color = IndTextSecondary
                            )
                        }

                        // User profile badge
                        if (userProfile != null) {
                            Column(horizontalAlignment = Alignment.End) {
                                Surface(
                                    shape = CircleShape,
                                    color = IndBlueLight,
                                    border = androidx.compose.foundation.BorderStroke(1.5.dp, IndBlue),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    if (userProfile?.profilePicUri?.isNotBlank() == true) {
                                        AsyncImage(
                                            model = userProfile?.profilePicUri,
                                            contentDescription = "My Profile",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = (userProfile?.name?.take(1) ?: "Y").uppercase(),
                                                fontWeight = FontWeight.Bold,
                                                color = IndBlue,
                                                fontSize = 16.sp
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = userProfile?.name ?: "You",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = IndTextPrimary,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Lent vs Borrowed row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(IndCardSecondary)
                            .border(1.dp, IndBorderSubtle, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(IndGreen))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Money Given (Lent)", style = MaterialTheme.typography.labelSmall, color = IndTextSecondary)
                            }
                            Text(
                                text = "$currencySymbol${"%,.0f".format(summary.totalLent)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = IndGreenDark
                            )
                        }

                        Box(modifier = Modifier.width(1.dp).height(36.dp).background(IndBorder))

                        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(IndRed))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Money Taken (Owed)", style = MaterialTheme.typography.labelSmall, color = IndTextSecondary)
                            }
                            Text(
                                text = "$currencySymbol${"%,.0f".format(summary.totalBorrowed)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = IndRed
                            )
                        }
                    }

                    if (summary.pendingApprovalCount > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = IndAmberLight,
                            border = androidx.compose.foundation.BorderStroke(1.dp, IndAmber.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.Draw, contentDescription = null, tint = IndAmber, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "${summary.pendingApprovalCount} agreement(s) awaiting mutual digital signature",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = IndAmber
                                )
                            }
                        }
                    }
                }
            }

            // 2. Action Buttons (Give/Lend and Take/Borrow)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            if (userProfile?.phoneNumber.isNullOrBlank()) {
                                showProfileEditor = true
                            } else {
                                onOpenAddLoan(LoanType.GIVEN)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("give_money_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = IndBlue),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("+ Give (Lend)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            if (userProfile?.phoneNumber.isNullOrBlank()) {
                                showProfileEditor = true
                            } else {
                                onOpenAddLoan(LoanType.TAKEN)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("take_money_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = IndNavyHeader),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("- Take (Borrow)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by contact name or phone number...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = IndTextMuted) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = IndSurface,
                        unfocusedContainerColor = IndSurface
                    )
                )
            }

            // Filter Chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val filters = listOf(
                        "ALL" to "All Contacts",
                        "GIVEN" to "You'll Get",
                        "TAKEN" to "You'll Pay",
                        "PENDING" to "Pending Sign ✍️",
                        "SETTLED" to "Settled 🤝"
                    )
                    items(filters) { (key, label) ->
                        FilterChip(
                            selected = selectedFilter == key,
                            onClick = { selectedFilter = key },
                            label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = IndBlueLight,
                                selectedLabelColor = IndBlue
                            )
                        )
                    }
                }
            }

            // Contact Threads Title
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Contact Threads (${filteredThreads.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = IndTextPrimary
                    )
                    Text(
                        text = "Tap to open chat thread →",
                        style = MaterialTheme.typography.labelSmall,
                        color = IndBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // List of Peer Threads
            if (filteredThreads.isEmpty()) {
                item {
                    IndCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.ContactPhone, contentDescription = null, tint = IndTextMuted, modifier = Modifier.size(48.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) "No contact thread found matching '$searchQuery'" else "No peer loan agreements yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = IndTextPrimary
                            )
                            Text(
                                text = "Start by giving or taking money with contacts. Every record is organized in a single thread with credit & debit flags.",
                                style = MaterialTheme.typography.bodySmall,
                                color = IndTextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            } else {
                items(filteredThreads, key = { it.counterpartyContact.ifBlank { it.counterpartyName } }) { thread ->
                    PeerThreadContactCard(
                        thread = thread,
                        currencySymbol = currencySymbol,
                        onClick = {
                            selectedThreadContact = thread.counterpartyContact.ifBlank { thread.counterpartyName }
                        }
                    )
                }
            }
        }
    }

    // --- Dialogs ---

    // Add Transaction from within Thread
    if (showAddDialogForThread != null) {
        val (type, defaultName) = showAddDialogForThread!!
        AddEditLoanDialog(
            currencySymbol = currencySymbol,
            initialType = type,
            existingLoans = allLoans,
            onSavePayment = { loanId, amount, note, proof, direct ->
                viewModel.recordPartialPayment(loanId, amount, note, proof, userProfile?.name ?: "You", direct)
                showAddDialogForThread = null
            },
            onDismiss = { showAddDialogForThread = null },
            onSave = { _, loanType, amount, name, contact, rate, isMonthly, start, due, note, proof ->
                val finalName = name.ifBlank { defaultName }
                viewModel.createLoan(
                    type = loanType,
                    amount = amount,
                    counterpartyName = finalName,
                    counterpartyContact = contact,
                    interestRatePercent = rate,
                    isMonthlyInterest = isMonthly,
                    startDateTimestamp = start,
                    dueDateTimestamp = due,
                    note = note,
                    proofUri = proof
                )
                if (contact.isNotBlank()) {
                    viewModel.checkUserRegistered(contact) { isRegistered ->
                        if (!isRegistered) {
                            inviteDialogData = InviteDialogData(
                                name = finalName,
                                phone = contact,
                                amount = amount,
                                isGiven = loanType == LoanType.GIVEN
                            )
                        }
                    }
                }
                showAddDialogForThread = null
            }
        )
    }

    // Edit Loan Dialog (Editable before approval)
    if (loanToEdit != null) {
        AddEditLoanDialog(
            loanToEdit = loanToEdit,
            currencySymbol = currencySymbol,
            onDismiss = { loanToEdit = null },
            onSave = { loanId, loanType, amount, name, contact, rate, isMonthly, start, due, note, proof ->
                val updated = loanToEdit!!.copy(
                    type = loanType.name,
                    totalAmount = amount,
                    counterpartyName = name,
                    counterpartyContact = contact,
                    interestRatePercent = rate,
                    isMonthlyInterest = isMonthly,
                    startDateTimestamp = start,
                    dueDateTimestamp = due,
                    note = note,
                    proofUri = proof,
                    userSigned = true,
                    counterpartySigned = false,
                    status = ApprovalStatus.PENDING_APPROVAL.name
                )
                viewModel.updateLoan(updated)
                loanToEdit = null
            }
        )
    }

    // Delete Confirmation Dialog (Direct Delete or Mutual Approval Request)
    if (loanToDelete != null) {
        val currentUserName = userProfile?.name?.trim() ?: "You"
        val isMutuallySigned = loanToDelete!!.userSigned && loanToDelete!!.counterpartySigned
        AlertDialog(
            onDismissRequest = { loanToDelete = null },
            icon = { Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = IndRed) },
            title = { Text("Delete Transaction Record", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Delete record of $currencySymbol${"%,.0f".format(loanToDelete!!.totalAmount)} with ${loanToDelete!!.counterpartyName}?",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (isMutuallySigned)
                            "• Delete Now: Permanently deletes this transaction and all associated payment logs immediately.\n• Request Approval: Sends a deletion request to ${loanToDelete!!.counterpartyName} for mutual confirmation."
                            else "This will permanently remove this transaction and its payment logs from your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = IndTextSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteLoan(loanToDelete!!.id)
                        loanToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndRed)
                ) {
                    Text("Delete Now", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { loanToDelete = null }) {
                        Text("Cancel")
                    }
                    if (isMutuallySigned && loanToDelete!!.counterpartyContact.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                viewModel.requestLoanDeletion(loanToDelete!!.id, currentUserName)
                                loanToDelete = null
                            }
                        ) {
                            Text("Request Approval")
                        }
                    }
                }
            }
        )
    }

    // Payment Record Deletion Dialog (Direct Delete or Mutual Approval Request)
    if (paymentToDelete != null) {
        val (loanId, payRecord) = paymentToDelete!!
        AlertDialog(
            onDismissRequest = { paymentToDelete = null },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = IndRed) },
            title = { Text("Delete Payment Record", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Are you sure you want to delete this payment record of $currencySymbol${"%,.0f".format(payRecord.amount)}?",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "• Delete Now: Immediately removes this payment entry, updates the settled amount, and recalculates remaining dues.\n• Request Confirmation: Asks the other party to approve before removing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = IndTextSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePaymentRecord(loanId, payRecord.id, userProfile?.name ?: "You")
                        paymentToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndRed)
                ) {
                    Text("Delete Now", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { paymentToDelete = null }
                    ) {
                        Text("Cancel")
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.requestPaymentDeletion(loanId, payRecord.id, userProfile?.name ?: "You")
                            paymentToDelete = null
                        }
                    ) {
                        Text("Request Approval")
                    }
                }
            }
        )
    }

    // Record Payment Dialog
    if (paymentLoan != null) {
        RecordPaymentDialog(
            loan = paymentLoan!!,
            currencySymbol = currencySymbol,
            userProfile = userProfile,
            onDismiss = { paymentLoan = null },
            onSavePayment = { amount, note, proof, isDirectApproval ->
                viewModel.recordPartialPayment(
                    loanId = paymentLoan!!.id,
                    amount = amount,
                    note = note,
                    proofUri = proof,
                    recordedBy = userProfile?.name ?: "You",
                    isDirectApproval = isDirectApproval
                )
                paymentLoan = null
            }
        )
    }

    // Full-Screen Image Viewer
    if (viewingProofUri != null) {
        FullScreenImageViewerDialog(
            proofUri = viewingProofUri!!,
            onDismiss = { viewingProofUri = null }
        )
    }

    // Small Profile Page Dialog (Requirement #1)
    if (showProfileEditor) {
        ProfileDialog(
            userProfile = userProfile,
            onDismiss = { showProfileEditor = false },
            onSaveProfile = { first, last, phone, email, pic ->
                viewModel.saveUserProfile(
                    firstName = first,
                    lastName = last,
                    phoneNumber = phone,
                    email = email,
                    profilePicUri = pic
                )
                showProfileEditor = false
            },
            onSignOut = {
                viewModel.clearUserProfile()
                showProfileEditor = false
            }
        )
    }

    // Invite Counterparty Dialog (WhatsApp, SMS, Social Share)
    if (inviteDialogData != null) {
        val data = inviteDialogData!!
        InviteCounterpartyDialog(
            counterpartyName = data.name,
            counterpartyPhone = data.phone,
            amount = data.amount,
            isGiven = data.isGiven,
            currencySymbol = currencySymbol,
            onDismiss = { inviteDialogData = null }
        )
    }
}

@Composable
fun PeerThreadContactCard(
    thread: PeerThread,
    currencySymbol: String,
    onClick: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    IndCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("peer_thread_${thread.counterpartyName}"),
        backgroundColor = IndSurface,
        borderColor = if (thread.pendingActionCount > 0) IndAmber.copy(alpha = 0.5f) else IndBorder
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Avatar with Badge
                Box {
                    Surface(
                        shape = CircleShape,
                        color = IndNavyHeaderLight,
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, IndNavyHeader),
                        modifier = Modifier.size(48.dp)
                    ) {
                        if (thread.counterpartyProfilePicUri.isNotBlank()) {
                            AsyncImage(
                                model = thread.counterpartyProfilePicUri,
                                contentDescription = thread.counterpartyName,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = thread.counterpartyName.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 18.sp
                                )
                            }
                        }
                    }

                    if (thread.pendingActionCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(IndAmber)
                                .align(Alignment.BottomEnd)
                                .border(1.5.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("!", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = thread.counterpartyName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = IndTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (thread.counterpartyContact.isNotBlank()) {
                        Text(
                            text = "+91 ${thread.counterpartyContact}",
                            style = MaterialTheme.typography.bodySmall,
                            color = IndTextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    Text(
                        text = "${thread.transactions.size} transactions • Last: ${dateFormatter.format(Date(thread.lastActivityTimestamp))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = IndTextMuted,
                        fontSize = 10.sp
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (thread.netBalance > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = IndGreenLight
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = IndGreenDark, modifier = Modifier.size(12.dp))
                            Text(
                                text = "You'll Get $currencySymbol${"%,.0f".format(thread.netBalance)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = IndGreenDark,
                                fontSize = 11.sp
                            )
                        }
                    }
                } else if (thread.netBalance < 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = IndRedLight
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Filled.ArrowDownward, contentDescription = null, tint = IndRed, modifier = Modifier.size(12.dp))
                            Text(
                                text = "You'll Pay $currencySymbol${"%,.0f".format(Math.abs(thread.netBalance))}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = IndRed,
                                fontSize = 11.sp
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = IndBorderSubtle
                    ) {
                        Text(
                            text = "Settled Up 🤝",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = IndTextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (thread.pendingActionCount > 0) {
                    Text(
                        text = "✍️ ${thread.pendingActionCount} Sign Needed",
                        style = MaterialTheme.typography.labelSmall,
                        color = IndAmber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SinglePeerThreadConversationView(
    thread: PeerThread,
    userProfile: UserProfile?,
    currencySymbol: String,
    onBack: () -> Unit,
    onAddTransaction: (LoanType) -> Unit,
    onEditLoan: (LoanTransaction) -> Unit,
    onDeleteLoan: (LoanTransaction) -> Unit,
    onSignLoan: (loanId: Long, isUser: Boolean, signerName: String) -> Unit,
    onRecordPayment: (LoanTransaction) -> Unit,
    onRequestSettlement: (loanId: Long) -> Unit,
    onConfirmSettlement: (loanId: Long, approve: Boolean) -> Unit,
    onApprovePayment: (loanId: Long, paymentId: String) -> Unit = { _, _ -> },
    onRejectPayment: (loanId: Long, paymentId: String) -> Unit = { _, _ -> },
    onRequestPaymentDeletion: (loanId: Long, paymentId: String) -> Unit = { _, _ -> },
    onConfirmPaymentDeletion: (loanId: Long, paymentId: String, approve: Boolean) -> Unit = { _, _, _ -> },
    onRequestLoanDeletion: (loanId: Long) -> Unit = {},
    onConfirmLoanDeletion: (loanId: Long, approve: Boolean) -> Unit = { _, _ -> },
    onDeletePayment: (loanId: Long, payment: PaymentRecord) -> Unit = { _, _ -> },
    onViewProof: (String) -> Unit,
    onSendReminder: (loanId: Long) -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var showReminderMenu by remember { mutableStateOf(false) }

    val sortedTransactions = remember(thread.transactions) {
        thread.transactions.sortedBy { it.startDateTimestamp }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = IndNavyHeaderLight,
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, IndNavyHeader),
                            modifier = Modifier.size(40.dp)
                        ) {
                            if (thread.counterpartyProfilePicUri.isNotBlank()) {
                                AsyncImage(
                                    model = thread.counterpartyProfilePicUri,
                                    contentDescription = thread.counterpartyName,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = thread.counterpartyName.take(1).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }

                        Column {
                            Text(
                                text = thread.counterpartyName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = IndTextPrimary,
                                maxLines = 1
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (thread.counterpartyContact.isNotBlank()) {
                                    Text(
                                        text = "+91 ${thread.counterpartyContact}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = IndTextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                                Text(
                                    text = "• Mutual Agreement 🤝",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = IndGreenDark,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = IndNavyHeader)
                    }
                },
                actions = {
                    // Quick Call/WhatsApp or Reminder
                    Box {
                        IconButton(onClick = { showReminderMenu = true }) {
                            Icon(Icons.Filled.NotificationsActive, contentDescription = "Remind", tint = IndBlue)
                        }
                        DropdownMenu(
                            expanded = showReminderMenu,
                            onDismissRequest = { showReminderMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Send Instant SMS Reminder") },
                                onClick = {
                                    showReminderMenu = false
                                    if (thread.counterpartyContact.isNotBlank()) {
                                        val smsIntent = Intent(Intent.ACTION_VIEW).apply {
                                            data = Uri.parse("sms:${thread.counterpartyContact}")
                                            putExtra("sms_body", "Hi ${thread.counterpartyName}, this is a friendly reminder regarding our pending agreement balance of $currencySymbol${"%,.0f".format(Math.abs(thread.netBalance))} on FinMoney.")
                                        }
                                        try { context.startActivity(smsIntent) } catch (e: Exception) {}
                                    }
                                    if (sortedTransactions.isNotEmpty()) onSendReminder(sortedTransactions.last().id)
                                },
                                leadingIcon = { Icon(Icons.Filled.Sms, contentDescription = null, tint = IndBlue) }
                            )
                            DropdownMenuItem(
                                text = { Text("Send WhatsApp Reminder") },
                                onClick = {
                                    showReminderMenu = false
                                    if (thread.counterpartyContact.isNotBlank()) {
                                        val url = "https://api.whatsapp.com/send?phone=+91${thread.counterpartyContact}&text=" + Uri.encode("Hi ${thread.counterpartyName}, friendly reminder about our pending balance of $currencySymbol${"%,.0f".format(Math.abs(thread.netBalance))} on FinMoney.")
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        try { context.startActivity(intent) } catch (e: Exception) {}
                                    }
                                },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = IndGreenDark) }
                            )
                            DropdownMenuItem(
                                text = { Text("Schedule 1-Day Advance Alert") },
                                onClick = {
                                    showReminderMenu = false
                                    if (sortedTransactions.isNotEmpty()) onSendReminder(sortedTransactions.last().id)
                                },
                                leadingIcon = { Icon(Icons.Filled.Alarm, contentDescription = null, tint = IndAmber) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = IndSurface)
            )
        },
        bottomBar = {
            // Bottom Action Bar: You Gave (Lend) & You Got (Borrow)
            Surface(
                color = IndSurface,
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, IndBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onAddTransaction(LoanType.GIVEN) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("thread_you_gave_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = IndBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("YOU GAVE (₹)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = { onAddTransaction(LoanType.TAKEN) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("thread_you_got_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = IndNavyHeader),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("YOU GOT (₹)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(IndBackground)
        ) {
            // Thread Summary Banner
            Surface(
                color = IndCardSecondary,
                border = androidx.compose.foundation.BorderStroke(1.dp, IndBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = IndNavyHeader, modifier = Modifier.size(18.dp))
                        Text(
                            text = "Net Balance with ${thread.counterpartyName}:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = IndTextPrimary
                        )
                    }

                    Text(
                        text = if (thread.netBalance > 0) "You'll Get $currencySymbol${"%,.0f".format(thread.netBalance)}"
                               else if (thread.netBalance < 0) "You'll Pay $currencySymbol${"%,.0f".format(Math.abs(thread.netBalance))}"
                               else "Settled Up 🤝",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (thread.netBalance > 0) IndGreenDark else if (thread.netBalance < 0) IndRed else IndTextSecondary
                    )
                }
            }

            // Chat / Transaction Stream
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
            ) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = IndBlueLight,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "🔒 Legally Binding 2-Party Agreement Thread with Digital Signatures",
                                style = MaterialTheme.typography.labelSmall,
                                color = IndBlue,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                items(sortedTransactions, key = { it.id }) { loan ->
                    TransactionBubbleCard(
                        loan = loan,
                        userProfile = userProfile,
                        currencySymbol = currencySymbol,
                        onEdit = { onEditLoan(loan) },
                        onDelete = { onDeleteLoan(loan) },
                        onSign = { isUser ->
                            val signer = if (isUser) (userProfile?.name ?: "You") else loan.counterpartyName
                            onSignLoan(loan.id, isUser, signer)
                        },
                        onRecordPayment = { onRecordPayment(loan) },
                        onRequestSettlement = { onRequestSettlement(loan.id) },
                        onConfirmSettlement = { approve -> onConfirmSettlement(loan.id, approve) },
                        onApprovePayment = { paymentId -> onApprovePayment(loan.id, paymentId) },
                        onRejectPayment = { paymentId -> onRejectPayment(loan.id, paymentId) },
                        onRequestPaymentDeletion = onRequestPaymentDeletion,
                        onConfirmPaymentDeletion = onConfirmPaymentDeletion,
                        onDeletePayment = onDeletePayment,
                        onRequestLoanDeletion = { onRequestLoanDeletion(loan.id) },
                        onConfirmLoanDeletion = { approve -> onConfirmLoanDeletion(loan.id, approve) },
                        onViewProof = onViewProof,
                        onSendReminder = { onSendReminder(loan.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionBubbleCard(
    loan: LoanTransaction,
    userProfile: UserProfile?,
    currencySymbol: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSign: (isUser: Boolean) -> Unit,
    onRecordPayment: () -> Unit,
    onRequestSettlement: () -> Unit,
    onConfirmSettlement: (Boolean) -> Unit,
    onApprovePayment: (paymentId: String) -> Unit = {},
    onRejectPayment: (paymentId: String) -> Unit = {},
    onRequestPaymentDeletion: (loanId: Long, paymentId: String) -> Unit = { _, _ -> },
    onConfirmPaymentDeletion: (loanId: Long, paymentId: String, Boolean) -> Unit = { _, _, _ -> },
    onDeletePayment: (loanId: Long, payment: PaymentRecord) -> Unit = { _, _ -> },
    onRequestLoanDeletion: () -> Unit = {},
    onConfirmLoanDeletion: (Boolean) -> Unit = {},
    onViewProof: (String) -> Unit,
    onSendReminder: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
    val currentPhone = userProfile?.phoneNumber?.trim() ?: ""
    val currentName = userProfile?.name?.trim() ?: "You"
    val creatorPhone = loan.creatorContact.trim()
    val isCreator = (creatorPhone.isNotBlank() && currentPhone.isNotBlank() && currentPhone.takeLast(10) == creatorPhone.takeLast(10)) ||
                    (creatorPhone.isBlank() && loan.createdBy.equals(currentName, ignoreCase = true)) ||
                    (creatorPhone.isBlank() && loan.createdBy == "You")

    val otherPartyName = if (isCreator) loan.counterpartyName else loan.createdBy

    val isGiven = if (isCreator) (loan.type == LoanType.GIVEN.name) else (loan.type == LoanType.TAKEN.name)
    val isApproved = loan.status == ApprovalStatus.APPROVED.name || loan.status == ApprovalStatus.SETTLED.name
    val isSettled = loan.status == ApprovalStatus.SETTLED.name
    val isPendingSettlement = loan.status == ApprovalStatus.PENDING_SETTLEMENT.name
    val isPendingApproval = loan.status == ApprovalStatus.PENDING_APPROVAL.name
    val paymentHistory = remember(loan.paymentHistoryJson) {
        FinMoneyRepository.parsePaymentHistory(loan.paymentHistoryJson)
    }

    val cardBorderColor = when {
        isSettled -> IndBorder
        isPendingSettlement -> IndAmber
        !loan.counterpartySigned || !loan.userSigned -> IndAmber
        isGiven -> IndGreen.copy(alpha = 0.5f)
        else -> IndRed.copy(alpha = 0.5f)
    }

    val cardBgColor = IndSurface
    var showSettleConfirmDialog by remember { mutableStateOf(false) }

    if (showSettleConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSettleConfirmDialog = false },
            title = { Text("Request Settlement Approval", fontWeight = FontWeight.Bold) },
            text = {
                Text("Settle Up requires mutual confirmation. A request will be sent to ${loan.counterpartyName} to verify that all dues are cleared. The record will only be marked Settled after ${loan.counterpartyName} approves. Send request?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSettleConfirmDialog = false
                        onRequestSettlement()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndNavyHeader)
                ) {
                    Text("Send Request", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showSettleConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, cardBorderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tx_bubble_${loan.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Type Flag, Status & Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isGiven) IndGreenLight else IndRedLight
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                if (isGiven) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                                contentDescription = null,
                                tint = if (isGiven) IndGreenDark else IndRed,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (isGiven) "CREDIT (LENT)" else "DEBIT (BORROWED)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isGiven) IndGreenDark else IndRed,
                                fontSize = 10.sp
                            )
                        }
                    }

                    if (isSettled) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = IndBorderSubtle
                        ) {
                            Text(
                                text = "SETTLED UP 🤝",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = IndTextSecondary,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = dateFormatter.format(Date(loan.startDateTimestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = IndTextMuted,
                        fontSize = 11.sp
                    )

                    // Edit Action (allowed if pending)
                    if (isPendingApproval) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = IndBlue, modifier = Modifier.size(17.dp))
                        }
                    }

                    // Delete Action (Always visible and positioned at top right)
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = IndRed, modifier = Modifier.size(17.dp))
                    }
                }
            }

            // Amount and Note with Dynamic Interest Accrual (Requirement #6)
            val interestBreakdown = remember(loan, paymentHistory) {
                com.example.util.InterestCalculator.calculate(
                    principal = loan.totalAmount,
                    ratePercent = loan.interestRatePercent,
                    isMonthly = loan.isMonthlyInterest,
                    startTimestamp = loan.startDateTimestamp,
                    payments = paymentHistory
                )
            }

            val remainingDue = if (loan.interestRatePercent > 0.0) {
                interestBreakdown.totalRemainingDue
            } else {
                (loan.totalAmount - loan.settledAmount).coerceAtLeast(0.0)
            }
            val pendingPayments = paymentHistory.filter { !it.isApproved }
            val pendingPaymentsTotal = pendingPayments.sumOf { it.amount }
            val effectiveRemaining = (remainingDue - pendingPaymentsTotal).coerceAtLeast(0.0)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isGiven) "+ $currencySymbol${"%,.0f".format(remainingDue)}" else "- $currencySymbol${"%,.0f".format(remainingDue)}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isGiven) IndGreenDark else IndRed
                        )

                        if (loan.settledAmount > 0) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = IndGreenLight
                            ) {
                                Text(
                                    text = "Paid: $currencySymbol${"%,.0f".format(loan.settledAmount)}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = IndGreenDark,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    if (pendingPaymentsTotal > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = IndAmberLight,
                            border = androidx.compose.foundation.BorderStroke(1.dp, IndAmber.copy(alpha = 0.5f)),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Filled.Schedule, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(13.dp))
                                Text(
                                    text = "$currencySymbol${"%,.0f".format(pendingPaymentsTotal)} pending confirmation (Balance after verification: $currencySymbol${"%,.0f".format(effectiveRemaining)})",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB45309)
                                )
                            }
                        }
                    }

                    // Interest & Principal Status Card (Requirement #6)
                    if (loan.interestRatePercent > 0.0) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF8FAFC),
                            border = androidx.compose.foundation.BorderStroke(1.dp, IndNavyHeader.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFFEF3C7),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD97706).copy(alpha = 0.8f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(Icons.Filled.Percent, contentDescription = null, tint = Color(0xFF92400E), modifier = Modifier.size(12.dp))
                                            Text(
                                                text = "${loan.interestRatePercent}% ${if (loan.isMonthlyInterest) "/ month" else "/ year"} (${interestBreakdown.daysElapsed} days)",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFF92400E)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Accrued: $currencySymbol${"%,.0f".format(interestBreakdown.accruedInterest)}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (interestBreakdown.remainingInterestDue > 0) Color(0xFFB45309) else IndGreenDark
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Principal: $currencySymbol${"%,.0f".format(interestBreakdown.remainingPrincipalDue)}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = IndTextSecondary
                                    )
                                    Text(
                                        text = "Interest Due: $currencySymbol${"%,.0f".format(interestBreakdown.remainingInterestDue)}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (interestBreakdown.remainingInterestDue > 0) Color(0xFFB45309) else IndGreenDark
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = IndCardSecondary,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Total Repayable (Principal + Interest): $currencySymbol${"%,.0f".format(interestBreakdown.totalAmountWithInterest)}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = IndTextPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    } else if (loan.settledAmount > 0) {
                        Text(
                            text = "Original: $currencySymbol${"%,.0f".format(loan.totalAmount)} • Remaining Due: $currencySymbol${"%,.0f".format(remainingDue)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = IndTextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Line-Clamped & Expandable Note / Purpose View (INDmoney style)
                    if (loan.note.isNotBlank()) {
                        var isNoteExpanded by remember { mutableStateOf(false) }
                        val isLongNote = loan.note.length > 75

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = IndCardSecondary.copy(alpha = 0.7f),
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, IndBorderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .animateContentSize()
                                .clickable(enabled = isLongNote) { isNoteExpanded = !isNoteExpanded }
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Notes,
                                        contentDescription = null,
                                        tint = IndBlueDark,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "Purpose / Description",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = IndBlueDark,
                                        fontSize = 10.sp
                                    )
                                }

                                Text(
                                    text = loan.note,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = IndTextPrimary,
                                    fontSize = 12.sp,
                                    maxLines = if (isNoteExpanded) Int.MAX_VALUE else 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (isLongNote) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Text(
                                            text = if (isNoteExpanded) "Show less ▴" else "Read more ▾",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = IndBlue,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (loan.dueDateTimestamp != null) {
                        val dueStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(loan.dueDateTimestamp))
                        Text(
                            text = "Repayment Due: $dueStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = IndTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                // Proof Attachment Thumbnail (Multi-proof support)
                val loanProofs = remember(loan.proofFilesJson, loan.proofUri) {
                    val raw = if (loan.proofFilesJson.isNotBlank()) loan.proofFilesJson else loan.proofUri
                    com.example.util.ProofStorageHelper.parseProofFiles(raw)
                }
                if (loanProofs.isNotEmpty()) {
                    val firstProof = loanProofs.first()
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val localPath = com.example.util.ProofStorageHelper.ensureLocalPath(context, firstProof)

                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, IndBorder, RoundedCornerShape(8.dp))
                            .clickable {
                                val payload = if (loan.proofFilesJson.isNotBlank()) loan.proofFilesJson else loan.proofUri
                                onViewProof(payload)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (firstProof.isPdf) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Filled.PictureAsPdf, contentDescription = "PDF", tint = IndRed, modifier = Modifier.size(24.dp))
                                Text("PDF", fontSize = 8.sp, color = IndRed, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            AsyncImage(
                                model = if (localPath.isNotBlank()) localPath else firstProof.cloudBase64,
                                contentDescription = "Proof Screenshot",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        if (loanProofs.size > 1) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(topStart = 4.dp),
                                modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp)
                            ) {
                                Text("+${loanProofs.size - 1}", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 3.dp))
                            }
                        }
                    }
                }
            }

            // Digital Signatures Box (Mutual dual-party signatures with Avatars)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = IndCardSecondary,
                border = androidx.compose.foundation.BorderStroke(1.dp, IndBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "DIGITAL AGREEMENT SIGNATURE STATUS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = IndTextMuted,
                        fontSize = 9.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // User Signature Status
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (loan.userSigned) IndGreenLight else IndAmberLight,
                                modifier = Modifier.size(24.dp)
                            ) {
                                if (loan.userProfilePicUri.isNotBlank()) {
                                    AsyncImage(
                                        model = loan.userProfilePicUri,
                                        contentDescription = "User",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("U", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = IndGreenDark)
                                    }
                                }
                            }
                            Column {
                                Text("You", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = IndTextPrimary)
                                Text(
                                    text = if (loan.userSigned) "Signed ✍️" else "Pending",
                                    fontSize = 10.sp,
                                    color = if (loan.userSigned) IndGreenDark else IndAmber,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Counterparty Signature Status
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (loan.counterpartySigned) IndGreenLight else IndAmberLight,
                                modifier = Modifier.size(24.dp)
                            ) {
                                if (loan.counterpartyProfilePicUri.isNotBlank()) {
                                    AsyncImage(
                                        model = loan.counterpartyProfilePicUri,
                                        contentDescription = loan.counterpartyName,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(loan.counterpartyName.take(1), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = IndNavyHeader)
                                    }
                                }
                            }
                            Column {
                                Text(loan.counterpartyName, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = IndTextPrimary, maxLines = 1)
                                Text(
                                    text = if (loan.counterpartySigned) "Signed ✍️" else "Pending Sign ⏳",
                                    fontSize = 10.sp,
                                    color = if (loan.counterpartySigned) IndGreenDark else IndAmber,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Interactive Dual-Signing Buttons
                    if (!loan.userSigned) {
                        Button(
                            onClick = { onSign(true) },
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IndNavyHeader),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Sign Agreement as You ✍️", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (!loan.counterpartySigned) {
                        val currentPhone = userProfile?.phoneNumber?.trim() ?: ""
                        val targetPhone = loan.counterpartyContact.trim()
                        val creatorPhone = loan.creatorContact.trim()

                        // A user is the creator if their phone matches creatorContact or loan.createdBy matches their name
                        val isCreator = (creatorPhone.isNotBlank() && currentPhone.isNotBlank() && currentPhone.takeLast(10) == creatorPhone.takeLast(10)) ||
                                        (loan.createdBy == (userProfile?.name ?: "You"))

                        // A user is the target user only if registered with counterparty phone AND is NOT the creator
                        val isCurrentUserTheTarget = currentPhone.isNotBlank() && targetPhone.isNotBlank() &&
                            (currentPhone == targetPhone || currentPhone.takeLast(10) == targetPhone.takeLast(10)) &&
                            !isCreator

                        if (isCurrentUserTheTarget) {
                            // Only target user matching the contact number sees the Sign button
                            Button(
                                onClick = { onSign(false) },
                                modifier = Modifier.fillMaxWidth().height(38.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = IndBlue),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Approve & Sign Agreement as ${loan.counterpartyName} ✍️", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            // Record creator: cannot approve for counterparty!
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
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Filled.Schedule, contentDescription = null, tint = IndAmber, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = "Awaiting ${loan.counterpartyName}'s Digital Signature",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color(0xFFB45309)
                                        )
                                    }
                                    Text(
                                        text = if (targetPhone.isNotBlank()) "Target party (+91 $targetPhone) must sign on their FinMoney app." else "Target party must approve on their app.",
                                        fontSize = 11.sp,
                                        color = IndTextSecondary
                                    )
                                    Button(
                                        onClick = onSendReminder,
                                        modifier = Modifier.fillMaxWidth().height(34.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = IndNavyHeader),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Remind ${loan.counterpartyName} to Sign", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Payment History / Installments Log
            if (paymentHistory.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(IndBackground)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Payment Logs (${paymentHistory.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = IndTextSecondary
                        )
                        val approvedCount = paymentHistory.count { it.isApproved }
                        Text(
                            text = "$approvedCount approved",
                            fontSize = 10.sp,
                            color = IndTextMuted
                        )
                    }

                    paymentHistory.forEach { pay ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = IndSurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, IndBorderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = if (pay.isSettlement) "Full Settlement" else "Partial Payment",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = IndTextPrimary
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (pay.isApproved) IndGreenLight else IndAmberLight
                                            ) {
                                                Text(
                                                    text = if (pay.isApproved) "✓ Confirmed" else "⏳ Awaiting Confirmation",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (pay.isApproved) IndGreenDark else Color(0xFFB45309),
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${dateFormatter.format(Date(pay.paymentDateTimestamp))} • by ${pay.recordedBy}",
                                            fontSize = 10.sp,
                                            color = IndTextMuted
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "$currencySymbol${"%,.0f".format(pay.amount)}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (pay.isApproved) IndGreenDark else IndAmber
                                        )
                                        IconButton(
                                            onClick = { onDeletePayment(loan.id, pay) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete Payment Record", tint = IndRed, modifier = Modifier.size(17.dp))
                                        }
                                    }
                                }

                                if (pay.note.isNotBlank()) {
                                    var isPayNoteExpanded by remember { mutableStateOf(false) }
                                    val isLongPayNote = pay.note.length > 50

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .animateContentSize()
                                            .clickable(enabled = isLongPayNote) { isPayNoteExpanded = !isPayNoteExpanded },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Note: ${pay.note}",
                                            fontSize = 11.sp,
                                            color = IndTextSecondary,
                                            maxLines = if (isPayNoteExpanded) Int.MAX_VALUE else 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isLongPayNote) {
                                            Text(
                                                text = if (isPayNoteExpanded) " Less" else " More",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = IndBlue
                                            )
                                        }
                                    }
                                }

                                // Mutual Payment Deletion Banner (Requirement #3)
                                if (pay.deletionRequestedBy.isNotBlank()) {
                                    val currentUserName = userProfile?.name?.trim() ?: "You"
                                    val isRequester = pay.deletionRequestedBy.equals(currentUserName, ignoreCase = true) ||
                                                      (pay.deletionRequestedBy == "You" && currentUserName == "You")

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = IndAmberLight,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, IndAmber.copy(alpha = 0.5f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (isRequester) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Filled.Schedule, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(14.dp))
                                                Text(
                                                    text = "Deletion requested by You. Awaiting confirmation from ${loan.counterpartyName}.",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFB45309)
                                                )
                                            }
                                        } else {
                                            Column(
                                                modifier = Modifier.padding(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = "⚠️ ${pay.deletionRequestedBy} requested to delete this ₹${"%,.0f".format(pay.amount)} payment record. Both parties must confirm.",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFB45309)
                                                )
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    OutlinedButton(
                                                        onClick = { onConfirmPaymentDeletion(loan.id, pay.id, false) },
                                                        shape = RoundedCornerShape(6.dp),
                                                        modifier = Modifier.height(28.dp),
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                    ) {
                                                        Text("Decline", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = IndRed)
                                                    }
                                                    Button(
                                                        onClick = { onConfirmPaymentDeletion(loan.id, pay.id, true) },
                                                        shape = RoundedCornerShape(6.dp),
                                                        colors = ButtonDefaults.buttonColors(containerColor = IndRed),
                                                        modifier = Modifier.height(28.dp),
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                    ) {
                                                        Text("Confirm Delete Record", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Attached Proof Preview (Multiple files and screenshots support)
                                val payProofRaw = if (pay.proofFilesJson.isNotBlank()) pay.proofFilesJson else pay.proofUri
                                val payProofFiles = remember(payProofRaw) {
                                    com.example.util.ProofStorageHelper.parseProofFiles(payProofRaw)
                                }
                                if (payProofFiles.isNotEmpty()) {
                                    val context = androidx.compose.ui.platform.LocalContext.current
                                    val firstPayFile = payProofFiles.first()
                                    val localThumbPath = remember(firstPayFile) {
                                        com.example.util.ProofStorageHelper.ensureLocalPath(context, firstPayFile)
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(IndBackground)
                                            .border(1.dp, IndBorder, RoundedCornerShape(8.dp))
                                            .clickable { onViewProof(payProofRaw) }
                                            .padding(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .border(1.dp, IndBorderSubtle, RoundedCornerShape(6.dp))
                                                .background(Color(0xFF0F172A)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (firstPayFile.isPdf) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Icon(Icons.Filled.PictureAsPdf, contentDescription = "PDF", tint = IndRed, modifier = Modifier.size(20.dp))
                                                    Text("PDF", fontSize = 7.sp, color = IndRed, fontWeight = FontWeight.Bold)
                                                }
                                            } else {
                                                AsyncImage(
                                                    model = if (localThumbPath.isNotBlank()) localThumbPath else firstPayFile.cloudBase64,
                                                    contentDescription = "Payment Screenshot",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                            if (payProofFiles.size > 1) {
                                                Surface(
                                                    color = Color.Black.copy(alpha = 0.75f),
                                                    shape = RoundedCornerShape(topStart = 4.dp),
                                                    modifier = Modifier.align(Alignment.BottomEnd)
                                                ) {
                                                    Text("+${payProofFiles.size - 1}", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 3.dp))
                                                }
                                            }
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Icon(Icons.Filled.Receipt, contentDescription = null, tint = IndBlue, modifier = Modifier.size(13.dp))
                                                Text(
                                                    text = if (payProofFiles.size > 1) "Payment Proofs (${payProofFiles.size} files)" else "Payment Proof Attached",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = IndBlue
                                                )
                                            }
                                            Text(
                                                text = firstPayFile.name,
                                                fontSize = 10.sp,
                                                color = IndTextSecondary,
                                                maxLines = 1
                                            )
                                        }

                                        Text("View Proof", fontSize = 10.sp, color = IndBlue, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Action row: Confirm Received
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {

                                    if (!pay.isApproved) {
                                        val currentUserName = userProfile?.name?.trim() ?: "You"
                                        val isRecorder = pay.recordedBy.equals(currentUserName, ignoreCase = true) || (pay.recordedBy == "You" && currentUserName == "You")

                                        if (isRecorder) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = IndAmberLight
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(12.dp))
                                                    Text("Awaiting ${loan.counterpartyName}'s confirmation", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                                                }
                                            }
                                        } else {
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                OutlinedButton(
                                                    onClick = { onRejectPayment(pay.id) },
                                                    shape = RoundedCornerShape(6.dp),
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = IndRed),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(30.dp),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, IndRed.copy(alpha = 0.5f))
                                                ) {
                                                    Text("Decline ✕", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }

                                                Button(
                                                    onClick = { onApprovePayment(pay.id) },
                                                    shape = RoundedCornerShape(6.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = IndBlue),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(30.dp)
                                                ) {
                                                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(13.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Confirm Received ✓", fontSize = 10.sp, fontWeight = FontWeight.Bold)
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

            // Mutual Loan Deletion Banner
            if (loan.deletionRequestedBy.isNotBlank()) {
                val isDelRequester = loan.deletionRequestedBy.equals(currentName, ignoreCase = true) ||
                                     (isCreator && (loan.deletionRequestedBy == loan.createdBy || loan.deletionRequestedBy == "You")) ||
                                     (!isCreator && (loan.deletionRequestedBy == loan.counterpartyName))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = IndAmberLight,
                    border = androidx.compose.foundation.BorderStroke(1.dp, IndAmber.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isDelRequester) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.Schedule, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(16.dp))
                                Text(
                                    text = "Deletion requested by You. Awaiting confirmation from $otherPartyName.",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color(0xFFB45309)
                                )
                            }
                            OutlinedButton(
                                onClick = { onConfirmLoanDeletion(false) },
                                modifier = Modifier.fillMaxWidth().height(30.dp),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Cancel Deletion Request", fontSize = 10.sp, color = IndTextSecondary)
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.Warning, contentDescription = null, tint = IndRed, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "⚠️ ${loan.deletionRequestedBy} requested to delete this agreement",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = IndRed
                                )
                            }
                            Text("Do you confirm deleting this entire transaction record of ₹${"%,.0f".format(loan.totalAmount)}?", fontSize = 11.sp, color = IndTextPrimary)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onConfirmLoanDeletion(false) },
                                    modifier = Modifier.weight(1f).height(32.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Decline", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IndRed)
                                }
                                Button(
                                    onClick = { onConfirmLoanDeletion(true) },
                                    colors = ButtonDefaults.buttonColors(containerColor = IndRed),
                                    modifier = Modifier.weight(1f).height(32.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Confirm Delete 🗑️", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Settlement / Payment Actions
            if (!isSettled) {
                if (isPendingSettlement) {
                    val isSettlementRequester = if (loan.settlementRequestedBy.isBlank()) false else {
                        val req = loan.settlementRequestedBy.trim()
                        val isReqCreator = req.equals(loan.createdBy.trim(), ignoreCase = true) || req.equals("You", ignoreCase = true) || req.equals("CREATOR", ignoreCase = true)
                        val isReqCounterparty = req.equals(loan.counterpartyName.trim(), ignoreCase = true) || req.equals("COUNTERPARTY", ignoreCase = true)
                        if (isCreator) {
                            isReqCreator || req.equals(currentName, ignoreCase = true)
                        } else {
                            isReqCounterparty || req.equals(currentName, ignoreCase = true)
                        }
                    }

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
                            if (isSettlementRequester) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "Settlement requested by You. Awaiting confirmation from $otherPartyName.",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color(0xFFB45309)
                                    )
                                }
                                Text("Dual Approval: $otherPartyName must review and approve this settlement before it is closed.", fontSize = 10.sp, color = IndTextSecondary)
                                OutlinedButton(
                                    onClick = { onConfirmSettlement(false) },
                                    modifier = Modifier.fillMaxWidth().height(32.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = IndTextSecondary)
                                ) {
                                    Text("Cancel Settlement Request", fontSize = 11.sp)
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Filled.Handshake, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "🤝 ${loan.settlementRequestedBy} requested to settle this loan",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFFB45309)
                                    )
                                }
                                Text("Do you confirm that all dues have been cleared and this record can be closed?", fontSize = 11.sp, color = IndTextPrimary)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onConfirmSettlement(false) },
                                        modifier = Modifier.weight(1f).height(34.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("Decline", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IndRed)
                                    }

                                    Button(
                                        onClick = { onConfirmSettlement(true) },
                                        colors = ButtonDefaults.buttonColors(containerColor = IndBlue),
                                        modifier = Modifier.weight(1f).height(34.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("Confirm Settle 🤝", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onRecordPayment,
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Filled.Payment, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Log Payment", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { showSettleConfirmDialog = true },
                            modifier = Modifier.weight(1f).height(36.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IndNavyHeader),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Filled.Handshake, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Settle Up", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
