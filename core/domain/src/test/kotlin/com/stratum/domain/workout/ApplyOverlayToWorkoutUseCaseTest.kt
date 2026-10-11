package com.stratum.domain.workout

import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.identity.WorkoutSupersetGroupId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.workout.myorep.MyoRepKind
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ApplyOverlayToWorkoutUseCaseTest {

    private val useCase = ApplyOverlayToWorkoutUseCase()

    @Test
    @DisplayName("Slot shape overrides update template set type and myo kind")
    fun slotShapeOverridesAppliedToTemplate() {
        val baseline = sampleWorkout()
        val overlay =
            WorkoutOverlay(
                slotShapeOverrides =
                    mapOf(
                        SetSlotId.Template(LiftSetId("ls-1")) to
                            SlotShapeOverride(
                                setType = SetType.MyoRep,
                                myoRepKind = MyoRepKind.RepFloor,
                                isTopSet = true,
                                repMin = 8,
                                repMax = 12,
                                rirGoal = 1,
                            ),
                    ),
            )

        val result = useCase(baseline, overlay)
        val set = result.workoutLifts[0].sets.first { it.id.value == "ls-1" }
        assertEquals(SetType.MyoRep, set.setType)
        assertEquals(MyoRepKind.RepFloor, set.myoRepKind)
        assertEquals(1, set.rirGoal)
    }

    @Test
    @DisplayName("Auto-generated myo minis are not written to the template")
    fun myoMinisExcludedFromTemplate() {
        val baseline = sampleWorkout()
        val mini =
            SetSlot(
                slotId = SetSlotId.Overlay(OverlaySetInstanceId("mini-ls-1-1-xyz")),
                workoutLiftId = WorkoutLiftId("wl-1"),
                liftId = LiftId("lift-bench"),
                position = 2,
                setType = SetType.MyoRep,
                isTopSet = false,
                repMin = 0,
                repMax = 0,
                rirGoal = 0,
                restSeconds = 15,
                myoRepKind = MyoRepKind.RepFloor,
            )
        val overlay = WorkoutOverlay(addedSlots = listOf(mini))

        val result = useCase(baseline, overlay)

        assertEquals(2, result.workoutLifts[0].sets.size)
        assertTrue(result.workoutLifts[0].sets.none { it.setType == SetType.MyoRep })
    }

    @Test
    @DisplayName("Overlay with no edits returns the baseline workout unchanged")
    fun noEditsReturnsBaseline() {
        val baseline = sampleWorkout()
        val result = useCase(baseline, WorkoutOverlay())

        assertEquals(baseline.workoutLifts.size, result.workoutLifts.size)
        assertEquals(baseline.workoutLifts[0].liftId, result.workoutLifts[0].liftId)
    }

    @Test
    @DisplayName("Deleted lifts are removed from the result")
    fun deletedLiftsRemoved() {
        val baseline = sampleWorkout()
        val overlay = WorkoutOverlay(deletedLiftIds = setOf(WorkoutLiftId("wl-1")))

        val result = useCase(baseline, overlay)

        assertEquals(1, result.workoutLifts.size)
        assertEquals(WorkoutLiftId("wl-2"), result.workoutLifts[0].id)
    }

    @Test
    @DisplayName("Swapped lifts update the liftId on the workout lift")
    fun swappedLiftsApplied() {
        val baseline = sampleWorkout()
        val newLiftId = LiftId("lift-deadlift")
        val overlay = WorkoutOverlay(
            swappedLifts = mapOf(WorkoutLiftId("wl-1") to newLiftId),
        )

        val result = useCase(baseline, overlay)

        assertEquals(newLiftId, result.workoutLifts[0].liftId)
    }

    @Test
    @DisplayName("STR-124: swapped equipment persists to the template lift")
    fun swappedEquipmentApplied() {
        val baseline = sampleWorkout()
        val newEquipmentId = com.stratum.domain.lift.identity.EquipmentId("ssb")
        val overlay = WorkoutOverlay(
            swappedEquipment = mapOf(WorkoutLiftId("wl-1") to newEquipmentId),
        )

        val result = useCase(baseline, overlay)

        assertEquals(newEquipmentId, result.workoutLifts[0].equipmentId)
        assertEquals(null, result.workoutLifts[1].equipmentId)
    }

    @Test
    @DisplayName("STR-124: template equipment survives a swap-free save-back")
    fun templateEquipmentPreservedWithoutSwap() {
        val barbell = com.stratum.domain.lift.identity.EquipmentId("barbell")
        val baseline =
            sampleWorkout().copy(
                workoutLifts =
                    sampleWorkout().workoutLifts.map { lift ->
                        lift.copy(equipmentId = barbell)
                    },
            )

        val result = useCase(baseline, WorkoutOverlay())

        assertEquals(barbell, result.workoutLifts[0].equipmentId)
        assertEquals(barbell, result.workoutLifts[1].equipmentId)
    }

    @Test
    @DisplayName("Deleted sets are removed from the workout lift")
    fun deletedSetsRemoved() {
        val baseline = sampleWorkout()
        val overlay = WorkoutOverlay(
            deletedSlotIds = setOf(SetSlotId.Template(LiftSetId("ls-1"))),
        )

        val result = useCase(baseline, overlay)

        assertEquals(1, result.workoutLifts[0].sets.size)
        assertEquals(LiftSetId("ls-2"), result.workoutLifts[0].sets[0].id)
    }

    @Test
    @DisplayName("Added sets are appended to the correct workout lift")
    fun addedSetsAppended() {
        val baseline = sampleWorkout()
        val addedSlot = SetSlot(
            slotId = SetSlotId.Overlay(OverlaySetInstanceId("added-1")),
            workoutLiftId = WorkoutLiftId("wl-1"),
            liftId = LiftId("lift-bench"),
            position = 2,
            setType = SetType.Standard,
            isTopSet = false,
            repMin = 8,
            repMax = 12,
            rirGoal = 1,
            restSeconds = 90,
        )
        val overlay = WorkoutOverlay(addedSlots = listOf(addedSlot))

        val result = useCase(baseline, overlay)

        assertEquals(3, result.workoutLifts[0].sets.size)
        val addedSet = result.workoutLifts[0].sets[2]
        assertEquals(2, addedSet.position)
        assertEquals(8, addedSet.repMin)
        assertEquals(12, addedSet.repMax)
    }

    @Test
    @DisplayName("Superset group overrides are applied")
    fun supersetGroupOverridesApplied() {
        val baseline = sampleWorkout()
        val groupId = WorkoutSupersetGroupId("ss-1")
        val overlay = WorkoutOverlay(
            supersetGroupIdOverrides = mapOf(WorkoutLiftId("wl-1") to groupId),
        )

        val result = useCase(baseline, overlay)

        assertEquals(groupId, result.workoutLifts[0].supersetGroupId)
    }

    @Test
    @DisplayName("Null superset override clears a baseline template group (key-present-null)")
    fun nullSupersetOverrideClearsTemplateGroup() {
        val groupId = WorkoutSupersetGroupId("ss-template")
        val baseline =
            sampleWorkout().copy(
                workoutLifts =
                    sampleWorkout().workoutLifts.map { lift ->
                        lift.copy(supersetGroupId = groupId)
                    },
            )
        val overlay =
            WorkoutOverlay(
                supersetGroupIdOverrides = mapOf(WorkoutLiftId("wl-1") to null),
            )

        val result = useCase(baseline, overlay)

        assertEquals(null, result.workoutLifts.single { it.id.value == "wl-1" }.supersetGroupId)
        assertEquals(groupId, result.workoutLifts.single { it.id.value == "wl-2" }.supersetGroupId)
    }

    @Test
    @DisplayName("Key-absent superset override leaves baseline group unchanged")
    fun absentSupersetOverrideKeepsTemplateGroup() {
        val groupId = WorkoutSupersetGroupId("ss-template")
        val baseline =
            sampleWorkout().copy(
                workoutLifts =
                    sampleWorkout().workoutLifts.map { lift ->
                        lift.copy(supersetGroupId = groupId)
                    },
            )

        val result = useCase(baseline, WorkoutOverlay())

        assertEquals(groupId, result.workoutLifts[0].supersetGroupId)
        assertEquals(groupId, result.workoutLifts[1].supersetGroupId)
    }

    @Test
    @DisplayName("Null superset override clears session-added OverlayAddedLift group")
    fun nullSupersetOverrideClearsSessionAddedGroup() {
        val groupId = WorkoutSupersetGroupId("ss-added")
        val newWlId = WorkoutLiftId("wl-added")
        val slot =
            SetSlot(
                slotId = SetSlotId.Overlay(OverlaySetInstanceId("added-ss-1")),
                workoutLiftId = newWlId,
                liftId = LiftId("lift-row"),
                position = 0,
                setType = SetType.Standard,
                isTopSet = true,
                repMin = 8,
                repMax = 12,
                rirGoal = 2,
                restSeconds = 90,
            )
        val overlay =
            WorkoutOverlay(
                addedLifts =
                    listOf(
                        OverlayAddedLift(
                            workoutLiftId = newWlId,
                            liftId = LiftId("lift-row"),
                            progressionScheme = ProgressionScheme.Double,
                            supersetGroupId = groupId,
                        ),
                    ),
                addedSlots = listOf(slot),
                supersetGroupIdOverrides = mapOf(newWlId to null),
            )

        val result = useCase(sampleWorkout(), overlay)

        val added = result.workoutLifts.single { it.id == newWlId }
        assertEquals(null, added.supersetGroupId)
    }

    @Test
    @DisplayName("Overlay note lists are carried onto the template with a first-note scalar mirror")
    fun overlayNoteListsCarriedToTemplate() {
        val newWlId = WorkoutLiftId("wl-added")
        val overlay =
            WorkoutOverlay(
                addedLifts =
                    listOf(
                        OverlayAddedLift(
                            workoutLiftId = newWlId,
                            liftId = LiftId("lift-row"),
                            progressionScheme = ProgressionScheme.Double,
                        ),
                    ),
                workoutLiftPinnedNoteLists =
                    mapOf(
                        newWlId to listOf("added one", "added two"),
                        WorkoutLiftId("wl-1") to listOf("tmpl one", "tmpl two"),
                    ),
            )

        val result = useCase(sampleWorkout(), overlay)

        val added = result.workoutLifts.single { it.id == newWlId }
        assertEquals(listOf("added one", "added two"), added.pinnedNotes)
        assertEquals("added one", added.pinnedNote)
        val existing = result.workoutLifts.single { it.id == WorkoutLiftId("wl-1") }
        assertEquals(listOf("tmpl one", "tmpl two"), existing.pinnedNotes)
        assertEquals("tmpl one", existing.pinnedNote)
        // Untouched slots carry nothing, keeping restore behavior downstream.
        val untouched = result.workoutLifts.single { it.id == WorkoutLiftId("wl-2") }
        assertTrue(untouched.pinnedNotes.isEmpty())
        assertEquals(null, untouched.pinnedNote)
    }

    @Test
    @DisplayName("Rest timer overrides are applied")
    fun restTimerOverridesApplied() {
        val baseline = sampleWorkout()
        val overlay = WorkoutOverlay(
            useRestTimerBetweenLiftsOverrides = mapOf(WorkoutLiftId("wl-1") to true),
        )

        val result = useCase(baseline, overlay)

        assertTrue(result.workoutLifts[0].useRestTimerBetweenLifts)
        assertFalse(result.workoutLifts[1].useRestTimerBetweenLifts)
    }

    @Test
    @DisplayName("Per-slot restSecondsOverrides are written onto template sets")
    fun restSecondsOverridesAppliedToTemplateSets() {
        val baseline = sampleWorkout()
        val overlay =
            WorkoutOverlay(
                restSecondsOverrides =
                    mapOf(
                        SetSlotId.Template(LiftSetId("ls-1")) to 45,
                        SetSlotId.Template(LiftSetId("ls-2")) to 200,
                    ),
            )

        val result = useCase(baseline, overlay)
        val sets = result.workoutLifts[0].sets

        assertEquals(45, sets[0].restSeconds)
        assertEquals(200, sets[1].restSeconds)
        // Unaffected lift keeps default rest.
        assertEquals(
            com.stratum.domain.program.DEFAULT_LIFT_SET_REST_SECONDS,
            result.workoutLifts[1].sets[0].restSeconds,
        )
    }

    @Test
    @DisplayName("restSecondsOverrides on overlay-added slots persist into new template sets")
    fun restSecondsOverridesOnAddedSlotsMaterialized() {
        val baseline = sampleWorkout()
        val addedSlot =
            SetSlot(
                slotId = SetSlotId.Overlay(OverlaySetInstanceId("added-rest")),
                workoutLiftId = WorkoutLiftId("wl-1"),
                liftId = LiftId("lift-bench"),
                position = 2,
                setType = SetType.Standard,
                isTopSet = false,
                repMin = 8,
                repMax = 12,
                rirGoal = 1,
                restSeconds = 90,
            )
        val overlay =
            WorkoutOverlay(
                addedSlots = listOf(addedSlot),
                restSecondsOverrides = mapOf(addedSlot.slotId to 75),
            )

        val result = useCase(baseline, overlay)
        val addedSet = result.workoutLifts[0].sets[2]
        assertEquals(75, addedSet.restSeconds)
    }

    @Test
    @DisplayName("restSecondsOverrides mark the overlay dirty")
    fun restSecondsOverridesAreDirty() {
        assertFalse(WorkoutOverlay().isDirty)
        assertTrue(
            WorkoutOverlay(
                restSecondsOverrides = mapOf(SetSlotId.Template(LiftSetId("ls-1")) to 60),
            ).isDirty,
        )
    }

    @Test
    @DisplayName("STR-124: swappedEquipment marks the overlay dirty (update-template offer)")
    fun swappedEquipmentIsDirty() {
        assertFalse(WorkoutOverlay().isDirty)
        assertTrue(
            WorkoutOverlay(
                swappedEquipment =
                    mapOf(
                        WorkoutLiftId("wl-1") to
                            com.stratum.domain.lift.identity.EquipmentId("ssb"),
                    ),
            ).isDirty,
        )
    }

    @Test
    @DisplayName("Reorder changes the lift order and renumbers position")
    fun reorderChangesLiftOrder() {
        val baseline = sampleWorkout()
        val overlay = WorkoutOverlay(
            orderedLiftIds = listOf("wl-2", "wl-1"),
        )

        val result = useCase(baseline, overlay)

        assertEquals(WorkoutLiftId("wl-2"), result.workoutLifts[0].id)
        assertEquals(WorkoutLiftId("wl-1"), result.workoutLifts[1].id)
        assertEquals(0, result.workoutLifts[0].position)
        assertEquals(1, result.workoutLifts[1].position)
    }

    @Test
    @DisplayName("Deleted overlay-added slots are not written to the template")
    fun deletedOverlayAddedSlotsNotMaterialized() {
        val baseline = sampleWorkout()
        val keptSlot =
            SetSlot(
                slotId = SetSlotId.Overlay(OverlaySetInstanceId("added-keep")),
                workoutLiftId = WorkoutLiftId("wl-1"),
                liftId = LiftId("lift-bench"),
                position = 2,
                setType = SetType.Standard,
                isTopSet = false,
                repMin = 8,
                repMax = 12,
                rirGoal = 1,
                restSeconds = 90,
            )
        val deletedSlot =
            SetSlot(
                slotId = SetSlotId.Overlay(OverlaySetInstanceId("added-deleted")),
                workoutLiftId = WorkoutLiftId("wl-1"),
                liftId = LiftId("lift-bench"),
                position = 3,
                setType = SetType.Standard,
                isTopSet = false,
                repMin = 8,
                repMax = 12,
                rirGoal = 1,
                restSeconds = 90,
            )
        val overlay =
            WorkoutOverlay(
                addedSlots = listOf(keptSlot, deletedSlot),
                deletedSlotIds = setOf(deletedSlot.slotId),
            )

        val result = useCase(baseline, overlay)

        // 2 template sets + 1 kept overlay set (deleted overlay set excluded)
        assertEquals(3, result.workoutLifts[0].sets.size)
        assertEquals(listOf(0, 1, 2), result.workoutLifts[0].sets.map { it.position })
    }

    @Test
    @DisplayName("Deleted overlay slots on session-added lifts are not written to the template")
    fun deletedSlotsOnAddedLiftNotMaterialized() {
        val baseline = sampleWorkout()
        val newWlId = WorkoutLiftId("wl-added")
        val kept =
            SetSlot(
                slotId = SetSlotId.Overlay(OverlaySetInstanceId("added-lift-keep")),
                workoutLiftId = newWlId,
                liftId = LiftId("lift-row"),
                position = 0,
                setType = SetType.Standard,
                isTopSet = true,
                repMin = 8,
                repMax = 12,
                rirGoal = 2,
                restSeconds = 90,
            )
        val deleted =
            SetSlot(
                slotId = SetSlotId.Overlay(OverlaySetInstanceId("added-lift-del")),
                workoutLiftId = newWlId,
                liftId = LiftId("lift-row"),
                position = 1,
                setType = SetType.Standard,
                isTopSet = false,
                repMin = 8,
                repMax = 12,
                rirGoal = 2,
                restSeconds = 90,
            )
        val overlay =
            WorkoutOverlay(
                addedLifts =
                    listOf(
                        OverlayAddedLift(
                            workoutLiftId = newWlId,
                            liftId = LiftId("lift-row"),
                            progressionScheme = ProgressionScheme.Double,
                        ),
                    ),
                addedSlots = listOf(kept, deleted),
                deletedSlotIds = setOf(deleted.slotId),
            )

        val result = useCase(baseline, overlay)
        val added = result.workoutLifts.single { it.id == newWlId }
        assertEquals(1, added.sets.size)
        assertEquals(0, added.sets[0].position)
        assertEquals(2, added.position) // after two baseline lifts
    }

    @Test
    @DisplayName("Added lifts are appended with their overlay slots")
    fun addedLiftsAppended() {
        val baseline = sampleWorkout()
        val newWlId = WorkoutLiftId("wl-added")
        val slot = SetSlot(
            slotId = SetSlotId.Overlay(OverlaySetInstanceId("added-lift-set-1")),
            workoutLiftId = newWlId,
            liftId = LiftId("lift-row"),
            position = 0,
            setType = SetType.Standard,
            isTopSet = true,
            repMin = 8,
            repMax = 12,
            rirGoal = 2,
            restSeconds = 90,
        )
        val overlay = WorkoutOverlay(
            addedLifts = listOf(
                OverlayAddedLift(
                    workoutLiftId = newWlId,
                    liftId = LiftId("lift-row"),
                    progressionScheme = ProgressionScheme.Double,
                ),
            ),
            addedSlots = listOf(slot),
        )

        val result = useCase(baseline, overlay)

        assertEquals(3, result.workoutLifts.size)
        val added = result.workoutLifts.last()
        assertEquals(newWlId, added.id)
        assertEquals(LiftId("lift-row"), added.liftId)
        assertEquals(1, added.sets.size)
        assertEquals(8, added.sets[0].repMin)
        assertEquals(12, added.sets[0].repMax)
    }

    @Test
    @DisplayName("Overlay workout-lift pinned notes copy onto template and added lifts")
    fun overlayPinnedNotesCopied() {
        val baseline = sampleWorkout()
        val newWlId = WorkoutLiftId("wl-added")
        val slot =
            SetSlot(
                slotId = SetSlotId.Overlay(OverlaySetInstanceId("added-lift-set-1")),
                workoutLiftId = newWlId,
                liftId = LiftId("lift-row"),
                position = 0,
                setType = SetType.Standard,
                isTopSet = true,
                repMin = 8,
                repMax = 12,
                rirGoal = 2,
                restSeconds = 90,
            )
        val overlay =
            WorkoutOverlay(
                addedLifts =
                    listOf(
                        OverlayAddedLift(
                            workoutLiftId = newWlId,
                            liftId = LiftId("lift-row"),
                            progressionScheme = ProgressionScheme.Double,
                        ),
                    ),
                addedSlots = listOf(slot),
                workoutLiftPinnedNotes =
                    mapOf(
                        WorkoutLiftId("wl-1") to "Template pin",
                        newWlId to "Added pin",
                    ),
            )

        val result = useCase(baseline, overlay)
        assertEquals("Template pin", result.workoutLifts.first { it.id == WorkoutLiftId("wl-1") }.pinnedNote)
        assertEquals("Added pin", result.workoutLifts.first { it.id == newWlId }.pinnedNote)
    }

    @Test
    @DisplayName("workoutLiftPinnedNotes mark the overlay dirty")
    fun workoutLiftPinnedNotesAreDirty() {
        assertFalse(WorkoutOverlay().isDirty)
        assertTrue(
            WorkoutOverlay(
                workoutLiftPinnedNotes = mapOf(WorkoutLiftId("wl-1") to "cue"),
            ).isDirty,
        )
    }

    private fun sampleWorkout(): Workout = Workout(
        id = WorkoutId("w-1"),
        programId = ProgramId("p-1"),
        name = "Test Workout",
        weekday = Weekday.Monday,
        position = 0,
        workoutLifts = listOf(
            WorkoutLift(
                id = WorkoutLiftId("wl-1"),
                workoutId = WorkoutId("w-1"),
                liftId = LiftId("lift-bench"),
                progressionScheme = ProgressionScheme.Double,
                position = 0,
                sets = listOf(
                    LiftSet(
                        id = LiftSetId("ls-1"),
                        workoutLiftId = WorkoutLiftId("wl-1"),
                        position = 0,
                        setType = SetType.Standard,
                        repMin = 6,
                        repMax = 10,
                        rirGoal = 2,
                        isTopSet = true,
                    ),
                    LiftSet(
                        id = LiftSetId("ls-2"),
                        workoutLiftId = WorkoutLiftId("wl-1"),
                        position = 1,
                        setType = SetType.Standard,
                        repMin = 6,
                        repMax = 10,
                        rirGoal = 2,
                        isTopSet = false,
                    ),
                ),
            ),
            WorkoutLift(
                id = WorkoutLiftId("wl-2"),
                workoutId = WorkoutId("w-1"),
                liftId = LiftId("lift-squat"),
                progressionScheme = ProgressionScheme.Double,
                position = 1,
                sets = listOf(
                    LiftSet(
                        id = LiftSetId("ls-3"),
                        workoutLiftId = WorkoutLiftId("wl-2"),
                        position = 0,
                        setType = SetType.Standard,
                        repMin = 5,
                        repMax = 5,
                        rirGoal = 1,
                        isTopSet = true,
                    ),
                ),
            ),
        ),
    )
}
