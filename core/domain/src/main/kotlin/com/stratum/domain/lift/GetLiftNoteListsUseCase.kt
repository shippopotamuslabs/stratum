package com.stratum.domain.lift

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.ProgramRepository
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.identity.TrainingSessionId
import javax.inject.Inject

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
    ): LiftNoteLists =
        LiftNoteLists(
            session = liftNotesRepository.getSessionNotes(sessionId, workoutLiftId),
            workout = programRepository.getWorkoutLiftPinnedNotes(workoutLiftId),
            lift = liftRepository.getPinnedNotes(liftId),
        )
}
