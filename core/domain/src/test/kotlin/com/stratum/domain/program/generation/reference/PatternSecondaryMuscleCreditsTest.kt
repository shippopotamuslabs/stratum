package com.stratum.domain.program.generation.reference

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.generation.context.SECONDARY_SET_CREDIT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PatternSecondaryMuscleCreditsTest {
    @Test
    fun squatAndHingePatternsCreditGluteForRecoveryScheduling() {
        val squatSecondaries = PATTERN_SECONDARY_MUSCLE_CREDITS.getValue(MovementPattern.SquatPattern)
        assertTrue(squatSecondaries.any { credit -> credit.muscle == MuscleGroup.Glute })
        assertEquals(SECONDARY_SET_CREDIT, squatSecondaries.first { it.muscle == MuscleGroup.Glute }.creditPerSet)

        val hingeSecondaries = PATTERN_SECONDARY_MUSCLE_CREDITS.getValue(MovementPattern.HipHinge)
        assertTrue(hingeSecondaries.any { credit -> credit.muscle == MuscleGroup.Glute })
        assertTrue(hingeSecondaries.any { credit -> credit.muscle == MuscleGroup.LowerBack })
        // G7a: hip hinges (RDL/stiff-leg) drive hamstring secondary credit so solver recovery
        // accounting matches lift metadata — closes the credit-divergence gap.
        assertTrue(hingeSecondaries.any { credit -> credit.muscle == MuscleGroup.Hamstring })
        assertEquals(
            SECONDARY_SET_CREDIT,
            hingeSecondaries.first { it.muscle == MuscleGroup.Hamstring }.creditPerSet,
        )
    }

    @Test
    fun hipExtensionCreditsHamstringForRecoveryScheduling() {
        val hipExtension = PATTERN_SECONDARY_MUSCLE_CREDITS.getValue(MovementPattern.HipExtension)
        assertTrue(hipExtension.any { credit -> credit.muscle == MuscleGroup.Hamstring })
        assertEquals(SECONDARY_SET_CREDIT, hipExtension.first { it.muscle == MuscleGroup.Hamstring }.creditPerSet)
    }

    @Test
    fun pressAndPullPatternsCreditTypicalSecondaryMuscles() {
        val horizontalPress = PATTERN_SECONDARY_MUSCLE_CREDITS.getValue(MovementPattern.HorizontalPress)
        assertTrue(horizontalPress.any { it.muscle == MuscleGroup.Tricep })
        assertTrue(horizontalPress.any { it.muscle == MuscleGroup.AnteriorDeltoid })

        val horizontalPull = PATTERN_SECONDARY_MUSCLE_CREDITS.getValue(MovementPattern.HorizontalPull)
        assertTrue(horizontalPull.any { it.muscle == MuscleGroup.Bicep })
        assertTrue(horizontalPull.any { it.muscle == MuscleGroup.PosteriorDeltoid })
    }
}
