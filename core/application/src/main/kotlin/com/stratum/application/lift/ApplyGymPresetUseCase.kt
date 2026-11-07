package com.stratum.application.lift

import com.stratum.domain.logging.LoggingPort
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import com.stratum.domain.lift.GymPreset

/**
 * Outcome of applying a gym preset (STR-243). The originating throwable of a
 * failed write goes once to the diagnostics channel ([LoggingPort.error]) at
 * the use-case boundary; it never crosses as a payload. Coroutine
 * cancellation is not a branch: it propagates.
 */
sealed interface ApplyGymPresetResult {
    data object Success : ApplyGymPresetResult

    /** The preset writes failed (repository / store I/O). */
    data object PersistenceFailed : ApplyGymPresetResult
}

/**
 * Enables every lift tagged with [preset] and records the preset choice. The single entry
 * point ViewModels use to apply a preset — they never call [LiftRepository] directly
 * (ARCHITECTURE.md §4.4).
 */
class ApplyGymPresetUseCase @Inject constructor(
    private val repository: LiftRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(preset: GymPreset): ApplyGymPresetResult =
        try {
            repository.applyPreset(preset)
            repository.setSelectedPreset(preset)
            ApplyGymPresetResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            loggingPort.error(TAG, "Failed to apply gym preset: ${t.message}", t)
            ApplyGymPresetResult.PersistenceFailed
        }

    private companion object {
        const val TAG = "ApplyGymPreset"
    }
}
