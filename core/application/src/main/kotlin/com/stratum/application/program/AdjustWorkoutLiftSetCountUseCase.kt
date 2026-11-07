package com.stratum.application.program

import com.stratum.application.diagnostics.OperationalDiagnostic
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.domain.training.ProgramChangeEventPayload
import com.stratum.application.training.ProgramChangeEventRepository
import com.stratum.domain.training.ProgramChangeEventType
import com.stratum.domain.training.identity.ProgramChangeEventId
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.WorkoutLiftSetAdjuster

/**
 * Outcome of [AdjustWorkoutLiftSetCountUseCase] (STR-243; STR-237 Route (i)).
 *
 * The refusal branches are expected caller outcomes and are not logged; an
 * operational load/save failure rides the [PersistenceFailed] branch as an
 * [OperationalDiagnostic] (legacy tag + rendered message, real throwable as
 * cause); the presentation boundary drains it exactly once through its
 * module's DiagnosticDrain. Coroutine cancellation is not a branch: it
 * propagates.
 */
sealed interface AdjustWorkoutLiftSetCountResult {
    data object Success : AdjustWorkoutLiftSetCountResult

    /** There is no active program to adjust sets in. */
    data object NoActiveProgram : AdjustWorkoutLiftSetCountResult

    /** The target workout lift is not part of the active program. */
    data class WorkoutLiftNotFound(val workoutLiftId: WorkoutLiftId) :
        AdjustWorkoutLiftSetCountResult

    /** The adjust failed (repository / store I/O). */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : AdjustWorkoutLiftSetCountResult
}

/**
 * Adds or removes working sets on a single [WorkoutLift] in the active program template.
 *
 * - Positive [delta]: append clones of the last set (non-top), reindex positions.
 * - Negative [delta]: drop trailing non-top sets first; never below 1 set.
 * Appends a [ProgramChangeEventType.WORKOUT_EDITED] event for the parent workout.
 */
class AdjustWorkoutLiftSetCountUseCase @Inject constructor(
    private val programRepository: ProgramRepository,
    private val programChangeEventRepository: ProgramChangeEventRepository,
) {
    suspend operator fun invoke(
        workoutLiftId: WorkoutLiftId,
        delta: Int,
    ): AdjustWorkoutLiftSetCountResult {
        if (delta == 0) return AdjustWorkoutLiftSetCountResult.Success
        return try {
            val program =
                programRepository.getProgram()
                    ?: return AdjustWorkoutLiftSetCountResult.NoActiveProgram

            val hit =
                program.workouts.firstNotNullOfOrNull { workout ->
                    workout.workoutLifts.firstOrNull { it.id == workoutLiftId }?.let { workout to it }
                }
                    ?: return AdjustWorkoutLiftSetCountResult.WorkoutLiftNotFound(workoutLiftId)
            val (parentWorkout, targetLift) = hit
            val updatedLift = WorkoutLiftSetAdjuster.withDelta(targetLift, delta)

            programRepository.saveProgram(program.replaceWorkoutLift(updatedLift))
            programChangeEventRepository.append(
                ProgramChangeEvent(
                    id = ProgramChangeEventId(UUID.randomUUID().toString()),
                    programId = program.id,
                    eventType = ProgramChangeEventType.WORKOUT_EDITED,
                    occurredAt = Instant.now(),
                    payload = ProgramChangeEventPayload.WorkoutEdited(parentWorkout.id),
                ),
            )
            AdjustWorkoutLiftSetCountResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            AdjustWorkoutLiftSetCountResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to adjust workout lift sets: ${error.message}",
                        cause = error,
                    ),
                ),
            )
        }
    }

    private companion object {
        const val TAG = "AdjustWorkoutLiftSetCount"
    }
}
