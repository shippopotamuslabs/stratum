package com.stratum.application.workout

import com.stratum.application.diagnostics.OperationalDiagnostic
import com.stratum.domain.training.identity.TrainingSessionId
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import com.stratum.domain.workout.WorkoutOverlay

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
 *
 * [save] / [clear] failures are best-effort: operational failures are returned
 * as [OperationalDiagnostic]s (empty on success) for the caller — the boundary
 * that owns the UI — to drain exactly once, and are otherwise swallowed so the
 * live workout UI is never crashed by overlay persistence (callers never
 * branched on the former failure Result). Coroutine cancellation propagates —
 * it is neither recorded nor swallowed.
 */
class ManageWorkoutOverlayUseCase @Inject constructor(
    private val store: WorkoutOverlayStore,
) {
    /** The persisted overlay for [sessionId], or `null` when nothing matching is stored. */
    suspend fun load(sessionId: TrainingSessionId): WorkoutOverlay? = store.load(sessionId)

    /**
     * Overwrites the stored overlay for [sessionId]. An operational failure is
     * returned as one diagnostic and swallowed; coroutine cancellation
     * propagates.
     */
    suspend fun save(sessionId: TrainingSessionId, overlay: WorkoutOverlay): List<OperationalDiagnostic> =
        try {
            store.save(sessionId, overlay)
            emptyList()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            listOf(
                OperationalDiagnostic(
                    tag = TAG,
                    message = "Failed to persist workout overlay: ${error.message}",
                    cause = error,
                ),
            )
        }

    /** Drops any stored overlay regardless of session id (call on finalize / abandon). */
    suspend fun clear(): List<OperationalDiagnostic> =
        try {
            store.clear()
            emptyList()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            listOf(
                OperationalDiagnostic(
                    tag = TAG,
                    message = "Failed to clear workout overlay: ${error.message}",
                    cause = error,
                ),
            )
        }

    private companion object {
        const val TAG = "ManageWorkoutOverlay"
    }
}
