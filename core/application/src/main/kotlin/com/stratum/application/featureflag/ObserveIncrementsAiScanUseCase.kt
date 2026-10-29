package com.stratum.application.featureflag

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import com.stratum.domain.featureflag.AppFeatureFlag

/**
 * Observes the increments AI scan launch flag
 * ([AppFeatureFlag.INCREMENTS_AI_SCAN]).
 *
 * ViewModels gate flag-driven UI through this use case rather than touching
 * [FeatureFlagRepository] directly (ARCHITECTURE.md §4.4).
 */
class ObserveIncrementsAiScanUseCase @Inject constructor(
    private val repository: FeatureFlagRepository,
) {
    operator fun invoke(): Flow<Boolean> =
        repository.observe(AppFeatureFlag.INCREMENTS_AI_SCAN)
}
