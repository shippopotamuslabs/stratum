package com.stratum.application.onboarding

import javax.inject.Inject
import com.stratum.domain.onboarding.OnboardingPath
import com.stratum.domain.onboarding.OnboardingProgress
import com.stratum.domain.onboarding.OnboardingStep

/**
 * Persists the user's current onboarding step so the wizard can resume after a
 * kill or app restart.
 */
class SaveOnboardingProgressUseCase @Inject constructor(
    private val repository: OnboardingRepository,
) {
    /**
     * @param selectedPath when non-null, persists as the durable path; when null, the repository
     * keeps the existing [OnboardingProgress.selectedPath].
     */
    suspend operator fun invoke(step: OnboardingStep, selectedPath: OnboardingPath? = null) {
        repository.saveStep(step, selectedPath = selectedPath)
    }
}
