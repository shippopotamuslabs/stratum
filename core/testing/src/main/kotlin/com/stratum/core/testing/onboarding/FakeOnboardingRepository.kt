package com.stratum.core.testing.onboarding

import com.stratum.domain.onboarding.OnboardingProgress
import com.stratum.domain.onboarding.OnboardingRepository
import com.stratum.domain.onboarding.OnboardingStep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory fake for [OnboardingRepository]. Seeds to the Welcome step with a
 * fixed start timestamp so tests can assert on state without wall-clock drift.
 */
class FakeOnboardingRepository(
    initial: OnboardingProgress = OnboardingProgress(
        currentStep = OnboardingStep.First,
        startedAt = STARTED_AT,
        completedAt = null,
    ),
) : OnboardingRepository {
    private val progress = MutableStateFlow(initial)

    override fun observeProgress(): Flow<OnboardingProgress?> = progress

    override suspend fun saveStep(step: OnboardingStep) {
        progress.value = progress.value.copy(currentStep = step, completedAt = null)
    }

    override suspend fun markComplete() {
        progress.value = progress.value.copy(completedAt = COMPLETED_AT)
    }

    override suspend fun reopenWizard() {
        progress.value =
            progress.value.copy(
                currentStep = OnboardingStep.Generate,
                completedAt = null,
            )
    }

    /** Test hook: the current progress value. */
    val current: OnboardingProgress get() = progress.value

    /** Test hook: seed a specific progress state. */
    fun seed(progress: OnboardingProgress) {
        this.progress.value = progress
    }

    companion object {
        const val STARTED_AT: Long = 1_700_000_000_000L
        const val COMPLETED_AT: Long = 1_700_000_123_456L
    }
}
