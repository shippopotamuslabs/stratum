package com.stratum.domain.workout

import com.stratum.domain.lift.LiftRepository
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.ProgramRepository
import com.stratum.domain.program.Workout
import com.stratum.domain.program.identity.WorkoutLiftId
import javax.inject.Inject

/**
 * Stored multi-note lists for every slot of a template [Workout].
 *
 * The live session projection is built from the [Workout] object, whose list fields are
 * write-carry only (storage reads leave them empty), so the card display path would
 * otherwise collapse to the legacy single-note mirrors. The session runtime loads these
 * once per structure build (and refreshes after pin saves) and feeds them into
 * `ProjectWorkoutStructureUseCase` so all pins render on the lift card.
 *
 * Every slot/lift key of [workout] is present; empty means no stored rows. Legacy
 * single-note mirrors surface through the repository reads themselves (which
 * lazy-migrate legacy pins to rows), so downstream `?:` fallbacks only serve slots
 * outside the loaded set (e.g. session-added lifts), never stored legacy pins.
 */
data class SessionPinnedNoteLists(
    val byWorkoutLiftId: Map<WorkoutLiftId, List<String>> = emptyMap(),
    val byLiftId: Map<LiftId, List<String>> = emptyMap(),
)

class GetSessionPinnedNoteListsUseCase @Inject constructor(
    private val programRepository: ProgramRepository,
    private val liftRepository: LiftRepository,
) {
    suspend operator fun invoke(workout: Workout): SessionPinnedNoteLists {
        val workoutLists =
            workout.workoutLifts.associate { slot ->
                slot.id to
                    programRepository.getWorkoutLiftPinnedNotes(slot.id).map { it.body }
            }
        val liftLists =
            workout.workoutLifts
                .map { it.liftId }
                .toSet()
                .associateWith { liftId ->
                    liftRepository.getPinnedNotes(liftId).map { it.body }
                }
        return SessionPinnedNoteLists(byWorkoutLiftId = workoutLists, byLiftId = liftLists)
    }
}
