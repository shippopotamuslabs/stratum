package com.stratum.domain.onboarding

import com.stratum.core.testing.onboarding.FakeOnboardingRepository
import com.stratum.core.testing.onboarding.FakeSpecializationPreferenceRepository
import com.stratum.core.testing.onboarding.FakeUserTrainingProfileRepository
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.ValidateGymLiftsStepUseCase
import com.stratum.domain.lift.identity.LiftId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ValidateCurrentOnboardingStepUseCaseTest {

    private lateinit var subject: ValidateCurrentOnboardingStepUseCase

    @BeforeEach
    fun setup() {
        subject = ValidateCurrentOnboardingStepUseCase(
            validateProfileStep = ValidateProfileStepUseCase(),
            validateScheduleStep = ValidateScheduleStepUseCase(),
            validateSpecializationStep = ValidateSpecializationStepUseCase(),
            validateGymLiftsStep = ValidateGymLiftsStepUseCase(),
        )
    }

    @Test
    @DisplayName("Given incomplete profile on Profile step then validation fails")
    fun profileStepInvalid() {
        val result = subject(
            step = OnboardingStep.Profile,
            profile = UserTrainingProfile.Empty,
            specialization = SpecializationPreference.Empty,
            lifts = emptyList(),
        )
        assertTrue(result is OnboardingStepValidationResult.Invalid)
    }

    @Test
    @DisplayName("Given complete profile on Profile step then validation passes")
    fun profileStepValid() {
        val result = subject(
            step = OnboardingStep.Profile,
            profile = UserTrainingProfile(
                trainingAge = TrainingAge.Intermediate,
                lastTrained = LastTrainedBucket.WithinLastMonth,
            ),
            specialization = SpecializationPreference.Empty,
            lifts = emptyList(),
        )
        assertTrue(result is OnboardingStepValidationResult.Valid)
    }

    @Test
    @DisplayName("Given empty specialization on Specialization step then validation passes")
    fun specializationSkipIsValid() {
        val result = subject(
            step = OnboardingStep.Specialization,
            profile = UserTrainingProfile.Empty,
            specialization = SpecializationPreference.Empty,
            lifts = emptyList(),
        )
        assertTrue(result is OnboardingStepValidationResult.Valid)
    }

    @Test
    @DisplayName("Given enabled lifts on GymAndLifts step then validation passes without preset")
    fun gymLiftsValidWithEnabledLifts() {
        val result = subject(
            step = OnboardingStep.GymAndLifts,
            profile = UserTrainingProfile.Empty,
            specialization = SpecializationPreference.Empty,
            lifts = listOf(enabledLift()),
            selectedPreset = null,
        )
        assertTrue(result is OnboardingStepValidationResult.Valid)
    }

    @Test
    @DisplayName("Given Welcome step then validation always passes")
    fun welcomeAlwaysValid() {
        val result = subject(
            step = OnboardingStep.Welcome,
            profile = UserTrainingProfile.Empty,
            specialization = SpecializationPreference.Empty,
            lifts = emptyList(),
        )
        assertTrue(result is OnboardingStepValidationResult.Valid)
    }

    private fun enabledLift(): Lift = Lift(
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
