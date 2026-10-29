package com.stratum.application.onboarding

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import com.stratum.domain.onboarding.OnboardingState

/**
 * Composes the persisted onboarding streams ([OnboardingRepository] + [UserTrainingProfileRepository])
 * into a single [OnboardingState] flow. The owner of multi-source composition — ViewModels
 * consume this, never the repositories directly (ARCHITECTURE.md §4.3).
 *
 * `UserTrainingProfileRepository` is permitted to emit `null` (no draft saved yet); the
 * emitted [OnboardingState] preserves that so callers can distinguish "empty" from "absent".
 */
class GetOnboardingStateUseCase @Inject constructor(
    private val onboardingRepository: OnboardingRepository,
    private val profileRepository: UserTrainingProfileRepository,
) {
    operator fun invoke(): Flow<OnboardingState> =
        combine(
            onboardingRepository.observeProgress(),
            profileRepository.observeProfile(),
        ) { progress, profile ->
            OnboardingState(progress = progress, profile = profile)
        }
}
