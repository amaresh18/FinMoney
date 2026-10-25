package com.example.ui

import com.example.cloud.CloudSyncStage

data class CloudSyncUiState(
    val isRunning: Boolean = false,
    val stage: CloudSyncStage = CloudSyncStage.CONNECTING,
    val statusLine: String = "",
    val restoredCount: Int = 0,
    val pendingOutbox: Int = 0,
    val lastSuccessAtMillis: Long = 0L,
    val errorMessage: String? = null,
    /** Full-screen blocker after reinstall / empty local ledger while downloading backup. */
    val showFullscreenOverlay: Boolean = false
)
