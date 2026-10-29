package com.stratum.application.lift

import com.stratum.domain.onboarding.MuscleGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class CustomLiftFieldRulesTest {

    @Test
    @DisplayName("Empty movement patterns fail with the shared message")
    fun rejectsEmptyPatterns() {
        val result = CustomLiftFieldRules.validateMovementPatterns(emptySet())
        assertEquals(
            CustomLiftFieldRules.MOVEMENT_PATTERNS_REQUIRED_MESSAGE,
            result.exceptionOrNull()?.message,
        )
    }

    @Test
    @DisplayName("Empty primary muscles fail the primary-muscle rule")
    fun primaryRule() {
        val result = CustomLiftFieldRules.validatePrimaryMuscles(emptySet())
        assertEquals(
            CustomLiftFieldRules.PRIMARY_MUSCLES_REQUIRED_MESSAGE,
            result.exceptionOrNull()?.message,
        )
    }

    @Test
    @DisplayName("Rest override outside the shared range fails; inside and absent pass")
    fun restRangeRule() {
        assertTrue(CustomLiftFieldRules.validateDefaultRestSeconds(null).isSuccess)
        assertTrue(CustomLiftFieldRules.validateDefaultRestSeconds(150).isSuccess)
        assertTrue(CustomLiftFieldRules.validateDefaultRestSeconds(9999).isFailure)
    }

    @Test
    @DisplayName("Secondary muscles never duplicate a primary muscle")
    fun distinctSecondary() {
        assertEquals(
            setOf(MuscleGroup.Tricep),
            CustomLiftFieldRules.distinctSecondaryMuscles(
                primaryMuscles = setOf(MuscleGroup.Chest),
                secondaryMuscles = setOf(MuscleGroup.Chest, MuscleGroup.Tricep),
            ),
        )
    }
}
