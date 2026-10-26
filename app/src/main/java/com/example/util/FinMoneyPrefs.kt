package com.example.util

import android.content.Context

object FinMoneyPrefs {
    private const val NAME = "finmoney_prefs"
    private const val KEY_AUTO_TRACK_ONBOARDING_SEEN = "auto_track_onboarding_seen"
    private const val KEY_DAILY_REMINDER_ENABLED = "daily_reminder_1030_enabled"
    private const val KEY_SMS_INTERCEPTOR_ENABLED = "sms_interceptor_enabled"
    private const val KEY_INSTANT_ALERTS_ENABLED = "instant_alerts_enabled"
    private const val KEY_DUE_REMINDERS_ENABLED = "due_reminders_enabled"

    fun shouldShowAutoTrackOnboarding(context: Context): Boolean {
        return !context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_TRACK_ONBOARDING_SEEN, false)
    }

    fun markAutoTrackOnboardingSeen(context: Context) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_TRACK_ONBOARDING_SEEN, true)
            .apply()
    }

    fun isDailyReminderEnabled(context: Context): Boolean {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DAILY_REMINDER_ENABLED, true) // ON by default for all users
    }

    fun setDailyReminderEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DAILY_REMINDER_ENABLED, enabled)
            .apply()
    }

    fun isSmsInterceptorEnabled(context: Context): Boolean {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SMS_INTERCEPTOR_ENABLED, true) // ON by default for all users
    }

    fun setSmsInterceptorEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SMS_INTERCEPTOR_ENABLED, enabled)
            .apply()
    }

    fun isInstantAlertsEnabled(context: Context): Boolean {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_INSTANT_ALERTS_ENABLED, true) // ON by default for all users
    }

    fun setInstantAlertsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_INSTANT_ALERTS_ENABLED, enabled)
            .apply()
    }

    fun isDueRemindersEnabled(context: Context): Boolean {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DUE_REMINDERS_ENABLED, true) // ON by default for all users
    }

    fun setDueRemindersEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DUE_REMINDERS_ENABLED, enabled)
            .apply()
    }
}

