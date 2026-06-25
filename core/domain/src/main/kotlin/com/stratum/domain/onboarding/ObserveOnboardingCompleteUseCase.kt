package com.stratum.domain.onboarding

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Exposes whether onboarding is complete for app-level routing (onboarding vs home).
 */
class ObserveOnboardingCompleteUseCase @Inject constructor(
    private val repository: OnboardingRepository,
) {
    operator fun invoke(): Flow<Boolean> =
        repository.observeProgress().map { progress -> progress?.isComplete == true }
}
