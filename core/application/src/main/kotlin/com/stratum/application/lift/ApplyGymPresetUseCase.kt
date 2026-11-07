package com.stratum.application.lift

import com.stratum.application.diagnostics.OperationalDiagnostic
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import com.stratum.domain.lift.GymPreset

/**
 * Outcome of applying a gym preset (STR-243; STR-237 Route (i)). On failure
 * the operational-failure record rides the [PersistenceFailed] branch as an
 * [OperationalDiagnostic] (legacy tag + rendered message, real throwable as
 * cause); the presentation boundary drains it exactly once. Coroutine
 * cancellation is not a branch: it propagates.
 */
sealed interface ApplyGymPresetResult {
    data object Success : ApplyGymPresetResult

    /** The preset writes failed (repository / store I/O). */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : ApplyGymPresetResult
}

/**
 * Enables every lift tagged with [preset] and records the preset choice. The single entry
 * point ViewModels use to apply a preset — they never call [LiftRepository] directly
 * (ARCHITECTURE.md §4.4).
 */
class ApplyGymPresetUseCase @Inject constructor(
    private val repository: LiftRepository,
) {
    suspend operator fun invoke(preset: GymPreset): ApplyGymPresetResult =
        try {
            repository.applyPreset(preset)
            repository.setSelectedPreset(preset)
            ApplyGymPresetResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            ApplyGymPresetResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to apply gym preset: ${t.message}",
                        cause = t,
                    ),
                ),
            )
        }

    private companion object {
        const val TAG = "ApplyGymPreset"
    }
}
