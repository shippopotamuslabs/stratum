package com.stratum.application.lift

import com.stratum.application.diagnostics.OperationalDiagnostic
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.WeightDisplayPreferences

/**
 * Outcome of a weight-display preferences write (STR-243; STR-237 Route (i)).
 *
 * The originating throwable rides the [PersistenceFailed] branch as an
 * [OperationalDiagnostic] (legacy tag + rendered message, real throwable as
 * cause); the presentation boundary drains it exactly once through its
 * module's DiagnosticDrain. Coroutine cancellation is not a branch: it
 * propagates as cancellation.
 */
sealed interface WeightDisplayPreferenceResult {
    data object Success : WeightDisplayPreferenceResult

    /** The preferences write failed (repository / store I/O). */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : WeightDisplayPreferenceResult
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
) {
    suspend operator fun invoke(unit: Weight.WeightUnit): WeightDisplayPreferenceResult =
        try {
            repository.setDisplayUnit(unit)
            WeightDisplayPreferenceResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            WeightDisplayPreferenceResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to set weight display unit: ${t.message}",
                        cause = t,
                    ),
                ),
            )
        }

    private companion object {
        const val TAG = "SetWeightDisplayUnit"
    }
}
