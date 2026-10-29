package com.stratum.application.workout

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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ProjectWorkoutStructure isolation scheme coercion")
class ProjectWorkoutStructureIsolationSchemeTest {

    @Test
    fun `isolation Linear is projected as Double`() {
        val liftId = LiftId("curl")
        val workout = Workout(
            id = WorkoutId("w-1"),
            programId = ProgramId("p-1"),
            name = "Day",
            weekday = Weekday.Monday,
            position = 0,
            workoutLifts = listOf(
                WorkoutLift(
                    id = WorkoutLiftId("wl-1"),
                    workoutId = WorkoutId("w-1"),
                    liftId = liftId,
                    progressionScheme = ProgressionScheme.Linear,
                    position = 0,
                    sets = listOf(
                        LiftSet(
                            id = LiftSetId("ls-1"),
                            workoutLiftId = WorkoutLiftId("wl-1"),
                            position = 0,
                            setType = SetType.Standard,
                            repMin = 8,
                            repMax = 12,
                            rirGoal = 2,
                            isTopSet = true,
                        ),
                    ),
                ),
            ),
        )
        val projection = ProjectWorkoutStructureUseCase()(
            workout,
            mapOf(liftId to "Curl"),
            isCompoundById = mapOf(liftId to false),
        )
        assertEquals(ProgressionScheme.Double, projection.lifts.single().progressionScheme)
    }

    @Test
    fun `compound Linear stays Linear`() {
        val liftId = LiftId("squat")
        val workout = Workout(
            id = WorkoutId("w-1"),
            programId = ProgramId("p-1"),
            name = "Day",
            weekday = Weekday.Monday,
            position = 0,
            workoutLifts = listOf(
                WorkoutLift(
                    id = WorkoutLiftId("wl-1"),
                    workoutId = WorkoutId("w-1"),
                    liftId = liftId,
                    progressionScheme = ProgressionScheme.Linear,
                    position = 0,
                    sets = listOf(
                        LiftSet(
                            id = LiftSetId("ls-1"),
                            workoutLiftId = WorkoutLiftId("wl-1"),
                            position = 0,
                            setType = SetType.Standard,
                            repMin = 5,
                            repMax = 5,
                            rirGoal = 2,
                            isTopSet = true,
                        ),
                    ),
                ),
            ),
        )
        val projection = ProjectWorkoutStructureUseCase()(
            workout,
            mapOf(liftId to "Squat"),
            isCompoundById = mapOf(liftId to true),
        )
        assertEquals(ProgressionScheme.Linear, projection.lifts.single().progressionScheme)
    }
}
