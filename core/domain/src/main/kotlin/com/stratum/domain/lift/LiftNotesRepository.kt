package com.stratum.domain.lift

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.identity.TrainingSessionId

/** Persistence for per-session lift notes (historical after complete). */
interface LiftNotesRepository {
    suspend fun getSessionNote(
        sessionId: TrainingSessionId,
        workoutLiftId: WorkoutLiftId,
    ): LiftSessionNote?

    suspend fun upsertSessionNote(note: LiftSessionNote)

    suspend fun deleteSessionNote(
        sessionId: TrainingSessionId,
        workoutLiftId: WorkoutLiftId,
    )

    /**
     * Completed sessions only, non-blank body, newest first.
     * [excludeSessionId] skips the in-progress session when present.
     */
    suspend fun getPastNotesForLift(
        liftId: LiftId,
        excludeSessionId: TrainingSessionId?,
    ): List<PastSessionLiftNote>

    /** Merge-lifts: rewrite session-note catalog ids onto the survivor. */
    suspend fun remapLiftIds(sourceLiftIds: Set<LiftId>, targetLiftId: LiftId)
}
