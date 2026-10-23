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

        if (!isFinancial || parsed == null) {
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
                // 1. Insert into bank_transactions table
                val record = BankTransactionRecord(
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

                // 2. Automatically save classified transaction to Firestore
                val userProfile = dao.getUserProfileDirect()
                val userPhone = userProfile?.phoneNumber ?: ""
                saveTransactionToFirestore(context, savedRecord, userPhone)

                // 3. If availableBalance is present, auto-update the bank balance in Monthly Reconciliation!
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

                // 4. Post interactive system notification so user can "Approve from IN" (Incoming Notification)
                postIncomingTransactionNotification(context, savedRecord)

                // 5. Emit toast / snackbar announcement for in-app UI
                val typeEmoji = if (parsed.type == BankTxType.CREDIT) "🟢 Credit" else "🔴 Debit"
                val balStr = if (parsed.availableBalance != null) " • Avl: ₹${"%,.0f".format(parsed.availableBalance)}" else ""
                val msg = "$typeEmoji ₹${"%,.0f".format(parsed.amount)} [${parsed.bankName}]$balStr"
                _syncNotificationEvents.emit(msg)

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

            val notifId = (record.id.toInt().let { if (it > 0) it else 1000 }) + 2000

            // Open app in Pending Transactions ("IN") screen on notification tap
            val contentIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_PENDING
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val contentPendingIntent = PendingIntent.getActivity(
                context,
                notifId,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // "Approve Expense" Action Button directly on notification
            val approveIntent = Intent(context, BankExpenseActionReceiver::class.java).apply {
                action = BankExpenseActionReceiver.ACTION_APPROVE_EXPENSE
                putExtra(BankExpenseActionReceiver.EXTRA_TX_ID, record.id)
                putExtra(BankExpenseActionReceiver.EXTRA_NOTIFICATION_ID, notifId)
            }
            val approvePendingIntent = PendingIntent.getBroadcast(
                context,
                notifId * 10 + 1,
                approveIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // "Dismiss" Action Button
            val rejectIntent = Intent(context, BankExpenseActionReceiver::class.java).apply {
                action = BankExpenseActionReceiver.ACTION_REJECT_EXPENSE
                putExtra(BankExpenseActionReceiver.EXTRA_TX_ID, record.id)
                putExtra(BankExpenseActionReceiver.EXTRA_NOTIFICATION_ID, notifId)
            }
            val rejectPendingIntent = PendingIntent.getBroadcast(
                context,
                notifId * 10 + 2,
                rejectIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
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
        matchPlannedDebit: Boolean = true
    ): String {
        android.util.Log.d("BankSyncManager", "Approving transaction: $txId")
        val db = AppDatabase.getDatabase(context)
        val dao = db.finMoneyDao()

        val tx = dao.getBankTransactionById(txId)
            ?: return "Transaction record not found."

        // Extract accurate month and year from transaction timestamp
        val cal = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
        val txMonth = cal.get(Calendar.MONTH) + 1
        val txYear = cal.get(Calendar.YEAR)
        val txDay = cal.get(Calendar.DAY_OF_MONTH)

        val userProfile = dao.getUserProfileDirect()
        val userPhone = userProfile?.phoneNumber ?: ""

        // Mark transaction as confirmed and status APPROVED
        dao.updateBankTransactionStatus(txId, true, "APPROVED")
        val updatedTx = tx.copy(isConfirmed = true, status = "APPROVED")
        saveTransactionToFirestore(context, updatedTx, userPhone)

        // Dismiss/clear system notification and original banking alert from status bar
        val notifId = (txId.toInt().let { if (it > 0) it else 1000 }) + 2000
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notifId)
        if (tx.sbnKey.isNotBlank()) {
            BankNotificationListenerService.cancelOriginalNotification(tx.sbnKey)
        }

        val resultMsg: String

        if (tx.type.equals("DEBIT", ignoreCase = true)) {
            // Check if it matches an existing planned debit
            var matchedPlanned = false
            if (matchPlannedDebit && tx.matchedDebitId != null) {
                val planned = dao.getDebitById(tx.matchedDebitId)
                if (planned != null) {
                    dao.setDebitPaidStatus(planned.id, true)
                    saveDebitToFirestore(context, planned.copy(isPaid = true), userPhone, updatedTx)
                    matchedPlanned = true
                }
            }

            if (matchedPlanned) {
                resultMsg = "✓ Planned debit '${tx.matchedDebitTitle ?: "Expense"}' marked as Paid (₹${"%,.0f".format(tx.amount)})!"
            } else {
                // Create a new expense entry (DebitItem) in the financial ledger
                val categoryToUse = customCategory ?: tx.category.ifBlank { DebitCategory.GENERAL.name }
                val titleToUse = if (tx.merchant.isNotBlank()) tx.merchant else "${tx.bankName} Expense"

                    val isCc = tx.isCreditCardRecord()
                    val debitItem = DebitItem(
                        cloudId = UUID.randomUUID().toString(),
                        month = txMonth,
                        year = txYear,
                        category = categoryToUse,
                        title = titleToUse,
                        amount = tx.amount,
                        dueDateDay = txDay,
                        dueDate = tx.timestamp,
                        isPaid = true,
                        isPaidViaDebitCard = !isCc,
                        isPaidViaCreditCard = isCc,
                        notes = "Approved from ${tx.bankName} alert. Ref: ${tx.referenceNumber}",
                        createdAt = tx.timestamp
                    )

                val newDebitId = dao.insertDebit(debitItem)
                val insertedDebit = debitItem.copy(id = newDebitId)
                saveDebitToFirestore(context, insertedDebit, userPhone, updatedTx)
                resultMsg = "✓ Expense of ₹${"%,.0f".format(tx.amount)} added to $categoryToUse!"
            }
        } else {
            // Credit / Income transaction: Add to Income Sources & update Salary Record
            val existingSalary = dao.getSalaryRecordDirect(txMonth, txYear)
            val currentSources = if (!existingSalary?.incomeSourcesJson.isNullOrBlank()) {
                com.example.ui.FinMoneyViewModel.parseIncomeSources(existingSalary!!.incomeSourcesJson)
            } else emptyList()

            val sourceName = if (tx.merchant.isNotBlank()) tx.merchant else "${tx.bankName} Credit"
            val categoryToUse = customCategory ?: tx.category.ifBlank { IncomeCategory.OTHER.name }
            val newSource = IncomeSource(
                id = UUID.randomUUID().toString(),
                name = sourceName,
                amount = tx.amount,
                category = categoryToUse,
                isReceived = true,
                isRecurring = false,
                receivedDateTimestamp = tx.timestamp
            )

            val updatedSources = currentSources + newSource
            val json = com.example.data.repository.FinMoneyRepository.encodeIncomeSources(updatedSources)
            val totalAdditional = updatedSources.sumOf { it.amount }

            val updatedSalaryRecord = if (existingSalary != null) {
                existingSalary.copy(
                    additionalIncome = totalAdditional,
                    incomeSourcesJson = json,
                    updatedAt = System.currentTimeMillis()
                )
            } else {
                SalaryRecord(
                    month = txMonth,
                    year = txYear,
                    salaryAmount = 0.0,
                    additionalIncome = totalAdditional,
                    incomeSourcesJson = json,
                    updatedAt = System.currentTimeMillis()
                )
            }
            dao.insertOrUpdateSalary(updatedSalaryRecord)
            saveSalaryToFirestore(context, updatedSalaryRecord, userPhone)
            saveIncomeToFirestore(context, newSource, txMonth, txYear, userPhone, updatedTx)

            resultMsg = "✓ Income of ₹${"%,.0f".format(tx.amount)} approved & recorded in Income Sources!"
        }

        // If availableBalance exists, update reconciliation bank balance
        if (tx.availableBalance > 0.0) {
            updateReconciliationBankBalance(
                dao = dao,
                month = txMonth,
                year = txYear,
                bankName = tx.bankName,
                accountLast4 = tx.accountNumberLast4,
                newBalance = tx.availableBalance
            )
        }

        return resultMsg
    }

    /**
     * Rejects transaction, dismisses notification, and records REJECTED status
     * to prevent re-ingestion if the banking app re-posts the notification.
     */
    suspend fun rejectTransaction(context: Context, txId: Long) {
        android.util.Log.d("BankSyncManager", "Rejecting transaction: $txId")
        val db = AppDatabase.getDatabase(context)
        val dao = db.finMoneyDao()
        val tx = dao.getBankTransactionById(txId)

        // Mark as confirmed = true, status = "REJECTED" so it is excluded from pending
        // but preserved in database for deduplication checks
        dao.updateBankTransactionStatus(txId, true, "REJECTED")

        if (tx != null) {
            val userProfile = dao.getUserProfileDirect()
            val userPhone = userProfile?.phoneNumber ?: ""
            saveTransactionToFirestore(context, tx.copy(isConfirmed = true, status = "REJECTED"), userPhone)
        }

        // Dismiss/clear system notification and original notification from status bar immediately
        val notifId = (txId.toInt().let { if (it > 0) it else 1000 }) + 2000
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notifId)
        if (tx?.sbnKey?.isNotBlank() == true) {
            BankNotificationListenerService.cancelOriginalNotification(tx.sbnKey)
        }
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
        userPhone: String,
        amount: Double,
        timestamp: Long,
        referenceNumber: String,
        cloudId: String
    ): Boolean {
        try {
            if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(context)
            }
            val firestore = getFirestoreInstance(context)
            val cleanPhone = userPhone.filter { it.isDigit() }.let { if (it.length >= 10) it.takeLast(10) else if (it.isNotBlank()) it else resolveUserPhone(context, AppDatabase.getDatabase(context).finMoneyDao()) }

            // 1. Check by exact cloudId / document ID
            val docRef = firestore.collection("expenses").document(cloudId).get().await()
            if (docRef.exists()) {
                android.util.Log.d("BankSyncManager", "Idempotency check: Expense with cloudId $cloudId already exists.")
                return true
            }

            // 2. Check by reference number if present
            if (referenceNumber.isNotBlank()) {
                val refQuery = firestore.collection("expenses")
                    .whereEqualTo("userPhone", cleanPhone)
                    .whereEqualTo("referenceNumber", referenceNumber)
                    .limit(1)
                    .get()
                    .await()
                if (!refQuery.isEmpty) {
                    android.util.Log.d("BankSyncManager", "Idempotency check: Expense with referenceNumber $referenceNumber already exists.")
                    return true
                }
            }

            // 3. Check by amount and timestamp proximity (within 60 seconds tolerance for same amount)
            val startWindow = timestamp - 60000L
            val endWindow = timestamp + 60000L
            val querySnapshot = firestore.collection("expenses")
                .whereEqualTo("userPhone", cleanPhone)
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
    internal suspend fun saveDebitToFirestore(context: Context, item: DebitItem, userPhone: String, bankTxRecord: BankTransactionRecord? = null) {
        try {
            if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(context)
            }
            val firestore = getFirestoreInstance(context)
            val db = AppDatabase.getDatabase(context)
            val cleanPhone = userPhone.filter { it.isDigit() }.let { if (it.length >= 10) it.takeLast(10) else if (it.isNotBlank()) it else resolveUserPhone(context, db.finMoneyDao()) }
            val docId = if (item.cloudId.isNotBlank()) item.cloudId else UUID.randomUUID().toString()
            val merchantName = if (!bankTxRecord?.merchant.isNullOrBlank()) bankTxRecord!!.merchant else item.title
            val timestampVal = if (item.createdAt > 0) item.createdAt else System.currentTimeMillis()
            val refNum = bankTxRecord?.referenceNumber ?: ""

            // Idempotency check before writing to Firestore
            val isAlreadyRecorded = checkAndEnsureIdempotentExpense(context, cleanPhone, item.amount, timestampVal, refNum, docId)
            if (isAlreadyRecorded) {
                android.util.Log.d("BankSyncManager", "Idempotency check passed: Skipping duplicate Firestore write for expense $docId")
                return
            }

            val debitMap = hashMapOf<String, Any?>(
                "cloudId" to docId,
                "userPhone" to cleanPhone,
                "month" to item.month,
                "year" to item.year,
                "category" to item.category,
                "customCategoryName" to item.customCategoryName,
                "title" to item.title,
                "merchant" to merchantName,
                "amount" to item.amount,
                "dueDateDay" to item.dueDateDay,
                "dueDate" to item.dueDate,
                "timestamp" to timestampVal,
                "createdAt" to timestampVal,
                "isRecurring" to item.isRecurring,
                "isPaid" to item.isPaid,
                "isPaidViaCreditCard" to item.isPaidViaCreditCard,
                "isPaidViaDebitCard" to item.isPaidViaDebitCard,
                "notes" to item.notes,
                "transactionType" to "EXPENSE",
                "bankTxCloudId" to (bankTxRecord?.cloudId ?: ""),
                "sourceBank" to (bankTxRecord?.bankName ?: ""),
                "referenceNumber" to (bankTxRecord?.referenceNumber ?: ""),
                "approvalSource" to "PENDING_TRANSACTIONS_SCREEN",
                "approvedAtTimestamp" to System.currentTimeMillis()
            )

            // Synchronously write to both 'expenses' and 'debit_items' collections in Firestore
            firestore.collection("expenses").document(docId)
                .set(debitMap, com.google.firebase.firestore.SetOptions.merge())
                .await()

            firestore.collection("debit_items").document(docId)
                .set(debitMap, com.google.firebase.firestore.SetOptions.merge())
                .await()

            android.util.Log.d("BankSyncManager", "Synced expense to Firestore collections ['expenses', 'debit_items'] [$docId]: $merchantName (₹${item.amount})")
        } catch (e: Exception) {
            android.util.Log.w("BankSyncManager", "Firestore debit sync warning: ${e.message}")
        }
    }

    internal suspend fun saveIncomeToFirestore(
        context: Context,
        incomeSource: IncomeSource,
        month: Int,
        year: Int,
        userPhone: String,
        bankTxRecord: BankTransactionRecord? = null
    ) {
        try {
            if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(context)
            }
            val firestore = getFirestoreInstance(context)
            val db = AppDatabase.getDatabase(context)
            val cleanPhone = userPhone.filter { it.isDigit() }.let { if (it.length >= 10) it.takeLast(10) else if (it.isNotBlank()) it else resolveUserPhone(context, db.finMoneyDao()) }
            val docId = if (incomeSource.id.isNotBlank()) incomeSource.id else UUID.randomUUID().toString()
            val merchantName = if (!bankTxRecord?.merchant.isNullOrBlank()) bankTxRecord!!.merchant else incomeSource.name
            val timestampVal = if ((incomeSource.receivedDateTimestamp ?: 0L) > 0) incomeSource.receivedDateTimestamp!! else System.currentTimeMillis()

            val incomeMap = hashMapOf<String, Any?>(
                "cloudId" to docId,
                "userPhone" to cleanPhone,
                "month" to month,
                "year" to year,
                "sourceName" to incomeSource.name,
                "merchant" to merchantName,
                "amount" to incomeSource.amount,
                "category" to incomeSource.category,
                "timestamp" to timestampVal,
                "receivedDateTimestamp" to timestampVal,
                "isReceived" to incomeSource.isReceived,
                "isRecurring" to incomeSource.isRecurring,
                "transactionType" to "INCOME",
                "bankTxCloudId" to (bankTxRecord?.cloudId ?: ""),
                "sourceBank" to (bankTxRecord?.bankName ?: ""),
                "referenceNumber" to (bankTxRecord?.referenceNumber ?: ""),
                "approvalSource" to "PENDING_TRANSACTIONS_SCREEN",
                "approvedAtTimestamp" to System.currentTimeMillis()
            )

            // Synchronously write to both 'incomes' and 'income_records' collections in Firestore
            firestore.collection("incomes").document(docId)
                .set(incomeMap, com.google.firebase.firestore.SetOptions.merge())
                .await()

            firestore.collection("income_records").document(docId)
                .set(incomeMap, com.google.firebase.firestore.SetOptions.merge())
                .await()

            android.util.Log.d("BankSyncManager", "Synced income to Firestore collections ['incomes', 'income_records'] [$docId]: $merchantName (₹${incomeSource.amount})")
        } catch (e: Exception) {
            android.util.Log.w("BankSyncManager", "Firestore income save warning: ${e.message}")
        }
    }

    internal suspend fun saveSalaryToFirestore(context: Context, record: SalaryRecord, userPhone: String) {
        try {
            if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(context)
            }
            val firestore = getFirestoreInstance(context)
            val db = AppDatabase.getDatabase(context)
            val cleanPhone = userPhone.filter { it.isDigit() }.let { if (it.length >= 10) it.takeLast(10) else if (it.isNotBlank()) it else resolveUserPhone(context, db.finMoneyDao()) }
            val docId = "salary_${cleanPhone}_${record.month}_${record.year}"

            val salaryMap = hashMapOf<String, Any?>(
                "userPhone" to cleanPhone,
                "month" to record.month,
                "year" to record.year,
                "salaryAmount" to record.salaryAmount,
                "additionalIncome" to record.additionalIncome,
                "notes" to record.notes,
                "incomeSourcesJson" to record.incomeSourcesJson,
                "updatedAt" to record.updatedAt
            )

            firestore.collection("salary_records").document(docId)
                .set(salaryMap, com.google.firebase.firestore.SetOptions.merge())
                .await()
        } catch (e: Exception) {
            android.util.Log.w("BankSyncManager", "Firestore salary save bypassed: ${e.message}")
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

    internal suspend fun saveTransactionToFirestore(context: Context, record: BankTransactionRecord, userPhone: String) {
        try {
            if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(context)
            }
            val firestore = getFirestoreInstance(context)
            val db = AppDatabase.getDatabase(context)
            val cleanPhone = userPhone.filter { it.isDigit() }.let { if (it.length >= 10) it.takeLast(10) else if (it.isNotBlank()) it else resolveUserPhone(context, db.finMoneyDao()) }
            val docId = if (record.cloudId.isNotBlank()) record.cloudId else UUID.randomUUID().toString()

            val txData = hashMapOf<String, Any?>(
                "cloudId" to docId,
                "userPhone" to cleanPhone,
                "bankName" to record.bankName,
                "accountNumberLast4" to record.accountNumberLast4,
                "amount" to record.amount,
                "type" to record.type,
                "classification" to record.classification,
                "category" to record.category,
                "merchant" to record.merchant,
                "referenceNumber" to record.referenceNumber,
                "availableBalance" to record.availableBalance,
                "originalText" to record.originalText,
                "source" to record.source,
                "timestamp" to record.timestamp,
                "isConfirmed" to record.isConfirmed,
                "matchedDebitId" to record.matchedDebitId,
                "matchedDebitTitle" to record.matchedDebitTitle,
                "isDuplicate" to record.isDuplicate,
                "duplicateReason" to record.duplicateReason,
                "isIncorrect" to record.isIncorrect,
                "incorrectReason" to record.incorrectReason,
                "approvedAtTimestamp" to System.currentTimeMillis()
            )

            firestore.collection("bank_transactions").document(docId)
                .set(txData, com.google.firebase.firestore.SetOptions.merge())
                .await()
        } catch (e: Exception) {
            android.util.Log.w("BankSyncManager", "Firestore save bypassed gracefully: ${e.message}")
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
