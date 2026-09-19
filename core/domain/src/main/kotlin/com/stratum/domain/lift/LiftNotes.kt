package com.stratum.domain.lift

import com.stratum.domain.lift.identity.LiftCatalogNoteId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.identity.LiftSessionNoteId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.identity.WorkoutLiftNoteId
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant

/** Maximum persisted length for any lift note body (session, workout, or catalog). */
const val MAX_LIFT_NOTE_LENGTH: Int = 2000

/**
 * One transient session note for a lift slot. Historical after the session completes;
 * CASCADE-deleted when the training session is abandoned.
 *
 * [position] orders notes within one slot (append-order; 0 for the single legacy note).
 */
data class LiftSessionNote(
    val id: LiftSessionNoteId,
    val sessionId: TrainingSessionId,
    val liftId: LiftId,
    val workoutLiftId: WorkoutLiftId,
    val body: String,
    val updatedAt: Instant,
    val position: Int = 0,
)

/** One row of the per-lift catalog note list (multi-note successor of the legacy pin). */
data class LiftCatalogNote(
    val id: LiftCatalogNoteId,
    val liftId: LiftId,
    val body: String,
    val position: Int,
    val updatedAt: Instant,
)

/** One row of the per-slot workout note list (multi-note successor of the legacy pin). */
data class WorkoutLiftNote(
    val id: WorkoutLiftNoteId,
    val workoutLiftId: WorkoutLiftId,
    val body: String,
    val position: Int,
    val updatedAt: Instant,
)

/**
 * A single note draft from the editor. A null [id] means "append a new note";
 * a present id means "update the note with this id". Blank bodies are dropped
 * (deleted) on save. Order in the submitted list is the display order.
 */
data class SessionNoteInput(val id: LiftSessionNoteId? = null, val body: String)

/** See [SessionNoteInput]. */
data class CatalogNoteInput(val id: LiftCatalogNoteId? = null, val body: String)

/** See [SessionNoteInput]. */
data class WorkoutLiftNoteInput(val id: WorkoutLiftNoteId? = null, val body: String)

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
    /**
     * Multi-note replacements. Non-null means "this tab was edited — replace its whole
     * list"; null means untouched (the legacy single-body field above still applies).
     */
    val sessionNotes: List<SessionNoteInput>? = null,
    val workoutNotes: List<WorkoutLiftNoteInput>? = null,
    val liftNotes: List<CatalogNoteInput>? = null,
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
