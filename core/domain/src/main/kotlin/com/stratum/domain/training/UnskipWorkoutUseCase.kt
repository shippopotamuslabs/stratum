package com.stratum.domain.training

import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.ProgramRepository
import com.stratum.domain.program.identity.WorkoutId
import javax.inject.Inject

/**
 * Reverses a workout skip (or completion) for the current cycle by deleting the matching
 * COMPLETED PROGRAM_WORKOUT session(s) and recomputing the cycle pointer. If the cycle
 * already advanced (the workout belongs to a previous cycle), this is a no-op — the
 * cycle reset already happened and the workout is uncompleted in the new cycle.
 *
 * Also supports un-skipping rest days via the [invoke] overload that accepts a [Weekday].
 * Rest-day sessions do not advance the cycle pointer, so un-skipping one is just a delete.
 */
class UnskipWorkoutUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val programRepository: ProgramRepository,
) {
    suspend operator fun invoke(workoutId: WorkoutId): Result<Unit> = runCatching {
        val program =
            programRepository.getProgram()
                ?: error("No active program found to unskip workout")
        val sessions = trainingLogRepository.getAllSessions()
        val toDelete = sessions.filter { session ->
            session.workoutId == workoutId &&
                session.cycleNumberSnapshot == program.currentCycleNumber &&
                session.status == TrainingSessionStatus.COMPLETED &&
                session.kind == TrainingSessionKind.PROGRAM_WORKOUT
        }
        if (toDelete.isEmpty()) return@runCatching

        toDelete.forEach { session ->
            trainingLogRepository.deleteSession(session.id)
        }

        val remaining = sessions.filterNot { it.id in toDelete.map(TrainingSession::id) }
        val effects = computeCycleAdvancement(program, remaining)
        programRepository.updateCyclePointer(
            programId = effects.pointerUpdate.programId,
            currentCycleNumber = effects.pointerUpdate.currentCycleNumber,
            cyclePosition = effects.pointerUpdate.cyclePosition,
        )
    }

    /**
     * Un-skip a rest day by deleting the matching COMPLETED REST_DAY session for the current
     * cycle. Rest-day sessions don't affect the cycle pointer, so no recompute is needed.
     */
    suspend operator fun invoke(weekday: Weekday): Result<Unit> = runCatching {
        val program =
            programRepository.getProgram()
                ?: error("No active program found to unskip rest day")
        val sessions = trainingLogRepository.getAllSessions()
        val toDelete = sessions.filter { session ->
            session.cycleNumberSnapshot == program.currentCycleNumber &&
                session.status == TrainingSessionStatus.COMPLETED &&
                session.kind == TrainingSessionKind.REST_DAY &&
                session.weekdaySnapshot == weekday
        }
        toDelete.forEach { session ->
            trainingLogRepository.deleteSession(session.id)
        }
    }
}
