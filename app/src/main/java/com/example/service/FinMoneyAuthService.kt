package com.example.service

import android.app.Activity
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
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
                    completeOnce(Result.failure(e))
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
}
