package com.example.util

import android.content.Context
import com.google.firebase.FirebaseApp

/**
 * Helps users align Firebase Console settings with the project baked into this APK.
 */
object FirebaseProjectDiagnostics {

    fun summary(context: Context): String {
        val pkg = context.packageName
        val (projectId, applicationId) = try {
            val options = FirebaseApp.getInstance().options
            Pair(options.projectId ?: "unknown", options.applicationId ?: "unknown")
        } catch (_: Exception) {
            Pair("not initialized", "unknown")
        }
        val apiKeyPresent = hasGoogleServicesApiKey(context)
        return buildString {
            append("Firebase project in this app: $projectId")
            append("\nAndroid package: $pkg")
            append("\nFirebase App ID: $applicationId")
            if (!apiKeyPresent) {
                append("\n⚠ google-services.json may be missing — add app/google-services.json from the same Firebase project and rebuild.")
            }
            append("\nIn that project: Authentication → Phone enabled, and Project settings → SHA-1 & SHA-256 for your signing key.")
        }
    }

    private fun hasGoogleServicesApiKey(context: Context): Boolean {
        val resId = context.resources.getIdentifier("google_api_key", "string", context.packageName)
        if (resId == 0) return false
        val key = runCatching { context.getString(resId) }.getOrNull() ?: return false
        return key.isNotBlank() &&
            !key.contains("placeholder", ignoreCase = true) &&
            !key.startsWith("AIzaSyCiPlaceholder")
    }

    fun rootCause(error: Throwable?): Throwable? {
        var current = error
        var depth = 0
        while (current != null && depth < 6) {
            if (current is com.google.firebase.auth.FirebaseAuthException) return current
            current = current.cause
            depth++
        }
        return error
    }
}
