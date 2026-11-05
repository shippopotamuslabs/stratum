package com.stratum.application.program

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
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
import com.stratum.application.lift.EquipmentRepository
import com.stratum.domain.workout.LiftSwapped

/**
 * Outcome of [SwapWorkoutLiftUseCase] (STR-243; STR-237 Route (i)).
 *
 * [Success] carries the [SwapWorkoutLiftOutcome] (with its §10 diagnostics).
 * The expected refusal branches are the record the consumer acts on — their
 * former warning breadcrumbs duplicated the branch facts and are dropped; an
 * operational load/save failure rides the [PersistenceFailed] branch as an
 * [OperationalDiagnostic] (legacy tag + rendered message, real throwable as
 * cause); the presentation boundary drains it exactly once through its
 * module's DiagnosticDrain. Coroutine cancellation is not a branch: it
 * propagates.
 */
sealed interface SwapWorkoutLiftResult {
    data class Success(val outcome: SwapWorkoutLiftOutcome) : SwapWorkoutLiftResult

    /** There is no active program to swap a lift in. */
    data object NoActiveProgram : SwapWorkoutLiftResult

    /** The target workout lift is not part of the active program. */
    data class WorkoutLiftNotFound(val workoutLiftId: WorkoutLiftId) : SwapWorkoutLiftResult

    /** A slot in the updated program still points at deleted (tombstoned) equipment. */
    data class EquipmentDeleted(val equipmentIds: List<EquipmentId>) : SwapWorkoutLiftResult

    /** The swap failed (repository / store I/O). */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : SwapWorkoutLiftResult
}

/**
 * Use case to swap the lift on a specific workout lift in the active program.
 * Modifies the template in memory and saves the updated program to persistence.
 */
class SwapWorkoutLiftUseCase @Inject constructor(
    private val programRepository: ProgramRepository,
    private val programChangeEventRepository: ProgramChangeEventRepository,
    private val equipmentRepository: com.stratum.application.lift.EquipmentRepository,
) {
    suspend operator fun invoke(
        workoutLiftId: WorkoutLiftId,
        newLiftId: LiftId,
    ): SwapWorkoutLiftResult =
        try {
            doInvoke(workoutLiftId, newLiftId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            SwapWorkoutLiftResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to swap workout lift: ${error.message}",
                        cause = error,
                    ),
                ),
            )
        }

    private suspend fun doInvoke(
        workoutLiftId: WorkoutLiftId,
        newLiftId: LiftId,
    ): SwapWorkoutLiftResult {
        val program = programRepository.getProgram()

        if (program == null) {
            // Expected domain state (no active program), not an operational fault: the
            // typed branch is the record (STR-237: the former warning breadcrumb
            // duplicated the branch fact and is dropped).
            return SwapWorkoutLiftResult.NoActiveProgram
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
        // A notes-read failure does not fail the swap — it is recorded as a
        // diagnostic and the swap proceeds with empty notes (STR-255). Cancellation
        // still propagates: it is not a degradation of this computation.
        var notesDiagnostic: SwapWorkoutLiftDiagnostic? = null
        val incomingNotes: List<String>? =
            if (targetLift != null && targetLift.liftId != newLiftId) {
                try {
                    programRepository.getWorkoutLiftPinnedNotes(workoutLiftId, newLiftId)
                        .map { it.body }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    notesDiagnostic =
                        SwapWorkoutLiftDiagnostic.PinnedNotesUnreadable(
                            workoutLiftId = workoutLiftId,
                            newLiftId = newLiftId,
                            cause = error,
                        )
                    emptyList()
                }
            } else {
                null
            }

        if (targetLift == null) {
            // Expected stale-id outcome, not an operational fault: the typed branch
            // (carrying the id) is the record (STR-237: breadcrumb dropped as a
            // duplicate of the branch fact).
            return SwapWorkoutLiftResult.WorkoutLiftNotFound(workoutLiftId)
        }

        val previousLiftId = targetLift.liftId
        val swappedLift =
            if (incomingNotes != null) {
                targetLift.copy(
                    liftId = newLiftId,
                    equipmentId = null,
                    pinnedNote = incomingNotes.firstOrNull(),
                    pinnedNotes = incomingNotes,
                )
            } else {
                targetLift.copy(liftId = newLiftId)
            }
        val updatedProgram = program.replaceWorkoutLift(swappedLift)
        // STR-105 Scope 8: this save must not persist any tombstoned slot pointer.
        val orphaned =
            updatedProgram.workouts
                .flatMap { it.workoutLifts }
                .mapNotNull { it.equipmentId }
                .distinct()
                .filter { id -> equipmentRepository.getEquipment(id)?.isTombstoned == true }
        if (orphaned.isNotEmpty()) {
            // Expected refusal (a slot still points at tombstoned equipment): the typed
            // branch (carrying the equipment ids) is the record (STR-237: breadcrumb
            // dropped as a duplicate of the branch fact).
            return SwapWorkoutLiftResult.EquipmentDeleted(orphaned)
        }
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
                        previousLiftId = previousLiftId,
                        newLiftId = newLiftId,
                    ),
            ),
        )
        return SwapWorkoutLiftResult.Success(
            SwapWorkoutLiftOutcome(diagnostics = listOfNotNull(notesDiagnostic)),
        )
    }

    private companion object {
        const val TAG = "SwapWorkoutLift"
    }
}
