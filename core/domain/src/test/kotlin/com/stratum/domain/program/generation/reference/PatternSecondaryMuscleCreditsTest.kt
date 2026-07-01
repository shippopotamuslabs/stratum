package com.stratum.domain.program.generation.reference

import com.stratum.domain.program.generation.context.SECONDARY_SET_CREDIT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PatternSecondaryMuscleCreditsTest {
    @Test
    fun squatAndHingePatternsCreditGluteForRecoveryScheduling() {
        val squatSecondaries = PATTERN_SECONDARY_MUSCLE_CREDITS.getValue("SquatPattern")
        assertTrue(squatSecondaries.any { credit -> credit.muscle == "Glute" })
        assertEquals(SECONDARY_SET_CREDIT, squatSecondaries.first { it.muscle == "Glute" }.creditPerSet)

        val hingeSecondaries = PATTERN_SECONDARY_MUSCLE_CREDITS.getValue("HipHinge")
        assertTrue(hingeSecondaries.any { credit -> credit.muscle == "Glute" })
        assertTrue(hingeSecondaries.any { credit -> credit.muscle == "LowerBack" })
        // G7a: hip hinges (RDL/stiff-leg) drive hamstring secondary credit so solver recovery
        // accounting matches lift metadata — closes the credit-divergence gap.
        assertTrue(hingeSecondaries.any { credit -> credit.muscle == "Hamstring" })
        assertEquals(
            SECONDARY_SET_CREDIT,
            hingeSecondaries.first { it.muscle == "Hamstring" }.creditPerSet,
        )
    }

    @Test
    fun hipExtensionCreditsHamstringForRecoveryScheduling() {
        val hipExtension = PATTERN_SECONDARY_MUSCLE_CREDITS.getValue("HipExtension")
        assertTrue(hipExtension.any { credit -> credit.muscle == "Hamstring" })
        assertEquals(SECONDARY_SET_CREDIT, hipExtension.first { it.muscle == "Hamstring" }.creditPerSet)
    }

    @Test
    fun pressAndPullPatternsCreditTypicalSecondaryMuscles() {
        val horizontalPress = PATTERN_SECONDARY_MUSCLE_CREDITS.getValue("HorizontalPress")
        assertTrue(horizontalPress.any { it.muscle == "Tricep" })
        assertTrue(horizontalPress.any { it.muscle == "AnteriorDeltoid" })

        val horizontalPull = PATTERN_SECONDARY_MUSCLE_CREDITS.getValue("HorizontalPull")
        assertTrue(horizontalPull.any { it.muscle == "Bicep" })
        assertTrue(horizontalPull.any { it.muscle == "PosteriorDeltoid" })
    }
}
