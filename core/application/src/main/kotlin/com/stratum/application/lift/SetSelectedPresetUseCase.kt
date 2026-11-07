package com.stratum.application.lift

import com.stratum.domain.logging.LoggingPort
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import com.stratum.domain.lift.GymPreset

/**
 * Outcome of recording the selected gym preset (STR-243). The originating
 * throwable of a failed write goes once to the diagnostics channel
 * ([LoggingPort.error]) at the use-case boundary; it never crosses as a
 * payload. Coroutine cancellation is not a branch: it propagates.
 */
sealed interface SetSelectedPresetResult {
    data object Success : SetSelectedPresetResult

    /** The preset-choice write failed (repository / store I/O). */
    data object PersistenceFailed : SetSelectedPresetResult
}

/**
 * Records (or clears, with `null`) the user's preset choice. Called when the user selects a
 * preset (alongside [ApplyGymPresetUseCase]) or when they manually deviate from a preset's
 * defaults and the chip should deselect. OS-5 Review renders the selected preset's name.
 */
class SetSelectedPresetUseCase @Inject constructor(
    private val repository: LiftRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(preset: GymPreset?): SetSelectedPresetResult =
        try {
            repository.setSelectedPreset(preset)
            SetSelectedPresetResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            loggingPort.error(TAG, "Failed to set selected preset: ${t.message}", t)
            SetSelectedPresetResult.PersistenceFailed
        }

    private companion object {
        const val TAG = "SetSelectedPreset"
    }
}
