package com.stratum.domain.onboarding

import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.Lift

/**
 * Read model for the onboarding wizard — every persisted stream the wizard needs in one
 * snapshot. Composed by [GetOnboardingWizardStateUseCase]; transient draft edits live
 * in the ViewModel, not here.
 */
data class OnboardingWizardState(
    val progress: OnboardingProgress?,
    val profile: UserTrainingProfile?,
    val specialization: SpecializationPreference,
    val lifts: List<Lift>,
    val selectedPreset: GymPreset?,
    val usedHistoricalImport: Boolean = false,
) {
    val currentStep: OnboardingStep get() = progress?.currentStep ?: OnboardingStep.First
    val isComplete: Boolean get() = progress?.isComplete == true

    /**
     * The active onboarding path, resolved from the current step and durable progress/profile state.
     * `null` while the user is in the common prefix (before choosing at [OnboardingStep.ChoosePath]).
     *
     * Generate-only steps imply Generate. Review uses [OnboardingProgress.selectedPath] and
     * persisted generate cycle-space — **not** empty [UserTrainingProfile.weekdays] (cleared on
     * Generate schedule flush).
     */
    val onboardingPath: OnboardingPath?
        get() =
            OnboardingPathResolver.resolve(
                step = currentStep,
                selectedPath = progress?.selectedPath,
                profile = profile,
            )

    companion object {
        val Initial = OnboardingWizardState(
            progress = null,
            profile = null,
            specialization = SpecializationPreference.Empty,
            lifts = emptyList(),
            selectedPreset = null,
        )
    }
}
