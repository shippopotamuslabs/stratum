package com.stratum.domain.onboarding

import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.ValidateGymLiftsStepUseCase
import com.stratum.domain.lift.identity.LiftId
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class GetOnboardingStepCapabilitiesUseCaseTest {
    private lateinit var subject: GetOnboardingStepCapabilitiesUseCase

    @BeforeEach
    fun setup() {
        subject =
            GetOnboardingStepCapabilitiesUseCase(
                validateCurrentStep =
                    ValidateCurrentOnboardingStepUseCase(
                        validateProfileStep = ValidateProfileStepUseCase(),
                        validateScheduleStep = ValidateScheduleStepUseCase(),
                        validateSpecializationStep = ValidateSpecializationStepUseCase(),
                        validateGymLiftsStep = ValidateGymLiftsStepUseCase(),
                    ),
            )
    }

    @Test
    @DisplayName("Given incomplete profile on Profile step then step is invalid and back is unavailable")
    fun profileStepInvalidOnWelcome() {
        val capabilities =
            subject(
                step = OnboardingStep.Profile,
                profile = UserTrainingProfile.Empty,
                specialization = SpecializationPreference.Empty,
                lifts = emptyList(),
            )

        assertFalse(capabilities.stepValid)
        assertFalse(capabilities.gymLiftsValid)
        assertTrue(capabilities.canGoBack)
    }

    @Test
    @DisplayName("Given Welcome step then step is valid and back is unavailable")
    fun welcomeStepCapabilities() {
        val capabilities =
            subject(
                step = OnboardingStep.Welcome,
                profile = UserTrainingProfile.Empty,
                specialization = SpecializationPreference.Empty,
                lifts = emptyList(),
            )

        assertTrue(capabilities.stepValid)
        assertFalse(capabilities.gymLiftsValid)
        assertFalse(capabilities.canGoBack)
    }

    @Test
    @DisplayName("Given historical import path then gym lifts validation passes without preset")
    fun gymLiftsValidWithHistoricalImport() {
        val capabilities =
            subject(
                step = OnboardingStep.GymAndLifts,
                profile = UserTrainingProfile.Empty,
                specialization = SpecializationPreference.Empty,
                lifts = listOf(enabledLift()),
                selectedPreset = null,
                usedHistoricalImport = true,
            )

        assertTrue(capabilities.stepValid)
        assertTrue(capabilities.gymLiftsValid)
        assertTrue(capabilities.canGoBack)
    }

    private fun enabledLift(): Lift =
        Lift(
            id = LiftId("squat"),
            name = "Squat",
            movementPattern = MovementPattern.LegPush,
            primaryMuscles = setOf(MuscleGroup.Quad),
            secondaryMuscles = emptySet(),
            allowWeightChangeBetweenSets = false,
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = true,
        )
}
