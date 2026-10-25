package com.example.util

import android.content.Context

object FinMoneyPrefs {
    private const val NAME = "finmoney_prefs"
    private const val KEY_AUTO_TRACK_ONBOARDING_SEEN = "auto_track_onboarding_seen"

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
}
