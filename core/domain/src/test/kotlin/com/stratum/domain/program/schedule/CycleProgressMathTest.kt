package com.stratum.domain.program.schedule

import com.stratum.domain.program.Workout
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class CycleProgressMathTest {
    private val workouts = (0 until 6).map { index -> workout("w-$index", index) }
    private val slots = buildCycleSlots(cycleLength = 8, restSlotIndices = setOf(3, 7), workouts = workouts)

    @Test
    @DisplayName("empty ledger → pointer at 0, incomplete")
    fun emptyLedger_pointerZero() {
        val progress =
            CycleProgressMath.reconcile(
                slots = slots,
                completedWorkoutIdsThisCycle = emptySet(),
                completedRestSlotIndicesThisCycle = emptySet(),
            )
        assertEquals(0, progress.cyclePosition)
        assertFalse(progress.isComplete)
        assertTrue(progress.consumedSlotIndices.isEmpty())
    }

    @Test
    @DisplayName("in-order trainings auto-consume mid-cycle rest when prefix is solid")
    fun inOrder_autoConsumesMidCycleRest() {
        // Slots: T0 T1 T2 R3 T4 T5 T6 R7 — complete first three trainings
        val progress =
            CycleProgressMath.reconcile(
                slots = slots,
                completedWorkoutIdsThisCycle =
                    setOf(WorkoutId("w-0"), WorkoutId("w-1"), WorkoutId("w-2")),
                completedRestSlotIndicesThisCycle = emptySet(),
                autoConsumeMidCycleRests = true,
            )
        assertTrue(3 in progress.consumedSlotIndices)
        assertTrue(3 in progress.autoConsumedRestSlotIndices)
        assertEquals(4, progress.cyclePosition)
        assertFalse(progress.isComplete)
    }

    @Test
    @DisplayName("default reconcile is ledger-only (no auto-consume)")
    fun default_ledgerOnly() {
        val progress =
            CycleProgressMath.reconcile(
                slots = slots,
                completedWorkoutIdsThisCycle =
                    setOf(WorkoutId("w-0"), WorkoutId("w-1"), WorkoutId("w-2")),
                completedRestSlotIndicesThisCycle = emptySet(),
            )
        assertFalse(3 in progress.consumedSlotIndices)
        assertTrue(progress.autoConsumedRestSlotIndices.isEmpty())
        assertEquals(3, progress.cyclePosition)
    }

    @Test
    @DisplayName("out-of-order later training does not auto-consume intervening rest")
    fun outOfOrder_doesNotAutoConsumeRest() {
        // Complete w-0 and jump to w-3 (slot 4) without w-1, w-2
        val progress =
            CycleProgressMath.reconcile(
                slots = slots,
                completedWorkoutIdsThisCycle = setOf(WorkoutId("w-0"), WorkoutId("w-3")),
                completedRestSlotIndicesThisCycle = emptySet(),
                autoConsumeMidCycleRests = true,
            )
        assertFalse(3 in progress.consumedSlotIndices)
        assertTrue(progress.autoConsumedRestSlotIndices.isEmpty())
        assertEquals(1, progress.cyclePosition) // first gap
    }

    @Test
    @DisplayName("trailing rest is never auto-consumed after all trainings complete")
    fun trailingRest_manualOnly() {
        val allTrainIds = workouts.map { it.id }.toSet()
        val progress =
            CycleProgressMath.reconcile(
                slots = slots,
                completedWorkoutIdsThisCycle = allTrainIds,
                completedRestSlotIndicesThisCycle = setOf(3), // mid rest marked or auto
                autoConsumeMidCycleRests = true,
            )
        // Mid rest 3 may already be auto from trains; trailing 7 must stay open
        assertFalse(7 in progress.consumedSlotIndices)
        assertFalse(progress.isComplete)
        // pointer at 7 if 0-6 consumed
        val withMid =
            CycleProgressMath.reconcile(
                slots = slots,
                completedWorkoutIdsThisCycle = allTrainIds,
                completedRestSlotIndicesThisCycle = setOf(3),
                autoConsumeMidCycleRests = true,
            )
        assertEquals(7, withMid.cyclePosition)
    }

    @Test
    @DisplayName("all slots including trailing rest → cycle complete")
    fun allSlots_complete() {
        val progress =
            CycleProgressMath.reconcile(
                slots = slots,
                completedWorkoutIdsThisCycle = workouts.map { it.id }.toSet(),
                completedRestSlotIndicesThisCycle = setOf(3, 7),
            )
        assertTrue(progress.isComplete)
        assertEquals(8, progress.cyclePosition)
        assertEquals(8, progress.consumedSlotIndices.size)
    }

    @Test
    @DisplayName("resolveNextTraining returns first incomplete training at or after pointer")
    fun resolveNextTraining_afterMidRest() {
        val consumed = setOf(0, 1, 2, 3)
        val next = CycleProgressMath.resolveNextTraining(slots, consumed, fromSlotIndex = 3)
        assertEquals(WorkoutId("w-3"), next?.workout?.id)
        assertEquals(4, next?.slotIndex)
    }

    @Test
    @DisplayName("seven-day 3-train cycle completes only when rests marked too")
    fun sevenDay_requiresRestCompletion() {
        val three = (0 until 3).map { index -> workout("t-$index", index) }
        val seven =
            buildCycleSlots(
                cycleLength = 7,
                restSlotIndices = setOf(1, 3, 5, 6),
                workouts = three,
            )
        val trainsOnly =
            CycleProgressMath.reconcile(
                slots = seven,
                completedWorkoutIdsThisCycle = three.map { it.id }.toSet(),
                completedRestSlotIndicesThisCycle = emptySet(),
                autoConsumeMidCycleRests = true,
            )
        // Mid rests 1 and 3 auto (prefix solid as trains complete in order); 5,6 trailing manual
        assertFalse(trainsOnly.isComplete)
        assertTrue(1 in trainsOnly.autoConsumedRestSlotIndices || 1 in trainsOnly.consumedSlotIndices)
        val full =
            CycleProgressMath.reconcile(
                slots = seven,
                completedWorkoutIdsThisCycle = three.map { it.id }.toSet(),
                completedRestSlotIndicesThisCycle = setOf(1, 3, 5, 6),
            )
        assertTrue(full.isComplete)
    }

    private fun workout(id: String, position: Int): Workout =
        Workout(
            id = WorkoutId(id),
            programId = ProgramId("p-1"),
            name = id,
            weekday = null,
            position = position,
            workoutLifts = emptyList(),
        )
}
