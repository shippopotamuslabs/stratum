package com.stratum.application.onboarding

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import com.stratum.domain.onboarding.SpecializationPreference

/**
 * Reads the specialization preference stream. The single entry point ViewModels use to
 * observe specialization — they never call [SpecializationPreferenceRepository] directly
 * (ARCHITECTURE.md §4.3).
 */
class ObserveSpecializationUseCase @Inject constructor(
    private val repository: SpecializationPreferenceRepository,
) {
    operator fun invoke(): Flow<SpecializationPreference> = repository.observe()
}
