package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.model.DebitCategory
import com.example.data.model.IncomeCategory
import com.example.data.model.LoanApprovalStatus
import com.example.data.model.LoanLedgerKind
import com.example.data.model.LoanType

class Converters {
    @TypeConverter
    fun fromLoanType(value: LoanType?): String? = value?.name

    @TypeConverter
    fun toLoanType(value: String?): LoanType? {
        return value?.let {
            try {
                LoanType.valueOf(it)
            } catch (_: Exception) {
                if (it.equals("BORROWED", true) || it.equals("TAKEN", true)) LoanType.BORROWED else LoanType.LENT
            }
        }
    }

    @TypeConverter
    fun fromLoanLedgerKind(value: LoanLedgerKind?): String? = value?.name

    @TypeConverter
    fun toLoanLedgerKind(value: String?): LoanLedgerKind? {
        return value?.let {
            try {
                LoanLedgerKind.valueOf(it)
            } catch (_: Exception) {
                LoanLedgerKind.CLEARING
            }
        }
    }

    @TypeConverter
    fun fromLoanApprovalStatus(value: LoanApprovalStatus?): String? = value?.name

    @TypeConverter
    fun toLoanApprovalStatus(value: String?): LoanApprovalStatus? {
        return value?.let {
            try {
                LoanApprovalStatus.valueOf(it)
            } catch (_: Exception) {
                LoanApprovalStatus.PENDING
            }
        }
    }

    @TypeConverter
    fun fromDebitCategory(value: DebitCategory?): String? = value?.name

    @TypeConverter
    fun toDebitCategory(value: String?): DebitCategory? {
        return value?.let {
            try {
                DebitCategory.valueOf(it)
            } catch (_: Exception) {
                DebitCategory.GENERAL
            }
        }
    }

    @TypeConverter
    fun fromIncomeCategory(value: IncomeCategory?): String? = value?.name

    @TypeConverter
    fun toIncomeCategory(value: String?): IncomeCategory? {
        return value?.let {
            try {
                IncomeCategory.valueOf(it)
            } catch (_: Exception) {
                IncomeCategory.OTHER
            }
        }
    }
}
