package com.stratum.domain.training

import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgramRepository
import com.stratum.domain.program.identity.WorkoutId
import javax.inject.Inject

/**
 * Reverses a workout skip (or completion) for the current cycle by deleting the matching
 * COMPLETED PROGRAM_WORKOUT session(s) and recomputing the cycle pointer. If the cycle
 * already advanced (the workout belongs to a previous cycle), this is a no-op — the
 * cycle reset already happened and the workout is uncompleted in the new cycle.
 *
 * Also supports un-skipping rest days (by [Weekday] or slot index). Rest consumption
 * counts toward cycle completion; unskip recomputes the pointer from the remaining ledger.
 *
 * When unskipping a workout, mid-cycle auto-consumed REST_DAY rows that pure math no longer
 * justifies are deleted in the same atomic batch (write-time auto-rests remain reversible).
 *
 * Deletes and cycle-pointer updates are applied atomically via
 * [TrainingLogRepository.deleteSessionsWithCycleEffects].
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
        val workoutSessions = sessions.filter { session ->
            session.programId == program.id &&
                session.workoutId == workoutId &&
                session.cycleNumberSnapshot == program.currentCycleNumber &&
                session.status == TrainingSessionStatus.COMPLETED &&
                session.kind == TrainingSessionKind.PROGRAM_WORKOUT
        }
        if (workoutSessions.isEmpty()) return@runCatching
        val remainingAfterWorkout = sessions.filterNot { session -> session.id in workoutSessions.map { it.id }.toSet() }
        val orphanedAutoRests =
            autoRestSessionsNoLongerJustified(
                program = program,
                remainingSessions = remainingAfterWorkout,
                deletedWorkoutIds = setOf(workoutId),
            )
        applyUnskip(program, sessions, workoutSessions + orphanedAutoRests)
    }

    /**
     * Un-skip a rest day by deleting the matching COMPLETED REST_DAY session for the current
     * cycle and recomputing the cycle pointer (rests count toward cycle completion).
     */
    suspend operator fun invoke(weekday: Weekday): Result<Unit> = runCatching {
        val program =
            programRepository.getProgram()
                ?: error("No active program found to unskip rest day")
        val sessions = trainingLogRepository.getAllSessions()
        val toDelete = sessions.filter { session ->
            session.programId == program.id &&
                session.cycleNumberSnapshot == program.currentCycleNumber &&
                session.status == TrainingSessionStatus.COMPLETED &&
                session.kind == TrainingSessionKind.REST_DAY &&
                session.weekdaySnapshot == weekday
        }
        applyUnskip(program, sessions, toDelete)
    }

    /** Un-skip rest by cycle slot index. */
    suspend fun unskipRestSlot(slotIndex: Int): Result<Unit> = runCatching {
        val program =
            programRepository.getProgram()
                ?: error("No active program found to unskip rest day")
        val sessions = trainingLogRepository.getAllSessions()
        val toDelete = sessions.filter { session ->
            session.programId == program.id &&
                session.cycleNumberSnapshot == program.currentCycleNumber &&
                session.status == TrainingSessionStatus.COMPLETED &&
                session.kind == TrainingSessionKind.REST_DAY &&
                session.cycleSlotIndexSnapshot == slotIndex
        }
        applyUnskip(program, sessions, toDelete)
    }

    private suspend fun applyUnskip(
        program: Program,
        sessions: List<TrainingSession>,
        toDelete: List<TrainingSession>,
    ) {
        if (toDelete.isEmpty()) return
        val deleteIds = toDelete.map(TrainingSession::id).toSet()
        val remaining = sessions.filterNot { it.id in deleteIds }
        val effects = computeCycleAdvancement(program, remaining)
        trainingLogRepository.deleteSessionsWithCycleEffects(
            sessionIds = deleteIds,
            cyclePointerUpdate = effects.pointerUpdate,
            cycleCompletedEvent = effects.cycleCompletedEvent,
        )
    }
}
