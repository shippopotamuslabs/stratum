package com.stratum.application.training

import com.stratum.application.program.ProgramRepository
import com.stratum.application.diagnostics.OperationalDiagnostic
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
 * missing workout id). The originating throwable of a failed write rides the
 * [PersistenceFailed] branch as an [OperationalDiagnostic] (legacy tag +
 * rendered message, real throwable as cause); the presentation boundary
 * drains it exactly once through its module's DiagnosticDrain. Coroutine
 * cancellation is not a branch: it propagates.
 */
sealed interface RecordWorkoutSkipResult {
    data class Success(val session: TrainingSession) : RecordWorkoutSkipResult

    data object NoActiveProgram : RecordWorkoutSkipResult

    data class WorkoutNotFound(val workoutId: WorkoutId) : RecordWorkoutSkipResult

    /** The skip write failed (repository / store I/O). */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : RecordWorkoutSkipResult
}

class RecordWorkoutSkipUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val programRepository: ProgramRepository,
) {
    suspend operator fun invoke(workoutId: WorkoutId): RecordWorkoutSkipResult {
        val program =
            try {
                programRepository.getProgram()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                return RecordWorkoutSkipResult.PersistenceFailed(
                    diagnostics = listOf(
                        OperationalDiagnostic(
                            tag = TAG,
                            message = "Failed to record workout skip: ${t.message}",
                            cause = t,
                        ),
                    ),
                )
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
            RecordWorkoutSkipResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to record workout skip: ${t.message}",
                        cause = t,
                    ),
                ),
            )
        }
    }

    private companion object {
        const val TAG = "RecordWorkoutSkip"
    }
}
