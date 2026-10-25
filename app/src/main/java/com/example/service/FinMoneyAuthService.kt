package com.example.service

import android.app.Activity
import android.content.Context
import android.util.Log
import com.example.util.FirebaseProjectDiagnostics
import com.google.firebase.FirebaseApp
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Firebase-backed phone OTP, email verification, and Google account linking.
 */
object FinMoneyAuthService {

    private const val TAG = "FinMoneyAuthService"

    private val auth: FirebaseAuth
        get() = Firebase.auth

    @Volatile
    var lastForceResendingToken: PhoneAuthProvider.ForceResendingToken? = null

    data class PhoneOtpDispatchResult(
        val verificationId: String?,
        val instantSignIn: Boolean
    )

    suspend fun sendPhoneOtp(
        activity: Activity,
        phone10Digits: String,
        forceResend: Boolean = false
    ): Result<PhoneOtpDispatchResult> {
        val digits = phone10Digits.filter { it.isDigit() }.takeLast(10)
        if (digits.length != 10) {
            return Result.failure(IllegalArgumentException("Enter a valid 10-digit mobile number."))
        }
        val e164 = "+91$digits"

        if (FirebaseApp.getApps(activity.applicationContext).isEmpty()) {
            return Result.failure(
                IllegalStateException("Firebase is not configured on this build. Please reinstall from Play Store or contact support.")
            )
        }

        return suspendCancellableCoroutine { cont ->
            var completed = false
            fun completeOnce(result: Result<PhoneOtpDispatchResult>) {
                if (!completed && cont.isActive) {
                    completed = true
                    cont.resume(result)
                }
            }

            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    if (!cont.isActive) return
                    val existing = auth.currentUser
                    val task = if (existing != null && existing.phoneNumber.isNullOrBlank()) {
                        existing.linkWithCredential(credential)
                    } else {
                        auth.signInWithCredential(credential)
                    }
                    task.addOnCompleteListener { result ->
                        if (result.isSuccessful) {
                            completeOnce(Result.success(PhoneOtpDispatchResult(verificationId = null, instantSignIn = true)))
                        } else {
                            completeOnce(Result.failure(result.exception ?: IllegalStateException("Phone sign-in failed")))
                        }
                    }
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    Log.e(TAG, "Phone verification failed: ${e.message}", e)
                    completeOnce(Result.failure(IllegalStateException(userMessageForPhoneAuthError(e))))
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    lastForceResendingToken = token
                    completeOnce(Result.success(PhoneOtpDispatchResult(verificationId = verificationId, instantSignIn = false)))
                }
            }

            val builder = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(e164)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)

            if (forceResend && lastForceResendingToken != null) {
                builder.setForceResendingToken(lastForceResendingToken!!)
            }

            try {
                PhoneAuthProvider.verifyPhoneNumber(builder.build())
            } catch (e: Exception) {
                Log.e(TAG, "verifyPhoneNumber failed to start: ${e.message}", e)
                completeOnce(Result.failure(e))
            }
        }
    }

    suspend fun verifyPhoneOtpCode(verificationId: String, otpCode: String): Result<FirebaseUser> {
        if (verificationId.isBlank()) {
            return Result.failure(IllegalArgumentException("Missing verification session. Please resend OTP."))
        }
        val code = otpCode.filter { it.isDigit() }
        if (code.length != 6) {
            return Result.failure(IllegalArgumentException("Enter the 6-digit OTP from SMS."))
        }
        return try {
            val credential = PhoneAuthProvider.getCredential(verificationId, code)
            signInOrLinkPhoneCredential(credential)
        } catch (e: Exception) {
            Log.e(TAG, "OTP confirmation failed", e)
            Result.failure(e)
        }
    }

    private suspend fun signInOrLinkPhoneCredential(credential: PhoneAuthCredential): Result<FirebaseUser> {
        val existing = auth.currentUser
        return try {
            val result = if (existing != null && existing.phoneNumber.isNullOrBlank()) {
                existing.linkWithCredential(credential).await()
            } else {
                auth.signInWithCredential(credential).await()
            }
            val user = result.user ?: return Result.failure(IllegalStateException("Phone sign-in returned no user."))
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun linkGoogleIdToken(idToken: String): Result<FirebaseUser> {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        return try {
            val existing = auth.currentUser
            val result = if (existing != null) {
                existing.linkWithCredential(credential).await()
            } else {
                auth.signInWithCredential(credential).await()
            }
            val user = result.user ?: return Result.failure(IllegalStateException("Google sign-in returned no user."))
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Google link/sign-in failed", e)
            Result.failure(e)
        }
    }

    suspend fun updateEmailAndSendVerification(email: String): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Sign in before adding email."))
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            return Result.failure(IllegalArgumentException("Enter a valid email address."))
        }
        return try {
            if (!user.email.equals(cleanEmail, ignoreCase = true)) {
                user.updateEmail(cleanEmail).await()
            }
            if (!user.isEmailVerified) {
                user.sendEmailVerification().await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Email verification dispatch failed", e)
            Result.failure(e)
        }
    }

    suspend fun reloadEmailVerified(): Result<Boolean> {
        val user = auth.currentUser ?: return Result.success(false)
        return try {
            user.reload().await()
            Result.success(auth.currentUser?.isEmailVerified == true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun currentFirebaseUser(): FirebaseUser? = auth.currentUser

    fun phoneDigitsFromFirebase(user: FirebaseUser?): String {
        val raw = user?.phoneNumber?.takeIf { it.isNotBlank() }
            ?: user?.providerData?.firstNotNullOfOrNull { it.phoneNumber?.takeIf { p -> p.isNotBlank() } }
            ?: ""
        return raw.filter { it.isDigit() }.takeLast(10)
    }

    /** True only when Firebase Auth has a linked phone provider (SMS OTP completed). */
    fun isPhoneLinkedOnFirebase(user: FirebaseUser?): Boolean {
        if (user == null) return false
        return user.providerData.any { provider ->
            provider.providerId == PhoneAuthProvider.PROVIDER_ID
        }
    }

    fun isEmailVerifiedOnFirebase(user: FirebaseUser?): Boolean = user?.isEmailVerified == true

    fun isPhoneAuthQuotaError(error: Throwable?): Boolean {
        val root = FirebaseProjectDiagnostics.rootCause(error) ?: error
        val authEx = root as? FirebaseAuthException
        if (authEx?.errorCode == "ERROR_QUOTA_EXCEEDED") return true
        val msg = root?.message.orEmpty()
        return msg.contains("quota", ignoreCase = true) &&
            msg.contains("exceed", ignoreCase = true)
    }

    fun userMessageForPhoneAuthError(error: Throwable?, context: Context? = null): String {
        val root = FirebaseProjectDiagnostics.rootCause(error) ?: error
        if (root == null) return "Could not send OTP. Please try again."
        if (isPhoneAuthQuotaError(root)) {
            return (
                "SMS OTP limit reached for this Firebase project (daily quota). " +
                    "Wait until the limit resets, upgrade billing in Firebase Console, or add your number under " +
                    "Authentication → Sign-in method → Phone → Phone numbers for testing (fixed OTP, no SMS)."
                )
        }
        val authEx = root as? FirebaseAuthException
        val core = when (authEx?.errorCode) {
            "ERROR_OPERATION_NOT_ALLOWED" -> (
                "Firebase rejected phone OTP. Phone must be enabled on the same Firebase project this app uses " +
                    "(not a different project in the console), and you must add SHA-1 & SHA-256 for package " +
                    "com.aistudio.finmoney.vqpz under Project settings → Your apps."
                )
            "ERROR_INVALID_PHONE_NUMBER" -> "Enter a valid 10-digit Indian mobile number."
            "ERROR_TOO_MANY_REQUESTS" -> "Too many OTP attempts. Please wait a few minutes and try again."
            "ERROR_QUOTA_EXCEEDED" -> "OTP service is busy. Please try again later."
            "ERROR_APP_NOT_AUTHORIZED" -> (
                "This app is not authorized for Firebase Auth. Add your signing SHA-1 and SHA-256 in Firebase " +
                    "Project settings for com.aistudio.finmoney.vqpz, then reinstall."
                )
            "ERROR_INVALID_API_KEY" -> "Firebase API key is invalid. Replace app/google-services.json from your Firebase project and rebuild."
            else -> {
                val msg = root.message.orEmpty()
                when {
                    msg.contains("OPERATION_NOT_ALLOWED", ignoreCase = true) ||
                        msg.contains("sign-in provider is disabled", ignoreCase = true) -> (
                        "Firebase rejected phone OTP. Enable Phone on the project this APK uses and verify SHA-1 & SHA-256 keys " +
                            "(see diagnostics below). If you enabled Phone on another Firebase project, link this app to that project via google-services.json."
                        )
                    msg.contains("not authorized", ignoreCase = true) -> (
                        "App not authorized for Firebase. Add SHA-1/SHA-256 for your signing certificate in Firebase Console."
                        )
                    else -> root.localizedMessage?.takeIf { it.isNotBlank() }
                        ?: msg.ifBlank { "Could not send OTP. Please try again." }
                }
            }
        }
        if (context == null) return core
        val needsDiag = authEx?.errorCode == "ERROR_OPERATION_NOT_ALLOWED" ||
            authEx?.errorCode == "ERROR_APP_NOT_AUTHORIZED" ||
            core.contains("Firebase project", ignoreCase = true) ||
            core.contains("google-services", ignoreCase = true)
        return if (needsDiag) "$core\n\n${FirebaseProjectDiagnostics.summary(context)}" else core
    }
}
