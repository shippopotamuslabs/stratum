package com.stratum.application.lift

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.logging.LoggingPort
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Outcome of setting a lift's enabled flag (STR-243). The originating
 * throwable of a failed write goes once to the diagnostics channel
 * ([LoggingPort.error]) at the use-case boundary; it never crosses as a
 * payload. Coroutine cancellation is not a branch: it propagates.
 */
sealed interface SetLiftEnabledResult {
    data object Success : SetLiftEnabledResult

    /** The enabled-flag write failed (repository / store I/O). */
    data object PersistenceFailed : SetLiftEnabledResult
}

/**
 * Flips a single lift's availability. Used by the manual on/off toggle on the Gym & Lifts
 * step. Clearing the preset (when the user toggles a lift against the preset's defaults) is
 * handled by [SetSelectedPresetUseCase]; this use case only owns the per-lift flag.
 */
class SetLiftEnabledUseCase @Inject constructor(
    private val repository: LiftRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(id: LiftId, enabled: Boolean): SetLiftEnabledResult =
        try {
            repository.setLiftEnabled(id, enabled)
            SetLiftEnabledResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            loggingPort.error(TAG, "Failed to set lift enabled: ${t.message}", t)
            SetLiftEnabledResult.PersistenceFailed
        }

    private companion object {
        const val TAG = "SetLiftEnabled"
    }
}
