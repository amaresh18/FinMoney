package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.data.bank.BankTransactionParser
import com.example.data.bank.BankTxType
import com.example.data.bank.ParsedBankTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.FinMoneyDao
import com.example.data.model.BankBalanceItem
import com.example.data.model.BankTransactionRecord
import com.example.data.model.DebitCategory
import com.example.data.model.DebitItem
import com.example.data.model.isCreditCardRecord
import com.example.data.model.IncomeCategory
import com.example.data.model.IncomeSource
import com.example.data.model.MonthlyReconciliation
import com.example.data.model.SalaryRecord
import com.example.cloud.CloudUserContext
import com.example.cloud.CloudUserContextResolver
import com.example.cloud.CloudSyncOutboxManager
import com.example.data.repository.FinMoneyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.UUID

object BankSyncManager {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val processedTxSignatures = java.util.Collections.synchronizedSet(LinkedHashSet<String>())
    private val dedupLock = Any()
    private data class PaymentBurst(val type: String, val amount: Double, val accountLast4: String, val at: Long)
    private val recentPaymentBursts = ArrayDeque<PaymentBurst>()

    private val _lastDetectedTransaction = MutableStateFlow<ParsedBankTransaction?>(null)
    val lastDetectedTransaction: StateFlow<ParsedBankTransaction?> = _lastDetectedTransaction.asStateFlow()

    private val _syncNotificationEvents = MutableSharedFlow<String>(extraBufferCapacity = 5)
    val syncNotificationEvents: SharedFlow<String> = _syncNotificationEvents.asSharedFlow()

    private val _isListenerConnected = MutableStateFlow(false)
    val isListenerConnected: StateFlow<Boolean> = _isListenerConnected.asStateFlow()

    const val INCOMING_EXPENSES_CHANNEL_ID = "finmoney_incoming_expenses"
    const val ACTION_OPEN_PENDING = "com.example.finmoney.ACTION_OPEN_PENDING"

    fun updateListenerConnectionStatus(connected: Boolean) {
        _isListenerConnected.value = connected
    }

    /**
     * Rebinds the NotificationListenerService and triggers active notification scan.
     */
    fun ensureServiceBound(context: Context) {
        if (isNotificationAccessGranted(context)) {
            BankNotificationListenerService.requestRebindService(context)
        }
    }

    /**
     * Checks if the user has granted Android Notification Access permission to this app.
     */
    fun isNotificationAccessGranted(context: Context): Boolean {
        try {
            val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
            if (enabledPackages.contains(context.packageName)) {
                return true
            }
        } catch (e: Exception) {
            // Fallback check
        }

        try {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            if (!TextUtils.isEmpty(flat)) {
                val names = flat.split(":").toTypedArray()
                for (name in names) {
                    val cn = ComponentName.unflattenFromString(name)
                    if (cn != null && TextUtils.equals(context.packageName, cn.packageName)) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return false
    }

    /**
     * Intent to open the Android Notification Listener settings screen.
     */
    fun createNotificationSettingsIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Core processing method: parses notification text and evaluates duplicates using BankTransactionDeduplicator.
     */
    fun processNotification(
        context: Context,
        title: String,
        text: String,
        packageName: String,
        timestamp: Long = System.currentTimeMillis(),
        sbnKey: String = ""
    ): ParsedBankTransaction? {
        val isFinancial = BankTransactionParser.isFinancialMessage(text, title) ||
            TransactionParser.isFinancialText(text, title)

        val db = AppDatabase.getDatabase(context)
        val dao = db.finMoneyDao()

        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val month = cal.get(Calendar.MONTH) + 1
        val year = cal.get(Calendar.YEAR)

        val plannedDebits = try {
            kotlinx.coroutines.runBlocking { dao.getDebitsForMonthDirect(month, year) }
        } catch (e: Exception) {
            emptyList()
        }

        val parseResult = TransactionParser.parse(
            rawText = text,
            senderOrTitle = title,
            sourcePackage = packageName,
            timestamp = timestamp
        )

        var parsed = BankTransactionParser.parse(
            text = text,
            senderOrTitle = title,
            sourcePackage = packageName,
            timestamp = timestamp,
            plannedDebits = plannedDebits
        ) ?: parseResult.toParsedBankTransaction()

        if (parsed != null && parseResult.amount != null) {
            parsed = parsed.copy(
                type = parseResult.transactionType,
                availableBalance = parseResult.availableBalance ?: parsed.availableBalance,
                amount = parseResult.amount
            )
        }

        val gateDecision = NotificationTransactionGate.evaluate(
            title = title,
            body = text,
            parseResult = parseResult,
            parsedTx = parsed
        )

        // Always log raw notification record into local database
        recordRawNotification(
            context = context,
            packageName = packageName,
            title = title,
            body = text,
            timestamp = timestamp,
            isMonitoredPkg = true,
            isFinancialContent = isFinancial,
            parsedTx = parsed
        )

        when (gateDecision.action) {
            NotificationTransactionGate.Action.IGNORE -> {
                android.util.Log.i("BankSyncManager", "Filtered notification (${gateDecision.reason})")
                return null
            }
            NotificationTransactionGate.Action.BALANCE_UPDATE_ONLY -> {
                scope.launch {
                    try {
                        val balance = gateDecision.balanceToApply ?: parseResult.availableBalance
                        if (balance != null && balance >= 0.0) {
                            updateReconciliationBankBalance(
                                dao = dao,
                                month = month,
                                year = year,
                                bankName = parseResult.bankName,
                                accountLast4 = parseResult.accountEnding,
                                newBalance = balance
                            )
                            android.util.Log.i(
                                "BankSyncManager",
                                "Silent balance update for ${parseResult.bankName}: ₹${"%,.2f".format(balance)} (${gateDecision.reason})"
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("BankSyncManager", "Balance-only update failed: ${e.message}")
                    }
                }
                return null
            }
            NotificationTransactionGate.Action.REQUEST_USER_APPROVAL -> { /* continue */ }
        }

        if (!isFinancial || parsed == null || !parseResult.isFinancialNotification) {
            android.util.Log.i("BankSyncManager", "Filtered non-financial or promotional notification: ${parseResult.debugReason}")
            return null
        }

        // Compute SHA-256 fingerprint for idempotent deduplication
        val fingerprint = BankTransactionDeduplicator.computeFingerprint(
            bankName = parsed.bankName,
            amount = parsed.amount,
            transactionType = parsed.type.name,
            merchant = parsed.merchantOrParty,
            accountLast4 = parsed.accountNumberLast4,
            referenceNumber = parsed.referenceNumber
        )

        synchronized(dedupLock) {
            val now = parsed.timestamp
            val duplicateBurst = recentPaymentBursts.any { burst ->
                burst.type == parsed.type.name &&
                    kotlin.math.abs(burst.amount - parsed.amount) < 0.01 &&
                    kotlin.math.abs(now - burst.at) <= BankTransactionDeduplicator.SAME_PAYMENT_BURST_MS &&
                    (burst.accountLast4.isBlank() || parsed.accountNumberLast4.isBlank() ||
                        burst.accountLast4.equals(parsed.accountNumberLast4, ignoreCase = true))
            }
            if (duplicateBurst) {
                android.util.Log.i("BankSyncManager", "Duplicate payment burst suppressed for ₹${parsed.amount}")
                return parsed
            }
            if (processedTxSignatures.contains(fingerprint)) {
                android.util.Log.d("BankSyncManager", "Duplicate transaction detected in memory fingerprint cache: $fingerprint")
                return parsed
            }
            recentPaymentBursts.addLast(
                PaymentBurst(parsed.type.name, parsed.amount, parsed.accountNumberLast4, now)
            )
            while (recentPaymentBursts.size > 80) recentPaymentBursts.removeFirst()
        }

        // Fast in-memory cache check already applied inside the lock above.

        // Query database for recent transactions to run deep market-proven deduplication check
        val existingTxs = try {
            kotlinx.coroutines.runBlocking { dao.getRecentBankTransactionsDirect(100) }
        } catch (e: Exception) {
            emptyList()
        }

        val contentHash = BankTransactionDeduplicator.normalizeContentHash(text)
        val isRepeatContent = existingTxs.any { existing ->
            BankTransactionDeduplicator.normalizeContentHash(existing.originalText) == contentHash &&
                kotlin.math.abs(existing.timestamp - timestamp) <= BankTransactionDeduplicator.DEDUPLICATION_MAX_AGE_MS
        }
        if (isRepeatContent) {
            android.util.Log.i("BankSyncManager", "Silently discarding duplicate notification body (content hash)")
            return null
        }

        // Automated deep deduplication against pending, confirmed, and debits
        val dedupResult = BankTransactionDeduplicator.evaluate(
            newTx = parsed,
            existingRecords = existingTxs,
            existingDebits = plannedDebits
        )

        // Silently discard duplicates (SMS + app push, same UTR, repeats, prior rejections).
        if (dedupResult.isDuplicate) {
            android.util.Log.i("BankSyncManager", "Silently discarding duplicate bank notification (${dedupResult.confidence}): ${dedupResult.reason}")
            processedTxSignatures.add(fingerprint)
            return parsed
        }

        val integrity = EntryIntegrityEngine.auditIncomingTransaction(parsed, text, existingTxs)
        if (integrity.isIncorrect) {
            android.util.Log.i("BankSyncManager", "Filtered incorrect parse: ${integrity.incorrectReason}")
            return null
        }

        processedTxSignatures.add(fingerprint)
        if (processedTxSignatures.size > 200) {
            val iterator = processedTxSignatures.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }

        _lastDetectedTransaction.value = parsed

        scope.launch {
            try {
                val dao = AppDatabase.getDatabase(context).finMoneyDao()
                val cloudUser = CloudUserContextResolver.resolve(dao)
                // 1. Insert into bank_transactions table
                val record = BankTransactionRecord(
                    userId = cloudUser.userId,
                    cloudId = parsed.id,
                    bankName = parsed.bankName,
                    accountNumberLast4 = parsed.accountNumberLast4,
                    amount = parsed.amount,
                    type = parsed.type.name,
                    classification = parsed.classification,
                    category = parsed.category,
                    merchant = parsed.merchantOrParty,
                    referenceNumber = parsed.referenceNumber,
                    availableBalance = parsed.availableBalance ?: 0.0,
                    originalText = parsed.rawText,
                    source = if (packageName.contains("sms", ignoreCase = true) || packageName.contains("messaging", ignoreCase = true)) "SMS_ALERT" else "NOTIFICATION",
                    timestamp = parsed.timestamp,
                    matchedDebitId = parsed.matchedDebitId,
                    matchedDebitTitle = parsed.matchedDebitTitle,
                    isConfirmed = false, // Pending user approval in IN
                    status = "PENDING",
                    fingerprint = fingerprint,
                    sbnKey = sbnKey,
                    isDuplicate = dedupResult.isDuplicate,
                    duplicateReason = dedupResult.reason,
                    isIncorrect = false,
                    incorrectReason = null
                )
                val insertedId = dao.insertBankTransaction(record)
                val savedRecord = record.copy(id = insertedId)

                if (parsed.availableBalance != null && parsed.availableBalance >= 0.0) {
                    updateReconciliationBankBalance(
                        dao = dao,
                        month = month,
                        year = year,
                        bankName = parsed.bankName,
                        accountLast4 = parsed.accountNumberLast4,
                        newBalance = parsed.availableBalance
                    )
                }

                postIncomingTransactionNotification(context, savedRecord)

                val typeEmoji = if (parsed.type == BankTxType.CREDIT) "🟢 Credit" else "🔴 Debit"
                val balStr = if (parsed.availableBalance != null) " • Avl: ₹${"%,.0f".format(parsed.availableBalance)}" else ""
                val msg = "$typeEmoji ₹${"%,.0f".format(parsed.amount)} [${parsed.bankName}]$balStr"
                _syncNotificationEvents.emit(msg)

                saveTransactionToFirestore(context, savedRecord, cloudUser)
            } catch (e: Exception) {
                android.util.Log.e("BankSyncManager", "Error saving transaction: ${e.message}", e)
            }
        }

        return parsed
    }

    /**
     * Posts an interactive heads-up notification enabling instant approval/rejection from "IN" (Incoming Notification).
     */
    fun postIncomingTransactionNotification(context: Context, record: BankTransactionRecord) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    INCOMING_EXPENSES_CHANNEL_ID,
                    "FinMoney Incoming Transactions",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts for incoming bank & UPI transactions with quick approval actions"
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val notifId = ((record.id.coerceAtLeast(1L) % 100_000L).toInt()) + 2000
            val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE
                else 0

            // Open app in Pending Transactions ("IN") screen on notification tap
            val contentIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_PENDING
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val contentPendingIntent = PendingIntent.getActivity(
                context,
                notifId,
                contentIntent,
                piFlags
            )

            fun actionIntent(action: String): Intent {
                return Intent(context, BankExpenseActionReceiver::class.java).apply {
                    this.action = action
                    putExtra(BankExpenseActionReceiver.EXTRA_TX_ID, record.id)
                    putExtra(BankExpenseActionReceiver.EXTRA_TX_CLOUD_ID, record.cloudId)
                    putExtra(BankExpenseActionReceiver.EXTRA_TX_FINGERPRINT, record.fingerprint)
                    putExtra(BankExpenseActionReceiver.EXTRA_NOTIFICATION_ID, notifId)
                }
            }

            val approvePendingIntent = PendingIntent.getBroadcast(
                context,
                notifId * 10 + 1,
                actionIntent(BankExpenseActionReceiver.ACTION_APPROVE_EXPENSE),
                piFlags
            )

            val rejectPendingIntent = PendingIntent.getBroadcast(
                context,
                notifId * 10 + 2,
                actionIntent(BankExpenseActionReceiver.ACTION_REJECT_EXPENSE),
                piFlags
            )

            val isCredit = record.type.equals("CREDIT", ignoreCase = true)
            val enriched = com.example.data.bank.MerchantEnrichmentEngine.enrich(
                rawMerchant = record.merchant.ifBlank { record.bankName },
                rawText = record.originalText,
                senderOrTitle = record.bankName
            )
            val merchantDisplay = enriched.cleanName
            val title = if (isCredit) {
                "🟢 Income Detected: ₹${"%,.0f".format(record.amount)} from $merchantDisplay"
            } else {
                "🔴 Expense Detected: ₹${"%,.0f".format(record.amount)} at $merchantDisplay"
            }

            val subtext = if (record.isDuplicate) "⚠️ ${record.duplicateReason}" else "${enriched.subtitle} • ${record.bankName} • Tap to view in IN"

            val notification = NotificationCompat.Builder(context, INCOMING_EXPENSES_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentTitle(title)
                .setContentText("$merchantDisplay • $subtext")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("$merchantDisplay\n${record.originalText.ifBlank { "Tap to approve and automatically create an expense entry in FinMoney." }}")
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(contentPendingIntent)
                .addAction(
                    android.R.drawable.ic_input_add,
                    if (isCredit) "✓ Approve Income" else "✓ Approve Expense",
                    approvePendingIntent
                )
                .addAction(
                    android.R.drawable.ic_delete,
                    "✕ Dismiss",
                    rejectPendingIntent
                )
                .build()

            notificationManager.notify(notifId, notification)
        } catch (e: Exception) {
            android.util.Log.w("BankSyncManager", "Could not post interactive incoming notification: ${e.message}")
        }
    }

    /**
     * Approves a pending transaction and creates the corresponding expense entry (DebitItem).
     * Works both from "IN" (Incoming Notification action) and in-app Pending Transactions screen.
     */
    suspend fun approveTransactionAndCreateExpense(
        context: Context,
        txId: Long,
        customCategory: String? = null,
        matchPlannedDebit: Boolean = true,
        cloudId: String = "",
        fingerprint: String = ""
    ): String {
        android.util.Log.d("BankSyncManager", "Approving transaction: $txId cloud=$cloudId")
        val db = AppDatabase.getDatabase(context.applicationContext)
        val dao = db.finMoneyDao()
        val tx = findBankTransaction(dao, txId, cloudId, fingerprint)
            ?: return "Transaction record not found."

        val txTs = if (tx.timestamp > 0L) tx.timestamp else System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = txTs }
        val txMonth = cal.get(Calendar.MONTH) + 1
        val txYear = cal.get(Calendar.YEAR)
        val txDay = cal.get(Calendar.DAY_OF_MONTH)
        val monthYear = "%04d-%02d".format(txYear, txMonth)

        val cloudUser = CloudUserContextResolver.resolve(dao)
        val ownerId = cloudUser.userId.ifBlank { tx.userId }
        val updatedTx = tx.copy(isConfirmed = true, status = "APPROVED", userId = ownerId, timestamp = txTs)
        val ledgerToken = "bankTx:${if (tx.cloudId.isNotBlank()) tx.cloudId else tx.id}"

        val isCredit = isCreditTransaction(updatedTx)
        val resultMsg: String = if (isCredit) {
            createIncomeFromApprovedTx(dao, updatedTx, ownerId, txMonth, txYear, monthYear, customCategory, ledgerToken)
        } else {
            createExpenseFromApprovedTx(
                dao = dao,
                tx = updatedTx,
                ownerId = ownerId,
                txMonth = txMonth,
                txYear = txYear,
                txDay = txDay,
                monthYear = monthYear,
                customCategory = customCategory,
                matchPlannedDebit = matchPlannedDebit,
                ledgerToken = ledgerToken
            )
        }

        dao.updateBankTransactionStatus(tx.id, true, "APPROVED")
        dismissIncomingNotification(context, tx)
        try {
            com.example.audit.UserActionAuditLogger.logMessageApproved(context, tx, customCategory, resultMsg)
        } catch (_: Exception) {}

        if (tx.availableBalance > 0.0) {
            try {
                updateReconciliationBankBalance(
                    dao = dao,
                    month = txMonth,
                    year = txYear,
                    bankName = tx.bankName,
                    accountLast4 = tx.accountNumberLast4,
                    newBalance = tx.availableBalance
                )
            } catch (e: Exception) {
                android.util.Log.w("BankSyncManager", "Balance update skipped: ${e.message}")
            }
        }

        try {
            saveTransactionToFirestore(context, updatedTx, cloudUser)
            val debit = dao.getDebitByNotesPattern("%$ledgerToken%")
            if (debit != null) saveDebitToFirestore(context, debit.copy(userId = ownerId), cloudUser, updatedTx)
            val salary = dao.getSalaryRecordDirect(txMonth, txYear, ownerId)
                ?: dao.getSalaryRecordDirect(txMonth, txYear)
            if (salary != null) {
                saveSalaryToFirestore(context, salary.copy(userId = ownerId), cloudUser)
                val sources = FinMoneyRepository.parseIncomeSources(salary.incomeSourcesJson)
                sources.lastOrNull { it.id == ledgerToken }?.let { source ->
                    saveIncomeToFirestore(context, source, txMonth, txYear, cloudUser, updatedTx)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("BankSyncManager", "Cloud queue after approval skipped: ${e.message}")
        }

        return resultMsg
    }

    /**
     * Rejects transaction, dismisses notification, and records REJECTED status
     * to prevent re-ingestion if the banking app re-posts the notification.
     */
    suspend fun rejectTransaction(context: Context, txId: Long, cloudId: String = "", fingerprint: String = "") {
        android.util.Log.d("BankSyncManager", "Rejecting transaction: $txId")
        val db = AppDatabase.getDatabase(context.applicationContext)
        val dao = db.finMoneyDao()
        val tx = findBankTransaction(dao, txId, cloudId, fingerprint) ?: return
        dao.updateBankTransactionStatus(tx.id, true, "REJECTED")
        dismissIncomingNotification(context, tx)
        try {
            com.example.audit.UserActionAuditLogger.logMessageRejected(context, tx)
        } catch (_: Exception) {}
        try {
            val cloudUser = CloudUserContextResolver.resolve(dao)
            saveTransactionToFirestore(
                context,
                tx.copy(isConfirmed = true, status = "REJECTED", userId = cloudUser.userId.ifBlank { tx.userId }),
                cloudUser
            )
        } catch (e: Exception) {
            android.util.Log.w("BankSyncManager", "Reject cloud sync skipped: ${e.message}")
        }
    }

    private suspend fun findBankTransaction(
        dao: FinMoneyDao,
        txId: Long,
        cloudId: String,
        fingerprint: String
    ): BankTransactionRecord? {
        if (txId > 0L) dao.getBankTransactionById(txId)?.let { return it }
        if (cloudId.isNotBlank()) dao.getBankTransactionByCloudId(cloudId)?.let { return it }
        if (fingerprint.isNotBlank()) dao.getBankTransactionByFingerprint(fingerprint)?.let { return it }
        return null
    }

    private fun isCreditType(type: String): Boolean {
        val t = type.trim().uppercase()
        return t == "CREDIT" || t == "CR" || t == "INCOME" || t == "REFUND" || t == "CASHBACK" || t == "DEPOSIT"
    }

    private fun isCreditTransaction(tx: BankTransactionRecord): Boolean {
        if (isCreditType(tx.type)) return true
        val cat = (tx.category + " " + tx.classification).trim().uppercase()
        if (cat.contains("INCOME") || cat.contains("SALARY") || cat.contains("REFUND") || cat.contains("CASHBACK") ||
            cat.contains("FREELANCE") || cat.contains("INVESTMENT") || cat.contains("RENTAL")) return true
        val text = tx.originalText.lowercase()
        if (text.contains("credited") || text.contains("refund") || text.contains("cashback") || text.contains("deposited") ||
            text.contains("received from") || text.contains("salary") || text.contains("inward") || text.contains("incoming")) return true
        return false
    }

    private fun dismissIncomingNotification(context: Context, tx: BankTransactionRecord) {
        val notifId = ((tx.id.coerceAtLeast(1L) % 100_000L).toInt()) + 2000
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notifId)
        if (tx.sbnKey.isNotBlank()) {
            BankNotificationListenerService.cancelOriginalNotification(tx.sbnKey)
        }
    }

    private suspend fun createExpenseFromApprovedTx(
        dao: FinMoneyDao,
        tx: BankTransactionRecord,
        ownerId: String,
        txMonth: Int,
        txYear: Int,
        txDay: Int,
        monthYear: String,
        customCategory: String?,
        matchPlannedDebit: Boolean,
        ledgerToken: String
    ): String {
        val existing = dao.getDebitByNotesPattern("%$ledgerToken%")
        if (existing != null) {
            return "✓ Expense already recorded (₹${"%,.0f".format(tx.amount)})."
        }
        if (matchPlannedDebit && tx.matchedDebitId != null) {
            val planned = dao.getDebitById(tx.matchedDebitId)
            if (planned != null) {
                dao.setDebitPaidStatus(planned.id, true)
                return "✓ Planned debit '${tx.matchedDebitTitle ?: planned.title}' marked as Paid (₹${"%,.0f".format(tx.amount)})."
            }
        }
        val categoryToUse = customCategory ?: tx.category.ifBlank { DebitCategory.GENERAL.name }
        val titleToUse = if (tx.merchant.isNotBlank()) tx.merchant else "${tx.bankName} Expense"
        val isCc = tx.isCreditCardRecord()
        val debitItem = DebitItem(
            userId = ownerId,
            cloudId = UUID.randomUUID().toString(),
            month = txMonth,
            year = txYear,
            monthYear = monthYear,
            category = categoryToUse,
            title = titleToUse,
            amount = tx.amount,
            dueDateDay = txDay,
            dueDate = tx.timestamp,
            isPaid = true,
            isPaidViaDebitCard = !isCc,
            isPaidViaCreditCard = isCc,
            paidDateTimestamp = tx.timestamp,
            paidDate = tx.timestamp,
            notes = "Approved from ${tx.bankName} alert. Ref: ${tx.referenceNumber} $ledgerToken",
            createdAt = tx.timestamp
        )
        dao.insertDebit(debitItem)
        return "✓ Expense of ₹${"%,.0f".format(tx.amount)} added to $categoryToUse."
    }

    private suspend fun createIncomeFromApprovedTx(
        dao: FinMoneyDao,
        tx: BankTransactionRecord,
        ownerId: String,
        txMonth: Int,
        txYear: Int,
        monthYear: String,
        customCategory: String?,
        ledgerToken: String
    ): String {
        val existingSalary = dao.getSalaryRecordDirect(txMonth, txYear, ownerId)
            ?: dao.getSalaryRecordDirect(txMonth, txYear)
            ?: dao.getSalaryRecordDirect(txMonth, txYear, "")
            ?: dao.getSalaryRecordDirect(txMonth, txYear, "user_default")

        val currentSources = if (existingSalary != null && !existingSalary.incomeSourcesJson.isNullOrBlank() && existingSalary.incomeSourcesJson != "[]") {
            FinMoneyRepository.parseIncomeSources(existingSalary.incomeSourcesJson).toMutableList()
        } else if (existingSalary != null && (existingSalary.baseSalary > 0 || existingSalary.salaryAmount > 0 || existingSalary.additionalIncome > 0)) {
            val list = mutableListOf<IncomeSource>()
            val base = if (existingSalary.baseSalary > 0) existingSalary.baseSalary else existingSalary.salaryAmount
            if (base > 0) {
                list.add(IncomeSource(id = "1", title = "Primary Salary", name = "Primary Salary", amount = base, category = "SALARY", isReceived = true))
            }
            if (existingSalary.additionalIncome > 0) {
                list.add(IncomeSource(id = "2", title = "Additional Income", name = "Additional Income", amount = existingSalary.additionalIncome, category = "FREELANCE", isReceived = true))
            }
            list
        } else {
            mutableListOf()
        }

        if (currentSources.any { it.id == ledgerToken }) {
            return "✓ Income already recorded (₹${"%,.0f".format(tx.amount)})."
        }
        val already = currentSources.any { source ->
            source.receivedDateTimestamp == tx.timestamp &&
                kotlin.math.abs(source.amount - tx.amount) < 0.01 &&
                source.name.equals(if (tx.merchant.isNotBlank()) tx.merchant else "${tx.bankName} Credit", ignoreCase = true)
        }
        if (already) return "✓ Income already recorded (₹${"%,.0f".format(tx.amount)})."

        val sourceName = if (tx.merchant.isNotBlank()) tx.merchant else "${tx.bankName} Credit"
        val categoryToUse = if (!customCategory.isNullOrBlank() && customCategory != "GENERAL") {
            customCategory
        } else if (tx.category.isNotBlank() && tx.category != "GENERAL") {
            tx.category
        } else {
            IncomeCategory.OTHER.name
        }

        val newSource = IncomeSource(
            id = ledgerToken,
            name = sourceName,
            title = sourceName,
            amount = tx.amount,
            category = categoryToUse,
            isReceived = true,
            isRecurring = false,
            receivedDateTimestamp = tx.timestamp
        )
        val updatedSources = currentSources + newSource
        val json = FinMoneyRepository.encodeIncomeSources(updatedSources)

        val salaryItem = updatedSources.find { it.category == "SALARY" || it.id == "1" }
        val newBase = salaryItem?.amount ?: (existingSalary?.baseSalary ?: 0.0)
        val newAdditional = updatedSources.filter { it.category != "SALARY" && it.id != "1" }.sumOf { it.amount }

        val targetUserId = ownerId.ifBlank { existingSalary?.userId ?: "user_default" }
        val updatedSalaryRecord = if (existingSalary != null) {
            existingSalary.copy(
                userId = targetUserId,
                baseSalary = newBase,
                salaryAmount = newBase,
                additionalIncome = newAdditional,
                incomeSourcesJson = json,
                monthYear = existingSalary.monthYear.ifBlank { monthYear },
                updatedAt = System.currentTimeMillis()
            )
        } else {
            SalaryRecord(
                userId = targetUserId,
                month = txMonth,
                year = txYear,
                monthYear = monthYear,
                baseSalary = newBase,
                salaryAmount = newBase,
                additionalIncome = newAdditional,
                incomeSourcesJson = json,
                updatedAt = System.currentTimeMillis()
            )
        }
        dao.insertOrUpdateSalary(updatedSalaryRecord)
        return "✓ Income of ₹${"%,.0f".format(tx.amount)} recorded."
    }

    private fun getFirestoreInstance(context: Context): com.google.firebase.firestore.FirebaseFirestore {
        val dbId = try {
            context.getString(com.example.R.string.firestore_database_id)
        } catch (e: Exception) { "" }

        return if (dbId.isNotBlank()) {
            try {
                com.google.firebase.firestore.FirebaseFirestore.getInstance(dbId)
            } catch (e: Exception) {
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
            }
        } else {
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
        }
    }

    private fun resolveUserPhone(context: Context, dao: FinMoneyDao): String {
        val userProfile = try {
            kotlinx.coroutines.runBlocking { dao.getUserProfileDirect() }
        } catch (e: Exception) { null }
        var phone = userProfile?.phoneNumber?.trim() ?: ""
        if (phone.isBlank()) {
            val authUser = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser } catch (e: Exception) { null }
            phone = authUser?.phoneNumber?.ifBlank { authUser.email } ?: authUser?.uid ?: "default_user"
        }
        return phone
    }

    /**
     * Implements an idempotency check using transaction timestamps, unique request/reference IDs,
     * or hash fingerprints before confirming expense entries in Firestore to prevent duplicate creation.
     */
    suspend fun checkAndEnsureIdempotentExpense(
        context: Context,
        cloudUser: CloudUserContext,
        amount: Double,
        timestamp: Long,
        referenceNumber: String,
        cloudId: String
    ): Boolean {
        try {
            if (!cloudUser.canSyncToCloud) return false
            if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(context)
            }
            val firestore = getFirestoreInstance(context)
            val cleanPhone = cloudUser.phone10
            val uid = cloudUser.userId

            // 1. Check by exact cloudId / document ID (user-scoped + legacy)
            val userDoc = firestore.collection("users").document(uid)
                .collection("expenses").document(cloudId).get().await()
            if (userDoc.exists()) {
                android.util.Log.d("BankSyncManager", "Idempotency check: Expense with cloudId $cloudId already exists for user.")
                return true
            }
            val docRef = firestore.collection("expenses").document(cloudId).get().await()
            if (docRef.exists()) {
                android.util.Log.d("BankSyncManager", "Idempotency check: Expense with cloudId $cloudId already exists.")
                return true
            }

            // 2. Check by reference number if present
            if (referenceNumber.isNotBlank()) {
                val refQuery = firestore.collection("expenses")
                    .whereEqualTo("userId", uid)
                    .whereEqualTo("referenceNumber", referenceNumber)
                    .limit(1)
                    .get()
                    .await()
                if (!refQuery.isEmpty) {
                    android.util.Log.d("BankSyncManager", "Idempotency check: Expense with referenceNumber $referenceNumber already exists.")
                    return true
                }
                if (cleanPhone.isNotBlank()) {
                    val refPhoneQuery = firestore.collection("expenses")
                        .whereEqualTo("userPhone", cleanPhone)
                        .whereEqualTo("referenceNumber", referenceNumber)
                        .limit(1)
                        .get()
                        .await()
                    if (!refPhoneQuery.isEmpty) return true
                }
            }

            // 3. Check by amount and timestamp proximity (within 60 seconds tolerance for same amount)
            val startWindow = timestamp - 60000L
            val endWindow = timestamp + 60000L
            val querySnapshot = firestore.collection("expenses")
                .whereEqualTo("userId", uid)
                .whereEqualTo("amount", amount)
                .whereGreaterThanOrEqualTo("timestamp", startWindow)
                .whereLessThanOrEqualTo("timestamp", endWindow)
                .limit(1)
                .get()
                .await()

            if (!querySnapshot.isEmpty) {
                android.util.Log.d("BankSyncManager", "Idempotency check: Expense with matching amount ₹$amount and timestamp proximity already exists.")
                return true
            }
        } catch (e: Exception) {
            android.util.Log.w("BankSyncManager", "Idempotency check warning: ${e.message}")
        }
        return false
    }

    /**
     * Saves created or updated debit item (expense) to Firestore cloud database with complete metadata.
     * Synchronously populates both 'expenses' and 'debit_items' collections.
     */
    internal suspend fun saveDebitToFirestore(
        context: Context,
        item: DebitItem,
        cloudUser: CloudUserContext,
        bankTxRecord: BankTransactionRecord? = null
    ) {
        try {
            com.example.cloud.LocalFirstCloudSync.queueExpense(context, cloudUser, item, bankTxRecord)
        } catch (e: Exception) {
            android.util.Log.w("BankSyncManager", "Expense cloud queue warning: ${e.message}")
        }
    }

    internal suspend fun saveIncomeToFirestore(
        context: Context,
        incomeSource: IncomeSource,
        month: Int,
        year: Int,
        cloudUser: CloudUserContext,
        bankTxRecord: BankTransactionRecord? = null
    ) {
        try {
            com.example.cloud.LocalFirstCloudSync.queueIncome(context, cloudUser, incomeSource, month, year, bankTxRecord)
        } catch (e: Exception) {
            android.util.Log.w("BankSyncManager", "Income cloud queue warning: ${e.message}")
        }
    }

    internal suspend fun saveSalaryToFirestore(context: Context, record: SalaryRecord, cloudUser: CloudUserContext) {
        try {
            com.example.cloud.LocalFirstCloudSync.queueSalary(context, cloudUser, record)
        } catch (e: Exception) {
            android.util.Log.w("BankSyncManager", "Salary cloud queue warning: ${e.message}")
        }
    }

    private suspend fun updateReconciliationBankBalance(
        dao: FinMoneyDao,
        month: Int,
        year: Int,
        bankName: String,
        accountLast4: String,
        newBalance: Double
    ) {
        val existingRec = dao.getReconciliationForMonthDirect(month, year)
        val currentBalances = if (existingRec != null && existingRec.bankBalancesJson.isNotBlank()) {
            FinMoneyRepository.parseBankBalances(existingRec.bankBalancesJson).toMutableList()
        } else {
            mutableListOf()
        }

        val existingIndex = currentBalances.indexOfFirst {
            it.bankName.equals(bankName, ignoreCase = true) ||
                    (accountLast4.isNotBlank() && it.accountNumberLast4 == accountLast4) ||
                    (it.bankName.contains(bankName, ignoreCase = true) || bankName.contains(it.bankName, ignoreCase = true))
        }

        if (existingIndex >= 0) {
            val old = currentBalances[existingIndex]
            currentBalances[existingIndex] = old.copy(
                endingBalance = newBalance,
                accountNumberLast4 = if (accountLast4.isNotBlank()) accountLast4 else old.accountNumberLast4,
                lastUpdatedTimestamp = System.currentTimeMillis()
            )
        } else {
            currentBalances.add(
                BankBalanceItem(
                    id = UUID.randomUUID().toString(),
                    bankName = bankName,
                    accountNumberLast4 = accountLast4,
                    startingBalance = 0.0,
                    endingBalance = newBalance,
                    notes = "Auto-synced via bank alert",
                    lastUpdatedTimestamp = System.currentTimeMillis()
                )
            )
        }

        val updatedJson = FinMoneyRepository.encodeBankBalances(currentBalances)
        val newRec = existingRec?.copy(
            bankBalancesJson = updatedJson,
            updatedAt = System.currentTimeMillis()
        ) ?: MonthlyReconciliation(
            month = month,
            year = year,
            bankBalancesJson = updatedJson,
            updatedAt = System.currentTimeMillis()
        )

        dao.insertOrUpdateReconciliation(newRec)
    }

    internal suspend fun saveTransactionToFirestore(
        context: Context,
        record: BankTransactionRecord,
        cloudUser: CloudUserContext
    ) {
        try {
            com.example.cloud.LocalFirstCloudSync.queueBankTransaction(context, cloudUser, record)
        } catch (e: Exception) {
            android.util.Log.w("BankSyncManager", "Bank tx cloud queue warning: ${e.message}")
        }
    }

    fun recordRawNotification(
        context: Context,
        packageName: String,
        title: String,
        body: String,
        timestamp: Long,
        isMonitoredPkg: Boolean,
        isFinancialContent: Boolean,
        parsedTx: ParsedBankTransaction?
    ) {
        scope.launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val dao = db.finMoneyDao()
                val needsReview = isFinancialContent && (parsedTx == null || parsedTx.amount <= 0.0)
                val record = com.example.data.model.RawNotificationRecord(
                    packageName = packageName,
                    title = title,
                    body = body,
                    timestamp = timestamp,
                    isMonitoredPkg = isMonitoredPkg,
                    isFinancialContent = isFinancialContent,
                    parsedBankName = parsedTx?.bankName,
                    parsedAmount = parsedTx?.amount,
                    parsedType = parsedTx?.type?.name,
                    parseStatus = if (parsedTx != null) "SUCCESS" else if (isFinancialContent) "FAILED_PARSING" else "IGNORED",
                    needsUserReview = needsReview
                )
                dao.insertRawNotification(record)
            } catch (e: Exception) {
                android.util.Log.e("BankSyncManager", "Error recording raw notification: ${e.message}")
            }
        }
    }
}
