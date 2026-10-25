package com.stratum.feature.program

import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.WorkoutLiftVolumeInput
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.feature.program.ui.DEFAULT_REP_MAX
import com.stratum.feature.program.ui.DEFAULT_REP_MIN
import com.stratum.feature.program.ui.DEFAULT_REST_SECONDS
import com.stratum.feature.program.ui.DEFAULT_RIR_GOAL
import com.stratum.feature.program.ui.EditLiftState
import com.stratum.feature.program.ui.EditSetState
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class EditWorkoutProgramVolumeTest {
    @Test
    @DisplayName("EW-PV-401 edited workout replaces persisted lifts in program volume inputs")
    fun ewPv401_editedWorkoutReplacesPersistedLifts() {
        val editedLifts =
            listOf(
                editLift(
                    workoutLiftId = "wl-1",
                    liftId = "bench-press",
                    setCount = 4,
                ),
            )
        val inputs =
            buildProgramVolumeInputsWithEditedWorkout(
                program = PROGRAM,
                editedWorkoutId = PUSH_WORKOUT_ID,
                editedLifts = editedLifts,
            )

        assertEquals(
            listOf(
                WorkoutLiftVolumeInput(liftId = "bench-press", setCount = 4),
                WorkoutLiftVolumeInput(liftId = "barbell-row", setCount = 3),
            ),
            inputs,
        )
    }

    @Test
    @DisplayName("EW-PV-402 non-edited workouts keep persisted set counts")
    fun ewPv402_nonEditedWorkoutsKeepPersistedSetCounts() {
        val editedLifts =
            listOf(
                editLift(
                    workoutLiftId = "wl-3",
                    liftId = "barbell-row",
                    setCount = 5,
                ),
            )
        val inputs =
            buildProgramVolumeInputsWithEditedWorkout(
                program = PROGRAM,
                editedWorkoutId = PULL_WORKOUT_ID,
                editedLifts = editedLifts,
            )

        assertEquals(
            listOf(
                WorkoutLiftVolumeInput(liftId = "bench-press", setCount = 2),
                WorkoutLiftVolumeInput(liftId = "incline-press", setCount = 1),
                WorkoutLiftVolumeInput(liftId = "barbell-row", setCount = 5),
            ),
            inputs,
        )
    }

    private fun editLift(
        workoutLiftId: String,
        liftId: String,
        setCount: Int,
    ): EditLiftState =
        EditLiftState(
            workoutLiftId = workoutLiftId,
            liftId = liftId,
            name = liftId,
            position = 0,
            sets =
                (1..setCount).map { index ->
                    EditSetState(
                        setId = "set-$workoutLiftId-$index",
                        setNumber = index,
                        repMin = DEFAULT_REP_MIN,
                        repMax = DEFAULT_REP_MAX,
                        restSeconds = DEFAULT_REST_SECONDS,
                        rirGoal = DEFAULT_RIR_GOAL,
                    )
                },
        )

    private companion object {
        const val PUSH_WORKOUT_ID = "w-push"
        const val PULL_WORKOUT_ID = "w-pull"

        val PROGRAM: Program =
            Program(
                id = ProgramId("p-1"),
                name = "Push/Pull",
                createdAt = Instant.parse("2026-06-26T12:00:00Z"),
                cycleLength = 1,
                trainingWeekdays = setOf(Weekday.Monday, Weekday.Wednesday),
                workouts =
                    listOf(
                        Workout(
                            id = WorkoutId(PUSH_WORKOUT_ID),
                            programId = ProgramId("p-1"),
                            name = "Push Day",
                            weekday = Weekday.Monday,
                            position = 0,
                            workoutLifts =
                                listOf(
                                    persistedLift("wl-1", PUSH_WORKOUT_ID, "bench-press", setCount = 2),
                                    persistedLift("wl-2", PUSH_WORKOUT_ID, "incline-press", setCount = 1, position = 1),
                                ),
                        ),
                        Workout(
                            id = WorkoutId(PULL_WORKOUT_ID),
                            programId = ProgramId("p-1"),
                            name = "Pull Day",
                            weekday = Weekday.Wednesday,
                            position = 1,
                            workoutLifts =
                                listOf(
                                    persistedLift("wl-3", PULL_WORKOUT_ID, "barbell-row", setCount = 3),
                                ),
                        ),
                    ),
            )

        private fun persistedLift(
            workoutLiftId: String,
            workoutId: String,
            liftId: String,
            setCount: Int,
            position: Int = 0,
        ): WorkoutLift =
            WorkoutLift(
                id = WorkoutLiftId(workoutLiftId),
                workoutId = WorkoutId(workoutId),
                liftId = LiftId(liftId),
                progressionScheme = ProgressionScheme.Linear,
                position = position,
                sets =
                    (0 until setCount).map { index ->
                        LiftSet(
                            id = LiftSetId("ls-$workoutLiftId-$index"),
                            workoutLiftId = WorkoutLiftId(workoutLiftId),
                            position = index,
                            setType = SetType.Standard,
                            repMin = 8,
                            repMax = 12,
                            rirGoal = 2,
                            isTopSet = index == 0,
                        )
                    },
            )
    }
}
