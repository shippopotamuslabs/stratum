package com.stratum.application.lift

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.Program
import com.stratum.application.program.ProgramRepository
import com.stratum.application.training.TrainingLogRepository
import com.stratum.domain.training.TrainingSession
import com.stratum.application.workout.WorkoutOverlayStore
import javax.inject.Inject
import com.stratum.domain.lift.AffectedLiftWorkout
import com.stratum.domain.lift.LiftDeleteImpact

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
        if (lift.isTombstoned) return null

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
            activeSession != null && sessionReferencesLift(activeSession, programs, id)

        return LiftDeleteImpact(
            liftId = id,
            liftName = lift.name,
            affectedWorkouts = affectedWorkouts,
            loggedSetCount = loggedSetCount,
            inActiveSession = inActiveSession,
        )
    }

    /**
     * STR-131 Scope 4.5 session-reference check, cheapest signal first: the
     * session's template workout, then the session overlay (a lift swapped in
     * or added mid-session, no set logged yet), then logged sets (a lift
     * logged then swapped back out, which neither of the first two names).
     */
    private suspend fun sessionReferencesLift(
        session: TrainingSession,
        programs: List<Program>,
        id: LiftId,
    ): Boolean {
        val templateHasLift =
            session.workoutId?.let { workoutId ->
                programs
                    .asSequence()
                    .flatMap { it.workouts.asSequence() }
                    .firstOrNull { it.id == workoutId }
                    ?.workoutLifts
                    ?.any { it.liftId == id }
            } ?: false
        if (templateHasLift) return true
        val overlay = workoutOverlayStore.load(session.id)
        if (overlay?.containsLift(id) == true) return true
        return trainingLogRepository.getSetsForSession(session.id).any { it.liftId == id }
    }
}
