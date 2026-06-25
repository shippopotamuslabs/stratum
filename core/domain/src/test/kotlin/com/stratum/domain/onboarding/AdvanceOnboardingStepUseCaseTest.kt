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

class AdvanceOnboardingStepUseCaseTest {

    private val fakeOnboarding = FakeOnboardingRepository()
    private val fakeProfile = FakeUserTrainingProfileRepository()
    private val fakeSpecialization = FakeSpecializationPreferenceRepository()
    private lateinit var subject: AdvanceOnboardingStepUseCase

    @BeforeEach
    fun setup() {
        fakeOnboarding.seed(
            OnboardingProgress(OnboardingStep.Profile, startedAt = 0L, completedAt = null),
        )
        fakeProfile.seed(null)
        fakeSpecialization.seed(SpecializationPreference.Empty)
        subject = AdvanceOnboardingStepUseCase(
            validateCurrentStep = ValidateCurrentOnboardingStepUseCase(
                validateProfileStep = ValidateProfileStepUseCase(),
                validateScheduleStep = ValidateScheduleStepUseCase(),
                validateSpecializationStep = ValidateSpecializationStepUseCase(),
                validateGymLiftsStep = ValidateGymLiftsStepUseCase(),
            ),
            updateUserTrainingProfile = UpdateUserTrainingProfileUseCase(fakeProfile),
            updateSpecialization = UpdateSpecializationUseCase(fakeSpecialization),
            saveOnboardingProgress = SaveOnboardingProgressUseCase(fakeOnboarding),
        )
    }

    @Test
    @DisplayName("Given invalid profile when advancing forward then validation fails and step unchanged")
    fun forwardValidationFailed() = runTest {
        val result = subject(
            currentStep = OnboardingStep.Profile,
            direction = OnboardingStepDirection.Forward,
            profile = UserTrainingProfile.Empty,
            specialization = SpecializationPreference.Empty,
            lifts = emptyList(),
            selectedPreset = null,
        )

        assertEquals(AdvanceOnboardingStepResult.ValidationFailed, result)
        assertEquals(OnboardingStep.Profile, fakeOnboarding.current.currentStep)
    }

    @Test
    @DisplayName("Given valid profile when advancing forward then step advances and data persists")
    fun forwardSuccess() = runTest {
        val profile = UserTrainingProfile(
            trainingAge = TrainingAge.Intermediate,
            lastTrained = LastTrainedBucket.WithinLastMonth,
        )

        val result = subject(
            currentStep = OnboardingStep.Profile,
            direction = OnboardingStepDirection.Forward,
            profile = profile,
            specialization = SpecializationPreference.Empty,
            lifts = emptyList(),
            selectedPreset = null,
        )

        assertTrue(result is AdvanceOnboardingStepResult.Success)
        assertEquals(OnboardingStep.Schedule, (result as AdvanceOnboardingStepResult.Success).newStep)
        assertEquals(profile, fakeProfile.current)
        assertEquals(OnboardingStep.Schedule, fakeOnboarding.current.currentStep)
    }

    @Test
    @DisplayName("Given any state when going back then step retreats without validation")
    fun backwardNoValidation() = runTest {
        val profile = UserTrainingProfile(trainingAge = TrainingAge.Beginner)

        val result = subject(
            currentStep = OnboardingStep.Profile,
            direction = OnboardingStepDirection.Backward,
            profile = profile,
            specialization = SpecializationPreference.Empty,
            lifts = emptyList(),
            selectedPreset = null,
        )

        assertTrue(result is AdvanceOnboardingStepResult.Success)
        assertEquals(OnboardingStep.Welcome, (result as AdvanceOnboardingStepResult.Success).newStep)
        assertEquals(TrainingAge.Beginner, fakeProfile.current?.trainingAge)
    }

    @Test
    @DisplayName("Given Welcome step when going back then returns NoOp")
    fun backwardFromFirstIsNoOp() = runTest {
        fakeOnboarding.seed(
            OnboardingProgress(OnboardingStep.Welcome, startedAt = 0L, completedAt = null),
        )

        val result = subject(
            currentStep = OnboardingStep.Welcome,
            direction = OnboardingStepDirection.Backward,
            profile = UserTrainingProfile.Empty,
            specialization = SpecializationPreference.Empty,
            lifts = emptyList(),
            selectedPreset = null,
        )

        assertEquals(AdvanceOnboardingStepResult.NoOp, result)
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
