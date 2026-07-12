package com.stratum.domain.onboarding

import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import javax.inject.Inject

/**
 * Clears onboarding completion and returns the user to [OnboardingStep.ChoosePath] so they can
 * pick Generate, Build From Scratch, or Import again (e.g. after preview "Discard Program").
 */
class ReopenOnboardingWizardUseCase @Inject constructor(
    private val onboardingRepository: OnboardingRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(): Result<Unit> =
        runCatchingLogged(loggingPort, TAG, "Failed to reopen onboarding wizard") {
            onboardingRepository.reopenWizard()
        }

    private companion object {
        const val TAG = "ReopenOnboardingWizard"
    }
}
