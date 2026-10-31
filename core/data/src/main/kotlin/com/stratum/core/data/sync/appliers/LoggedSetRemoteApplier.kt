package com.stratum.core.data.sync.appliers

import com.stratum.core.infrastructure.db.TrainingLoggedSetDao
import com.stratum.core.data.sync.codecs.SyncEntityDecoders
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies remote logged sets.
 *
 * **Invariant side-effects of applying a remote winner mark PENDING so they upload and heal
 * cloud.** When the remote set is a personal record, demotes any existing local PR for that
 * (lift, equipment) pair with [com.stratum.core.infrastructure.sync.SyncFlags.PENDING_UPSERT] so the unique
 * PR index cannot permanently stall pull and dual-PR cloud state heals. Same rule as program
 * demotion in [ProgramRemoteApplier].
 */
@Singleton
class LoggedSetRemoteApplier @Inject constructor(
    private val loggedSetDao: TrainingLoggedSetDao,
    private val decoders: SyncEntityDecoders,
) {
    suspend fun upsertFromRemote(id: String, data: Map<String, Any?>): Boolean {
        val entity = decoders.loggedSet(id, data) ?: return false
        val liftId = entity.liftId
        if (entity.isPersonalRecord == true && liftId != null) {
            loggedSetDao.demotePersonalRecordsForLift(
                liftId = liftId,
                equipmentId = entity.equipmentId,
                exceptId = entity.id,
                minUpdatedAtEpochMillis = maxOf(entity.updatedAtEpochMillis, System.currentTimeMillis()),
            )
        }
        loggedSetDao.upsert(entity)
        return true
    }
}
