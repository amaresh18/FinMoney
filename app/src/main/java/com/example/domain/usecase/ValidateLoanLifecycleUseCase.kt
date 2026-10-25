package com.example.domain.usecase

import com.example.data.model.LoanApprovalStatus

/**
 * Enforces strict party authorization (creator vs counterparty) and transition checks.
 */
class ValidateLoanLifecycleUseCase {

    sealed interface ValidationResult {
        data object Valid : ValidationResult
        data class Denied(val reason: String) : ValidationResult
    }

    private fun isPendingApproval(status: LoanApprovalStatus): Boolean =
        status == LoanApprovalStatus.PENDING || status == LoanApprovalStatus.PENDING_APPROVAL

    fun validateApprovalPermission(
        creatorPhone: String,
        counterpartyPhone: String,
        userPhone: String,
        creatorUserId: String,
        activeUserId: String,
        currentStatus: LoanApprovalStatus
    ): ValidationResult {
        val cleanUser = LoanPartyAuth.phone10(userPhone)
        if (cleanUser.length != 10) {
            return ValidationResult.Denied("Add your verified mobile number in profile before approving loans.")
        }
        if (!isPendingApproval(currentStatus)) {
            return ValidationResult.Denied("Record is already ${currentStatus.name}. Cannot approve again.")
        }
        if (LoanPartyAuth.isCreator(creatorPhone, userPhone, creatorUserId, activeUserId)) {
            return ValidationResult.Denied("You created this record. Only the counterparty can approve.")
        }
        if (!LoanPartyAuth.isCounterparty(counterpartyPhone, userPhone)) {
            return ValidationResult.Denied("Only the counterparty (${LoanPartyAuth.phone10(counterpartyPhone)}) can approve this request.")
        }
        return ValidationResult.Valid
    }

    fun validateRejectPermission(
        creatorPhone: String,
        counterpartyPhone: String,
        userPhone: String,
        creatorUserId: String,
        activeUserId: String,
        currentStatus: LoanApprovalStatus
    ): ValidationResult {
        if (!isPendingApproval(currentStatus)) {
            return ValidationResult.Denied("Record is not pending approval.")
        }
        val isCreator = LoanPartyAuth.isCreator(creatorPhone, userPhone, creatorUserId, activeUserId)
        val isCounterparty = LoanPartyAuth.isCounterparty(counterpartyPhone, userPhone)
        return if (isCounterparty || isCreator) {
            ValidationResult.Valid
        } else {
            ValidationResult.Denied("Only the creator or counterparty can reject this request.")
        }
    }

    fun validateSettlementRequest(
        creatorPhone: String,
        userPhone: String,
        creatorUserId: String,
        activeUserId: String,
        currentStatus: LoanApprovalStatus,
        amendmentPending: Boolean = false
    ): ValidationResult {
        if (currentStatus != LoanApprovalStatus.MUTUAL_APPROVED && currentStatus != LoanApprovalStatus.APPROVED) {
            return ValidationResult.Denied("Settlement can only be requested after mutual agreement.")
        }
        if (amendmentPending) {
            return ValidationResult.Denied("A change is still waiting for the counterparty. Settle only after that change is approved or withdrawn.")
        }
        if (!LoanPartyAuth.isCreator(creatorPhone, userPhone, creatorUserId, activeUserId)) {
            return ValidationResult.Denied("Only the record creator can request settlement.")
        }
        return ValidationResult.Valid
    }

    /**
     * Drafts and rejected requests can be removed (wrong number).
     * An open mutual agreement cannot be deleted by one side.
     * After both parties confirm settlement, the creator may remove the closed record.
     */
    fun canCreatorDelete(status: LoanApprovalStatus): Boolean =
        isPendingApproval(status) ||
            status == LoanApprovalStatus.REJECTED ||
            status == LoanApprovalStatus.SETTLED

    fun validateDeletePermission(
        creatorPhone: String,
        userPhone: String,
        creatorUserId: String,
        activeUserId: String,
        currentStatus: LoanApprovalStatus
    ): ValidationResult {
        if (!LoanPartyAuth.isCreator(creatorPhone, userPhone, creatorUserId, activeUserId)) {
            return ValidationResult.Denied("Only the creator can delete this record.")
        }
        if (!canCreatorDelete(currentStatus)) {
            return ValidationResult.Denied(
                "This is an open mutual agreement. It can be deleted only before the counterparty approves, or after both parties confirm settlement."
            )
        }
        return ValidationResult.Valid
    }

    fun validateAmendmentApproval(
        creatorPhone: String,
        counterpartyPhone: String,
        userPhone: String,
        creatorUserId: String,
        activeUserId: String,
        amendmentPending: Boolean
    ): ValidationResult {
        val cleanUser = LoanPartyAuth.phone10(userPhone)
        if (cleanUser.length != 10) {
            return ValidationResult.Denied("Add your verified mobile number in profile before approving changes.")
        }
        if (!amendmentPending) {
            return ValidationResult.Denied("There is no change waiting for approval.")
        }
        if (LoanPartyAuth.isCreator(creatorPhone, userPhone, creatorUserId, activeUserId)) {
            return ValidationResult.Denied("You proposed this change. Only the counterparty can approve it.")
        }
        if (!LoanPartyAuth.isCounterparty(counterpartyPhone, userPhone)) {
            return ValidationResult.Denied("Only the counterparty can approve this change.")
        }
        return ValidationResult.Valid
    }

    fun validateSettlementConfirm(
        creatorPhone: String,
        counterpartyPhone: String,
        userPhone: String,
        creatorUserId: String,
        activeUserId: String,
        currentStatus: LoanApprovalStatus
    ): ValidationResult {
        if (currentStatus != LoanApprovalStatus.PENDING_SETTLEMENT) {
            return ValidationResult.Denied("No settlement is pending confirmation.")
        }
        if (LoanPartyAuth.isCreator(creatorPhone, userPhone, creatorUserId, activeUserId)) {
            return ValidationResult.Denied("Creator cannot confirm settlement. Counterparty must confirm.")
        }
        if (!LoanPartyAuth.isCounterparty(counterpartyPhone, userPhone)) {
            return ValidationResult.Denied("Only the counterparty can confirm settlement.")
        }
        return ValidationResult.Valid
    }

    fun calculateTotalPayable(principal: Double, interestPercent: Double, tenureMonths: Int): Double {
        if (principal <= 0.0) return 0.0
        if (interestPercent <= 0.0 || tenureMonths <= 0) return principal
        val interest = (principal * (interestPercent / 100.0) * (tenureMonths / 12.0)).coerceAtLeast(0.0)
        return principal + interest
    }
}
