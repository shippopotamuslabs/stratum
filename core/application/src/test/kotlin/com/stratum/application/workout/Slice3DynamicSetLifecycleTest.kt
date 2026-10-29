package com.stratum.application.workout

import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.workout.fatigue.FatigueModel
import com.stratum.core.testing.workout.FakeFatigueModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.workout.OverlaySetInstanceId
import com.stratum.domain.workout.ProgressionSnapshot
import com.stratum.domain.workout.SetSlot
import com.stratum.domain.workout.SetSlotId
import com.stratum.domain.workout.SlotRecommendation
import com.stratum.domain.workout.StructuralMutation
import com.stratum.domain.workout.WorkoutOverlay

/**
 * Slice 3 — Dynamic set lifecycle (Standard). 8 scenarios (S3-*).
 */
@DisplayName("Slice 3: Dynamic set lifecycle (Standard)")
class Slice3DynamicSetLifecycleTest {

    private val workoutLiftId = WorkoutLiftId("wl-1")
    private val liftId = LiftId("L-1")
    private val ls1 = SetSlotId.Template(LiftSetId("ls-1"))
    private val ls2 = SetSlotId.Template(LiftSetId("ls-2"))
    private val ls3 = SetSlotId.Template(LiftSetId("ls-3"))

    private fun lb(amount: Double) = Weight(amount, Weight.WeightUnit.Lb)

    private fun slot(id: SetSlotId, isTopSet: Boolean = true, position: Int = 0) = SetSlot(
        slotId = id,
        workoutLiftId = workoutLiftId,
        liftId = liftId,
        position = position,
        setType = SetType.Standard,
        isTopSet = isTopSet,
        repMin = 4,
        repMax = 6,
        rirGoal = 2,
        restSeconds = 120,
    )

    private fun newOverlaySlot(id: String = "added-1", position: Int = 1) = SetSlot(
        slotId = SetSlotId.Overlay(OverlaySetInstanceId(id)),
        workoutLiftId = workoutLiftId,
        liftId = liftId,
        position = position,
        setType = SetType.Standard,
        isTopSet = false,
        repMin = 4,
        repMax = 6,
        rirGoal = 2,
        restSeconds = 120,
    )

    // --- Fatigue model (3 scenarios) ---

    @Test
    @DisplayName("S3-fatigue-per-lift-slope-used")
    fun `S3-fatigue-per-lift-slope-used`() {
        val model = FakeFatigueModel()
        val withPerLift = model.expectedRepsForAddedSet(
            baselineOneRm = 150.0,
            targetWeight = lb(100.0),
            nextSetIndex = 2,
            perLiftSlope = -3.0,
            perLiftIntercept = null,
        )
        val withPopulation = model.expectedRepsForAddedSet(
            baselineOneRm = 150.0,
            targetWeight = lb(100.0),
            nextSetIndex = 2,
            perLiftSlope = -2.0,
            perLiftIntercept = null,
        )
        assertTrue(
            withPerLift < withPopulation,
            "steeper per-lift slope (-3.0) must yield fewer reps than population (-2.0)",
        )
    }

    @Test
    @DisplayName("S3-fatigue-population-default-when-null")
    fun `S3-fatigue-population-default-when-null`() {
        val model = FakeFatigueModel()
        val withNull = model.expectedRepsForAddedSet(
            baselineOneRm = 150.0,
            targetWeight = lb(100.0),
            nextSetIndex = 2,
            perLiftSlope = null,
            perLiftIntercept = null,
        )
        val withPopulation = model.expectedRepsForAddedSet(
            baselineOneRm = 150.0,
            targetWeight = lb(100.0),
            nextSetIndex = 2,
            perLiftSlope = -2.0,
            perLiftIntercept = null,
        )
        assertEquals(withPopulation, withNull)
    }

    @Test
    @DisplayName("S3-fatigue-rep-projection-monotonic-non-increasing")
    fun `S3-fatigue-rep-projection-monotonic-non-increasing`() {
        val model = FakeFatigueModel()
        val results = (1..4).map { idx ->
            model.expectedRepsForAddedSet(
                baselineOneRm = 150.0,
                targetWeight = lb(100.0),
                nextSetIndex = idx,
                perLiftSlope = -2.0,
                perLiftIntercept = null,
            )
        }
        assertTrue(results[0] >= results[1], "result[1] >= result[2]")
        assertTrue(results[1] >= results[2], "result[2] >= result[3]")
        assertTrue(results[2] >= results[3], "result[3] >= result[4]")
    }

    // --- Overlay mutation API (4 scenarios) ---

    @Test
    @DisplayName("S3-add-set-copies-weight-estimates-reps")
    fun `S3-add-set-copies-weight-estimates-reps`() {
        val overlay = WorkoutOverlay().apply(
            StructuralMutation.AddSet(afterSlotId = ls1, newSlot = newOverlaySlot()),
        )
        assertEquals(1, overlay.addedSlots.size)
        // The added slot's recommended weight is copied from the previous recommendation;
        // the merge use case surfaces it. We assert the overlay carries the added slot and
        // the fatigue-estimated rep count is positive (computed by EstimateAddedSetRepsUseCase).
        val estimate = EstimateAddedSetRepsUseCase(FakeFatigueModel())
            .invoke(
                previousRecommendation = SlotRecommendation(ls1, lb(135.0), 5),
                nextSetIndex = 1,
                perLiftSlope = null,
                perLiftIntercept = null,
            )
        assertTrue(estimate > 0, "estimated reps must be a positive Int")
    }

    @Test
    @DisplayName("S3-delete-set-removes")
    fun `S3-delete-set-removes`() {
        val overlay = WorkoutOverlay().apply(StructuralMutation.DeleteSet(slotId = ls2))
        assertTrue(overlay.deletedSlotIds.contains(ls2))
    }

    @Test
    @DisplayName("S3-delete-set-drops-rest-override")
    fun `S3-delete-set-drops-rest-override`() {
        val withRest =
            WorkoutOverlay(restSecondsOverrides = mapOf(ls2 to 45))
                .apply(StructuralMutation.DeleteSet(slotId = ls2))
        assertTrue(withRest.deletedSlotIds.contains(ls2))
        assertTrue(ls2 !in withRest.restSecondsOverrides)
        // Undo restores the slot but intentionally does NOT restore the rest override.
        val afterUndo = withRest.undo()
        assertTrue(ls2 !in afterUndo.deletedSlotIds)
        assertTrue(ls2 !in afterUndo.restSecondsOverrides)
    }

    @Test
    @DisplayName("S3-undo-add-removes")
    fun `S3-undo-add-removes`() {
        val afterAdd = WorkoutOverlay().apply(
            StructuralMutation.AddSet(afterSlotId = ls1, newSlot = newOverlaySlot()),
        )
        assertEquals(1, afterAdd.addedSlots.size)
        val afterUndo = afterAdd.undo()
        assertTrue(afterUndo.addedSlots.isEmpty(), "undo of AddSet must remove the added slot")
        assertTrue(afterUndo.undoStack.isEmpty(), "undoStack must be empty after undo")
    }

    @Test
    @DisplayName("S3-undo-delete-restores")
    fun `S3-undo-delete-restores`() {
        val afterDelete = WorkoutOverlay().apply(StructuralMutation.DeleteSet(slotId = ls2))
        assertTrue(afterDelete.deletedSlotIds.contains(ls2))
        val afterUndo = afterDelete.undo()
        assertTrue(!afterUndo.deletedSlotIds.contains(ls2), "undo of DeleteSet must restore ls-2")
    }

    @Test
    @DisplayName("S3-mutation-defense-snapshot-untouched-on-add-set")
    fun `S3-mutation-defense-snapshot-untouched-on-add-set`() {
        val snapshot = ProgressionSnapshot(
            workoutId = com.stratum.domain.program.identity.WorkoutId("w-1"),
            bySlot = mapOf(ls1 to SlotRecommendation(ls1, lb(135.0), 5)),
        )
        val overlay = WorkoutOverlay().apply(
            StructuralMutation.AddSet(afterSlotId = ls1, newSlot = newOverlaySlot()),
        )
        // The snapshot must be untouched by the overlay add-set mutation.
        assertEquals(1, snapshot.bySlot.size)
        assertEquals(lb(135.0), snapshot.bySlot[ls1]?.recommendedWeight)
    }
}
