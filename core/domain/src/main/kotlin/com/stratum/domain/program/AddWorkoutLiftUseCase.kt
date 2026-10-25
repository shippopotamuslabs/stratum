package com.stratum.domain.program

import com.stratum.domain.lift.LiftRepository
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.resolvedDefaultRestSeconds
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.domain.training.ProgramChangeEventPayload
import com.stratum.domain.training.ProgramChangeEventRepository
import com.stratum.domain.training.ProgramChangeEventType
import com.stratum.domain.training.identity.ProgramChangeEventId
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.first

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
    suspend operator fun invoke(workoutId: WorkoutId, liftId: LiftId): Result<Unit> {
        val program = programRepository.getProgram() ?: return Result.failure(
            IllegalStateException("No active program found to add lift")
        )
        val restSeconds =
            liftRepository.observeLifts().first()
                .firstOrNull { it.id == liftId }
                ?.resolvedDefaultRestSeconds()
                ?: DEFAULT_LIFT_SET_REST_SECONDS

        if (program.workouts.none { it.id == workoutId }) {
            return Result.failure(
                IllegalArgumentException("Workout with ID $workoutId not found in the program")
            )
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
        return Result.success(Unit)
    }
}
