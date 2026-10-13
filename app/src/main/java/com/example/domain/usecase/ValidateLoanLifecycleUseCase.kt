package com.example.domain.usecase

import com.example.data.model.LoanApprovalStatus

/**
 * Pure Kotlin Use Case to validate loan record lifecycle actions.
 * Enforces strict party authorization (creator vs counterparty) and transition checks.
 */
class ValidateLoanLifecycleUseCase {

    sealed interface ValidationResult {
        data object Valid : ValidationResult
        data class Denied(val reason: String) : ValidationResult
    }

    /**
     * Verifies if a user identified by [userPhone] is authorized to approve or reject a loan.
     */
    fun validateApprovalPermission(
        creatorPhone: String,
        counterpartyPhone: String,
        userPhone: String,
        currentStatus: LoanApprovalStatus
    ): ValidationResult {
        val cleanUser = userPhone.trim().filter { it.isDigit() }
        val cleanCounter = counterpartyPhone.trim().filter { it.isDigit() }
        val cleanCreator = creatorPhone.trim().filter { it.isDigit() }

        if (cleanUser.isEmpty()) {
            return ValidationResult.Denied("User phone number not authenticated.")
        }

        if (currentStatus != LoanApprovalStatus.PENDING) {
            return ValidationResult.Denied("Record is already ${currentStatus.name}. Cannot re-evaluate.")
        }

        val isCounterparty = cleanUser.endsWith(cleanCounter) || cleanCounter.endsWith(cleanUser)
        val isCreator = cleanUser.endsWith(cleanCreator) || cleanCreator.endsWith(cleanUser)

        return if (isCounterparty) {
            ValidationResult.Valid
        } else if (isCreator) {
            ValidationResult.Denied("Only the counterparty can approve or reject this request. Creator cannot self-approve.")
        } else {
            ValidationResult.Denied("Unauthorized user. Neither counterparty nor creator.")
        }
    }

    /**
     * Calculates the total repayment amount with interest.
     */
    fun calculateTotalPayable(principal: Double, interestPercent: Double, tenureMonths: Int): Double {
        if (principal <= 0.0) return 0.0
        if (interestPercent <= 0.0 || tenureMonths <= 0) return principal
        val interest = (principal * (interestPercent / 100.0) * (tenureMonths / 12.0)).coerceAtLeast(0.0)
        return principal + interest
    }
}
