package com.example.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.example.MainActivity
import com.example.cloud.CloudUserContextResolver
import com.example.cloud.LocalFirstCloudSync
import com.example.cloud.UserCloudDataRepository
import com.example.data.local.AppDatabase
import com.example.data.model.AppNotification
import com.example.data.model.LoanApprovalStatus
import com.example.data.model.LoanLedgerEntry
import com.example.data.model.LoanLedgerKind
import com.example.data.model.LoanTransaction
import com.example.data.model.LoanType
import com.example.domain.usecase.LoanPartyAuth
import com.example.identity.UserIdentityRegistry
import com.example.util.PhoneNotificationHelper
import java.util.UUID

object LoanInviteService {
    const val ACTION_OPEN_LOAN = "com.example.finmoney.ACTION_OPEN_LOAN"
    const val EXTRA_LOAN_CLOUD_ID = "extra_loan_cloud_id"
    const val EXTRA_LOAN_ID = "extra_loan_id"
    const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.aistudio.finmoney.vqpz"
    const val DEEP_LINK_HOST = "loan"

    private val registry = UserIdentityRegistry()
    private val cloud = UserCloudDataRepository()

    enum class Action {
        APPROVE_RECORD,
        APPROVE_CHANGE,
        APPROVE_PAYMENT,
        CONFIRM_SETTLE
    }

    suspend fun dispatch(
        context: Context,
        loan: LoanTransaction,
        action: Action = Action.APPROVE_RECORD,
        entry: LoanLedgerEntry? = null,
        messageOverride: String? = null
    ): String {
        val app = context.applicationContext
        val dao = AppDatabase.getDatabase(app).finMoneyDao()
        val sender = CloudUserContextResolver.resolve(dao)
        val phone = LoanPartyAuth.phone10(loan.personContact.ifBlank { loan.counterpartyContact })
        val cloudId = loan.cloudId.ifBlank { "loan_${loan.id}_${UUID.randomUUID().toString().take(8)}" }
        val ensured = if (loan.cloudId.isBlank()) {
            val copy = loan.copy(cloudId = cloudId)
            dao.updateLoan(copy)
            copy
        } else loan

        LocalFirstCloudSync.queueLoan(app, sender, ensured)
        val title = actionTitle(action)
        val body = messageOverride ?: LoanAgreementService.requestMessage(ensured)
        val inviteId = "inv_${cloudId}_${action.name}_${entry?.id ?: 0}_${System.currentTimeMillis()}"
        val payload = invitePayload(ensured, sender.userId, action, entry, title, body, inviteId)

        postLocalActionNotification(app, ensured, title, body, inviteId)

        if (phone.length != 10) {
            shareInstallInvite(app, "", shareText(ensured, body, registered = false))
            return "Shared the request. Add a 10-digit number next time to find them in FinMoney."
        }

        val registeredUid = try {
            registry.lookupPhone(phone)?.uid?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }

        val inbox = cloud.writePhoneInbox(phone, inviteId, payload)
        if (inbox.isFailure) {
            LocalFirstCloudSync.queueLoan(app, sender, ensured)
        }

        dao.insertNotification(
            AppNotification(
                userId = sender.userId,
                cloudId = inviteId,
                title = "Sent: $title",
                message = "Waiting for ${ensured.personName.ifBlank { phone }} · $body",
                type = "LOAN_SENT",
                actionType = action.name,
                relatedLoanId = ensured.id,
                referenceId = entry?.id ?: 0L
            )
        )

        if (registeredUid != null && registeredUid != sender.userId) {
            return "Sent to their FinMoney inbox. They can open the record to approve or reject."
        }

        shareInstallInvite(app, phone, shareText(ensured, body, registered = false))
        return "They are not on FinMoney yet. Share the details with the install link."
    }

    suspend fun pullInbox(context: Context) {
        val app = context.applicationContext
        val dao = AppDatabase.getDatabase(app).finMoneyDao()
        val me = CloudUserContextResolver.resolve(dao)
        val phone = me.phone10
        if (phone.length != 10) return
        val items = cloud.listPhoneInbox(phone)
        for (item in items) {
            val loan = loanFromPayload(item, me.userId) ?: continue
            val existing = if (loan.cloudId.isNotBlank()) dao.getLoanByCloudId(loan.cloudId) else null
            val localLoan = if (existing == null) {
                val id = dao.insertLoan(loan.copy(id = 0L, userId = me.userId))
                loan.copy(id = id, userId = me.userId)
            } else {
                val merged = existing.copy(
                    status = loan.status,
                    totalAmount = loan.totalAmount,
                    outstandingAmount = loan.outstandingAmount,
                    paidAmount = loan.paidAmount,
                    notes = loan.notes.ifBlank { existing.notes },
                    amendmentPending = loan.amendmentPending,
                    proposedAmount = loan.proposedAmount,
                    proposedNotes = loan.proposedNotes,
                    counterpartyApproved = loan.counterpartyApproved,
                    agreementText = loan.agreementText.ifBlank { existing.agreementText },
                    updatedAt = maxOf(existing.updatedAt, loan.updatedAt)
                )
                dao.updateLoan(merged)
                merged
            }
            val entry = entryFromPayload(item, localLoan.id, me.userId)
            if (entry != null) {
                val already = dao.listLedgerEntries(localLoan.id).any {
                    it.kind == entry.kind && kotlin.math.abs(it.amount - entry.amount) < 0.01 &&
                        kotlin.math.abs(it.createdAt - entry.createdAt) < 2_000
                }
                if (!already) dao.insertLedgerEntry(entry)
            }
            val inviteId = item["inviteId"]?.toString().orEmpty()
            if (inviteId.isNotBlank() && dao.getNotificationByCloudId(inviteId) == null) {
                dao.insertNotification(
                    AppNotification(
                        userId = me.userId,
                        cloudId = inviteId,
                        title = item["title"]?.toString() ?: "Loan request",
                        message = item["body"]?.toString() ?: "Open this record to take action.",
                        type = "LOAN_ACTION",
                        actionType = item["action"]?.toString() ?: Action.APPROVE_RECORD.name,
                        relatedLoanId = localLoan.id,
                        referenceId = entry?.id ?: 0L,
                        isRead = false
                    )
                )
            }
        }
    }

    fun loanOpenIntent(context: Context, loanCloudId: String, loanId: Long): Intent {
        return Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_LOAN
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_LOAN_CLOUD_ID, loanCloudId)
            putExtra(EXTRA_LOAN_ID, loanId)
            data = Uri.parse("finmoney://$DEEP_LINK_HOST/${loanCloudId.ifBlank { loanId }}")
        }
    }

    private fun postLocalActionNotification(
        context: Context,
        loan: LoanTransaction,
        title: String,
        body: String,
        inviteId: String
    ) {
        val notifId = PhoneNotificationHelper.getNotificationId(loan.id, loan.cloudId, inviteId)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pending = PendingIntent.getActivity(
            context,
            notifId,
            loanOpenIntent(context, loan.cloudId, loan.id),
            flags
        )
        PhoneNotificationHelper.showReminderNotification(context, notifId, title, body)
        // Content tap uses helper's default activity; extra deep-link pending is also posted via a second notify id.
        val tapId = notifId + 17
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "finmoney_reminders"
        val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(com.example.R.drawable.ic_stat_finmoney)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            manager.notify(tapId, notification)
        } catch (_: Exception) {
        }
    }

    private fun shareInstallInvite(context: Context, phone: String, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val extras = mutableListOf<Intent>()
        if (phone.length >= 8) {
            extras += Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")).apply {
                putExtra("sms_body", text)
            }
            extras += Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/91$phone?text=${Uri.encode(text)}"))
        }
        val chooser = Intent.createChooser(send, "Share FinMoney request")
        if (extras.isNotEmpty()) {
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, extras.toTypedArray())
        }
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(chooser)
        } catch (_: Exception) {
        }
    }

    private fun shareText(loan: LoanTransaction, body: String, registered: Boolean): String {
        val name = loan.personName.ifBlank { "there" }
        val amount = "₹${"%,.0f".format(loan.totalAmount)}"
        val purpose = loan.notes.ifBlank { "a payment record" }
        return buildString {
            append("Hi $name, ")
            append(body)
            append("\n\nAmount: $amount")
            append("\nPurpose: $purpose")
            if (loan.creatorPhone.isNotBlank()) append("\nFrom: +91 ${loan.creatorPhone.takeLast(10)}")
            if (!registered) {
                append("\n\nInstall FinMoney to approve or reject this in-app:\n$PLAY_STORE_URL")
            } else {
                append("\n\nOpen FinMoney → bell icon → this record to take action.")
            }
        }
    }

    private fun actionTitle(action: Action): String = when (action) {
        Action.APPROVE_RECORD -> "Approve this payment record"
        Action.APPROVE_CHANGE -> "Approve a change"
        Action.APPROVE_PAYMENT -> "Approve a payment"
        Action.CONFIRM_SETTLE -> "Confirm settlement"
    }

    private fun invitePayload(
        loan: LoanTransaction,
        senderUid: String,
        action: Action,
        entry: LoanLedgerEntry?,
        title: String,
        body: String,
        inviteId: String
    ): Map<String, Any?> = buildMap {
        put("inviteId", inviteId)
        put("action", action.name)
        put("title", title)
        put("body", body)
        put("senderUid", senderUid)
        put("loanCloudId", loan.cloudId)
        put("localLoanId", loan.id)
        put("personName", loan.personName)
        put("personContact", loan.personContact)
        put("counterpartyName", loan.counterpartyName)
        put("counterpartyContact", loan.counterpartyContact)
        put("creatorPhone", loan.creatorPhone)
        put("creatorUserId", loan.userId)
        put("type", loan.type.name)
        put("status", loan.status.name)
        put("totalAmount", loan.totalAmount)
        put("outstandingAmount", loan.outstandingAmount)
        put("paidAmount", loan.paidAmount)
        put("notes", loan.notes)
        put("interestRatePercent", loan.interestRatePercent)
        put("startDateTimestamp", loan.startDateTimestamp)
        put("dueDateTimestamp", loan.dueDateTimestamp)
        put("amendmentPending", loan.amendmentPending)
        put("proposedAmount", loan.proposedAmount)
        put("proposedNotes", loan.proposedNotes)
        put("counterpartyApproved", loan.counterpartyApproved)
        put("agreementText", loan.agreementText)
        put("updatedAt", System.currentTimeMillis())
        if (entry != null) {
            put("entryKind", entry.kind.name)
            put("entryAmount", entry.amount)
            put("entryNotes", entry.notes)
            put("entryStatus", entry.status.name)
            put("entryCreatedAt", entry.createdAt)
        }
    }

    private fun loanFromPayload(item: Map<String, Any?>, viewerUid: String): LoanTransaction? {
        val cloudId = item["loanCloudId"]?.toString().orEmpty()
        if (cloudId.isBlank() && item["localLoanId"] == null) return null
        val type = try {
            LoanType.valueOf(item["type"]?.toString() ?: "LENT")
        } catch (_: Exception) {
            LoanType.LENT
        }
        val status = try {
            LoanApprovalStatus.valueOf(item["status"]?.toString() ?: "PENDING_APPROVAL")
        } catch (_: Exception) {
            LoanApprovalStatus.PENDING_APPROVAL
        }
        return LoanTransaction(
            userId = viewerUid,
            cloudId = cloudId,
            creatorPhone = item["creatorPhone"]?.toString().orEmpty(),
            personName = item["personName"]?.toString().orEmpty(),
            personContact = item["personContact"]?.toString().orEmpty(),
            counterpartyName = item["counterpartyName"]?.toString().orEmpty(),
            counterpartyContact = item["counterpartyContact"]?.toString().orEmpty(),
            type = type,
            status = status,
            totalAmount = (item["totalAmount"] as? Number)?.toDouble() ?: 0.0,
            principalAmount = (item["totalAmount"] as? Number)?.toDouble() ?: 0.0,
            outstandingAmount = (item["outstandingAmount"] as? Number)?.toDouble() ?: 0.0,
            paidAmount = (item["paidAmount"] as? Number)?.toDouble() ?: 0.0,
            notes = item["notes"]?.toString().orEmpty(),
            interestRatePercent = (item["interestRatePercent"] as? Number)?.toDouble() ?: 0.0,
            startDateTimestamp = (item["startDateTimestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            dueDateTimestamp = (item["dueDateTimestamp"] as? Number)?.toLong(),
            amendmentPending = item["amendmentPending"] as? Boolean ?: false,
            proposedAmount = (item["proposedAmount"] as? Number)?.toDouble() ?: 0.0,
            proposedNotes = item["proposedNotes"]?.toString().orEmpty(),
            counterpartyApproved = item["counterpartyApproved"] as? Boolean ?: false,
            agreementText = item["agreementText"]?.toString().orEmpty(),
            updatedAt = (item["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }

    private fun entryFromPayload(item: Map<String, Any?>, loanId: Long, userId: String): LoanLedgerEntry? {
        val amount = (item["entryAmount"] as? Number)?.toDouble() ?: return null
        val kind = try {
            LoanLedgerKind.valueOf(item["entryKind"]?.toString() ?: return null)
        } catch (_: Exception) {
            return null
        }
        val status = try {
            LoanApprovalStatus.valueOf(item["entryStatus"]?.toString() ?: "PENDING_APPROVAL")
        } catch (_: Exception) {
            LoanApprovalStatus.PENDING_APPROVAL
        }
        return LoanLedgerEntry(
            loanId = loanId,
            userId = userId,
            kind = kind,
            amount = amount,
            notes = item["entryNotes"]?.toString().orEmpty(),
            status = status,
            createdAt = (item["entryCreatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }
}
