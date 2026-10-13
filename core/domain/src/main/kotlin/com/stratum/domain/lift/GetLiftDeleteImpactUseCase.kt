package com.stratum.domain.lift

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.ProgramRepository
import com.stratum.domain.training.TrainingLogRepository
import com.stratum.domain.workout.WorkoutOverlayStore
import javax.inject.Inject

/**
 * Computes [LiftDeleteImpact] for [LiftId] over the full database: every
 * program (active + library) for slots, the full log for set counts.
 * Returns null when the lift does not exist or is already tombstoned.
 * Programs are loaded once and shared by both derivations.
 */
class GetLiftDeleteImpactUseCase @Inject constructor(
    private val liftRepository: LiftRepository,
    private val programRepository: ProgramRepository,
    private val trainingLogRepository: TrainingLogRepository,
    private val workoutOverlayStore: WorkoutOverlayStore,
) {
    suspend operator fun invoke(id: LiftId): LiftDeleteImpact? {
        val lift = liftRepository.getLiftsByIds(listOf(id)).firstOrNull() ?: return null
        if (lift.isDeleted) return null

        val programs = programRepository.getAllUserPrograms()
        val affectedWorkouts =
            programs
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
                        programs
                            .asSequence()
                            .flatMap { it.workouts.asSequence() }
                            .firstOrNull { it.id == workoutId }
                            ?.workoutLifts
                            ?.any { it.liftId == id }
                    } ?: false
                // STR-131 Scope 4.5: the lift also counts as session-referenced
                // when it only exists in the session overlay (swapped in or
                // added mid-session, no set logged yet). The logged-set check
                // stays: it covers a lift logged then swapped back out, which
                // neither the template nor the overlay still names.
                val overlay = workoutOverlayStore.load(activeSession.id)
                val overlayHasLift =
                    overlay != null &&
                        (id in overlay.swappedLifts.values ||
                            overlay.addedLifts.any { it.liftId == id })
                templateHasLift || overlayHasLift ||
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
