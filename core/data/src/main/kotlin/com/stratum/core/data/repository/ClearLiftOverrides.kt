package com.stratum.core.data.repository

import com.stratum.core.data.db.IncrementTierDao
import com.stratum.core.data.db.LiftDao
import com.stratum.domain.lift.identity.LiftId

/**
 * Clears a lift's band rows and its lift-scoped micro override together so a
 * reassigned lift always re-inherits the new equipment's lattice and knob.
 * Callers run this inside their own transaction with the same [now].
 */
internal suspend fun clearLiftIncrementOverrides(
    incrementTierDao: IncrementTierDao,
    liftDao: LiftDao,
    liftId: LiftId,
    now: Long,
) {
    incrementTierDao.deleteForLift(liftId)
    liftDao.setMicroOptions(
        id = liftId,
        microOptionsJson = null,
        updatedAtEpochMillis = now,
    )
}
