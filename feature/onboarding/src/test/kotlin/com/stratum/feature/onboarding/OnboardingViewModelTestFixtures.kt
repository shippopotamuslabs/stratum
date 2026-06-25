package com.stratum.feature.onboarding

import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.onboarding.FakeOnboardingRepository
import com.stratum.core.testing.onboarding.FakeSpecializationPreferenceRepository
import com.stratum.core.testing.onboarding.FakeUserTrainingProfileRepository
import com.stratum.domain.lift.ApplyGymPresetUseCase
import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.lift.ObserveSelectedPresetUseCase
import com.stratum.domain.lift.SetSelectedPresetUseCase
import com.stratum.domain.lift.ValidateGymLiftsStepUseCase
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.AdvanceOnboardingStepUseCase
import com.stratum.domain.onboarding.CompleteOnboardingUseCase
import com.stratum.domain.onboarding.GetOnboardingStateUseCase
import com.stratum.domain.onboarding.GetOnboardingWizardStateUseCase
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.ObserveSpecializationUseCase
import com.stratum.domain.onboarding.OnboardingProgress
import com.stratum.domain.onboarding.OnboardingStep
import com.stratum.domain.onboarding.SaveOnboardingProgressUseCase
import com.stratum.domain.onboarding.SpecializationPreference
import com.stratum.domain.onboarding.UpdateSpecializationUseCase
import com.stratum.domain.onboarding.UpdateUserTrainingProfileUseCase
import com.stratum.domain.onboarding.ValidateCurrentOnboardingStepUseCase
import com.stratum.domain.onboarding.ValidateProfileStepUseCase
import com.stratum.domain.onboarding.ValidateScheduleStepUseCase
import com.stratum.domain.onboarding.ValidateSpecializationStepUseCase

internal object OnboardingViewModelTestFixtures {

    val fakeRepository = FakeOnboardingRepository()
    val fakeProfileRepository = FakeUserTrainingProfileRepository()
    val fakeSpecializationRepository = FakeSpecializationPreferenceRepository()
    val fakeLiftRepository = FakeLiftRepository()

    fun resetDefaults() {
        fakeRepository.seed(
            OnboardingProgress(OnboardingStep.Welcome, startedAt = 0L, completedAt = null),
        )
        fakeProfileRepository.seed(null)
        fakeSpecializationRepository.seed(SpecializationPreference.Empty)
        fakeLiftRepository.seedLifts(emptyList())
        fakeLiftRepository.seedPreset(null)
    }

    fun buildViewModel(): OnboardingViewModel = OnboardingViewModel(
        getOnboardingWizardState = GetOnboardingWizardStateUseCase(
            getOnboardingState = GetOnboardingStateUseCase(fakeRepository, fakeProfileRepository),
            observeSpecialization = ObserveSpecializationUseCase(fakeSpecializationRepository),
            observeLiftCatalog = ObserveLiftCatalogUseCase(fakeLiftRepository),
            observeSelectedPreset = ObserveSelectedPresetUseCase(fakeLiftRepository),
        ),
        advanceOnboardingStep = AdvanceOnboardingStepUseCase(
            validateCurrentStep = ValidateCurrentOnboardingStepUseCase(
                validateProfileStep = ValidateProfileStepUseCase(),
                validateScheduleStep = ValidateScheduleStepUseCase(),
                validateSpecializationStep = ValidateSpecializationStepUseCase(),
                validateGymLiftsStep = ValidateGymLiftsStepUseCase(),
            ),
            updateUserTrainingProfile = UpdateUserTrainingProfileUseCase(fakeProfileRepository),
            updateSpecialization = UpdateSpecializationUseCase(fakeSpecializationRepository),
            saveOnboardingProgress = SaveOnboardingProgressUseCase(fakeRepository),
        ),
        validateCurrentStep = ValidateCurrentOnboardingStepUseCase(
            validateProfileStep = ValidateProfileStepUseCase(),
            validateScheduleStep = ValidateScheduleStepUseCase(),
            validateSpecializationStep = ValidateSpecializationStepUseCase(),
            validateGymLiftsStep = ValidateGymLiftsStepUseCase(),
        ),
        applyGymPreset = ApplyGymPresetUseCase(fakeLiftRepository),
        setSelectedPreset = SetSelectedPresetUseCase(fakeLiftRepository),
        completeOnboarding = CompleteOnboardingUseCase(fakeRepository, fakeProfileRepository),
    )

    fun aLift(
        slug: String,
        tags: Set<GymPreset> = emptySet(),
        enabled: Boolean = false,
    ): Lift = Lift(
        id = LiftId(slug),
        name = slug.replace('-', ' ').replaceFirstChar { it.uppercase() },
        movementPattern = MovementPattern.LegPush,
        primaryMuscles = setOf(MuscleGroup.Quad),
        secondaryMuscles = emptySet(),
        allowWeightChangeBetweenSets = false,
        incrementTiers = emptyList(),
        presetTags = tags,
        enabled = enabled,
    )

    fun stepId(step: OnboardingStep): String = step.name
}
