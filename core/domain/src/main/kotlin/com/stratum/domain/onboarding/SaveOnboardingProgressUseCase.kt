package com.stratum.domain.onboarding

import javax.inject.Inject

/**
 * Persists the user's current onboarding step so the wizard can resume after a
 * kill or app restart.
 */
class SaveOnboardingProgressUseCase @Inject constructor(
    private val repository: OnboardingRepository,
) {
    suspend operator fun invoke(step: OnboardingStep) {
        repository.saveStep(step)
    }
}
