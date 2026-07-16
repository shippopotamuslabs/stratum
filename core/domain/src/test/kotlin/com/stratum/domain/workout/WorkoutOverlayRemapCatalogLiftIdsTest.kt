package com.stratum.domain.workout

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.WorkoutLiftId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("WorkoutOverlay.remapCatalogLiftIds")
class WorkoutOverlayRemapCatalogLiftIdsTest {

    private val sourceA = LiftId("source-a")
    private val sourceB = LiftId("source-b")
    private val target = LiftId("target")
    private val unrelated = LiftId("unrelated")

    @Test
    @DisplayName("Given empty sources when remapped then returns same instance")
    fun emptySourcesIsNoOp() {
        val overlay =
            WorkoutOverlay(
                swappedLifts = mapOf(WorkoutLiftId("wl-1") to sourceA),
            )
        val result = overlay.remapCatalogLiftIds(emptySet(), target)
        assertSame(overlay, result)
    }

    @Test
    @DisplayName("Given no matching catalog ids when remapped then returns same instance")
    fun noMatchingIdsIsNoOp() {
        val overlay =
            WorkoutOverlay(
                swappedLifts = mapOf(WorkoutLiftId("wl-1") to unrelated),
                addedLifts =
                    listOf(
                        OverlayAddedLift(
                            workoutLiftId = WorkoutLiftId("wl-add"),
                            liftId = unrelated,
                        ),
                    ),
            )
        val result = overlay.remapCatalogLiftIds(setOf(sourceA, sourceB), target)
        assertSame(overlay, result)
    }

    @Test
    @DisplayName("Given swappedLifts and addedLifts on sources when remapped then all move to target")
    fun remapsSwapsAndAddedLifts() {
        val overlay =
            WorkoutOverlay(
                swappedLifts =
                    mapOf(
                        WorkoutLiftId("wl-1") to sourceA,
                        WorkoutLiftId("wl-2") to unrelated,
                    ),
                addedLifts =
                    listOf(
                        OverlayAddedLift(
                            workoutLiftId = WorkoutLiftId("wl-add-a"),
                            liftId = sourceB,
                        ),
                        OverlayAddedLift(
                            workoutLiftId = WorkoutLiftId("wl-add-u"),
                            liftId = unrelated,
                        ),
                    ),
            )

        val result = overlay.remapCatalogLiftIds(setOf(sourceA, sourceB), target)

        assertEquals(target, result.swappedLifts[WorkoutLiftId("wl-1")])
        assertEquals(unrelated, result.swappedLifts[WorkoutLiftId("wl-2")])
        assertEquals(target, result.addedLifts[0].liftId)
        assertEquals(unrelated, result.addedLifts[1].liftId)
    }

    @Test
    @DisplayName("Given addedSlots on sources when remapped then slot liftIds move to target")
    fun remapsAddedSlots() {
        val overlay =
            WorkoutOverlay(
                addedSlots =
                    listOf(
                        setSlot("ov-1", WorkoutLiftId("wl-add"), sourceA),
                        setSlot("ov-2", WorkoutLiftId("wl-add"), unrelated),
                    ),
            )

        val result = overlay.remapCatalogLiftIds(setOf(sourceA), target)

        assertEquals(target, result.addedSlots[0].liftId)
        assertEquals(unrelated, result.addedSlots[1].liftId)
        assertTrue(result.addedSlots[0].slotId == overlay.addedSlots[0].slotId)
    }

    @Test
    @DisplayName("Given multi-source set when remapped then every matching id becomes target")
    fun multiSourceRemapsAll() {
        val overlay =
            WorkoutOverlay(
                swappedLifts = mapOf(WorkoutLiftId("wl-1") to sourceA),
                addedLifts =
                    listOf(
                        OverlayAddedLift(WorkoutLiftId("wl-a"), sourceB),
                    ),
                addedSlots = listOf(setSlot("ov-1", WorkoutLiftId("wl-a"), sourceA)),
            )

        val result = overlay.remapCatalogLiftIds(setOf(sourceA, sourceB), target)

        assertEquals(target, result.swappedLifts.values.single())
        assertEquals(target, result.addedLifts.single().liftId)
        assertEquals(target, result.addedSlots.single().liftId)
    }

    private fun setSlot(
        instanceId: String,
        workoutLiftId: WorkoutLiftId,
        liftId: LiftId,
    ): SetSlot =
        SetSlot(
            slotId = SetSlotId.Overlay(OverlaySetInstanceId(instanceId)),
            workoutLiftId = workoutLiftId,
            liftId = liftId,
            position = 0,
            setType = SetType.Standard,
            isTopSet = true,
            repMin = 8,
            repMax = 12,
            rirGoal = 2,
            restSeconds = 90,
        )
}
