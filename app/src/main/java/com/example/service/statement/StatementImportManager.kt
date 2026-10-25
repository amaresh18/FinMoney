package com.example.service.statement

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.bank.BankTxType
import com.example.data.local.AppDatabase
import com.example.data.model.BankTransactionRecord
import com.example.data.model.DebitItem
import com.example.data.model.IncomeCategory
import com.example.data.model.IncomeSource
import com.example.service.BankSyncManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.UUID

data class ImportCommitSummary(
    val totalProcessed: Int,
    val debitsAddedCount: Int,
    val totalDebitAmount: Double,
    val creditsAddedCount: Int,
    val totalCreditAmount: Double,
    val duplicatesSkippedCount: Int,
    val failedSkippedCount: Int
)

object StatementImportManager {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val incomeListType = Types.newParameterizedType(List::class.java, IncomeSource::class.java)
    private val incomeAdapter = moshi.adapter<List<IncomeSource>>(incomeListType)

    /**
     * Reads and extracts text from an uploaded statement file Uri.
     */
    suspend fun readStatementFromUri(context: Context, uri: Uri): Pair<String, String> = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        val fileName = getFileName(context, uri)
        val mimeType = contentResolver.getType(uri) ?: ""
        val lowerName = fileName.lowercase()

        val extractedText = try {
            val stream: InputStream? = contentResolver.openInputStream(uri)
            if (stream == null) {
                ""
            } else {
                when {
                    lowerName.endsWith(".pdf") || mimeType.contains("pdf") -> {
                        PdfTextExtractor.extractText(stream)
                    }
                    lowerName.endsWith(".xlsx") || mimeType.contains("spreadsheetml") -> {
                        XlsxTextExtractor.extractText(stream)
                    }
                    else -> {
                        // Plain text, CSV, TSV
                        stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("StatementImportManager", "Failed reading file from Uri: ${e.message}", e)
            ""
        }

        Pair(fileName, extractedText)
    }

    /**
     * Parses the extracted text into structured credit and debit transactions.
     * Considers only successful transaction statuses.
     */
    fun parseStatementText(text: String, fileName: String): StatementParseResult {
        return UniversalStatementParser.parse(text, fileName)
    }

    /**
     * Commits successful imported transactions into the local Room database and Firestore.
     * Accurately creates paid DebitItems for debits and IncomeSources for credits.
     * Deduplicates against existing database entries.
     */
    suspend fun commitTransactions(
        context: Context,
        transactions: List<ImportedStatementTransaction>
    ): ImportCommitSummary = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val dao = db.finMoneyDao()
        val cloudUser = com.example.cloud.CloudUserContextResolver.resolve(dao)

        val existingTxs = dao.getRecentBankTransactionsDirect(300)
        val existingRefs = existingTxs.mapNotNull { it.referenceNumber.takeIf { ref -> ref.isNotBlank() } }.toSet()

        var debitsAdded = 0
        var totalDebits = 0.0
        var creditsAdded = 0
        var totalCredits = 0.0
        var duplicatesSkipped = 0
        var failedSkipped = 0

        for (tx in transactions) {
            // Strictly enforce SUCCESS status check
            if (!tx.isSuccess) {
                failedSkipped++
                continue
            }

            // Deduplication: skip if reference number already exists in bank transactions
            if (tx.referenceNumber.isNotBlank() && existingRefs.contains(tx.referenceNumber)) {
                duplicatesSkipped++
                continue
            }

            // Check if identical amount, bank, and date already exists
            val isDuplicate = existingTxs.any { existing ->
                existing.bankName.equals(tx.bankName, ignoreCase = true) &&
                kotlin.math.abs(existing.amount - tx.amount) < 0.01 &&
                kotlin.math.abs(existing.timestamp - tx.timestamp) < 86_400_000L &&
                existing.type.equals(tx.type.name, ignoreCase = true)
            }

            if (isDuplicate) {
                duplicatesSkipped++
                continue
            }

            val monthYear = "%04d-%02d".format(tx.year, tx.month)
            val fingerprint = listOf(
                tx.sourceSystem,
                tx.bankName,
                tx.type.name,
                "%.2f".format(tx.amount),
                tx.referenceNumber.ifBlank { "${tx.day}-${tx.month}-${tx.year}-${tx.merchantOrName.take(12)}" }
            ).joinToString("|").lowercase()

            // 1. Insert into bank_transactions as APPROVED (already reconciled via statement)
            val record = BankTransactionRecord(
                userId = cloudUser.userId,
                cloudId = UUID.randomUUID().toString(),
                bankName = tx.bankName,
                accountNumberLast4 = tx.accountLast4,
                amount = tx.amount,
                type = tx.type.name,
                classification = "Bank",
                category = tx.category,
                merchant = tx.merchantOrName,
                referenceNumber = tx.referenceNumber,
                originalText = tx.rawLine,
                source = "STATEMENT_IMPORT",
                timestamp = tx.timestamp,
                isConfirmed = true,
                status = "APPROVED",
                fingerprint = fingerprint
            )
            val insertedTxId = dao.insertBankTransaction(record)
            val savedRecord = record.copy(id = insertedTxId)

            // 2. Handle Debits vs Credits in financial ledger
            if (tx.type == BankTxType.DEBIT) {
                val debitItem = DebitItem(
                    userId = cloudUser.userId,
                    cloudId = UUID.randomUUID().toString(),
                    month = tx.month,
                    year = tx.year,
                    monthYear = monthYear,
                    category = tx.category.ifBlank { "GENERAL" },
                    title = tx.merchantOrName,
                    amount = tx.amount,
                    dueDateDay = tx.day,
                    dueDate = tx.timestamp,
                    isPaid = true,
                    isPaidViaDebitCard = true,
                    isPaidViaCreditCard = false,
                    paidDateTimestamp = tx.timestamp,
                    paidDate = tx.timestamp,
                    notes = "Imported from ${tx.sourceSystem} statement (${tx.bankName}). Status: SUCCESS",
                    createdAt = tx.timestamp
                )
                val debitId = dao.insertDebit(debitItem)
                // Link bank tx → debit for reconciliation screens
                dao.insertBankTransaction(
                    savedRecord.copy(matchedDebitId = debitId, matchedDebitTitle = debitItem.title)
                )
                BankSyncManager.saveDebitToFirestore(context, debitItem.copy(id = debitId), cloudUser, savedRecord)

                debitsAdded++
                totalDebits += tx.amount
            } else {
                // CREDIT: Add to Monthly Income sources for that month/year
                addIncomeCredit(context, dao, tx, cloudUser, savedRecord)
                creditsAdded++
                totalCredits += tx.amount
            }

            BankSyncManager.saveTransactionToFirestore(context, savedRecord, cloudUser)
        }

        ImportCommitSummary(
            totalProcessed = transactions.size,
            debitsAddedCount = debitsAdded,
            totalDebitAmount = totalDebits,
            creditsAddedCount = creditsAdded,
            totalCreditAmount = totalCredits,
            duplicatesSkippedCount = duplicatesSkipped,
            failedSkippedCount = failedSkipped
        )
    }

    private suspend fun addIncomeCredit(
        context: Context,
        dao: com.example.data.local.FinMoneyDao,
        tx: ImportedStatementTransaction,
        cloudUser: com.example.cloud.CloudUserContext,
        bankTxRecord: BankTransactionRecord? = null
    ) {
        val existingSalary = dao.getSalaryRecordDirect(tx.month, tx.year)
        val currentSources = if (!existingSalary?.incomeSourcesJson.isNullOrBlank()) {
            com.example.ui.FinMoneyViewModel.parseIncomeSources(existingSalary!!.incomeSourcesJson)
        } else emptyList()

        val creditName = tx.merchantOrName.ifBlank { "${tx.bankName} Credit" }
        val newSource = IncomeSource(
            id = UUID.randomUUID().toString(),
            title = creditName,
            name = creditName,
            amount = tx.amount,
            category = IncomeCategory.OTHER.name,
            isReceived = true,
            isRecurring = false,
            receivedDateTimestamp = tx.timestamp
        )

        val updatedSources = currentSources + newSource
        val json = com.example.data.repository.FinMoneyRepository.encodeIncomeSources(updatedSources)
        val totalAdditional = updatedSources.sumOf { it.amount }
        val monthYear = "%04d-%02d".format(tx.year, tx.month)

        if (existingSalary != null) {
            val updatedRecord = existingSalary.copy(
                monthYear = existingSalary.monthYear.ifBlank { monthYear },
                additionalIncome = totalAdditional,
                incomeSourcesJson = json,
                updatedAt = System.currentTimeMillis()
            )
            dao.insertOrUpdateSalary(updatedRecord)
            BankSyncManager.saveSalaryToFirestore(context, updatedRecord.copy(userId = cloudUser.userId), cloudUser)
        } else {
            val newRecord = com.example.data.model.SalaryRecord(
                userId = cloudUser.userId,
                month = tx.month,
                year = tx.year,
                monthYear = monthYear,
                salaryAmount = 0.0,
                additionalIncome = totalAdditional,
                incomeSourcesJson = json,
                updatedAt = System.currentTimeMillis()
            )
            dao.insertOrUpdateSalary(newRecord)
            BankSyncManager.saveSalaryToFirestore(context, newRecord, cloudUser)
        }
        BankSyncManager.saveIncomeToFirestore(context, newSource, tx.month, tx.year, cloudUser, bankTxRecord)
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var name = "Statement"
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex) ?: name
                }
            }
        } else {
            name = uri.lastPathSegment ?: name
        }
        return name
    }
}
