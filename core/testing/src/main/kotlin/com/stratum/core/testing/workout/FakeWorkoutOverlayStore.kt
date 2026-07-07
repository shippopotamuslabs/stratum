package com.stratum.core.testing.workout

import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.workout.WorkoutOverlay
import com.stratum.domain.workout.WorkoutOverlayStore

/**
 * In-memory fake for [WorkoutOverlayStore] (UNIT_TESTING.md §6 — fakes over MockK for
 * Stratum-owned ports). Mirrors the session-id-guarding semantics of the real DataStore
 * implementation: [load] returns the stored overlay only when the session id matches.
 *
 * State-based assertion hooks: [saveCallCount], [clearCallCount], and [storedSessionId] /
 * [storedOverlay] for direct inspection without `verify()`.
 */
class FakeWorkoutOverlayStore : WorkoutOverlayStore {

    private var storedSessionId: String? = null
    private var storedOverlay: WorkoutOverlay = WorkoutOverlay()

    var saveCallCount: Int = 0
        private set
    var clearCallCount: Int = 0
        private set

    /** Seeds the store as if [overlay] were persisted for [sessionId]. */
    fun seed(sessionId: TrainingSessionId, overlay: WorkoutOverlay) {
        storedSessionId = sessionId.value
        storedOverlay = overlay
    }

    /** The currently stored session id, or `null` when cleared/nothing stored. */
    fun storedSessionId(): String? = storedSessionId

    /** The currently stored overlay (regardless of session id). */
    fun storedOverlay(): WorkoutOverlay = storedOverlay

    override suspend fun load(sessionId: TrainingSessionId): WorkoutOverlay? =
        storedOverlay.takeIf { storedSessionId == sessionId.value }

    override suspend fun save(sessionId: TrainingSessionId, overlay: WorkoutOverlay) {
        storedSessionId = sessionId.value
        storedOverlay = overlay
        saveCallCount += 1
    }

    override suspend fun clear() {
        storedSessionId = null
        storedOverlay = WorkoutOverlay()
        clearCallCount += 1
    }
}
