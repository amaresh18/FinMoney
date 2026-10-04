package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.local.FinMoneyDao
import com.example.data.model.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.UUID
import com.example.util.PhoneNotificationHelper
import com.example.util.ProofStorageHelper

class FinMoneyRepository(
    private val dao: FinMoneyDao,
    private val context: Context? = null
) {
    private val TAG = "FinMoneyRepo"
    private val repoScope = CoroutineScope(Dispatchers.IO)

    private val firestoreDbId: String by lazy {
        try {
            context?.getString(R.string.firestore_database_id)
                ?: "ai-studio-android-e20fef11-4f95-4b1d-be45-fa8c63567bfb"
        } catch (e: Exception) {
            "ai-studio-android-e20fef11-4f95-4b1d-be45-fa8c63567bfb"
        }
    }

    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(firestoreDbId)
    }

    private var loansListener: ListenerRegistration? = null
    private var notificationsListener: ListenerRegistration? = null
    private var debitsListener: ListenerRegistration? = null
    private var salariesListener: ListenerRegistration? = null
    private var categoriesListener: ListenerRegistration? = null
    private var currentSyncedPhone: String = ""

    // --- Phone Number Normalization Helper ---
    companion object {
        fun normalizePhone(phone: String?): String {
            if (phone.isNullOrBlank()) return ""
            val digits = phone.filter { it.isDigit() }
            return if (digits.length >= 10) {
                digits.takeLast(10)
            } else {
                digits
            }
        }

        fun parsePaymentHistory(json: String): List<PaymentRecord> {
            if (json.isBlank()) return emptyList()
            return try {
                val array = org.json.JSONArray(json)
                val list = mutableListOf<PaymentRecord>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        PaymentRecord(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            amount = obj.optDouble("amount", 0.0),
                            paymentDateTimestamp = obj.optLong("paymentDateTimestamp", System.currentTimeMillis()),
                            note = obj.optString("note", ""),
                            proofUri = obj.optString("proofUri", ""),
                            recordedBy = obj.optString("recordedBy", "You"),
                            isSettlement = obj.optBoolean("isSettlement", false),
                            isApproved = obj.optBoolean("isApproved", true),
                            approvedBy = obj.optString("approvedBy", ""),
                            deletionRequestedBy = obj.optString("deletionRequestedBy", ""),
                            paymentTarget = obj.optString("paymentTarget", "COMBINED"),
                            proofFilesJson = obj.optString("proofFilesJson", "")
                        )
                    )
                }
                list
            } catch (e: Exception) {
                emptyList()
            }
        }

        fun encodePaymentHistory(payments: List<PaymentRecord>): String {
            if (payments.isEmpty()) return ""
            return try {
                val array = org.json.JSONArray()
                for (p in payments) {
                    val obj = org.json.JSONObject()
                    obj.put("id", p.id)
                    obj.put("amount", p.amount)
                    obj.put("paymentDateTimestamp", p.paymentDateTimestamp)
                    obj.put("note", p.note)
                    obj.put("proofUri", p.proofUri)
                    obj.put("recordedBy", p.recordedBy)
                    obj.put("isSettlement", p.isSettlement)
                    obj.put("isApproved", p.isApproved)
                    obj.put("approvedBy", p.approvedBy)
                    obj.put("deletionRequestedBy", p.deletionRequestedBy)
                    obj.put("paymentTarget", p.paymentTarget)
                    obj.put("proofFilesJson", p.proofFilesJson)
                    array.put(obj)
                }
                array.toString()
            } catch (e: Exception) {
                ""
            }
        }
    }

    // --- Realtime Firestore Sync Engine ---

    fun startRealtimeSync(userPhone: String) {
        val cleanPhone = normalizePhone(userPhone)
        if (cleanPhone.isBlank() || cleanPhone == currentSyncedPhone) return
        currentSyncedPhone = cleanPhone

        loansListener?.remove()
        notificationsListener?.remove()

        Log.d(TAG, "Starting real-time Firestore sync for phone: $cleanPhone")

        // 1. Listen for loans where this user is a participant
        loansListener = firestore.collection("loans")
            .whereArrayContains("participants", cleanPhone)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Loans sync error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                repoScope.launch {
                    for (doc in snapshot.documents) {
                        try {
                            val cloudId = doc.getString("cloudId") ?: doc.id
                            val type = doc.getString("type") ?: LoanType.GIVEN.name
                            val totalAmount = doc.getDouble("totalAmount") ?: 0.0
                            val counterpartyName = doc.getString("counterpartyName") ?: ""
                            val counterpartyContact = doc.getString("counterpartyContact") ?: ""
                            val creatorContact = doc.getString("creatorContact") ?: ""
                            val interestRatePercent = doc.getDouble("interestRatePercent") ?: 0.0
                            val isMonthlyInterest = doc.getBoolean("isMonthlyInterest") ?: true
                            val startDateTimestamp = doc.getLong("startDateTimestamp") ?: System.currentTimeMillis()
                            val dueDateTimestamp = doc.getLong("dueDateTimestamp")
                            val note = doc.getString("note") ?: ""
                            val proofUri = doc.getString("proofUri") ?: ""
                            val userProfilePicUri = doc.getString("userProfilePicUri") ?: ""
                            val counterpartyProfilePicUri = doc.getString("counterpartyProfilePicUri") ?: ""
                            val paymentHistoryJson = doc.getString("paymentHistoryJson") ?: ""
                            val status = doc.getString("status") ?: ApprovalStatus.PENDING_APPROVAL.name
                            val createdBy = doc.getString("createdBy") ?: "You"
                            val userApproved = doc.getBoolean("userApproved") ?: true
                            val counterpartyApproved = doc.getBoolean("counterpartyApproved") ?: false
                            val userSigned = doc.getBoolean("userSigned") ?: true
                            val counterpartySigned = doc.getBoolean("counterpartySigned") ?: false
                            val userSignature = doc.getString("userSignature") ?: ""
                            val counterpartySignature = doc.getString("counterpartySignature") ?: ""
                            val pendingEditJson = doc.getString("pendingEditJson") ?: ""
                            val editRequestedBy = doc.getString("editRequestedBy") ?: ""
                            val deletionRequestedBy = doc.getString("deletionRequestedBy") ?: ""
                            val settlementRequestedBy = doc.getString("settlementRequestedBy") ?: ""
                            val settlementProofUri = doc.getString("settlementProofUri") ?: ""
                            val proofFilesJson = doc.getString("proofFilesJson") ?: ""
                            val settledAmount = doc.getDouble("settledAmount") ?: 0.0
                            val remindedAtTimestamp = doc.getLong("remindedAtTimestamp") ?: 0L
                            val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                            val updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()

                            val existing = dao.getLoanByCloudId(cloudId)
                            val loanToSave = LoanTransaction(
                                id = existing?.id ?: 0,
                                cloudId = cloudId,
                                type = type,
                                totalAmount = totalAmount,
                                counterpartyName = counterpartyName,
                                counterpartyContact = counterpartyContact,
                                creatorContact = creatorContact,
                                interestRatePercent = interestRatePercent,
                                isMonthlyInterest = isMonthlyInterest,
                                startDateTimestamp = startDateTimestamp,
                                dueDateTimestamp = dueDateTimestamp,
                                note = note,
                                proofUri = proofUri,
                                userProfilePicUri = userProfilePicUri,
                                counterpartyProfilePicUri = counterpartyProfilePicUri,
                                paymentHistoryJson = paymentHistoryJson,
                                status = status,
                                createdBy = createdBy,
                                userApproved = userApproved,
                                counterpartyApproved = counterpartyApproved,
                                userSigned = userSigned,
                                counterpartySigned = counterpartySigned,
                                userSignature = userSignature,
                                counterpartySignature = counterpartySignature,
                                pendingEditJson = pendingEditJson,
                                editRequestedBy = editRequestedBy,
                                deletionRequestedBy = deletionRequestedBy,
                                settlementRequestedBy = settlementRequestedBy,
                                settlementProofUri = settlementProofUri,
                                proofFilesJson = proofFilesJson,
                                settledAmount = settledAmount,
                                remindedAtTimestamp = remindedAtTimestamp,
                                createdAt = createdAt,
                                updatedAt = updatedAt
                            )

                            if (existing != null) {
                                dao.updateLoan(loanToSave)
                            } else {
                                dao.insertLoan(loanToSave)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse incoming loan doc ${doc.id}", e)
                        }
                    }
                }
            }

        // 2. Listen for real-time notifications directed to this user's phone number
        notificationsListener = firestore.collection("notifications")
            .whereEqualTo("targetPhone", cleanPhone)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Notifications sync error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                repoScope.launch {
                    for (doc in snapshot.documents) {
                        try {
                            val notifCloudId = doc.getString("cloudId") ?: doc.id
                            val title = doc.getString("title") ?: "Alert"
                            val message = doc.getString("message") ?: ""
                            val relatedLoanId = doc.getLong("relatedLoanId")
                            val relatedLoanCloudId = doc.getString("relatedLoanCloudId") ?: ""
                            val actionType = doc.getString("actionType") ?: "APPROVAL_REQUEST"
                            val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                            val isRead = doc.getBoolean("isRead") ?: false
                            val targetPhone = doc.getString("targetPhone") ?: cleanPhone
                            val senderPhone = doc.getString("senderPhone") ?: ""
                            val senderName = doc.getString("senderName") ?: ""

                            val existing = dao.getNotificationByCloudId(notifCloudId)
                            if (existing == null) {
                                val localLoan = if (relatedLoanCloudId.isNotBlank()) {
                                    dao.getLoanByCloudId(relatedLoanCloudId)
                                } else null

                                val resolvedLoanId = localLoan?.id ?: relatedLoanId

                                dao.insertNotification(
                                    AppNotification(
                                        id = 0,
                                        cloudId = notifCloudId,
                                        targetPhone = targetPhone,
                                        senderPhone = senderPhone,
                                        senderName = senderName,
                                        title = title,
                                        message = message,
                                        relatedLoanId = resolvedLoanId,
                                        actionType = actionType,
                                        timestamp = timestamp,
                                        isRead = isRead
                                    )
                                )
                                context?.let { ctx ->
                                    PhoneNotificationHelper.showNotification(
                                        context = ctx,
                                        title = title,
                                        message = message,
                                        loanId = resolvedLoanId,
                                        actionType = actionType
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse incoming notification doc ${doc.id}", e)
                        }
                    }
                }
            }

        // 3. Listen for real-time expenses / monthly debits
        debitsListener?.remove()
        debitsListener = firestore.collection("debit_items")
            .whereEqualTo("userPhone", cleanPhone)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                repoScope.launch {
                    for (doc in snapshot.documents) {
                        try {
                            val cloudId = doc.getString("cloudId") ?: doc.id
                            val month = doc.getLong("month")?.toInt() ?: 1
                            val year = doc.getLong("year")?.toInt() ?: 2026
                            val category = doc.getString("category") ?: DebitCategory.GENERAL.name
                            val customCategoryName = doc.getString("customCategoryName") ?: ""
                            val title = doc.getString("title") ?: ""
                            val amount = doc.getDouble("amount") ?: 0.0
                            val dueDateDay = doc.getLong("dueDateDay")?.toInt() ?: 1
                            val isRecurring = doc.getBoolean("isRecurring") ?: true
                            val isPaid = doc.getBoolean("isPaid") ?: false
                            val interestRate = doc.getDouble("interestRate") ?: 0.0
                            val totalTenureMonths = doc.getLong("totalTenureMonths")?.toInt() ?: 0
                            val currentMonthTenure = doc.getLong("currentMonthTenure")?.toInt() ?: 0
                            val notes = doc.getString("notes") ?: ""
                            val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()

                            val existing = dao.getDebitByCloudId(cloudId)
                            val debitToSave = DebitItem(
                                id = existing?.id ?: 0,
                                cloudId = cloudId,
                                month = month,
                                year = year,
                                category = category,
                                customCategoryName = customCategoryName,
                                title = title,
                                amount = amount,
                                dueDateDay = dueDateDay,
                                isRecurring = isRecurring,
                                isPaid = isPaid,
                                interestRate = interestRate,
                                totalTenureMonths = totalTenureMonths,
                                currentMonthTenure = currentMonthTenure,
                                notes = notes,
                                createdAt = createdAt
                            )
                            if (existing != null) {
                                dao.updateDebit(debitToSave)
                            } else {
                                dao.insertDebit(debitToSave)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse incoming debit doc ${doc.id}", e)
                        }
                    }
                }
            }

        // 4. Listen for real-time monthly salary & income records
        salariesListener?.remove()
        salariesListener = firestore.collection("salary_records")
            .whereEqualTo("userPhone", cleanPhone)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                repoScope.launch {
                    for (doc in snapshot.documents) {
                        try {
                            val month = doc.getLong("month")?.toInt() ?: 1
                            val year = doc.getLong("year")?.toInt() ?: 2026
                            val salaryAmount = doc.getDouble("salaryAmount") ?: 0.0
                            val additionalIncome = doc.getDouble("additionalIncome") ?: 0.0
                            val currencySymbol = doc.getString("currencySymbol") ?: "₹"
                            val notes = doc.getString("notes") ?: ""
                            val incomeSourcesJson = doc.getString("incomeSourcesJson") ?: ""
                            val updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()

                            val existing = dao.getSalaryRecordDirect(month, year)
                            val recordToSave = SalaryRecord(
                                id = existing?.id ?: 0,
                                month = month,
                                year = year,
                                salaryAmount = salaryAmount,
                                additionalIncome = additionalIncome,
                                currencySymbol = currencySymbol,
                                notes = notes,
                                incomeSourcesJson = incomeSourcesJson,
                                updatedAt = updatedAt
                            )
                            dao.insertOrUpdateSalary(recordToSave)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse incoming salary doc ${doc.id}", e)
                        }
                    }
                }
            }

        // 5. Listen for real-time custom categories
        categoriesListener?.remove()
        categoriesListener = firestore.collection("custom_categories")
            .whereEqualTo("userPhone", cleanPhone)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                repoScope.launch {
                    for (doc in snapshot.documents) {
                        try {
                            val name = doc.getString("name") ?: ""
                            val iconName = doc.getString("iconName") ?: "category"
                            val colorHex = doc.getString("colorHex") ?: "#00D09C"
                            val isDefault = doc.getBoolean("isDefault") ?: false

                            if (name.isNotBlank()) {
                                dao.insertCustomCategory(
                                    CustomCategory(
                                        id = 0,
                                        name = name,
                                        iconName = iconName,
                                        colorHex = colorHex,
                                        isDefault = isDefault
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse incoming category doc ${doc.id}", e)
                        }
                    }
                }
            }
    }

    // --- User Profile Operations ---

    fun getUserProfile(): Flow<UserProfile?> = dao.getUserProfile()
    suspend fun getUserProfileDirect(): UserProfile? = dao.getUserProfileDirect()

    suspend fun fetchUserProfileFromCloud(phone: String): UserProfile? {
        val cleanPhone = normalizePhone(phone)
        if (cleanPhone.isBlank()) return null
        return try {
            val doc = firestore.collection("users").document(cleanPhone).get().await()
            if (doc.exists()) {
                val displayName = doc.getString("displayName") ?: doc.getString("name") ?: "User"
                val firstName = doc.getString("firstName") ?: ""
                val lastName = doc.getString("lastName") ?: ""
                val email = doc.getString("email") ?: ""
                val photoUrl = doc.getString("photoUrl") ?: doc.getString("profilePicUri") ?: ""
                val isOtpVerified = doc.getBoolean("isOtpVerified") ?: true

                val profile = UserProfile(
                    id = 1,
                    name = displayName,
                    firstName = firstName,
                    lastName = lastName,
                    email = email,
                    phoneNumber = phone,
                    profilePicUri = photoUrl,
                    isOtpVerified = isOtpVerified,
                    isEmailVerified = email.isNotBlank()
                )
                dao.insertOrUpdateUserProfile(profile)
                startRealtimeSync(cleanPhone)
                profile
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fetch profile warning: ${e.message}")
            null
        }
    }

    suspend fun isUserRegistered(phone: String): Boolean {
        val clean = normalizePhone(phone)
        if (clean.length < 10) return false

        try {
            val localSigned = dao.getAllLoans().firstOrNull()?.any {
                (normalizePhone(it.counterpartyContact) == clean || normalizePhone(it.creatorContact) == clean) && it.counterpartySigned
            } == true
            if (localSigned) return true
        } catch (_: Exception) {}

        return try {
            val doc1 = firestore.collection("users").document(clean).get().await()
            if (doc1.exists()) return true

            val doc2 = firestore.collection("users").document("+91$clean").get().await()
            if (doc2.exists()) return true

            val last10 = clean.takeLast(10)
            val query = firestore.collection("users")
                .whereEqualTo("normalizedPhone", last10)
                .limit(1)
                .get()
                .await()
            !query.isEmpty
        } catch (e: Exception) {
            false
        }
    }

    suspend fun saveUserProfile(profile: UserProfile): Long {
        val rowId = dao.insertOrUpdateUserProfile(profile)
        val cleanPhone = normalizePhone(profile.phoneNumber)
        if (cleanPhone.isNotBlank()) {
            startRealtimeSync(cleanPhone)
            try {
                val userMap = hashMapOf(
                    "userId" to cleanPhone,
                    "phoneNumber" to profile.phoneNumber,
                    "normalizedPhone" to cleanPhone,
                    "displayName" to profile.name,
                    "firstName" to profile.firstName,
                    "lastName" to profile.lastName,
                    "email" to profile.email,
                    "photoUrl" to profile.profilePicUri,
                    "isOtpVerified" to profile.isOtpVerified,
                    "updatedAt" to System.currentTimeMillis()
                )
                firestore.collection("users").document(cleanPhone)
                    .set(userMap, SetOptions.merge())
            } catch (e: Exception) {
                Log.w(TAG, "Firestore user profile sync warning: ${e.message}")
            }
        }
        return rowId
    }

    // --- Salary & Debits (Cloud Sync & Backup) ---

    fun getSalaryRecord(month: Int, year: Int): Flow<SalaryRecord?> = dao.getSalaryRecord(month, year)
    fun getAllSalaryRecords(): Flow<List<SalaryRecord>> = dao.getAllSalaryRecords()

    suspend fun saveSalary(record: SalaryRecord): Long {
        val existing = dao.getSalaryRecordDirect(record.month, record.year)
        val finalRecord = if (existing != null) {
            record.copy(id = existing.id)
        } else {
            record
        }
        val rowId = dao.insertOrUpdateSalary(finalRecord)

        val userProf = dao.getUserProfileDirect()
        val cleanPhone = normalizePhone(userProf?.phoneNumber)
        if (cleanPhone.isNotBlank()) {
            try {
                val docId = "salary_${cleanPhone}_${record.month}_${record.year}"
                val salaryMap = hashMapOf(
                    "userPhone" to cleanPhone,
                    "month" to record.month,
                    "year" to record.year,
                    "salaryAmount" to record.salaryAmount,
                    "additionalIncome" to record.additionalIncome,
                    "currencySymbol" to record.currencySymbol,
                    "notes" to record.notes,
                    "incomeSourcesJson" to record.incomeSourcesJson,
                    "updatedAt" to record.updatedAt
                )
                firestore.collection("salary_records").document(docId).set(salaryMap, SetOptions.merge())
            } catch (e: Exception) {
                Log.w(TAG, "Cloud salary sync warning: ${e.message}")
            }
        }
        return rowId
    }

    fun getDebitsForMonth(month: Int, year: Int): Flow<List<DebitItem>> = dao.getDebitsForMonth(month, year)
    suspend fun getDebitsForMonthDirect(month: Int, year: Int): List<DebitItem> = dao.getDebitsForMonthDirect(month, year)
    fun getAllDebits(): Flow<List<DebitItem>> = dao.getAllDebits()

    suspend fun addDebit(debit: DebitItem): Long {
        val cloudId = if (debit.cloudId.isNotBlank()) debit.cloudId else "debit_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val finalDebit = debit.copy(cloudId = cloudId)
        val rowId = dao.insertDebit(finalDebit)

        val userProf = dao.getUserProfileDirect()
        val cleanPhone = normalizePhone(userProf?.phoneNumber)
        if (cleanPhone.isNotBlank()) {
            try {
                val debitMap = hashMapOf(
                    "cloudId" to cloudId,
                    "userPhone" to cleanPhone,
                    "month" to debit.month,
                    "year" to debit.year,
                    "category" to debit.category,
                    "customCategoryName" to debit.customCategoryName,
                    "title" to debit.title,
                    "amount" to debit.amount,
                    "dueDateDay" to debit.dueDateDay,
                    "isRecurring" to debit.isRecurring,
                    "isPaid" to debit.isPaid,
                    "interestRate" to debit.interestRate,
                    "totalTenureMonths" to debit.totalTenureMonths,
                    "currentMonthTenure" to debit.currentMonthTenure,
                    "notes" to debit.notes,
                    "createdAt" to debit.createdAt
                )
                firestore.collection("debit_items").document(cloudId).set(debitMap, SetOptions.merge())
            } catch (e: Exception) {
                Log.w(TAG, "Cloud debit sync warning: ${e.message}")
            }
        }
        return rowId
    }

    suspend fun updateDebit(debit: DebitItem) {
        val existing = dao.getDebitById(debit.id)
        val cloudId = if (debit.cloudId.isNotBlank()) debit.cloudId else (existing?.cloudId ?: "debit_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}")
        val finalDebit = debit.copy(cloudId = cloudId)
        dao.updateDebit(finalDebit)

        val userProf = dao.getUserProfileDirect()
        val cleanPhone = normalizePhone(userProf?.phoneNumber)
        if (cleanPhone.isNotBlank()) {
            try {
                val debitMap = hashMapOf(
                    "cloudId" to cloudId,
                    "userPhone" to cleanPhone,
                    "month" to debit.month,
                    "year" to debit.year,
                    "category" to debit.category,
                    "customCategoryName" to debit.customCategoryName,
                    "title" to debit.title,
                    "amount" to debit.amount,
                    "dueDateDay" to debit.dueDateDay,
                    "isRecurring" to debit.isRecurring,
                    "isPaid" to debit.isPaid,
                    "interestRate" to debit.interestRate,
                    "totalTenureMonths" to debit.totalTenureMonths,
                    "currentMonthTenure" to debit.currentMonthTenure,
                    "notes" to debit.notes,
                    "createdAt" to debit.createdAt
                )
                firestore.collection("debit_items").document(cloudId).set(debitMap, SetOptions.merge())
            } catch (e: Exception) {
                Log.w(TAG, "Cloud debit update warning: ${e.message}")
            }
        }
    }

    suspend fun deleteDebit(id: Long) {
        val existing = dao.getDebitById(id)
        dao.deleteDebitById(id)
        if (existing != null && existing.cloudId.isNotBlank()) {
            try {
                firestore.collection("debit_items").document(existing.cloudId).delete()
            } catch (e: Exception) {
                Log.w(TAG, "Cloud debit delete warning: ${e.message}")
            }
        }
    }

    suspend fun toggleDebitPaid(id: Long, isPaid: Boolean) {
        dao.setDebitPaidStatus(id, isPaid)
        val existing = dao.getDebitById(id)
        if (existing != null && existing.cloudId.isNotBlank()) {
            try {
                firestore.collection("debit_items").document(existing.cloudId)
                    .update("isPaid", isPaid)
            } catch (e: Exception) {
                Log.w(TAG, "Cloud toggle debit paid warning: ${e.message}")
            }
        }
    }

    suspend fun copyDebitsFromMonth(fromMonth: Int, fromYear: Int, toMonth: Int, toYear: Int, existingDebits: List<DebitItem>) {
        val userProf = dao.getUserProfileDirect()
        val cleanPhone = normalizePhone(userProf?.phoneNumber)

        val newItems = existingDebits.map { item ->
            val nextTenure = if (item.totalTenureMonths > 0) item.currentMonthTenure + 1 else 0
            val cloudId = "debit_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
            val newItem = item.copy(
                id = 0,
                cloudId = cloudId,
                month = toMonth,
                year = toYear,
                isPaid = false,
                currentMonthTenure = nextTenure,
                createdAt = System.currentTimeMillis()
            )
            if (cleanPhone.isNotBlank()) {
                try {
                    val debitMap = hashMapOf(
                        "cloudId" to cloudId,
                        "userPhone" to cleanPhone,
                        "month" to toMonth,
                        "year" to toYear,
                        "category" to newItem.category,
                        "customCategoryName" to newItem.customCategoryName,
                        "title" to newItem.title,
                        "amount" to newItem.amount,
                        "dueDateDay" to newItem.dueDateDay,
                        "isRecurring" to newItem.isRecurring,
                        "isPaid" to false,
                        "interestRate" to newItem.interestRate,
                        "totalTenureMonths" to newItem.totalTenureMonths,
                        "currentMonthTenure" to nextTenure,
                        "notes" to newItem.notes,
                        "createdAt" to newItem.createdAt
                    )
                    firestore.collection("debit_items").document(cloudId).set(debitMap, SetOptions.merge())
                } catch (e: Exception) {
                    Log.w(TAG, "Copy debit to cloud error: ${e.message}")
                }
            }
            newItem
        }
        dao.insertDebits(newItems)
    }

    // Custom Categories
    fun getAllCustomCategories(): Flow<List<CustomCategory>> = dao.getAllCustomCategories()

    suspend fun addCustomCategory(category: CustomCategory): Long {
        val rowId = dao.insertCustomCategory(category)
        val userProf = dao.getUserProfileDirect()
        val cleanPhone = normalizePhone(userProf?.phoneNumber)
        if (cleanPhone.isNotBlank()) {
            try {
                val docId = "cat_${cleanPhone}_${category.name.lowercase().replace(" ", "_")}"
                val catMap = hashMapOf(
                    "userPhone" to cleanPhone,
                    "name" to category.name,
                    "iconName" to category.iconName,
                    "colorHex" to category.colorHex,
                    "isDefault" to category.isDefault
                )
                firestore.collection("custom_categories").document(docId).set(catMap, SetOptions.merge())
            } catch (e: Exception) {
                Log.w(TAG, "Cloud category sync error: ${e.message}")
            }
        }
        return rowId
    }

    suspend fun deleteCustomCategory(category: CustomCategory) {
        dao.deleteCustomCategory(category)
        val userProf = dao.getUserProfileDirect()
        val cleanPhone = normalizePhone(userProf?.phoneNumber)
        if (cleanPhone.isNotBlank()) {
            try {
                val docId = "cat_${cleanPhone}_${category.name.lowercase().replace(" ", "_")}"
                firestore.collection("custom_categories").document(docId).delete()
            } catch (e: Exception) {
                Log.w(TAG, "Cloud category delete error: ${e.message}")
            }
        }
    }

    // --- Loans (Peer-to-Peer 1-on-1 Money Given / Borrowed with Cloud Sync) ---

    fun getAllLoans(): Flow<List<LoanTransaction>> = dao.getAllLoans()
    fun getPendingApprovalLoans(): Flow<List<LoanTransaction>> = dao.getPendingApprovalLoans()

    suspend fun createLoan(
        type: LoanType,
        amount: Double,
        counterpartyName: String,
        counterpartyContact: String,
        interestRatePercent: Double,
        isMonthlyInterest: Boolean,
        startDateTimestamp: Long,
        dueDateTimestamp: Long?,
        note: String,
        proofUri: String = "",
        userProfilePicUri: String = "",
        counterpartyProfilePicUri: String = "",
        proofFilesJson: String = ""
    ): Long {
        val userProf = dao.getUserProfileDirect()
        val userPic = if (userProfilePicUri.isNotBlank()) userProfilePicUri else (userProf?.profilePicUri ?: "")
        val userName = userProf?.name ?: "You"
        val userPhone = userProf?.phoneNumber?.trim() ?: ""

        val cleanCreatorPhone = normalizePhone(userPhone)
        val cleanCounterpartyPhone = normalizePhone(counterpartyContact)
        val cloudId = "loan_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"

        val participants = listOf(cleanCreatorPhone, cleanCounterpartyPhone).filter { it.isNotBlank() }

        val filesJson = if (proofFilesJson.isNotBlank()) {
            proofFilesJson
        } else if (proofUri.trim().startsWith("[")) {
            proofUri
        } else ""

        val effectiveProofUri = if (proofUri.trim().startsWith("[")) {
            val parsed = ProofStorageHelper.parseProofFiles(proofUri)
            parsed.firstOrNull()?.localPath ?: ""
        } else {
            proofUri
        }

        val loan = LoanTransaction(
            cloudId = cloudId,
            type = type.name,
            totalAmount = amount,
            counterpartyName = counterpartyName,
            counterpartyContact = counterpartyContact,
            creatorContact = userPhone,
            interestRatePercent = interestRatePercent,
            isMonthlyInterest = isMonthlyInterest,
            startDateTimestamp = startDateTimestamp,
            dueDateTimestamp = dueDateTimestamp,
            note = note,
            proofUri = effectiveProofUri,
            userProfilePicUri = userPic,
            counterpartyProfilePicUri = counterpartyProfilePicUri,
            proofFilesJson = filesJson,
            status = ApprovalStatus.PENDING_APPROVAL.name,
            createdBy = userName,
            userApproved = true,
            counterpartyApproved = false,
            userSigned = true,
            counterpartySigned = false,
            userSignature = "Digitally Signed by $userName",
            counterpartySignature = ""
        )
        val localLoanId = dao.insertLoan(loan)

        // 1. Insert local notification for the creator & post phone notification
        notifyBothInAppAndPhone(
            title = "Agreement Created: ₹${"%,.0f".format(amount)} with $counterpartyName",
            message = "Agreement created with $counterpartyName. Awaiting mutual signature.",
            loanId = localLoanId,
            actionType = "APPROVAL_REQUEST"
        )

        // 2. Sync to Firestore for real-time delivery to Counterparty
        try {
            val loanMap = hashMapOf(
                "cloudId" to cloudId,
                "type" to type.name,
                "totalAmount" to amount,
                "counterpartyName" to counterpartyName,
                "counterpartyContact" to counterpartyContact,
                "creatorContact" to userPhone,
                "normalizedCreatorPhone" to cleanCreatorPhone,
                "normalizedCounterpartyPhone" to cleanCounterpartyPhone,
                "interestRatePercent" to interestRatePercent,
                "isMonthlyInterest" to isMonthlyInterest,
                "startDateTimestamp" to startDateTimestamp,
                "dueDateTimestamp" to (dueDateTimestamp ?: 0L),
                "note" to note,
                "proofUri" to proofUri,
                "userProfilePicUri" to userPic,
                "counterpartyProfilePicUri" to counterpartyProfilePicUri,
                "paymentHistoryJson" to "",
                "status" to ApprovalStatus.PENDING_APPROVAL.name,
                "createdBy" to userName,
                "userApproved" to true,
                "counterpartyApproved" to false,
                "userSigned" to true,
                "counterpartySigned" to false,
                "userSignature" to "Digitally Signed by $userName",
                "counterpartySignature" to "",
                "settledAmount" to 0.0,
                "proofFilesJson" to proofFilesJson,
                "createdAt" to System.currentTimeMillis(),
                "updatedAt" to System.currentTimeMillis(),
                "participants" to participants
            )
            firestore.collection("loans").document(cloudId).set(loanMap)

            // 3. Push Cloud Notification targeting counterparty's phone number
            if (cleanCounterpartyPhone.isNotBlank()) {
                val notifCloudId = "notif_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
                val counterpartyNotifTitle = "Action Required: ₹${"%,.0f".format(amount)} ${if (type == LoanType.GIVEN) "Borrowed from" else "Lent to"} $userName"
                val counterpartyNotifMsg = "$userName (+91 $userPhone) recorded a ₹${"%,.0f".format(amount)} loan agreement with you. Tap to review and digitally sign."

                val notifMap = hashMapOf(
                    "cloudId" to notifCloudId,
                    "targetPhone" to cleanCounterpartyPhone,
                    "senderPhone" to cleanCreatorPhone,
                    "senderName" to userName,
                    "title" to counterpartyNotifTitle,
                    "message" to counterpartyNotifMsg,
                    "relatedLoanCloudId" to cloudId,
                    "actionType" to "APPROVAL_REQUEST",
                    "timestamp" to System.currentTimeMillis(),
                    "isRead" to false
                )
                firestore.collection("notifications").document(notifCloudId).set(notifMap)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firestore sync warning on createLoan: ${e.message}")
        }

        return localLoanId
    }

    private suspend fun syncLoanToFirestore(loan: LoanTransaction) {
        val cloudId = loan.cloudId.ifBlank {
            "loan_${loan.id}_${System.currentTimeMillis()}"
        }
        val cleanCreator = normalizePhone(loan.creatorContact)
        val cleanCounterparty = normalizePhone(loan.counterpartyContact)
        val participants = listOf(cleanCreator, cleanCounterparty).filter { it.isNotBlank() }

        try {
            val loanMap = hashMapOf(
                "cloudId" to cloudId,
                "type" to loan.type,
                "totalAmount" to loan.totalAmount,
                "counterpartyName" to loan.counterpartyName,
                "counterpartyContact" to loan.counterpartyContact,
                "creatorContact" to loan.creatorContact,
                "normalizedCreatorPhone" to cleanCreator,
                "normalizedCounterpartyPhone" to cleanCounterparty,
                "interestRatePercent" to loan.interestRatePercent,
                "isMonthlyInterest" to loan.isMonthlyInterest,
                "startDateTimestamp" to loan.startDateTimestamp,
                "dueDateTimestamp" to (loan.dueDateTimestamp ?: 0L),
                "note" to loan.note,
                "proofUri" to loan.proofUri,
                "userProfilePicUri" to loan.userProfilePicUri,
                "counterpartyProfilePicUri" to loan.counterpartyProfilePicUri,
                "paymentHistoryJson" to loan.paymentHistoryJson,
                "status" to loan.status,
                "createdBy" to loan.createdBy,
                "userApproved" to loan.userApproved,
                "counterpartyApproved" to loan.counterpartyApproved,
                "userSigned" to loan.userSigned,
                "counterpartySigned" to loan.counterpartySigned,
                "userSignature" to loan.userSignature,
                "counterpartySignature" to loan.counterpartySignature,
                "pendingEditJson" to loan.pendingEditJson,
                "editRequestedBy" to loan.editRequestedBy,
                "deletionRequestedBy" to loan.deletionRequestedBy,
                "settlementRequestedBy" to loan.settlementRequestedBy,
                "settlementProofUri" to loan.settlementProofUri,
                "proofFilesJson" to loan.proofFilesJson,
                "settledAmount" to loan.settledAmount,
                "remindedAtTimestamp" to loan.remindedAtTimestamp,
                "createdAt" to loan.createdAt,
                "updatedAt" to loan.updatedAt,
                "participants" to participants
            )
            firestore.collection("loans").document(cloudId).set(loanMap, SetOptions.merge())
        } catch (e: Exception) {
            Log.w(TAG, "Firestore update loan warning: ${e.message}")
        }
    }

    private suspend fun sendCloudNotification(
        targetPhone: String,
        senderPhone: String,
        senderName: String,
        title: String,
        message: String,
        relatedLoanCloudId: String,
        actionType: String = "SYSTEM"
    ) {
        val cleanTarget = normalizePhone(targetPhone)
        val cleanSender = normalizePhone(senderPhone)
        if (cleanTarget.isBlank()) return

        try {
            val notifCloudId = "notif_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
            val notifMap = hashMapOf(
                "cloudId" to notifCloudId,
                "targetPhone" to cleanTarget,
                "senderPhone" to cleanSender,
                "senderName" to senderName,
                "title" to title,
                "message" to message,
                "relatedLoanCloudId" to relatedLoanCloudId,
                "actionType" to actionType,
                "timestamp" to System.currentTimeMillis(),
                "isRead" to false
            )
            firestore.collection("notifications").document(notifCloudId).set(notifMap)
        } catch (e: Exception) {
            Log.w(TAG, "Firestore send cloud notification warning: ${e.message}")
        }
    }

    private suspend fun notifyBothInAppAndPhone(
        title: String,
        message: String,
        loanId: Long? = null,
        actionType: String = "SYSTEM"
    ) {
        dao.insertNotification(
            AppNotification(
                title = title,
                message = message,
                relatedLoanId = loanId,
                actionType = actionType
            )
        )
        context?.let { ctx ->
            PhoneNotificationHelper.showNotification(
                context = ctx,
                title = title,
                message = message,
                loanId = loanId,
                actionType = actionType
            )
        }
    }

    suspend fun updateLoan(loan: LoanTransaction) {
        val updated = loan.copy(updatedAt = System.currentTimeMillis())
        dao.updateLoan(updated)
        syncLoanToFirestore(updated)

        val userProf = dao.getUserProfileDirect()
        val currentPhone = userProf?.phoneNumber ?: ""
        val currentName = userProf?.name ?: "You"

        val otherPhone = if (normalizePhone(currentPhone) == normalizePhone(loan.creatorContact)) {
            loan.counterpartyContact
        } else {
            loan.creatorContact
        }

        sendCloudNotification(
            targetPhone = otherPhone,
            senderPhone = currentPhone,
            senderName = currentName,
            title = "Agreement Details Updated",
            message = "$currentName updated details for ₹${"%,.0f".format(loan.totalAmount)} agreement with ${loan.counterpartyName}.",
            relatedLoanCloudId = loan.cloudId,
            actionType = "APPROVAL_REQUEST"
        )
    }

    suspend fun signAgreement(loanId: Long, signerName: String, isUser: Boolean) {
        val loan = dao.getLoanById(loanId) ?: return
        val userProf = dao.getUserProfileDirect()
        val currentPhone = userProf?.phoneNumber ?: ""

        val updated = if (isUser) {
            loan.copy(
                userSigned = true,
                userApproved = true,
                userSignature = "Digitally Signed by $signerName",
                status = if (loan.counterpartySigned) ApprovalStatus.APPROVED.name else loan.status,
                updatedAt = System.currentTimeMillis()
            )
        } else {
            loan.copy(
                counterpartySigned = true,
                counterpartyApproved = true,
                counterpartySignature = "Digitally Signed by $signerName",
                status = if (loan.userSigned) ApprovalStatus.APPROVED.name else loan.status,
                updatedAt = System.currentTimeMillis()
            )
        }
        dao.updateLoan(updated)
        syncLoanToFirestore(updated)

        dao.insertNotification(
            AppNotification(
                title = "Agreement Signed ✍️",
                message = "$signerName digitally signed the transaction agreement of ₹${"%,.0f".format(loan.totalAmount)}.",
                relatedLoanId = loanId,
                actionType = "SYSTEM"
            )
        )

        // Notify other party via Cloud
        val otherPhone = if (isUser || normalizePhone(currentPhone) == normalizePhone(loan.creatorContact)) {
            loan.counterpartyContact
        } else {
            loan.creatorContact
        }

        sendCloudNotification(
            targetPhone = otherPhone,
            senderPhone = currentPhone,
            senderName = signerName,
            title = "Agreement Signed & Approved! ✍️",
            message = "$signerName digitally signed and approved the ₹${"%,.0f".format(loan.totalAmount)} transaction agreement.",
            relatedLoanCloudId = loan.cloudId
        )
    }

    suspend fun approveLoanByCounterparty(loanId: Long, approve: Boolean) {
        val loan = dao.getLoanById(loanId) ?: return
        val userProf = dao.getUserProfileDirect()
        val currentPhone = userProf?.phoneNumber ?: ""

        val otherPhone = if (normalizePhone(currentPhone) == normalizePhone(loan.counterpartyContact)) {
            loan.creatorContact
        } else {
            loan.counterpartyContact
        }

        if (approve) {
            val updatedLoan = loan.copy(
                status = ApprovalStatus.APPROVED.name,
                counterpartyApproved = true,
                counterpartySigned = true,
                counterpartySignature = "Digitally Signed by ${loan.counterpartyName}",
                updatedAt = System.currentTimeMillis()
            )
            dao.updateLoan(updatedLoan)
            syncLoanToFirestore(updatedLoan)

            dao.insertNotification(
                AppNotification(
                    title = "Approval & Agreement Confirmed! 🎉",
                    message = "${loan.counterpartyName} signed and mutually agreed to the transaction of ₹${"%,.0f".format(loan.totalAmount)}.",
                    relatedLoanId = loanId,
                    actionType = "SYSTEM"
                )
            )

            sendCloudNotification(
                targetPhone = otherPhone,
                senderPhone = currentPhone,
                senderName = loan.counterpartyName,
                title = "Agreement Approved & Signed! 🎉",
                message = "${loan.counterpartyName} signed and approved the ₹${"%,.0f".format(loan.totalAmount)} loan agreement.",
                relatedLoanCloudId = loan.cloudId
            )
        } else {
            val updatedLoan = loan.copy(
                status = ApprovalStatus.REJECTED.name,
                counterpartyApproved = false,
                counterpartySigned = false,
                counterpartySignature = "Declined by ${loan.counterpartyName}",
                updatedAt = System.currentTimeMillis()
            )
            dao.updateLoan(updatedLoan)
            syncLoanToFirestore(updatedLoan)

            dao.insertNotification(
                AppNotification(
                    title = "Transaction Disputed/Declined",
                    message = "${loan.counterpartyName} declined the record of ₹${"%,.0f".format(loan.totalAmount)}.",
                    relatedLoanId = loanId,
                    actionType = "SYSTEM"
                )
            )

            sendCloudNotification(
                targetPhone = otherPhone,
                senderPhone = currentPhone,
                senderName = loan.counterpartyName,
                title = "Agreement Declined",
                message = "${loan.counterpartyName} declined the record of ₹${"%,.0f".format(loan.totalAmount)}.",
                relatedLoanCloudId = loan.cloudId
            )
        }
    }

    suspend fun requestSettlement(
        loanId: Long,
        requestedBy: String = "You",
        settlementProofUri: String = "",
        proofFilesJson: String = ""
    ) {
        val loan = dao.getLoanById(loanId) ?: return
        val userProf = dao.getUserProfileDirect()
        val currentPhone = userProf?.phoneNumber ?: ""

        val updated = loan.copy(
            status = ApprovalStatus.PENDING_SETTLEMENT.name,
            settlementRequestedBy = requestedBy,
            settlementProofUri = settlementProofUri,
            proofFilesJson = if (proofFilesJson.isNotBlank()) proofFilesJson else loan.proofFilesJson,
            updatedAt = System.currentTimeMillis()
        )
        dao.updateLoan(updated)
        syncLoanToFirestore(updated)

        val notifTitle = "Settlement Requested: ₹${"%,.0f".format(loan.totalAmount)}"
        val notifMessage = "$requestedBy requested full settlement confirmation for this record. Dual confirmation is required."

        notifyBothInAppAndPhone(
            title = notifTitle,
            message = notifMessage,
            loanId = loanId,
            actionType = "APPROVAL_REQUEST"
        )

        val otherPhone = if (requestedBy == loan.createdBy || normalizePhone(currentPhone) == normalizePhone(loan.creatorContact)) {
            loan.counterpartyContact
        } else {
            loan.creatorContact
        }

        sendCloudNotification(
            targetPhone = otherPhone,
            senderPhone = currentPhone,
            senderName = requestedBy,
            title = notifTitle,
            message = "$requestedBy requested full settlement confirmation for ₹${"%,.0f".format(loan.totalAmount)}. Please review and confirm.",
            relatedLoanCloudId = loan.cloudId,
            actionType = "APPROVAL_REQUEST"
        )
    }

    suspend fun confirmSettlement(loanId: Long, confirmed: Boolean, confirmedBy: String = "You") {
        val loan = dao.getLoanById(loanId) ?: return
        val userProf = dao.getUserProfileDirect()
        val currentPhone = userProf?.phoneNumber ?: ""
        val currentName = userProf?.name?.trim() ?: "You"

        val isRequester = loan.settlementRequestedBy.isNotBlank() && (
            loan.settlementRequestedBy.equals(confirmedBy, ignoreCase = true) ||
            loan.settlementRequestedBy.equals(currentName, ignoreCase = true) ||
            (loan.settlementRequestedBy == "You" && confirmedBy == "You")
        )

        // Positive scenario: Requester cannot self-approve; only counterparty can confirm settlement
        if (confirmed && isRequester) {
            Log.w(TAG, "Cannot self-approve settlement. Counterparty confirmation required.")
            return
        }

        val otherPhone = if (normalizePhone(currentPhone) == normalizePhone(loan.creatorContact)) {
            loan.counterpartyContact
        } else {
            loan.creatorContact
        }

        if (confirmed) {
            val remainingAmt = (loan.totalAmount - loan.settledAmount).coerceAtLeast(0.0)
            val existingPayments = parsePaymentHistory(loan.paymentHistoryJson)
            val finalPayment = PaymentRecord(
                amount = remainingAmt,
                note = "Full Settlement Confirmed",
                proofUri = loan.settlementProofUri.ifBlank { loan.proofUri },
                recordedBy = confirmedBy,
                isSettlement = true,
                isApproved = true,
                approvedBy = confirmedBy
            )
            val newPayments = if (remainingAmt > 0) existingPayments + finalPayment else existingPayments

            val updated = loan.copy(
                status = ApprovalStatus.SETTLED.name,
                settledAmount = loan.totalAmount,
                paymentHistoryJson = encodePaymentHistory(newPayments),
                updatedAt = System.currentTimeMillis()
            )
            dao.updateLoan(updated)
            syncLoanToFirestore(updated)

            val title = "Settlement Confirmed! 🤝"
            val message = "Both parties confirmed full settlement of ₹${"%,.0f".format(loan.totalAmount)} for ${loan.counterpartyName}."

            notifyBothInAppAndPhone(
                title = title,
                message = message,
                loanId = loanId,
                actionType = "SYSTEM"
            )

            sendCloudNotification(
                targetPhone = otherPhone,
                senderPhone = currentPhone,
                senderName = confirmedBy,
                title = title,
                message = "Full settlement of ₹${"%,.0f".format(loan.totalAmount)} mutually confirmed.",
                relatedLoanCloudId = loan.cloudId
            )
        } else {
            val updated = loan.copy(
                status = ApprovalStatus.APPROVED.name,
                settlementRequestedBy = "",
                updatedAt = System.currentTimeMillis()
            )
            dao.updateLoan(updated)
            syncLoanToFirestore(updated)

            val title = "Settlement Request Declined"
            val message = "Settlement request for ₹${"%,.0f".format(loan.totalAmount)} was declined by $confirmedBy."

            notifyBothInAppAndPhone(
                title = title,
                message = message,
                loanId = loanId,
                actionType = "SYSTEM"
            )

            sendCloudNotification(
                targetPhone = otherPhone,
                senderPhone = currentPhone,
                senderName = confirmedBy,
                title = title,
                message = message,
                relatedLoanCloudId = loan.cloudId
            )
        }
    }

    suspend fun recordPartialPayment(
        loanId: Long,
        amount: Double,
        note: String,
        proofUri: String = "",
        recordedBy: String = "You",
        isDirectApproval: Boolean = false,
        paymentTarget: String = "COMBINED",
        proofFilesJson: String = ""
    ) {
        val loan = dao.getLoanById(loanId) ?: return
        val userProf = dao.getUserProfileDirect()
        val currentPhone = userProf?.phoneNumber ?: ""

        val filesJson = if (proofFilesJson.isNotBlank()) {
            proofFilesJson
        } else if (proofUri.trim().startsWith("[")) {
            proofUri
        } else ""

        val effectiveProofUri = if (proofUri.trim().startsWith("[")) {
            val parsed = ProofStorageHelper.parseProofFiles(proofUri)
            parsed.firstOrNull()?.localPath ?: ""
        } else {
            proofUri
        }

        val effectiveTarget = when {
            paymentTarget.equals("INTEREST", ignoreCase = true) || note.startsWith("[INTEREST]", ignoreCase = true) -> "INTEREST"
            paymentTarget.equals("PRINCIPAL", ignoreCase = true) || note.startsWith("[PRINCIPAL]", ignoreCase = true) -> "PRINCIPAL"
            else -> "COMBINED"
        }

        val cleanNote = when {
            note.startsWith("[INTEREST]", ignoreCase = true) -> note.removePrefix("[INTEREST]").trim()
            note.startsWith("[PRINCIPAL]", ignoreCase = true) -> note.removePrefix("[PRINCIPAL]").trim()
            else -> note
        }

        val payment = PaymentRecord(
            amount = amount,
            note = cleanNote,
            proofUri = effectiveProofUri,
            recordedBy = recordedBy,
            isSettlement = false,
            isApproved = isDirectApproval,
            approvedBy = if (isDirectApproval) recordedBy else "",
            paymentTarget = effectiveTarget,
            proofFilesJson = filesJson
        )
        val existingPayments = parsePaymentHistory(loan.paymentHistoryJson)
        val newPayments = existingPayments + payment

        val approvedTotal = newPayments.filter { it.isApproved }.sumOf { it.amount }.coerceAtMost(loan.totalAmount)

        val updated = loan.copy(
            settledAmount = approvedTotal,
            paymentHistoryJson = encodePaymentHistory(newPayments),
            updatedAt = System.currentTimeMillis()
        )
        dao.updateLoan(updated)
        syncLoanToFirestore(updated)

        val notifTitle = if (isDirectApproval) "Payment Confirmed: ₹${"%,.0f".format(amount)}" else "Payment Verification Pending: ₹${"%,.0f".format(amount)}"
        val notifMessage = if (isDirectApproval) {
            "Payment of ₹${"%,.0f".format(amount)} recorded and confirmed. Remaining: ₹${"%,.0f".format(loan.totalAmount - approvedTotal)}."
        } else {
            "Payment of ₹${"%,.0f".format(amount)} logged by $recordedBy with proof. Awaiting verification."
        }

        notifyBothInAppAndPhone(
            title = notifTitle,
            message = notifMessage,
            loanId = loanId,
            actionType = if (isDirectApproval) "SYSTEM" else "APPROVAL_REQUEST"
        )

        val otherPhone = if (normalizePhone(currentPhone) == normalizePhone(loan.creatorContact)) {
            loan.counterpartyContact
        } else {
            loan.creatorContact
        }

        sendCloudNotification(
            targetPhone = otherPhone,
            senderPhone = currentPhone,
            senderName = recordedBy,
            title = notifTitle,
            message = notifMessage,
            relatedLoanCloudId = loan.cloudId,
            actionType = if (isDirectApproval) "SYSTEM" else "APPROVAL_REQUEST"
        )
    }

    suspend fun approvePartialPayment(loanId: Long, paymentId: String, approvedBy: String) {
        val loan = dao.getLoanById(loanId) ?: return
        val userProf = dao.getUserProfileDirect()
        val currentPhone = userProf?.phoneNumber ?: ""

        val existingPayments = parsePaymentHistory(loan.paymentHistoryJson)
        val targetPayment = existingPayments.firstOrNull { it.id == paymentId } ?: return

        val newPayments = existingPayments.map {
            if (it.id == paymentId) it.copy(isApproved = true, approvedBy = approvedBy) else it
        }

        val approvedTotal = newPayments.filter { it.isApproved }.sumOf { it.amount }.coerceAtMost(loan.totalAmount)

        val updated = loan.copy(
            settledAmount = approvedTotal,
            paymentHistoryJson = encodePaymentHistory(newPayments),
            updatedAt = System.currentTimeMillis()
        )
        dao.updateLoan(updated)
        syncLoanToFirestore(updated)

        val notifTitle = "Payment Confirmed! ✓"
        val notifMessage = "$approvedBy confirmed receipt of ₹${"%,.0f".format(targetPayment.amount)}. Remaining: ₹${"%,.0f".format(loan.totalAmount - approvedTotal)}."

        notifyBothInAppAndPhone(
            title = notifTitle,
            message = notifMessage,
            loanId = loanId,
            actionType = "SYSTEM"
        )

        val otherPhone = if (normalizePhone(currentPhone) == normalizePhone(loan.creatorContact)) {
            loan.counterpartyContact
        } else {
            loan.creatorContact
        }

        sendCloudNotification(
            targetPhone = otherPhone,
            senderPhone = currentPhone,
            senderName = approvedBy,
            title = notifTitle,
            message = notifMessage,
            relatedLoanCloudId = loan.cloudId
        )
    }

    suspend fun rejectPartialPayment(loanId: Long, paymentId: String, declinedBy: String) {
        val loan = dao.getLoanById(loanId) ?: return
        val userProf = dao.getUserProfileDirect()
        val currentPhone = userProf?.phoneNumber ?: ""

        val existingPayments = parsePaymentHistory(loan.paymentHistoryJson)
        val targetPayment = existingPayments.firstOrNull { it.id == paymentId } ?: return

        val newPayments = existingPayments.filterNot { it.id == paymentId }
        val approvedTotal = newPayments.filter { it.isApproved }.sumOf { it.amount }.coerceAtMost(loan.totalAmount)

        val updated = loan.copy(
            settledAmount = approvedTotal,
            paymentHistoryJson = encodePaymentHistory(newPayments),
            updatedAt = System.currentTimeMillis()
        )
        dao.updateLoan(updated)
        syncLoanToFirestore(updated)

        val notifTitle = "Payment Entry Declined"
        val notifMessage = "Payment entry of ₹${"%,.0f".format(targetPayment.amount)} was declined by $declinedBy."

        notifyBothInAppAndPhone(
            title = notifTitle,
            message = notifMessage,
            loanId = loanId,
            actionType = "SYSTEM"
        )

        val otherPhone = if (normalizePhone(currentPhone) == normalizePhone(loan.creatorContact)) {
            loan.counterpartyContact
        } else {
            loan.creatorContact
        }

        sendCloudNotification(
            targetPhone = otherPhone,
            senderPhone = currentPhone,
            senderName = declinedBy,
            title = notifTitle,
            message = notifMessage,
            relatedLoanCloudId = loan.cloudId
        )
    }

    suspend fun sendReminder(loanId: Long) {
        val loan = dao.getLoanById(loanId) ?: return
        val userProf = dao.getUserProfileDirect()
        val currentPhone = userProf?.phoneNumber ?: ""
        val currentName = userProf?.name ?: "You"

        val updated = loan.copy(
            remindedAtTimestamp = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        dao.updateLoan(updated)
        syncLoanToFirestore(updated)

        val title = "Payment Reminder 🔔"
        val message = "Payment reminder sent to ${loan.counterpartyName} for ₹${"%,.0f".format(loan.totalAmount - loan.settledAmount)}."

        notifyBothInAppAndPhone(
            title = title,
            message = message,
            loanId = loanId,
            actionType = "PAYMENT_REMINDER"
        )

        sendCloudNotification(
            targetPhone = loan.counterpartyContact,
            senderPhone = currentPhone,
            senderName = currentName,
            title = title,
            message = "$currentName sent a payment reminder for outstanding balance of ₹${"%,.0f".format(loan.totalAmount - loan.settledAmount)}.",
            relatedLoanCloudId = loan.cloudId,
            actionType = "PAYMENT_REMINDER"
        )
    }

    /**
     * Direct deletion of payment record.
     * Deletes the payment from history, recalculates settled balance, and updates both local and cloud.
     */
    suspend fun deletePaymentRecord(loanId: Long, paymentId: String, deletedBy: String = "You") {
        val loan = dao.getLoanById(loanId) ?: return
        val userProf = dao.getUserProfileDirect()
        val currentPhone = userProf?.phoneNumber ?: ""

        val existingPayments = parsePaymentHistory(loan.paymentHistoryJson)
        val targetPayment = existingPayments.firstOrNull { it.id == paymentId } ?: return

        val newPayments = existingPayments.filterNot { it.id == paymentId }
        val approvedTotal = newPayments.filter { it.isApproved }.sumOf { it.amount }.coerceAtMost(loan.totalAmount)

        // If loan was previously settled, but a payment was removed, reopen it to APPROVED
        val newStatus = if (loan.status == ApprovalStatus.SETTLED.name && approvedTotal < loan.totalAmount) {
            ApprovalStatus.APPROVED.name
        } else {
            loan.status
        }

        val updated = loan.copy(
            settledAmount = approvedTotal,
            paymentHistoryJson = encodePaymentHistory(newPayments),
            status = newStatus,
            updatedAt = System.currentTimeMillis()
        )
        dao.updateLoan(updated)
        syncLoanToFirestore(updated)

        val notifTitle = "Payment Record Deleted"
        val notifMessage = "Payment entry of ₹${"%,.0f".format(targetPayment.amount)} was removed by $deletedBy. Updated total paid: ₹${"%,.0f".format(approvedTotal)}."

        notifyBothInAppAndPhone(
            title = notifTitle,
            message = notifMessage,
            loanId = loanId,
            actionType = "SYSTEM"
        )

        val otherPhone = if (normalizePhone(currentPhone) == normalizePhone(loan.creatorContact)) {
            loan.counterpartyContact
        } else {
            loan.creatorContact
        }

        sendCloudNotification(
            targetPhone = otherPhone,
            senderPhone = currentPhone,
            senderName = deletedBy,
            title = notifTitle,
            message = notifMessage,
            relatedLoanCloudId = loan.cloudId,
            actionType = "SYSTEM"
        )
    }

    suspend fun requestPaymentDeletion(loanId: Long, paymentId: String, requesterName: String) {
        val loan = dao.getLoanById(loanId) ?: return
        val userProf = dao.getUserProfileDirect()
        val currentPhone = userProf?.phoneNumber ?: ""

        val existingPayments = parsePaymentHistory(loan.paymentHistoryJson)
        val targetPayment = existingPayments.firstOrNull { it.id == paymentId } ?: return

        // If counterparty is not on app or payment was unapproved, delete directly without waiting
        if (!targetPayment.isApproved || loan.counterpartyContact.isBlank()) {
            deletePaymentRecord(loanId, paymentId, requesterName)
            return
        }

        val updatedPayments = existingPayments.map {
            if (it.id == paymentId) it.copy(deletionRequestedBy = requesterName) else it
        }
        val updated = loan.copy(
            paymentHistoryJson = encodePaymentHistory(updatedPayments),
            updatedAt = System.currentTimeMillis()
        )
        dao.updateLoan(updated)
        syncLoanToFirestore(updated)

        val notifTitle = "Payment Deletion Requested: ₹${"%,.0f".format(targetPayment.amount)}"
        val notifMessage = "$requesterName requested deletion of ₹${"%,.0f".format(targetPayment.amount)} payment record. Confirmation required."

        notifyBothInAppAndPhone(
            title = notifTitle,
            message = notifMessage,
            loanId = loanId,
            actionType = "APPROVAL_REQUEST"
        )

        val otherPhone = if (normalizePhone(currentPhone) == normalizePhone(loan.creatorContact)) {
            loan.counterpartyContact
        } else {
            loan.creatorContact
        }

        sendCloudNotification(
            targetPhone = otherPhone,
            senderPhone = currentPhone,
            senderName = requesterName,
            title = notifTitle,
            message = notifMessage,
            relatedLoanCloudId = loan.cloudId,
            actionType = "APPROVAL_REQUEST"
        )
    }

    suspend fun confirmPaymentDeletion(loanId: Long, paymentId: String, approve: Boolean, confirmedBy: String) {
        val loan = dao.getLoanById(loanId) ?: return

        if (approve) {
            deletePaymentRecord(loanId, paymentId, confirmedBy)
        } else {
            val existingPayments = parsePaymentHistory(loan.paymentHistoryJson)
            val targetPayment = existingPayments.firstOrNull { it.id == paymentId } ?: return

            val updatedPayments = existingPayments.map {
                if (it.id == paymentId) it.copy(deletionRequestedBy = "") else it
            }
            val updated = loan.copy(
                paymentHistoryJson = encodePaymentHistory(updatedPayments),
                updatedAt = System.currentTimeMillis()
            )
            dao.updateLoan(updated)
            syncLoanToFirestore(updated)

            val title = "Payment Deletion Declined"
            val message = "Request to delete payment of ₹${"%,.0f".format(targetPayment.amount)} was declined by $confirmedBy."

            notifyBothInAppAndPhone(
                title = title,
                message = message,
                loanId = loanId,
                actionType = "SYSTEM"
            )
        }
    }

    suspend fun deleteLoan(id: Long) {
        val loan = dao.getLoanById(id)
        dao.deleteLoanById(id)
        if (loan != null && loan.cloudId.isNotBlank()) {
            try {
                firestore.collection("loans").document(loan.cloudId).delete()
            } catch (e: Exception) {
                Log.w(TAG, "Firestore delete warning: ${e.message}")
            }
        }
    }

    suspend fun requestLoanDeletion(id: Long, requesterName: String) {
        val loan = dao.getLoanById(id) ?: return
        val userProf = dao.getUserProfileDirect()
        val currentPhone = userProf?.phoneNumber ?: ""

        if (loan.status == ApprovalStatus.PENDING_APPROVAL.name && !loan.counterpartySigned) {
            deleteLoan(id)
            return
        }
        val updated = loan.copy(
            deletionRequestedBy = requesterName,
            updatedAt = System.currentTimeMillis()
        )
        dao.updateLoan(updated)
        syncLoanToFirestore(updated)

        dao.insertNotification(
            AppNotification(
                title = "Deletion Requested: ₹${"%,.0f".format(loan.totalAmount)}",
                message = "$requesterName requested mutual deletion of this transaction record. Your confirmation is required.",
                relatedLoanId = id,
                actionType = "APPROVAL_REQUEST"
            )
        )

        val otherPhone = if (normalizePhone(currentPhone) == normalizePhone(loan.creatorContact)) {
            loan.counterpartyContact
        } else {
            loan.creatorContact
        }

        sendCloudNotification(
            targetPhone = otherPhone,
            senderPhone = currentPhone,
            senderName = requesterName,
            title = "Transaction Deletion Requested",
            message = "$requesterName requested deletion of ₹${"%,.0f".format(loan.totalAmount)} agreement. Both parties must confirm.",
            relatedLoanCloudId = loan.cloudId,
            actionType = "APPROVAL_REQUEST"
        )
    }

    suspend fun confirmLoanDeletion(id: Long, approve: Boolean, confirmedBy: String) {
        val loan = dao.getLoanById(id) ?: return
        val userProf = dao.getUserProfileDirect()
        val currentPhone = userProf?.phoneNumber ?: ""

        if (approve) {
            deleteLoan(id)
            dao.insertNotification(
                AppNotification(
                    title = "Record Mutually Deleted",
                    message = "Transaction record of ₹${"%,.0f".format(loan.totalAmount)} was mutually confirmed and deleted by $confirmedBy.",
                    relatedLoanId = id,
                    actionType = "SYSTEM"
                )
            )

            val otherPhone = if (normalizePhone(currentPhone) == normalizePhone(loan.creatorContact)) {
                loan.counterpartyContact
            } else {
                loan.creatorContact
            }

            sendCloudNotification(
                targetPhone = otherPhone,
                senderPhone = currentPhone,
                senderName = confirmedBy,
                title = "Record Mutually Deleted",
                message = "Transaction record of ₹${"%,.0f".format(loan.totalAmount)} was confirmed and deleted.",
                relatedLoanCloudId = loan.cloudId
            )
        } else {
            val updated = loan.copy(
                deletionRequestedBy = "",
                updatedAt = System.currentTimeMillis()
            )
            dao.updateLoan(updated)
            syncLoanToFirestore(updated)

            dao.insertNotification(
                AppNotification(
                    title = "Deletion Request Declined",
                    message = "Deletion request for ₹${"%,.0f".format(loan.totalAmount)} was declined by $confirmedBy. Record remains active.",
                    relatedLoanId = id,
                    actionType = "SYSTEM"
                )
            )
        }
    }

    // Notifications
    fun getAllNotifications(): Flow<List<AppNotification>> = dao.getAllNotifications()
    fun getUnreadNotificationCount(): Flow<Int> = dao.getUnreadNotificationCount()
    suspend fun markNotificationRead(id: Long) = dao.markNotificationRead(id)
    suspend fun markAllNotificationsRead() = dao.markAllNotificationsRead()
}
