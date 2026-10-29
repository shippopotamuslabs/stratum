package com.stratum.domain.onboarding


/**
 * Read model for the onboarding wizard's persisted state — everything the UI needs to
 * render the current step and seed the Profile/Schedule drafts.
 *
 * Composed by [GetOnboardingStateUseCase] from the two persistence streams so the
 * ViewModel never combines repositories directly (ARCHITECTURE.md §4.3). Transient
 * (unsaved) draft edits live in the ViewModel, not here.
 */
data class OnboardingState(
    val progress: OnboardingProgress?,
    val profile: UserTrainingProfile?,
) {
    val currentStep: OnboardingStep get() = progress?.currentStep ?: OnboardingStep.First
    val isComplete: Boolean get() = progress?.isComplete == true

    companion object {
        val Initial = OnboardingState(progress = null, profile = null)
    }
}
