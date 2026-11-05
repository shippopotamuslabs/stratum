package com.stratum.application.program

import com.stratum.application.lift.LiftRepository
import com.stratum.domain.lift.identity.LiftId
import com.stratum.application.diagnostics.OperationalDiagnostic
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.WorkoutId
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
import kotlinx.coroutines.flow.first
import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.DEFAULT_LIFT_SET_REST_SECONDS

/**
 * Outcome of [AddWorkoutLiftUseCase] (STR-243; STR-237 Route (i)).
 *
 * The refusal branches are expected caller outcomes and are not logged; an
 * operational load/save failure rides the [PersistenceFailed] branch as an
 * [OperationalDiagnostic] (legacy tag + rendered message, real throwable as
 * cause); the presentation boundary drains it exactly once through its
 * module's DiagnosticDrain. Coroutine cancellation is not a branch: it
 * propagates.
 */
sealed interface AddWorkoutLiftResult {
    data object Success : AddWorkoutLiftResult

    /** There is no active program to add the lift to. */
    data object NoActiveProgram : AddWorkoutLiftResult

    /** The target workout is not part of the active program. */
    data class WorkoutNotFound(val workoutId: WorkoutId) : AddWorkoutLiftResult

    /** The add failed (repository / store I/O). */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : AddWorkoutLiftResult
}

/**
 * Use case to add a new lift to a workout template in the active program.
 * Saves the updated program to persistence and records a WorkoutEdited event.
 * New sets use the lift's [com.stratum.domain.lift.resolvedDefaultRestSeconds].
 */
class AddWorkoutLiftUseCase @Inject constructor(
    private val programRepository: ProgramRepository,
    private val programChangeEventRepository: ProgramChangeEventRepository,
    private val liftRepository: LiftRepository,
) {
    suspend operator fun invoke(workoutId: WorkoutId, liftId: LiftId): AddWorkoutLiftResult {
        return try {
            val program =
                programRepository.getProgram()
                    ?: return AddWorkoutLiftResult.NoActiveProgram
            val restSeconds =
                liftRepository.observeLifts().first()
                    .firstOrNull { it.id == liftId }
                    ?.resolvedDefaultRestSeconds()
                    ?: DEFAULT_LIFT_SET_REST_SECONDS

            if (program.workouts.none { it.id == workoutId }) {
                return AddWorkoutLiftResult.WorkoutNotFound(workoutId)
            }

            val newWorkoutLiftId = WorkoutLiftId(UUID.randomUUID().toString())
            val newSet = LiftSet(
                id = LiftSetId(UUID.randomUUID().toString()),
                workoutLiftId = newWorkoutLiftId,
                position = 0,
                setType = SetType.Standard,
                repMin = 8,
                repMax = 12,
                rirGoal = 2,
                isTopSet = true,
                restSeconds = restSeconds,
            )
            val newWorkoutLift = WorkoutLift(
                id = newWorkoutLiftId,
                workoutId = workoutId,
                liftId = liftId,
                progressionScheme = ProgressionScheme.Double,
                // Placeholder: Program.addWorkoutLift owns append-position allocation
                // (stored workouts may carry position gaps after pruning).
                position = 0,
                sets = listOf(newSet),
                supersetGroupId = null,
                useRestTimerBetweenLifts = false,
            )

            val updatedProgram = program.addWorkoutLift(newWorkoutLift)
            programRepository.saveProgram(updatedProgram)
            programChangeEventRepository.append(
                ProgramChangeEvent(
                    id = ProgramChangeEventId(UUID.randomUUID().toString()),
                    programId = program.id,
                    eventType = ProgramChangeEventType.WORKOUT_EDITED,
                    occurredAt = Instant.now(),
                    payload = ProgramChangeEventPayload.WorkoutEdited(workoutId),
                ),
            )
            AddWorkoutLiftResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            AddWorkoutLiftResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to add workout lift: ${error.message}",
                        cause = error,
                    ),
                ),
            )
        }
    }

    private companion object {
        const val TAG = "AddWorkoutLift"
    }
}
