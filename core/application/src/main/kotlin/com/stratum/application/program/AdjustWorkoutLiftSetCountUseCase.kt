package com.stratum.application.program

import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.domain.training.ProgramChangeEventPayload
import com.stratum.application.training.ProgramChangeEventRepository
import com.stratum.domain.training.ProgramChangeEventType
import com.stratum.domain.training.identity.ProgramChangeEventId
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.WorkoutLiftSetAdjuster

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
    suspend operator fun invoke(workoutLiftId: WorkoutLiftId, delta: Int): Result<Unit> {
        if (delta == 0) return Result.success(Unit)
        val program =
            programRepository.getProgram()
                ?: return Result.failure(IllegalStateException("No active program found to adjust sets"))

        val hit =
            program.workouts.firstNotNullOfOrNull { workout ->
                workout.workoutLifts.firstOrNull { it.id == workoutLiftId }?.let { workout to it }
            }
                ?: return Result.failure(
                    IllegalArgumentException("Workout lift ${workoutLiftId.value} not found in the program"),
                )
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
        return Result.success(Unit)
    }
}
