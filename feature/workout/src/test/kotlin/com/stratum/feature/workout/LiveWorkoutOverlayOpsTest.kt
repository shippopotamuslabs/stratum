package com.stratum.feature.workout

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.workout.OverlaySetInstanceId
import com.stratum.domain.workout.SetSlot
import com.stratum.domain.workout.SetSlotId
import com.stratum.domain.workout.WorkoutLiftProjection
import com.stratum.domain.workout.WorkoutOverlay
import com.stratum.domain.workout.WorkoutStructureProjection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Ensures slot resolution applies in-session [WorkoutOverlay.swappedLifts] so log/unlog
 * attribute sets to the performed lift rather than the template original.
 */
class LiveWorkoutOverlayOpsTest {

    private val workoutLiftId = WorkoutLiftId("wl-1")
    private val originalLiftId = LiftId("L-original")
    private val swappedLiftId = LiftId("L-swapped")
    private val templateSlotId = SetSlotId.Template(LiftSetId("ls-1"))
    private val overlaySlotId = SetSlotId.Overlay(OverlaySetInstanceId("added-1"))

    private val templateSlot = SetSlot(
        slotId = templateSlotId,
        workoutLiftId = workoutLiftId,
        liftId = originalLiftId,
        position = 0,
        setType = SetType.Standard,
        isTopSet = true,
        repMin = 6,
        repMax = 10,
        rirGoal = 2,
        restSeconds = 120,
    )

    private val structure = WorkoutStructureProjection(
        workoutId = WorkoutId("w-1"),
        workoutName = "Test",
        lifts = listOf(
            WorkoutLiftProjection(
                workoutLiftId = workoutLiftId,
                liftId = originalLiftId,
                liftName = "Original Lift",
                progressionScheme = ProgressionScheme.Double,
                slots = listOf(templateSlot),
            ),
        ),
    )

    @Test
    @DisplayName("resolveSlotByKey applies swappedLifts to template slot liftId")
    fun resolveSlotByKeyAppliesSwapToTemplateSlot() {
        val overlay = WorkoutOverlay(
            swappedLifts = mapOf(workoutLiftId to swappedLiftId),
        )

        val resolved = LiveWorkoutOverlayOps.resolveSlotByKey(
            structure = structure,
            overlay = overlay,
            slotKey = "ls-1",
        )

        assertEquals(swappedLiftId, resolved!!.liftId)
        assertEquals(workoutLiftId, resolved.workoutLiftId)
        assertEquals(templateSlotId, resolved.slotId)
    }

    @Test
    @DisplayName("resolveSlot applies swappedLifts to template slot liftId")
    fun resolveSlotAppliesSwapToTemplateSlot() {
        val overlay = WorkoutOverlay(
            swappedLifts = mapOf(workoutLiftId to swappedLiftId),
        )

        val resolved = LiveWorkoutOverlayOps.resolveSlot(
            structure = structure,
            overlay = overlay,
            slotId = templateSlotId,
        )

        assertEquals(swappedLiftId, resolved!!.liftId)
    }

    @Test
    @DisplayName("resolveSlotByKey without swap leaves template liftId unchanged")
    fun resolveSlotByKeyNoSwapKeepsOriginal() {
        val resolved = LiveWorkoutOverlayOps.resolveSlotByKey(
            structure = structure,
            overlay = WorkoutOverlay(),
            slotKey = "ls-1",
        )

        assertEquals(originalLiftId, resolved!!.liftId)
    }

    @Test
    @DisplayName("resolveSlotByKey applies swap to overlay-added slots still carrying original liftId")
    fun resolveSlotByKeyAppliesSwapToAddedSlots() {
        val addedSlot = SetSlot(
            slotId = overlaySlotId,
            workoutLiftId = workoutLiftId,
            liftId = originalLiftId, // stored before / without re-stamping on swap
            position = 1,
            setType = SetType.Standard,
            isTopSet = false,
            repMin = 6,
            repMax = 10,
            rirGoal = 2,
            restSeconds = 120,
        )
        val overlay = WorkoutOverlay(
            swappedLifts = mapOf(workoutLiftId to swappedLiftId),
            addedSlots = listOf(addedSlot),
        )

        val resolved = LiveWorkoutOverlayOps.resolveSlotByKey(
            structure = structure,
            overlay = overlay,
            slotKey = "added-1",
        )

        assertEquals(swappedLiftId, resolved!!.liftId)
        assertEquals(overlaySlotId, resolved.slotId)
    }

    @Test
    @DisplayName("visibleSlotsForLift applies swap to every returned slot")
    fun visibleSlotsForLiftAppliesSwap() {
        val overlay = WorkoutOverlay(
            swappedLifts = mapOf(workoutLiftId to swappedLiftId),
        )

        val slots = LiveWorkoutOverlayOps.visibleSlotsForLift(
            structure = structure,
            overlay = overlay,
            workoutLiftId = workoutLiftId,
        )

        assertEquals(1, slots.size)
        assertEquals(swappedLiftId, slots.single().liftId)
    }

    @Test
    @DisplayName("resolveSlotByKey returns null for unknown key")
    fun resolveSlotByKeyUnknownReturnsNull() {
        assertNull(
            LiveWorkoutOverlayOps.resolveSlotByKey(
                structure = structure,
                overlay = WorkoutOverlay(),
                slotKey = "missing",
            ),
        )
    }
}
