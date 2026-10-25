package com.example.domain.usecase

import com.example.data.model.LoanApprovalStatus
import com.example.data.model.LoanLedgerEntry
import com.example.data.model.LoanLedgerKind
import kotlin.math.round

object LoanLedgerMath {
    private const val EPS = 0.009

    fun money(value: Double): Double = round(value * 100.0) / 100.0

    fun countsTowardBalance(status: LoanApprovalStatus): Boolean =
        status == LoanApprovalStatus.MUTUAL_APPROVED || status == LoanApprovalStatus.APPROVED

    fun isPending(status: LoanApprovalStatus): Boolean =
        status == LoanApprovalStatus.PENDING || status == LoanApprovalStatus.PENDING_APPROVAL

    fun sign(kind: LoanLedgerKind): Int = if (kind == LoanLedgerKind.ADDITIONAL) 1 else -1

    /** Agreed principal plus approved additions, minus approved repayments. Pending rows are ignored. */
    fun outstanding(principal: Double, entries: List<LoanLedgerEntry>): Double {
        val delta = entries
            .filter { countsTowardBalance(it.status) }
            .sumOf { sign(it.kind) * it.amount }
        return money(principal + delta)
    }

    fun cleared(entries: List<LoanLedgerEntry>): Double =
        money(entries.filter { countsTowardBalance(it.status) && it.kind == LoanLedgerKind.CLEARING }.sumOf { it.amount })

    fun added(entries: List<LoanLedgerEntry>): Double =
        money(entries.filter { countsTowardBalance(it.status) && it.kind == LoanLedgerKind.ADDITIONAL }.sumOf { it.amount })

    /**
     * Balance after this entry is approved or its approved amount is replaced.
     * [replacingId] drops that row's current approved amount before applying [newAmount].
     */
    fun outstandingAfter(
        principal: Double,
        entries: List<LoanLedgerEntry>,
        replacingId: Long?,
        kind: LoanLedgerKind,
        newAmount: Double
    ): Double {
        val delta = entries
            .filter { countsTowardBalance(it.status) && it.id != replacingId }
            .sumOf { sign(it.kind) * it.amount }
        return money(principal + delta + sign(kind) * newAmount)
    }

    fun clearingFits(outstandingAfter: Double): Boolean = outstandingAfter >= -EPS

    fun hasOpenChild(entries: List<LoanLedgerEntry>): Boolean =
        entries.any { isPending(it.status) || it.amendmentPending }

    /** Clearings already proposed but not yet approved. They are not in the balance, but they reserve the remaining amount. */
    fun reservedClearing(entries: List<LoanLedgerEntry>, exceptId: Long? = null): Double =
        money(
            entries.filter { it.id != exceptId && isPending(it.status) && it.kind == LoanLedgerKind.CLEARING }
                .sumOf { it.amount }
        )
}
