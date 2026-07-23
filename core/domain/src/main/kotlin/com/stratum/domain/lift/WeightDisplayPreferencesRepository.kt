package com.stratum.domain.lift

import kotlinx.coroutines.flow.Flow

/**
 * Observes and updates persisted [WeightDisplayPreferences]. Implementations use DataStore;
 * callers must not block the main thread on writes.
 */
interface WeightDisplayPreferencesRepository {
    fun observe(): Flow<WeightDisplayPreferences>

    suspend fun setDisplayUnit(unit: Weight.WeightUnit)
}
