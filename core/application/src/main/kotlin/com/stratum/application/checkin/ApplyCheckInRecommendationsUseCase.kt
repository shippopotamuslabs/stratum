package com.stratum.application.checkin

import com.stratum.domain.program.Program
import com.stratum.application.program.ProgramRepository
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.WorkoutLiftSetAdjuster
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
import com.stratum.domain.checkin.CheckInApplyCommand
import com.stratum.domain.logging.LoggingPort

/**
 * Outcome of [ApplyCheckInRecommendationsUseCase] (STR-243).
 *
 * The refusal branches are expected caller outcomes and are not logged; an
 * operational load/save failure is logged once via [LoggingPort.error] at the
 * use-case boundary and surfaces as [PersistenceFailed]. A failed completion
 * write is logged once at the [CompleteCheckInUseCase] boundary and likewise
 * surfaces as [PersistenceFailed]. Coroutine cancellation is not a branch:
 * it propagates.
 */
sealed interface ApplyCheckInRecommendationsResult {
    data object Success : ApplyCheckInRecommendationsResult

    /** No active program exists to apply the accepted commands to. */
    data object NoActiveProgram : ApplyCheckInRecommendationsResult

    /** A command referenced a workout lift that is not in the active program. */
    data class WorkoutLiftNotFound(val workoutLiftId: WorkoutLiftId) : ApplyCheckInRecommendationsResult

    /** The apply failed (repository / store I/O). */
    data object PersistenceFailed : ApplyCheckInRecommendationsResult
}

/**
 * Applies accepted check-in mutations in a **single** program load/save, then clears pending.
 *
 * Commands use absolute set targets so retries after failure do not compound deltas.
 * Empty command lists still complete the check-in (user rejected all actions).
 */
class ApplyCheckInRecommendationsUseCase @Inject constructor(
    private val programRepository: ProgramRepository,
    private val programChangeEventRepository: ProgramChangeEventRepository,
    private val completeCheckIn: CompleteCheckInUseCase,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(
        commands: List<CheckInApplyCommand>,
        now: Instant = Instant.now(),
    ): ApplyCheckInRecommendationsResult {
        if (commands.isEmpty()) {
            return when (completeCheckIn(now)) {
                CheckInPreferenceResult.Success -> ApplyCheckInRecommendationsResult.Success
                CheckInPreferenceResult.PersistenceFailed ->
                    ApplyCheckInRecommendationsResult.PersistenceFailed
            }
        }
        return try {
            val program =
                programRepository.getProgram()
                    ?: return ApplyCheckInRecommendationsResult.NoActiveProgram
            val applied =
                when (val applyResult = applyAll(program, commands, now)) {
                    is ApplyAllResult.WorkoutLiftNotFound ->
                        return ApplyCheckInRecommendationsResult.WorkoutLiftNotFound(
                            applyResult.workoutLiftId,
                        )
                    is ApplyAllResult.Applied -> applyResult
                }
            programRepository.saveProgram(applied.program)
            applied.events.forEach { programChangeEventRepository.append(it) }
            when (completeCheckIn(now)) {
                CheckInPreferenceResult.Success -> ApplyCheckInRecommendationsResult.Success
                CheckInPreferenceResult.PersistenceFailed ->
                    ApplyCheckInRecommendationsResult.PersistenceFailed
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            loggingPort.error(TAG, "Failed to apply check-in recommendations: ${error.message}", error)
            ApplyCheckInRecommendationsResult.PersistenceFailed
        }
    }

    private companion object {
        const val TAG = "ApplyCheckIn"
    }

    private sealed interface ApplyAllResult {
        data class Applied(
            val program: Program,
            val events: List<ProgramChangeEvent>,
        ) : ApplyAllResult

        data class WorkoutLiftNotFound(val workoutLiftId: WorkoutLiftId) : ApplyAllResult
    }

    private fun applyAll(
        program: Program,
        commands: List<CheckInApplyCommand>,
        now: Instant,
    ): ApplyAllResult {
        var current = program
        val events = mutableListOf<ProgramChangeEvent>()

        for (command in commands) {
            when (command) {
                is CheckInApplyCommand.Swap -> {
                    val hit =
                        findWorkoutLift(current.workouts, command.workoutLiftId)
                            ?: return ApplyAllResult.WorkoutLiftNotFound(command.workoutLiftId)
                    val (workout, lift) = hit
                    val previousLiftId = lift.liftId
                    current = current.replaceWorkoutLift(lift.copy(liftId = command.newLiftId))
                    events +=
                        ProgramChangeEvent(
                            id = ProgramChangeEventId(UUID.randomUUID().toString()),
                            programId = program.id,
                            eventType = ProgramChangeEventType.LIFT_SWAPPED,
                            occurredAt = now,
                            payload =
                                ProgramChangeEventPayload.LiftSwapped(
                                    workoutLiftId = command.workoutLiftId,
                                    previousLiftId = previousLiftId,
                                    newLiftId = command.newLiftId,
                                ),
                        )
                }
                is CheckInApplyCommand.SetWorkingSets -> {
                    if (command.targetSetCount >= 1) {
                        val hit =
                            findWorkoutLift(current.workouts, command.workoutLiftId)
                                ?: return ApplyAllResult.WorkoutLiftNotFound(command.workoutLiftId)
                        val (workout, lift) = hit
                        if (lift.sets.size != command.targetSetCount) {
                            current =
                                current.replaceWorkoutLift(
                                    WorkoutLiftSetAdjuster.withSetCount(
                                        lift,
                                        command.targetSetCount,
                                    ),
                                )
                            events +=
                                ProgramChangeEvent(
                                    id = ProgramChangeEventId(UUID.randomUUID().toString()),
                                    programId = program.id,
                                    eventType = ProgramChangeEventType.WORKOUT_EDITED,
                                    occurredAt = now,
                                    payload = ProgramChangeEventPayload.WorkoutEdited(workout.id),
                                )
                        }
                    }
                }
            }
        }
        return ApplyAllResult.Applied(current, events)
    }

    private fun findWorkoutLift(
        workouts: List<Workout>,
        workoutLiftId: WorkoutLiftId,
    ): Pair<Workout, WorkoutLift>? =
        workouts.firstNotNullOfOrNull { workout ->
            workout.workoutLifts.firstOrNull { it.id == workoutLiftId }?.let { workout to it }
        }
}
