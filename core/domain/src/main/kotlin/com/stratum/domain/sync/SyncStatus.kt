package com.stratum.domain.sync

/**
 * Coarse sync lifecycle for UI / diagnostics. Not a full queue of pending row counts.
 */
sealed interface SyncStatus {
    data object Idle : SyncStatus

    data object Hydrating : SyncStatus

    data object Syncing : SyncStatus

    data class Error(val message: String) : SyncStatus
}
