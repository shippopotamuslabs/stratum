package com.stratum.domain.onboarding

import javax.inject.Inject

/** Clears onboarding completion and returns the user to the Generate step for edits + regeneration. */
class ReopenOnboardingWizardUseCase @Inject constructor(
    private val onboardingRepository: OnboardingRepository,
) {
    suspend operator fun invoke() {
        onboardingRepository.reopenWizard()
    }
}
