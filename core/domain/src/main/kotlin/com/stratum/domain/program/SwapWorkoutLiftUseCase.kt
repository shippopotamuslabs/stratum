package com.stratum.domain.program

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.expectedFailure
import com.stratum.domain.logging.runCatchingLogged
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.domain.training.ProgramChangeEventPayload
import com.stratum.domain.training.ProgramChangeEventRepository
import com.stratum.domain.training.ProgramChangeEventType
import com.stratum.domain.training.identity.ProgramChangeEventId
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Use case to swap the lift on a specific workout lift in the active program.
 * Modifies the template in memory and saves the updated program to persistence.
 */
class SwapWorkoutLiftUseCase @Inject constructor(
    private val programRepository: ProgramRepository,
    private val programChangeEventRepository: ProgramChangeEventRepository,
    private val equipmentRepository: com.stratum.domain.lift.EquipmentRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(workoutLiftId: WorkoutLiftId, newLiftId: LiftId): Result<Unit> {
        val program =
            try {
                programRepository.getProgram()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                loggingPort.error(TAG, "Failed to swap workout lift: ${error.message}", error)
                return Result.failure(error)
            }

        if (program == null) {
            // Expected domain state (no active program), not an operational fault: breadcrumb
            // only, so this never becomes a Crashlytics non-fatal.
            return loggingPort.expectedFailure(
                TAG,
                IllegalStateException("No active program found to swap lift"),
                "Failed to swap workout lift: no active program",
            )
        }

        // STR-127: row-held state is scoped to the (slot, lift) pair. The incoming
        // lift brings its own pair's stashed notes (empty when it never occupied
        // this slot) and inherits its catalog default equipment (null assignment);
        // the outgoing lift's notes stay stored under its own pair and resurface
        // if it is swapped back in. A same-lift "swap" is not a swap: no re-scope.
        val targetLift =
            program.workouts
                .flatMap { it.workoutLifts }
                .firstOrNull { it.id == workoutLiftId }
        val incomingNotes =
            if (targetLift != null && targetLift.liftId != newLiftId) {
                runCatching {
                    programRepository.getWorkoutLiftPinnedNotes(workoutLiftId, newLiftId)
                        .map { it.body }
                }.getOrDefault(emptyList())
            } else {
                null
            }

        var found = false
        var previousLiftId: LiftId? = null
        val updatedWorkouts = program.workouts.map { workout ->
            val updatedLifts = workout.workoutLifts.map { lift ->
                if (lift.id == workoutLiftId) {
                    found = true
                    previousLiftId = lift.liftId
                    if (incomingNotes != null) {
                        lift.copy(
                            liftId = newLiftId,
                            equipmentId = null,
                            pinnedNote = incomingNotes.firstOrNull(),
                            pinnedNotes = incomingNotes,
                        )
                    } else {
                        lift.copy(liftId = newLiftId)
                    }
                } else {
                    lift
                }
            }
            workout.copy(workoutLifts = updatedLifts)
        }

        if (!found) {
            // Expected stale-id outcome, not an operational fault: breadcrumb only.
            return loggingPort.expectedFailure(
                TAG,
                IllegalArgumentException("Workout lift with ID $workoutLiftId not found in the program"),
                "Failed to swap workout lift: workout lift $workoutLiftId not found",
            )
        }

        val updatedProgram = program.copy(workouts = updatedWorkouts)
        // STR-105 Scope 8: this save must not persist any tombstoned slot pointer.
        val orphaned =
            updatedProgram.workouts
                .flatMap { it.workoutLifts }
                .mapNotNull { it.equipmentId }
                .distinct()
                .filter { id -> equipmentRepository.getEquipment(id)?.isDeleted == true }
        if (orphaned.isNotEmpty()) {
            return Result.failure(
                IllegalStateException("Deleted equipment is still assigned; pick a live equipment first"),
            )
        }
        return runCatchingLogged(loggingPort, TAG, "Failed to swap workout lift") {
            programRepository.saveProgram(updatedProgram)
            programChangeEventRepository.append(
                ProgramChangeEvent(
                    id = ProgramChangeEventId(UUID.randomUUID().toString()),
                    programId = program.id,
                    eventType = ProgramChangeEventType.LIFT_SWAPPED,
                    occurredAt = Instant.now(),
                    payload =
                        ProgramChangeEventPayload.LiftSwapped(
                            workoutLiftId = workoutLiftId,
                            previousLiftId = requireNotNull(previousLiftId),
                            newLiftId = newLiftId,
                        ),
                ),
            )
        }
    }

    private companion object {
        const val TAG = "SwapWorkoutLift"
    }
}
