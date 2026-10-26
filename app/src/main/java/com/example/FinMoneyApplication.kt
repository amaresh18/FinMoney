package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FinMoneyApplication : Application() {
    companion object {
        private const val TAG = "FinMoneyAppInit"
    }

    override fun onCreate() {
        super.onCreate()

        try {
            val apps = FirebaseApp.getApps(this)
            val firebaseApp = if (apps.isEmpty()) {
                FirebaseApp.initializeApp(this)
            } else {
                apps[0]
            }
            
            if (firebaseApp != null) {
                Log.i(TAG, "✅ [DIAGNOSTIC] FirebaseApp successfully initialized! App Name: ${firebaseApp.name}, Project ID: ${firebaseApp.options.projectId}, Application ID: ${firebaseApp.options.applicationId}")
                
                try {
                    val auth = FirebaseAuth.getInstance()
                    Log.i(TAG, "✅ [DIAGNOSTIC] FirebaseAuth is available. Current User: ${auth.currentUser?.uid ?: "Not signed in"}")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ [DIAGNOSTIC] FirebaseAuth initialization check failed: ${e.message}", e)
                }

                try {
                    val firestoreDbId = try { getString(R.string.firestore_database_id) } catch (e: Exception) { "" }
                    val firestore = if (firestoreDbId.isNotBlank()) FirebaseFirestore.getInstance(firestoreDbId) else FirebaseFirestore.getInstance()
                    try {
                        firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
                            .setPersistenceEnabled(true)
                            .build()
                    } catch (e: Exception) {
                        Log.w(TAG, "Firestore offline persistence already configured: ${e.message}")
                    }
                    Log.i(TAG, "✅ [DIAGNOSTIC] FirebaseFirestore is available. Database: ${firestore.app.name}")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ [DIAGNOSTIC] FirebaseFirestore initialization check failed: ${e.message}", e)
                }

                try {
                    val crashlytics = com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance()
                    crashlytics.setCrashlyticsCollectionEnabled(false)
                    Log.i(TAG, "✅ [DIAGNOSTIC] FirebaseCrashlytics initialized safely.")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ [DIAGNOSTIC] FirebaseCrashlytics initialization skipped: ${e.message}")
                }
            } else {
                Log.w(TAG, "⚠️ [DIAGNOSTIC] FirebaseApp.initializeApp returned null.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ [DIAGNOSTIC] Exception during FirebaseApp initialization in Application.onCreate: ${e.message}", e)
        }

        // Verify local database health on app launch (No automatic cloud backup call; manual sync via Profile page)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = com.example.data.local.AppDatabase.getDatabase(this@FinMoneyApplication)
                val dao = db.finMoneyDao()
                com.example.data.local.DatabaseHealthGuard.verifyAndRepairDatabase(dao, forceReset = false)
            } catch (e: Exception) {
                Log.e(TAG, "Error running DatabaseHealthGuard on app launch", e)
            }
        }

        // Schedule daily 10:30 PM expense & income review reminder by default
        try {
            if (com.example.util.FinMoneyPrefs.isDailyReminderEnabled(this)) {
                com.example.service.DailyReminderReceiver.scheduleNext1030PmAlarm(this)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling daily 10:30 PM reminder: ${e.message}")
        }
    }
}
