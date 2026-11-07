package com.stratum.application.onboarding

import com.stratum.application.diagnostics.OperationalDiagnostic
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import com.stratum.domain.onboarding.OnboardingStep
import com.stratum.domain.program.Program

/**
 * Outcome of reopening the onboarding wizard (STR-243; STR-237 Route (i)).
 *
 * On failure the operational-failure record rides the [PersistenceFailed]
 * branch as an [OperationalDiagnostic] (legacy tag + rendered message, real
 * throwable as cause); the presentation boundary drains it exactly once.
 * Coroutine cancellation is not a branch: it propagates as cancellation.
 */
sealed interface ReopenOnboardingWizardResult {
    data object Success : ReopenOnboardingWizardResult

    /** The wizard-state write failed (repository / store I/O). */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : ReopenOnboardingWizardResult
}

/**
 * Clears onboarding completion and returns the user to [OnboardingStep.ChoosePath] so they can
 * pick Generate, Build From Scratch, or Import again (e.g. after preview "Discard Program").
 */
class ReopenOnboardingWizardUseCase @Inject constructor(
    private val onboardingRepository: OnboardingRepository,
) {
    suspend operator fun invoke(): ReopenOnboardingWizardResult =
        try {
            onboardingRepository.reopenWizard()
            ReopenOnboardingWizardResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            ReopenOnboardingWizardResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to reopen onboarding wizard: ${t.message}",
                        cause = t,
                    ),
                ),
            )
        }

    private companion object {
        const val TAG = "ReopenOnboardingWizard"
    }
}
