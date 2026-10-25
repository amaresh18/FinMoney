package com.example.cloud

import com.example.data.local.FinMoneyDao

/**
 * Attach orphaned on-device rows to the active Firebase user id after sign-in.
 */
object UserLocalDataScoping {

    suspend fun assignOrphanedRowsToUser(dao: FinMoneyDao, firebaseUid: String) {
        if (firebaseUid.isBlank() || firebaseUid == "user_default") return
        dao.scopeOrphanBankTransactionsToUser(firebaseUid)
        dao.scopeOrphanDebitsToUser(firebaseUid)
        dao.scopeOrphanLoansToUser(firebaseUid)
        dao.scopeOrphanSalaryRecordsToUser(firebaseUid)
        dao.scopeOrphanReconciliationsToUser(firebaseUid)
        dao.scopeOrphanNotificationsToUser(firebaseUid)
        dao.scopeOrphanCustomCategoriesToUser(firebaseUid)
    }
}
