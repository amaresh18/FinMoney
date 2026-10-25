package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.example.data.model.LoanApprovalStatus
import com.example.domain.usecase.LoanLedgerMath
import com.example.domain.usecase.LoanPartyAuth
import com.example.domain.usecase.ValidateLoanLifecycleUseCase
import com.example.data.model.LoanTransaction
import com.example.data.model.LoanType
import com.example.service.LoanAgreementService
import com.example.ui.FinMoneyViewModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LentBorrowedScreen(
    viewModel: FinMoneyViewModel,
    onBack: () -> Unit = {},
    focusLoanId: Long? = null,
    focusLoanCloudId: String? = null,
    onFocusConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val loans by viewModel.allLoans.collectAsState()
    val ledgerEntries by viewModel.loanLedgerEntries.collectAsState()
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    var openLoanId by remember { mutableStateOf<Long?>(null) }
    val openLoan = loans.firstOrNull { it.id == openLoanId }
        ?: loans.firstOrNull { !focusLoanCloudId.isNullOrBlank() && it.cloudId == focusLoanCloudId }

    LaunchedEffect(focusLoanId, focusLoanCloudId, loans) {
        val match = loans.firstOrNull { it.id == focusLoanId }
            ?: loans.firstOrNull { !focusLoanCloudId.isNullOrBlank() && it.cloudId == focusLoanCloudId }
        if (match != null) {
            openLoanId = match.id
            onFocusConsumed()
        }
    }

    val myPhone = remember(userProfile) {
        LoanPartyAuth.phone10(userProfile?.phoneNumber ?: "")
    }
    val activeUserId = remember(userProfile) {
        userProfile?.userId?.takeIf { it.isNotBlank() } ?: viewModel.getActiveUserId()
    }

    if (openLoan != null) {
        LoanLedgerScreen(
            loan = openLoan,
            entries = ledgerEntries.filter { it.loanId == openLoan.id },
            currencySymbol = currencySymbol,
            myPhone = myPhone,
            activeUserId = activeUserId,
            viewModel = viewModel,
            onBack = { openLoanId = null }
        )
        return
    }

    var selectedContactKey by remember { mutableStateOf<String?>(null) }
    val contacts = remember(loans, ledgerEntries) { groupLoanContacts(loans, ledgerEntries) }
    val selectedContact = contacts.firstOrNull { it.key == selectedContactKey }
    val visibleLoans = selectedContact?.loans.orEmpty()

    BackHandler {
        if (selectedContactKey != null) selectedContactKey = null else onBack()
    }

    LaunchedEffect(Unit) {
        viewModel.loanActionMessage.collectLatest { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    val loanLifecycleRules = remember { ValidateLoanLifecycleUseCase() }

    var showAddLoanDialog by remember { mutableStateOf(false) }

    var editingLoan by remember { mutableStateOf<LoanTransaction?>(null) }
    var editAmountStr by remember { mutableStateOf("") }
    var editNotesStr by remember { mutableStateOf("") }
    var editNameStr by remember { mutableStateOf("") }
    var editPhoneStr by remember { mutableStateOf("") }
    var loanPendingDelete by remember { mutableStateOf<LoanTransaction?>(null) }

    val totalLent = remember(loans, ledgerEntries) {
        loans.filter { (it.type == LoanType.LENT || it.type == LoanType.GIVEN) && it.status != LoanApprovalStatus.REJECTED && it.status != LoanApprovalStatus.SETTLED }
            .sumOf { loanOutstanding(it, ledgerEntries) }
    }
    val totalBorrowed = remember(loans, ledgerEntries) {
        loans.filter { (it.type == LoanType.BORROWED || it.type == LoanType.TAKEN) && it.status != LoanApprovalStatus.REJECTED && it.status != LoanApprovalStatus.SETTLED }
            .sumOf { loanOutstanding(it, ledgerEntries) }
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
                Text(if (selectedContact == null) "Add record" else "New record", fontWeight = FontWeight.Bold)
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
            if (selectedContact == null) {
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
                        Text("To receive", fontSize = 11.sp, color = IndGreenDark, fontWeight = FontWeight.SemiBold)
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
                        Text("To pay", fontSize = 11.sp, color = IndRed, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("$currencySymbol${"%,.0f".format(totalBorrowed)}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = IndRed)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            }

            if (selectedContact == null) {
                Text("People", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = IndNavyHeader)
                Text("Net across every open record with each person", fontSize = 12.sp, color = IndTextSecondary)
            } else {
                Text(selectedContact.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = IndNavyHeader)
                val net = selectedContact.net
                Text(
                    when {
                        net > 0.5 -> "To receive $currencySymbol${"%,.0f".format(net)} · ${selectedContact.openCount} open"
                        net < -0.5 -> "To pay $currencySymbol${"%,.0f".format(-net)} · ${selectedContact.openCount} open"
                        else -> "All clear · ${selectedContact.loans.size} records"
                    },
                    fontSize = 12.sp,
                    color = IndTextSecondary
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            if (selectedContact == null && loans.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(48.dp), tint = IndTextSecondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No people yet", fontWeight = FontWeight.Bold, color = IndTextPrimary)
                        Text("Add a record to start a contact", fontSize = 12.sp, color = IndTextSecondary)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    if (selectedContact == null) {
                        items(contacts, key = { it.key }) { contact ->
                            ContactNetRow(contact, currencySymbol) { selectedContactKey = contact.key }
                        }
                    }
                    items(visibleLoans, key = { it.id }) { loan ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { openLoanId = loan.id },
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

                                        Text(
                                            if (selectedContact == null) counterparty else loan.notes.ifBlank { if (isLent) "Lent" else "Borrowed" },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = IndTextPrimary,
                                            maxLines = 1
                                        )
                                    }

                                    val isCreator = LoanPartyAuth.isCreator(
                                        loan.creatorPhone,
                                        myPhone,
                                        loan.userId,
                                        activeUserId
                                    )
                                    val canDelete = isCreator && loanLifecycleRules.canCreatorDelete(loan.status)
                                    val canEdit = isCreator &&
                                        loan.status != LoanApprovalStatus.SETTLED &&
                                        loan.status != LoanApprovalStatus.PENDING_SETTLEMENT
                                    if (canEdit || canDelete) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            if (canEdit) {
                                                IconButton(
                                                    onClick = {
                                                        editingLoan = loan
                                                        editAmountStr = if (loan.amendmentPending && loan.proposedAmount > 0.0) {
                                                            loan.proposedAmount.toString()
                                                        } else {
                                                            loan.totalAmount.toString()
                                                        }
                                                        editNotesStr = if (loan.amendmentPending) loan.proposedNotes else loan.notes
                                                        editNameStr = if (loan.amendmentPending && loan.proposedPersonName.isNotBlank()) {
                                                            loan.proposedPersonName
                                                        } else {
                                                            loan.personName.ifBlank { loan.counterpartyName }
                                                        }
                                                        editPhoneStr = if (loan.amendmentPending && loan.proposedPersonContact.isNotBlank()) {
                                                            loan.proposedPersonContact
                                                        } else {
                                                            loan.personContact.ifBlank { loan.counterpartyContact }
                                                        }
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.Edit, contentDescription = "Edit Record", tint = IndBlue, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                            if (canDelete) {
                                                IconButton(
                                                    onClick = { loanPendingDelete = loan },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete Record", tint = IndRed, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val left = loanOutstanding(loan, ledgerEntries)
                                    Column {
                                        Text(
                                            text = "$currencySymbol${"%,.0f".format(if (loan.status == LoanApprovalStatus.SETTLED) loan.totalAmount else left)}",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 18.sp,
                                            color = if (isLent) IndGreenDark else IndRed
                                        )
                                        if (loan.status != LoanApprovalStatus.SETTLED && left != loan.totalAmount) {
                                            Text(
                                                "Agreed $currencySymbol${"%,.0f".format(loan.totalAmount)} · left $currencySymbol${"%,.0f".format(left)}",
                                                fontSize = 11.sp,
                                                color = IndTextSecondary
                                            )
                                        } else {
                                            Text("Open payments", fontSize = 11.sp, color = IndTextSecondary)
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = when (loan.status) {
                                            LoanApprovalStatus.APPROVED, LoanApprovalStatus.MUTUAL_APPROVED -> IndGreenLight
                                            LoanApprovalStatus.SETTLED -> IndBlueLight
                                            LoanApprovalStatus.REJECTED -> IndRed.copy(alpha = 0.1f)
                                            else -> Color(0xFFFEF3C7)
                                        }
                                    ) {
                                        Text(
                                            text = loanStatusLabel(loan.status),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (loan.status) {
                                                LoanApprovalStatus.APPROVED, LoanApprovalStatus.MUTUAL_APPROVED -> IndGreenDark
                                                LoanApprovalStatus.SETTLED -> IndBlue
                                                LoanApprovalStatus.REJECTED -> IndRed
                                                else -> Color(0xFFD97706)
                                            },
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                val phone = loan.personContact.ifBlank { loan.counterpartyContact }
                                if (selectedContact == null && phone.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(phone, fontSize = 12.sp, color = IndTextSecondary)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Interest ${loan.interestRatePercent}% · Start ${formatLoanDay(loan.startDateTimestamp)}" +
                                        (loan.dueDateTimestamp?.let { " · Return ${formatLoanDay(it)}" } ?: "") +
                                        " · Proofs ${LoanAgreementService.proofCount(loan)}",
                                    fontSize = 11.sp,
                                    color = IndTextSecondary
                                )
                                if (loan.notes.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Note: ${loan.notes}", fontSize = 12.sp, color = IndTextSecondary)
                                }
                                if (loan.amendmentPending && loan.proposedAmount > 0.0) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "Agreed $currencySymbol${"%,.0f".format(loan.totalAmount)} · Proposed $currencySymbol${"%,.0f".format(loan.proposedAmount)} waiting for counterparty",
                                        fontSize = 12.sp,
                                        color = Color(0xFFD97706),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (loan.proposedNotes.isNotBlank()) {
                                        Text("Proposed note: ${loan.proposedNotes}", fontSize = 11.sp, color = IndTextSecondary)
                                    }
                                }
                                if (loan.agreementText.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Mutual agreement on file", fontSize = 12.sp, color = IndGreenDark, fontWeight = FontWeight.SemiBold)
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                val counterpartyPhone = loan.personContact.ifBlank { loan.counterpartyContact }
                                val isCreator = LoanPartyAuth.isCreator(
                                    loan.creatorPhone,
                                    myPhone,
                                    loan.userId,
                                    activeUserId
                                )
                                val isCounterparty = LoanPartyAuth.isCounterparty(counterpartyPhone, myPhone)
                                val pendingApproval = loan.status == LoanApprovalStatus.PENDING ||
                                    loan.status == LoanApprovalStatus.PENDING_APPROVAL

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = when {
                                            loan.amendmentPending -> "⏳ Change pending · agreed amount unchanged"
                                            loan.status == LoanApprovalStatus.SETTLED -> "✓ Settled by both parties"
                                            loan.status == LoanApprovalStatus.PENDING_SETTLEMENT -> "⏳ Settlement pending counterparty"
                                            loan.counterpartyApproved -> "✓ Mutual agreement active"
                                            else -> "⏳ Waiting for counterparty approval"
                                        },
                                        fontSize = 11.sp,
                                        color = when {
                                            loan.status == LoanApprovalStatus.SETTLED || loan.counterpartyApproved -> IndGreenDark
                                            else -> Color(0xFFD97706)
                                        },
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (loan.amendmentPending && isCreator) {
                                        OutlinedButton(
                                            onClick = { viewModel.notifyLoanCounterparty(loan.id) },
                                            modifier = Modifier.height(32.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("Notify", fontSize = 11.sp)
                                        }
                                        OutlinedButton(
                                            onClick = { viewModel.rejectLoanAmendment(loan.id) },
                                            modifier = Modifier.height(32.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("Withdraw", fontSize = 11.sp)
                                        }
                                    }
                                    if (loan.amendmentPending && isCounterparty) {
                                        OutlinedButton(
                                            onClick = { viewModel.rejectLoanAmendment(loan.id) },
                                            modifier = Modifier.height(32.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("Decline", fontSize = 11.sp)
                                        }
                                        Button(
                                            onClick = { viewModel.approveLoanAmendment(loan.id) },
                                            modifier = Modifier.height(32.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = IndGreen),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                        ) {
                                            Text("Approve change", fontSize = 11.sp, color = Color.White)
                                        }
                                    }
                                    if (pendingApproval && isCreator) {
                                        OutlinedButton(
                                            onClick = { viewModel.notifyLoanCounterparty(loan.id) },
                                            modifier = Modifier.height(32.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("Notify", fontSize = 11.sp)
                                        }
                                        Text("Only ${counterpartyPhone.takeLast(10)} can approve", fontSize = 10.sp, color = IndTextSecondary)
                                    }
                                    if (pendingApproval && isCounterparty) {
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
                                    }
                                    if (pendingApproval && !isCreator && !isCounterparty) {
                                        Text(
                                            "Sign in with creator or counterparty phone to act on this record.",
                                            fontSize = 10.sp,
                                            color = IndTextSecondary
                                        )
                                    }
                                    if ((loan.status == LoanApprovalStatus.MUTUAL_APPROVED || loan.status == LoanApprovalStatus.APPROVED) &&
                                        loan.agreementText.isNotBlank()
                                    ) {
                                        OutlinedButton(
                                            onClick = { viewModel.downloadLoanAgreement(loan.id) },
                                            modifier = Modifier.height(32.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("Agreement", fontSize = 11.sp)
                                        }
                                    }
                                    if ((loan.status == LoanApprovalStatus.MUTUAL_APPROVED || loan.status == LoanApprovalStatus.APPROVED) &&
                                        isCreator && !loan.amendmentPending
                                    ) {
                                        OutlinedButton(
                                            onClick = { viewModel.requestLoanSettlement(loan.id) },
                                            modifier = Modifier.height(32.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("Request settle", fontSize = 11.sp)
                                        }
                                    }
                                    if (loan.status == LoanApprovalStatus.PENDING_SETTLEMENT && isCounterparty) {
                                        Button(
                                            onClick = { viewModel.confirmLoanSettlement(loan.id) },
                                            modifier = Modifier.height(32.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = IndBlue),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                        ) {
                                            Text("Confirm settle", fontSize = 11.sp, color = Color.White)
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
        LoanRecordWizard(
            currencySymbol = currencySymbol,
            initialName = selectedContact?.name.orEmpty(),
            initialPhone = selectedContact?.phone.orEmpty(),
            lockContact = selectedContact != null,
            onDismiss = { showAddLoanDialog = false },
            onSaveAndSend = { type, person, phone, amount, start, interest, returnDate, notes, proofs ->
                viewModel.submitLoanForApproval(
                    personName = person,
                    phone = phone,
                    amount = amount,
                    type = type,
                    notes = notes,
                    interestPercent = interest,
                    startDate = start,
                    returnDate = returnDate,
                    proofPaths = proofs,
                    sendNow = true
                )
                showAddLoanDialog = false
            }
        )
    }

    loanPendingDelete?.let { loan ->
        AlertDialog(
            onDismissRequest = { loanPendingDelete = null },
            title = { Text("Delete this record?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Delete the ${loan.personName.ifBlank { "loan" }} record of $currencySymbol${"%,.0f".format(loan.totalAmount)}? This cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.requestDeleteApproval(loan.id)
                        loanPendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndRed)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { loanPendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (editingLoan != null) {
        AlertDialog(
            onDismissRequest = { editingLoan = null },
            title = {
                val agreed = editingLoan?.status == LoanApprovalStatus.MUTUAL_APPROVED ||
                    editingLoan?.status == LoanApprovalStatus.APPROVED
                Text(
                    if (agreed) "Propose a change" else "Edit record",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                val agreed = editingLoan?.status == LoanApprovalStatus.MUTUAL_APPROVED ||
                    editingLoan?.status == LoanApprovalStatus.APPROVED
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (agreed) {
                        Text(
                            "The agreed amount stays $currencySymbol${"%,.0f".format(editingLoan?.totalAmount ?: 0.0)} until the counterparty approves this change.",
                            fontSize = 12.sp,
                            color = IndTextSecondary
                        )
                    }
                    OutlinedTextField(
                        value = editNameStr,
                        onValueChange = { editNameStr = it },
                        label = { Text("Counterparty name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editPhoneStr,
                        onValueChange = { editPhoneStr = it },
                        label = { Text("Counterparty phone") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editAmountStr,
                        onValueChange = { editAmountStr = it },
                        label = { Text(if (agreed) "Proposed amount ($currencySymbol)" else "Amount ($currencySymbol)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editNotesStr,
                        onValueChange = { editNotesStr = it },
                        label = { Text("Notes") },
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
                            viewModel.requestEditApproval(loan.id, amt, editNotesStr, editNameStr, editPhoneStr)
                            editingLoan = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndBlue)
                ) {
                    Text(if (editingLoan?.status == LoanApprovalStatus.MUTUAL_APPROVED || editingLoan?.status == LoanApprovalStatus.APPROVED) "Send for approval" else "Save")
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

private data class ContactRollup(
    val key: String,
    val name: String,
    val phone: String,
    val loans: List<LoanTransaction>,
    val toReceive: Double,
    val toPay: Double
) {
    val net: Double get() = toReceive - toPay
    val openCount: Int get() = loans.count {
        it.status != LoanApprovalStatus.SETTLED && it.status != LoanApprovalStatus.REJECTED
    }
}

private fun contactGroupKey(loan: LoanTransaction): String {
    val phone = LoanPartyAuth.phone10(loan.personContact.ifBlank { loan.counterpartyContact })
    if (phone.length == 10) return "p:$phone"
    val name = loan.personName.ifBlank { loan.counterpartyName }.trim().lowercase()
    return "n:$name"
}

private fun groupLoanContacts(
    loans: List<LoanTransaction>,
    entries: List<com.example.data.model.LoanLedgerEntry>
): List<ContactRollup> {
    return loans.groupBy { contactGroupKey(it) }.map { (key, group) ->
        val ordered = group.sortedByDescending { it.createdAt }
        val name = ordered.firstOrNull { it.personName.isNotBlank() }?.personName
            ?: ordered.firstOrNull { it.counterpartyName.isNotBlank() }?.counterpartyName
            ?: "Unknown"
        val phone = ordered.firstOrNull { it.personContact.isNotBlank() }?.personContact
            ?: ordered.firstOrNull { it.counterpartyContact.isNotBlank() }?.counterpartyContact
            ?: ""
        ContactRollup(
            key = key,
            name = name,
            phone = phone,
            loans = ordered,
            toReceive = ordered.filter { it.type == LoanType.LENT || it.type == LoanType.GIVEN }.sumOf { loanOutstanding(it, entries) },
            toPay = ordered.filter { it.type == LoanType.BORROWED || it.type == LoanType.TAKEN }.sumOf { loanOutstanding(it, entries) }
        )
    }.sortedByDescending { kotlin.math.abs(it.net) }
}

@Composable
private fun ContactNetRow(contact: ContactRollup, currencySymbol: String, onClick: () -> Unit) {
    val net = contact.net
    val amountColor = when {
        net > 0.5 -> IndGreenDark
        net < -0.5 -> IndRed
        else -> IndTextSecondary
    }
    val caption = when {
        net > 0.5 -> "To receive"
        net < -0.5 -> "To pay"
        else -> "Clear"
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = IndSurface,
        shadowElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, IndBorderSubtle)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(42.dp).background(IndBlueLight, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    contact.name.trim().take(1).uppercase().ifBlank { "?" },
                    color = IndBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(contact.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = IndTextPrimary, maxLines = 1)
                Text(
                    "${contact.openCount} open · ${contact.loans.size} records",
                    fontSize = 12.sp,
                    color = IndTextSecondary
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "$currencySymbol${"%,.0f".format(kotlin.math.abs(net))}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = amountColor
                )
                Text(caption, fontSize = 11.sp, color = amountColor)
            }
        }
    }
}

private fun loanOutstanding(loan: LoanTransaction, entries: List<com.example.data.model.LoanLedgerEntry>): Double {
    if (loan.status == LoanApprovalStatus.REJECTED || loan.status == LoanApprovalStatus.SETTLED) return 0.0
    return LoanLedgerMath.outstanding(loan.totalAmount, entries.filter { it.loanId == loan.id }).coerceAtLeast(0.0)
}

private fun formatLoanDay(millis: Long): String {
    return SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(millis))
}

private fun loanStatusLabel(status: LoanApprovalStatus): String {
    return when (status) {
        LoanApprovalStatus.PENDING, LoanApprovalStatus.PENDING_APPROVAL -> "Pending approval"
        LoanApprovalStatus.PENDING_SETTLEMENT -> "Settlement pending"
        LoanApprovalStatus.MUTUAL_APPROVED, LoanApprovalStatus.APPROVED -> "Mutual agreement"
        LoanApprovalStatus.SETTLED -> "Settled"
        LoanApprovalStatus.REJECTED -> "Rejected"
    }
}
