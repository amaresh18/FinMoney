package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.service.LoanAgreementService
import com.example.ui.theme.IndBlue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanRecordWizard(
    currencySymbol: String,
    onDismiss: () -> Unit,
    onSaveAndSend: (
        type: String,
        name: String,
        phone: String,
        amount: Double,
        startDate: Long,
        interestPercent: Double,
        returnDate: Long?,
        notes: String,
        proofPaths: String
    ) -> Unit
) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var loanType by remember { mutableStateOf("LENT") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var interestStr by remember { mutableStateOf("0") }
    var notes by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var returnDate by remember { mutableStateOf<Long?>(null) }
    var proofUris by remember { mutableStateOf(listOf<android.net.Uri>()) }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingReturn by remember { mutableStateOf(false) }

    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) {
            val contact = LoanAgreementService.readContact(context, uri)
            if (contact.name.isNotBlank()) name = contact.name
            if (contact.phone.isNotBlank()) phone = contact.phone
        }
    }
    val contactPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) contactPicker.launch(null)
    }
    val proofPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) proofUris = proofUris + uris
    }

    fun openContacts() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) contactPicker.launch(null) else contactPermission.launch(Manifest.permission.READ_CONTACTS)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (step) {
                    0 -> "Lend or borrow?"
                    1 -> "Who is the other person?"
                    2 -> "Amount and dates"
                    else -> "Proof, then send"
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Step ${step + 1} of 4", color = IndBlue, fontWeight = FontWeight.SemiBold)
                when (step) {
                    0 -> {
                        Text("Choose the direction of this record. It stays pending until the other person approves.")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = loanType == "LENT",
                                onClick = { loanType = "LENT" },
                                label = { Text("I lent") }
                            )
                            FilterChip(
                                selected = loanType == "BORROWED",
                                onClick = { loanType = "BORROWED" },
                                label = { Text("I borrowed") }
                            )
                        }
                    }
                    1 -> {
                        OutlinedButton(onClick = { openContacts() }, modifier = Modifier.fillMaxWidth()) {
                            androidx.compose.material3.Icon(Icons.Default.Contacts, contentDescription = null)
                            Text("  Select contact")
                        }
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone number") }, modifier = Modifier.fillMaxWidth())
                    }
                    2 -> {
                        OutlinedTextField(value = amountStr, onValueChange = { amountStr = it }, label = { Text("Amount ($currencySymbol)") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = interestStr, onValueChange = { interestStr = it }, label = { Text("Rate of interest %") }, modifier = Modifier.fillMaxWidth())
                        OutlinedButton(onClick = { pickingStart = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Start date: ${formatDay(startDate)}")
                        }
                        OutlinedButton(onClick = { pickingReturn = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Possible return: ${returnDate?.let { formatDay(it) } ?: "Not set"}")
                        }
                        OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Purpose (optional)") }, modifier = Modifier.fillMaxWidth())
                    }
                    else -> {
                        OutlinedButton(
                            onClick = { proofPicker.launch(arrayOf("image/*", "application/pdf", "application/*")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            androidx.compose.material3.Icon(Icons.Default.AttachFile, contentDescription = null)
                            Text("  Attach images or documents")
                        }
                        Text(
                            if (proofUris.isEmpty()) "No proofs yet. You can attach more than one."
                            else "${proofUris.size} file(s) ready to save with this record."
                        )
                        Text("Save & send asks $name to approve. Until they do, status is Pending approval. A mutual agreement is created only after approval.")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (step < 3) {
                        if (step == 1 && name.isBlank()) return@Button
                        if (step == 2 && (amountStr.toDoubleOrNull() ?: 0.0) <= 0.0) return@Button
                        step += 1
                    } else {
                        val amount = amountStr.toDoubleOrNull() ?: return@Button
                        val proofs = LoanAgreementService.persistProofs(context, proofUris)
                        onSaveAndSend(
                            loanType,
                            name.trim(),
                            phone.trim(),
                            amount,
                            startDate,
                            interestStr.toDoubleOrNull() ?: 0.0,
                            returnDate,
                            notes.trim(),
                            proofs
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = IndBlue)
            ) {
                Text(if (step < 3) "Next" else "Save & send")
            }
        },
        dismissButton = {
            TextButton(onClick = { if (step == 0) onDismiss() else step -= 1 }) {
                Text(if (step == 0) "Cancel" else "Back")
            }
        }
    )

    if (pickingStart || pickingReturn) {
        val state = rememberDatePickerState(initialSelectedDateMillis = if (pickingReturn) returnDate ?: startDate else startDate)
        DatePickerDialog(
            onDismissRequest = {
                pickingStart = false
                pickingReturn = false
            },
            confirmButton = {
                TextButton(onClick = {
                    val picked = state.selectedDateMillis
                    if (picked != null) {
                        if (pickingStart) startDate = picked else returnDate = picked
                    }
                    pickingStart = false
                    pickingReturn = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pickingStart = false
                    pickingReturn = false
                }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

private fun formatDay(millis: Long): String {
    return SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(millis))
}
