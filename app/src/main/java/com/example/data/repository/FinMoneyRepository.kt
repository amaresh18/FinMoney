package com.example.data.repository

import com.example.data.local.FinMoneyDao
import com.example.data.model.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow

class FinMoneyRepository(private val dao: FinMoneyDao) {

    val allLoans: Flow<List<LoanTransaction>> = dao.getAllLoans()
    val allDebits: Flow<List<DebitItem>> = dao.getAllDebits()
    val allNotifications: Flow<List<AppNotification>> = dao.getAllNotifications()
    val allBankTransactions: Flow<List<BankTransactionRecord>> = dao.getAllBankTransactions()

    fun getSalaryRecord(month: Int, year: Int): Flow<SalaryRecord?> = dao.getSalaryRecord(month, year)
    fun getDebitsForMonth(month: Int, year: Int): Flow<List<DebitItem>> = dao.getDebitsForMonth(month, year)

    suspend fun insertOrUpdateSalary(record: SalaryRecord) = dao.insertOrUpdateSalary(record)
    suspend fun insertDebit(item: DebitItem) = dao.insertDebit(item)
    suspend fun insertDebits(items: List<DebitItem>) = dao.insertDebits(items)
    suspend fun updateDebit(item: DebitItem) = dao.updateDebit(item)
    suspend fun deleteDebit(item: DebitItem) = dao.deleteDebit(item)
    suspend fun insertLoan(loan: LoanTransaction) = dao.insertLoan(loan)
    suspend fun updateLoan(loan: LoanTransaction) = dao.updateLoan(loan)
    suspend fun deleteLoanById(id: Long) = dao.deleteLoanById(id)
    suspend fun insertNotification(notification: AppNotification) = dao.insertNotification(notification)

    companion object {
        private val gson = Gson()

        fun parseBankBalances(json: String): List<BankBalanceItem> {
            if (json.isBlank()) return emptyList()
            return try {
                val type = object : TypeToken<List<BankBalanceItem>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }

        fun encodeBankBalances(balances: List<BankBalanceItem>): String {
            return try {
                gson.toJson(balances)
            } catch (e: Exception) {
                "[]"
            }
        }

        fun parseIncomeSources(json: String): List<IncomeSource> {
            if (json.isBlank()) return emptyList()
            return try {
                val type = object : TypeToken<List<IncomeSource>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }

        fun encodeIncomeSources(sources: List<IncomeSource>): String {
            return try {
                gson.toJson(sources)
            } catch (e: Exception) {
                "[]"
            }
        }
    }
}
