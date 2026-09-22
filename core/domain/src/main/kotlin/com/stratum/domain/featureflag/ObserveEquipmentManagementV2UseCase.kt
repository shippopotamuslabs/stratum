package com.stratum.domain.featureflag

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Observes the equipment-management rework launch flag
 * ([AppFeatureFlag.EQUIPMENT_MANAGEMENT_V2]).
 *
 * ViewModels gate flag-driven UI through this use case rather than touching
 * [FeatureFlagRepository] directly (ARCHITECTURE.md §4.4).
 */
class ObserveEquipmentManagementV2UseCase @Inject constructor(
    private val repository: FeatureFlagRepository,
) {
    operator fun invoke(): Flow<Boolean> =
        repository.observe(AppFeatureFlag.EQUIPMENT_MANAGEMENT_V2)
}
