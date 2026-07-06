package com.stratum.feature.program

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.Weekday
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
import com.stratum.core.ui.component.isLastInSupersetChain
import com.stratum.feature.program.ui.EditLiftState
import com.stratum.feature.program.ui.EditSetState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class EditWorkoutDomainMapperTest {
    @Test
    @DisplayName("toWorkout maps edited sets with rest seconds and superset fields")
    fun mapsEditedWorkout() {
        val baseline = baselineWorkout()
        val edited =
            listOf(
                editLift(
                    workoutLiftId = "wl-1",
                    position = 0,
                    setId = "ls-1",
                    restSeconds = 75,
                    groupId = "sg-1",
                    useRestBetween = true,
                ),
                editLift(
                    workoutLiftId = "wl-2",
                    position = 1,
                    setId = "ls-2",
                    restSeconds = 90,
                    groupId = "sg-1",
                    useRestBetween = false,
                ),
            )

        val workout = edited.toWorkout(baseline, WorkoutId("w-1"))

        assertEquals(75, workout.workoutLifts[0].sets.single().restSeconds)
        assertEquals(WorkoutSupersetGroupId("sg-1"), workout.workoutLifts[0].supersetGroupId)
        assertTrue(workout.workoutLifts[0].useRestTimerBetweenLifts)
        assertFalse(workout.workoutLifts[1].useRestTimerBetweenLifts)
        assertEquals(ProgressionScheme.Linear, workout.workoutLifts[0].progressionScheme)
    }

    @Test
    @DisplayName("toWorkout defaults new set fields from progression scheme")
    fun mapsNewSetDefaults() {
        val baseline = baselineWorkout()
        val edited =
            listOf(
                editLift(
                    workoutLiftId = "wl-1",
                    position = 0,
                    setId = "ls-1",
                    restSeconds = 120,
                ).copy(
                    sets =
                        listOf(
                            EditSetState("ls-1", 1, 8, 12, 120, 2),
                            EditSetState("new-set", 2, 8, 12, 60, 2),
                        ),
                ),
            )

        val workout = edited.toWorkout(baseline, WorkoutId("w-1"))
        val newSet = workout.workoutLifts.single().sets.last()

        assertEquals(SetType.Standard, newSet.setType)
        assertFalse(newSet.isTopSet)
        assertEquals(60, newSet.restSeconds)
    }

    @Test
    @DisplayName("hasPlaceholderLift detects lifts missing from catalog")
    fun detectsPlaceholderLift() {
        val lifts =
            listOf(
                editLift("wl-1", 0, "ls-1", 120),
                editLift("wl-2", 1, "ls-2", 120).copy(liftId = "missing-lift"),
            )

        assertTrue(lifts.hasPlaceholderLift(setOf("bench-press")))
    }

    @Test
    @DisplayName("isLastInSupersetChain uses position ordering within group")
    fun lastInChainUsesPosition() {
        val lifts =
            listOf(
                editLift("wl-1", 0, "ls-1", 120, groupId = "sg-1"),
                editLift("wl-2", 1, "ls-2", 120, groupId = "sg-1"),
            )

        assertFalse(isLastInSupersetChain(lifts[0], lifts))
        assertTrue(isLastInSupersetChain(lifts[1], lifts))
    }

    private fun baselineWorkout(): Workout =
        Workout(
            id = WorkoutId("w-1"),
            programId = ProgramId("p-1"),
            name = "Push",
            weekday = Weekday.Monday,
            position = 0,
            workoutLifts =
                listOf(
                    WorkoutLift(
                        id = WorkoutLiftId("wl-1"),
                        workoutId = WorkoutId("w-1"),
                        liftId = LiftId("bench-press"),
                        progressionScheme = ProgressionScheme.Linear,
                        position = 0,
                        sets =
                            listOf(
                                LiftSet(
                                    LiftSetId("ls-1"),
                                    WorkoutLiftId("wl-1"),
                                    0,
                                    SetType.Standard,
                                    8,
                                    12,
                                    2,
                                    isTopSet = true,
                                    restSeconds = 120,
                                ),
                            ),
                    ),
                    WorkoutLift(
                        id = WorkoutLiftId("wl-2"),
                        workoutId = WorkoutId("w-1"),
                        liftId = LiftId("incline-press"),
                        progressionScheme = ProgressionScheme.Linear,
                        position = 1,
                        sets =
                            listOf(
                                LiftSet(
                                    LiftSetId("ls-2"),
                                    WorkoutLiftId("wl-2"),
                                    0,
                                    SetType.Standard,
                                    10,
                                    10,
                                    2,
                                    isTopSet = true,
                                ),
                            ),
                    ),
                ),
        )

    private fun editLift(
        workoutLiftId: String,
        position: Int,
        setId: String,
        restSeconds: Int,
        groupId: String? = null,
        useRestBetween: Boolean = false,
    ): EditLiftState =
        EditLiftState(
            workoutLiftId = workoutLiftId,
            liftId = if (workoutLiftId == "wl-2") "incline-press" else "bench-press",
            name = workoutLiftId,
            position = position,
            sets =
                listOf(
                    EditSetState(
                        setId = setId,
                        setNumber = 1,
                        repMin = 8,
                        repMax = 12,
                        restSeconds = restSeconds,
                        rirGoal = 2,
                    ),
                ),
            supersetGroupId = groupId,
            useRestTimerBetweenLifts = useRestBetween,
            progressionScheme = ProgressionScheme.Linear,
        )
}
