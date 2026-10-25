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
        LoanLedgerEntry::class,
        AppNotification::class,
        MonthlyReconciliation::class,
        BankTransactionRecord::class,
        RawNotificationRecord::class,
        CloudSyncOutboxEntry::class
    ],
    version = 22,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun finMoneyDao(): FinMoneyDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE loan_transactions ADD COLUMN proofPaths TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE loan_transactions ADD COLUMN agreementText TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_user_profiles_phone_unique " +
                        "ON user_profiles(phoneNumber) WHERE phoneNumber != ''"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_user_profiles_email_unique " +
                        "ON user_profiles(email) WHERE email != ''"
                )
            }
        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE loan_transactions ADD COLUMN outstandingAmount REAL NOT NULL DEFAULT 0.0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS loan_ledger_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        loanId INTEGER NOT NULL,
                        userId TEXT NOT NULL,
                        proposerPhone TEXT NOT NULL,
                        proposerIsCreator INTEGER NOT NULL,
                        kind TEXT NOT NULL,
                        amount REAL NOT NULL,
                        proposedAmount REAL NOT NULL,
                        notes TEXT NOT NULL,
                        proposedNotes TEXT NOT NULL,
                        amendmentPending INTEGER NOT NULL,
                        proofPaths TEXT NOT NULL,
                        proposedProofPaths TEXT NOT NULL,
                        status TEXT NOT NULL,
                        agreementText TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(loanId) REFERENCES loan_transactions(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_loan_ledger_entries_loanId ON loan_ledger_entries(loanId)")
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE loan_transactions ADD COLUMN proposedAmount REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE loan_transactions ADD COLUMN proposedNotes TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE loan_transactions ADD COLUMN proposedPersonName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE loan_transactions ADD COLUMN proposedPersonContact TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE loan_transactions ADD COLUMN amendmentPending INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE loan_transactions ADD COLUMN creatorPhone TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cloud_sync_outbox (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId TEXT NOT NULL,
                        subcollection TEXT NOT NULL,
                        documentId TEXT NOT NULL,
                        payloadJson TEXT NOT NULL,
                        legacyCollectionsJson TEXT NOT NULL,
                        attempts INTEGER NOT NULL,
                        lastError TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_cloud_sync_outbox_user_doc " +
                        "ON cloud_sync_outbox(userId, subcollection, documentId)"
                )
            }
        }

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
                        .addMigrations(
                            MIGRATION_15_16,
                            MIGRATION_16_17,
                            MIGRATION_17_18,
                            MIGRATION_18_19,
                            MIGRATION_19_20,
                            MIGRATION_20_21,
                            MIGRATION_21_22
                        )
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
