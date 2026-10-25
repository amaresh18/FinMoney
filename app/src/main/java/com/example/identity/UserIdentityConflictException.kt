package com.example.identity

/**
 * User-facing identity binding conflicts (INDmoney / CRED style 1:1 phone ↔ email).
 */
class UserIdentityConflictException(
    val code: Code,
    override val message: String
) : Exception(message) {

    enum class Code {
        PHONE_TAKEN,
        EMAIL_TAKEN,
        PHONE_BOUND_TO_OTHER_EMAIL,
        EMAIL_BOUND_TO_OTHER_PHONE,
        PAIR_MISMATCH,
        OFFLINE_REGISTRY_REQUIRED
    }

    companion object {
        fun phoneTaken(maskedEmail: String?): UserIdentityConflictException {
            val detail = maskedEmail?.takeIf { it.isNotBlank() }?.let { " It is linked to $it." } ?: ""
            return UserIdentityConflictException(
                Code.PHONE_TAKEN,
                "This mobile number is already registered on FinMoney.$detail Sign in with that account or use account recovery."
            )
        }

        fun emailTaken(maskedPhone: String?): UserIdentityConflictException {
            val detail = maskedPhone?.takeIf { it.isNotBlank() }?.let { " It is linked to +91 $it." } ?: ""
            return UserIdentityConflictException(
                Code.EMAIL_TAKEN,
                "This email is already registered on FinMoney.$detail Sign in with that mobile number."
            )
        }

        fun phoneBoundToEmail(email: String): UserIdentityConflictException =
            UserIdentityConflictException(
                Code.PHONE_BOUND_TO_OTHER_EMAIL,
                "This mobile number is linked to $email. You cannot use a different email with this number."
            )

        fun emailBoundToPhone(phone10: String): UserIdentityConflictException =
            UserIdentityConflictException(
                Code.EMAIL_BOUND_TO_OTHER_PHONE,
                "This email is linked to +91 $phone10. You cannot use a different mobile number with this email."
            )
    }
}
