package com.stratum.application.onboarding

import com.stratum.domain.logging.LoggingPort
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import com.stratum.domain.onboarding.OnboardingStep
import com.stratum.domain.program.Program

/**
 * Outcome of reopening the onboarding wizard (STR-243).
 *
 * The originating throwable goes once to the diagnostics channel ([LoggingPort.error])
 * at the use-case boundary; it never crosses as a payload. Coroutine cancellation is
 * not a branch: it propagates as cancellation.
 */
sealed interface ReopenOnboardingWizardResult {
    data object Success : ReopenOnboardingWizardResult

    /** The wizard-state write failed (repository / store I/O). */
    data object PersistenceFailed : ReopenOnboardingWizardResult
}

/**
 * Clears onboarding completion and returns the user to [OnboardingStep.ChoosePath] so they can
 * pick Generate, Build From Scratch, or Import again (e.g. after preview "Discard Program").
 */
class ReopenOnboardingWizardUseCase @Inject constructor(
    private val onboardingRepository: OnboardingRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(): ReopenOnboardingWizardResult =
        try {
            onboardingRepository.reopenWizard()
            ReopenOnboardingWizardResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            loggingPort.error(TAG, "Failed to reopen onboarding wizard: ${t.message}", t)
            ReopenOnboardingWizardResult.PersistenceFailed
        }

    private companion object {
        const val TAG = "ReopenOnboardingWizard"
    }
}
