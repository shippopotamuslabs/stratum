package com.stratum.domain.lift

import com.stratum.domain.onboarding.MuscleGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class CustomLiftFieldRulesTest {

    @Test
    @DisplayName("Empty movement patterns fail with the shared message")
    fun rejectsEmptyPatterns() {
        val result = CustomLiftFieldRules.validate(movementPatterns = emptySet())
        assertEquals(
            CustomLiftFieldRules.MOVEMENT_PATTERNS_REQUIRED_MESSAGE,
            result.exceptionOrNull()?.message,
        )
    }

    @Test
    @DisplayName("Empty primary muscles fail only when the path writes them")
    fun primaryRuleSkipsWhenNotWritten() {
        assertTrue(
            CustomLiftFieldRules.validate(
                movementPatterns = setOf(MovementPattern.SquatPattern),
            ).isSuccess,
        )
        val result =
            CustomLiftFieldRules.validate(
                movementPatterns = setOf(MovementPattern.SquatPattern),
                primaryMuscles = emptySet(),
            )
        assertEquals(
            CustomLiftFieldRules.PRIMARY_MUSCLES_REQUIRED_MESSAGE,
            result.exceptionOrNull()?.message,
        )
    }

    @Test
    @DisplayName("Rest override outside the shared range fails; inside and absent pass")
    fun restRangeRule() {
        val patterns = setOf(MovementPattern.SquatPattern)
        assertTrue(CustomLiftFieldRules.validate(patterns, defaultRestSeconds = null).isSuccess)
        assertTrue(CustomLiftFieldRules.validate(patterns, defaultRestSeconds = 150).isSuccess)
        assertTrue(CustomLiftFieldRules.validate(patterns, defaultRestSeconds = 9999).isFailure)
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
