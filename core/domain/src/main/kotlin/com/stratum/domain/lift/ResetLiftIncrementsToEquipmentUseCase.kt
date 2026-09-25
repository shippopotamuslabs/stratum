package com.stratum.domain.lift

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import javax.inject.Inject

/**
 * Clears a lift's band and micro overrides in a single atomic repository write so the
 * lift fully re-inherits equipment defaults. Replaces the old sequential
 * clear-bands-then-clear-micro pair, which could leave a half-reset lift (inherited
 * bands, stale knob override) when the second write failed.
 */
class ResetLiftIncrementsToEquipmentUseCase @Inject constructor(
    private val repository: LiftRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(id: LiftId): Result<Unit> =
        runCatchingLogged(loggingPort, TAG, "Failed to reset lift increments") {
            repository.resetIncrementTiersToEquipment(id)
        }

    private companion object {
        const val TAG = "ResetLiftIncrements"
    }
}
