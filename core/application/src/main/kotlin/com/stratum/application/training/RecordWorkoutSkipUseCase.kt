package com.stratum.application.training

import com.stratum.application.program.ProgramRepository
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.schedule.resolvedCycleSlots
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.sessionsWithMaterializedAutoRests
import com.stratum.domain.program.Workout
import com.stratum.domain.program.schedule.CycleSlot
import kotlin.coroutines.cancellation.CancellationException

/**
 * Outcome of recording a workout skip (STR-243).
 *
 * The guard branches carry the typed facts ([WorkoutNotFound] carries the
 * missing workout id). The originating throwable of a failed write goes once
 * to the diagnostics channel ([LoggingPort.error]) at the use-case boundary —
 * this use case previously returned its failures silently; it now logs them
 * once at the boundary, per the C4 precedent. Coroutine cancellation is not a
 * branch: it propagates.
 */
sealed interface RecordWorkoutSkipResult {
    data class Success(val session: TrainingSession) : RecordWorkoutSkipResult

    data object NoActiveProgram : RecordWorkoutSkipResult

    data class WorkoutNotFound(val workoutId: WorkoutId) : RecordWorkoutSkipResult

    /** The skip write failed (repository / store I/O). */
    data object PersistenceFailed : RecordWorkoutSkipResult
}

class RecordWorkoutSkipUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val programRepository: ProgramRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(workoutId: WorkoutId): RecordWorkoutSkipResult {
        val program =
            try {
                programRepository.getProgram()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                loggingPort.error(TAG, "Failed to record workout skip: ${t.message}", t)
                return RecordWorkoutSkipResult.PersistenceFailed
            }
                ?: return RecordWorkoutSkipResult.NoActiveProgram
        val workout =
            program.workouts.firstOrNull { it.id == workoutId }
                ?: return RecordWorkoutSkipResult.WorkoutNotFound(workoutId)
        val now = Instant.now()

        val slotIndex =
            program.resolvedCycleSlots()
                .filterIsInstance<com.stratum.domain.program.schedule.CycleSlot.Training>()
                .firstOrNull { it.workout.id == workoutId }
                ?.slotIndex
        val session =
            TrainingSession.skipped(
                id = TrainingSessionId(UUID.randomUUID().toString()),
                programId = program.id,
                workoutId = workoutId,
                kind = TrainingSessionKind.PROGRAM_WORKOUT,
                cycleNumberSnapshot = program.currentCycleNumber,
                weekdaySnapshot = workout.weekday,
                cycleSlotIndexSnapshot = slotIndex,
                at = now,
                programNameSnapshot = program.name,
                workoutNameSnapshot = workout.name,
            )

        return try {
            val baseSessions = trainingLogRepository.getAllSessions() + session
            val (ledger, autoRests) =
                sessionsWithMaterializedAutoRests(
                    program = program,
                    sessions = baseSessions,
                    now = now,
                )
            val effects = program.advanceCycle(ledger)

            trainingLogRepository.persistSessionsWithCycleEffects(
                sessions = listOf(session) + autoRests,
                cyclePointerUpdate = effects.pointerUpdate,
                cycleCompletedEvent = effects.cycleCompletedEvent,
            )

            RecordWorkoutSkipResult.Success(session)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            loggingPort.error(TAG, "Failed to record workout skip: ${t.message}", t)
            RecordWorkoutSkipResult.PersistenceFailed
        }
    }

    private companion object {
        const val TAG = "RecordWorkoutSkip"
    }
}
