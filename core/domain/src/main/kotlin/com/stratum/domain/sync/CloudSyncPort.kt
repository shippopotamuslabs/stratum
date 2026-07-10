package com.stratum.domain.sync

import kotlinx.coroutines.flow.StateFlow

/**
 * Cloud sync boundary. Domain never sees Firebase/Firestore types.
 *
 * Implementations live in `:core:data`. Call sites are use cases only (ARCHITECTURE.md §4.4).
 *
 * Contract:
 * - [start]: ensure account ownership of local Room, upload dirty rows, then full-hydrate
 *   (first time) or incremental pull. No live Firestore listeners in v1.
 * - [stop]: session teardown only (cancel WorkManager / clear active uid); durable hydrate
 *   and pull cursors are retained for same-user re-login.
 * - [requestSync]: multi-pass upload of residual dirty + incremental pull while started.
 *
 * Incomplete surface (not synced by design): response-modeling tables, import job metadata,
 * insights, theme prefs, workout overlays — multi-device may recompute or diverge for those.
 */
interface CloudSyncPort {
    val syncStatus: StateFlow<SyncStatus>

    /**
     * Begin sync for [userId]: account isolation, upload pending, hydrate/pull.
     */
    suspend fun start(userId: String): Result<Unit>

    /** Tear down the current sync session (not an account wipe). */
    suspend fun stop()

    /**
     * One-shot push of dirty rows then pull remote changes. Safe to call while [start] is active.
     */
    suspend fun requestSync(): Result<Unit>
}
