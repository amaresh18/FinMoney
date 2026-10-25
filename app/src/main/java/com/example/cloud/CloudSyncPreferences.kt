package com.example.cloud

import android.content.Context

/**
 * Lightweight sync metadata (INDmoney / CRED style "last backed up" without a Room migration).
 */
object CloudSyncPreferences {
    private const val PREFS = "finmoney_cloud_sync"

    fun lastSuccessfulSyncAt(context: Context, userId: String): Long {
        if (userId.isBlank()) return 0L
        return context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong("last_sync_$userId", 0L)
    }

    fun setLastSuccessfulSyncAt(context: Context, userId: String, at: Long = System.currentTimeMillis()) {
        if (userId.isBlank()) return
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong("last_sync_$userId", at)
            .apply()
    }
}
