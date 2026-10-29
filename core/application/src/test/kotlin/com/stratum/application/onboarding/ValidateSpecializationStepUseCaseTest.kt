package com.stratum.application.onboarding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.onboarding.SpecializationPreference
import com.stratum.domain.onboarding.SpecializationValidationError
import com.stratum.domain.onboarding.SpecializationValidationResult

class ValidateSpecializationStepUseCaseTest {

    private lateinit var subject: ValidateSpecializationStepUseCase

    @BeforeEach
    fun setup() {
        subject = ValidateSpecializationStepUseCase()
    }

    @Nested
    @DisplayName("cap")
    inner class Cap {
        @Test
        @DisplayName("Given no specialization when validated then it is valid (skip path)")
        fun emptyIsValid() {
            assertEquals(SpecializationValidationResult.Valid, subject.invoke(SpecializationPreference.Empty))
        }

        @Test
        @DisplayName("Given one specialized muscle when validated then it is valid")
        fun singleIsValid() {
            val preference = SpecializationPreference(
                levels = mapOf(MuscleGroup.Chest to SpecializationLevel.Specialize),
            )
            assertEquals(SpecializationValidationResult.Valid, subject.invoke(preference))
        }

        @Test
        @DisplayName("Given exactly the cap of specialized muscles when validated then it is valid")
        fun atCapIsValid() {
            val preference = specialize(MuscleGroup.Chest, MuscleGroup.Back, MuscleGroup.Quad)
            assertEquals(SpecializationValidationResult.Valid, subject.invoke(preference))
        }

        @Test
        @DisplayName("Given one over the cap when validated then TooManySpecialized is returned")
        fun overCapIsInvalid() {
            val preference = specialize(
                MuscleGroup.Chest,
                MuscleGroup.Back,
                MuscleGroup.Quad,
                MuscleGroup.Hamstring,
            )
            assertInvalid(subject.invoke(preference), SpecializationValidationError.TooManySpecialized)
        }
    }

    @Nested
    @DisplayName("maintenance / omit levels")
    inner class NonSpecializeLevels {
        @Test
        @DisplayName("Given only maintenance levels when validated then it is valid")
        fun onlyMaintenanceIsValid() {
            val preference = SpecializationPreference(
                levels = mapOf(
                    MuscleGroup.Chest to SpecializationLevel.Maintenance,
                    MuscleGroup.Back to SpecializationLevel.Maintenance,
                ),
            )
            assertEquals(SpecializationValidationResult.Valid, subject.invoke(preference))
        }

        @Test
        @DisplayName("Given maintenance levels mixed with a full cap of specializes when validated then it is valid")
        fun maintenanceDoesNotConsumeCap() {
            val preference = SpecializationPreference(
                levels = buildMap {
                    put(MuscleGroup.Chest, SpecializationLevel.Specialize)
                    put(MuscleGroup.Back, SpecializationLevel.Specialize)
                    put(MuscleGroup.Quad, SpecializationLevel.Specialize)
                    put(MuscleGroup.Hamstring, SpecializationLevel.Maintenance)
                    put(MuscleGroup.Glute, SpecializationLevel.Omit)
                },
            )
            assertEquals(SpecializationValidationResult.Valid, subject.invoke(preference))
        }
    }

    private fun specialize(vararg muscles: MuscleGroup): SpecializationPreference =
        SpecializationPreference(levels = muscles.associateWith { SpecializationLevel.Specialize })

    private fun assertInvalid(result: SpecializationValidationResult, expected: SpecializationValidationError) {
        assertTrue(result is SpecializationValidationResult.Invalid, "Expected Invalid, got $result")
        assertTrue(
            (result as SpecializationValidationResult.Invalid).errors.contains(expected),
            "Expected $expected in ${result.errors}",
        )
    }
}
