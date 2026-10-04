package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

@Database(
    entities = [
        UserProfile::class,
        SalaryRecord::class,
        DebitItem::class,
        CustomCategory::class,
        LoanTransaction::class,
        AppNotification::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun finMoneyDao(): FinMoneyDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "finmoney_database"
                )
                .addCallback(AppDatabaseCallback(scope))
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class AppDatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialData(database.finMoneyDao())
                    }
                }
            }

            suspend fun populateInitialData(dao: FinMoneyDao) {
                // Default Custom Categories only for clean production onboarding
                dao.insertCustomCategory(CustomCategory(name = "House Rent", iconName = "home", colorHex = "#38BDF8", isDefault = true))
                dao.insertCustomCategory(CustomCategory(name = "Utilities & Bills", iconName = "flash", colorHex = "#F59E0B", isDefault = true))
                dao.insertCustomCategory(CustomCategory(name = "Insurance Premium", iconName = "shield", colorHex = "#EC4899", isDefault = true))
                dao.insertCustomCategory(CustomCategory(name = "Child Education", iconName = "school", colorHex = "#8B5CF6", isDefault = true))
                dao.insertCustomCategory(CustomCategory(name = "Subscriptions", iconName = "tv", colorHex = "#6366F1", isDefault = true))
            }
        }
    }
}
