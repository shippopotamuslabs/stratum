package com.stratum.application.workout

import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.identity.WorkoutSupersetGroupId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.workout.myorep.MyoRepKind
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.workout.EquipmentSwapped
import com.stratum.domain.workout.LiftAdded
import com.stratum.domain.workout.LiftMoved
import com.stratum.domain.workout.LiftRemoved
import com.stratum.domain.workout.LiftSwapped
import com.stratum.domain.workout.OverlaySetInstanceId
import com.stratum.domain.workout.PinnedNotesChanged
import com.stratum.domain.workout.RestBetweenLiftsChanged
import com.stratum.domain.workout.RestEdited
import com.stratum.domain.workout.SetCountChanged
import com.stratum.domain.workout.SetEdited
import com.stratum.domain.workout.SetSlot
import com.stratum.domain.workout.SetSlotId
import com.stratum.domain.workout.SupersetGroupingChanged
import com.stratum.domain.workout.TemplateSetShape
import com.stratum.domain.workout.UNKNOWN_LIFT_DISPLAY_NAME
import com.stratum.domain.workout.WorkoutOverlay

class ComputeTemplateChangeSummaryUseCaseTest {

    private val useCase = ComputeTemplateChangeSummaryUseCase()

    private val liftNames =
        mapOf(
            LiftId("lift-bench") to "Bench Press",
            LiftId("lift-squat") to "Back Squat",
            LiftId("lift-row") to "Barbell Row",
        )

    @Test
    @DisplayName("Identical workouts produce an empty summary")
    fun identicalWorkoutsAreEmpty() {
        val baseline = sampleWorkout()

        val summary = useCase(baseline, baseline, liftNames)

        assertTrue(summary.isEmpty)
        assertEquals(0, summary.rowCount)
    }

    @Test
    @DisplayName("Added lift produces a LiftAdded row with name and set count")
    fun addedLiftRow() {
        val baseline = sampleWorkout()
        val added = addedLift()
        val applied = baseline.copy(workoutLifts = baseline.workoutLifts + added)

        val summary = useCase(baseline, applied, liftNames)

        assertEquals(listOf(LiftAdded("Barbell Row", 2)), summary.rows)
    }

    @Test
    @DisplayName("Removed lift produces a LiftRemoved row")
    fun removedLiftRow() {
        val baseline = sampleWorkout()
        val applied = baseline.copy(workoutLifts = listOf(baseline.workoutLifts[1].copy(position = 0)))

        val summary = useCase(baseline, applied, liftNames)

        // Removal alone shifts positions but does not read as a reorder.
        assertEquals(listOf(LiftRemoved("Bench Press", 2)), summary.rows)
    }

    @Test
    @DisplayName("Swapped lift produces a LiftSwapped row with old and new names")
    fun swappedLiftRow() {
        val baseline = sampleWorkout()
        val applied =
            baseline.copy(
                workoutLifts =
                    listOf(
                        baseline.workoutLifts[0].copy(liftId = LiftId("lift-row")),
                        baseline.workoutLifts[1],
                    ),
            )

        val summary = useCase(baseline, applied, liftNames)

        assertEquals(listOf(LiftSwapped("Bench Press", "Barbell Row")), summary.rows)
    }

    @Test
    @DisplayName("STR-124: swapped equipment produces an EquipmentSwapped row with ids")
    fun swappedEquipmentRow() {
        val barbell = com.stratum.domain.lift.identity.EquipmentId("barbell")
        val ssb = com.stratum.domain.lift.identity.EquipmentId("ssb")
        val baseline =
            sampleWorkout().copy(
                workoutLifts =
                    sampleWorkout().workoutLifts.map { lift ->
                        lift.copy(equipmentId = barbell)
                    },
            )
        val applied =
            baseline.copy(
                workoutLifts =
                    listOf(
                        baseline.workoutLifts[0].copy(equipmentId = ssb),
                        baseline.workoutLifts[1],
                    ),
            )

        val summary = useCase(baseline, applied, liftNames)

        assertEquals(
            listOf(EquipmentSwapped("Bench Press", barbell, ssb)),
            summary.rows,
        )
    }

    @Test
    @DisplayName("STR-124: unchanged equipment produces no row")
    fun unchangedEquipmentNoRow() {
        val barbell = com.stratum.domain.lift.identity.EquipmentId("barbell")
        val baseline =
            sampleWorkout().copy(
                workoutLifts =
                    sampleWorkout().workoutLifts.map { lift ->
                        lift.copy(equipmentId = barbell)
                    },
            )

        val summary = useCase(baseline, baseline, liftNames)

        assertTrue(summary.isEmpty)
    }

    @Test
    @DisplayName("Reorder produces LiftMoved rows with old and new positions")
    fun reorderRows() {
        val baseline = sampleWorkout()
        val applied =
            baseline.copy(
                workoutLifts =
                    listOf(
                        baseline.workoutLifts[1].copy(position = 0),
                        baseline.workoutLifts[0].copy(position = 1),
                    ),
            )

        val summary = useCase(baseline, applied, liftNames)

        assertEquals(
            listOf(
                LiftMoved("Back Squat", oldPosition = 1, newPosition = 0),
                LiftMoved("Bench Press", oldPosition = 0, newPosition = 1),
            ),
            summary.rows,
        )
    }

    @Test
    @DisplayName("Added and removed sets net to one totals row with replaced churn")
    fun addedAndRemovedSetsNetToOneRow() {
        val baseline = sampleWorkout()
        val appliedLift =
            baseline.workoutLifts[0].copy(
                sets =
                    listOf(
                        baseline.workoutLifts[0].sets[1].copy(position = 0),
                        LiftSet(
                            id = LiftSetId("ls-new"),
                            workoutLiftId = WorkoutLiftId("wl-1"),
                            position = 1,
                            setType = SetType.Standard,
                            repMin = 8,
                            repMax = 12,
                            rirGoal = 1,
                            isTopSet = false,
                        ),
                    ),
            )
        val applied = baseline.copy(workoutLifts = listOf(appliedLift, baseline.workoutLifts[1]))

        val summary = useCase(baseline, applied, liftNames)

        assertEquals(
            listOf(SetCountChanged("Bench Press", oldCount = 2, newCount = 2, replacedCount = 1)),
            summary.rows,
        )
    }

    @Test
    @DisplayName("Added-only sets produce a totals row with no replaced churn")
    fun addedOnlySetRow() {
        val baseline = sampleWorkout()
        val appliedLift =
            baseline.workoutLifts[0].copy(
                sets =
                    baseline.workoutLifts[0].sets +
                        LiftSet(
                            id = LiftSetId("ls-new"),
                            workoutLiftId = WorkoutLiftId("wl-1"),
                            position = 2,
                            setType = SetType.Standard,
                            repMin = 8,
                            repMax = 12,
                            rirGoal = 1,
                            isTopSet = false,
                        ),
            )
        val applied = baseline.copy(workoutLifts = listOf(appliedLift, baseline.workoutLifts[1]))

        val summary = useCase(baseline, applied, liftNames)

        assertEquals(
            listOf(SetCountChanged("Bench Press", oldCount = 2, newCount = 3, replacedCount = 0)),
            summary.rows,
        )
    }

    @Test
    @DisplayName("Removed-only sets produce a totals row with no replaced churn")
    fun removedOnlySetRow() {
        val baseline = sampleWorkout()
        val appliedLift =
            baseline.workoutLifts[0].copy(
                sets = listOf(baseline.workoutLifts[0].sets[0]),
            )
        val applied = baseline.copy(workoutLifts = listOf(appliedLift, baseline.workoutLifts[1]))

        val summary = useCase(baseline, applied, liftNames)

        assertEquals(
            listOf(SetCountChanged("Bench Press", oldCount = 2, newCount = 1, replacedCount = 0)),
            summary.rows,
        )
    }

    @Test
    @DisplayName("Edited set shape produces a SetEdited row with old and new shapes")
    fun editedSetRow() {
        val baseline = sampleWorkout()
        val editedSet =
            baseline.workoutLifts[0].sets[0].copy(
                setType = SetType.MyoRep,
                myoRepKind = MyoRepKind.RepFloor,
                repMin = 8,
                repMax = 12,
                rirGoal = 1,
            )
        val applied =
            baseline.copy(
                workoutLifts =
                    listOf(
                        baseline.workoutLifts[0].copy(sets = listOf(editedSet, baseline.workoutLifts[0].sets[1])),
                        baseline.workoutLifts[1],
                    ),
            )

        val summary = useCase(baseline, applied, liftNames)

        assertEquals(
            listOf(
                SetEdited(
                    liftName = "Bench Press",
                    setPosition = 0,
                    oldShape =
                        TemplateSetShape(
                            setType = SetType.Standard,
                            myoRepKind = null,
                            repMin = 6,
                            repMax = 10,
                            rirGoal = 2,
                            isTopSet = true,
                        ),
                    newShape =
                        TemplateSetShape(
                            setType = SetType.MyoRep,
                            myoRepKind = MyoRepKind.RepFloor,
                            repMin = 8,
                            repMax = 12,
                            rirGoal = 1,
                            isTopSet = true,
                        ),
                ),
            ),
            summary.rows,
        )
    }

    @Test
    @DisplayName("Edited rest produces a RestEdited row with old and new seconds")
    fun editedRestRow() {
        val baseline = sampleWorkout()
        val editedSet = baseline.workoutLifts[0].sets[1].copy(restSeconds = 45)
        val applied =
            baseline.copy(
                workoutLifts =
                    listOf(
                        baseline.workoutLifts[0].copy(sets = listOf(baseline.workoutLifts[0].sets[0], editedSet)),
                        baseline.workoutLifts[1],
                    ),
            )

        val summary = useCase(baseline, applied, liftNames)

        assertEquals(
            listOf(RestEdited("Bench Press", setPosition = 1, oldSeconds = 120, newSeconds = 45)),
            summary.rows,
        )
    }

    @Test
    @DisplayName("Formed superset produces grouping rows naming the partners")
    fun supersetFormedRows() {
        val baseline = sampleWorkout()
        val groupId = WorkoutSupersetGroupId("ss-1")
        val applied =
            baseline.copy(
                workoutLifts = baseline.workoutLifts.map { it.copy(supersetGroupId = groupId) },
            )

        val summary = useCase(baseline, applied, liftNames)

        assertEquals(
            listOf(
                SupersetGroupingChanged("Bench Press", oldPartners = emptyList(), newPartners = listOf("Back Squat")),
                SupersetGroupingChanged("Back Squat", oldPartners = emptyList(), newPartners = listOf("Bench Press")),
            ),
            summary.rows,
        )
    }

    @Test
    @DisplayName("Cleared superset produces a grouping row on the unlinked lift only")
    fun supersetClearedRow() {
        val groupId = WorkoutSupersetGroupId("ss-1")
        val baseline =
            sampleWorkout().copy(
                workoutLifts = sampleWorkout().workoutLifts.map { it.copy(supersetGroupId = groupId) },
            )
        val applied =
            baseline.copy(
                workoutLifts =
                    listOf(
                        baseline.workoutLifts[0].copy(supersetGroupId = null),
                        baseline.workoutLifts[1],
                    ),
            )

        val summary = useCase(baseline, applied, liftNames)

        assertEquals(
            listOf(
                SupersetGroupingChanged("Bench Press", oldPartners = listOf("Back Squat"), newPartners = emptyList()),
            ),
            summary.rows,
        )
    }

    @Test
    @DisplayName("Rest-between-lifts toggle flip produces a row")
    fun restBetweenLiftsRow() {
        val baseline = sampleWorkout()
        val applied =
            baseline.copy(
                workoutLifts =
                    listOf(
                        baseline.workoutLifts[0].copy(useRestTimerBetweenLifts = true),
                        baseline.workoutLifts[1],
                    ),
            )

        val summary = useCase(baseline, applied, liftNames)

        assertEquals(
            listOf(RestBetweenLiftsChanged("Bench Press", oldEnabled = false, newEnabled = true)),
            summary.rows,
        )
    }

    @Test
    @DisplayName("Changed pinned notes produce a PinnedNotesChanged row")
    fun pinnedNotesRow() {
        val baseline =
            sampleWorkout().copy(
                workoutLifts =
                    listOf(
                        sampleWorkout().workoutLifts[0].copy(pinnedNote = "old cue"),
                        sampleWorkout().workoutLifts[1],
                    ),
            )
        val applied =
            baseline.copy(
                workoutLifts =
                    listOf(
                        baseline.workoutLifts[0].copy(pinnedNote = "new cue", pinnedNotes = listOf("new cue")),
                        baseline.workoutLifts[1],
                    ),
            )

        val summary = useCase(baseline, applied, liftNames)

        assertEquals(
            listOf(PinnedNotesChanged("Bench Press", oldNotes = listOf("old cue"), newNotes = listOf("new cue"))),
            summary.rows,
        )
    }

    @Test
    @DisplayName("Scalar-vs-list holding the same note text produces no row")
    fun sameNoteTextAcrossFormsIsNotAChange() {
        val baseline =
            sampleWorkout().copy(
                workoutLifts =
                    listOf(
                        sampleWorkout().workoutLifts[0].copy(pinnedNote = "cue"),
                        sampleWorkout().workoutLifts[1],
                    ),
            )
        val applied =
            baseline.copy(
                workoutLifts =
                    listOf(
                        baseline.workoutLifts[0].copy(pinnedNotes = listOf("cue")),
                        baseline.workoutLifts[1],
                    ),
            )

        val summary = useCase(baseline, applied, liftNames)

        assertTrue(summary.isEmpty)
    }

    @Test
    @DisplayName("Missing catalog name falls back to the unknown-lift label")
    fun unknownLiftFallback() {
        val baseline = sampleWorkout()
        val applied = baseline.copy(workoutLifts = listOf(baseline.workoutLifts[1].copy(position = 0)))

        val summary = useCase(baseline, applied, emptyMap())

        assertEquals(listOf(LiftRemoved(UNKNOWN_LIFT_DISPLAY_NAME, 2)), summary.rows)
    }

    @Test
    @DisplayName("Rows follow category order then lift position")
    fun rowOrdering() {
        val baseline = sampleWorkout()
        val editedSet = baseline.workoutLifts[1].sets[0].copy(restSeconds = 30)
        val movedAndEdited =
            baseline.workoutLifts[1].copy(position = 0, sets = listOf(editedSet))
        val applied =
            baseline.copy(
                workoutLifts =
                    listOf(
                        movedAndEdited,
                        baseline.workoutLifts[0].copy(position = 1),
                        addedLift().copy(position = 2),
                    ),
            )

        val summary = useCase(baseline, applied, liftNames)

        assertEquals(
            listOf(
                LiftAdded("Barbell Row", 2),
                LiftMoved("Back Squat", oldPosition = 1, newPosition = 0),
                LiftMoved("Bench Press", oldPosition = 0, newPosition = 1),
                RestEdited("Back Squat", setPosition = 0, oldSeconds = 120, newSeconds = 30),
            ),
            summary.rows,
        )
    }

    @Test
    @DisplayName("Diffing apply-output excludes session-only myo minis from the summary")
    fun appliedOutputExcludesMyoMinis() {
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
        val applied =
            ApplyOverlayToWorkoutUseCase()(baseline, WorkoutOverlay(addedSlots = listOf(mini)))

        val summary = useCase(baseline, applied, liftNames)

        assertTrue(summary.isEmpty)
    }

    @Test
    @DisplayName("Diffing apply-output surfaces overlay rest and reorder edits")
    fun appliedOutputSurfacesOverlayEdits() {
        val baseline = sampleWorkout()
        val overlay =
            WorkoutOverlay(
                restSecondsOverrides = mapOf(SetSlotId.Template(LiftSetId("ls-1")) to 45),
                orderedLiftIds = listOf("wl-2", "wl-1"),
            )
        val applied = ApplyOverlayToWorkoutUseCase()(baseline, overlay)

        val summary = useCase(baseline, applied, liftNames)

        assertEquals(
            listOf(
                LiftMoved("Back Squat", oldPosition = 1, newPosition = 0),
                LiftMoved("Bench Press", oldPosition = 0, newPosition = 1),
                RestEdited("Bench Press", setPosition = 0, oldSeconds = 120, newSeconds = 45),
            ),
            summary.rows,
        )
    }

    private fun addedLift(): WorkoutLift =
        WorkoutLift(
            id = WorkoutLiftId("wl-added"),
            workoutId = WorkoutId("w-1"),
            liftId = LiftId("lift-row"),
            progressionScheme = ProgressionScheme.Double,
            position = 2,
            sets =
                listOf(
                    LiftSet(
                        id = LiftSetId("ls-added-1"),
                        workoutLiftId = WorkoutLiftId("wl-added"),
                        position = 0,
                        setType = SetType.Standard,
                        repMin = 8,
                        repMax = 12,
                        rirGoal = 2,
                        isTopSet = true,
                    ),
                    LiftSet(
                        id = LiftSetId("ls-added-2"),
                        workoutLiftId = WorkoutLiftId("wl-added"),
                        position = 1,
                        setType = SetType.Standard,
                        repMin = 8,
                        repMax = 12,
                        rirGoal = 2,
                        isTopSet = false,
                    ),
                ),
        )

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
