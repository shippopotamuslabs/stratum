package com.stratum.domain.workout

import com.stratum.domain.training.identity.TrainingSessionId

/**
 * Port: persists the transient in-memory [WorkoutOverlay] so a training session survives
 * an app process death (AGENTS.md §4.5 — no Room table for transient state; DataStore is
 * the designated home). Implemented in `:core:data`; faked in `:core:testing`.
 *
 * Storage is a single slot scoped to the currently active training session. [save] tags the
 * record with [sessionId]; [load] returns the overlay only when the stored session id
 * matches, so a stale record left over from a different (e.g. previously abandoned)
 * session is ignored. [clear] discards any stored overlay and is called when a session is
 * finalized or abandoned.
 */
interface WorkoutOverlayStore {
    /** The persisted overlay for [sessionId], or `null` when nothing (matching) is stored. */
    suspend fun load(sessionId: TrainingSessionId): WorkoutOverlay?

    /** Overwrites the stored overlay for [sessionId]. */
    suspend fun save(sessionId: TrainingSessionId, overlay: WorkoutOverlay)

    /** Drops any stored overlay regardless of session id. */
    suspend fun clear()
}
