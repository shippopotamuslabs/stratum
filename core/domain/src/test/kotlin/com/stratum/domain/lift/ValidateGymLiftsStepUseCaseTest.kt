package com.stratum.domain.lift

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ValidateGymLiftsStepUseCaseTest {

    private lateinit var subject: ValidateGymLiftsStepUseCase

    @BeforeEach
    fun setup() {
        subject = ValidateGymLiftsStepUseCase()
    }

    @Nested
    @DisplayName("enabled count")
    inner class EnabledCount {
        @Test
        @DisplayName("Given no lifts when validated then it is invalid (NoLiftsEnabled)")
        fun emptyIsInvalid() {
            assertInvalid(subject.invoke(emptyList()), GymLiftsValidationError.NoLiftsEnabled)
        }

        @Test
        @DisplayName("Given lifts but none enabled when validated then it is invalid")
        fun allDisabledIsInvalid() {
            val lifts = listOf(
                aLift(enabled = false),
                aLift(enabled = false),
            )
            assertInvalid(subject.invoke(lifts), GymLiftsValidationError.NoLiftsEnabled)
        }

        @Test
        @DisplayName("Given one enabled lift when validated then it is valid")
        fun oneEnabledIsValid() {
            val lifts = listOf(aLift(enabled = true), aLift(enabled = false))
            assertEquals(GymLiftsValidationResult.Valid, subject.invoke(lifts))
        }

        @Test
        @DisplayName("Given many enabled lifts when validated then it is valid")
        fun manyEnabledIsValid() {
            val lifts = listOf(
                aLift(enabled = true),
                aLift(enabled = true),
                aLift(enabled = true),
            )
            assertEquals(GymLiftsValidationResult.Valid, subject.invoke(lifts))
        }
    }

    private fun aLift(enabled: Boolean): Lift = Lift(
        id = LiftId("lift-$enabled-${System.nanoTime()}"),
        name = "Test Lift",
        movementPatterns = setOf(MovementPattern.SquatPattern),
        primaryMuscles = setOf(MuscleGroup.Quad),
        secondaryMuscles = emptySet(),
        incrementTiers = emptyList(),
        presetTags = emptySet(),
        enabled = enabled,
    )

    private fun assertInvalid(result: GymLiftsValidationResult, expected: GymLiftsValidationError) {
        assertTrue(result is GymLiftsValidationResult.Invalid, "Expected Invalid, got $result")
        assertTrue(
            (result as GymLiftsValidationResult.Invalid).errors.contains(expected),
            "Expected $expected in ${result.errors}",
        )
    }
}
