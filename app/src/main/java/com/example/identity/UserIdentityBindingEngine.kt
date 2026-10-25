package com.example.identity

import android.content.Context
import com.example.data.local.FinMoneyDao
import com.example.util.NetworkReachabilityHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

/**
 * Enforces strict 1:1 phone ↔ email binding locally and via Firestore (CRED / INDmoney pattern).
 */
class UserIdentityBindingEngine(
    private val appContext: Context,
    private val dao: FinMoneyDao,
    private val registry: UserIdentityRegistry = UserIdentityRegistry()
) {

    enum class BindPhase {
        /** Before sending phone OTP — email may be empty. */
        PRE_PHONE_OTP,
        /** Before attaching / verifying email — phone should already be verified for this uid. */
        PRE_EMAIL_BIND,
        /** Final save when both identifiers are known. */
        COMMIT_PAIR
    }

    suspend fun assertCanBind(
        phone: String,
        email: String,
        phase: BindPhase,
        uid: String? = currentUid()
    ): Result<Unit> {
        val cleanPhone = UserIdentityNormalizer.phone10(phone)
        val cleanEmail = UserIdentityNormalizer.email(email)

        assertLocalBinding(cleanPhone, cleanEmail, uid, phase).getOrElse { return Result.failure(it) }

        if (!NetworkReachabilityHelper.isNetworkAvailable(appContext)) {
            if (phase == BindPhase.COMMIT_PAIR) {
                return Result.failure(
                    UserIdentityConflictException(
                        UserIdentityConflictException.Code.OFFLINE_REGISTRY_REQUIRED,
                        "Internet is required to secure your FinMoney account. Please connect and try again."
                    )
                )
            }
            return Result.success(Unit)
        }

        return assertRemoteBinding(cleanPhone, cleanEmail, uid, phase)
    }

    suspend fun reservePhoneAfterOtp(phone: String): Result<Unit> {
        val uid = currentUid() ?: return Result.failure(IllegalStateException("Sign-in session missing after OTP."))
        val cleanPhone = UserIdentityNormalizer.phone10(phone)
        if (!UserIdentityNormalizer.isValidPhone10(cleanPhone)) {
            return Result.failure(IllegalArgumentException("Invalid phone number."))
        }
        assertLocalBinding(cleanPhone, "", uid, BindPhase.PRE_PHONE_OTP).getOrElse { return Result.failure(it) }
        if (!NetworkReachabilityHelper.isNetworkAvailable(appContext)) {
            return Result.failure(
                UserIdentityConflictException(
                    UserIdentityConflictException.Code.OFFLINE_REGISTRY_REQUIRED,
                    "Internet is required to verify your mobile number. Please connect and try again."
                )
            )
        }
        return registry.reservePhoneForUid(uid, cleanPhone)
    }

    suspend fun commitVerifiedPair(
        phone: String,
        email: String,
        phoneVerified: Boolean,
        emailVerified: Boolean,
        uid: String? = currentUid()
    ): Result<Unit> {
        val resolvedUid = uid ?: return Result.failure(IllegalStateException("No signed-in user."))
        val cleanPhone = UserIdentityNormalizer.phone10(phone)
        val cleanEmail = UserIdentityNormalizer.email(email)
        assertCanBind(cleanPhone, cleanEmail, BindPhase.COMMIT_PAIR, resolvedUid).getOrElse { return Result.failure(it) }
        return registry.commitVerifiedPair(resolvedUid, cleanPhone, cleanEmail, phoneVerified, emailVerified)
    }

    private suspend fun assertLocalBinding(
        cleanPhone: String,
        cleanEmail: String,
        uid: String?,
        phase: BindPhase
    ): Result<Unit> {
        if (cleanPhone.isNotBlank() && UserIdentityNormalizer.isValidPhone10(cleanPhone)) {
            val byPhone = dao.getUserByPhone(cleanPhone)
            if (byPhone != null) {
                val ownerUid = byPhone.userId
                val boundEmail = byPhone.email.trim().lowercase()
                if (uid != null && ownerUid == uid) {
                    if (cleanEmail.isNotBlank() && boundEmail.isNotBlank() && boundEmail != cleanEmail) {
                        return Result.failure(UserIdentityConflictException.phoneBoundToEmail(boundEmail))
                    }
                } else {
                    if (boundEmail.isNotBlank()) {
                        if (cleanEmail.isNotBlank() && boundEmail != cleanEmail) {
                            return Result.failure(UserIdentityConflictException.phoneBoundToEmail(boundEmail))
                        }
                        return Result.failure(UserIdentityConflictException.phoneTaken(boundEmail))
                    }
                    if (uid == null || ownerUid != uid) {
                        return Result.failure(UserIdentityConflictException.phoneTaken(byPhone.email.ifBlank { null }))
                    }
                }
            }
        }

        if (cleanEmail.isNotBlank() && UserIdentityNormalizer.isValidEmail(cleanEmail)) {
            val byEmail = dao.getUserByEmail(cleanEmail)
            if (byEmail != null) {
                val ownerUid = byEmail.userId
                val boundPhone = UserIdentityNormalizer.phone10(byEmail.phoneNumber)
                if (uid != null && ownerUid == uid) {
                    if (cleanPhone.isNotBlank() && boundPhone.isNotBlank() && boundPhone != cleanPhone) {
                        return Result.failure(UserIdentityConflictException.emailBoundToPhone(boundPhone))
                    }
                } else {
                    if (boundPhone.isNotBlank()) {
                        if (cleanPhone.isNotBlank() && boundPhone != cleanPhone) {
                            return Result.failure(UserIdentityConflictException.emailBoundToPhone(boundPhone))
                        }
                        return Result.failure(UserIdentityConflictException.emailTaken(boundPhone))
                    }
                    if (uid == null || ownerUid != uid) {
                        return Result.failure(UserIdentityConflictException.emailTaken(byEmail.phoneNumber.ifBlank { null }))
                    }
                }
            }
        }

        if (phase == BindPhase.COMMIT_PAIR || phase == BindPhase.PRE_EMAIL_BIND) {
            if (!UserIdentityNormalizer.isValidPhone10(cleanPhone)) {
                return Result.failure(IllegalArgumentException("Verify your mobile number first."))
            }
            if (phase == BindPhase.COMMIT_PAIR && !UserIdentityNormalizer.isValidEmail(cleanEmail)) {
                return Result.failure(IllegalArgumentException("Enter a valid email address."))
            }
        }

        return Result.success(Unit)
    }

    private suspend fun assertRemoteBinding(
        cleanPhone: String,
        cleanEmail: String,
        uid: String?,
        phase: BindPhase
    ): Result<Unit> {
        if (cleanPhone.isNotBlank() && UserIdentityNormalizer.isValidPhone10(cleanPhone)) {
            val remotePhone = registry.lookupPhone(cleanPhone)
            if (remotePhone != null) {
                if (uid != null && remotePhone.uid == uid) {
                    if (cleanEmail.isNotBlank() && remotePhone.email.isNotBlank() && remotePhone.email != cleanEmail) {
                        return Result.failure(UserIdentityConflictException.phoneBoundToEmail(remotePhone.email))
                    }
                } else {
                    if (remotePhone.email.isNotBlank()) {
                        if (cleanEmail.isNotBlank() && remotePhone.email != cleanEmail) {
                            return Result.failure(UserIdentityConflictException.phoneBoundToEmail(remotePhone.email))
                        }
                        return Result.failure(UserIdentityConflictException.phoneTaken(remotePhone.email))
                    }
                    if (uid == null || remotePhone.uid != uid) {
                        return Result.failure(UserIdentityConflictException.phoneTaken(null))
                    }
                }
            }
        }

        if (cleanEmail.isNotBlank() && UserIdentityNormalizer.isValidEmail(cleanEmail)) {
            val remoteEmail = registry.lookupEmail(cleanEmail)
            if (remoteEmail != null) {
                if (uid != null && remoteEmail.uid == uid) {
                    if (cleanPhone.isNotBlank() && remoteEmail.phone.isNotBlank() && remoteEmail.phone != cleanPhone) {
                        return Result.failure(UserIdentityConflictException.emailBoundToPhone(remoteEmail.phone))
                    }
                } else {
                    if (remoteEmail.phone.isNotBlank()) {
                        if (cleanPhone.isNotBlank() && remoteEmail.phone != cleanPhone) {
                            return Result.failure(UserIdentityConflictException.emailBoundToPhone(remoteEmail.phone))
                        }
                        return Result.failure(UserIdentityConflictException.emailTaken(remoteEmail.phone))
                    }
                    if (uid == null || remoteEmail.uid != uid) {
                        return Result.failure(UserIdentityConflictException.emailTaken(null))
                    }
                }
            }
        }

        return Result.success(Unit)
    }

    private fun currentUid(): String? = try {
        Firebase.auth.currentUser?.uid
    } catch (_: Throwable) {
        null
    }
}
