package com.stratum.application.lift

import com.stratum.domain.logging.LoggingPort
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.WeightDisplayPreferences

/**
 * Outcome of a weight-display preferences write (STR-243).
 *
 * The originating throwable goes once to the diagnostics channel ([LoggingPort.error])
 * at the use-case boundary; it never crosses as a payload. Coroutine cancellation is
 * not a branch: it propagates as cancellation.
 */
sealed interface WeightDisplayPreferenceResult {
    data object Success : WeightDisplayPreferenceResult

    /** The preferences write failed (repository / store I/O). */
    data object PersistenceFailed : WeightDisplayPreferenceResult
}

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
    suspend operator fun invoke(unit: Weight.WeightUnit): WeightDisplayPreferenceResult =
        try {
            repository.setDisplayUnit(unit)
            WeightDisplayPreferenceResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            loggingPort.error(TAG, "Failed to set weight display unit: ${t.message}", t)
            WeightDisplayPreferenceResult.PersistenceFailed
        }

    private companion object {
        const val TAG = "SetWeightDisplayUnit"
    }
}
