package com.stratum.application.lift

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import javax.inject.Inject

/**
 * Flips a single lift's availability. Used by the manual on/off toggle on the Gym & Lifts
 * step. Clearing the preset (when the user toggles a lift against the preset's defaults) is
 * handled by [SetSelectedPresetUseCase]; this use case only owns the per-lift flag.
 */
class SetLiftEnabledUseCase @Inject constructor(
    private val repository: LiftRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(id: LiftId, enabled: Boolean): Result<Unit> =
        runCatchingLogged(loggingPort, TAG, "Failed to set lift enabled") {
            repository.setLiftEnabled(id, enabled)
        }

    private companion object {
        const val TAG = "SetLiftEnabled"
    }
}
