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
