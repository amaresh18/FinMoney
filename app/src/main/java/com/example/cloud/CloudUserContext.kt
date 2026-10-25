package com.example.cloud

import com.example.data.local.FinMoneyDao
import com.example.data.model.UserProfile
import com.example.identity.UserIdentityNormalizer
import com.example.service.FinMoneyAuthService
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

/**
 * Resolved owner for cloud + local financial data (Firebase uid is canonical).
 */
data class CloudUserContext(
    val userId: String,
    val phone10: String,
    val email: String,
    val canSyncToCloud: Boolean
) {
    fun ownerFields(): Map<String, Any> = buildMap {
        if (userId.isNotBlank()) put("userId", userId)
        if (phone10.isNotBlank()) put("userPhone", phone10)
        if (email.isNotBlank()) put("userEmail", email)
    }
}

object CloudUserContextResolver {

    suspend fun resolve(dao: FinMoneyDao): CloudUserContext {
        val fbUser = try {
            Firebase.auth.currentUser
        } catch (_: Throwable) {
            null
        }
        val profile = dao.getUserProfileDirect()
        val uid = fbUser?.uid?.takeIf { it.isNotBlank() }
            ?: profile?.userId?.takeIf { it.isNotBlank() && it != "user_default" }
            ?: ""
        val phone10 = FinMoneyAuthService.phoneDigitsFromFirebase(fbUser).ifBlank {
            UserIdentityNormalizer.phone10(profile?.phoneNumber ?: "")
        }
        val email = UserIdentityNormalizer.email(
            profile?.email ?: fbUser?.email ?: ""
        )
        val canSync = fbUser != null && uid.isNotBlank() && uid == fbUser.uid
        return CloudUserContext(
            userId = uid,
            phone10 = phone10,
            email = email,
            canSyncToCloud = canSync
        )
    }

    fun fromProfile(profile: UserProfile, firebaseUid: String?): CloudUserContext {
        val uid = firebaseUid?.takeIf { it.isNotBlank() } ?: profile.userId
        return CloudUserContext(
            userId = uid,
            phone10 = UserIdentityNormalizer.phone10(profile.phoneNumber),
            email = UserIdentityNormalizer.email(profile.email),
            canSyncToCloud = firebaseUid != null && uid == firebaseUid
        )
    }
}
