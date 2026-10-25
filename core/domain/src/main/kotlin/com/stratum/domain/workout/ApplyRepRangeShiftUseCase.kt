package com.stratum.domain.workout

import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.program.ProgramRepository
import com.stratum.domain.program.ProgressionRepRangePolicy
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.domain.training.ProgramChangeEventPayload
import com.stratum.domain.training.ProgramChangeEventRepository
import com.stratum.domain.training.ProgramChangeEventType
import com.stratum.domain.training.identity.ProgramChangeEventId
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Applies a post-workout rep-range shift to a single [WorkoutLift] in the active program.
 *
 * Re-reads the live template, verifies the expected current band, and re-runs
 * [evaluateRepRangeShift] so stale Accepts no-op. Serializes mutations via [writeMutex]
 * so concurrent Accepts cannot lose updates. Event append failures after a successful
 * save are logged but do not fail the apply (template is already durable).
 */
@Singleton
class ApplyRepRangeShiftUseCase @Inject constructor(
    private val programRepository: ProgramRepository,
    private val programChangeEventRepository: ProgramChangeEventRepository,
    private val loggingPort: LoggingPort,
) {
    private val writeMutex = Mutex()

    suspend operator fun invoke(
        workoutLiftId: WorkoutLiftId,
        expectedCurrentRepMin: Int,
        expectedCurrentRepMax: Int,
        proposedRepMin: Int,
        proposedRepMax: Int,
        fatigueSlope: Double,
        setCount: Int,
    ): ApplyRepRangeShiftResult {
        if (proposedRepMin <= 0) {
            return ApplyRepRangeShiftResult.ProposalMismatch("proposedRepMin must be positive")
        }
        if (proposedRepMax < proposedRepMin) {
            return ApplyRepRangeShiftResult.ProposalMismatch("proposedRepMax must be ≥ proposedRepMin")
        }

        return try {
            writeMutex.withLock {
                applyLocked(
                    workoutLiftId = workoutLiftId,
                    expectedCurrentRepMin = expectedCurrentRepMin,
                    expectedCurrentRepMax = expectedCurrentRepMax,
                    proposedRepMin = proposedRepMin,
                    proposedRepMax = proposedRepMax,
                    fatigueSlope = fatigueSlope,
                    setCount = setCount,
                )
            }
        } catch (t: Throwable) {
            loggingPort.error(TAG, "Failed to apply rep-range shift for ${workoutLiftId.value}", t)
            ApplyRepRangeShiftResult.Failure(t)
        }
    }

    private suspend fun applyLocked(
        workoutLiftId: WorkoutLiftId,
        expectedCurrentRepMin: Int,
        expectedCurrentRepMax: Int,
        proposedRepMin: Int,
        proposedRepMax: Int,
        fatigueSlope: Double,
        setCount: Int,
    ): ApplyRepRangeShiftResult {
        val program =
            programRepository.getProgram()
                ?: return ApplyRepRangeShiftResult.ProgramNotFound

        val hit =
            program.workouts.firstNotNullOfOrNull { workout ->
                workout.workoutLifts
                    .firstOrNull { it.id == workoutLiftId }
                    ?.let { workout to it }
            } ?: return ApplyRepRangeShiftResult.LiftNotFound

        val (parentWorkout, targetLift) = hit
        val mismatch =
            validateProposal(
                targetLift = targetLift,
                workoutLiftId = workoutLiftId,
                expectedCurrentRepMin = expectedCurrentRepMin,
                expectedCurrentRepMax = expectedCurrentRepMax,
                proposedRepMin = proposedRepMin,
                proposedRepMax = proposedRepMax,
                fatigueSlope = fatigueSlope,
                setCount = setCount,
            )
        if (mismatch != null) return mismatch

        val updatedLift =
            withRepBand(
                lift = targetLift,
                repMin = proposedRepMin,
                repMax = proposedRepMax,
            )
        programRepository.saveProgram(program.replaceWorkoutLift(updatedLift))
        // Template is durable; event is audit-only — do not fail Accept if append breaks.
        runCatching {
            programChangeEventRepository.append(
                ProgramChangeEvent(
                    id = ProgramChangeEventId(UUID.randomUUID().toString()),
                    programId = program.id,
                    eventType = ProgramChangeEventType.WORKOUT_EDITED,
                    occurredAt = Instant.now(),
                    payload = ProgramChangeEventPayload.WorkoutEdited(parentWorkout.id),
                ),
            )
        }.onFailure { error ->
            loggingPort.error(
                TAG,
                "Program saved but WORKOUT_EDITED event failed for ${workoutLiftId.value}: " +
                    "${error.message}",
                error,
            )
        }

        return ApplyRepRangeShiftResult.Success
    }

    private fun validateProposal(
        targetLift: WorkoutLift,
        workoutLiftId: WorkoutLiftId,
        expectedCurrentRepMin: Int,
        expectedCurrentRepMax: Int,
        proposedRepMin: Int,
        proposedRepMax: Int,
        fatigueSlope: Double,
        setCount: Int,
    ): ApplyRepRangeShiftResult.ProposalMismatch? {
        val liveBand = currentBand(targetLift)
        if (liveBand.first != expectedCurrentRepMin || liveBand.second != expectedCurrentRepMax) {
            return ApplyRepRangeShiftResult.ProposalMismatch(
                "Rep band changed since proposal " +
                    "(live ${liveBand.first}–${liveBand.second}, " +
                    "expected $expectedCurrentRepMin–$expectedCurrentRepMax)",
            )
        }

        val liveSetCount = targetLift.sets.size
        if (setCount > 0 && setCount != liveSetCount) {
            return ApplyRepRangeShiftResult.ProposalMismatch(
                "Set count changed since proposal (live $liveSetCount, expected $setCount)",
            )
        }
        val evaluation =
            evaluateRepRangeShift(
                RepRangeShiftInput(
                    fatigueSlope = fatigueSlope,
                    setCount = liveSetCount,
                    currentRepMin = liveBand.first,
                    currentRepMax = liveBand.second,
                ),
            ) ?: return ApplyRepRangeShiftResult.ProposalMismatch(
                "Rep-range shift no longer warranted for ${workoutLiftId.value}",
            )

        if (
            evaluation.proposedRepMin != proposedRepMin ||
            evaluation.proposedRepMax != proposedRepMax
        ) {
            return ApplyRepRangeShiftResult.ProposalMismatch(
                "Proposed band mismatch " +
                    "(live eval ${evaluation.proposedRepMin}–${evaluation.proposedRepMax}, " +
                    "card $proposedRepMin–$proposedRepMax)",
            )
        }
        return null
    }

    /**
     * Applies [repMin]/[repMax] under the lift's scheme while preserving set ids, RIR, rest,
     * and set types.
     */
    internal fun withRepBand(
        lift: WorkoutLift,
        repMin: Int,
        repMax: Int,
    ): WorkoutLift {
        val ordered = lift.sets.sortedBy { it.position }
        if (ordered.isEmpty()) return lift
        val specs =
            ProgressionRepRangePolicy.applyBand(
                scheme = lift.progressionScheme,
                setCount = ordered.size,
                repMin = repMin,
                repMax = repMax,
            )
        val rewritten =
            ordered.zip(specs).map { (set, spec) ->
                set.copy(
                    repMin = spec.repMin,
                    repMax = spec.repMax,
                    isTopSet = spec.isTopSet,
                )
            }
        return lift.copy(sets = rewritten)
    }

    private fun currentBand(workoutLift: WorkoutLift): Pair<Int, Int> {
        val specs =
            workoutLift.sets
                .sortedBy { it.position }
                .map { set ->
                    ProgressionRepRangePolicy.WorkingSetRepSpec(
                        repMin = set.repMin,
                        repMax = set.repMax,
                        isTopSet = set.isTopSet,
                    )
                }
        return ProgressionRepRangePolicy.resolveSharedBand(specs)
    }

    private companion object {
        const val TAG = "ApplyRepRangeShift"
    }
}
