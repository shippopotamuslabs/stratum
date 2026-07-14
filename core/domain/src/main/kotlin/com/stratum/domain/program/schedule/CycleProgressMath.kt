package com.stratum.domain.program.schedule

import com.stratum.domain.program.identity.WorkoutId

/**
 * Derived cycle progress from an explicit consumption ledger (training workout ids + rest slot
 * indices), with optional mid-cycle rest auto-consume.
 *
 * [cyclePosition] is the next unconsumed slot index, or [cycleLength] when the cycle is complete.
 */
data class CycleSlotProgress(
    val consumedSlotIndices: Set<Int>,
    val cyclePosition: Int,
    val isComplete: Boolean,
    val autoConsumedRestSlotIndices: Set<Int>,
)

/**
 * Pure cycle progress math: map completed trainings/rests onto slots, apply mid-cycle rest
 * auto-consume, derive pointer and completion.
 *
 * Rest rules (product):
 * - Mid-cycle rest may auto-consume when its ordered prefix is fully consumed and at least one
 *   training slot remains after it in the cycle.
 * - Trailing rests (after the last training slot) are **never** auto-consumed — user marks them.
 * - Out-of-order training with a gap before a rest never auto-consumes that rest.
 */
object CycleProgressMath {
    fun reconcile(
        slots: List<CycleSlot>,
        completedWorkoutIdsThisCycle: Set<WorkoutId>,
        completedRestSlotIndicesThisCycle: Set<Int>,
        /** Default false — write-time paths opt in; read paths stay ledger-only. */
        autoConsumeMidCycleRests: Boolean = false,
    ): CycleSlotProgress {
        require(slots.isNotEmpty()) { "slots must not be empty" }
        val cycleLength = slots.size
        require(slots.map { it.slotIndex } == (0 until cycleLength).toList()) {
            "slots must be contiguous indices 0..${cycleLength - 1}"
        }

        val trainingSlotByWorkoutId =
            slots
                .filterIsInstance<CycleSlot.Training>()
                .associate { training -> training.workout.id to training.slotIndex }

        val explicitConsumed = mutableSetOf<Int>()
        for (workoutId in completedWorkoutIdsThisCycle) {
            val slotIndex = trainingSlotByWorkoutId[workoutId]
            if (slotIndex != null) {
                explicitConsumed += slotIndex
            }
        }
        for (restIndex in completedRestSlotIndicesThisCycle) {
            if (restIndex in 0 until cycleLength && slots[restIndex] is CycleSlot.Rest) {
                explicitConsumed += restIndex
            }
        }

        val autoConsumed =
            if (autoConsumeMidCycleRests) {
                autoConsumeMidCycleRests(slots, explicitConsumed)
            } else {
                emptySet()
            }
        val consumed = explicitConsumed + autoConsumed
        val isComplete = consumed.size == cycleLength
        val cyclePosition =
            if (isComplete) {
                cycleLength
            } else {
                (0 until cycleLength).first { index -> index !in consumed }
            }
        return CycleSlotProgress(
            consumedSlotIndices = consumed,
            cyclePosition = cyclePosition,
            isComplete = isComplete,
            autoConsumedRestSlotIndices = autoConsumed,
        )
    }

    /**
     * Rest slots that may be auto-marked: ordered prefix solid, and the rest is not trailing
     * (there is still a training slot later in the cycle).
     */
    fun autoConsumeMidCycleRests(
        slots: List<CycleSlot>,
        alreadyConsumed: Set<Int>,
    ): Set<Int> {
        val lastTrainingIndex =
            slots.indexOfLast { slot -> slot is CycleSlot.Training }.takeIf { it >= 0 }
                ?: return emptySet()
        val consumed = alreadyConsumed.toMutableSet()
        val auto = mutableSetOf<Int>()
        // Walk low→high so chained mid-cycle rests with solid prefixes can fill.
        for (slot in slots.filterIsInstance<CycleSlot.Rest>()) {
            val r = slot.slotIndex
            val eligible =
                r !in consumed &&
                    r <= lastTrainingIndex && // trailing rests (after last train) stay manual
                    (0 until r).all { index -> index in consumed }
            if (eligible) {
                consumed += r
                auto += r
            }
        }
        return auto
    }

    /** First incomplete training slot at or after [fromSlotIndex], or any incomplete if none after. */
    fun resolveNextTraining(
        slots: List<CycleSlot>,
        consumedSlotIndices: Set<Int>,
        fromSlotIndex: Int = 0,
    ): CycleSlot.Training? {
        val trainings = slots.filterIsInstance<CycleSlot.Training>()
        val incomplete = trainings.filter { training -> training.slotIndex !in consumedSlotIndices }
        if (incomplete.isEmpty()) return null
        return incomplete.firstOrNull { training -> training.slotIndex >= fromSlotIndex }
            ?: incomplete.first()
    }
}
