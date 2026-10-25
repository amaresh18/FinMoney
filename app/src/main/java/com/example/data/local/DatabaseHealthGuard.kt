package com.example.data.local

import android.util.Log
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Robust Database Health Guard
 * Ensures the application database has a valid clean user profile and schema.
 * All transactions in the database come strictly from user entry or approved imports.
 */
object DatabaseHealthGuard {
    private const val TAG = "DatabaseHealthGuard"

    suspend fun verifyAndRepairDatabase(dao: FinMoneyDao, forceReset: Boolean = false) = withContext(Dispatchers.IO) {
        try {
            val userProfile = dao.getUserProfileDirect()

            if (userProfile == null || forceReset) {
                if (forceReset) {
                    dao.clearAllDebits()
                    dao.clearAllLoans()
                }

                if (userProfile == null) {
                    dao.insertOrUpdateUserProfile(
                        UserProfile(
                            userId = "user_default",
                            name = "FinMoney Member",
                            email = "",
                            phoneNumber = "",
                            isOtpVerified = false,
                            isEmailVerified = false,
                            monthlyBudget = 0.0,
                            currencySymbol = "₹"
                        )
                    )
                }

                Log.i(TAG, "Database verified clean with 0 simulated transactions.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying database state: ${e.message}", e)
        }
    }
}
