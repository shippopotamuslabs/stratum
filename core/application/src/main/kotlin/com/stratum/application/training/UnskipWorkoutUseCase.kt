package com.stratum.application.training

import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.Program
import com.stratum.application.program.ProgramRepository
import com.stratum.domain.program.identity.WorkoutId
import javax.inject.Inject
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.autoRestSessionsNoLongerJustified
import kotlin.coroutines.cancellation.CancellationException

/**
 * Outcome of unskipping a workout or rest day (STR-243).
 *
 * The originating throwable of a failed write goes once to the diagnostics
 * channel ([LoggingPort.error]) at the use-case boundary — this use case
 * previously returned its failures silently; it now logs them once at the
 * boundary, per the C4 precedent. Coroutine cancellation is not a branch: it
 * propagates.
 */
sealed interface UnskipWorkoutResult {
    data object Success : UnskipWorkoutResult

    data object NoActiveProgram : UnskipWorkoutResult

    /** The unskip write failed (repository / store I/O). */
    data object PersistenceFailed : UnskipWorkoutResult
}

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
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(workoutId: WorkoutId): UnskipWorkoutResult {
        val program =
            try {
                programRepository.getProgram()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                loggingPort.error(TAG, "Failed to unskip workout: ${t.message}", t)
                return UnskipWorkoutResult.PersistenceFailed
            }
                ?: return UnskipWorkoutResult.NoActiveProgram
        return try {
            val sessions = trainingLogRepository.getAllSessions()
            val workoutSessions = sessions.filter { session ->
                session.programId == program.id &&
                    session.workoutId == workoutId &&
                    session.cycleNumberSnapshot == program.currentCycleNumber &&
                    session.status == TrainingSessionStatus.COMPLETED &&
                    session.kind == TrainingSessionKind.PROGRAM_WORKOUT
            }
            if (workoutSessions.isNotEmpty()) {
                val remainingAfterWorkout = sessions.filterNot { session -> session.id in workoutSessions.map { it.id }.toSet() }
                val orphanedAutoRests =
                    autoRestSessionsNoLongerJustified(
                        program = program,
                        remainingSessions = remainingAfterWorkout,
                        deletedWorkoutIds = setOf(workoutId),
                    )
                applyUnskip(program, sessions, workoutSessions + orphanedAutoRests)
            }
            UnskipWorkoutResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            loggingPort.error(TAG, "Failed to unskip workout: ${t.message}", t)
            UnskipWorkoutResult.PersistenceFailed
        }
    }

    /**
     * Un-skip a rest day by deleting the matching COMPLETED REST_DAY session for the current
     * cycle and recomputing the cycle pointer (rests count toward cycle completion).
     */
    suspend operator fun invoke(weekday: Weekday): UnskipWorkoutResult {
        val program =
            try {
                programRepository.getProgram()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                loggingPort.error(TAG, "Failed to unskip rest day: ${t.message}", t)
                return UnskipWorkoutResult.PersistenceFailed
            }
                ?: return UnskipWorkoutResult.NoActiveProgram
        return try {
            val sessions = trainingLogRepository.getAllSessions()
            val toDelete = sessions.filter { session ->
                session.programId == program.id &&
                    session.cycleNumberSnapshot == program.currentCycleNumber &&
                    session.status == TrainingSessionStatus.COMPLETED &&
                    session.kind == TrainingSessionKind.REST_DAY &&
                    session.weekdaySnapshot == weekday
            }
            applyUnskip(program, sessions, toDelete)
            UnskipWorkoutResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            loggingPort.error(TAG, "Failed to unskip rest day: ${t.message}", t)
            UnskipWorkoutResult.PersistenceFailed
        }
    }

    /** Un-skip rest by cycle slot index. */
    suspend fun unskipRestSlot(slotIndex: Int): UnskipWorkoutResult {
        val program =
            try {
                programRepository.getProgram()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                loggingPort.error(TAG, "Failed to unskip rest day: ${t.message}", t)
                return UnskipWorkoutResult.PersistenceFailed
            }
                ?: return UnskipWorkoutResult.NoActiveProgram
        return try {
            val sessions = trainingLogRepository.getAllSessions()
            val toDelete = sessions.filter { session ->
                session.programId == program.id &&
                    session.cycleNumberSnapshot == program.currentCycleNumber &&
                    session.status == TrainingSessionStatus.COMPLETED &&
                    session.kind == TrainingSessionKind.REST_DAY &&
                    session.cycleSlotIndexSnapshot == slotIndex
            }
            applyUnskip(program, sessions, toDelete)
            UnskipWorkoutResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            loggingPort.error(TAG, "Failed to unskip rest day: ${t.message}", t)
            UnskipWorkoutResult.PersistenceFailed
        }
    }

    private suspend fun applyUnskip(
        program: Program,
        sessions: List<TrainingSession>,
        toDelete: List<TrainingSession>,
    ) {
        if (toDelete.isEmpty()) return
        val deleteIds = toDelete.map(TrainingSession::id).toSet()
        val remaining = sessions.filterNot { it.id in deleteIds }
        val effects = program.advanceCycle(remaining)
        trainingLogRepository.deleteSessionsWithCycleEffects(
            sessionIds = deleteIds,
            cyclePointerUpdate = effects.pointerUpdate,
            cycleCompletedEvent = effects.cycleCompletedEvent,
        )
    }

    private companion object {
        const val TAG = "UnskipWorkout"
    }
}
