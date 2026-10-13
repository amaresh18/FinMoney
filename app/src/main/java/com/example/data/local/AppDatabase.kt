package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
        AppNotification::class,
        MonthlyReconciliation::class,
        BankTransactionRecord::class,
        RawNotificationRecord::class
    ],
    version = 16,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun finMoneyDao(): FinMoneyDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bank_transactions ADD COLUMN status TEXT NOT NULL DEFAULT 'PENDING'")
                db.execSQL("ALTER TABLE bank_transactions ADD COLUMN fingerprint TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE bank_transactions ADD COLUMN sbnKey TEXT NOT NULL DEFAULT ''")
            }
        }

        fun resetDatabase(context: Context) {
            synchronized(this) {
                try {
                    INSTANCE?.close()
                } catch (_: Exception) {}
                INSTANCE = null
                try {
                    val appContext = context.applicationContext
                    appContext.deleteDatabase("finmoney_database")
                    val dbFile = appContext.getDatabasePath("finmoney_database")
                    if (dbFile.exists()) dbFile.delete()
                    val shmFile = java.io.File(dbFile.path + "-shm")
                    if (shmFile.exists()) shmFile.delete()
                    val walFile = java.io.File(dbFile.path + "-wal")
                    if (walFile.exists()) walFile.delete()
                } catch (e: Exception) {
                    android.util.Log.e("AppDatabase", "Error deleting database on reset: ${e.message}")
                }
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope? = null): AppDatabase {
            val existing = INSTANCE
            if (existing != null && existing.isOpen) {
                return existing
            }

            return synchronized(this) {
                if (INSTANCE != null && INSTANCE!!.isOpen) {
                    return@synchronized INSTANCE!!
                }

                val appContext = context.applicationContext

                fun createBuilder(): AppDatabase {
                    return Room.databaseBuilder(
                        appContext,
                        AppDatabase::class.java,
                        "finmoney_database"
                    )
                        .addMigrations(MIGRATION_15_16)
                        .fallbackToDestructiveMigration()
                        .fallbackToDestructiveMigrationOnDowngrade()
                        .build()
                }

                var instance = createBuilder()
                try {
                    // Eagerly verify schema integrity by opening the writable database
                    instance.openHelper.writableDatabase
                } catch (t: Throwable) {
                    android.util.Log.e("AppDatabase", "Room schema integrity or open exception: ${t.message}. Recreating database.", t)
                    try {
                        instance.close()
                    } catch (_: Exception) {}
                    try {
                        appContext.deleteDatabase("finmoney_database")
                        val dbFile = appContext.getDatabasePath("finmoney_database")
                        if (dbFile.exists()) dbFile.delete()
                        val shmFile = java.io.File(dbFile.path + "-shm")
                        if (shmFile.exists()) shmFile.delete()
                        val walFile = java.io.File(dbFile.path + "-wal")
                        if (walFile.exists()) walFile.delete()
                    } catch (ex: Exception) {
                        android.util.Log.e("AppDatabase", "Error during database cleanup: ${ex.message}")
                    }
                    instance = createBuilder()
                    try {
                        instance.openHelper.writableDatabase
                    } catch (t2: Throwable) {
                        android.util.Log.e("AppDatabase", "Secondary Room open exception: ${t2.message}", t2)
                    }
                }

                INSTANCE = instance

                // Ensure DatabaseHealthGuard seeds initial data if missing
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val dao = instance.finMoneyDao()
                        DatabaseHealthGuard.verifyAndRepairDatabase(dao, forceReset = false)
                    } catch (e: Exception) {
                        android.util.Log.e("AppDatabase", "Error initializing default profile and health guard: ${e.message}")
                    }
                }

                instance
            }
        }
    }
}
