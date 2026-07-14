package com.stratum.domain.training

import com.stratum.domain.program.Program
import com.stratum.domain.program.Workout
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.schedule.CycleProgressMath
import com.stratum.domain.program.schedule.CycleSlot
import com.stratum.domain.program.schedule.CycleSlotProgress
import com.stratum.domain.program.schedule.resolvedCycleSlots

/**
 * Cycle progress from the session ledger against the program's cycle space (train + rest slots).
 *
 * [reconcileCyclePosition] is the next unconsumed **slot index** (not a completed-workout count).
 * Cycle complete requires every slot consumed (trainings and rests).
 *
 * Mid-cycle rest auto-consume is **not** applied here — those rests are written as real
 * [TrainingSessionKind.REST_DAY] rows at complete/skip time ([materializeAutoConsumedRestSessions])
 * so unskip/checkbox can reverse them like manual rest checks.
 */
object CycleProgressResolver {
    fun isWorkoutCompletedThisCycle(
        program: Program,
        sessions: List<TrainingSession>,
        workoutId: WorkoutId,
    ): Boolean =
        completedProgramWorkoutSessions(program, sessions).any { it.workoutId == workoutId }

    fun resolveNextWorkout(
        program: Program,
        sessions: List<TrainingSession>,
    ): Workout? {
        val progress = reconcileSlotProgress(program, sessions)
        val nextTraining =
            CycleProgressMath.resolveNextTraining(
                slots = program.resolvedCycleSlots(),
                consumedSlotIndices = progress.consumedSlotIndices,
                fromSlotIndex = progress.cyclePosition.coerceAtMost(program.resolvedCycleSlots().size),
            )
        return nextTraining?.workout
    }

    fun isCycleComplete(
        program: Program,
        sessions: List<TrainingSession>,
    ): Boolean {
        val slots = program.resolvedCycleSlots()
        if (slots.isEmpty()) return false
        return reconcileSlotProgress(program, sessions).isComplete
    }

    /**
     * Next unconsumed slot index, or [Program.cycleLength] / slot count when the cycle is complete
     * (before [computeCycleAdvancement] resets to 0).
     */
    fun reconcileCyclePosition(
        program: Program,
        sessions: List<TrainingSession>,
    ): Int = reconcileSlotProgress(program, sessions).cyclePosition

    fun reconcileSlotProgress(
        program: Program,
        sessions: List<TrainingSession>,
    ): CycleSlotProgress {
        val slots = program.resolvedCycleSlots()
        if (slots.isEmpty()) {
            return CycleSlotProgress(
                consumedSlotIndices = emptySet(),
                cyclePosition = 0,
                isComplete = false,
                autoConsumedRestSlotIndices = emptySet(),
            )
        }
        return CycleProgressMath.reconcile(
            slots = slots,
            completedWorkoutIdsThisCycle =
                completedProgramWorkoutSessions(program, sessions)
                    .mapNotNull { it.workoutId }
                    .toSet(),
            completedRestSlotIndicesThisCycle = completedRestSlotIndices(program, sessions, slots),
            // Ledger-only: auto-rests are materialised as sessions at write time.
            autoConsumeMidCycleRests = false,
        )
    }

    fun completedRestSlotIndicesThisCycle(
        program: Program,
        sessions: List<TrainingSession>,
    ): Set<Int> {
        val slots = program.resolvedCycleSlots()
        return completedRestSlotIndices(program, sessions, slots)
    }

    private fun completedRestSlotIndices(
        program: Program,
        sessions: List<TrainingSession>,
        slots: List<CycleSlot>,
    ): Set<Int> {
        val restIndices = slots.filterIsInstance<CycleSlot.Rest>().map { it.slotIndex }.toSet()
        return sessions
            .filter { session ->
                session.programId == program.id &&
                    session.cycleNumberSnapshot == program.currentCycleNumber &&
                    session.status == TrainingSessionStatus.COMPLETED &&
                    session.kind == TrainingSessionKind.REST_DAY
            }
            .mapNotNull { session ->
                session.cycleSlotIndexSnapshot?.takeIf { index -> index in restIndices }
            }
            .toSet()
    }

    private fun completedProgramWorkoutSessions(
        program: Program,
        sessions: List<TrainingSession>,
    ): List<TrainingSession> =
        sessions.filter { session ->
            session.programId == program.id &&
                session.cycleNumberSnapshot == program.currentCycleNumber &&
                session.status == TrainingSessionStatus.COMPLETED &&
                session.kind == TrainingSessionKind.PROGRAM_WORKOUT &&
                session.workoutId != null
        }
}
