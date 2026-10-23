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
import com.example.service.LoanAgreementService
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormatSymbols

class FinMoneyViewModel(private val app: Application) : AndroidViewModel(app) {
    val repository: FinMoneyRepository = FinMoneyRepository(AppDatabase.getDatabase(app).finMoneyDao())
    private val dao = AppDatabase.getDatabase(app).finMoneyDao()
    private val groqApiClient = GroqApiClient(app)

    // Domain Use Cases
    val calculateBudgetHealthUseCase = CalculateBudgetHealthUseCase()
    val validateLoanLifecycleUseCase = ValidateLoanLifecycleUseCase()
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
    val allDebits: StateFlow<List<DebitItem>> = userProfile
        .flatMapLatest { u -> dao.getAllDebits(u?.userId ?: getActiveUserId()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val notifications: StateFlow<List<AppNotification>> = userProfile
        .flatMapLatest { u -> dao.getAllNotifications(u?.userId ?: getActiveUserId()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val bankTransactions: StateFlow<List<BankTransactionRecord>> = userProfile
        .flatMapLatest { u -> dao.getAllBankTransactions(u?.userId ?: getActiveUserId()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monthlySummary: StateFlow<MonthlyFinanceSummary?> = combine(
        currentSalaryRecord,
        currentDebits,
        currentReconciliation,
        _currentMonth,
        _currentYear
    ) { salary, debits, recon, m, y ->
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

        val categoryGroups = debits.groupBy { it.category }
        val categorySummaries = categoryGroups.map { (cat, items) ->
            val catTotal = items.sumOf { it.amount }
            val catPaid = items.filter { it.isPaid }.sumOf { it.amount }
            val pct = if (totalDebits > 0) (catTotal / totalDebits).toFloat() else 0f
            CategorySummary(
                categoryName = cat,
                categoryKey = cat.uppercase(),
                iconKey = cat,
                totalAmount = catTotal,
                paidAmount = catPaid,
                yetToPayAmount = catTotal - catPaid,
                itemCount = items.size,
                paidCount = items.count { it.isPaid },
                percentage = pct
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

    private val _isNotificationAccessGranted = MutableStateFlow(true)
    val isNotificationAccessGranted: StateFlow<Boolean> = _isNotificationAccessGranted.asStateFlow()

    fun checkNotificationAccess(context: Context) {}

    fun saveReconciliation(
        month: Int = 10,
        year: Int = 2026,
        bankBalances: List<BankBalanceItem> = emptyList(),
        manualAdjustment: Any = 0.0,
        adjustmentNote: String = "",
        isReconciled: Boolean = true
    ) {
        viewModelScope.launch {
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
        viewModelScope.launch {
            dao.deleteBankTransaction(id)
        }
    }

    fun deleteBankTransaction(tx: BankTransactionRecord) {
        viewModelScope.launch {
            dao.deleteBankTransaction(tx.id)
        }
    }

    fun updateBankTransaction(tx: BankTransactionRecord) {
        viewModelScope.launch {
            val activeUid = getActiveUserId()
            val updatedTx = if (tx.userId.isBlank()) tx.copy(userId = activeUid) else tx
            dao.insertBankTransaction(updatedTx)
        }
    }

    fun clearAllBankTransactions() {
        viewModelScope.launch {
            dao.clearAllBankTransactions(getActiveUserId())
        }
    }

    fun scanDeviceSms(context: Context) {}
    fun parseAndProcessCustomAlert(text: String, title: String = "", context: Context? = null) {}

    val pendingBankTransactions: StateFlow<List<BankTransactionRecord>> = bankTransactions.map { list ->
        val pending = list.filter { tx ->
            !tx.isConfirmed &&
                tx.status.equals("PENDING", ignoreCase = true) &&
                !tx.isDuplicate &&
                !tx.isIncorrect
        }
        val audit = EntryIntegrityEngine.auditPendingTransactions(pending)
        pending.filter { tx -> audit[tx.id]?.isDuplicate != true }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun approveAllPendingTransactions() {
        viewModelScope.launch {
            val pending = dao.getPendingBankTransactionsDirect(getActiveUserId())
            val audit = EntryIntegrityEngine.auditPendingTransactions(pending)
            pending.filter { tx -> audit[tx.id]?.isDuplicate != true }.forEach { tx ->
                BankSyncManager.approveTransactionAndCreateExpense(
                    context = app,
                    txId = tx.id,
                    customCategory = tx.category
                )
            }
        }
    }

    fun approvePendingTransaction(id: Long, category: String = "GENERAL") {
        viewModelScope.launch {
            BankSyncManager.approveTransactionAndCreateExpense(
                context = app,
                txId = id,
                customCategory = category
            )
        }
    }

    fun approvePendingTransaction(tx: BankTransactionRecord) {
        approvePendingTransaction(tx.id, tx.category)
    }

    fun rejectPendingTransaction(id: Long) {
        viewModelScope.launch {
            BankSyncManager.rejectTransaction(
                context = app,
                txId = id
            )
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

        viewModelScope.launch(Dispatchers.IO) {
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
        viewModelScope.launch {
            val activeUid = getActiveUserId()
            repository.insertDebit(
                DebitItem(
                    userId = activeUid,
                    title = title,
                    amount = amount,
                    category = category.uppercase(),
                    dueDateDay = dueDateDay,
                    month = month,
                    year = year,
                    notes = notes,
                    principalAmount = principalAmount,
                    interestRate = interestRate,
                    totalTenureMonths = totalTenureMonths,
                    currentMonthTenure = currentMonthTenure,
                    isPaidViaCreditCard = isPaidViaCreditCard,
                    isRecurring = isRecurring
                )
            )
        }
    }

    /**
     * Prefills recurring debit obligations from recurring templates or previous months into target month.
     * Automatically increments currentMonthTenure for Chit and Loan EMIs.
     */
    fun prefillRecurringDebitsForMonth(targetMonth: Int = _currentMonth.value, targetYear: Int = _currentYear.value) {
        viewModelScope.launch {
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
                    dao.insertDebits(newDebitsToInsert)
                    Log.i("FinMoneyViewModel", "Prefilled ${newDebitsToInsert.size} recurring debits into $targetMonth/$targetYear for $activeUid")
                }

            } catch (e: Exception) {
                Log.e("FinMoneyViewModel", "Failed to prefill recurring debits: ${e.message}", e)
            }
        }
    }

    fun toggleDebitRecurring(item: DebitItem) {
        viewModelScope.launch {
            val updated = item.copy(isRecurring = !item.isRecurring)
            repository.updateDebit(updated)
        }
    }

    fun insertDebit(item: DebitItem) {
        viewModelScope.launch {
            val activeUid = getActiveUserId()
            val itemWithUid = if (item.userId.isBlank()) item.copy(userId = activeUid) else item
            repository.insertDebit(itemWithUid)
        }
    }

    fun insertDebits(items: List<DebitItem>) {
        viewModelScope.launch {
            val activeUid = getActiveUserId()
            val itemsWithUid = items.map { if (it.userId.isBlank()) it.copy(userId = activeUid) else it }
            repository.insertDebits(itemsWithUid)
        }
    }

    fun deleteDebit(item: DebitItem) {
        viewModelScope.launch {
            repository.deleteDebit(item)
        }
    }

    fun updateDebit(item: DebitItem) {
        viewModelScope.launch {
            repository.updateDebit(item)
        }
    }

    fun toggleDebitCreditCardStatus(item: DebitItem) {
        viewModelScope.launch {
            val newCcStatus = !item.isActualCreditCardTransaction()
            repository.updateDebit(
                item.copy(
                    isPaidViaCreditCard = newCcStatus,
                    isPaidViaDebitCard = if (newCcStatus) false else item.isPaidViaDebitCard
                )
            )
        }
    }

    fun toggleDebitPaid(item: DebitItem) {
        viewModelScope.launch {
            dao.setDebitPaidStatus(item.id, !item.isPaid)
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
    }

    fun addIncomeCredit(
        title: String,
        amount: Double,
        category: String = "OTHER",
        dateString: String? = null,
        month: Int? = null,
        year: Int? = null
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

        viewModelScope.launch {
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
                receivedDateTimestamp = timestamp
            )
            currentSources.add(newSource)
            saveUpdatedIncomeSources(existing, currentSources, m, y)
        }
    }

    fun updateIncomeSource(
        id: String,
        newTitle: String,
        newAmount: Double,
        newCategory: String = "OTHER",
        dateString: String? = null,
        month: Int? = null,
        year: Int? = null
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

        viewModelScope.launch {
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
                    receivedDateTimestamp = timestamp
                )
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

        viewModelScope.launch {
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

        viewModelScope.launch {
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

        viewModelScope.launch {
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
        viewModelScope.launch {
            val activeUid = getActiveUserId()
            val draft = LoanTransaction(
                userId = activeUid,
                personName = personName,
                counterpartyName = personName,
                personContact = phone,
                counterpartyContact = phone,
                totalAmount = amount,
                principalAmount = amount,
                type = loanType,
                status = LoanApprovalStatus.PENDING_APPROVAL,
                counterpartyApproved = false,
                notes = notes,
                interestRatePercent = interestPercent,
                startDateTimestamp = startDate,
                dueDateTimestamp = returnDate,
                dueDate = returnDate,
                proofPaths = proofPaths,
                agreementText = ""
            )
            val id = repository.insertLoan(draft)
            if (sendNow) {
                LoanAgreementService.notifyCounterparty(getApplication(), draft.copy(id = id))
            }
        }
    }

    fun notifyLoanCounterparty(loanId: Long) {
        viewModelScope.launch {
            val loan = dao.getLoanById(loanId) ?: return@launch
            if (loan.status == LoanApprovalStatus.REJECTED || loan.status == LoanApprovalStatus.SETTLED) return@launch
            if (loan.counterpartyApproved && loan.status != LoanApprovalStatus.PENDING_APPROVAL &&
                loan.status != LoanApprovalStatus.PENDING_SETTLEMENT
            ) {
                return@launch
            }
            LoanAgreementService.notifyCounterparty(getApplication(), loan)
        }
    }

    fun approveLoan(loanId: Long) {
        viewModelScope.launch {
            val loan = dao.getLoanById(loanId) ?: return@launch
            val agreed = LoanAgreementService.buildAgreement(loan)
            repository.updateLoan(
                loan.copy(
                    status = LoanApprovalStatus.MUTUAL_APPROVED,
                    counterpartyApproved = true,
                    agreementText = agreed,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun rejectLoan(loanId: Long) {
        viewModelScope.launch {
            dao.updateLoanApproval(loanId, LoanApprovalStatus.REJECTED.name, false)
        }
    }

    fun settleLoan(loanId: Long) {
        viewModelScope.launch {
            dao.settleLoan(loanId)
        }
    }

    fun requestEditApproval(loanId: Long, newAmount: Double, newNotes: String) {
        viewModelScope.launch {
            val loan = dao.getLoanById(loanId)
            if (loan != null) {
                val updated = loan.copy(
                    totalAmount = newAmount,
                    principalAmount = newAmount,
                    notes = newNotes,
                    status = LoanApprovalStatus.PENDING_APPROVAL,
                    counterpartyApproved = false,
                    agreementText = "",
                    updatedAt = System.currentTimeMillis()
                )
                repository.updateLoan(updated)
                LoanAgreementService.notifyCounterparty(getApplication(), updated)
            }
        }
    }

    fun requestDeleteApproval(loanId: Long) {
        viewModelScope.launch {
            val loan = dao.getLoanById(loanId)
            if (loan != null) {
                val updated = loan.copy(
                    status = LoanApprovalStatus.PENDING_SETTLEMENT,
                    counterpartyApproved = false,
                    updatedAt = System.currentTimeMillis()
                )
                repository.updateLoan(updated)
                LoanAgreementService.notifyCounterparty(getApplication(), updated)
            }
        }
    }

    fun deleteLoan(id: Long) {
        viewModelScope.launch {
            repository.deleteLoanById(id)
        }
    }

    fun saveUserProfile(
        name: String = "You",
        email: String = "",
        phoneNumber: String = "",
        profilePicUri: String? = null,
        isOtpVerified: Boolean = true,
        isEmailVerified: Boolean = true,
        monthlyBudget: Double? = null,
        currencySymbol: String? = null
    ) {
        viewModelScope.launch {
            val existing = dao.getUserProfileDirect()
            val activeUid = existing?.userId ?: "user_${phoneNumber.filter { it.isDigit() }.takeLast(10).ifBlank { "default" }}"
            val cleanExistingName = if (existing?.name != null && existing.name != "Signed Out" && existing.name.isNotBlank()) existing.name else "FinMoney Member"
            val finalName = if (name.isNotBlank() && name != "Signed Out") name else cleanExistingName
            val finalEmail = if (email.isNotBlank()) email else (existing?.email ?: "")
            val finalPhone = if (phoneNumber.isNotBlank()) phoneNumber.filter { it.isDigit() }.takeLast(10) else (existing?.phoneNumber ?: "")
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

    suspend fun registerOrAuthenticateUser(
        name: String,
        email: String,
        phone: String,
        profilePicUri: String? = null
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val cleanPhone = phone.filter { it.isDigit() }.takeLast(10)
            val cleanEmail = email.trim().lowercase()

            val existingByPhone = if (cleanPhone.isNotBlank()) dao.getUserByPhone(cleanPhone) else null
            val existingByEmail = if (cleanEmail.isNotBlank()) dao.getUserByEmail(cleanEmail) else null

            // Prevent duplicate registration collisions across users
            if (existingByPhone != null && cleanEmail.isNotBlank() && existingByPhone.email.isNotBlank() && existingByPhone.email.lowercase() != cleanEmail) {
                return@withContext Result.failure(
                    IllegalArgumentException("Mobile number +91 $cleanPhone is already registered to ${existingByPhone.email}. Please sign in with your registered email or use a different phone number.")
                )
            }

            if (existingByEmail != null && cleanPhone.isNotBlank() && existingByEmail.phoneNumber.isNotBlank() && existingByEmail.phoneNumber != cleanPhone) {
                return@withContext Result.failure(
                    IllegalArgumentException("Email address $cleanEmail is already registered to phone +91 ${existingByEmail.phoneNumber}. Please sign in with your registered phone number.")
                )
            }

            // Determine unique userId
            val fbUser = try { com.google.firebase.Firebase.auth.currentUser } catch (_: Throwable) { null }
            val userId = fbUser?.uid?.ifBlank { null }
                ?: existingByPhone?.userId?.ifBlank { null }
                ?: existingByEmail?.userId?.ifBlank { null }
                ?: "user_${cleanPhone.ifBlank { cleanEmail.replace("@", "_").replace(".", "_") }}"

            dao.deactivateAllUserProfiles()

            val finalName = name.ifBlank { fbUser?.displayName ?: existingByPhone?.name ?: existingByEmail?.name ?: "FinMoney Member" }
            val finalEmail = cleanEmail.ifBlank { fbUser?.email ?: existingByPhone?.email ?: "" }
            val finalPhone = cleanPhone.ifBlank { fbUser?.phoneNumber ?: existingByPhone?.phoneNumber ?: "" }
            val pic = profilePicUri ?: fbUser?.photoUrl?.toString() ?: existingByPhone?.profilePicUri ?: existingByEmail?.profilePicUri

            val newProfile = UserProfile(
                userId = userId,
                name = finalName,
                email = finalEmail,
                phoneNumber = finalPhone,
                profilePicUri = pic,
                isOtpVerified = true,
                isEmailVerified = true,
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
        viewModelScope.launch {
            try {
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
        viewModelScope.launch {
            val cleanPhone = phone?.filter { it.isDigit() }?.takeLast(10) ?: ""
            val cleanEmail = email?.trim()?.lowercase() ?: ""
            val cleanName = name?.takeIf { it != "Signed Out" && it.isNotBlank() } ?: "FinMoney Member"

            if (cleanPhone.isBlank() && cleanEmail.isBlank()) return@launch

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
        viewModelScope.launch(Dispatchers.IO) {
            DatabaseHealthGuard.verifyAndRepairDatabase(dao, forceReset = true)
        }
    }

    private val authStateListener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        viewModelScope.launch(Dispatchers.IO) {
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
                    isOtpVerified = finalPhone.isNotBlank() || (existing?.isOtpVerified == true),
                    isEmailVerified = user.isEmailVerified || finalEmail.isNotBlank(),
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
        viewModelScope.launch(Dispatchers.IO) {
            DatabaseHealthGuard.verifyAndRepairDatabase(dao, forceReset = false)
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
