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

    /** All notes for one slot, display order (position ascending). */
    suspend fun getSessionNotes(
        sessionId: TrainingSessionId,
        workoutLiftId: WorkoutLiftId,
    ): List<LiftSessionNote>

    /**
     * Replaces the whole note list for one slot. Blank bodies are dropped; an empty
     * surviving list deletes all rows. Null input ids mint new rows; unknown ids are
     * treated as new rows so legacy single-note callers can migrate forward.
     */
    suspend fun replaceSessionNotes(
        sessionId: TrainingSessionId,
        workoutLiftId: WorkoutLiftId,
        liftId: LiftId,
        notes: List<SessionNoteInput>,
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
