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
            } else {
                Log.w(TAG, "⚠️ [DIAGNOSTIC] FirebaseApp.initializeApp returned null.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ [DIAGNOSTIC] Exception during FirebaseApp initialization in Application.onCreate: ${e.message}", e)
        }

        // After Firebase is ready: repair local DB, then pull cloud backup + flush outbox (premium cold-start sync).
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = com.example.data.local.AppDatabase.getDatabase(this@FinMoneyApplication)
                val dao = db.finMoneyDao()
                com.example.data.local.DatabaseHealthGuard.verifyAndRepairDatabase(dao, forceReset = false)
                com.example.cloud.UserCloudBackupOrchestrator.runFullAccountSync(
                    this@FinMoneyApplication,
                    dao
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error running DatabaseHealthGuard on app launch", e)
            }
        }
    }
}
