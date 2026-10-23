package com.example.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import com.example.data.model.LoanTransaction
import com.example.data.model.LoanType
import com.example.util.PhoneNotificationHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Contact lookup, proof storage, and counterparty notification for lend/borrow records.
 * The other person approves on this device (or after they receive the share). Until they do,
 * the record stays pending and is not a mutual agreement.
 */
object LoanAgreementService {

    data class ContactDetails(val name: String, val phone: String)

    fun readContact(context: Context, contactUri: Uri): ContactDetails {
        var name = ""
        var phone = ""
        val resolver = context.contentResolver
        try {
            // Read directly from Phone data URI (zero-permission Intent.ACTION_PICK on CommonDataKinds.Phone.CONTENT_URI)
            resolver.query(
                contactUri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(0).orEmpty()
                    phone = cursor.getString(1).orEmpty()
                }
            }
        } catch (_: Exception) {
            // Ignore permission or cursor failure
        }

        // Secondary fallback if contact-only URI was provided
        if (name.isBlank() && phone.isBlank()) {
            try {
                resolver.query(contactUri, arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME
                ), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        name = cursor.getString(1).orEmpty()
                        val id = cursor.getString(0)
                        resolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                            arrayOf(id),
                            null
                        )?.use { phones ->
                            if (phones.moveToFirst()) phone = phones.getString(0).orEmpty()
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore permission or cursor failure
            }
        }
        return ContactDetails(name.trim(), phone.trim())
    }

    fun persistProofs(context: Context, uris: List<Uri>): String {
        if (uris.isEmpty()) return ""
        val dir = File(context.filesDir, "loan_proofs").apply { mkdirs() }
        val saved = uris.mapIndexedNotNull { index, uri ->
            val ext = context.contentResolver.getType(uri)?.substringAfterLast('/')?.take(8) ?: "bin"
            val dest = File(dir, "proof_${System.currentTimeMillis()}_$index.$ext")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                } ?: return@mapIndexedNotNull null
                dest.absolutePath
            } catch (_: Exception) {
                null
            }
        }
        return saved.joinToString("\n")
    }

    fun proofCount(loan: LoanTransaction): Int {
        return loan.proofPaths.lineSequence().count { it.isNotBlank() }
    }

    fun requestMessage(loan: LoanTransaction): String {
        val kind = if (loan.type == LoanType.LENT || loan.type == LoanType.GIVEN) "lent to you" else "borrowed from you"
        val date = formatDate(loan.startDateTimestamp)
        val due = loan.dueDateTimestamp?.let { " Possible return: ${formatDate(it)}." } ?: ""
        val interest = if (loan.interestRatePercent > 0) " Interest: ${loan.interestRatePercent}%." else " No interest."
        return "FinMoney approval request: ₹${"%,.0f".format(loan.totalAmount)} $kind on $date.$interest$due " +
            "Please approve or reject in FinMoney. This is not a mutual agreement until you approve."
    }

    fun buildAgreement(loan: LoanTransaction, approvedAt: Long = System.currentTimeMillis()): String {
        val kind = if (loan.type == LoanType.LENT || loan.type == LoanType.GIVEN) {
            "Lender gives and counterparty receives"
        } else {
            "Counterparty gives and recorder receives"
        }
        return buildString {
            appendLine("FINMONEY MUTUAL LOAN AGREEMENT")
            appendLine("Status: Mutually agreed")
            appendLine("Record #${loan.id}")
            appendLine("Counterparty: ${loan.personName.ifBlank { loan.counterpartyName }}")
            appendLine("Phone: ${loan.personContact.ifBlank { loan.counterpartyContact }}")
            appendLine("Direction: $kind")
            appendLine("Principal: ₹${"%,.2f".format(loan.totalAmount)}")
            appendLine("Start date: ${formatDate(loan.startDateTimestamp)}")
            loan.dueDateTimestamp?.let { appendLine("Possible return date: ${formatDate(it)}") }
            appendLine("Interest rate: ${loan.interestRatePercent}%")
            if (loan.notes.isNotBlank()) appendLine("Purpose: ${loan.notes}")
            appendLine("Proofs attached: ${proofCount(loan)}")
            appendLine("Agreed on: ${formatDate(approvedAt)}")
            appendLine("Both parties accept this record as the shared understanding of the amount, dates, and interest.")
        }.trim()
    }

    fun notifyCounterparty(context: Context, loan: LoanTransaction) {
        val name = loan.personName.ifBlank { "Counterparty" }
        PhoneNotificationHelper.showReminderNotification(
            context = context,
            notificationId = (loan.id % Int.MAX_VALUE).toInt().let { if (it == 0) 1 else it },
            title = "Waiting for $name",
            message = "₹${"%,.0f".format(loan.totalAmount)} is pending approval or rejection."
        )
        val text = requestMessage(loan)
        val sms = loan.personContact.filter { it.isDigit() || it == '+' }
        val send = if (sms.length >= 8) {
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$sms")).apply {
                putExtra("sms_body", text)
            }
        } else {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
        }
        val chooser = Intent.createChooser(send, "Notify ${loan.personName.ifBlank { "counterparty" }}")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(chooser)
        } catch (_: Exception) {
            // Share sheet unavailable; local notification already posted.
        }
    }

    private fun formatDate(millis: Long): String {
        return SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(millis))
    }
}
