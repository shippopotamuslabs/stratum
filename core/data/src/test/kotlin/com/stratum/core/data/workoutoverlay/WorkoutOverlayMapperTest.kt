package com.stratum.core.data.workoutoverlay

import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.identity.WorkoutSupersetGroupId
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.workout.LoggedSetValue
import com.stratum.domain.workout.OverlayAddedLift
import com.stratum.domain.workout.OverlaySetInstanceId
import com.stratum.domain.workout.SetSlot
import com.stratum.domain.workout.SetSlotId
import com.stratum.domain.workout.SlotRecommendation
import com.stratum.domain.workout.StructuralMutation
import com.stratum.domain.workout.WorkoutOverlay
import com.stratum.domain.workout.myorep.MyoRepKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Round-trip tests for [WorkoutOverlay] ↔ [WorkoutOverlayDto]. `recommendations` is dropped
 * on the DTO round-trip (recomputed by the ViewModel); every other field — including the
 * polymorphic [SetSlotId] keys, the sealed [StructuralMutation] undo stack, nullable superset
 * overrides, and nullable `orderedLiftIds` — must survive intact.
 */
@DisplayName("WorkoutOverlay ↔ DTO mapper")
class WorkoutOverlayMapperTest {

    @Test
    @DisplayName("empty overlay round-trips to empty overlay")
    fun emptyRoundTrip() {
        val overlay = WorkoutOverlay()
        val restored = overlay.toDto().toDomain()
        assertEquals(overlay, restored)
    }

    @Test
    @DisplayName("overlay with both SetSlotId variants and all StructuralMutation kinds round-trips")
    fun fullOverlayRoundTrip() {
        val templateSlotId = SetSlotId.Template(LiftSetId("ls-1"))
        val overlaySlotId = SetSlotId.Overlay(OverlaySetInstanceId("added-1"))
        val workoutLiftId = WorkoutLiftId("wl-1")
        val liftId = LiftId("back-squat")

        val addedSlot = SetSlot(
            slotId = overlaySlotId,
            workoutLiftId = workoutLiftId,
            liftId = liftId,
            position = 3,
            setType = SetType.DropSet,
            isTopSet = false,
            repMin = 0,
            repMax = 0,
            rirGoal = 0,
            restSeconds = 60,
            myoRepKind = MyoRepKind.RepFloor,
        )

        val overlay = WorkoutOverlay(
            loggedSets = mapOf(
                templateSlotId to LoggedSetValue(templateSlotId, Weight(135.0, Weight.WeightUnit.Lb), reps = 8, rir = 2),
            ),
            addedSlots = listOf(addedSlot),
            deletedSlotIds = setOf(SetSlotId.Template(LiftSetId("ls-2"))),
            undoStack = listOf(
                StructuralMutation.AddSet(afterSlotId = templateSlotId, newSlot = addedSlot),
                StructuralMutation.DeleteSet(slotId = SetSlotId.Template(LiftSetId("ls-9"))),
                StructuralMutation.GenerateMyoRepMiniSet(activationSlotId = templateSlotId, newSlot = addedSlot),
                StructuralMutation.AddDropSet(afterSlotId = templateSlotId, newSlot = addedSlot),
            ),
            dynamicBaselines = mapOf(
                overlaySlotId to SlotRecommendation(
                    slotId = overlaySlotId,
                    recommendedWeight = Weight(95.0, Weight.WeightUnit.Kg),
                    baselineExpectedReps = 5,
                    previousWeight = Weight(90.0, Weight.WeightUnit.Kg),
                    previousReps = 6,
                ),
            ),
            swappedLifts = mapOf(workoutLiftId to LiftId("front-squat")),
            deletedLiftIds = setOf(WorkoutLiftId("wl-gone")),
            addedLifts = listOf(
                OverlayAddedLift(
                    workoutLiftId = WorkoutLiftId("wl-added"),
                    liftId = LiftId("db-row"),
                    progressionScheme = ProgressionScheme.Double,
                    supersetGroupId = WorkoutSupersetGroupId("group-b"),
                    useRestTimerBetweenLifts = true,
                ),
            ),
            supersetGroupIdOverrides = mapOf(
                workoutLiftId to WorkoutSupersetGroupId("group-a"), // override to a group
                WorkoutLiftId("wl-unlinked") to null, // override = "removed from superset"
            ),
            useRestTimerBetweenLiftsOverrides = mapOf(workoutLiftId to false),
            restSecondsOverrides = mapOf(templateSlotId to 45, overlaySlotId to 75),
            orderedLiftIds = listOf("wl-1", "wl-2"),
        )

        val restored = overlay.toDto().toDomain()

        // recommendations is intentionally not persisted — restored must be empty.
        assertEquals(overlay.copy(recommendations = emptyMap()), restored)
    }

    @Test
    @DisplayName("null orderedLiftIds round-trips as null")
    fun nullOrderedLiftIdsRoundTrip() {
        val overlay = WorkoutOverlay(orderedLiftIds = null)
        val restored = overlay.toDto().toDomain()
        assertNull(restored.orderedLiftIds)
    }

    @Test
    @DisplayName("recommendations are dropped on round-trip")
    fun recommendationsDropped() {
        val slotId = SetSlotId.Template(LiftSetId("ls-1"))
        val overlay = WorkoutOverlay(
            recommendations = mapOf(slotId to com.stratum.domain.workout.LiveSetRecommendation(Weight(100.0, Weight.WeightUnit.Lb), reps = 5)),
        )
        val restored = overlay.toDto().toDomain()
        assertTrue(restored.recommendations.isEmpty())
    }

    @Test
    @DisplayName("blank id strings in DTO are skipped defensively")
    fun blankIdsSkipped() {
        // Hand-built DTO with a blank workoutLiftId in swappedLifts and a blank liftId — the
        // mapper must drop the entry rather than crash the value-class `init`.
        val dto = WorkoutOverlayDto(
            swappedLifts = listOf(
                SwappedLiftEntryDto(workoutLiftId = "wl-1", liftId = "front-squat"),
                SwappedLiftEntryDto(workoutLiftId = "", liftId = "should-not-appear"),
                SwappedLiftEntryDto(workoutLiftId = "wl-3", liftId = "   "),
            ),
        )
        val restored = dto.toDomain()
        assertEquals(1, restored.swappedLifts.size)
        assertNotNull(restored.swappedLifts[WorkoutLiftId("wl-1")])
    }
}
