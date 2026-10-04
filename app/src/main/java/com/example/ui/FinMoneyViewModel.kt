package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.*
import com.example.data.repository.FinMoneyRepository
import com.example.data.sms.DetectedCategory
import com.example.data.sms.ParsedSmsTransaction
import com.example.data.sms.SmartSmsParser
import com.example.data.sms.SmsTransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

data class CategorySummary(
    val categoryName: String,
    val totalAmount: Double,
    val itemCount: Int,
    val colorHex: String,
    val iconKey: String
)

data class PeerThread(
    val counterpartyName: String,
    val counterpartyContact: String = "",
    val counterpartyProfilePicUri: String = "",
    val userProfilePicUri: String = "",
    val transactions: List<LoanTransaction> = emptyList(),
    val netBalance: Double = 0.0, // Positive means they owe user, negative means user owes
    val totalGiven: Double = 0.0,
    val totalTaken: Double = 0.0,
    val pendingActionCount: Int = 0,
    val lastActivityTimestamp: Long = 0L
)

data class MonthlyFinanceSummary(
    val totalSalary: Double = 0.0,
    val additionalIncome: Double = 0.0,
    val customIncomeTotal: Double = 0.0,
    val incomeSources: List<IncomeSource> = emptyList(),
    val totalIncome: Double = 0.0,
    val totalDebits: Double = 0.0,
    val paidDebits: Double = 0.0,
    val unpaidDebits: Double = 0.0,
    val netFlexibleSpending: Double = 0.0,
    val savingsRatePercent: Double = 0.0,
    val dailySafeSpend: Double = 0.0,
    val categoryBreakdown: List<CategorySummary> = emptyList()
)

data class LentBorrowedSummary(
    val totalLent: Double = 0.0,
    val totalBorrowed: Double = 0.0,
    val netBalance: Double = 0.0, // Positive means others owe you, negative means you owe
    val pendingApprovalCount: Int = 0,
    val settledCount: Int = 0
)

@OptIn(ExperimentalCoroutinesApi::class)
class FinMoneyViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application, viewModelScope)
    private val repository = FinMoneyRepository(database.finMoneyDao(), application)

    private val cal = Calendar.getInstance()
    private val _currentMonth = MutableStateFlow(cal.get(Calendar.MONTH) + 1)
    val currentMonth: StateFlow<Int> = _currentMonth.asStateFlow()

    private val _currentYear = MutableStateFlow(cal.get(Calendar.YEAR))
    val currentYear: StateFlow<Int> = _currentYear.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _currencySymbol = MutableStateFlow("₹")
    val currencySymbol: StateFlow<String> = _currencySymbol.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    // User Profile
    val userProfile: StateFlow<UserProfile?> = repository.getUserProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.getUserProfile().collect { profile ->
                if (profile != null && profile.phoneNumber.isNotBlank()) {
                    repository.startRealtimeSync(profile.phoneNumber)
                }
            }
        }
    }

    // Monthly Salary Flow
    val currentSalaryRecord: StateFlow<SalaryRecord?> = combine(_currentMonth, _currentYear) { month, year ->
        month to year
    }.flatMapLatest { (m, y) ->
        repository.getSalaryRecord(m, y)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Monthly Debits Flow
    val currentDebits: StateFlow<List<DebitItem>> = combine(_currentMonth, _currentYear) { month, year ->
        month to year
    }.flatMapLatest { (m, y) ->
        repository.getDebitsForMonth(m, y)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All Historical Salary Records
    val allSalaryRecords: StateFlow<List<SalaryRecord>> = repository.getAllSalaryRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All Debits
    val allDebits: StateFlow<List<DebitItem>> = repository.getAllDebits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Custom Categories
    val customCategories: StateFlow<List<CustomCategory>> = repository.getAllCustomCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Loans
    val allLoans: StateFlow<List<LoanTransaction>> = repository.getAllLoans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Notifications synthesized from active loan states and stored notifications for current user/phone
    val notifications: StateFlow<List<AppNotification>> = combine(
        repository.getAllNotifications(),
        allLoans,
        userProfile
    ) { dbNotifs, loans, profile ->
        val currentPhone = profile?.phoneNumber?.trim() ?: ""
        val currentName = profile?.name?.trim() ?: "You"

        val dynamicActionNotifs = mutableListOf<AppNotification>()

        for (loan in loans) {
            val cPhone = loan.creatorContact.trim()
            val targetPhone = loan.counterpartyContact.trim()

            val isCreator = (cPhone.isNotBlank() && currentPhone.isNotBlank() && currentPhone.takeLast(10) == cPhone.takeLast(10)) ||
                            (cPhone.isBlank() && loan.createdBy.equals(currentName, ignoreCase = true)) ||
                            (cPhone.isBlank() && loan.createdBy == "You")

            val isCounterparty = (targetPhone.isNotBlank() && currentPhone.isNotBlank() && currentPhone.takeLast(10) == targetPhone.takeLast(10)) ||
                                 (!isCreator && (targetPhone.isBlank() || loan.counterpartyName.equals(currentName, ignoreCase = true)))

            // 1. Pending Agreement Digital Signature for Counterparty
            if (isCounterparty && !loan.counterpartySigned && loan.status != ApprovalStatus.SETTLED.name && loan.status != ApprovalStatus.REJECTED.name) {
                val actionDirection = if (loan.type == LoanType.GIVEN.name) "lent you" else "borrowed from you"
                dynamicActionNotifs.add(
                    AppNotification(
                        id = - (loan.id * 100 + 1),
                        title = "✍️ Action Required: ₹${"%,.0f".format(loan.totalAmount)} Agreement",
                        message = "${loan.createdBy} (${if (loan.creatorContact.isNotBlank()) "+91 ${loan.creatorContact}" else "Creator"}) $actionDirection ₹${"%,.0f".format(loan.totalAmount)}. Please review and digitally sign.",
                        relatedLoanId = loan.id,
                        actionType = "APPROVAL_REQUEST",
                        timestamp = loan.createdAt,
                        isRead = false
                    )
                )
            }

            // 2. Pending Settlement Confirmation
            if (loan.status == ApprovalStatus.PENDING_SETTLEMENT.name && loan.settlementRequestedBy.isNotBlank()) {
                val isRequester = loan.settlementRequestedBy.equals(currentName, ignoreCase = true) || loan.settlementRequestedBy == "You"
                if (!isRequester && (isCounterparty || isCreator)) {
                    dynamicActionNotifs.add(
                        AppNotification(
                            id = - (loan.id * 100 + 2),
                            title = "🤝 Settlement Confirmation Required",
                            message = "${loan.settlementRequestedBy} requested to settle the remaining balance of ₹${"%,.0f".format(loan.totalAmount - loan.settledAmount)}. Tap to confirm.",
                            relatedLoanId = loan.id,
                            actionType = "APPROVAL_REQUEST",
                            timestamp = loan.updatedAt,
                            isRead = false
                        )
                    )
                }
            }

            // 3. Pending Mutual Loan Deletion Request
            if (loan.deletionRequestedBy.isNotBlank()) {
                val isRequester = loan.deletionRequestedBy.equals(currentName, ignoreCase = true) || loan.deletionRequestedBy == "You"
                if (!isRequester && (isCounterparty || isCreator)) {
                    dynamicActionNotifs.add(
                        AppNotification(
                            id = - (loan.id * 100 + 3),
                            title = "⚠️ Agreement Deletion Request",
                            message = "${loan.deletionRequestedBy} requested mutual deletion of ₹${"%,.0f".format(loan.totalAmount)} agreement.",
                            relatedLoanId = loan.id,
                            actionType = "APPROVAL_REQUEST",
                            timestamp = loan.updatedAt,
                            isRead = false
                        )
                    )
                }
            }

            // 4. Payment logs confirmation & deletion requests
            val payments = FinMoneyRepository.parsePaymentHistory(loan.paymentHistoryJson)
            payments.forEach { pay ->
                val isPayRecorder = pay.recordedBy.equals(currentName, ignoreCase = true) || (pay.recordedBy == "You" && currentName == "You")
                if (!pay.isApproved && !isPayRecorder && (isCounterparty || isCreator)) {
                    dynamicActionNotifs.add(
                        AppNotification(
                            id = - Math.abs((loan.id.toString() + "_" + pay.id).hashCode().toLong()),
                            title = "💰 Payment Verification: ₹${"%,.0f".format(pay.amount)}",
                            message = "${pay.recordedBy} logged a payment of ₹${"%,.0f".format(pay.amount)}. Tap to confirm received.",
                            relatedLoanId = loan.id,
                            actionType = "APPROVAL_REQUEST",
                            timestamp = pay.paymentDateTimestamp,
                            isRead = false
                        )
                    )
                }

                if (pay.deletionRequestedBy.isNotBlank()) {
                    val isDelRequester = pay.deletionRequestedBy.equals(currentName, ignoreCase = true) || (pay.deletionRequestedBy == "You" && currentName == "You")
                    if (!isDelRequester && (isCounterparty || isCreator)) {
                        dynamicActionNotifs.add(
                            AppNotification(
                                id = - Math.abs((loan.id.toString() + "_" + pay.id + "_del").hashCode().toLong()),
                                title = "⚠️ Payment Deletion Request: ₹${"%,.0f".format(pay.amount)}",
                                message = "${pay.deletionRequestedBy} requested mutual deletion of ₹${"%,.0f".format(pay.amount)} payment.",
                                relatedLoanId = loan.id,
                                actionType = "APPROVAL_REQUEST",
                                timestamp = loan.updatedAt,
                                isRead = false
                            )
                        )
                    }
                }
            }
        }

        // Combine with dbNotifs (deduplicating by relatedLoanId if action is already active)
        val combined = (dynamicActionNotifs + dbNotifs)
            .distinctBy { if (it.relatedLoanId != null && it.actionType == "APPROVAL_REQUEST") "${it.relatedLoanId}_${it.actionType}" else it.id.toString() }
            .sortedByDescending { it.timestamp }

        combined
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadNotificationCount: StateFlow<Int> = notifications.map { list ->
        list.count { !it.isRead }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // --- Smart SMS Scanner & Auto-Categorization Flow ---
    private val _detectedSmsTransactions = MutableStateFlow<List<ParsedSmsTransaction>>(emptyList())
    val detectedSmsTransactions: StateFlow<List<ParsedSmsTransaction>> = _detectedSmsTransactions.asStateFlow()

    // Financial calculations for current selected month
    val monthlySummary: StateFlow<MonthlyFinanceSummary> = combine(
        currentSalaryRecord,
        currentDebits,
        _currentMonth,
        _currentYear
    ) { salary, debits, month, year ->
        val salAmount = salary?.salaryAmount ?: 0.0
        val extraIncome = salary?.additionalIncome ?: 0.0
        val customSources = parseIncomeSources(salary?.incomeSourcesJson ?: "")
        val customIncomeTotal = customSources.sumOf { it.amount }
        val totalInc = salAmount + extraIncome + customIncomeTotal

        val totalDeb = debits.sumOf { it.amount }
        val paidDeb = debits.filter { it.isPaid }.sumOf { it.amount }
        val unpaidDeb = debits.filter { !it.isPaid }.sumOf { it.amount }
        val netFlex = totalInc - totalDeb

        // Savings & Investments share
        val wealthBuilding = debits.filter {
            it.category == DebitCategory.INVESTMENT.name ||
            it.category == DebitCategory.SAVING.name
        }.sumOf { it.amount }
        val savingsRate = if (totalInc > 0) (wealthBuilding / totalInc) * 100 else 0.0

        // Remaining days in selected month
        val daysInMonth = getDaysInMonth(month, year)
        val today = Calendar.getInstance()
        val remainingDays = if (today.get(Calendar.MONTH) + 1 == month && today.get(Calendar.YEAR) == year) {
            (daysInMonth - today.get(Calendar.DAY_OF_MONTH) + 1).coerceAtLeast(1)
        } else {
            daysInMonth
        }
        val dailySpend = if (netFlex > 0 && remainingDays > 0) netFlex / remainingDays else 0.0

        // Group breakdown
        val breakdown = groupDebitsByCategory(debits)

        MonthlyFinanceSummary(
            totalSalary = salAmount,
            additionalIncome = extraIncome,
            customIncomeTotal = customIncomeTotal,
            incomeSources = customSources,
            totalIncome = totalInc,
            totalDebits = totalDeb,
            paidDebits = paidDeb,
            unpaidDebits = unpaidDeb,
            netFlexibleSpending = netFlex,
            savingsRatePercent = savingsRate,
            dailySafeSpend = dailySpend,
            categoryBreakdown = breakdown
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MonthlyFinanceSummary())

    // Lent & Borrowed summary calculation
    val lentBorrowedSummary: StateFlow<LentBorrowedSummary> = combine(allLoans, userProfile) { loans, profile ->
        val currentPhone = profile?.phoneNumber?.trim() ?: ""
        val currentName = profile?.name?.trim() ?: "You"

        var lent = 0.0
        var borrowed = 0.0
        var pending = 0
        var settled = 0

        for (loan in loans) {
            val creatorPhone = loan.creatorContact.trim()
            val isCreator = (creatorPhone.isNotBlank() && currentPhone.isNotBlank() && currentPhone.takeLast(10) == creatorPhone.takeLast(10)) ||
                            (creatorPhone.isBlank() && loan.createdBy.equals(currentName, ignoreCase = true)) ||
                            (creatorPhone.isBlank() && loan.createdBy == "You")

            // Effective direction for the current user
            val isGivenForUser = if (!isCreator) {
                loan.type == LoanType.TAKEN.name
            } else {
                loan.type == LoanType.GIVEN.name
            }

            if (loan.status == ApprovalStatus.SETTLED.name) {
                settled++
                continue
            }
            if (loan.status == ApprovalStatus.PENDING_APPROVAL.name || 
                loan.status == ApprovalStatus.PENDING_SETTLEMENT.name || 
                loan.deletionRequestedBy.isNotBlank() ||
                !loan.counterpartySigned || !loan.userSigned) {
                pending++
            }

            val payments = FinMoneyRepository.parsePaymentHistory(loan.paymentHistoryJson)
            val remaining = if (loan.interestRatePercent > 0.0) {
                com.example.util.InterestCalculator.calculate(
                    principal = loan.totalAmount,
                    ratePercent = loan.interestRatePercent,
                    isMonthly = loan.isMonthlyInterest,
                    startTimestamp = loan.startDateTimestamp,
                    payments = payments
                ).totalRemainingDue
            } else {
                (loan.totalAmount - loan.settledAmount).coerceAtLeast(0.0)
            }
            if (isGivenForUser) {
                lent += remaining
            } else {
                borrowed += remaining
            }
        }

        LentBorrowedSummary(
            totalLent = lent,
            totalBorrowed = borrowed,
            netBalance = lent - borrowed,
            pendingApprovalCount = pending,
            settledCount = settled
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LentBorrowedSummary())

    // Single thread transaction history per counterparty (PhonePe style)
    val peerThreads: StateFlow<List<PeerThread>> = combine(allLoans, userProfile) { loans, profile ->
        val currentPhone = profile?.phoneNumber?.trim() ?: ""
        val currentName = profile?.name?.trim() ?: "You"

        val groups = loans.groupBy { loan ->
            val creatorPhone = loan.creatorContact.trim()
            val targetPhone = loan.counterpartyContact.trim()

            val isCreator = (creatorPhone.isNotBlank() && currentPhone.isNotBlank() && currentPhone.takeLast(10) == creatorPhone.takeLast(10)) ||
                            (creatorPhone.isBlank() && loan.createdBy.equals(currentName, ignoreCase = true)) ||
                            (creatorPhone.isBlank() && loan.createdBy == "You")

            val otherPhone = if (isCreator) targetPhone else creatorPhone
            val otherName = if (isCreator) loan.counterpartyName.trim() else loan.createdBy.trim()

            if (otherPhone.isNotBlank()) otherPhone.takeLast(10) else otherName.lowercase()
        }

        groups.map { (_, loanList) ->
            val first = loanList.first()
            val creatorPhone = first.creatorContact.trim()
            val isFirstCreator = (creatorPhone.isNotBlank() && currentPhone.isNotBlank() && currentPhone.takeLast(10) == creatorPhone.takeLast(10)) ||
                                 (creatorPhone.isBlank() && first.createdBy.equals(currentName, ignoreCase = true)) ||
                                 (creatorPhone.isBlank() && first.createdBy == "You")

            val displayCounterpartyName = if (isFirstCreator) first.counterpartyName else first.createdBy.ifBlank { "Counterparty" }
            val displayCounterpartyContact = if (isFirstCreator) first.counterpartyContact else first.creatorContact
            val displayCounterpartyPic = if (isFirstCreator) first.counterpartyProfilePicUri else first.userProfilePicUri

            var given = 0.0
            var taken = 0.0
            var pending = 0
            var lastTime = 0L

            for (loan in loanList) {
                if (loan.updatedAt > lastTime) lastTime = loan.updatedAt
                if (loan.status == ApprovalStatus.PENDING_APPROVAL.name || 
                    loan.status == ApprovalStatus.PENDING_SETTLEMENT.name || 
                    loan.deletionRequestedBy.isNotBlank() ||
                    !loan.counterpartySigned || !loan.userSigned) {
                    pending++
                }

                val cPhone = loan.creatorContact.trim()
                val isCreator = (cPhone.isNotBlank() && currentPhone.isNotBlank() && currentPhone.takeLast(10) == cPhone.takeLast(10)) ||
                                (cPhone.isBlank() && loan.createdBy.equals(currentName, ignoreCase = true)) ||
                                (cPhone.isBlank() && loan.createdBy == "You")

                val isGivenForUser = if (!isCreator) {
                    loan.type == LoanType.TAKEN.name
                } else {
                    loan.type == LoanType.GIVEN.name
                }

                if (loan.status != ApprovalStatus.SETTLED.name) {
                    val payments = FinMoneyRepository.parsePaymentHistory(loan.paymentHistoryJson)
                    val rem = if (loan.interestRatePercent > 0.0) {
                        com.example.util.InterestCalculator.calculate(
                            principal = loan.totalAmount,
                            ratePercent = loan.interestRatePercent,
                            isMonthly = loan.isMonthlyInterest,
                            startTimestamp = loan.startDateTimestamp,
                            payments = payments
                        ).totalRemainingDue
                    } else {
                        (loan.totalAmount - loan.settledAmount).coerceAtLeast(0.0)
                    }
                    if (isGivenForUser) {
                        given += rem
                    } else {
                        taken += rem
                    }
                }
            }

            PeerThread(
                counterpartyName = displayCounterpartyName,
                counterpartyContact = displayCounterpartyContact,
                counterpartyProfilePicUri = displayCounterpartyPic,
                userProfilePicUri = profile?.profilePicUri ?: "",
                transactions = loanList.sortedBy { it.startDateTimestamp },
                netBalance = given - taken,
                totalGiven = given,
                totalTaken = taken,
                pendingActionCount = pending,
                lastActivityTimestamp = if (lastTime > 0) lastTime else first.createdAt
            )
        }.sortedByDescending { it.lastActivityTimestamp }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- User Profile & Registration Operations ---

    fun saveUserProfile(
        name: String = "",
        firstName: String = "",
        lastName: String = "",
        email: String = "",
        phoneNumber: String = "",
        profilePicUri: String = "",
        isOtpVerified: Boolean = true,
        isEmailVerified: Boolean = true
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val combinedName = if (name.isNotBlank()) name.trim() else "$firstName $lastName".trim().ifBlank { "You" }
            val profile = UserProfile(
                id = 1,
                name = combinedName,
                firstName = firstName.trim(),
                lastName = lastName.trim(),
                email = email.trim(),
                phoneNumber = phoneNumber.trim(),
                profilePicUri = profilePicUri.trim(),
                isOtpVerified = isOtpVerified,
                isEmailVerified = isEmailVerified,
                createdAt = System.currentTimeMillis()
            )
            repository.saveUserProfile(profile)
            _toastMessage.emit("Profile saved successfully!")
        }
    }

    fun loginWithPhoneOtp(
        phoneNumber: String,
        fallbackName: String = "",
        onComplete: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val clean = FinMoneyRepository.normalizePhone(phoneNumber)
            val cloudProfile = repository.fetchUserProfileFromCloud(phoneNumber)
            if (cloudProfile != null) {
                repository.startRealtimeSync(cloudProfile.phoneNumber)
                _toastMessage.emit("Welcome back, ${cloudProfile.name}! 👋")
            } else {
                val initial = UserProfile(
                    id = 1,
                    name = fallbackName.ifBlank { "User ${clean.takeLast(4)}" },
                    phoneNumber = phoneNumber.trim(),
                    isOtpVerified = true,
                    isEmailVerified = true,
                    createdAt = System.currentTimeMillis()
                )
                repository.saveUserProfile(initial)
                _toastMessage.emit("Verified & Signed in successfully!")
            }
            onComplete()
        }
    }

    fun clearUserProfile() {
        viewModelScope.launch(Dispatchers.IO) {
            val emptyProfile = UserProfile(
                id = 1,
                name = "",
                firstName = "",
                lastName = "",
                email = "",
                phoneNumber = "",
                profilePicUri = "",
                isOtpVerified = false,
                isEmailVerified = false,
                createdAt = 0L
            )
            repository.saveUserProfile(emptyProfile)
        }
    }

    // --- Navigation & State Mutators ---

    fun setTab(index: Int) {
        _selectedTab.value = index
    }

    fun setMonthAndYear(month: Int, year: Int) {
        _currentMonth.value = month
        _currentYear.value = year
    }

    fun nextMonth() {
        if (_currentMonth.value == 12) {
            _currentMonth.value = 1
            _currentYear.value += 1
        } else {
            _currentMonth.value += 1
        }
    }

    fun previousMonth() {
        if (_currentMonth.value == 1) {
            _currentMonth.value = 12
            _currentYear.value -= 1
        } else {
            _currentMonth.value -= 1
        }
    }

    fun setCurrency(symbol: String) {
        _currencySymbol.value = symbol
    }

    // --- Salary Operations ---

    fun saveSalary(
        salaryAmount: Double,
        additionalIncome: Double,
        notes: String,
        incomeSources: List<IncomeSource> = emptyList()
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val record = SalaryRecord(
                month = _currentMonth.value,
                year = _currentYear.value,
                salaryAmount = salaryAmount,
                additionalIncome = additionalIncome,
                currencySymbol = _currencySymbol.value,
                notes = notes,
                incomeSourcesJson = encodeIncomeSources(incomeSources),
                updatedAt = System.currentTimeMillis()
            )
            repository.saveSalary(record)
        }
    }

    // --- Debit Operations ---

    fun addDebit(
        category: String,
        customCategoryName: String = "",
        title: String,
        amount: Double,
        dueDateDay: Int = 1,
        isRecurring: Boolean = true,
        interestRate: Double = 0.0,
        totalTenureMonths: Int = 0,
        currentMonthTenure: Int = 0,
        notes: String = ""
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = DebitItem(
                month = _currentMonth.value,
                year = _currentYear.value,
                category = category,
                customCategoryName = customCategoryName,
                title = title,
                amount = amount,
                dueDateDay = dueDateDay,
                isRecurring = isRecurring,
                isPaid = false,
                interestRate = interestRate,
                totalTenureMonths = totalTenureMonths,
                currentMonthTenure = currentMonthTenure,
                notes = notes
            )
            repository.addDebit(item)
        }
    }

    fun updateDebit(debit: DebitItem) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateDebit(debit)
        }
    }

    fun deleteDebit(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteDebit(id)
        }
    }

    fun toggleDebitPaid(id: Long, currentStatus: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.toggleDebitPaid(id, !currentStatus)
        }
    }

    fun prefillDebitsFromPreviousMonth() {
        viewModelScope.launch(Dispatchers.IO) {
            val m = _currentMonth.value
            val y = _currentYear.value
            val prevMonth = if (m == 1) 12 else m - 1
            val prevYear = if (m == 1) y - 1 else y
            val prevMonthName = getMonthName(prevMonth)

            // Strictly fetch previous month's expenses directly from database
            val prevExpenses = repository.getDebitsForMonthDirect(prevMonth, prevYear)
            if (prevExpenses.isEmpty()) {
                _toastMessage.emit("No expenses in previous month ($prevMonthName $prevYear).")
                return@launch
            }

            val currentMonthDebits = repository.getDebitsForMonthDirect(m, y)
            val itemsToInsert = prevExpenses.filterNot { prevItem ->
                currentMonthDebits.any { it.title.equals(prevItem.title, ignoreCase = true) && it.category == prevItem.category }
            }

            if (itemsToInsert.isEmpty()) {
                _toastMessage.emit("All expenses from $prevMonthName $prevYear are already added.")
            } else {
                repository.copyDebitsFromMonth(prevMonth, prevYear, m, y, itemsToInsert)
                _toastMessage.emit("Prefilled ${itemsToInsert.size} expense(s) from $prevMonthName $prevYear.")
            }
        }
    }

    // --- Custom Category Operations ---

    fun addCustomCategory(name: String, iconName: String = "category", colorHex: String = "#00D09C") {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addCustomCategory(
                CustomCategory(
                    name = name.trim(),
                    iconName = iconName,
                    colorHex = colorHex
                )
            )
        }
    }

    fun deleteCustomCategory(category: CustomCategory) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteCustomCategory(category)
        }
    }

    // --- Loan / Borrow Operations (1-on-1 Peer Records) ---

    fun createLoan(
        type: LoanType,
        amount: Double,
        counterpartyName: String,
        counterpartyContact: String = "",
        interestRatePercent: Double = 0.0,
        isMonthlyInterest: Boolean = true,
        startDateTimestamp: Long = System.currentTimeMillis(),
        dueDateTimestamp: Long? = null,
        note: String = "",
        proofUri: String = "",
        userProfilePicUri: String = "",
        counterpartyProfilePicUri: String = "",
        proofFilesJson: String = ""
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.createLoan(
                type = type,
                amount = amount,
                counterpartyName = counterpartyName,
                counterpartyContact = counterpartyContact,
                interestRatePercent = interestRatePercent,
                isMonthlyInterest = isMonthlyInterest,
                startDateTimestamp = startDateTimestamp,
                dueDateTimestamp = dueDateTimestamp,
                note = note,
                proofUri = proofUri,
                userProfilePicUri = userProfilePicUri,
                counterpartyProfilePicUri = counterpartyProfilePicUri,
                proofFilesJson = proofFilesJson
            )
            _toastMessage.emit("Agreement created & signed by you. Awaiting counterparty.")
        }
    }

    suspend fun isUserRegistered(phone: String): Boolean {
        return repository.isUserRegistered(phone)
    }

    fun checkUserRegistered(phone: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val registered = repository.isUserRegistered(phone)
            withContext(Dispatchers.Main) {
                onResult(registered)
            }
        }
    }

    fun signAgreement(loanId: Long, isUser: Boolean, signerName: String = "You") {
        viewModelScope.launch(Dispatchers.IO) {
            repository.signAgreement(loanId, signerName, isUser)
            _toastMessage.emit("Agreement signed successfully! ✍️")
        }
    }

    fun updateLoan(loan: LoanTransaction) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateLoan(loan)
            _toastMessage.emit("Transaction details updated.")
        }
    }

    fun approveLoanByCounterparty(loanId: Long, approve: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.approveLoanByCounterparty(loanId, approve)
            if (approve) {
                _toastMessage.emit("Transaction approved and active!")
            } else {
                _toastMessage.emit("Transaction marked as declined / disputed.")
            }
        }
    }

    fun requestSettlement(loanId: Long, requestedBy: String = "You", proofUri: String = "", proofFilesJson: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            repository.requestSettlement(loanId, requestedBy, proofUri, proofFilesJson)
            _toastMessage.emit("Settlement requested. Waiting for counterparty confirmation.")
        }
    }

    fun confirmSettlement(loanId: Long, confirmed: Boolean, confirmedBy: String = "You") {
        viewModelScope.launch(Dispatchers.IO) {
            repository.confirmSettlement(loanId, confirmed, confirmedBy)
            if (confirmed) {
                _toastMessage.emit("Full settlement mutually approved! 🎉")
            } else {
                _toastMessage.emit("Settlement request was declined.")
            }
        }
    }

    fun recordPartialPayment(
        loanId: Long,
        amount: Double,
        note: String,
        proofUri: String = "",
        recordedBy: String = "You",
        isDirectApproval: Boolean = false,
        paymentTarget: String = "COMBINED",
        proofFilesJson: String = ""
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.recordPartialPayment(
                loanId = loanId,
                amount = amount,
                note = note,
                proofUri = proofUri,
                recordedBy = recordedBy,
                isDirectApproval = isDirectApproval,
                paymentTarget = paymentTarget,
                proofFilesJson = proofFilesJson
            )
            if (isDirectApproval) {
                _toastMessage.emit("Payment of ₹${"%,.0f".format(amount)} recorded & confirmed.")
            } else {
                _toastMessage.emit("Payment of ₹${"%,.0f".format(amount)} logged. Awaiting confirmation.")
            }
        }
    }

    fun approvePartialPayment(loanId: Long, paymentId: String, approvedBy: String = "You") {
        viewModelScope.launch(Dispatchers.IO) {
            repository.approvePartialPayment(loanId, paymentId, approvedBy)
            _toastMessage.emit("Payment confirmed and deducted from balance! ✓")
        }
    }

    fun rejectPartialPayment(loanId: Long, paymentId: String, declinedBy: String = "You") {
        viewModelScope.launch(Dispatchers.IO) {
            repository.rejectPartialPayment(loanId, paymentId, declinedBy)
            _toastMessage.emit("Payment record declined.")
        }
    }

    fun deletePaymentRecord(loanId: Long, paymentId: String, deletedBy: String = "You") {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deletePaymentRecord(loanId, paymentId, deletedBy)
            _toastMessage.emit("Payment record deleted.")
        }
    }

    fun requestPaymentDeletion(loanId: Long, paymentId: String, requesterName: String = "You") {
        viewModelScope.launch(Dispatchers.IO) {
            repository.requestPaymentDeletion(loanId, paymentId, requesterName)
            _toastMessage.emit("Payment deletion requested.")
        }
    }

    fun confirmPaymentDeletion(loanId: Long, paymentId: String, approve: Boolean, confirmedBy: String = "You") {
        viewModelScope.launch(Dispatchers.IO) {
            repository.confirmPaymentDeletion(loanId, paymentId, approve, confirmedBy)
            if (approve) {
                _toastMessage.emit("Payment record mutually removed.")
            } else {
                _toastMessage.emit("Payment deletion request declined.")
            }
        }
    }

    fun sendReminder(loanId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.sendReminder(loanId)
            _toastMessage.emit("Payment reminder notification sent!")
        }
    }

    fun deleteLoan(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteLoan(id)
            _toastMessage.emit("Record deleted.")
        }
    }

    fun requestLoanDeletion(id: Long, requesterName: String = "You") {
        viewModelScope.launch(Dispatchers.IO) {
            repository.requestLoanDeletion(id, requesterName)
            _toastMessage.emit("Deletion requested. Awaiting counterparty confirmation.")
        }
    }

    fun confirmLoanDeletion(id: Long, approve: Boolean, confirmedBy: String = "You") {
        viewModelScope.launch(Dispatchers.IO) {
            repository.confirmLoanDeletion(id, approve, confirmedBy)
            if (approve) {
                _toastMessage.emit("Record mutually deleted.")
            } else {
                _toastMessage.emit("Deletion request declined. Record retained.")
            }
        }
    }

    // --- Smart SMS Scanner & Auto-Categorization Operations ---

    fun scanDeviceSms(context: android.content.Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val debits = currentDebits.value
            val list = SmartSmsParser.readDeviceSms(context, limit = 50, plannedDebits = debits)
            if (list.isEmpty()) {
                _toastMessage.emit("No new bank or financial SMS found in inbox.")
            } else {
                val current = _detectedSmsTransactions.value.toMutableList()
                val newItems = list.filterNot { parsed -> current.any { it.originalSms == parsed.originalSms } }
                _detectedSmsTransactions.value = newItems + current
                _toastMessage.emit("Found ${newItems.size} new bank transaction SMS.")
            }
        }
    }

    fun loadSampleSmsTransactions() {
        viewModelScope.launch(Dispatchers.Default) {
            val debits = currentDebits.value
            val samples = SmartSmsParser.getSampleBankSmsList().mapNotNull { (sender, text) ->
                SmartSmsParser.parseMessage(text, sender, System.currentTimeMillis(), debits)
            }
            val current = _detectedSmsTransactions.value.toMutableList()
            val newItems = samples.filterNot { parsed -> current.any { it.originalSms == parsed.originalSms } }
            _detectedSmsTransactions.value = newItems + current
            _toastMessage.emit("Loaded ${newItems.size} sample bank SMS for smart categorization!")
        }
    }

    fun parseAndAddCustomSms(smsText: String, sender: String = "Bank SMS") {
        viewModelScope.launch(Dispatchers.Default) {
            val debits = currentDebits.value
            val parsed = SmartSmsParser.parseMessage(smsText, sender, System.currentTimeMillis(), debits)
            if (parsed != null) {
                _detectedSmsTransactions.value = listOf(parsed) + _detectedSmsTransactions.value
                _toastMessage.emit("Parsed ${parsed.detectedCategory.displayName}: ₹${"%,.0f".format(parsed.amount)}")
            } else {
                _toastMessage.emit("Could not identify valid bank transaction in entered text.")
            }
        }
    }

    fun confirmSmsTransaction(
        tx: ParsedSmsTransaction,
        asPaidPlannedDebit: Boolean = false,
        overrideCategory: String? = null,
        overridePeerType: LoanType? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            // Case 1: Match planned commitment -> Mark as paid
            if ((tx.matchedPlannedDebitId != null || asPaidPlannedDebit) && tx.matchedPlannedDebitId != null) {
                repository.toggleDebitPaid(tx.matchedPlannedDebitId, true)
                _toastMessage.emit("Marked '${tx.matchedPlannedDebitTitle ?: "Planned Item"}' as PAID ✓")
            }
            // Case 2: Peer Transfer -> Add to Lent / Borrowed (Phone number / contact level)
            else if (tx.detectedCategory == DetectedCategory.PEER_TRANSFER) {
                val loanType = overridePeerType ?: if (tx.type == SmsTransactionType.CREDIT) LoanType.TAKEN else LoanType.GIVEN
                repository.createLoan(
                    type = loanType,
                    amount = tx.amount,
                    counterpartyName = tx.merchantOrParty.ifBlank { "Contact" },
                    counterpartyContact = tx.phoneNumber,
                    interestRatePercent = 0.0,
                    isMonthlyInterest = true,
                    startDateTimestamp = tx.timestamp,
                    dueDateTimestamp = tx.timestamp + (30L * 86400000L),
                    note = "Auto-detected from bank SMS: ${tx.sender}",
                    proofUri = ""
                )
                _toastMessage.emit("Added ${if (loanType == LoanType.GIVEN) "Given" else "Taken"} ₹${"%,.0f".format(tx.amount)} with ${tx.merchantOrParty} 🤝")
            }
            // Case 3: Salary Credit -> Update monthly income
            else if (tx.detectedCategory == DetectedCategory.SALARY_CREDIT || (tx.type == SmsTransactionType.CREDIT && tx.amount > 5000)) {
                val currentSal = currentSalaryRecord.value
                val newSal = (currentSal?.salaryAmount ?: 0.0).coerceAtLeast(tx.amount)
                val record = SalaryRecord(
                    month = _currentMonth.value,
                    year = _currentYear.value,
                    salaryAmount = newSal,
                    additionalIncome = currentSal?.additionalIncome ?: 0.0,
                    notes = currentSal?.notes ?: "Auto-synced from SMS",
                    incomeSourcesJson = currentSal?.incomeSourcesJson ?: "",
                    updatedAt = System.currentTimeMillis()
                )
                repository.saveSalary(record)
                _toastMessage.emit("Updated salary income to ₹${"%,.0f".format(newSal)} 💰")
            }
            // Case 4: Self Transfer -> Ignored or noted
            else if (tx.detectedCategory == DetectedCategory.SELF_TRANSFER) {
                _toastMessage.emit("Self-transfer of ₹${"%,.0f".format(tx.amount)} confirmed.")
            }
            // Case 5: Regular Outflow / Shopping / Bills -> Add as paid Debit item
            else {
                val catName = overrideCategory ?: when (tx.detectedCategory) {
                    DetectedCategory.SHOPPING -> DebitCategory.SHOPPING.name
                    DetectedCategory.INTERNET_RECHARGE -> DebitCategory.INTERNET.name
                    DetectedCategory.ELECTRICITY -> DebitCategory.ELECTRICITY.name
                    DetectedCategory.RENT -> DebitCategory.RENT.name
                    DetectedCategory.LOAN_EMI -> DebitCategory.EMI.name
                    DetectedCategory.INVESTMENT_SIP -> DebitCategory.INVESTMENT.name
                    else -> DebitCategory.GENERAL.name
                }

                val debit = DebitItem(
                    month = _currentMonth.value,
                    year = _currentYear.value,
                    category = catName,
                    customCategoryName = if (catName == DebitCategory.CUSTOM.name) tx.detectedCategory.displayName else "",
                    title = tx.merchantOrParty.ifBlank { tx.detectedCategory.displayName },
                    amount = tx.amount,
                    dueDateDay = 1,
                    isRecurring = false,
                    isPaid = true, // Already debited via SMS
                    interestRate = 0.0,
                    totalTenureMonths = 0,
                    currentMonthTenure = 0,
                    notes = "SMS Auto-Logged (${tx.sender})"
                )
                repository.addDebit(debit)
                _toastMessage.emit("Logged ₹${"%,.0f".format(tx.amount)} under ${tx.detectedCategory.displayName} ✓")
            }

            // Remove from pending list
            _detectedSmsTransactions.value = _detectedSmsTransactions.value.filterNot { it.id == tx.id }
        }
    }

    fun rejectSmsTransaction(txId: String) {
        viewModelScope.launch(Dispatchers.Default) {
            _detectedSmsTransactions.value = _detectedSmsTransactions.value.filterNot { it.id == txId }
            _toastMessage.emit("Transaction rejected & removed ✕")
        }
    }

    // --- Notifications ---

    fun markNotificationRead(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.markNotificationRead(id)
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.markAllNotificationsRead()
        }
    }

    private fun groupDebitsByCategory(debits: List<DebitItem>): List<CategorySummary> {
        val map = debits.groupBy {
            if (it.category == DebitCategory.CUSTOM.name) {
                if (it.customCategoryName.isNotBlank()) it.customCategoryName else "Custom"
            } else {
                it.category
            }
        }

        return map.map { (catName, items) ->
            val total = items.sumOf { it.amount }
            val (colorHex, iconKey) = when (catName) {
                DebitCategory.RENT.name -> "#0066FF" to "home"
                DebitCategory.EMI.name -> "#F43F5E" to "emi"
                DebitCategory.ELECTRICITY.name -> "#F59E0B" to "flash"
                DebitCategory.INTERNET.name -> "#0284C7" to "internet"
                DebitCategory.SHOPPING.name -> "#DB2777" to "shopping"
                DebitCategory.INVESTMENT.name -> "#00B377" to "investment"
                DebitCategory.SAVING.name -> "#0F766E" to "saving"
                DebitCategory.GENERAL.name -> "#64748B" to "general"
                else -> "#7C3AED" to "custom"
            }
            val displayName = when (catName) {
                DebitCategory.RENT.name -> "Rent / Housing"
                DebitCategory.EMI.name -> "Loan / EMI"
                DebitCategory.ELECTRICITY.name -> "Electricity"
                DebitCategory.INTERNET.name -> "Internet & WiFi"
                DebitCategory.SHOPPING.name -> "Shopping"
                DebitCategory.INVESTMENT.name -> "Investments / SIP"
                DebitCategory.SAVING.name -> "Savings & RD"
                DebitCategory.GENERAL.name -> "General Expenses"
                else -> catName
            }
            CategorySummary(
                categoryName = displayName,
                totalAmount = total,
                itemCount = items.size,
                colorHex = colorHex,
                iconKey = iconKey
            )
        }.sortedByDescending { it.totalAmount }
    }

    companion object {
        fun parseIncomeSources(json: String): List<IncomeSource> {
            if (json.isBlank()) return emptyList()
            return try {
                val list = mutableListOf<IncomeSource>()
                val regex = Regex("""\{"name":"(.*?)","amount":([0-9.]+)(?:,"category":"(.*?)")?\}""")
                val matches = regex.findAll(json)
                for (m in matches) {
                    val name = m.groupValues[1].replace("\\\"", "\"")
                    val amt = m.groupValues[2].toDoubleOrNull() ?: 0.0
                    val cat = if (m.groupValues.size > 3 && m.groupValues[3].isNotBlank()) m.groupValues[3] else IncomeCategory.OTHER.name
                    if (name.isNotBlank() && amt > 0) {
                        list.add(IncomeSource(name, amt, cat))
                    }
                }
                list
            } catch (e: Exception) {
                emptyList()
            }
        }

        fun encodeIncomeSources(sources: List<IncomeSource>): String {
            if (sources.isEmpty()) return ""
            return sources.joinToString(prefix = "[", postfix = "]") {
                """{"name":"${it.name.replace("\"", "\\\"")}","amount":${it.amount},"category":"${it.category}"}"""
            }
        }

        fun getMonthName(month: Int): String {
            return when (month) {
                1 -> "January"
                2 -> "February"
                3 -> "March"
                4 -> "April"
                5 -> "May"
                6 -> "June"
                7 -> "July"
                8 -> "August"
                9 -> "September"
                10 -> "October"
                11 -> "November"
                12 -> "December"
                else -> "Month $month"
            }
        }

        fun getMonthShortName(month: Int): String {
            return when (month) {
                1 -> "Jan"
                2 -> "Feb"
                3 -> "Mar"
                4 -> "Apr"
                5 -> "May"
                6 -> "Jun"
                7 -> "Jul"
                8 -> "Aug"
                9 -> "Sep"
                10 -> "Oct"
                11 -> "Nov"
                12 -> "Dec"
                else -> "M$month"
            }
        }

        fun getDaysInMonth(month: Int, year: Int): Int {
            return when (month) {
                2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
                4, 6, 9, 11 -> 30
                else -> 31
            }
        }
    }
}
