package com.stratum.core.testing.lift

import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.WeightDisplayPreferences
import com.stratum.domain.lift.WeightDisplayPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory [WeightDisplayPreferencesRepository] for ViewModel and use-case tests. */
class FakeWeightDisplayPreferencesRepository(
    initial: WeightDisplayPreferences = WeightDisplayPreferences(),
) : WeightDisplayPreferencesRepository {

    private val preferences = MutableStateFlow(initial)

    val current: WeightDisplayPreferences
        get() = preferences.value

    fun seed(value: WeightDisplayPreferences) {
        preferences.value = value
    }

    override fun observe(): Flow<WeightDisplayPreferences> = preferences.asStateFlow()

    override suspend fun setDisplayUnit(unit: Weight.WeightUnit) {
        preferences.value = preferences.value.copy(displayUnit = unit)
    }
}
