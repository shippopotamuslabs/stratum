package com.stratum.application.lift

import com.stratum.application.diagnostics.OperationalDiagnostic
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import com.stratum.domain.lift.GymPreset

/**
 * Outcome of recording the selected gym preset (STR-243; STR-237 Route (i)).
 * On failure the operational-failure record rides the [PersistenceFailed]
 * branch as an [OperationalDiagnostic] (legacy tag + rendered message, real
 * throwable as cause); the presentation boundary drains it exactly once.
 * Coroutine cancellation is not a branch: it propagates.
 */
sealed interface SetSelectedPresetResult {
    data object Success : SetSelectedPresetResult

    /** The preset-choice write failed (repository / store I/O). */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : SetSelectedPresetResult
}

/**
 * Records (or clears, with `null`) the user's preset choice. Called when the user selects a
 * preset (alongside [ApplyGymPresetUseCase]) or when they manually deviate from a preset's
 * defaults and the chip should deselect. OS-5 Review renders the selected preset's name.
 */
class SetSelectedPresetUseCase @Inject constructor(
    private val repository: LiftRepository,
) {
    suspend operator fun invoke(preset: GymPreset?): SetSelectedPresetResult =
        try {
            repository.setSelectedPreset(preset)
            SetSelectedPresetResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            SetSelectedPresetResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to set selected preset: ${t.message}",
                        cause = t,
                    ),
                ),
            )
        }

    private companion object {
        const val TAG = "SetSelectedPreset"
    }
}
