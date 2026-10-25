package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.model.LoanApprovalStatus
import com.example.data.model.LoanLedgerEntry
import com.example.data.model.LoanLedgerKind
import com.example.data.model.LoanTransaction
import com.example.data.model.LoanType
import com.example.domain.usecase.LoanLedgerMath
import com.example.domain.usecase.LoanPartyAuth
import com.example.service.LoanAgreementService
import com.example.ui.FinMoneyViewModel
import com.example.ui.theme.IndBackground
import com.example.ui.theme.IndBlue
import com.example.ui.theme.IndGreen
import com.example.ui.theme.IndGreenDark
import com.example.ui.theme.IndRed
import com.example.ui.theme.IndSurface
import com.example.ui.theme.IndTextPrimary
import com.example.ui.theme.IndTextSecondary
import kotlinx.coroutines.flow.collectLatest
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanLedgerScreen(
    loan: LoanTransaction,
    entries: List<LoanLedgerEntry>,
    currencySymbol: String,
    myPhone: String,
    activeUserId: String,
    viewModel: FinMoneyViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.loanActionMessage.collectLatest { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    val isLent = loan.type == LoanType.LENT || loan.type == LoanType.GIVEN
    val leftLabel = if (isLent) "Lent more" else "Borrowed more"
    val rightLabel = if (isLent) "Received" else "Paid back"
    val agreedOpen = loan.status == LoanApprovalStatus.MUTUAL_APPROVED || loan.status == LoanApprovalStatus.APPROVED
    val canAdd = agreedOpen && !loan.amendmentPending
    val outstanding = if (loan.status == LoanApprovalStatus.SETTLED || loan.status == LoanApprovalStatus.REJECTED) {
        0.0
    } else {
        LoanLedgerMath.outstanding(loan.totalAmount, entries).coerceAtLeast(0.0)
    }
    val isCreator = LoanPartyAuth.isCreator(loan.creatorPhone, myPhone, loan.userId, activeUserId)
    val isCounterparty = LoanPartyAuth.isCounterparty(loan.personContact.ifBlank { loan.counterpartyContact }, myPhone)

    var kind by remember { mutableStateOf(LoanLedgerKind.CLEARING) }
    var amountStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var proofUris by remember { mutableStateOf(listOf<android.net.Uri>()) }
    var editing by remember { mutableStateOf<LoanLedgerEntry?>(null) }
    var editAmount by remember { mutableStateOf("") }
    var editNotes by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<LoanLedgerEntry?>(null) }
    val listState = rememberLazyListState()
    val proofPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) proofUris = proofUris + uris
    }

    LaunchedEffect(entries.size) {
        val last = entries.size
        if (last >= 0) listState.animateScrollToItem(last)
    }

    Column(modifier = Modifier.fillMaxSize().background(IndBackground)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = IndTextPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(loan.personName.ifBlank { "Payment record" }, fontWeight = FontWeight.Bold, color = IndTextPrimary)
                Text(
                    "Left ₹$currencySymbol${"%,.0f".format(outstanding)} · Agreed ₹$currencySymbol${"%,.0f".format(loan.totalAmount)}",
                    fontSize = 12.sp,
                    color = IndTextSecondary
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Surface(shape = RoundedCornerShape(16.dp), color = IndSurface) {
                        Text(
                            "Agreed opening $currencySymbol${"%,.0f".format(loan.totalAmount)}",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            fontSize = 12.sp,
                            color = IndTextSecondary
                        )
                    }
                }
            }
            items(entries, key = { it.id }) { entry ->
                LedgerBubble(
                    entry = entry,
                    loan = loan,
                    currencySymbol = currencySymbol,
                    isCreator = isCreator,
                    isCounterparty = isCounterparty,
                    onApprove = { viewModel.approveLedgerEntry(entry.id) },
                    onDecline = { viewModel.declineLedgerEntry(entry.id) },
                    onApproveChange = { viewModel.approveLedgerAmendment(entry.id) },
                    onDeclineChange = { viewModel.declineLedgerAmendment(entry.id) },
                    onEdit = {
                        editing = entry
                        editAmount = if (entry.amendmentPending && entry.proposedAmount > 0.0) entry.proposedAmount.toString() else entry.amount.toString()
                        editNotes = if (entry.amendmentPending) entry.proposedNotes else entry.notes
                    },
                    onDelete = { pendingDelete = entry },
                    onOpenProof = { path -> openProof(context, path) }
                )
            }
        }

        Surface(color = IndSurface, shadowElevation = 6.dp) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!canAdd) {
                    Text(
                        when {
                            loan.amendmentPending -> "Finish the pending change on the main amount before adding a payment."
                            loan.status == LoanApprovalStatus.PENDING_SETTLEMENT -> "Settlement is waiting. New payments are paused."
                            loan.status == LoanApprovalStatus.SETTLED -> "This record is settled."
                            else -> "The other person must approve the main record before payments can be added."
                        },
                        fontSize = 12.sp,
                        color = IndTextSecondary
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = kind == LoanLedgerKind.ADDITIONAL, onClick = { kind = LoanLedgerKind.ADDITIONAL }, label = { Text(leftLabel) })
                        FilterChip(selected = kind == LoanLedgerKind.CLEARING, onClick = { kind = LoanLedgerKind.CLEARING }, label = { Text(rightLabel) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = amountStr,
                            onValueChange = { amountStr = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("Amount ($currencySymbol)") },
                            singleLine = true
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { proofPicker.launch(arrayOf("image/*", "application/pdf")) }) {
                            Icon(Icons.Default.AttachFile, contentDescription = "Attach proof", tint = IndBlue)
                        }
                        Button(
                            onClick = {
                                val amount = amountStr.toDoubleOrNull() ?: return@Button
                                val proofs = LoanAgreementService.persistProofs(context, proofUris)
                                viewModel.submitLedgerEntry(loan.id, kind, amount, notes.trim(), proofs)
                                amountStr = ""
                                notes = ""
                                proofUris = emptyList()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (kind == LoanLedgerKind.CLEARING) IndGreen else IndBlue)
                        ) { Text("Send") }
                    }
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Note (optional)") },
                        singleLine = true
                    )
                    if (proofUris.isNotEmpty()) {
                        Text("${proofUris.size} proof(s) attached", fontSize = 11.sp, color = IndBlue)
                    }
                    Text(
                        if (kind == LoanLedgerKind.ADDITIONAL) "$leftLabel increases what is left."
                        else "$rightLabel reduces what is left. It cannot be more than ₹${"%,.0f".format(outstanding)}.",
                        fontSize = 11.sp,
                        color = IndTextSecondary
                    )
                }
            }
        }
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this payment?", fontWeight = FontWeight.Bold) },
            text = { Text("Delete $currencySymbol${"%,.0f".format(entry.amount)}? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteLedgerEntry(entry.id)
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndRed)
                ) { Text("Delete", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }

    editing?.let { entry ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (LoanLedgerMath.countsTowardBalance(entry.status)) "Propose a change" else "Edit payment", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (LoanLedgerMath.countsTowardBalance(entry.status)) {
                        Text(
                            "The balance keeps $currencySymbol${"%,.0f".format(entry.amount)} until the other person approves.",
                            fontSize = 12.sp,
                            color = IndTextSecondary
                        )
                    }
                    OutlinedTextField(value = editAmount, onValueChange = { editAmount = it }, label = { Text("Amount") })
                    OutlinedTextField(value = editNotes, onValueChange = { editNotes = it }, label = { Text("Note") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amount = editAmount.toDoubleOrNull() ?: return@Button
                    viewModel.editLedgerEntry(entry.id, amount, editNotes, null)
                    editing = null
                }) { Text("Send") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun LedgerBubble(
    entry: LoanLedgerEntry,
    loan: LoanTransaction,
    currencySymbol: String,
    isCreator: Boolean,
    isCounterparty: Boolean,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
    onApproveChange: () -> Unit,
    onDeclineChange: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenProof: (String) -> Unit
) {
    val increase = entry.kind == LoanLedgerKind.ADDITIONAL
    val samePerson = isCreator && isCounterparty
    val isProposer = if (entry.proposerIsCreator) isCreator else isCounterparty
    val isOther = !samePerson && if (entry.proposerIsCreator) isCounterparty else isCreator
    val pending = LoanLedgerMath.isPending(entry.status)
    val approved = LoanLedgerMath.countsTowardBalance(entry.status)
    val canDelete = isProposer && (pending || entry.status == LoanApprovalStatus.REJECTED || loan.status == LoanApprovalStatus.SETTLED)
    val canEdit = isProposer && loan.status != LoanApprovalStatus.SETTLED &&
        loan.status != LoanApprovalStatus.PENDING_SETTLEMENT &&
        (pending || approved)
    val bubbleColor = when {
        entry.status == LoanApprovalStatus.REJECTED -> Color(0xFFF3F4F6)
        increase -> Color(0xFFFFF7ED)
        else -> Color(0xFFECFDF3)
    }
    val align = if (increase) Alignment.Start else Alignment.End
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = align) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = bubbleColor,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (increase) {
                        if (loan.type == LoanType.LENT || loan.type == LoanType.GIVEN) "Lent" else "Borrowed more"
                    } else {
                        if (loan.type == LoanType.LENT || loan.type == LoanType.GIVEN) "Received" else "Paid back"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (increase) Color(0xFFC2410C) else IndGreenDark
                )
                Text(
                    "$currencySymbol${"%,.0f".format(entry.amount)}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = IndTextPrimary
                )
                if (entry.amendmentPending && entry.proposedAmount > 0.0) {
                    Text(
                        "Proposed $currencySymbol${"%,.0f".format(entry.proposedAmount)} · balance unchanged",
                        fontSize = 11.sp,
                        color = Color(0xFFD97706)
                    )
                }
                if (entry.notes.isNotBlank()) Text(entry.notes, fontSize = 12.sp, color = IndTextSecondary)
                Text(
                    when {
                        entry.status == LoanApprovalStatus.REJECTED -> "Declined"
                        entry.amendmentPending -> "Change waiting"
                        pending -> "Waiting for approval"
                        else -> "Agreed"
                    },
                    fontSize = 11.sp,
                    color = IndTextSecondary
                )
                val proofs = entry.proofPaths.lineSequence().filter { it.isNotBlank() }.toList()
                proofs.forEachIndexed { index, path ->
                    TextButton(onClick = { onOpenProof(path) }, contentPadding = PaddingValues(0.dp)) {
                        Text("Proof ${index + 1}", fontSize = 11.sp, color = IndBlue)
                    }
                }
                Text(
                    SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault()).format(Date(entry.createdAt)),
                    fontSize = 10.sp,
                    color = IndTextSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (pending && isOther) {
                        OutlinedButton(onClick = onDecline, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text("Decline", fontSize = 11.sp)
                        }
                        Button(onClick = onApprove, modifier = Modifier.height(32.dp), colors = ButtonDefaults.buttonColors(containerColor = IndGreen), contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text("Approve", fontSize = 11.sp, color = Color.White)
                        }
                    }
                    if (pending && isProposer) {
                        OutlinedButton(onClick = onDecline, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text("Withdraw", fontSize = 11.sp)
                        }
                    }
                    if (entry.amendmentPending && isOther) {
                        OutlinedButton(onClick = onDeclineChange, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text("Decline", fontSize = 11.sp)
                        }
                        Button(onClick = onApproveChange, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text("Approve", fontSize = 11.sp)
                        }
                    }
                    if (entry.amendmentPending && isProposer) {
                        OutlinedButton(onClick = onDeclineChange, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text("Withdraw", fontSize = 11.sp)
                        }
                    }
                    if (canEdit) {
                        TextButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Edit", fontSize = 11.sp) }
                    }
                    if (canDelete) {
                        TextButton(onClick = onDelete, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Delete", fontSize = 11.sp, color = IndRed) }
                    }
                }
            }
        }
    }
}

private fun openProof(context: android.content.Context, path: String) {
    try {
        val file = File(path)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, if (path.endsWith(".pdf", true)) "application/pdf" else "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}
