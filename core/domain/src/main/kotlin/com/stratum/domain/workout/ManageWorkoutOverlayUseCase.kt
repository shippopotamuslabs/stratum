package com.stratum.domain.workout

import com.stratum.domain.training.identity.TrainingSessionId
import javax.inject.Inject

/**
 * Persists and restores the transient in-memory [WorkoutOverlay] so a training session
 * survives an app process death (AGENTS.md §4.5 — no Room table for transient state;
 * DataStore is the designated home). Backed by [WorkoutOverlayStore]; the store is
 * session-id-guarded so a stale record from a different session is never restored.
 *
 * The three operations — [load], [save], [clear] — are grouped here because they are all
 * persistence concerns over the same overlay aggregate. The workout ViewModel consumes this
 * single use case rather than three (ARCHITECTURE.md §4.4: ViewModels depend on use cases,
 * not repository ports).
 */
class ManageWorkoutOverlayUseCase @Inject constructor(
    private val store: WorkoutOverlayStore,
) {
    /** The persisted overlay for [sessionId], or `null` when nothing matching is stored. */
    suspend fun load(sessionId: TrainingSessionId): WorkoutOverlay? = store.load(sessionId)

    /** Overwrites the stored overlay for [sessionId]. */
    suspend fun save(sessionId: TrainingSessionId, overlay: WorkoutOverlay) =
        store.save(sessionId, overlay)

    /** Drops any stored overlay regardless of session id (call on finalize / abandon). */
    suspend fun clear() = store.clear()
}
