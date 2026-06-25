package com.stratum.domain.onboarding

import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.Lift
import javax.inject.Inject

/** Direction of wizard step navigation. */
enum class OnboardingStepDirection {
    Forward,
    Backward,
}

/** Outcome of attempting to advance or retreat in the wizard. */
sealed interface AdvanceOnboardingStepResult {
    data class Success(val newStep: OnboardingStep) : AdvanceOnboardingStepResult
    data object ValidationFailed : AdvanceOnboardingStepResult
    data object NoOp : AdvanceOnboardingStepResult
}

/**
 * Encapsulates the triple-write on step navigation: validate (forward only), persist profile,
 * persist specialization, save new step.
 */
class AdvanceOnboardingStepUseCase @Inject constructor(
    private val validateCurrentStep: ValidateCurrentOnboardingStepUseCase,
    private val updateUserTrainingProfile: UpdateUserTrainingProfileUseCase,
    private val updateSpecialization: UpdateSpecializationUseCase,
    private val saveOnboardingProgress: SaveOnboardingProgressUseCase,
) {
    suspend operator fun invoke(
        currentStep: OnboardingStep,
        direction: OnboardingStepDirection,
        profile: UserTrainingProfile,
        specialization: SpecializationPreference,
        lifts: List<Lift>,
        selectedPreset: GymPreset?,
    ): AdvanceOnboardingStepResult {
        val targetStep = when (direction) {
            OnboardingStepDirection.Forward -> currentStep.next()
            OnboardingStepDirection.Backward -> currentStep.previous()
        } ?: return AdvanceOnboardingStepResult.NoOp

        if (direction == OnboardingStepDirection.Forward) {
            val validation = validateCurrentStep(
                step = currentStep,
                profile = profile,
                specialization = specialization,
                lifts = lifts,
                selectedPreset = selectedPreset,
            )
            if (!validation.isValid) {
                return AdvanceOnboardingStepResult.ValidationFailed
            }
        }

        updateUserTrainingProfile(profile)
        updateSpecialization(specialization)
        saveOnboardingProgress(targetStep)
        return AdvanceOnboardingStepResult.Success(targetStep)
    }
}
