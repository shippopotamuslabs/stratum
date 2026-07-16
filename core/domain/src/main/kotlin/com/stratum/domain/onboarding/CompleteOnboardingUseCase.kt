package com.stratum.domain.onboarding

import javax.inject.Inject

/**
 * Marks onboarding complete after the user finishes the final step. Wraps the profile
 * persistence + completion marking so the ViewModel composes one operation rather than
 * reaching into repositories (ARCHITECTURE.md §4.3).
 */
open class CompleteOnboardingUseCase @Inject constructor(
    private val onboardingRepository: OnboardingRepository,
    private val profileRepository: UserTrainingProfileRepository,
) {
    open suspend operator fun invoke(finalProfile: UserTrainingProfile) {
        profileRepository.saveProfile(finalProfile)
        onboardingRepository.markComplete()
    }
}
