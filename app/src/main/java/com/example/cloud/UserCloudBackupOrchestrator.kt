package com.example.cloud

import android.content.Context
import android.util.Log
import com.example.data.local.FinMoneyDao

/**
 * Single entry point for account backup / restore, aligned with premium finance apps:
 *
 * 1. **Local-first writes** — Room is always updated first; cloud is async via outbox.
 * 2. **Pull on session** — After Google sign-in, cold start, or profile refresh, download
 *    `users/{firebaseUid}/...` into Room (idempotent `cloudId` keys).
 * 3. **Reconcile push** — Queue any local rows missing from Firestore, then flush the outbox.
 * 4. **Offline resilience** — Failed uploads stay in `cloud_sync_outbox` with exponential backoff.
 *
 * Firestore layout: `users/{uid}/expenses`, `salary_records`, `custom_categories`,
 * `custom_expense_tags`, `loans`, `bank_transactions`, `account/profile`.
 */
object UserCloudBackupOrchestrator {
    private const val TAG = "UserCloudBackup"

    data class Report(
        val restore: CloudAccountRestore.Result,
        val outboxFlushed: Int,
        val pendingOutboxRemaining: Int,
        val completedAt: Long
    )

    suspend fun runFullAccountSync(
        context: Context,
        dao: FinMoneyDao,
        cloud: UserCloudDataRepository = UserCloudDataRepository(),
        user: CloudUserContext? = null,
        onProgress: (CloudSyncProgress) -> Unit = {}
    ): Report {
        val resolvedUser = user ?: CloudUserContextResolver.resolve(dao)
        if (!resolvedUser.canSyncToCloud) {
            return Report(
                restore = CloudAccountRestore.Result(),
                outboxFlushed = 0,
                pendingOutboxRemaining = dao.getCloudSyncOutboxForUser(resolvedUser.userId).size,
                completedAt = 0L
            )
        }
        val appContext = context.applicationContext
        onProgress(CloudSyncProgress(CloudSyncStage.CONNECTING, "Connecting to secure cloud…"))
        val restore = CloudAccountRestore.syncAccount(appContext, dao, cloud, resolvedUser, onProgress)
        onProgress(CloudSyncProgress(CloudSyncStage.UPLOADING, "Syncing to cloud…"))
        var flushed = CloudSyncOutboxManager.flushPending(appContext, dao)
        // Second pass: pull may have queued many rows; flush again like CRED post-login sync.
        flushed += CloudSyncOutboxManager.flushPending(appContext, dao)
        onProgress(CloudSyncProgress(CloudSyncStage.FINISHING, "Finalizing…"))
        val pending = dao.getCloudSyncOutboxForUser(resolvedUser.userId).size
        val at = System.currentTimeMillis()
        if (pending == 0) {
            CloudSyncPreferences.setLastSuccessfulSyncAt(appContext, resolvedUser.userId, at)
        }
        onProgress(
            CloudSyncProgress(
                CloudSyncStage.SUCCESS,
                if (restore.totalRestored() > 0) {
                    "Restored ${restore.totalRestored()} item(s) from backup"
                } else {
                    "Your data is up to date"
                }
            )
        )
        Log.i(
            TAG,
            "Full sync uid=${resolvedUser.userId} restored=${restore.totalRestored()} " +
                "queuedUpload=${restore.totalQueuedUpload()} flushed=$flushed pending=$pending"
        )
        return Report(restore, flushed, pending, at)
    }
}
