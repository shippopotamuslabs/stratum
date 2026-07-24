package com.stratum.feature.workout

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.identity.WorkoutSupersetGroupId
import com.stratum.domain.workout.MergeRenderedWorkoutUseCase
import com.stratum.domain.workout.OverlayAddedLift
import com.stratum.domain.workout.OverlaySetInstanceId
import com.stratum.domain.workout.SetSlot
import com.stratum.domain.workout.SetSlotId
import com.stratum.domain.workout.WorkoutLiftProjection
import com.stratum.domain.workout.WorkoutOverlay
import com.stratum.domain.workout.WorkoutStructureProjection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test
    @DisplayName("merge applies swappedLifts to session-added lift name and slot liftId")
    fun mergeAppliesSwapToSessionAddedLifts() {
        val addedWl = WorkoutLiftId("wl-added")
        val addedSlot = SetSlot(
            slotId = overlaySlotId,
            workoutLiftId = addedWl,
            liftId = originalLiftId,
            position = 0,
            setType = SetType.Standard,
            isTopSet = true,
            repMin = 8,
            repMax = 12,
            rirGoal = 2,
            restSeconds = 120,
        )
        val emptyStructure = WorkoutStructureProjection(
            workoutId = WorkoutId("ts-adhoc"),
            workoutName = "Empty Workout",
            lifts = emptyList(),
        )
        val overlay = WorkoutOverlay(
            swappedLifts = mapOf(addedWl to swappedLiftId),
            addedLifts = listOf(
                com.stratum.domain.workout.OverlayAddedLift(
                    workoutLiftId = addedWl,
                    liftId = originalLiftId,
                ),
            ),
            addedSlots = listOf(addedSlot),
            dynamicBaselines = mapOf(
                overlaySlotId to com.stratum.domain.workout.SlotRecommendation(
                    slotId = overlaySlotId,
                    recommendedWeight = com.stratum.domain.lift.Weight(0.0, com.stratum.domain.lift.Weight.WeightUnit.Lb),
                    baselineExpectedReps = 8,
                ),
            ),
        )
        val liftsById = mapOf(
            originalLiftId to com.stratum.domain.lift.Lift(
                id = originalLiftId,
                name = "Original Lift",
                movementPatterns = emptySet(),
                primaryMuscles = emptySet(),
                secondaryMuscles = emptySet(),
                incrementTiers = emptyList(),
                presetTags = emptySet(),
                enabled = true,
            ),
            swappedLiftId to com.stratum.domain.lift.Lift(
                id = swappedLiftId,
                name = "Swapped Lift",
                movementPatterns = emptySet(),
                primaryMuscles = emptySet(),
                secondaryMuscles = emptySet(),
                incrementTiers = emptyList(),
                presetTags = emptySet(),
                enabled = true,
            ),
        )

        val rendered = LiveWorkoutOverlayOps.merge(
            structure = emptyStructure,
            overlay = overlay,
            snapshotBySlot = emptyMap(),
            liftsById = liftsById,
            liftNamesById = liftsById.mapValues { it.value.name },
            mergeRenderedWorkout = com.stratum.domain.workout.MergeRenderedWorkoutUseCase(),
        )

        assertEquals(1, rendered.lifts.size)
        assertEquals(swappedLiftId, rendered.lifts.single().liftId)
        assertEquals("Swapped Lift", rendered.lifts.single().liftName)
        assertEquals(swappedLiftId, rendered.lifts.single().sets.single().liftId)
    }


    @Test
    @DisplayName("resolveSlot applies restSecondsOverrides")
    fun resolveSlotAppliesRestSecondsOverride() {
        val overlay =
            WorkoutOverlay(
                restSecondsOverrides = mapOf(templateSlotId to 45),
            )

        val resolved =
            LiveWorkoutOverlayOps.resolveSlot(
                structure = structure,
                overlay = overlay,
                slotId = templateSlotId,
            )

        assertEquals(45, resolved!!.restSeconds)
    }

    @Test
    @DisplayName("visibleSlotsForLift applies restSecondsOverrides")
    fun visibleSlotsApplyRestSecondsOverride() {
        val overlay =
            WorkoutOverlay(
                restSecondsOverrides = mapOf(templateSlotId to 30),
            )

        val slots =
            LiveWorkoutOverlayOps.visibleSlotsForLift(
                structure = structure,
                overlay = overlay,
                workoutLiftId = workoutLiftId,
            )

        assertEquals(30, slots.single().restSeconds)
    }

    @Test
    @DisplayName("merge: key-present null override clears template superset group")
    fun mergeNullOverrideClearsTemplateGroup() {
        val groupId = WorkoutSupersetGroupId("ss-1")
        val withGroup =
            structure.copy(
                lifts =
                    structure.lifts.map { lift ->
                        lift.copy(supersetGroupId = groupId)
                    },
            )
        val overlay =
            WorkoutOverlay(
                supersetGroupIdOverrides = mapOf(workoutLiftId to null),
            )

        val rendered =
            LiveWorkoutOverlayOps.merge(
                structure = withGroup,
                overlay = overlay,
                snapshotBySlot = emptyMap(),
                liftsById = emptyMap(),
                liftNamesById = emptyMap(),
                mergeRenderedWorkout = MergeRenderedWorkoutUseCase(),
            )

        assertNull(rendered.lifts.single().supersetGroupId)
    }

    @Test
    @DisplayName("merge: key-absent leaves template superset group")
    fun mergeAbsentOverrideKeepsTemplateGroup() {
        val groupId = WorkoutSupersetGroupId("ss-1")
        val withGroup =
            structure.copy(
                lifts =
                    structure.lifts.map { lift ->
                        lift.copy(supersetGroupId = groupId)
                    },
            )

        val rendered =
            LiveWorkoutOverlayOps.merge(
                structure = withGroup,
                overlay = WorkoutOverlay(),
                snapshotBySlot = emptyMap(),
                liftsById = emptyMap(),
                liftNamesById = emptyMap(),
                mergeRenderedWorkout = MergeRenderedWorkoutUseCase(),
            )

        assertEquals(groupId, rendered.lifts.single().supersetGroupId)
    }

    @Test
    @DisplayName("removeLift stamps cleared group onto remaining OverlayAddedLift (dual-source)")
    fun removeLiftClearsSessionAddedSurvivorGroupOnAddedLift() {
        val groupId = WorkoutSupersetGroupId("ss-session")
        val wlA = WorkoutLiftId("wl-added-a")
        val wlB = WorkoutLiftId("wl-added-b")
        val overlay =
            WorkoutOverlay(
                addedLifts =
                    listOf(
                        OverlayAddedLift(
                            workoutLiftId = wlA,
                            liftId = LiftId("L-a"),
                            supersetGroupId = groupId,
                        ),
                        OverlayAddedLift(
                            workoutLiftId = wlB,
                            liftId = LiftId("L-b"),
                            supersetGroupId = groupId,
                        ),
                    ),
            )

        val result =
            LiveWorkoutOverlayOps.removeLift(
                overlay = overlay,
                workoutLiftId = wlB,
                cleanedGroupOverrides = mapOf(wlA to null),
                cleanedRestOverrides = emptyMap(),
                resolveSlot = { null },
                baselineLiftIdsInOrder = emptyList(),
            )

        assertEquals(1, result.addedLifts.size)
        assertNull(result.addedLifts.single().supersetGroupId)
        assertTrue(result.supersetGroupIdOverrides.containsKey(wlA))
        assertNull(result.supersetGroupIdOverrides[wlA])
    }

    @Test
    @DisplayName("differentialSupersetOverrides writes only changed keys including null clears")
    fun differentialWritesOnlyChangedIncludingNull() {
        val prior =
            listOf(
                LiveWorkoutOverlayOps.LinkableSupersetFields("wl-1", "g", false),
                LiveWorkoutOverlayOps.LinkableSupersetFields("wl-2", "g", true),
                LiveWorkoutOverlayOps.LinkableSupersetFields("wl-3", null, false),
            )
        val updated =
            listOf(
                LiveWorkoutOverlayOps.LinkableSupersetFields("wl-1", null, false),
                LiveWorkoutOverlayOps.LinkableSupersetFields("wl-2", null, false),
                LiveWorkoutOverlayOps.LinkableSupersetFields("wl-3", null, false),
            )

        val (groups, rest) =
            LiveWorkoutOverlayOps.differentialSupersetOverrides(
                prior = prior,
                updated = updated,
                existingGroupOverrides = emptyMap(),
                existingRestOverrides = emptyMap(),
            )

        assertTrue(groups.containsKey(WorkoutLiftId("wl-1")))
        assertNull(groups[WorkoutLiftId("wl-1")])
        assertTrue(groups.containsKey(WorkoutLiftId("wl-2")))
        assertNull(groups[WorkoutLiftId("wl-2")])
        assertFalse(groups.containsKey(WorkoutLiftId("wl-3")))
        assertTrue(rest.containsKey(WorkoutLiftId("wl-2")))
        assertEquals(false, rest[WorkoutLiftId("wl-2")])
        assertFalse(rest.containsKey(WorkoutLiftId("wl-3")))
    }

    @Test
    @DisplayName("orderLiftSlots inserts myo minis after activation before trailing standards")
    fun orderLiftSlotsInsertsMinisAfterMyoActivation() {
        val myoAct =
            SetSlot(
                slotId = SetSlotId.Template(LiftSetId("ls-myo")),
                workoutLiftId = workoutLiftId,
                liftId = originalLiftId,
                position = 0,
                setType = SetType.MyoRep,
                isTopSet = true,
                repMin = 10,
                repMax = 15,
                rirGoal = 1,
                restSeconds = 120,
            )
        val std1 =
            SetSlot(
                slotId = SetSlotId.Template(LiftSetId("ls-s1")),
                workoutLiftId = workoutLiftId,
                liftId = originalLiftId,
                position = 1,
                setType = SetType.Standard,
                isTopSet = false,
                repMin = 8,
                repMax = 12,
                rirGoal = 2,
                restSeconds = 120,
            )
        val std2 =
            SetSlot(
                slotId = SetSlotId.Template(LiftSetId("ls-s2")),
                workoutLiftId = workoutLiftId,
                liftId = originalLiftId,
                position = 2,
                setType = SetType.Standard,
                isTopSet = false,
                repMin = 8,
                repMax = 12,
                rirGoal = 2,
                restSeconds = 120,
            )
        val mini1 =
            SetSlot(
                slotId = SetSlotId.Overlay(OverlaySetInstanceId("mini-1")),
                workoutLiftId = workoutLiftId,
                liftId = originalLiftId,
                position = 1,
                setType = SetType.MyoRep,
                isTopSet = false,
                repMin = 0,
                repMax = 0,
                rirGoal = 0,
                restSeconds = 15,
            )
        val mini2 =
            SetSlot(
                slotId = SetSlotId.Overlay(OverlaySetInstanceId("mini-2")),
                workoutLiftId = workoutLiftId,
                liftId = originalLiftId,
                position = 2,
                setType = SetType.MyoRep,
                isTopSet = false,
                repMin = 0,
                repMax = 0,
                rirGoal = 0,
                restSeconds = 15,
            )

        val ordered =
            LiveIntensifierOverlayOps.orderLiftSlots(
                templateSlots = listOf(myoAct, std1, std2),
                addedSlots = listOf(mini1, mini2),
            )

        assertEquals(
            listOf(
                SetType.MyoRep,
                SetType.MyoRep,
                SetType.MyoRep,
                SetType.Standard,
                SetType.Standard,
            ),
            ordered.map { it.setType },
        )
        assertEquals(
            listOf("ls-myo", "mini-1", "mini-2", "ls-s1", "ls-s2"),
            ordered.map { LiveWorkoutOverlayOps.slotKey(it.slotId) },
        )
        assertEquals(listOf(0, 1, 2, 3, 4), ordered.map { it.position })
    }

    @Test
    @DisplayName("cascade delete activation removes minis but keeps trailing standards")
    fun cascadeDeleteMyoActivationKeepsTrailingStandards() {
        val myoActId = SetSlotId.Template(LiftSetId("ls-myo"))
        val stdId = SetSlotId.Template(LiftSetId("ls-s1"))
        val miniId = SetSlotId.Overlay(OverlaySetInstanceId("mini-1"))
        val myoStruct =
            WorkoutStructureProjection(
                workoutId = WorkoutId("w-1"),
                workoutName = "Test",
                lifts =
                    listOf(
                        WorkoutLiftProjection(
                            workoutLiftId = workoutLiftId,
                            liftId = originalLiftId,
                            liftName = "Lift",
                            progressionScheme = ProgressionScheme.Double,
                            slots =
                                listOf(
                                    SetSlot(
                                        slotId = myoActId,
                                        workoutLiftId = workoutLiftId,
                                        liftId = originalLiftId,
                                        position = 0,
                                        setType = SetType.MyoRep,
                                        isTopSet = true,
                                        repMin = 10,
                                        repMax = 15,
                                        rirGoal = 1,
                                        restSeconds = 120,
                                    ),
                                    SetSlot(
                                        slotId = stdId,
                                        workoutLiftId = workoutLiftId,
                                        liftId = originalLiftId,
                                        position = 1,
                                        setType = SetType.Standard,
                                        isTopSet = false,
                                        repMin = 8,
                                        repMax = 12,
                                        rirGoal = 2,
                                        restSeconds = 120,
                                    ),
                                ),
                        ),
                    ),
            )
        val overlay =
            WorkoutOverlay(
                addedSlots =
                    listOf(
                        SetSlot(
                            slotId = miniId,
                            workoutLiftId = workoutLiftId,
                            liftId = originalLiftId,
                            position = 1,
                            setType = SetType.MyoRep,
                            isTopSet = false,
                            repMin = 0,
                            repMax = 0,
                            rirGoal = 0,
                            restSeconds = 15,
                        ),
                    ),
            )

        val targets =
            LiveIntensifierOverlayOps.cascadeDeleteTargets(
                structure = myoStruct,
                overlay = overlay,
                slotId = myoActId,
            )

        assertEquals(setOf(myoActId, miniId), targets.toSet())
        assertFalse(targets.contains(stdId))
    }

    @Test
    @DisplayName("myo lifecycle spawns mini after activation even when standards follow")
    fun myoLifecycleSpawnsMiniWithTrailingStandards() {
        val myoActId = SetSlotId.Template(LiftSetId("ls-myo"))
        val stdId = SetSlotId.Template(LiftSetId("ls-s1"))
        val myoStruct =
            WorkoutStructureProjection(
                workoutId = WorkoutId("w-1"),
                workoutName = "Test",
                lifts =
                    listOf(
                        WorkoutLiftProjection(
                            workoutLiftId = workoutLiftId,
                            liftId = originalLiftId,
                            liftName = "Lift",
                            progressionScheme = ProgressionScheme.Double,
                            slots =
                                listOf(
                                    SetSlot(
                                        slotId = myoActId,
                                        workoutLiftId = workoutLiftId,
                                        liftId = originalLiftId,
                                        position = 0,
                                        setType = SetType.MyoRep,
                                        isTopSet = true,
                                        repMin = 10,
                                        repMax = 15,
                                        rirGoal = 1,
                                        restSeconds = 120,
                                        myoRepKind = com.stratum.domain.workout.myorep.MyoRepKind.RepFloor,
                                    ),
                                    SetSlot(
                                        slotId = stdId,
                                        workoutLiftId = workoutLiftId,
                                        liftId = originalLiftId,
                                        position = 1,
                                        setType = SetType.Standard,
                                        isTopSet = false,
                                        repMin = 8,
                                        repMax = 12,
                                        rirGoal = 2,
                                        restSeconds = 120,
                                    ),
                                ),
                        ),
                    ),
            )
        val overlay =
            WorkoutOverlay(
                loggedSets =
                    mapOf(
                        myoActId to
                            com.stratum.domain.workout.LoggedSetValue(
                                slotId = myoActId,
                                weight = com.stratum.domain.lift.Weight(65.0, com.stratum.domain.lift.Weight.WeightUnit.Lb),
                                reps = 11,
                                rir = 1,
                            ),
                    ),
            )

        val next =
            LiveIntensifierOverlayOps.applyMyoLifecycleAfterLog(
                structure = myoStruct,
                overlay = overlay,
                loggedSlotId = myoActId,
            )

        assertEquals(1, next.addedSlots.size, "expected one mini after activation")
        val mini = next.addedSlots.single()
        assertEquals(SetType.MyoRep, mini.setType)
        assertFalse(mini.isTopSet)
        // Mini inherits the activation set's programmed rest (120 here).
        assertEquals(120, mini.restSeconds)
        assertTrue(next.dynamicBaselines.containsKey(mini.slotId), "mini needs baseline to render")

        val ordered =
            LiveIntensifierOverlayOps.orderLiftSlots(
                templateSlots = myoStruct.lifts.single().slots,
                addedSlots = next.addedSlots,
            )
        assertEquals(
            listOf(SetType.MyoRep, SetType.MyoRep, SetType.Standard),
            ordered.map { it.setType },
        )
    }

    @Test
    @DisplayName("myo mini inherits custom activation rest (not hard-coded 15s)")
    fun myoMiniInheritsCustomActivationRest() {
        val myoActId = SetSlotId.Template(LiftSetId("ls-myo"))
        val myoStruct =
            WorkoutStructureProjection(
                workoutId = WorkoutId("w-1"),
                workoutName = "Test",
                lifts =
                    listOf(
                        WorkoutLiftProjection(
                            workoutLiftId = workoutLiftId,
                            liftId = originalLiftId,
                            liftName = "Lift",
                            progressionScheme = ProgressionScheme.Double,
                            slots =
                                listOf(
                                    SetSlot(
                                        slotId = myoActId,
                                        workoutLiftId = workoutLiftId,
                                        liftId = originalLiftId,
                                        position = 0,
                                        setType = SetType.MyoRep,
                                        isTopSet = true,
                                        repMin = 10,
                                        repMax = 15,
                                        rirGoal = 1,
                                        restSeconds = 30,
                                        myoRepKind = com.stratum.domain.workout.myorep.MyoRepKind.RepFloor,
                                    ),
                                ),
                        ),
                    ),
            )
        val overlay =
            WorkoutOverlay(
                loggedSets =
                    mapOf(
                        myoActId to
                            com.stratum.domain.workout.LoggedSetValue(
                                slotId = myoActId,
                                weight = com.stratum.domain.lift.Weight(65.0, com.stratum.domain.lift.Weight.WeightUnit.Lb),
                                reps = 11,
                                rir = 1,
                            ),
                    ),
            )

        val next =
            LiveIntensifierOverlayOps.applyMyoLifecycleAfterLog(
                structure = myoStruct,
                overlay = overlay,
                loggedSlotId = myoActId,
            )

        assertEquals(1, next.addedSlots.size)
        assertEquals(30, next.addedSlots.single().restSeconds)
    }
}
