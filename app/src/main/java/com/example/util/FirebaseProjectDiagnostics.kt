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

    data class CertificateFingerprints(
        val sha1: String,
        val sha256: String
    )

    fun getSigningFingerprints(context: Context): CertificateFingerprints? {
        return try {
            val pm = context.packageManager
            val pkg = context.packageName
            val signatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(pkg, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
                info.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                val info = pm.getPackageInfo(pkg, android.content.pm.PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                info.signatures
            }
            val certBytes = signatures?.firstOrNull()?.toByteArray() ?: return null
            val sha1 = java.security.MessageDigest.getInstance("SHA-1").digest(certBytes).joinToString(":") { "%02X".format(it) }
            val sha256 = java.security.MessageDigest.getInstance("SHA-256").digest(certBytes).joinToString(":") { "%02X".format(it) }
            CertificateFingerprints(sha1 = sha1, sha256 = sha256)
        } catch (_: Exception) {
            null
        }
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
