package com.example.cloud

import android.util.Log
import com.example.data.model.UserProfile
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * User-scoped cloud documents under users/{firebaseUid}/...
 */
class UserCloudDataRepository(
    private val firestore: FirebaseFirestore = try {
        FirebaseFirestore.getInstance("ai-studio-android-e20fef11-4f95-4b1d-be45-fa8c63567bfb")
    } catch (_: Exception) {
        FirebaseFirestore.getInstance()
    }
) {

    suspend fun upsertUserProfileSnapshot(context: CloudUserContext, profile: UserProfile): Result<Unit> {
        if (!context.canSyncToCloud) {
            return Result.failure(IllegalStateException("Cloud sync requires a signed-in Firebase user."))
        }
        return try {
            val payload = hashMapOf<String, Any>(
                "userId" to context.userId,
                "name" to profile.name,
                "email" to context.email,
                "phone" to context.phone10,
                "phoneVerified" to profile.isOtpVerified,
                "emailVerified" to profile.isEmailVerified,
                "currencySymbol" to profile.currencySymbol,
                "monthlyBudget" to profile.monthlyBudget,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val merge = SetOptions.merge()
            firestore.collection("users").document(context.userId).set(payload, merge).await()
            firestore.collection("users").document(context.userId)
                .collection("account")
                .document("profile")
                .set(payload, merge)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Profile cloud upsert failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun writeUserDocument(
        context: CloudUserContext,
        subcollection: String,
        documentId: String,
        data: Map<String, Any?>,
        legacyCollections: List<String> = emptyList()
    ): Result<Unit> {
        if (!context.canSyncToCloud) {
            return Result.failure(IllegalStateException("Cloud sync requires a signed-in Firebase user."))
        }
        return try {
            val enriched = data.toMutableMap()
            enriched.putAll(context.ownerFields())
            enriched["cloudSyncedAt"] = System.currentTimeMillis()
            val merge = SetOptions.merge()
            firestore.collection("users").document(context.userId)
                .collection(subcollection)
                .document(documentId)
                .set(enriched, merge)
                .await()
            for (legacy in legacyCollections) {
                firestore.collection(legacy).document(documentId).set(enriched, merge).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "User cloud write failed ($subcollection/$documentId): ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun deleteUserDocument(
        context: CloudUserContext,
        subcollection: String,
        documentId: String,
        legacyCollections: List<String> = emptyList()
    ): Result<Unit> {
        if (!context.canSyncToCloud) {
            return Result.failure(IllegalStateException("Cloud sync requires a signed-in Firebase user."))
        }
        return try {
            firestore.collection("users").document(context.userId)
                .collection(subcollection)
                .document(documentId)
                .delete()
                .await()
            val tombstone = hashMapOf<String, Any>(
                "isDeleted" to true,
                "deletedAt" to System.currentTimeMillis()
            )
            firestore.collection("users").document(context.userId)
                .collection(subcollection)
                .document(documentId)
                .set(tombstone, SetOptions.merge())
                .await()
            for (legacy in legacyCollections) {
                try {
                    firestore.collection(legacy).document(documentId).delete().await()
                } catch (_: Exception) {}
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "User cloud delete failed ($subcollection/$documentId): ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun writePhoneInbox(phone10: String, documentId: String, data: Map<String, Any?>): Result<Unit> {
        if (phone10.length != 10 || documentId.isBlank()) {
            return Result.failure(IllegalArgumentException("Invalid inbox address"))
        }
        return try {
            firestore.collection("loan_inbox_by_phone")
                .document(phone10)
                .collection("items")
                .document(documentId)
                .set(data, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Phone inbox write failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun deletePhoneInboxItem(phone10: String, documentId: String): Result<Unit> {
        if (phone10.length != 10 || documentId.isBlank()) return Result.success(Unit)
        return try {
            firestore.collection("loan_inbox_by_phone")
                .document(phone10)
                .collection("items")
                .document(documentId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Phone inbox delete failed ($phone10/$documentId): ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun listUserSubcollection(userId: String, subcollection: String): List<Pair<String, Map<String, Any?>>> {
        if (userId.isBlank()) return emptyList()
        return try {
            val snap = firestore.collection("users").document(userId)
                .collection(subcollection)
                .get()
                .await()
            snap.documents.map { doc -> doc.id to (doc.data ?: emptyMap()) }
        } catch (e: Exception) {
            Log.w(TAG, "List $subcollection failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun listPhoneInbox(phone10: String): List<Map<String, Any?>> {
        if (phone10.length != 10) return emptyList()
        return try {
            val snap = firestore.collection("loan_inbox_by_phone")
                .document(phone10)
                .collection("items")
                .get()
                .await()
            snap.documents.map { doc ->
                val map = doc.data.orEmpty().toMutableMap()
                if (!map.containsKey("inviteId")) map["inviteId"] = doc.id
                map
            }
        } catch (e: Exception) {
            Log.w(TAG, "Phone inbox list failed: ${e.message}")
            emptyList()
        }
    }

    companion object {
        private const val TAG = "UserCloudDataRepository"
    }
}
