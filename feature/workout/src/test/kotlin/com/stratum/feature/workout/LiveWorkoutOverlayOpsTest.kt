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
                slotId = SetSlotId.Overlay(OverlaySetInstanceId("mini-ls-myo-1-aaaaaaaa")),
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
                slotId = SetSlotId.Overlay(OverlaySetInstanceId("mini-ls-myo-2-bbbbbbbb")),
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
            listOf("ls-myo", "mini-ls-myo-1-aaaaaaaa", "mini-ls-myo-2-bbbbbbbb", "ls-s1", "ls-s2"),
            ordered.map { LiveWorkoutOverlayOps.slotKey(it.slotId) },
        )
        assertEquals(listOf(0, 1, 2, 3, 4), ordered.map { it.position })
    }

    @Test
    @DisplayName("cascade delete activation removes minis but keeps trailing standards")
    fun cascadeDeleteMyoActivationKeepsTrailingStandards() {
        val myoActId = SetSlotId.Template(LiftSetId("ls-myo"))
        val stdId = SetSlotId.Template(LiftSetId("ls-s1"))
        val miniId = SetSlotId.Overlay(OverlaySetInstanceId("mini-ls-myo-1-aaaaaaaa"))
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
    @DisplayName("orderLiftSlots places mini after second activation when two myos are back-to-back")
    fun orderLiftSlotsMultiMyoClusterInsertsAfterOwningActivation() {
        val myo1 =
            SetSlot(
                slotId = SetSlotId.Template(LiftSetId("ls-myo-1")),
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
            )
        val myo2 =
            SetSlot(
                slotId = SetSlotId.Template(LiftSetId("ls-myo-2")),
                workoutLiftId = workoutLiftId,
                liftId = originalLiftId,
                position = 1,
                setType = SetType.MyoRep,
                isTopSet = false,
                repMin = 8,
                repMax = 12,
                rirGoal = 1,
                restSeconds = 120,
                myoRepKind = com.stratum.domain.workout.myorep.MyoRepKind.RepFloor,
            )
        val std =
            SetSlot(
                slotId = SetSlotId.Template(LiftSetId("ls-s1")),
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
        // Encoded activation key matches production mini id format.
        val miniForSecond =
            SetSlot(
                slotId = SetSlotId.Overlay(OverlaySetInstanceId("mini-ls-myo-2-1-deadbeef")),
                workoutLiftId = workoutLiftId,
                liftId = originalLiftId,
                position = 2,
                setType = SetType.MyoRep,
                isTopSet = false,
                repMin = 0,
                repMax = 0,
                rirGoal = 0,
                restSeconds = 120,
                myoRepKind = com.stratum.domain.workout.myorep.MyoRepKind.RepFloor,
            )

        val ordered =
            LiveIntensifierOverlayOps.orderLiftSlots(
                templateSlots = listOf(myo1, myo2, std),
                addedSlots = listOf(miniForSecond),
            )

        assertEquals(
            listOf("ls-myo-1", "ls-myo-2", "mini-ls-myo-2-1-deadbeef", "ls-s1"),
            ordered.map { LiveWorkoutOverlayOps.slotKey(it.slotId) },
        )
    }

    @Test
    @DisplayName("logging second myo activation spawns mini under that cluster not the first")
    fun myoLifecycleSecondClusterMiniGoesAfterSecondActivation() {
        val myo1Id = SetSlotId.Template(LiftSetId("ls-myo-1"))
        val myo2Id = SetSlotId.Template(LiftSetId("ls-myo-2"))
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
                                        slotId = myo1Id,
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
                                        slotId = myo2Id,
                                        workoutLiftId = workoutLiftId,
                                        liftId = originalLiftId,
                                        position = 1,
                                        setType = SetType.MyoRep,
                                        isTopSet = false,
                                        repMin = 8,
                                        repMax = 12,
                                        rirGoal = 1,
                                        restSeconds = 90,
                                        myoRepKind = com.stratum.domain.workout.myorep.MyoRepKind.RepFloor,
                                    ),
                                    SetSlot(
                                        slotId = stdId,
                                        workoutLiftId = workoutLiftId,
                                        liftId = originalLiftId,
                                        position = 2,
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
                        myo2Id to
                            com.stratum.domain.workout.LoggedSetValue(
                                slotId = myo2Id,
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
                loggedSlotId = myo2Id,
            )

        assertEquals(1, next.addedSlots.size)
        val mini = next.addedSlots.single()
        assertTrue(mini.slotId is SetSlotId.Overlay)
        val miniKey = LiveWorkoutOverlayOps.slotKey(mini.slotId)
        assertTrue(miniKey.startsWith("mini-ls-myo-2-"), "mini id should encode second activation: $miniKey")
        assertEquals(90, mini.restSeconds, "mini inherits second activation rest")

        val ordered =
            LiveIntensifierOverlayOps.orderLiftSlots(
                templateSlots = myoStruct.lifts.single().slots,
                addedSlots = next.addedSlots,
            )
        assertEquals(
            listOf("ls-myo-1", "ls-myo-2", miniKey, "ls-s1"),
            ordered.map { LiveWorkoutOverlayOps.slotKey(it.slotId) },
        )
    }

    @Test
    @DisplayName("cascade delete first myo does not remove second myo activation")
    fun cascadeDeleteFirstMyoKeepsSecondActivation() {
        val myo1Id = SetSlotId.Template(LiftSetId("ls-myo-1"))
        val myo2Id = SetSlotId.Template(LiftSetId("ls-myo-2"))
        val mini1Id = SetSlotId.Overlay(OverlaySetInstanceId("mini-ls-myo-1-1-aaaaaaaa"))
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
                                        slotId = myo1Id,
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
                                        slotId = myo2Id,
                                        workoutLiftId = workoutLiftId,
                                        liftId = originalLiftId,
                                        position = 1,
                                        setType = SetType.MyoRep,
                                        isTopSet = false,
                                        repMin = 8,
                                        repMax = 12,
                                        rirGoal = 1,
                                        restSeconds = 120,
                                        myoRepKind = com.stratum.domain.workout.myorep.MyoRepKind.RepFloor,
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
                            slotId = mini1Id,
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
                slotId = myo1Id,
            )

        assertEquals(setOf(myo1Id, mini1Id), targets.toSet())
        assertFalse(targets.contains(myo2Id))
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

    // -------------------------------------------------------------------------
    // Myo generation end-conditions + drop cascade (live intensifier scenarios)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("myo lifecycle: logging mini at rep floor does not spawn another mini")
    fun myoLifecycleFloorEndsBlock() {
        val myoActId = SetSlotId.Template(LiftSetId("ls-myo"))
        val miniId = SetSlotId.Overlay(OverlaySetInstanceId("mini-ls-myo-1-aaaaaaaa"))
        val myoStruct = singleMyoStructure(myoActId, restSeconds = 120)
        val miniSlot = miniSlot(miniId, position = 1, restSeconds = 120)
        val overlay =
            WorkoutOverlay(
                addedSlots = listOf(miniSlot),
                loggedSets =
                    mapOf(
                        myoActId to log(myoActId, 65.0, 11, 1),
                        miniId to log(miniId, 65.0, 5, 0), // DEFAULT_REP_FLOOR
                    ),
            )

        val next =
            LiveIntensifierOverlayOps.applyMyoLifecycleAfterLog(
                structure = myoStruct,
                overlay = overlay,
                loggedSlotId = miniId,
            )

        assertEquals(1, next.addedSlots.size, "floor ends block — no second mini")
        assertEquals(miniId, next.addedSlots.single().slotId)
    }

    @Test
    @DisplayName("myo lifecycle: logging mini above floor spawns the next mini")
    fun myoLifecycleAboveFloorSpawnsNextMini() {
        val myoActId = SetSlotId.Template(LiftSetId("ls-myo"))
        val miniId = SetSlotId.Overlay(OverlaySetInstanceId("mini-ls-myo-1-aaaaaaaa"))
        val myoStruct = singleMyoStructure(myoActId, restSeconds = 120)
        val miniSlot = miniSlot(miniId, position = 1, restSeconds = 120)
        val overlay =
            WorkoutOverlay(
                addedSlots = listOf(miniSlot),
                loggedSets =
                    mapOf(
                        myoActId to log(myoActId, 65.0, 11, 1),
                        miniId to log(miniId, 65.0, 7, 0),
                    ),
            )

        val next =
            LiveIntensifierOverlayOps.applyMyoLifecycleAfterLog(
                structure = myoStruct,
                overlay = overlay,
                loggedSlotId = miniId,
            )

        assertEquals(2, next.addedSlots.size, "above floor → one more mini")
        val newMini = next.addedSlots.single { it.slotId != miniId }
        assertTrue(
            LiveWorkoutOverlayOps.slotKey(newMini.slotId).startsWith("mini-ls-myo-"),
            "new mini encodes activation key",
        )
    }

    @Test
    @DisplayName("myo lifecycle: open unlogged mini blocks spawning another on activation re-log")
    fun myoLifecycleOpenMiniBlocksSecondSpawn() {
        val myoActId = SetSlotId.Template(LiftSetId("ls-myo"))
        val miniId = SetSlotId.Overlay(OverlaySetInstanceId("mini-ls-myo-1-aaaaaaaa"))
        val myoStruct = singleMyoStructure(myoActId, restSeconds = 120)
        val miniSlot = miniSlot(miniId, position = 1, restSeconds = 120)
        val overlay =
            WorkoutOverlay(
                addedSlots = listOf(miniSlot),
                loggedSets = mapOf(myoActId to log(myoActId, 65.0, 11, 1)),
            )

        val next =
            LiveIntensifierOverlayOps.applyMyoLifecycleAfterLog(
                structure = myoStruct,
                overlay = overlay,
                loggedSlotId = myoActId,
            )

        assertEquals(1, next.addedSlots.size, "must not double-spawn while mini is open")
        assertEquals(miniId, next.addedSlots.single().slotId)
    }

    @Test
    @DisplayName("myo lifecycle: logging a standard set does not spawn a mini")
    fun myoLifecycleStandardLogDoesNotSpawnMini() {
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
                                    myoActivation(myoActId, position = 0, restSeconds = 120),
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
                        myoActId to log(myoActId, 65.0, 11, 1),
                        stdId to log(stdId, 65.0, 8, 1),
                    ),
            )

        val next =
            LiveIntensifierOverlayOps.applyMyoLifecycleAfterLog(
                structure = myoStruct,
                overlay = overlay,
                loggedSlotId = stdId,
            )

        assertTrue(next.addedSlots.isEmpty(), "standard log must not mint myo minis")
    }

    @Test
    @DisplayName("cascade delete first drop removes following drop chain")
    fun cascadeDeleteDropRemovesFollowingDrops() {
        val s1 = SetSlotId.Template(LiftSetId("ls-s1"))
        val d1 = SetSlotId.Template(LiftSetId("ls-d1"))
        val d2 = SetSlotId.Template(LiftSetId("ls-d2"))
        val stdTail = SetSlotId.Template(LiftSetId("ls-s2"))
        val struct =
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
                                        slotId = s1,
                                        workoutLiftId = workoutLiftId,
                                        liftId = originalLiftId,
                                        position = 0,
                                        setType = SetType.Standard,
                                        isTopSet = true,
                                        repMin = 6,
                                        repMax = 10,
                                        rirGoal = 1,
                                        restSeconds = 120,
                                    ),
                                    SetSlot(
                                        slotId = d1,
                                        workoutLiftId = workoutLiftId,
                                        liftId = originalLiftId,
                                        position = 1,
                                        setType = SetType.DropSet,
                                        isTopSet = false,
                                        repMin = 0,
                                        repMax = 0,
                                        rirGoal = 0,
                                        restSeconds = 0,
                                    ),
                                    SetSlot(
                                        slotId = d2,
                                        workoutLiftId = workoutLiftId,
                                        liftId = originalLiftId,
                                        position = 2,
                                        setType = SetType.DropSet,
                                        isTopSet = false,
                                        repMin = 0,
                                        repMax = 0,
                                        rirGoal = 0,
                                        restSeconds = 0,
                                    ),
                                    SetSlot(
                                        slotId = stdTail,
                                        workoutLiftId = workoutLiftId,
                                        liftId = originalLiftId,
                                        position = 3,
                                        setType = SetType.Standard,
                                        isTopSet = false,
                                        repMin = 6,
                                        repMax = 10,
                                        rirGoal = 1,
                                        restSeconds = 120,
                                    ),
                                ),
                        ),
                    ),
            )

        val targets =
            LiveIntensifierOverlayOps.cascadeDeleteTargets(
                structure = struct,
                overlay = WorkoutOverlay(),
                slotId = d1,
            )

        assertEquals(setOf(d1, d2), targets.toSet())
        assertFalse(targets.contains(s1))
        assertFalse(targets.contains(stdTail), "must stop at non-drop after the chain")
    }

    @Test
    @DisplayName("cascade delete auto-mini alone only removes that mini")
    fun cascadeDeleteMiniOnlyRemovesSelf() {
        val myoActId = SetSlotId.Template(LiftSetId("ls-myo"))
        val miniId = SetSlotId.Overlay(OverlaySetInstanceId("mini-ls-myo-1-aaaaaaaa"))
        val myoStruct = singleMyoStructure(myoActId, restSeconds = 120)
        val overlay =
            WorkoutOverlay(
                addedSlots = listOf(miniSlot(miniId, position = 1, restSeconds = 120)),
            )

        val targets =
            LiveIntensifierOverlayOps.cascadeDeleteTargets(
                structure = myoStruct,
                overlay = overlay,
                slotId = miniId,
            )

        assertEquals(listOf(miniId), targets)
    }

    private fun log(
        slotId: SetSlotId,
        weight: Double,
        reps: Int,
        rir: Int?,
    ) = com.stratum.domain.workout.LoggedSetValue(
        slotId = slotId,
        weight = com.stratum.domain.lift.Weight(weight, com.stratum.domain.lift.Weight.WeightUnit.Lb),
        reps = reps,
        rir = rir,
    )

    private fun myoActivation(
        slotId: SetSlotId,
        position: Int,
        restSeconds: Int,
    ) = SetSlot(
        slotId = slotId,
        workoutLiftId = workoutLiftId,
        liftId = originalLiftId,
        position = position,
        setType = SetType.MyoRep,
        isTopSet = true,
        repMin = 10,
        repMax = 15,
        rirGoal = 1,
        restSeconds = restSeconds,
        myoRepKind = com.stratum.domain.workout.myorep.MyoRepKind.RepFloor,
    )

    private fun singleMyoStructure(
        myoActId: SetSlotId,
        restSeconds: Int,
    ) = WorkoutStructureProjection(
        workoutId = WorkoutId("w-1"),
        workoutName = "Test",
        lifts =
            listOf(
                WorkoutLiftProjection(
                    workoutLiftId = workoutLiftId,
                    liftId = originalLiftId,
                    liftName = "Lift",
                    progressionScheme = ProgressionScheme.Double,
                    slots = listOf(myoActivation(myoActId, position = 0, restSeconds = restSeconds)),
                ),
            ),
    )

    private fun miniSlot(
        miniId: SetSlotId,
        position: Int,
        restSeconds: Int,
    ) = SetSlot(
        slotId = miniId,
        workoutLiftId = workoutLiftId,
        liftId = originalLiftId,
        position = position,
        setType = SetType.MyoRep,
        isTopSet = false,
        repMin = 0,
        repMax = 0,
        rirGoal = 0,
        restSeconds = restSeconds,
        myoRepKind = com.stratum.domain.workout.myorep.MyoRepKind.RepFloor,
    )
}
