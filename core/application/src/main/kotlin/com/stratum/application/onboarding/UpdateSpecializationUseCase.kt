package com.stratum.application.onboarding

import javax.inject.Inject
import com.stratum.domain.onboarding.SpecializationPreference

/**
 * Persists the specialization preference. The single entry point ViewModels use to write
 * specialization — they never call [SpecializationPreferenceRepository] directly
 * (ARCHITECTURE.md §4.3). Used both when advancing from the step and when navigating back
 * (so edits survive).
 */
class UpdateSpecializationUseCase @Inject constructor(
    private val repository: SpecializationPreferenceRepository,
) {
    suspend operator fun invoke(preference: SpecializationPreference) {
        repository.save(preference)
    }
}
