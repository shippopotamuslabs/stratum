package com.stratum.domain.onboarding

import javax.inject.Inject

/**
 * Persists the Profile + Schedule draft. The single entry point ViewModels use to write
 * the profile — they never call [UserTrainingProfileRepository] directly (ARCHITECTURE.md
 * §4.3). Used both when advancing from a step and when navigating back (so edits survive).
 */
class UpdateUserTrainingProfileUseCase @Inject constructor(
    private val repository: UserTrainingProfileRepository,
) {
    suspend operator fun invoke(profile: UserTrainingProfile) {
        repository.saveProfile(profile)
    }
}
