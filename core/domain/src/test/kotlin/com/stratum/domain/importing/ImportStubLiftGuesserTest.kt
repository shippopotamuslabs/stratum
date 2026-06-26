package com.stratum.domain.importing

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.onboarding.MuscleGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ImportStubLiftGuesserTest {
    @Test
    @DisplayName("Given row exercise name when guessing pattern then horizontal pull is returned")
    fun rowNameGuessesHorizontalPull() {
        assertEquals(
            MovementPattern.HorizontalPull,
            ImportStubLiftGuesser.guessMovementPattern("Iso-Lateral Row"),
        )
    }

    @Test
    @DisplayName("Given stub row definition when building then back primary and bicep secondary are set")
    fun rowDefinitionIncludesPullSecondaries() {
        val definition =
            ImportStubLiftGuesser.definitionFromName(
                name = "Leverage High Row",
                increment = Weight(5.0, Weight.WeightUnit.Lb),
            )

        assertEquals(MovementPattern.HorizontalPull, definition.movementPattern)
        assertEquals(setOf(MuscleGroup.Back), definition.primaryMuscles)
        assertEquals(setOf(MuscleGroup.Bicep), definition.secondaryMuscles)
    }
}
