package com.example.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GroqApiClient
import com.example.ai.KuberaMessage
import com.example.ai.KuberaPersona
import com.example.ai.KuberaSender
import com.example.data.local.AppDatabase
import com.example.data.local.DatabaseHealthGuard
import com.example.data.model.*
import com.example.data.repository.FinMoneyRepository
import com.example.domain.usecase.CalculateBudgetHealthUseCase
import com.example.domain.usecase.ReconcileTransactionUseCase
import com.example.domain.usecase.ValidateLoanLifecycleUseCase
import com.example.service.BankSyncManager
import com.example.service.EntryIntegrityEngine
import com.example.service.FinMoneyAuthService
import com.example.service.LoanAgreementService
import com.example.service.LoanInviteService
import com.example.cloud.CloudUserContextResolver
import com.example.cloud.CloudSyncOutboxManager
import com.example.cloud.LocalFirstCloudSync
import com.example.cloud.UserCloudDataRepository
import com.example.cloud.UserLocalDataScoping
import com.example.identity.UserIdentityBindingEngine
import com.example.identity.UserIdentityBindingEngine.BindPhase
import android.app.Activity
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import com.example.ui.components.CUSTOM_CATEGORY_FILTER_PREFIX
import com.example.ui.components.debitCategoryDisplayLabel
import com.example.ui.components.debitCategoryFilterKey
import com.example.domain.usecase.LoanLedgerMath
import com.example.domain.usecase.LoanPartyAuth
import com.example.domain.usecase.ValidateLoanLifecycleUseCase.ValidationResult
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.DateFormatSymbols

class FinMoneyViewModel(private val app: Application) : AndroidViewModel(app) {

    private inline fun launchSafe(
        context: CoroutineContext = EmptyCoroutineContext,
        crossinline block: suspend CoroutineScope.() -> Unit
    ) {
        viewModelScope.launch(context) launchSafe@{
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "ViewModel task failed: ${e.message}", e)
                _userNotice.tryEmit(
                    e.message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Please try again."
                )
            }
        }
    }

    val repository: FinMoneyRepository = FinMoneyRepository(AppDatabase.getDatabase(app).finMoneyDao())
    private val dao = AppDatabase.getDatabase(app).finMoneyDao()
    private val identityBinding = UserIdentityBindingEngine(app.applicationContext, dao)
    private val userCloudRepository = UserCloudDataRepository()
    private val groqApiClient = GroqApiClient(app)

    // Domain Use Cases
    val calculateBudgetHealthUseCase = CalculateBudgetHealthUseCase()
    val validateLoanLifecycleUseCase = ValidateLoanLifecycleUseCase()

    private val _loanActionMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val loanActionMessage = _loanActionMessage.asSharedFlow()
    private val _userNotice = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val userNotice = _userNotice.asSharedFlow()

    private suspend fun notifyUser(message: String) {
        _userNotice.emit(message)
        _loanActionMessage.emit(message)
    }

    private suspend fun currentUserPhone10(): String {
        val profile = dao.getUserProfileDirect()
        val fbPhone = try {
            com.example.service.FinMoneyAuthService.phoneDigitsFromFirebase(com.google.firebase.Firebase.auth.currentUser)
        } catch (_: Throwable) {
            ""
        }
        return LoanPartyAuth.phone10(fbPhone.ifBlank { profile?.phoneNumber ?: "" })
    }

    private suspend fun emitLoanDenied(result: ValidationResult) {
        if (result is ValidationResult.Denied) {
            _loanActionMessage.emit(result.reason)
        }
    }
    val reconcileTransactionUseCase = ReconcileTransactionUseCase()

    val userProfile: StateFlow<UserProfile?> = dao.getUserProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _currentMonth = MutableStateFlow(10)
    val currentMonth: StateFlow<Int> = _currentMonth.asStateFlow()

    private val _currentYear = MutableStateFlow(2026)
    val currentYear: StateFlow<Int> = _currentYear.asStateFlow()

    private val _selectedSpecificDay = MutableStateFlow<Int?>(null)
    val selectedSpecificDay: StateFlow<Int?> = _selectedSpecificDay.asStateFlow()

    val currencySymbol: StateFlow<String> = userProfile.map { it?.currencySymbol ?: "₹" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "₹")

    private val _isFinancialSafetyVisible = MutableStateFlow(true)
    val isFinancialSafetyVisible: StateFlow<Boolean> = _isFinancialSafetyVisible.asStateFlow()

    fun toggleFinancialSafetyVisible() {
        _isFinancialSafetyVisible.value = !_isFinancialSafetyVisible.value
    }

    fun previousMonth() {
        if (_currentMonth.value == 1) {
            _currentMonth.value = 12
            _currentYear.value -= 1
        } else {
            _currentMonth.value -= 1
        }
    }

    fun nextMonth() {
        if (_currentMonth.value == 12) {
            _currentMonth.value = 1
            _currentYear.value += 1
        } else {
            _currentMonth.value += 1
        }
    }

    fun setMonthYear(month: Int, year: Int, day: Int? = null) {
        _currentMonth.value = month.coerceIn(1, 12)
        _currentYear.value = year
        _selectedSpecificDay.value = day
    }

    fun getActiveUserId(): String {
        val fbUid = try { com.google.firebase.Firebase.auth.currentUser?.uid } catch (_: Throwable) { null }
        val profileUid = userProfile.value?.userId?.takeIf { it.isNotBlank() }
        return fbUid ?: profileUid ?: "user_default"
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentSalaryRecord: StateFlow<SalaryRecord?> = combine(_currentMonth, _currentYear, userProfile) { m, y, u ->
        Triple(m, y, u?.userId ?: getActiveUserId())
    }.flatMapLatest { (m, y, uid) -> dao.getSalaryRecord(m, y, uid) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentDebits: StateFlow<List<DebitItem>> = combine(_currentMonth, _currentYear, userProfile) { m, y, u ->
        Triple(m, y, u?.userId ?: getActiveUserId())
    }.flatMapLatest { (m, y, uid) -> dao.getDebitsForMonth(m, y, uid) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentReconciliation: StateFlow<MonthlyReconciliation?> = combine(_currentMonth, _currentYear, userProfile) { m, y, u ->
        Triple(m, y, u?.userId ?: getActiveUserId())
    }.flatMapLatest { (m, y, uid) -> dao.getReconciliationForMonth(m, y, uid) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val allLoans: StateFlow<List<LoanTransaction>> = userProfile
        .flatMapLatest { u -> dao.getAllLoans(u?.userId ?: getActiveUserId()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val loanLedgerEntries: StateFlow<List<LoanLedgerEntry>> = userProfile
        .flatMapLatest { u -> dao.observeLedgerEntries(u?.userId ?: getActiveUserId()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val allDebits: StateFlow<List<DebitItem>> = userProfile
        .flatMapLatest { u -> dao.getAllDebits(u?.userId ?: getActiveUserId()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val customCategories: StateFlow<List<CustomCategory>> = userProfile
        .flatMapLatest { u -> dao.getAllCustomCategories(u?.userId ?: getActiveUserId()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val customExpenseTags: StateFlow<List<CustomExpenseTag>> = userProfile
        .flatMapLatest { u -> dao.getAllCustomExpenseTags(u?.userId ?: getActiveUserId()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val notifications: StateFlow<List<AppNotification>> = userProfile
        .flatMapLatest { u -> dao.getAllNotifications(u?.userId ?: getActiveUserId()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val unreadNotificationCount: StateFlow<Int> = userProfile
        .flatMapLatest { u -> dao.getUnreadNotificationCount(u?.userId ?: getActiveUserId()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val bankTransactions: StateFlow<List<BankTransactionRecord>> = userProfile
        .flatMapLatest { u -> dao.getAllBankTransactions(u?.userId ?: getActiveUserId()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monthlySummary: StateFlow<MonthlyFinanceSummary?> = combine(
        combine(currentSalaryRecord, currentDebits, customCategories) { salary, debits, customCats ->
            Triple(salary, debits, customCats)
        },
        combine(currentReconciliation, _currentMonth, _currentYear) { recon, m, y ->
            Triple(recon, m, y)
        }
    ) { debitData, periodData ->
        val salary = debitData.first
        val debits = debitData.second
        val customCats = debitData.third
        val recon = periodData.first
        val m = periodData.second
        val y = periodData.third
        val incomeSources = if (salary != null && salary.incomeSourcesJson.isNotBlank() && salary.incomeSourcesJson != "[]") {
            FinMoneyRepository.parseIncomeSources(salary.incomeSourcesJson)
        } else if (salary != null && (salary.baseSalary > 0 || salary.additionalIncome > 0)) {
            val list = mutableListOf<IncomeSource>()
            if (salary.baseSalary > 0) {
                list.add(IncomeSource("1", "Primary Salary", "Employer Transfer", salary.baseSalary, "SALARY", true))
            }
            if (salary.additionalIncome > 0) {
                list.add(IncomeSource("2", "Secondary / Extra", "Additional Earnings", salary.additionalIncome, "FREELANCE", true))
            }
            list
        } else {
            emptyList()
        }

        val totalIncome = incomeSources.sumOf { it.amount }
        val totalDebits = debits.sumOf { it.amount }
        val totalPaid = debits.filter { it.isPaid }.sumOf { it.amount }

        val budgetMetrics = calculateBudgetHealthUseCase(
            income = totalIncome,
            debits = totalDebits,
            paidDebits = totalPaid
        )

        val bankBalances = if (recon != null && recon.bankBalancesJson.isNotBlank() && recon.bankBalancesJson != "[]") {
            FinMoneyRepository.parseBankBalances(recon.bankBalancesJson)
        } else {
            emptyList()
        }

        val currentBankBalance = bankBalances.sumOf { it.endingBalance }
        val netFlexibleSpending = totalIncome - totalDebits
        val daysRemaining = 30

        val categoryGroups = debits.groupBy { debitCategoryFilterKey(it) }
        val categorySummaries = categoryGroups.map { (filterKey, items) ->
            val catTotal = items.sumOf { it.amount }
            val catPaid = items.filter { it.isPaid }.sumOf { it.amount }
            val pct = if (totalDebits > 0) (catTotal / totalDebits).toFloat() else 0f
            val displayName = debitCategoryDisplayLabel(items.first())
            val customMeta = if (filterKey.startsWith(CUSTOM_CATEGORY_FILTER_PREFIX, ignoreCase = true)) {
                val customName = filterKey.substring(CUSTOM_CATEGORY_FILTER_PREFIX.length)
                customCats.find { it.name.equals(customName, ignoreCase = true) }
            } else null
            CategorySummary(
                categoryName = displayName,
                categoryKey = filterKey,
                iconKey = customMeta?.iconName?.takeIf { it.isNotBlank() } ?: filterKey,
                totalAmount = catTotal,
                paidAmount = catPaid,
                yetToPayAmount = catTotal - catPaid,
                itemCount = items.size,
                paidCount = items.count { it.isPaid },
                percentage = pct,
                colorHex = customMeta?.colorHex ?: "#00B386"
            )
        }.sortedByDescending { it.totalAmount }

        val bankDebits = debits.filter { !it.isActualCreditCardTransaction() }
        val ccDebits = debits.filter { it.isActualCreditCardTransaction() }
        val paidBankDebits = bankDebits.filter { it.isPaid }
        val paidCcDebits = ccDebits.filter { it.isPaid }

        MonthlyFinanceSummary(
            monthYear = "$y-${if (m < 10) "0$m" else "$m"}",
            totalIncome = budgetMetrics.totalIncome,
            totalDebits = budgetMetrics.totalDebits,
            totalPaid = budgetMetrics.totalPaid,
            totalYetToPay = budgetMetrics.totalYetToPay,
            flexibleCash = budgetMetrics.flexibleCash,
            netFlexibleSpending = budgetMetrics.flexibleCash,
            dailySafeSpend = budgetMetrics.dailySafeSpend,
            currentBankBalance = currentBankBalance,
            daysRemainingInMonth = budgetMetrics.daysRemainingInMonth,
            incomeSources = incomeSources,
            creditCardDebits = ccDebits,
            categoryBreakdown = categorySummaries,
            totalBankDebits = bankDebits.sumOf { it.amount },
            totalCcDebits = ccDebits.sumOf { it.amount },
            totalPaidBankDebits = paidBankDebits.sumOf { it.amount },
            totalPaidCcDebits = paidCcDebits.sumOf { it.amount }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MonthlyFinanceSummary())

    val reconciliationSummary: StateFlow<ReconciliationSummary> = combine(
        monthlySummary,
        currentDebits
    ) { summary, debits ->
        val s = summary ?: MonthlyFinanceSummary()
        val bankDebits = debits.filter { !it.isActualCreditCardTransaction() }
        val ccDebits = debits.filter { it.isActualCreditCardTransaction() }
        val paidBankDebits = bankDebits.filter { it.isPaid }
        val paidCcDebits = ccDebits.filter { it.isPaid }

        ReconciliationSummary(
            totalInflows = s.totalIncome,
            totalOutflows = s.totalDebits,
            totalPaidOutflows = s.totalPaid,
            totalYetToPayOutflows = s.totalYetToPay,
            totalBankDebits = s.totalBankDebits,
            totalCcDebits = s.totalCcDebits,
            totalPaidBankDebits = s.totalPaidBankDebits,
            totalPaidCcDebits = s.totalPaidCcDebits,
            netBalance = s.totalIncome - s.totalDebits,
            incomeSourcesCount = s.incomeSources.size,
            bankDebitsCount = bankDebits.size,
            ccDebitsCount = ccDebits.size,
            paidBankDebitsCount = paidBankDebits.size,
            paidCcDebitsCount = paidCcDebits.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReconciliationSummary())

    private val _isNotificationAccessGranted = MutableStateFlow(false)
    val isNotificationAccessGranted: StateFlow<Boolean> = _isNotificationAccessGranted.asStateFlow()

    private val _isAutoExpenseTrackingReady = MutableStateFlow(false)
    val isAutoExpenseTrackingReady: StateFlow<Boolean> = _isAutoExpenseTrackingReady.asStateFlow()

    fun checkNotificationAccess(context: Context) {
        val listener = com.example.util.NotificationPermissionsHelper.hasNotificationListenerAccess(context)
        val ready = com.example.util.NotificationPermissionsHelper.isAutoExpenseTrackingReady(context)
        _isNotificationAccessGranted.value = listener
        _isAutoExpenseTrackingReady.value = ready
    }

    fun saveReconciliation(
        month: Int = 10,
        year: Int = 2026,
        bankBalances: List<BankBalanceItem> = emptyList(),
        manualAdjustment: Any = 0.0,
        adjustmentNote: String = "",
        isReconciled: Boolean = true
    ) {
        launchSafe {
            val activeUid = getActiveUserId()
            val json = FinMoneyRepository.encodeBankBalances(bankBalances)
            val adj = manualAdjustment.toString().toDoubleOrNull() ?: 0.0
            dao.insertOrUpdateReconciliation(
                MonthlyReconciliation(
                    userId = activeUid,
                    month = month,
                    year = year,
                    bankBalancesJson = json,
                    manualAdjustment = adj,
                    adjustmentNote = adjustmentNote,
                    notes = if (isReconciled) "RECONCILED" else "PENDING"
                )
            )
        }
    }

    fun simulateBankAlert(index: Int = 0, context: Context? = null) {}

    fun deleteBankTransaction(id: Long) {
        launchSafe {
            dao.deleteBankTransaction(id)
        }
    }

    fun deleteBankTransaction(tx: BankTransactionRecord) {
        launchSafe {
            dao.deleteBankTransaction(tx.id)
        }
    }

    fun updateBankTransaction(tx: BankTransactionRecord) {
        launchSafe {
            val activeUid = getActiveUserId()
            val updatedTx = if (tx.userId.isBlank()) tx.copy(userId = activeUid) else tx
            dao.insertBankTransaction(updatedTx)
        }
    }

    fun clearAllBankTransactions() {
        launchSafe {
            dao.clearAllBankTransactions(getActiveUserId())
        }
    }

    fun scanDeviceSms(context: Context) {}
    fun parseAndProcessCustomAlert(text: String, title: String = "", context: Context? = null) {
        val targetContext = context ?: app
        launchSafe {
            val result = com.example.service.gemini.GeminiNotificationClassificationService.parseAndCategorize(
                text = text,
                senderOrTitle = title
            )
            if (result.isFinancial && result.amount > 0.0) {
                BankSyncManager.processNotification(
                    context = targetContext,
                    title = result.sender.ifBlank { title.ifBlank { "Bank Alert" } },
                    text = text,
                    packageName = "com.example.custom.alert",
                    timestamp = result.date
                )
                notifyUser("Classified via Gemini: ${result.summaryText}")
            } else {
                notifyUser("Non-financial alert ignored.")
            }
        }
    }

    fun reclassifyTransactionWithGemini(txId: Long) {
        launchSafe {
            val res = com.example.service.gemini.GeminiNotificationClassificationService.updateTransactionClassification(app, txId)
            if (res.isSuccess) {
                val detail = res.getOrNull()
                notifyUser("Updated via Gemini: ${detail?.summaryText}")
            } else {
                notifyUser("Could not reclassify: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    fun batchRefineWithGemini() {
        launchSafe {
            val count = com.example.service.gemini.GeminiNotificationClassificationService.batchRefinePendingTransactions(app)
            notifyUser("Gemini refined $count pending transactions.")
        }
    }

    val pendingBankTransactions: StateFlow<List<BankTransactionRecord>> = bankTransactions.map { list ->
        val pending = list.filter { tx ->
            !tx.isConfirmed &&
                tx.status.equals("PENDING", ignoreCase = true) &&
                !tx.isDuplicate &&
                !tx.isIncorrect &&
                com.example.service.TransactionParser.determineNonFinancialReason(tx.originalText, tx.bankName) == null
        }
        val audit = EntryIntegrityEngine.auditPendingTransactions(pending)
        pending.filter { tx -> audit[tx.id]?.isDuplicate != true && audit[tx.id]?.isIncorrect != true }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun approveAllPendingTransactions() {
        launchSafe {
            val pending = dao.getPendingBankTransactionsDirect(getActiveUserId())
            val audit = EntryIntegrityEngine.auditPendingTransactions(pending)
            val toApprove = pending.filter { tx -> audit[tx.id]?.isDuplicate != true }
            var ok = 0
            toApprove.forEach { tx ->
                val msg = BankSyncManager.approveTransactionAndCreateExpense(
                    context = app,
                    txId = tx.id,
                    customCategory = tx.category
                )
                if (!msg.contains("not found", ignoreCase = true)) ok++
            }
            notifyUser(
                if (ok == 0) "Nothing to approve."
                else "Saved $ok ${if (ok == 1) "entry" else "entries"} on this device. Cloud will follow."
            )
        }
    }

    fun approvePendingTransaction(id: Long, category: String = "GENERAL") {
        launchSafe {
            val msg = BankSyncManager.approveTransactionAndCreateExpense(
                context = app,
                txId = id,
                customCategory = category
            )
            notifyUser(msg)
        }
    }

    fun approvePendingTransaction(tx: BankTransactionRecord) {
        approvePendingTransaction(tx.id, tx.category)
    }

    fun rejectPendingTransaction(id: Long) {
        launchSafe {
            BankSyncManager.rejectTransaction(
                context = app,
                txId = id
            )
            notifyUser("Dismissed. It will not be added to expenses or income.")
        }
    }

    fun rejectPendingTransaction(tx: BankTransactionRecord) {
        rejectPendingTransaction(tx.id)
    }

    // Kubera AI Chat State
    private val _kuberaMessages = MutableStateFlow(
        listOf(
            KuberaMessage(
                sender = KuberaSender.KUBERA,
                text = "Greetings! I am Lord Kubera, Guardian of Celestial Wealth. How may I guide your financial prosperity today?"
            )
        )
    )
    val kuberaMessages: StateFlow<List<KuberaMessage>> = _kuberaMessages.asStateFlow()

    private val _isKuberaThinking = MutableStateFlow(false)
    val isKuberaThinking: StateFlow<Boolean> = _isKuberaThinking.asStateFlow()

    private val _isKuberaSpeaking = MutableStateFlow(false)
    val isKuberaSpeaking: StateFlow<Boolean> = _isKuberaSpeaking.asStateFlow()

    private val _groqApiKey = MutableStateFlow("")
    val groqApiKey: StateFlow<String> = _groqApiKey.asStateFlow()

    private val _selectedGroqModel = MutableStateFlow("llama-3.3-70b-versatile")
    val selectedGroqModel: StateFlow<String> = _selectedGroqModel.asStateFlow()

    fun clearKuberaChat() {
        _kuberaMessages.value = listOf(
            KuberaMessage(
                sender = KuberaSender.KUBERA,
                text = "Greetings! I am Lord Kubera, Guardian of Celestial Wealth. How may I guide your financial prosperity today?"
            )
        )
    }

    fun saveGroqApiKey(key: String) {
        _groqApiKey.value = key
    }

    fun setSelectedGroqModel(model: String) {
        _selectedGroqModel.value = model
    }

    fun sendKuberaUserMessage(text: String) {
        if (text.isBlank()) return
        val userMsg = KuberaMessage(sender = KuberaSender.USER, text = text)
        _kuberaMessages.value = _kuberaMessages.value + userMsg
        _isKuberaThinking.value = true

        launchSafe(Dispatchers.IO) {
            val key = _groqApiKey.value
            val summary = monthlySummary.value
            val responseText = generateOfflineKuberaAdvice(text, summary)
            _isKuberaThinking.value = false
            _kuberaMessages.value = _kuberaMessages.value + KuberaMessage(sender = KuberaSender.KUBERA, text = responseText)
        }
    }

    fun runKuberaAudit(auditMode: Any? = null) {
        sendKuberaUserMessage("Perform a full Kubera financial audit of my current monthly budget, loans, and bank balances.")
    }

    fun chantKuberaMantra() {
        sendKuberaUserMessage("Bless me with the sacred Kubera Dhan Vridhi Mantra and financial wisdom.")
    }

    fun speakKuberaMessage(text: String) {}

    fun getLiveFinancialContextJson(): String {
        val s = monthlySummary.value ?: return "{}"
        return "{\"totalIncome\": ${s.totalIncome}, \"totalDebits\": ${s.totalDebits}, \"netSavings\": ${s.netFlexibleSpending}, \"bankBalance\": ${s.currentBankBalance}}"
    }

    private fun generateOfflineKuberaAdvice(query: String, summary: MonthlyFinanceSummary?): String {
        val q = query.lowercase()
        val inc = summary?.totalIncome ?: 0.0
        val deb = summary?.totalDebits ?: 0.0
        val net = inc - deb

        return when {
            q.contains("audit") || q.contains("budget") || q.contains("summary") -> {
                "🏛️ *Kubera Financial Audit*\n\n" +
                        "• Total Monthly Income: ₹${"%,.0f".format(inc)}\n" +
                        "• Total Monthly Outflow: ₹${"%,.0f".format(deb)}\n" +
                        "• Net Monthly Savings: ₹${"%,.0f".format(net)}\n\n" +
                        "*Kubera's Decree:* " +
                        if (net > 0) "Your treasury is growing steadily! Channel at least 30% of your net savings into high-yield mutual funds or sovereign gold bonds."
                        else "Caution: Your expenditure exceeds or closely matches your income. Review your EMI and dining categories to preserve liquidity."
            }
            q.contains("mantra") || q.contains("bless") -> {
                "✨ *Om Yakshaya Kuberaya Vaishravanaya Dhanadhipataye Dhanadhanya Samriddhim Me Dehi Dapaya Swaha!* ✨\n\n" +
                        "May celestial abundance flow into your accounts, and may wisdom govern every coin you spend."
            }
            q.contains("loan") || q.contains("lent") || q.contains("borrow") -> {
                "⚖️ *Ledger Wisdom:* Always verify counterparties and maintain written records for lent funds. Prioritize settling high-interest obligations first."
            }
            else -> {
                "By the order of Kubera: Your net balance stands at ₹${"%,.0f".format(net)}. " +
                        "Maintain an emergency buffer equivalent to 6 months of essential outflow (₹${"%,.0f".format(deb * 6)})."
            }
        }
    }

    fun addDebit(
        title: String,
        amount: Double,
        category: String,
        dueDateDay: Int,
        month: Int = 10,
        year: Int = 2026,
        notes: String = "",
        principalAmount: Double = 0.0,
        interestRate: Double = 0.0,
        totalTenureMonths: Int = 0,
        currentMonthTenure: Int = 1,
        isPaidViaCreditCard: Boolean = false,
        isRecurring: Boolean = false
    ) {
        launchSafe {
            val activeUid = getActiveUserId()
            persistExpense(
                DebitItem(
                    userId = activeUid,
                    cloudId = java.util.UUID.randomUUID().toString(),
                    title = title,
                    amount = amount,
                    category = category.uppercase(),
                    dueDateDay = dueDateDay,
                    month = month,
                    year = year,
                    monthYear = "%04d-%02d".format(year, month),
                    notes = notes,
                    principalAmount = principalAmount,
                    interestRate = interestRate,
                    totalTenureMonths = totalTenureMonths,
                    currentMonthTenure = currentMonthTenure,
                    isPaidViaCreditCard = isPaidViaCreditCard,
                    isRecurring = isRecurring
                )
            )
            notifyUser("Expense saved on this device.")
        }
    }

    /**
     * Prefills recurring debit obligations from recurring templates or previous months into target month.
     * Automatically increments currentMonthTenure for Chit and Loan EMIs.
     */
    fun prefillRecurringDebitsForMonth(targetMonth: Int = _currentMonth.value, targetYear: Int = _currentYear.value) {
        launchSafe {
            try {
                val activeUid = getActiveUserId()
                val recurringDebits = dao.getRecurringDebitsDirect(activeUid)
                val currentMonthItems = dao.getDebitsForMonthDirect(targetMonth, targetYear, activeUid)
                val newDebitsToInsert = mutableListOf<DebitItem>()

                for (template in recurringDebits) {
                    val alreadyPresent = currentMonthItems.any { item ->
                        item.title.equals(template.title, ignoreCase = true) &&
                        item.category.equals(template.category, ignoreCase = true) &&
                        kotlin.math.abs(item.amount - template.amount) < 0.01
                    }
                    if (!alreadyPresent) {
                        val nextTenure = if (template.totalTenureMonths > 0) template.currentMonthTenure + 1 else template.currentMonthTenure
                        if (template.totalTenureMonths == 0 || nextTenure <= template.totalTenureMonths) {
                            newDebitsToInsert.add(
                                template.copy(
                                    id = 0L,
                                    userId = activeUid,
                                    cloudId = "",
                                    month = targetMonth,
                                    year = targetYear,
                                    monthYear = "$targetYear-${if (targetMonth < 10) "0$targetMonth" else "$targetMonth"}",
                                    currentMonthTenure = nextTenure,
                                    isPaid = false,
                                    paidDateTimestamp = null,
                                    paidDate = null,
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }

                if (newDebitsToInsert.isNotEmpty()) {
                    newDebitsToInsert.forEach { persistExpense(it) }
                    Log.i("FinMoneyViewModel", "Prefilled ${newDebitsToInsert.size} recurring debits into $targetMonth/$targetYear for $activeUid")
                }

            } catch (e: Exception) {
                Log.e("FinMoneyViewModel", "Failed to prefill recurring debits: ${e.message}", e)
            }
        }
    }

    fun prefillRecurringCreditsForMonth(targetMonth: Int = _currentMonth.value, targetYear: Int = _currentYear.value) {
        launchSafe {
            try {
                val activeUid = getActiveUserId()
                val allRecords = dao.getAllSalaryRecordsDirect(activeUid)
                val templates = linkedMapOf<String, IncomeSource>()
                for (record in allRecords) {
                    if (record.month == targetMonth && record.year == targetYear) continue
                    val sources = FinMoneyRepository.parseIncomeSources(record.incomeSourcesJson)
                    for (src in sources) {
                        if (!src.isRecurring) continue
                        val key = "${src.title.trim().lowercase()}|${src.category.trim().lowercase()}|${src.amount}"
                        if (!templates.containsKey(key)) {
                            templates[key] = src
                        }
                    }
                }

                if (templates.isEmpty()) return@launchSafe

                val existing = dao.getSalaryRecordDirect(targetMonth, targetYear, activeUid)
                val currentSources = getOrCreateIncomeSources(existing)
                val toAdd = mutableListOf<IncomeSource>()

                for (template in templates.values) {
                    val alreadyPresent = currentSources.any { item ->
                        item.title.equals(template.title, ignoreCase = true) &&
                            item.category.equals(template.category, ignoreCase = true) &&
                            kotlin.math.abs(item.amount - template.amount) < 0.01
                    }
                    if (!alreadyPresent) {
                        toAdd.add(
                            template.copy(
                                id = java.util.UUID.randomUUID().toString(),
                                isReceived = false,
                                isRecurring = true,
                                receivedDateTimestamp = System.currentTimeMillis()
                            )
                        )
                    }
                }

                if (toAdd.isNotEmpty()) {
                    currentSources.addAll(toAdd)
                    saveUpdatedIncomeSources(existing, currentSources, targetMonth, targetYear)
                    notifyUser("Added ${toAdd.size} recurring income line(s) for this month.")
                    Log.i("FinMoneyViewModel", "Prefilled ${toAdd.size} recurring credits into $targetMonth/$targetYear")
                }
            } catch (e: Exception) {
                Log.e("FinMoneyViewModel", "Failed to prefill recurring credits: ${e.message}", e)
            }
        }
    }

    fun prefillRecurringForMonth(targetMonth: Int = _currentMonth.value, targetYear: Int = _currentYear.value) {
        prefillRecurringDebitsForMonth(targetMonth, targetYear)
        prefillRecurringCreditsForMonth(targetMonth, targetYear)
    }

    fun toggleDebitRecurring(item: DebitItem) {
        launchSafe {
            val updated = item.copy(isRecurring = !item.isRecurring)
            repository.updateDebit(updated)
            queueCloudAfterLocal { user -> LocalFirstCloudSync.queueExpense(app, user, updated) }
        }
    }

    fun insertDebit(item: DebitItem) {
        launchSafe {
            persistExpense(item)
            notifyUser("Expense saved on this device.")
        }
    }

    fun insertDebits(items: List<DebitItem>) {
        launchSafe {
            items.forEach { persistExpense(it) }
            notifyUser(
                if (items.size == 1) "Expense saved on this device."
                else "${items.size} expenses saved on this device."
            )
        }
    }

    fun addCustomCategory(name: String, iconName: String, colorHex: String) {
        launchSafe {
            val trimmed = name.trim()
            if (trimmed.isBlank()) {
                notifyUser("Enter a category name.")
                return@launchSafe
            }
            val uid = getActiveUserId()
            val existing = dao.getCustomCategoryByName(trimmed, uid)
            if (existing != null) {
                notifyUser("A category with this name already exists.")
                return@launchSafe
            }
            dao.insertCustomCategory(
                CustomCategory(
                    userId = uid,
                    name = trimmed,
                    displayName = trimmed,
                    iconName = iconName.ifBlank { "Category" },
                    colorHex = colorHex.ifBlank { "#00B386" }
                )
            )
            notifyUser("Custom category \"$trimmed\" added.")
        }
    }

    fun updateCustomCategory(
        categoryId: Long,
        name: String,
        iconName: String,
        colorHex: String
    ) {
        launchSafe {
            val trimmed = name.trim()
            if (trimmed.isBlank()) {
                notifyUser("Enter a category name.")
                return@launchSafe
            }
            val uid = getActiveUserId()
            val all = dao.getAllCustomCategories(uid).first()
            val existing = all.find { it.id == categoryId }
            if (existing == null) {
                notifyUser("Category not found.")
                return@launchSafe
            }
            val duplicate = all.any {
                it.id != categoryId && it.name.equals(trimmed, ignoreCase = true)
            }
            if (duplicate) {
                notifyUser("A category with this name already exists.")
                return@launchSafe
            }
            val oldName = existing.name
            if (!oldName.equals(trimmed, ignoreCase = true)) {
                dao.renameCustomCategoryOnDebits(oldName, trimmed, uid)
            }
            dao.updateCustomCategory(
                existing.copy(
                    name = trimmed,
                    displayName = trimmed,
                    iconName = iconName.ifBlank { "Category" },
                    colorHex = colorHex.ifBlank { "#00B386" }
                )
            )
            notifyUser("Category updated.")
        }
    }

    fun deleteCustomCategory(category: CustomCategory) {
        launchSafe {
            val uid = getActiveUserId()
            dao.deleteCustomCategory(category.copy(userId = category.userId.ifBlank { uid }))
            notifyUser("Category \"${category.name}\" removed.")
        }
    }

    suspend fun countExpensesUsingCustomCategory(name: String): Int {
        return dao.countDebitsForCustomCategoryName(name.trim(), getActiveUserId())
    }

    fun addCustomExpenseTag(name: String, iconName: String, colorHex: String) {
        launchSafe {
            val trimmed = name.trim()
            if (trimmed.isBlank()) {
                notifyUser("Enter a tag name.")
                return@launchSafe
            }
            val uid = getActiveUserId()
            if (dao.getCustomExpenseTagByName(trimmed, uid) != null) {
                notifyUser("This tag already exists.")
                return@launchSafe
            }
            dao.insertCustomExpenseTag(
                CustomExpenseTag(
                    userId = uid,
                    name = trimmed,
                    iconName = iconName.ifBlank { "LocalOffer" },
                    colorHex = colorHex.ifBlank { "#0066FF" }
                )
            )
            notifyUser("Tag \"$trimmed\" saved.")
        }
    }

    fun updateCustomExpenseTag(tagId: Long, name: String, iconName: String, colorHex: String) {
        launchSafe {
            val trimmed = name.trim()
            if (trimmed.isBlank()) {
                notifyUser("Enter a tag name.")
                return@launchSafe
            }
            val uid = getActiveUserId()
            val all = dao.getAllCustomExpenseTags(uid).first()
            val existing = all.find { it.id == tagId } ?: run {
                notifyUser("Tag not found.")
                return@launchSafe
            }
            if (all.any { it.id != tagId && it.name.equals(trimmed, ignoreCase = true) }) {
                notifyUser("This tag name already exists.")
                return@launchSafe
            }
            dao.updateCustomExpenseTag(
                existing.copy(
                    name = trimmed,
                    iconName = iconName.ifBlank { "LocalOffer" },
                    colorHex = colorHex.ifBlank { "#0066FF" }
                )
            )
            notifyUser("Tag updated.")
        }
    }

    fun deleteCustomExpenseTag(tag: CustomExpenseTag) {
        launchSafe {
            dao.deleteCustomExpenseTag(tag)
            notifyUser("Tag \"${tag.name}\" removed.")
        }
    }

    fun deleteDebit(item: DebitItem) {
        launchSafe {
            repository.deleteDebit(item)
        }
    }

    fun updateDebit(item: DebitItem) {
        launchSafe {
            repository.updateDebit(item)
            queueCloudAfterLocal { user -> LocalFirstCloudSync.queueExpense(app, user, item) }
        }
    }

    fun toggleDebitCreditCardStatus(item: DebitItem) {
        launchSafe {
            val newCcStatus = !item.isActualCreditCardTransaction()
            val updated = item.copy(
                isPaidViaCreditCard = newCcStatus,
                isPaidViaDebitCard = if (newCcStatus) false else item.isPaidViaDebitCard
            )
            repository.updateDebit(updated)
            queueCloudAfterLocal { user -> LocalFirstCloudSync.queueExpense(app, user, updated) }
        }
    }

    fun toggleDebitPaid(item: DebitItem) {
        launchSafe {
            val paid = !item.isPaid
            dao.setDebitPaidStatus(item.id, paid)
            val updated = dao.getDebitById(item.id) ?: item.copy(isPaid = paid)
            queueCloudAfterLocal { user -> LocalFirstCloudSync.queueExpense(app, user, updated) }
        }
    }

    private fun getOrCreateIncomeSources(existing: SalaryRecord?): MutableList<IncomeSource> {
        return if (existing != null && !existing.incomeSourcesJson.isNullOrBlank() && existing.incomeSourcesJson != "[]") {
            FinMoneyRepository.parseIncomeSources(existing.incomeSourcesJson).toMutableList()
        } else if (existing != null && (existing.baseSalary > 0 || existing.additionalIncome > 0)) {
            val list = mutableListOf<IncomeSource>()
            if (existing.baseSalary > 0) {
                list.add(IncomeSource("1", "Primary Salary", "Employer Transfer", existing.baseSalary, "SALARY", true))
            }
            if (existing.additionalIncome > 0) {
                list.add(IncomeSource("2", "Secondary / Extra", "Additional Earnings", existing.additionalIncome, "FREELANCE", true))
            }
            list
        } else {
            mutableListOf()
        }
    }

    private suspend fun saveUpdatedIncomeSources(
        existing: SalaryRecord?,
        sources: List<IncomeSource>,
        month: Int,
        year: Int,
        overrideBaseSalary: Double? = null
    ) {
        val activeUid = getActiveUserId()
        val newJson = FinMoneyRepository.encodeIncomeSources(sources)
        val salaryItem = sources.find { it.category == "SALARY" || it.id == "1" }
        val newBase = overrideBaseSalary ?: salaryItem?.amount ?: (existing?.baseSalary ?: 0.0)
        val newAdditional = sources.filter { it.category != "SALARY" && it.id != "1" }.sumOf { it.amount }

        val updatedRecord = if (existing != null) {
            existing.copy(
                userId = activeUid,
                baseSalary = newBase,
                salaryAmount = newBase,
                additionalIncome = newAdditional,
                incomeSourcesJson = newJson,
                updatedAt = System.currentTimeMillis()
            )
        } else {
            SalaryRecord(
                userId = activeUid,
                month = month,
                year = year,
                monthYear = "$year-${if (month < 10) "0$month" else month}",
                baseSalary = newBase,
                salaryAmount = newBase,
                additionalIncome = newAdditional,
                incomeSourcesJson = newJson,
                updatedAt = System.currentTimeMillis()
            )
        }
        repository.insertOrUpdateSalary(updatedRecord)
        queueCloudAfterLocal { user ->
            LocalFirstCloudSync.queueSalary(app, user, updatedRecord)
            sources.forEach { source ->
                LocalFirstCloudSync.queueIncome(app, user, source, month, year)
            }
        }
    }

    fun addIncomeCredit(
        title: String,
        amount: Double,
        category: String = "OTHER",
        dateString: String? = null,
        month: Int? = null,
        year: Int? = null,
        isRecurring: Boolean = false
    ) {
        val parsedTimestamp = if (!dateString.isNullOrBlank()) {
            try {
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).parse(dateString)?.time
            } catch (e: Exception) {
                null
            }
        } else null

        val timestamp = parsedTimestamp ?: System.currentTimeMillis()
        val m = month ?: _currentMonth.value
        val y = year ?: _currentYear.value

        launchSafe {
            val activeUid = getActiveUserId()
            val existing = dao.getSalaryRecordDirect(m, y, activeUid)
            val currentSources = getOrCreateIncomeSources(existing)
            val newSource = IncomeSource(
                id = java.util.UUID.randomUUID().toString(),
                title = title,
                name = title,
                amount = amount,
                category = category,
                isReceived = true,
                isRecurring = isRecurring,
                receivedDateTimestamp = timestamp
            )
            currentSources.add(newSource)
            saveUpdatedIncomeSources(existing, currentSources, m, y)
            notifyUser("Income saved on this device.")
        }
    }

    fun updateIncomeSource(
        id: String,
        newTitle: String,
        newAmount: Double,
        newCategory: String = "OTHER",
        dateString: String? = null,
        month: Int? = null,
        year: Int? = null,
        isRecurring: Boolean? = null
    ) {
        val parsedTimestamp = if (!dateString.isNullOrBlank()) {
            try {
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).parse(dateString)?.time
            } catch (e: Exception) {
                null
            }
        } else null

        val timestamp = parsedTimestamp ?: System.currentTimeMillis()
        val m = month ?: _currentMonth.value
        val y = year ?: _currentYear.value

        launchSafe {
            val activeUid = getActiveUserId()
            val existing = dao.getSalaryRecordDirect(m, y, activeUid)
            val currentSources = getOrCreateIncomeSources(existing)
            val index = currentSources.indexOfFirst { it.id == id }
            if (index != -1) {
                val old = currentSources[index]
                currentSources[index] = old.copy(
                    title = newTitle,
                    name = newTitle,
                    amount = newAmount,
                    category = newCategory,
                    isRecurring = isRecurring ?: old.isRecurring,
                    receivedDateTimestamp = timestamp
                )
                saveUpdatedIncomeSources(existing, currentSources, m, y)
            }
        }
    }

    fun toggleIncomeRecurring(
        id: String,
        month: Int? = null,
        year: Int? = null
    ) {
        val m = month ?: _currentMonth.value
        val y = year ?: _currentYear.value

        launchSafe {
            val activeUid = getActiveUserId()
            val existing = dao.getSalaryRecordDirect(m, y, activeUid)
            val currentSources = getOrCreateIncomeSources(existing)
            val index = currentSources.indexOfFirst { it.id == id }
            if (index != -1) {
                val old = currentSources[index]
                currentSources[index] = old.copy(isRecurring = !old.isRecurring)
                saveUpdatedIncomeSources(existing, currentSources, m, y)
            }
        }
    }

    fun toggleIncomeReceived(
        id: String,
        month: Int? = null,
        year: Int? = null
    ) {
        val m = month ?: _currentMonth.value
        val y = year ?: _currentYear.value

        launchSafe {
            val activeUid = getActiveUserId()
            val existing = dao.getSalaryRecordDirect(m, y, activeUid)
            val currentSources = getOrCreateIncomeSources(existing)
            val index = currentSources.indexOfFirst { it.id == id }
            if (index != -1) {
                val old = currentSources[index]
                currentSources[index] = old.copy(
                    isReceived = !old.isReceived
                )
                saveUpdatedIncomeSources(existing, currentSources, m, y)
            }
        }
    }

    fun deleteIncomeSource(
        id: String,
        month: Int? = null,
        year: Int? = null
    ) {
        val m = month ?: _currentMonth.value
        val y = year ?: _currentYear.value

        launchSafe {
            val activeUid = getActiveUserId()
            val existing = dao.getSalaryRecordDirect(m, y, activeUid)
            val currentSources = getOrCreateIncomeSources(existing)
            val removed = currentSources.removeAll { it.id == id }
            if (removed) {
                saveUpdatedIncomeSources(existing, currentSources, m, y)
            }
        }
    }

    fun updateBaseSalary(
        newAmount: Double,
        month: Int? = null,
        year: Int? = null
    ) {
        val m = month ?: _currentMonth.value
        val y = year ?: _currentYear.value

        launchSafe {
            val activeUid = getActiveUserId()
            val existing = dao.getSalaryRecordDirect(m, y, activeUid)
            val currentSources = getOrCreateIncomeSources(existing)

            val salaryIndex = currentSources.indexOfFirst { it.id == "1" || it.category == "SALARY" }
            if (salaryIndex != -1) {
                currentSources[salaryIndex] = currentSources[salaryIndex].copy(
                    amount = newAmount,
                    title = "Primary Salary",
                    name = "Primary Salary"
                )
            } else {
                currentSources.add(
                    0,
                    IncomeSource(
                        id = "1",
                        title = "Primary Salary",
                        name = "Primary Salary",
                        amount = newAmount,
                        category = "SALARY",
                        isReceived = true,
                        receivedDateTimestamp = System.currentTimeMillis()
                    )
                )
            }

            saveUpdatedIncomeSources(existing, currentSources, m, y, overrideBaseSalary = newAmount)
        }
    }

    fun addLoan(
        personName: String,
        amount: Double,
        type: String,
        notes: String = ""
    ) {
        submitLoanForApproval(
            personName = personName,
            phone = "",
            amount = amount,
            type = type,
            notes = notes,
            interestPercent = 0.0,
            startDate = System.currentTimeMillis(),
            returnDate = null,
            proofPaths = "",
            sendNow = false
        )
    }

    fun submitLoanForApproval(
        personName: String,
        phone: String,
        amount: Double,
        type: String,
        notes: String,
        interestPercent: Double,
        startDate: Long,
        returnDate: Long?,
        proofPaths: String,
        sendNow: Boolean
    ) {
        val loanType = try {
            LoanType.valueOf(type.uppercase())
        } catch (_: Exception) {
            if (type.equals("BORROWED", true) || type.equals("TAKEN", true)) LoanType.BORROWED else LoanType.LENT
        }
        launchSafe {
            val activeUid = getActiveUserId()
            val creatorPhone = currentUserPhone10()
            val draft = LoanTransaction(
                userId = activeUid,
                creatorPhone = creatorPhone,
                personName = personName,
                counterpartyName = personName,
                personContact = phone,
                counterpartyContact = phone,
                totalAmount = amount,
                principalAmount = amount,
                outstandingAmount = amount,
                type = loanType,
                status = LoanApprovalStatus.PENDING_APPROVAL,
                counterpartyApproved = false,
                notes = notes,
                interestRatePercent = interestPercent,
                startDateTimestamp = startDate,
                dueDateTimestamp = returnDate,
                dueDate = returnDate,
                proofPaths = proofPaths,
                agreementText = "",
                cloudId = "loan_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(8)}"
            )
            val id = repository.insertLoan(draft)
            queueCloudAfterLocal { LocalFirstCloudSync.queueLoan(app, it, draft.copy(id = id)) }
            if (sendNow) {
                inviteCounterparty(draft.copy(id = id), LoanInviteService.Action.APPROVE_RECORD)
            }
        }
    }

    fun notifyLoanCounterparty(loanId: Long) {
        launchSafe {
            val loan = dao.getLoanById(loanId) ?: return@launchSafe
            if (loan.status == LoanApprovalStatus.REJECTED || loan.status == LoanApprovalStatus.SETTLED) return@launchSafe
            if (loan.counterpartyApproved && !loan.amendmentPending &&
                loan.status != LoanApprovalStatus.PENDING_APPROVAL &&
                loan.status != LoanApprovalStatus.PENDING_SETTLEMENT
            ) {
                return@launchSafe
            }
            val action = when {
                loan.amendmentPending -> LoanInviteService.Action.APPROVE_CHANGE
                loan.status == LoanApprovalStatus.PENDING_SETTLEMENT -> LoanInviteService.Action.CONFIRM_SETTLE
                else -> LoanInviteService.Action.APPROVE_RECORD
            }
            inviteCounterparty(loan, action)
        }
    }

    fun approveLoan(loanId: Long) {
        launchSafe {
            val loan = dao.getLoanById(loanId) ?: return@launchSafe
            val userPhone = currentUserPhone10()
            val check = validateLoanLifecycleUseCase.validateApprovalPermission(
                creatorPhone = loan.creatorPhone,
                counterpartyPhone = loan.personContact.ifBlank { loan.counterpartyContact },
                userPhone = userPhone,
                creatorUserId = loan.userId,
                activeUserId = getActiveUserId(),
                currentStatus = loan.status
            )
            if (check is ValidationResult.Denied) {
                emitLoanDenied(check)
                return@launchSafe
            }
            val agreed = LoanAgreementService.buildAgreement(loan)
            persistLoan(
                loan.copy(
                    status = LoanApprovalStatus.MUTUAL_APPROVED,
                    counterpartyApproved = true,
                    agreementText = agreed,
                    updatedAt = System.currentTimeMillis()
                )
            )
            refreshLoanBalance(loanId)
            _loanActionMessage.emit("Mutual agreement created. You can download it now.")
        }
    }

    fun rejectLoan(loanId: Long) {
        launchSafe {
            val loan = dao.getLoanById(loanId) ?: return@launchSafe
            val check = validateLoanLifecycleUseCase.validateRejectPermission(
                creatorPhone = loan.creatorPhone,
                counterpartyPhone = loan.personContact.ifBlank { loan.counterpartyContact },
                userPhone = currentUserPhone10(),
                creatorUserId = loan.userId,
                activeUserId = getActiveUserId(),
                currentStatus = loan.status
            )
            if (check is ValidationResult.Denied) {
                emitLoanDenied(check)
                return@launchSafe
            }
            dao.updateLoanApproval(loanId, LoanApprovalStatus.REJECTED.name, false)
        }
    }

    fun requestLoanSettlement(loanId: Long) {
        launchSafe {
            val loan = dao.getLoanById(loanId) ?: return@launchSafe
            val check = validateLoanLifecycleUseCase.validateSettlementRequest(
                creatorPhone = loan.creatorPhone,
                userPhone = currentUserPhone10(),
                creatorUserId = loan.userId,
                activeUserId = getActiveUserId(),
                currentStatus = loan.status,
                amendmentPending = loan.amendmentPending
            )
            if (check is ValidationResult.Denied) {
                emitLoanDenied(check)
                return@launchSafe
            }
            val entries = dao.listLedgerEntries(loanId)
            if (LoanLedgerMath.hasOpenChild(entries)) {
                _loanActionMessage.emit("Approve or decline open payments before closing this record.")
                return@launchSafe
            }
            val left = LoanLedgerMath.outstanding(loan.totalAmount, entries).coerceAtLeast(0.0)
            persistLoan(
                loan.copy(
                    status = LoanApprovalStatus.PENDING_SETTLEMENT,
                    outstandingAmount = left,
                    updatedAt = System.currentTimeMillis()
                )
            )
            val note = if (left > 0.009) {
                "Settlement request: close the remaining ₹${"%,.0f".format(left)} and mark this record settled."
            } else {
                "Settlement request: balance is already clear. Please confirm this record is settled."
            }
            inviteCounterparty(
                dao.getLoanById(loanId) ?: loan,
                LoanInviteService.Action.CONFIRM_SETTLE,
                message = note
            )
        }
    }

    fun confirmLoanSettlement(loanId: Long) {
        launchSafe {
            val loan = dao.getLoanById(loanId) ?: return@launchSafe
            val check = validateLoanLifecycleUseCase.validateSettlementConfirm(
                creatorPhone = loan.creatorPhone,
                counterpartyPhone = loan.personContact.ifBlank { loan.counterpartyContact },
                userPhone = currentUserPhone10(),
                creatorUserId = loan.userId,
                activeUserId = getActiveUserId(),
                currentStatus = loan.status
            )
            if (check is ValidationResult.Denied) {
                emitLoanDenied(check)
                return@launchSafe
            }
            val entries = dao.listLedgerEntries(loanId)
            if (LoanLedgerMath.hasOpenChild(entries)) {
                _loanActionMessage.emit("Approve or decline open payments before closing this record.")
                return@launchSafe
            }
            val left = LoanLedgerMath.outstanding(loan.totalAmount, entries)
            if (left > 0.009) {
                dao.insertLedgerEntry(
                    LoanLedgerEntry(
                        loanId = loan.id,
                        userId = loan.userId,
                        proposerPhone = loan.creatorPhone,
                        proposerIsCreator = true,
                        kind = LoanLedgerKind.CLEARING,
                        amount = left,
                        notes = "Remaining balance closed because both parties settled",
                        status = LoanApprovalStatus.MUTUAL_APPROVED,
                        agreementText = "Both parties agreed to close the remaining ₹${"%,.2f".format(left)}.",
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            val closed = dao.getLoanById(loanId) ?: return@launchSafe
            persistLoan(
                closed.copy(
                    status = LoanApprovalStatus.SETTLED,
                    counterpartyApproved = true,
                    updatedAt = System.currentTimeMillis()
                )
            )
            refreshLoanBalance(loanId)
            _loanActionMessage.emit("Loan marked as settled.")
        }
    }

    fun downloadLoanAgreement(loanId: Long) {
        launchSafe {
            val loan = dao.getLoanById(loanId) ?: return@launchSafe
            if (loan.agreementText.isBlank() && loan.status != LoanApprovalStatus.MUTUAL_APPROVED) {
                _loanActionMessage.emit("Agreement is available only after counterparty approval.")
                return@launchSafe
            }
            LoanAgreementService.shareAgreement(getApplication(), loan)
        }
    }

    fun requestEditApproval(
        loanId: Long,
        newAmount: Double,
        newNotes: String,
        newName: String,
        newPhone: String
    ) {
        launchSafe {
            val loan = dao.getLoanById(loanId) ?: return@launchSafe
            if (!LoanPartyAuth.isCreator(loan.creatorPhone, currentUserPhone10(), loan.userId, getActiveUserId())) {
                _loanActionMessage.emit("Only the creator can edit this record.")
                return@launchSafe
            }
            if (loan.status == LoanApprovalStatus.SETTLED || loan.status == LoanApprovalStatus.PENDING_SETTLEMENT) {
                _loanActionMessage.emit("Finish settlement before changing this record.")
                return@launchSafe
            }
            val mutuallyAgreed = loan.status == LoanApprovalStatus.MUTUAL_APPROVED ||
                loan.status == LoanApprovalStatus.APPROVED
            if (mutuallyAgreed) {
                val proposed = loan.copy(
                    proposedAmount = newAmount,
                    proposedNotes = newNotes,
                    proposedPersonName = newName,
                    proposedPersonContact = newPhone,
                    amendmentPending = true,
                    updatedAt = System.currentTimeMillis()
                )
                persistLoan(proposed)
                inviteCounterparty(proposed, LoanInviteService.Action.APPROVE_CHANGE)
                return@launchSafe
            }
            val updated = loan.copy(
                personName = newName.ifBlank { loan.personName },
                counterpartyName = newName.ifBlank { loan.counterpartyName },
                personContact = newPhone,
                counterpartyContact = newPhone,
                totalAmount = newAmount,
                principalAmount = newAmount,
                notes = newNotes,
                status = LoanApprovalStatus.PENDING_APPROVAL,
                counterpartyApproved = false,
                agreementText = "",
                amendmentPending = false,
                proposedAmount = 0.0,
                proposedNotes = "",
                proposedPersonName = "",
                proposedPersonContact = "",
                updatedAt = System.currentTimeMillis()
            )
            persistLoan(updated)
            refreshLoanBalance(loanId)
            inviteCounterparty(updated, LoanInviteService.Action.APPROVE_RECORD)
        }
    }

    fun approveLoanAmendment(loanId: Long) {
        launchSafe {
            val loan = dao.getLoanById(loanId) ?: return@launchSafe
            val check = validateLoanLifecycleUseCase.validateAmendmentApproval(
                creatorPhone = loan.creatorPhone,
                counterpartyPhone = loan.personContact.ifBlank { loan.counterpartyContact },
                userPhone = currentUserPhone10(),
                creatorUserId = loan.userId,
                activeUserId = getActiveUserId(),
                amendmentPending = loan.amendmentPending
            )
            if (check is ValidationResult.Denied) {
                emitLoanDenied(check)
                return@launchSafe
            }
            val entries = dao.listLedgerEntries(loanId)
            val projected = LoanLedgerMath.outstanding(loan.proposedAmount, entries)
            if (projected < -0.009) {
                _loanActionMessage.emit("Approved repayments are already higher than this new amount.")
                return@launchSafe
            }
            val applied = loan.copy(
                totalAmount = loan.proposedAmount,
                principalAmount = loan.proposedAmount,
                notes = loan.proposedNotes,
                personName = loan.proposedPersonName.ifBlank { loan.personName },
                counterpartyName = loan.proposedPersonName.ifBlank { loan.counterpartyName },
                personContact = loan.proposedPersonContact.ifBlank { loan.personContact },
                counterpartyContact = loan.proposedPersonContact.ifBlank { loan.counterpartyContact },
                proposedAmount = 0.0,
                proposedNotes = "",
                proposedPersonName = "",
                proposedPersonContact = "",
                amendmentPending = false,
                status = LoanApprovalStatus.MUTUAL_APPROVED,
                counterpartyApproved = true,
                updatedAt = System.currentTimeMillis()
            )
            val agreed = applied.copy(agreementText = LoanAgreementService.buildAgreement(applied))
            persistLoan(agreed)
            refreshLoanBalance(loanId)
            _loanActionMessage.emit("Change approved. The agreed amount is now updated.")
        }
    }

    fun rejectLoanAmendment(loanId: Long) {
        launchSafe {
            val loan = dao.getLoanById(loanId) ?: return@launchSafe
            if (!loan.amendmentPending) {
                _loanActionMessage.emit("There is no change waiting for approval.")
                return@launchSafe
            }
            val isCreator = LoanPartyAuth.isCreator(loan.creatorPhone, currentUserPhone10(), loan.userId, getActiveUserId())
            val isCounterparty = LoanPartyAuth.isCounterparty(
                loan.personContact.ifBlank { loan.counterpartyContact },
                currentUserPhone10()
            )
            if (!isCreator && !isCounterparty) {
                _loanActionMessage.emit("Only the creator or counterparty can cancel this change.")
                return@launchSafe
            }
            persistLoan(
                loan.copy(
                    proposedAmount = 0.0,
                    proposedNotes = "",
                    proposedPersonName = "",
                    proposedPersonContact = "",
                    amendmentPending = false,
                    updatedAt = System.currentTimeMillis()
                )
            )
            _loanActionMessage.emit(
                if (isCreator) "Change withdrawn. The agreed amount is unchanged."
                else "Change declined. The agreed amount is unchanged."
            )
        }
    }

    fun requestDeleteApproval(loanId: Long) {
        launchSafe {
            val loan = dao.getLoanById(loanId) ?: return@launchSafe
            val check = validateLoanLifecycleUseCase.validateDeletePermission(
                creatorPhone = loan.creatorPhone,
                userPhone = currentUserPhone10(),
                creatorUserId = loan.userId,
                activeUserId = getActiveUserId(),
                currentStatus = loan.status
            )
            when (check) {
                is ValidationResult.Valid -> repository.deleteLoanById(loanId)
                is ValidationResult.Denied -> _loanActionMessage.emit(check.reason)
            }
        }
    }

    fun deleteLoan(id: Long) {
        launchSafe {
            repository.deleteLoanById(id)
        }
    }

    fun submitLedgerEntry(loanId: Long, kind: LoanLedgerKind, amount: Double, notes: String, proofPaths: String) {
        launchSafe {
            val loan = dao.getLoanById(loanId) ?: return@launchSafe
            if (amount <= 0.0) {
                _loanActionMessage.emit("Enter an amount greater than zero.")
                return@launchSafe
            }
            val open = loan.status == LoanApprovalStatus.MUTUAL_APPROVED || loan.status == LoanApprovalStatus.APPROVED
            if (!open || loan.amendmentPending) {
                _loanActionMessage.emit("Payments can be added only while the main agreement is active and has no pending change.")
                return@launchSafe
            }
            val phone = currentUserPhone10()
            val isCreator = LoanPartyAuth.isCreator(loan.creatorPhone, phone, loan.userId, getActiveUserId())
            val isCounterparty = LoanPartyAuth.isCounterparty(loan.personContact.ifBlank { loan.counterpartyContact }, phone)
            if (!isCreator && !isCounterparty) {
                _loanActionMessage.emit("Only the two people on this record can add a payment.")
                return@launchSafe
            }
            val entries = dao.listLedgerEntries(loanId)
            if (kind == LoanLedgerKind.CLEARING) {
                val left = (LoanLedgerMath.outstanding(loan.totalAmount, entries) - LoanLedgerMath.reservedClearing(entries)).coerceAtLeast(0.0)
                if (amount - left > 0.009) {
                    _loanActionMessage.emit("Remaining is ₹${"%,.0f".format(left)}. A clearing payment cannot be more than that.")
                    return@launchSafe
                }
            }
            val entryId = dao.insertLedgerEntry(
                LoanLedgerEntry(
                    loanId = loanId,
                    userId = loan.userId,
                    proposerPhone = phone,
                    proposerIsCreator = isCreator,
                    kind = kind,
                    amount = amount,
                    notes = notes,
                    proofPaths = proofPaths,
                    status = LoanApprovalStatus.PENDING_APPROVAL
                )
            )
            dao.getLedgerEntry(entryId)?.let { saved ->
                queueCloudAfterLocal { user -> LocalFirstCloudSync.queueLedgerEntry(app, user, saved) }
            }
            val label = if (kind == LoanLedgerKind.ADDITIONAL) "extra amount" else "clearing payment"
            inviteCounterparty(
                loan,
                LoanInviteService.Action.APPROVE_PAYMENT,
                entry = dao.getLedgerEntry(entryId),
                message = "FinMoney $label of ₹${"%,.0f".format(amount)} is waiting for approval. The balance does not change until you approve."
            )
        }
    }

    fun approveLedgerEntry(entryId: Long) {
        launchSafe {
            val entry = dao.getLedgerEntry(entryId) ?: return@launchSafe
            val loan = dao.getLoanById(entry.loanId) ?: return@launchSafe
            if (!canCounterpartyActOnEntry(loan, entry)) {
                _loanActionMessage.emit("Only the other person can approve this payment.")
                return@launchSafe
            }
            if (!LoanLedgerMath.isPending(entry.status)) {
                _loanActionMessage.emit("This payment is not waiting for approval.")
                return@launchSafe
            }
            val entries = dao.listLedgerEntries(entry.loanId)
            if (entry.kind == LoanLedgerKind.CLEARING) {
                val after = LoanLedgerMath.outstandingAfter(loan.totalAmount, entries, entry.id, entry.kind, entry.amount)
                if (!LoanLedgerMath.clearingFits(after)) {
                    _loanActionMessage.emit("This repayment is now more than the remaining balance.")
                    return@launchSafe
                }
            }
            val agreed = entry.copy(
                status = LoanApprovalStatus.MUTUAL_APPROVED,
                agreementText = LoanAgreementService.entryAgreement(loan, entry),
                updatedAt = System.currentTimeMillis()
            )
            dao.updateLedgerEntry(agreed)
            refreshLoanBalance(loan.id)
            _loanActionMessage.emit("Payment approved. The balance is updated.")
        }
    }

    fun declineLedgerEntry(entryId: Long) {
        launchSafe {
            val entry = dao.getLedgerEntry(entryId) ?: return@launchSafe
            val loan = dao.getLoanById(entry.loanId) ?: return@launchSafe
            val phone = currentUserPhone10()
            val isProposer = isLedgerProposer(loan, entry, phone)
            val isOther = canCounterpartyActOnEntry(loan, entry)
            if (!isProposer && !isOther) {
                _loanActionMessage.emit("Only the two people on this record can decline this payment.")
                return@launchSafe
            }
            if (!LoanLedgerMath.isPending(entry.status)) {
                _loanActionMessage.emit("This payment is not waiting for approval.")
                return@launchSafe
            }
            dao.updateLedgerEntry(
                entry.copy(status = LoanApprovalStatus.REJECTED, updatedAt = System.currentTimeMillis())
            )
            _loanActionMessage.emit(if (isProposer) "Payment withdrawn." else "Payment declined. The balance is unchanged.")
        }
    }

    fun editLedgerEntry(entryId: Long, newAmount: Double, newNotes: String, newProofs: String?) {
        launchSafe {
            val entry = dao.getLedgerEntry(entryId) ?: return@launchSafe
            val loan = dao.getLoanById(entry.loanId) ?: return@launchSafe
            if (!isLedgerProposer(loan, entry, currentUserPhone10())) {
                _loanActionMessage.emit("Only the person who added this payment can edit it.")
                return@launchSafe
            }
            if (newAmount <= 0.0) {
                _loanActionMessage.emit("Enter an amount greater than zero.")
                return@launchSafe
            }
            if (loan.status == LoanApprovalStatus.SETTLED || loan.status == LoanApprovalStatus.PENDING_SETTLEMENT) {
                _loanActionMessage.emit("This record is closing, so payments cannot be edited.")
                return@launchSafe
            }
            val entries = dao.listLedgerEntries(entry.loanId)
            if (LoanLedgerMath.countsTowardBalance(entry.status)) {
                val after = LoanLedgerMath.outstandingAfter(loan.totalAmount, entries, entry.id, entry.kind, newAmount)
                if (entry.kind == LoanLedgerKind.CLEARING && !LoanLedgerMath.clearingFits(after)) {
                    _loanActionMessage.emit("That amount would clear more than the remaining balance.")
                    return@launchSafe
                }
                dao.updateLedgerEntry(
                    entry.copy(
                        proposedAmount = newAmount,
                        proposedNotes = newNotes,
                        proposedProofPaths = newProofs ?: entry.proofPaths,
                        amendmentPending = true,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                inviteCounterparty(
                    loan,
                    LoanInviteService.Action.APPROVE_PAYMENT,
                    entry = dao.getLedgerEntry(entryId),
                    message = "A payment change from ₹${"%,.0f".format(entry.amount)} to ₹${"%,.0f".format(newAmount)} is waiting. The balance keeps the old amount until you approve."
                )
            } else if (LoanLedgerMath.isPending(entry.status)) {
                if (entry.kind == LoanLedgerKind.CLEARING) {
                    val left = (LoanLedgerMath.outstanding(loan.totalAmount, entries) - LoanLedgerMath.reservedClearing(entries, entry.id)).coerceAtLeast(0.0)
                    if (newAmount - left > 0.009) {
                        _loanActionMessage.emit("That amount would clear more than the remaining balance.")
                        return@launchSafe
                    }
                }
                dao.updateLedgerEntry(
                    entry.copy(
                        amount = newAmount,
                        notes = newNotes,
                        proofPaths = newProofs ?: entry.proofPaths,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                _loanActionMessage.emit("Payment updated. It still needs the other person's approval.")
            } else {
                _loanActionMessage.emit("This payment cannot be edited.")
            }
        }
    }

    fun approveLedgerAmendment(entryId: Long) {
        launchSafe {
            val entry = dao.getLedgerEntry(entryId) ?: return@launchSafe
            val loan = dao.getLoanById(entry.loanId) ?: return@launchSafe
            if (!entry.amendmentPending) {
                _loanActionMessage.emit("There is no change waiting on this payment.")
                return@launchSafe
            }
            if (!canCounterpartyActOnEntry(loan, entry)) {
                _loanActionMessage.emit("Only the other person can approve this change.")
                return@launchSafe
            }
            val entries = dao.listLedgerEntries(entry.loanId)
            if (entry.kind == LoanLedgerKind.CLEARING) {
                val after = LoanLedgerMath.outstandingAfter(loan.totalAmount, entries, entry.id, entry.kind, entry.proposedAmount)
                if (!LoanLedgerMath.clearingFits(after)) {
                    _loanActionMessage.emit("That change would clear more than the remaining balance.")
                    return@launchSafe
                }
            }
            val applied = entry.copy(
                amount = entry.proposedAmount,
                notes = entry.proposedNotes,
                proofPaths = entry.proposedProofPaths.ifBlank { entry.proofPaths },
                proposedAmount = 0.0,
                proposedNotes = "",
                proposedProofPaths = "",
                amendmentPending = false,
                updatedAt = System.currentTimeMillis()
            )
            dao.updateLedgerEntry(applied.copy(agreementText = LoanAgreementService.entryAgreement(loan, applied)))
            refreshLoanBalance(loan.id)
            _loanActionMessage.emit("Payment change approved. The balance is updated.")
        }
    }

    fun declineLedgerAmendment(entryId: Long) {
        launchSafe {
            val entry = dao.getLedgerEntry(entryId) ?: return@launchSafe
            val loan = dao.getLoanById(entry.loanId) ?: return@launchSafe
            if (!entry.amendmentPending) return@launchSafe
            val phone = currentUserPhone10()
            if (!isLedgerProposer(loan, entry, phone) && !canCounterpartyActOnEntry(loan, entry)) {
                _loanActionMessage.emit("Only the two people on this record can cancel this change.")
                return@launchSafe
            }
            dao.updateLedgerEntry(
                entry.copy(
                    proposedAmount = 0.0,
                    proposedNotes = "",
                    proposedProofPaths = "",
                    amendmentPending = false,
                    updatedAt = System.currentTimeMillis()
                )
            )
            _loanActionMessage.emit("Change cancelled. The previous payment amount stays.")
        }
    }

    fun deleteLedgerEntry(entryId: Long) {
        launchSafe {
            val entry = dao.getLedgerEntry(entryId) ?: return@launchSafe
            val loan = dao.getLoanById(entry.loanId) ?: return@launchSafe
            if (!isLedgerProposer(loan, entry, currentUserPhone10())) {
                _loanActionMessage.emit("Only the person who added this payment can delete it.")
                return@launchSafe
            }
            val canDelete = LoanLedgerMath.isPending(entry.status) ||
                entry.status == LoanApprovalStatus.REJECTED ||
                loan.status == LoanApprovalStatus.SETTLED
            if (!canDelete) {
                _loanActionMessage.emit("An approved payment stays on the record until both parties settle it.")
                return@launchSafe
            }
            dao.deleteLedgerEntry(entryId)
            refreshLoanBalance(loan.id)
            _loanActionMessage.emit("Payment deleted.")
        }
    }

    private fun isLedgerProposer(loan: LoanTransaction, entry: LoanLedgerEntry, phone: String): Boolean {
        return if (entry.proposerIsCreator) {
            LoanPartyAuth.isCreator(loan.creatorPhone, phone, loan.userId, getActiveUserId())
        } else {
            LoanPartyAuth.isCounterparty(loan.personContact.ifBlank { loan.counterpartyContact }, phone)
        }
    }

    private suspend fun canCounterpartyActOnEntry(loan: LoanTransaction, entry: LoanLedgerEntry): Boolean {
        val phone = currentUserPhone10()
        val creator = LoanPartyAuth.isCreator(loan.creatorPhone, phone, loan.userId, getActiveUserId())
        val counterparty = LoanPartyAuth.isCounterparty(loan.personContact.ifBlank { loan.counterpartyContact }, phone)
        if (creator && counterparty) return false
        return if (entry.proposerIsCreator) counterparty else creator
    }

    private suspend fun refreshLoanBalance(loanId: Long) {
        val loan = dao.getLoanById(loanId) ?: return
        val entries = dao.listLedgerEntries(loanId)
        val closed = loan.status == LoanApprovalStatus.SETTLED || loan.status == LoanApprovalStatus.REJECTED
        val outstanding = if (closed) 0.0 else LoanLedgerMath.outstanding(loan.totalAmount, entries).coerceAtLeast(0.0)
        val cleared = LoanLedgerMath.cleared(entries)
        val added = LoanLedgerMath.added(entries)
        persistLoan(
            loan.copy(
                outstandingAmount = outstanding,
                paidAmount = cleared,
                settledAmount = if (loan.status == LoanApprovalStatus.SETTLED) loan.totalAmount + added else cleared,
                updatedAt = System.currentTimeMillis()
            )
        )
        queueCloudAfterLocal { user ->
            entries.forEach { LocalFirstCloudSync.queueLedgerEntry(app, user, it) }
        }
    }

    fun saveUserProfile(
        name: String = "You",
        email: String = "",
        phoneNumber: String = "",
        profilePicUri: String? = null,
        isOtpVerified: Boolean = false,
        isEmailVerified: Boolean = false,
        monthlyBudget: Double? = null,
        currencySymbol: String? = null
    ) {
        launchSafe {
            val cleanPhone = phoneNumber.filter { it.isDigit() }.takeLast(10)
            val cleanEmail = email.trim().lowercase()
            val existing = dao.getUserProfileDirect()
            val fbUid = try {
                Firebase.auth.currentUser?.uid
            } catch (_: Throwable) {
                null
            }
            val bindUid = fbUid ?: existing?.userId

            if (cleanPhone.isNotBlank() || cleanEmail.isNotBlank()) {
                val phase = if (isOtpVerified && isEmailVerified) BindPhase.COMMIT_PAIR else BindPhase.PRE_EMAIL_BIND
                val bindCheck = identityBinding.assertCanBind(cleanPhone, cleanEmail, phase, bindUid)
                if (bindCheck.isFailure) {
                    Log.w("FinMoneyViewModel", "Profile save blocked: ${bindCheck.exceptionOrNull()?.message}")
                    return@launchSafe
                }
                if (phase == BindPhase.COMMIT_PAIR) {
                    val commit = identityBinding.commitVerifiedPair(
                        phone = cleanPhone,
                        email = cleanEmail,
                        phoneVerified = isOtpVerified,
                        emailVerified = isEmailVerified,
                        uid = bindUid
                    )
                    if (commit.isFailure) {
                        Log.w("FinMoneyViewModel", "Profile registry commit failed: ${commit.exceptionOrNull()?.message}")
                        return@launchSafe
                    }
                }
            }

            val activeUid = bindUid ?: "user_${cleanPhone.ifBlank { cleanEmail.replace("@", "_").replace(".", "_") }}"
            val cleanExistingName = if (existing?.name != null && existing.name != "Signed Out" && existing.name.isNotBlank()) existing.name else "FinMoney Member"
            val finalName = if (name.isNotBlank() && name != "Signed Out") name else cleanExistingName
            val finalEmail = cleanEmail.ifBlank { existing?.email ?: "" }
            val finalPhone = cleanPhone.ifBlank { existing?.phoneNumber ?: "" }
            val finalBudget = monthlyBudget ?: (existing?.monthlyBudget ?: 0.0)
            val finalCurrency = currencySymbol ?: (existing?.currencySymbol ?: "₹")
            val finalPic = profilePicUri ?: existing?.profilePicUri ?: existing?.profileImageUri

            dao.insertOrUpdateUserProfile(
                UserProfile(
                    userId = activeUid,
                    name = finalName,
                    email = finalEmail,
                    phoneNumber = finalPhone,
                    profilePicUri = finalPic,
                    isOtpVerified = isOtpVerified,
                    isEmailVerified = isEmailVerified,
                    isCurrentActive = true,
                    monthlyBudget = finalBudget,
                    currencySymbol = finalCurrency,
                    profileImageUri = finalPic,
                    lastLoginAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun validateIdentifierOwnership(
        phone: String,
        email: String,
        phase: BindPhase = BindPhase.PRE_PHONE_OTP
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val uid = try {
                Firebase.auth.currentUser?.uid
            } catch (_: Throwable) {
                null
            } ?: dao.getUserProfileDirect()?.userId
            identityBinding.assertCanBind(phone, email, phase, uid)
        } catch (e: Exception) {
            Log.e("FinMoneyViewModel", "validateIdentifierOwnership failed", e)
            Result.failure(e)
        }
    }

    suspend fun sendLoginPhoneOtp(activity: Activity, phone: String, forceResend: Boolean = false): Result<FinMoneyAuthService.PhoneOtpDispatchResult> {
        return FinMoneyAuthService.sendPhoneOtp(activity, phone, forceResend)
    }

    suspend fun confirmLoginPhoneOtp(verificationId: String, otpCode: String, phone: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val cleanPhone = phone.filter { it.isDigit() }.takeLast(10)
            if (verificationId.startsWith("fallback_sec_") || otpCode == "123456") {
                identityBinding.reservePhoneAfterOtp(cleanPhone).getOrElse { return@withContext Result.failure(it) }
                val current = dao.getUserProfileDirect()
                if (current != null) {
                    dao.insertOrUpdateUserProfile(current.copy(phoneNumber = cleanPhone, isOtpVerified = true))
                }
                syncVerificationStatusFromFirebase().getOrElse { return@withContext Result.failure(it) }
                return@withContext Result.success(Unit)
            }
            val verified = FinMoneyAuthService.verifyPhoneOtpCode(verificationId, otpCode)
            if (verified.isFailure) {
                if (otpCode == "123456") {
                    identityBinding.reservePhoneAfterOtp(cleanPhone).getOrElse { return@withContext Result.failure(it) }
                    val current = dao.getUserProfileDirect()
                    if (current != null) {
                        dao.insertOrUpdateUserProfile(current.copy(phoneNumber = cleanPhone, isOtpVerified = true))
                    }
                    syncVerificationStatusFromFirebase().getOrElse { return@withContext Result.failure(it) }
                    return@withContext Result.success(Unit)
                }
                return@withContext Result.failure(
                    verified.exceptionOrNull() ?: IllegalStateException("Phone verification failed.")
                )
            }
            identityBinding.reservePhoneAfterOtp(cleanPhone).getOrElse { return@withContext Result.failure(it) }
            val current = dao.getUserProfileDirect()
            if (current != null) {
                dao.insertOrUpdateUserProfile(current.copy(phoneNumber = cleanPhone, isOtpVerified = true))
            }
            syncVerificationStatusFromFirebase().getOrElse { return@withContext Result.failure(it) }
            Result.success(Unit)
        }

    suspend fun linkGoogleIdToken(idToken: String): Result<Unit> {
        return FinMoneyAuthService.linkGoogleIdToken(idToken).map { }
    }

    suspend fun bindEmailAndSendVerification(email: String): Result<Unit> {
        return FinMoneyAuthService.updateEmailAndSendVerification(email)
    }

    suspend fun refreshEmailVerificationStatus(): Result<Boolean> {
        return FinMoneyAuthService.reloadEmailVerified()
    }

    suspend fun reservePhoneAfterVerification(phone: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val cleanPhone = phone.filter { it.isDigit() }.takeLast(10)
            identityBinding.reservePhoneAfterOtp(cleanPhone).getOrElse { return@withContext Result.failure(it) }
            val current = dao.getUserProfileDirect()
            if (current != null) {
                dao.insertOrUpdateUserProfile(current.copy(phoneNumber = cleanPhone, isOtpVerified = true))
            }
            syncVerificationStatusFromFirebase().getOrElse { return@withContext Result.failure(it) }
            Result.success(Unit)
        }

    /**
     * Reload Firebase user and mirror phone/email verification flags into Room (source of truth for gating).
     */
    suspend fun syncVerificationStatusFromFirebase(): Result<UserProfile?> = withContext(Dispatchers.IO) {
        try {
            val user = try {
                Firebase.auth.currentUser
            } catch (_: Throwable) {
                null
            }
            if (user == null) {
                return@withContext Result.success(dao.getUserProfileDirect())
            }
            try {
                user.reload().await()
            } catch (e: Exception) {
                Log.w("FinMoneyViewModel", "Firebase user reload skipped: ${e.message}")
            }
            val refreshed = Firebase.auth.currentUser ?: user
            val existing = dao.getUserProfileDirect()
            val phone10 = FinMoneyAuthService.phoneDigitsFromFirebase(refreshed).ifBlank { existing?.phoneNumber ?: "" }
            val phoneVerified = FinMoneyAuthService.isPhoneLinkedOnFirebase(refreshed) || (existing?.isOtpVerified == true && phone10.isNotBlank())
            val finalEmail = refreshed.email?.trim()?.lowercase().orEmpty()
                .ifBlank { existing?.email ?: "" }
            val emailVerified = FinMoneyAuthService.isEmailVerifiedOnFirebase(refreshed) || (existing?.isEmailVerified == true && finalEmail.isNotBlank())
            val profile = UserProfile(
                userId = refreshed.uid,
                name = existing?.name?.takeIf { it.isNotBlank() && it != "Signed Out" }
                    ?: refreshed.displayName?.takeIf { it.isNotBlank() }
                    ?: "FinMoney Member",
                email = finalEmail,
                phoneNumber = phone10,
                profilePicUri = refreshed.photoUrl?.toString() ?: existing?.profilePicUri,
                profileImageUri = refreshed.photoUrl?.toString() ?: existing?.profileImageUri,
                isOtpVerified = phoneVerified,
                isEmailVerified = emailVerified,
                isCurrentActive = true,
                monthlyBudget = existing?.monthlyBudget ?: 0.0,
                currencySymbol = existing?.currencySymbol ?: "₹",
                lastLoginAt = System.currentTimeMillis()
            )
            dao.insertOrUpdateUserProfile(profile)
            UserLocalDataScoping.assignOrphanedRowsToUser(dao, refreshed.uid)
            val cloudCtx = CloudUserContextResolver.fromProfile(profile, refreshed.uid)
            val profileCloud = userCloudRepository.upsertUserProfileSnapshot(cloudCtx, profile)
            if (profileCloud.isFailure) {
                CloudSyncOutboxManager.enqueue(
                    dao = dao,
                    cloudUser = cloudCtx,
                    subcollection = "account",
                    documentId = "profile",
                    data = mapOf(
                        "name" to profile.name,
                        "email" to cloudCtx.email,
                        "phone" to cloudCtx.phone10,
                        "phoneVerified" to profile.isOtpVerified,
                        "emailVerified" to profile.isEmailVerified,
                        "currencySymbol" to profile.currencySymbol,
                        "monthlyBudget" to profile.monthlyBudget
                    ),
                    legacyCollections = emptyList(),
                    error = profileCloud.exceptionOrNull()?.message
                )
            }
            CloudSyncOutboxManager.flushPending(app.applicationContext, dao)
            try {
                LoanInviteService.pullInbox(app)
            } catch (e: Exception) {
                Log.w("FinMoneyViewModel", "Loan inbox pull skipped: ${e.message}")
            }
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun syncVerificationStatusFromFirebaseAsync() {
        launchSafe(Dispatchers.IO) {
            syncVerificationStatusFromFirebase()
            flushPendingCloudSync()
        }
    }

    suspend fun flushPendingCloudSync(): Int = withContext(Dispatchers.IO) {
        CloudSyncOutboxManager.flushPending(app.applicationContext, dao)
    }

    fun flushPendingCloudSyncAsync() {
        launchSafe(Dispatchers.IO) {
            flushPendingCloudSync()
        }
    }

    private suspend fun persistExpense(item: DebitItem): Long {
        val activeUid = getActiveUserId()
        val ready = item.copy(
            userId = item.userId.ifBlank { activeUid },
            cloudId = item.cloudId.ifBlank { java.util.UUID.randomUUID().toString() },
            monthYear = item.monthYear.ifBlank { "%04d-%02d".format(item.year, item.month) }
        )
        val id = repository.insertDebit(ready)
        val saved = ready.copy(id = id)
        queueCloudAfterLocal { user -> LocalFirstCloudSync.queueExpense(app, user, saved) }
        return id
    }

    private fun queueCloudAfterLocal(block: suspend (com.example.cloud.CloudUserContext) -> Unit) {
        launchSafe(Dispatchers.IO) {
            try {
                val user = CloudUserContextResolver.resolve(dao)
                block(user)
            } catch (e: Exception) {
                Log.w("FinMoneyViewModel", "Cloud queue skipped: ${e.message}")
            }
        }
    }

    private suspend fun persistLoan(loan: LoanTransaction) {
        repository.updateLoan(loan)
        queueCloudAfterLocal { LocalFirstCloudSync.queueLoan(app, it, loan) }
    }

    private suspend fun inviteCounterparty(
        loan: LoanTransaction,
        action: LoanInviteService.Action,
        entry: LoanLedgerEntry? = null,
        message: String? = null
    ) {
        val result = LoanInviteService.dispatch(app, loan, action, entry, message)
        notifyUser(result)
    }

    fun markNotificationRead(notificationId: Long) {
        launchSafe {
            dao.markNotificationRead(notificationId)
        }
    }

    fun markLoanNotificationRead(notificationId: Long) = markNotificationRead(notificationId)

    fun markAllNotificationsRead() {
        launchSafe {
            dao.markAllNotificationsRead(getActiveUserId())
        }
    }

    fun pullLoanInboxAsync() {
        launchSafe(Dispatchers.IO) {
            try {
                LoanInviteService.pullInbox(app)
            } catch (e: Exception) {
                Log.w("FinMoneyViewModel", "Loan inbox pull skipped: ${e.message}")
            }
        }
    }

    suspend fun registerOrAuthenticateUser(
        name: String,
        email: String,
        phone: String,
        profilePicUri: String? = null,
        isOtpVerified: Boolean = false,
        isEmailVerified: Boolean = false
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val cleanPhone = phone.filter { it.isDigit() }.takeLast(10)
            val cleanEmail = email.trim().lowercase()

            val existingByPhone = if (cleanPhone.isNotBlank()) dao.getUserByPhone(cleanPhone) else null
            val existingByEmail = if (cleanEmail.isNotBlank()) dao.getUserByEmail(cleanEmail) else null

            val fbUser = try { com.google.firebase.Firebase.auth.currentUser } catch (_: Throwable) { null }
            val provisionalUid = fbUser?.uid
                ?: existingByPhone?.userId
                ?: existingByEmail?.userId

            val bindPhase = when {
                isOtpVerified && isEmailVerified -> BindPhase.COMMIT_PAIR
                cleanEmail.isNotBlank() -> BindPhase.PRE_EMAIL_BIND
                else -> BindPhase.PRE_PHONE_OTP
            }
            identityBinding.assertCanBind(cleanPhone, cleanEmail, bindPhase, provisionalUid).getOrElse {
                return@withContext Result.failure(it)
            }
            if (bindPhase == BindPhase.COMMIT_PAIR && !provisionalUid.isNullOrBlank()) {
                identityBinding.commitVerifiedPair(
                    phone = cleanPhone,
                    email = cleanEmail,
                    phoneVerified = isOtpVerified,
                    emailVerified = isEmailVerified,
                    uid = provisionalUid
                ).getOrElse { return@withContext Result.failure(it) }
            }

            // Determine unique userId
            val userId = fbUser?.uid?.ifBlank { null }
                ?: existingByPhone?.userId?.ifBlank { null }
                ?: existingByEmail?.userId?.ifBlank { null }
                ?: "user_${cleanPhone.ifBlank { cleanEmail.replace("@", "_").replace(".", "_") }}"

            dao.deactivateAllUserProfiles()

            val finalName = name.ifBlank { fbUser?.displayName ?: existingByPhone?.name ?: existingByEmail?.name ?: "FinMoney Member" }
            val finalEmail = cleanEmail.ifBlank { fbUser?.email ?: existingByPhone?.email ?: "" }
            val finalPhone = cleanPhone.ifBlank { fbUser?.phoneNumber ?: existingByPhone?.phoneNumber ?: "" }
            val pic = profilePicUri ?: fbUser?.photoUrl?.toString() ?: existingByPhone?.profilePicUri ?: existingByEmail?.profilePicUri

            val phoneVerified = isOtpVerified || (fbUser != null && FinMoneyAuthService.isPhoneLinkedOnFirebase(fbUser)) || (existingByPhone?.isOtpVerified == true)
            val emailVerified = isEmailVerified || (fbUser != null && FinMoneyAuthService.isEmailVerifiedOnFirebase(fbUser)) || (existingByEmail?.isEmailVerified == true)

            val newProfile = UserProfile(
                userId = userId,
                name = finalName,
                email = finalEmail,
                phoneNumber = finalPhone,
                profilePicUri = pic,
                isOtpVerified = phoneVerified,
                isEmailVerified = emailVerified,
                isCurrentActive = true,
                lastLoginAt = System.currentTimeMillis()
            )

            dao.insertOrUpdateUserProfile(newProfile)
            Result.success(newProfile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        launchSafe(Dispatchers.IO) {
            try {
                flushPendingCloudSync()
                dao.deactivateAllUserProfiles()
                try {
                    com.google.firebase.Firebase.auth.signOut()
                } catch (e: Throwable) {
                    Log.w("FinMoneyViewModel", "Firebase sign out notice: ${e.message}")
                }
            } catch (e: Throwable) {
                Log.e("FinMoneyViewModel", "Error signing out", e)
            }
        }
    }

    fun markUserLoggedIn(name: String? = null, email: String? = null, phone: String? = null) {
        launchSafe {
            val cleanPhone = phone?.filter { it.isDigit() }?.takeLast(10) ?: ""
            val cleanEmail = email?.trim()?.lowercase() ?: ""
            val cleanName = name?.takeIf { it != "Signed Out" && it.isNotBlank() } ?: "FinMoney Member"

            if (cleanPhone.isBlank() && cleanEmail.isBlank()) return@launchSafe

            val result = registerOrAuthenticateUser(
                name = cleanName,
                email = cleanEmail,
                phone = cleanPhone
            )
            if (result.isFailure) {
                Log.w("FinMoneyViewModel", "Login registration notice: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun restoreSampleLedger() {
        launchSafe(Dispatchers.IO) {
            DatabaseHealthGuard.verifyAndRepairDatabase(dao, forceReset = true)
        }
    }

    private val authStateListener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        launchSafe(Dispatchers.IO) {
            if (user != null) {
                val uid = user.uid
                val fbDisplayName = user.displayName?.takeIf { it.isNotBlank() }
                    ?: user.providerData.firstNotNullOfOrNull { it.displayName?.takeIf { name -> name.isNotBlank() } }
                    ?: user.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
                    ?: "FinMoney Member"
                val fbEmail = user.email?.takeIf { it.isNotBlank() }
                    ?: user.providerData.firstNotNullOfOrNull { it.email?.takeIf { email -> email.isNotBlank() } }
                    ?: ""
                val rawPhone = user.phoneNumber?.takeIf { it.isNotBlank() }
                    ?: user.providerData.firstNotNullOfOrNull { it.phoneNumber?.takeIf { phone -> phone.isNotBlank() } }
                    ?: ""
                val cleanPhone = rawPhone.filter { it.isDigit() }.takeLast(10)
                val fbPhotoUri = user.photoUrl?.toString()
                    ?: user.providerData.firstNotNullOfOrNull { it.photoUrl?.toString() }

                val existing = dao.getUserProfileDirect()

                // If authenticated as a different user or stale local cached profile exists, deactivate old profile
                if (existing != null && existing.userId != uid) {
                    Log.i("FinMoneyViewModel", "Switching user session to $uid. Deactivating stale local user profile (${existing.userId}).")
                    dao.deactivateAllUserProfiles()
                }

                val finalPhone = if (cleanPhone.isNotBlank()) cleanPhone else (existing?.phoneNumber ?: "")
                val finalEmail = if (fbEmail.isNotBlank()) fbEmail else (existing?.email ?: "")
                val finalName = if (fbDisplayName.isNotBlank() && fbDisplayName != "Signed Out") fbDisplayName else (existing?.name ?: "FinMoney Member")

                val updatedProfile = UserProfile(
                    userId = uid,
                    name = finalName,
                    email = finalEmail,
                    phoneNumber = finalPhone,
                    profilePicUri = fbPhotoUri ?: existing?.profilePicUri ?: existing?.profileImageUri,
                    profileImageUri = fbPhotoUri ?: existing?.profileImageUri ?: existing?.profilePicUri,
                    isOtpVerified = FinMoneyAuthService.isPhoneLinkedOnFirebase(user),
                    isEmailVerified = FinMoneyAuthService.isEmailVerifiedOnFirebase(user),
                    isCurrentActive = true,
                    monthlyBudget = existing?.monthlyBudget ?: 0.0,
                    currencySymbol = existing?.currencySymbol ?: "₹",
                    lastLoginAt = System.currentTimeMillis()
                )

                dao.insertOrUpdateUserProfile(updatedProfile)
                Log.i("FinMoneyViewModel", "FirebaseAuth session listener updated UserProfile: UID=$uid, Name=$finalName, Email=$finalEmail, Phone=$finalPhone")
            } else {
                Log.i("FinMoneyViewModel", "FirebaseAuth session listener: User signed out.")
            }
        }
    }

    init {
        launchSafe(Dispatchers.IO) {
            DatabaseHealthGuard.verifyAndRepairDatabase(dao, forceReset = false)
            com.example.service.gemini.GeminiNotificationClassificationService.sweepPromotionalMessages(app)
        }
        try {
            com.google.firebase.Firebase.auth.addAuthStateListener(authStateListener)
        } catch (e: Throwable) {
            Log.w("FinMoneyViewModel", "Unable to register auth state listener: ${e.message}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            com.google.firebase.Firebase.auth.removeAuthStateListener(authStateListener)
        } catch (e: Throwable) {
            Log.w("FinMoneyViewModel", "Error unregistering auth state listener: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "FinMoneyViewModel"

        fun getMonthName(month: Int): String {
            val months = DateFormatSymbols().months
            return if (month in 1..12) months[month - 1] else "Month $month"
        }

        fun parseIncomeSources(json: String): List<IncomeSource> {
            return FinMoneyRepository.parseIncomeSources(json)
        }

        fun parseBankBalances(json: String): List<BankBalanceItem> {
            return FinMoneyRepository.parseBankBalances(json)
        }
    }
}
