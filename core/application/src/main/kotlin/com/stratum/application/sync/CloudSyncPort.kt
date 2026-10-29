package com.stratum.application.sync

import kotlinx.coroutines.flow.StateFlow
import com.stratum.domain.sync.CloudSessionState
import com.stratum.domain.sync.SyncStatus
import com.stratum.domain.lift.residual

/**
 * Cloud sync boundary. Domain never sees Firebase/Firestore types.
 *
 * Implementations live in `:core:data`. Call sites are use cases only (ARCHITECTURE.md §4.4).
 *
 * Contract:
 * - [start]: ensure account ownership of local Room, then push/pull (or ownership-only when
 *   the feature gate is off). Marks [sessionState] [CloudSessionState.Ready] once local data
 *   is owned/usable (hydrated local cache or after first successful hydrate); network failures
 *   after local-ready do not revoke Ready.
 * - [stop]: session teardown only (cancel WorkManager / clear active uid); durable hydrate
 *   and pull cursors are retained for same-user re-login **unless** [endSessionAndWipe] ran.
 * - [requestSync]: multi-pass upload of residual dirty + incremental pull while started.
 * - [endSessionAndWipe]: atomic logout teardown — stop session + wipe Room + durable
 *   hydrate/cursors so next [start] full-hydrates from cloud. Last-account ownership is
 *   retained so a different uid on the next [start] still triggers a wipe. Call before
 *   Firebase sign-out.
 * - [sessionState]: shell gate for account-scoped local readiness (not merely Firebase auth).
 *
 * Incomplete surface (not synced by design): response-modeling tables, import job metadata,
 * insights, theme prefs, workout overlays — multi-device may recompute or diverge for those.
 */
interface CloudSyncPort {
    val syncStatus: StateFlow<SyncStatus>

    /**
     * Account-scoped local session readiness. Never [CloudSessionState.Ready] solely because
     * [syncStatus] is Idle before [start] has run.
     */
    val sessionState: StateFlow<CloudSessionState>

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

    /**
     * Atomic logout teardown: cancel workers, clear active uid, wipe Room user data and
     * durable hydrate/cursors for [userId]. Last-account ownership is retained so a different
     * uid on the next [start] still triggers a wipe. Safe to call without a prior [stop].
     */
    suspend fun endSessionAndWipe(userId: String): Result<Unit>
}
