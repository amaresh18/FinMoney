package com.example.cloud

/** Progress events for cloud backup / restore (mapped to UI in [com.example.ui.FinMoneyViewModel]). */
data class CloudSyncProgress(
    val stage: CloudSyncStage,
    val message: String
)

enum class CloudSyncStage {
    CONNECTING,
    DOWNLOADING_EXPENSES,
    DOWNLOADING_SALARY,
    DOWNLOADING_CATEGORIES,
    DOWNLOADING_LOANS,
    DOWNLOADING_BANK,
    UPLOADING,
    FINISHING,
    SUCCESS,
    ERROR
}
