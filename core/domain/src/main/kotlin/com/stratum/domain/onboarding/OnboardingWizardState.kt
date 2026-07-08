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
     * The active onboarding path, resolved from the current step and profile state. `null` while
     * the user is in the common prefix (before choosing at [OnboardingStep.ChoosePath]).
     *
     * The Generate-only steps imply Generate; Review is disambiguated by whether the user entered a
     * schedule (non-empty `weekdays`). The Generate path enforces non-empty weekdays via Schedule
     * validation, while the Import path skips Schedule entirely — so empty weekdays at Review is a
     * reliable Import signal.
     */
    val onboardingPath: OnboardingPath? get() = when (currentStep) {
        OnboardingStep.Welcome,
        OnboardingStep.Profile,
        OnboardingStep.GymAndLifts,
        OnboardingStep.ChoosePath,
        -> null
        OnboardingStep.Schedule,
        OnboardingStep.Specialization,
        OnboardingStep.MinimumVolume,
        -> OnboardingPath.Generate
        OnboardingStep.Review ->
            if (profile?.weekdays.isNullOrEmpty()) OnboardingPath.Import else OnboardingPath.Generate
    }

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
