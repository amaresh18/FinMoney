package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        UserProfile::class,
        SalaryRecord::class,
        DebitItem::class,
        CustomCategory::class,
        LoanTransaction::class,
        AppNotification::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun finMoneyDao(): FinMoneyDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope? = null): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "finmoney_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance

                // Populate default custom categories if needed
                scope?.launch(Dispatchers.IO) {
                    val dao = instance.finMoneyDao()
                    val existing = dao.getUserProfileDirect()
                    if (existing == null) {
                        dao.insertOrUpdateUserProfile(
                            UserProfile(
                                id = 1,
                                name = "You",
                                email = "",
                                phoneNumber = "",
                                isOtpVerified = true,
                                isEmailVerified = true
                            )
                        )
                    }
                }

                instance
            }
        }
    }
}
