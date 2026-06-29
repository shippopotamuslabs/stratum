package com.stratum.domain.program

import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

class ComputeProgramMuscleVolumeUseCaseTest {
    private val subject = ComputeProgramMuscleVolumeUseCase()

    @Test
    @DisplayName("ComputeProgramMuscleVolumeUseCase credits primary and secondary muscles")
    fun computeVolume_creditsPrimaryAndSecondary() {
        val bench =
            Lift(
                id = LiftId("bench-press"),
                name = "Bench Press",
                movementPatterns = emptySet(),
                primaryMuscles = setOf(MuscleGroup.Chest),
                secondaryMuscles = setOf(MuscleGroup.Tricep),
                allowWeightChangeBetweenSets = true,
                incrementTiers = emptyList(),
                presetTags = emptySet(),
                enabled = true,
            )
        val program =
            Program(
                id = ProgramId("p-1"),
                name = "Test",
                createdAt = Instant.parse("2026-06-26T12:00:00Z"),
                cycleLength = 1,
                trainingWeekdays = setOf(Weekday.Monday),
                workouts =
                    listOf(
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
                                                    id = LiftSetId("ls-1"),
                                                    workoutLiftId = WorkoutLiftId("wl-1"),
                                                    position = 0,
                                                    setType = SetType.Standard,
                                                    repMin = 8,
                                                    repMax = 12,
                                                    rirGoal = 2,
                                                    isTopSet = true,
                                                ),
                                                LiftSet(
                                                    id = LiftSetId("ls-2"),
                                                    workoutLiftId = WorkoutLiftId("wl-1"),
                                                    position = 1,
                                                    setType = SetType.Standard,
                                                    repMin = 8,
                                                    repMax = 12,
                                                    rirGoal = 1,
                                                    isTopSet = false,
                                                ),
                                            ),
                                    ),
                                ),
                        ),
                    ),
            )

        val summaries = subject(program, listOf(bench))

        val chest = summaries.first { summary -> summary.muscle == MuscleGroup.Chest }
        val tricep = summaries.first { summary -> summary.muscle == MuscleGroup.Tricep }
        assertEquals(2.0, chest.setCount)
        assertEquals(1, chest.exerciseCount)
        assertEquals(1.0, tricep.setCount)
    }
}
