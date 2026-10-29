package com.stratum.application.lift

import com.stratum.domain.lift.identity.LiftId
import com.stratum.application.program.ProgramRepository
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.identity.WorkoutLiftNoteId
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import javax.inject.Inject
import com.stratum.domain.lift.LiftCatalogNote
import com.stratum.domain.lift.LiftSessionNote
import com.stratum.domain.lift.WorkoutLiftNote

/** Ordered note lists for all three Manage Notes tabs (empty when untouched). */
data class LiftNoteLists(
    val session: List<LiftSessionNote> = emptyList(),
    val workout: List<WorkoutLiftNote> = emptyList(),
    val lift: List<LiftCatalogNote> = emptyList(),
)

/**
 * Loads every note list for one lift slot in a single call for the notes editor.
 * Session rows come in display order; pins fall back to the legacy single-note
 * mirror when the slot predates list storage.
 *
 * @param overlayWorkoutNotes explicit overlay note bodies for the workout tab
 * (overlay-added slots have no `workout_lifts` row). Non-null wins over the
 * repository list; null means "no overlay entry — read the repository".
 * Overlay bodies carry synthetic stable ids (`<slot>::overlay::<index>`) so
 * the editor can reconcile rows; they never match stored row ids, so a later
 * repository write always mints fresh ids.
 */
class GetLiftNoteListsUseCase @Inject constructor(
    private val liftNotesRepository: LiftNotesRepository,
    private val liftRepository: LiftRepository,
    private val programRepository: ProgramRepository,
) {
    suspend operator fun invoke(
        sessionId: TrainingSessionId,
        workoutLiftId: WorkoutLiftId,
        liftId: LiftId,
        overlayWorkoutNotes: List<String>? = null,
    ): LiftNoteLists =
        LiftNoteLists(
            session = liftNotesRepository.getSessionNotes(sessionId, workoutLiftId),
            workout = overlayWorkoutNotes?.toOverlayNotes(workoutLiftId)
                // Pair-scoped (STR-127): the editor shows the notes of the lift
                // currently on the slot, not the previous occupant's.
                ?: programRepository.getWorkoutLiftPinnedNotes(workoutLiftId, liftId),
            lift = liftRepository.getPinnedNotes(liftId),
        )

    private fun List<String>.toOverlayNotes(workoutLiftId: WorkoutLiftId): List<WorkoutLiftNote> {
        val now = Instant.now()
        return mapIndexed { index, body ->
            WorkoutLiftNote(
                id = WorkoutLiftNoteId("${workoutLiftId.value}::overlay::$index"),
                workoutLiftId = workoutLiftId,
                body = body,
                position = index,
                updatedAt = now,
            )
        }
    }
}
