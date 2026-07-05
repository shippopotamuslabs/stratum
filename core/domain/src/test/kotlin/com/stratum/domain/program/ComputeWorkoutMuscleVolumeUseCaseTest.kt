package com.stratum.domain.program

import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ComputeWorkoutMuscleVolumeUseCaseTest {
    private val subject = ComputeWorkoutMuscleVolumeUseCase()

    @Test
    @DisplayName("ComputeWorkoutMuscleVolumeUseCase credits primary and secondary muscles for one workout")
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

        val summaries =
            subject(
                workoutLifts =
                    listOf(
                        WorkoutLiftVolumeInput(liftId = "bench-press", setCount = 2),
                    ),
                lifts = listOf(bench),
            )

        val chest = summaries.first { summary -> summary.muscle == MuscleGroup.Chest }
        val tricep = summaries.first { summary -> summary.muscle == MuscleGroup.Tricep }
        assertEquals(2.0, chest.setCount)
        assertEquals(1, chest.exerciseCount)
        assertEquals(1.0, tricep.setCount)
    }
}
