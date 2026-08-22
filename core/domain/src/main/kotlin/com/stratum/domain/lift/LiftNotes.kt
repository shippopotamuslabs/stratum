package com.stratum.domain.lift

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.identity.LiftSessionNoteId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant

/** Maximum persisted length for any lift note body (session, workout, or catalog). */
const val MAX_LIFT_NOTE_LENGTH: Int = 2000

/**
 * One transient session note for a lift slot. Historical after the session completes;
 * CASCADE-deleted when the training session is abandoned.
 */
data class LiftSessionNote(
    val id: LiftSessionNoteId,
    val sessionId: TrainingSessionId,
    val liftId: LiftId,
    val workoutLiftId: WorkoutLiftId,
    val body: String,
    val updatedAt: Instant,
)

/** Completed-session history row for "View Past Session Notes". */
data class PastSessionLiftNote(
    val sessionId: TrainingSessionId,
    val workoutLiftId: WorkoutLiftId,
    val body: String,
    val sessionStartedAt: Instant,
    val workoutName: String?,
)

/**
 * Dirty-tab payload for [SaveLiftNotesUseCase]. A null [OptionalNoteEdit] means that tab
 * was not modified; a present edit with blank [body] deletes / nulls that note.
 */
data class OptionalNoteEdit(val body: String?)

data class SaveLiftNotesCommand(
    val sessionId: TrainingSessionId,
    val workoutLiftId: WorkoutLiftId,
    val liftId: LiftId,
    val sessionNote: OptionalNoteEdit? = null,
    val workoutNote: OptionalNoteEdit? = null,
    val liftNote: OptionalNoteEdit? = null,
)

data class SaveLiftNotesResult(
    /** True when a dirty workout note could not be written to `workout_lifts` (overlay-only slot). */
    val workoutNoteNeedsOverlay: Boolean = false,
)

fun sanitizeLiftNoteBody(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    return trimmed.take(MAX_LIFT_NOTE_LENGTH)
}
