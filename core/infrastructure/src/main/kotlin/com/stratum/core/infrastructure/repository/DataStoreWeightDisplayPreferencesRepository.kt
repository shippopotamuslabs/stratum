package com.stratum.core.infrastructure.repository

import android.content.Context
import com.stratum.core.infrastructure.weightdisplay.toDomain
import com.stratum.core.infrastructure.weightdisplay.toRecord
import com.stratum.core.infrastructure.weightdisplay.weightDisplayPreferencesDataStore
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.WeightDisplayPreferences
import com.stratum.application.lift.WeightDisplayPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed [WeightDisplayPreferencesRepository]. Emits defaults (Lb) until the first
 * successful read; unknown stored names map back to Lb.
 */
@Singleton
class DataStoreWeightDisplayPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : WeightDisplayPreferencesRepository {

    override fun observe(): Flow<WeightDisplayPreferences> =
        context.weightDisplayPreferencesDataStore.data.map { record -> record.toDomain() }

    override suspend fun setDisplayUnit(unit: Weight.WeightUnit) {
        context.weightDisplayPreferencesDataStore.updateData { current ->
            current.toDomain().copy(displayUnit = unit).toRecord()
        }
    }
}
