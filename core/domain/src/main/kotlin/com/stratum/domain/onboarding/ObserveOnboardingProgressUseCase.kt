package com.stratum.domain.onboarding

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Exposes the onboarding progress stream for app-level routing (onboarding vs home).
 */
class ObserveOnboardingProgressUseCase @Inject constructor(
    private val repository: OnboardingRepository,
) {
    operator fun invoke(): Flow<OnboardingProgress?> = repository.observeProgress()
}
