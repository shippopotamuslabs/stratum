package com.stratum.application.lift

import kotlinx.coroutines.flow.Flow
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.WeightDisplayPreferences

/**
 * Observes and updates persisted [WeightDisplayPreferences]. Implementations use DataStore;
 * callers must not block the main thread on writes.
 */
interface WeightDisplayPreferencesRepository {
    fun observe(): Flow<WeightDisplayPreferences>

    suspend fun setDisplayUnit(unit: Weight.WeightUnit)
}
