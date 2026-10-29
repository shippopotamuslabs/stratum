package com.stratum.application.lift

import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.WeightDisplayPreferences

/**
 * Observes persisted weight display preferences. ViewModels use this instead of
 * [WeightDisplayPreferencesRepository] directly (ARCHITECTURE.md §4.4).
 */
class ObserveWeightDisplayPreferencesUseCase @Inject constructor(
    private val repository: WeightDisplayPreferencesRepository,
) {
    operator fun invoke(): Flow<WeightDisplayPreferences> = repository.observe()
}

/** Persists the preferred weight display unit (lb / kg). */
class SetWeightDisplayUnitUseCase @Inject constructor(
    private val repository: WeightDisplayPreferencesRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(unit: Weight.WeightUnit): Result<Unit> =
        runCatchingLogged(loggingPort, TAG, "Failed to set weight display unit") {
            repository.setDisplayUnit(unit)
        }

    private companion object {
        const val TAG = "SetWeightDisplayUnit"
    }
}
