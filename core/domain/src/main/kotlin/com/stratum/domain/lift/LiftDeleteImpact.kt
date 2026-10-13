package com.stratum.domain.lift

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.ProgramRepository
import com.stratum.domain.training.TrainingLogRepository
import javax.inject.Inject

/** One workout whose template slots point at the lift being deleted. */
data class AffectedLiftWorkout(
    val programName: String,
    val workoutName: String,
    val slotCount: Int,
)

/**
 * Delete-impact preview for one lift (STR-131 Scope 3): computed from the
 * database at dialog open, not from UI state, so the numbers are true even
 * when the on-screen catalog list is filtered or searched.
 *
 * - [affectedWorkouts]: template slots that will be removed (the workouts
 *   themselves survive, possibly empty).
 * - [loggedSetCount]: logged sets linked to the lift. They stay in history,
 *   untouched, still resolving the retained tombstoned row by id.
 * - [inActiveSession]: the lift is part of the in-progress session (template
 *   slot or already-logged set). The delete proceeds anyway — the session
 *   grandfathers the retained row; the dialog only notes it.
 */
data class LiftDeleteImpact(
    val liftId: LiftId,
    val liftName: String,
    val affectedWorkouts: List<AffectedLiftWorkout>,
    val loggedSetCount: Int,
    val inActiveSession: Boolean,
) {
    val totalSlotCount: Int get() = affectedWorkouts.sumOf { it.slotCount }
}

/**
 * Computes [LiftDeleteImpact] for [LiftId] over the full database: every
 * program (active + library) for slots, the full log for set counts.
 * Returns null when the lift does not exist or is already tombstoned.
 */
class GetLiftDeleteImpactUseCase @Inject constructor(
    private val liftRepository: LiftRepository,
    private val programRepository: ProgramRepository,
    private val trainingLogRepository: TrainingLogRepository,
) {
    suspend operator fun invoke(id: LiftId): LiftDeleteImpact? {
        val lift = liftRepository.getLiftsByIds(listOf(id)).firstOrNull() ?: return null
        if (lift.isDeleted) return null

        val affectedWorkouts =
            programRepository.getAllUserPrograms()
                .flatMap { program ->
                    program.workouts.map { workout -> program.name to workout }
                }
                .mapNotNull { (programName, workout) ->
                    val count = workout.workoutLifts.count { it.liftId == id }
                    if (count == 0) {
                        null
                    } else {
                        AffectedLiftWorkout(programName, workout.name, count)
                    }
                }
                .sortedWith(compareBy({ it.workoutName.lowercase() }, { it.programName.lowercase() }))

        val loggedSetCount = trainingLogRepository.getSetsForLift(id).size

        val activeSession = trainingLogRepository.getActiveSession()
        val inActiveSession =
            activeSession != null && run {
                val templateHasLift =
                    activeSession.workoutId?.let { workoutId ->
                        programRepository.getAllUserPrograms()
                            .asSequence()
                            .flatMap { it.workouts.asSequence() }
                            .firstOrNull { it.id == workoutId }
                            ?.workoutLifts
                            ?.any { it.liftId == id }
                    } ?: false
                templateHasLift ||
                    trainingLogRepository.getSetsForSession(activeSession.id).any { it.liftId == id }
            }

        return LiftDeleteImpact(
            liftId = id,
            liftName = lift.name,
            affectedWorkouts = affectedWorkouts,
            loggedSetCount = loggedSetCount,
            inActiveSession = inActiveSession,
        )
    }
}
