package com.example.identity

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Global phone ↔ email registry in Firestore (authoritative across devices).
 *
 * identity_registry/phones/{10-digit} → { uid, email, updatedAt }
 * identity_registry/emails/{lowercase email} → { uid, phone, updatedAt }
 * identity_registry/users/{firebase uid} → { phone, email, phoneVerified, emailVerified, updatedAt }
 */
class UserIdentityRegistry(
    private val firestore: FirebaseFirestore = try {
        FirebaseFirestore.getInstance("ai-studio-android-e20fef11-4f95-4b1d-be45-fa8c63567bfb")
    } catch (_: Exception) {
        FirebaseFirestore.getInstance()
    }
) {
    private val root = firestore.collection("identity_registry")
    private val phones = root.document("phones").collection("entries")
    private val emails = root.document("emails").collection("entries")
    private val users = root.document("users").collection("entries")

    data class PhoneRegistryEntry(val uid: String, val email: String, val phoneVerified: Boolean = true)
    data class EmailRegistryEntry(val uid: String, val phone: String)

    suspend fun lookupPhone(phone10: String): PhoneRegistryEntry? {
        if (!UserIdentityNormalizer.isValidPhone10(phone10)) return null
        return try {
            val snap = phones.document(phone10).get().await()
            if (!snap.exists()) return null
            val uid = snap.getString("uid") ?: return null
            val isVerified = snap.getBoolean("phoneVerified") ?: true
            PhoneRegistryEntry(uid = uid, email = snap.getString("email") ?: "", phoneVerified = isVerified)
        } catch (e: Exception) {
            Log.w(TAG, "lookupPhone failed for $phone10: ${e.message}")
            null
        }
    }

    suspend fun lookupEmail(email: String): EmailRegistryEntry? {
        val clean = UserIdentityNormalizer.email(email)
        if (!UserIdentityNormalizer.isValidEmail(clean)) return null
        return try {
            val snap = emails.document(clean).get().await()
            if (!snap.exists()) return null
            val uid = snap.getString("uid") ?: return null
            EmailRegistryEntry(uid = uid, phone = snap.getString("phone") ?: "")
        } catch (e: Exception) {
            Log.w(TAG, "lookupEmail failed for $clean: ${e.message}")
            null
        }
    }

    suspend fun reservePhoneForUid(uid: String, phone10: String): Result<Unit> = runCatching {
        if (!UserIdentityNormalizer.isValidPhone10(phone10)) {
            throw IllegalArgumentException("Invalid phone number.")
        }
        firestore.runTransaction { tx ->
            val phoneRef = phones.document(phone10)
            val phoneSnap = tx.get(phoneRef)
            if (phoneSnap.exists()) {
                val owner = phoneSnap.getString("uid")
                val boundEmail = phoneSnap.getString("email") ?: ""
                if (owner != null && owner != uid) {
                    throw UserIdentityConflictException.phoneTaken(boundEmail.ifBlank { null })
                }
                if (boundEmail.isNotBlank()) {
                    // Phone already paired — only that uid+email may use it
                    val emailRef = emails.document(boundEmail)
                    val emailSnap = tx.get(emailRef)
                    val emailOwner = emailSnap.getString("uid")
                    if (emailOwner != null && emailOwner != uid) {
                        throw UserIdentityConflictException.phoneTaken(boundEmail)
                    }
                }
            }
            tx.set(
                phoneRef,
                mapOf(
                    "uid" to uid,
                    "email" to (phoneSnap.getString("email") ?: ""),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            val userRef = users.document(uid)
            tx.set(
                userRef,
                mapOf(
                    "phone" to phone10,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
        }.await()
    }.fold(
        onSuccess = { Result.success(Unit) },
        onFailure = { e ->
            Log.w(TAG, "reservePhoneForUid failed: ${e.message}")
            Result.failure(e)
        }
    )

    suspend fun commitVerifiedPair(
        uid: String,
        phone10: String,
        email: String,
        phoneVerified: Boolean,
        emailVerified: Boolean
    ): Result<Unit> = runCatching {
        val cleanPhone = UserIdentityNormalizer.phone10(phone10)
        val cleanEmail = UserIdentityNormalizer.email(email)
        if (!UserIdentityNormalizer.isValidPhone10(cleanPhone)) {
            throw IllegalArgumentException("Invalid phone number.")
        }
        if (!UserIdentityNormalizer.isValidEmail(cleanEmail)) {
            throw IllegalArgumentException("Invalid email address.")
        }

        firestore.runTransaction { tx ->
            val phoneRef = phones.document(cleanPhone)
            val emailRef = emails.document(cleanEmail)
            val userRef = users.document(uid)

            val phoneSnap = tx.get(phoneRef)
            val emailSnap = tx.get(emailRef)

            if (phoneSnap.exists()) {
                val owner = phoneSnap.getString("uid")
                val boundEmail = phoneSnap.getString("email") ?: ""
                if (owner != null && owner != uid) {
                    throw UserIdentityConflictException.phoneTaken(boundEmail.ifBlank { null })
                }
                if (boundEmail.isNotBlank() && boundEmail != cleanEmail) {
                    throw UserIdentityConflictException.phoneBoundToEmail(boundEmail)
                }
            }

            if (emailSnap.exists()) {
                val owner = emailSnap.getString("uid")
                val boundPhone = emailSnap.getString("phone") ?: ""
                if (owner != null && owner != uid) {
                    throw UserIdentityConflictException.emailTaken(boundPhone.ifBlank { null })
                }
                if (boundPhone.isNotBlank() && boundPhone != cleanPhone) {
                    throw UserIdentityConflictException.emailBoundToPhone(boundPhone)
                }
            }

            tx.set(
                phoneRef,
                mapOf(
                    "uid" to uid,
                    "email" to cleanEmail,
                    "phoneVerified" to phoneVerified,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            tx.set(
                emailRef,
                mapOf(
                    "uid" to uid,
                    "phone" to cleanPhone,
                    "emailVerified" to emailVerified,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            tx.set(
                userRef,
                mapOf(
                    "phone" to cleanPhone,
                    "email" to cleanEmail,
                    "phoneVerified" to phoneVerified,
                    "emailVerified" to emailVerified,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
        }.await()
    }.fold(
        onSuccess = { Result.success(Unit) },
        onFailure = { e ->
            Log.w(TAG, "commitVerifiedPair failed: ${e.message}")
            Result.failure(e)
        }
    )

    companion object {
        private const val TAG = "UserIdentityRegistry"
    }
}
