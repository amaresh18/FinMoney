package com.example.cloud

import android.content.Context

/**
 * Lightweight sync metadata (INDmoney / CRED style "last backed up" without a Room migration).
 * Also tracks user-deleted custom categories and tags to prevent resurrection during cloud restore.
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

    // --- Deleted Categories Tracking ---

    fun isCategoryDeleted(context: Context, userId: String, categoryName: String): Boolean {
        if (userId.isBlank() || categoryName.isBlank()) return false
        val set = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet("deleted_categories_$userId", emptySet()) ?: emptySet()
        return set.contains(categoryName.trim().lowercase())
    }

    fun markCategoryDeleted(context: Context, userId: String, categoryName: String) {
        if (userId.isBlank() || categoryName.isBlank()) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getStringSet("deleted_categories_$userId", emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(categoryName.trim().lowercase())
        prefs.edit().putStringSet("deleted_categories_$userId", current).apply()
    }

    fun unmarkCategoryDeleted(context: Context, userId: String, categoryName: String) {
        if (userId.isBlank() || categoryName.isBlank()) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getStringSet("deleted_categories_$userId", emptySet())?.toMutableSet() ?: return
        if (current.remove(categoryName.trim().lowercase())) {
            prefs.edit().putStringSet("deleted_categories_$userId", current).apply()
        }
    }

    // --- Deleted Tags Tracking ---

    fun isTagDeleted(context: Context, userId: String, tagName: String): Boolean {
        if (userId.isBlank() || tagName.isBlank()) return false
        val set = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet("deleted_tags_$userId", emptySet()) ?: emptySet()
        return set.contains(tagName.trim().lowercase())
    }

    fun markTagDeleted(context: Context, userId: String, tagName: String) {
        if (userId.isBlank() || tagName.isBlank()) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getStringSet("deleted_tags_$userId", emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(tagName.trim().lowercase())
        prefs.edit().putStringSet("deleted_tags_$userId", current).apply()
    }

    fun unmarkTagDeleted(context: Context, userId: String, tagName: String) {
        if (userId.isBlank() || tagName.isBlank()) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getStringSet("deleted_tags_$userId", emptySet())?.toMutableSet() ?: return
        if (current.remove(tagName.trim().lowercase())) {
            prefs.edit().putStringSet("deleted_tags_$userId", current).apply()
        }
    }

    // --- Deleted Ledger Entries Tracking ---
    fun isLedgerEntryDeleted(context: Context, userId: String, entryKey: String): Boolean {
        if (userId.isBlank() || entryKey.isBlank()) return false
        val set = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet("deleted_ledger_entries_$userId", emptySet()) ?: emptySet()
        return set.contains(entryKey.trim().lowercase())
    }

    fun markLedgerEntryDeleted(context: Context, userId: String, entryKey: String) {
        if (userId.isBlank() || entryKey.isBlank()) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getStringSet("deleted_ledger_entries_$userId", emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(entryKey.trim().lowercase())
        prefs.edit().putStringSet("deleted_ledger_entries_$userId", current).apply()
    }

    fun unmarkLedgerEntryDeleted(context: Context, userId: String, entryKey: String) {
        if (userId.isBlank() || entryKey.isBlank()) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getStringSet("deleted_ledger_entries_$userId", emptySet())?.toMutableSet() ?: return
        if (current.remove(entryKey.trim().lowercase())) {
            prefs.edit().putStringSet("deleted_ledger_entries_$userId", current).apply()
        }
    }
}
