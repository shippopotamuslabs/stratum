package com.stratum.domain.checkin

import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgramRepository
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.WorkoutLiftSetAdjuster
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.domain.training.ProgramChangeEventPayload
import com.stratum.domain.training.ProgramChangeEventRepository
import com.stratum.domain.training.ProgramChangeEventType
import com.stratum.domain.training.identity.ProgramChangeEventId
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

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
    private val loggingPort: com.stratum.domain.logging.LoggingPort,
) {
    suspend operator fun invoke(
        commands: List<CheckInApplyCommand>,
        now: Instant = Instant.now(),
    ): Result<Unit> =
        com.stratum.domain.logging.runCatchingLogged(
            loggingPort,
            TAG,
            "Failed to apply check-in recommendations",
        ) {
            if (commands.isNotEmpty()) {
                val program =
                    programRepository.getProgram()
                        ?: error("No active program found to apply check-in")
                val (updated, events) = applyAll(program, commands, now)
                programRepository.saveProgram(updated)
                events.forEach { programChangeEventRepository.append(it) }
            }
            completeCheckIn(now).getOrThrow()
        }

    private companion object {
        const val TAG = "ApplyCheckIn"
    }

    private fun applyAll(
        program: Program,
        commands: List<CheckInApplyCommand>,
        now: Instant,
    ): Pair<Program, List<ProgramChangeEvent>> {
        var workouts = program.workouts
        val events = mutableListOf<ProgramChangeEvent>()

        for (command in commands) {
            when (command) {
                is CheckInApplyCommand.Swap -> {
                    val hit = findWorkoutLift(workouts, command.workoutLiftId)
                        ?: error("Workout lift ${command.workoutLiftId.value} not found")
                    val (workout, lift) = hit
                    val previousLiftId = lift.liftId
                    workouts =
                        workouts.map { w ->
                            if (w.id != workout.id) {
                                w
                            } else {
                                w.copy(
                                    workoutLifts =
                                        w.workoutLifts.map { wl ->
                                            if (wl.id == command.workoutLiftId) {
                                                wl.copy(liftId = command.newLiftId)
                                            } else {
                                                wl
                                            }
                                        },
                                )
                            }
                        }
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
                        val hit = findWorkoutLift(workouts, command.workoutLiftId)
                            ?: error("Workout lift ${command.workoutLiftId.value} not found")
                        val (workout, lift) = hit
                        if (lift.sets.size != command.targetSetCount) {
                            workouts =
                                workouts.map { w ->
                                    if (w.id != workout.id) {
                                        w
                                    } else {
                                        w.copy(
                                            workoutLifts =
                                                w.workoutLifts.map { wl ->
                                                    if (wl.id == command.workoutLiftId) {
                                                        WorkoutLiftSetAdjuster.withSetCount(
                                                            wl,
                                                            command.targetSetCount,
                                                        )
                                                    } else {
                                                        wl
                                                    }
                                                },
                                        )
                                    }
                                }
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
        return program.copy(workouts = workouts) to events
    }

    private fun findWorkoutLift(
        workouts: List<Workout>,
        workoutLiftId: WorkoutLiftId,
    ): Pair<Workout, WorkoutLift>? =
        workouts.firstNotNullOfOrNull { workout ->
            workout.workoutLifts.firstOrNull { it.id == workoutLiftId }?.let { workout to it }
        }
}
