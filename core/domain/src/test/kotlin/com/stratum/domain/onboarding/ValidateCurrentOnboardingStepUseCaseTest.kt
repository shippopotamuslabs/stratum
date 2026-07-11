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
            validateMinimumVolumeStep = ValidateMinimumVolumeStepUseCase(),
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
                lastTrained = LastTrainedBucket.WithinLastThreeMonths,
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
    @DisplayName("Given historical import path on GymAndLifts step then validation passes without preset")
    fun gymLiftsValidWithHistoricalImport() {
        val result = subject(
            step = OnboardingStep.GymAndLifts,
            profile = UserTrainingProfile.Empty,
            specialization = SpecializationPreference.Empty,
            lifts = listOf(enabledLift()),
            selectedPreset = null,
            usedHistoricalImport = true,
        )
        assertTrue(result is OnboardingStepValidationResult.Valid)
    }

    private fun enabledLift(): Lift = Lift(
        id = LiftId("squat"),
        name = "Squat",
        movementPatterns = setOf(MovementPattern.SquatPattern),
        primaryMuscles = setOf(MuscleGroup.Quad),
        secondaryMuscles = emptySet(),
        incrementTiers = emptyList(),
        presetTags = emptySet(),
        enabled = true,
    )
}
