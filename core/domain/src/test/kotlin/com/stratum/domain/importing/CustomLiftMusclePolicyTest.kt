package com.stratum.domain.importing

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class CustomLiftMusclePolicyTest {
    @Test
    @DisplayName("Given horizontal pull without bicep secondary when normalizing then bicep is added")
    fun horizontalPullAddsBicepSecondary() {
        val normalized =
            CustomLiftMusclePolicy.withPatternSecondaryDefaults(
                InferredCustomLiftMetadata(
                    exerciseNameRaw = "Neutral Wide Grip Cable Row",
                    movementPattern = MovementPattern.HorizontalPull,
                    primaryMuscles = setOf(MuscleGroup.Back),
                    secondaryMuscles = emptySet(),
                ),
            )

        assertEquals(setOf(MuscleGroup.Bicep), normalized.secondaryMuscles)
    }

    @Test
    @DisplayName("Given vertical pull when normalizing then bicep is added as secondary")
    fun verticalPullAddsBicepSecondary() {
        val normalized =
            CustomLiftMusclePolicy.withPatternSecondaryDefaults(
                InferredCustomLiftMetadata(
                    exerciseNameRaw = "Wide Grip Lat Pulldown",
                    movementPattern = MovementPattern.VerticalPull,
                    primaryMuscles = setOf(MuscleGroup.Back),
                    secondaryMuscles = setOf(MuscleGroup.AnteriorDeltoid),
                ),
            )

        assertTrue(MuscleGroup.Bicep in normalized.secondaryMuscles)
        assertTrue(MuscleGroup.AnteriorDeltoid in normalized.secondaryMuscles)
    }

    @Test
    @DisplayName("Given bicep isolation when normalizing then bicep is not duplicated in secondary")
    fun bicepPrimaryIsNotDuplicatedInSecondary() {
        val normalized =
            CustomLiftMusclePolicy.withPatternSecondaryDefaults(
                InferredCustomLiftMetadata(
                    exerciseNameRaw = "Incline Dumbbell Biceps Curl",
                    movementPattern = MovementPattern.BicepIso,
                    primaryMuscles = setOf(MuscleGroup.Bicep),
                    secondaryMuscles = emptySet(),
                ),
            )

        assertEquals(emptySet<MuscleGroup>(), normalized.secondaryMuscles)
    }

    @Test
    @DisplayName("Given horizontal push without tricep secondary when normalizing then tricep is added")
    fun horizontalPushAddsTricepSecondary() {
        val normalized =
            CustomLiftMusclePolicy.withPatternSecondaryDefaults(
                InferredCustomLiftMetadata(
                    exerciseNameRaw = "Machine Chest Press",
                    movementPattern = MovementPattern.HorizontalPush,
                    primaryMuscles = setOf(MuscleGroup.Chest),
                    secondaryMuscles = emptySet(),
                ),
            )

        assertEquals(setOf(MuscleGroup.Tricep), normalized.secondaryMuscles)
    }

    @Test
    @DisplayName("Given tricep isolation when normalizing then tricep is not duplicated in secondary")
    fun tricepPrimaryIsNotDuplicatedInSecondary() {
        val normalized =
            CustomLiftMusclePolicy.withPatternSecondaryDefaults(
                InferredCustomLiftMetadata(
                    exerciseNameRaw = "Katana Triceps Extension",
                    movementPattern = MovementPattern.TricepIso,
                    primaryMuscles = setOf(MuscleGroup.Tricep),
                    secondaryMuscles = emptySet(),
                ),
            )

        assertEquals(emptySet<MuscleGroup>(), normalized.secondaryMuscles)
    }
}
