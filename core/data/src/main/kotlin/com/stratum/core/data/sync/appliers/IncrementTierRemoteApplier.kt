package com.stratum.core.data.sync.appliers

import com.stratum.core.data.db.EquipmentDao
import com.stratum.core.data.db.IncrementTierDao
import com.stratum.core.data.db.LiftDao
import com.stratum.core.data.sync.codecs.SyncEntityDecoders

/**
 * Applies remote increment-tier documents. Missing parent lift/equipment is treated as a
 * successful skip so one orphan cannot freeze the collection cursor. Parents are pulled
 * first; a failed parent collection skips this collection entirely so retryable gaps
 * are not skipped forever.
 */
class IncrementTierRemoteApplier(
    private val incrementTierDao: IncrementTierDao,
    private val liftDao: LiftDao,
    private val equipmentDao: EquipmentDao,
    private val decoders: SyncEntityDecoders,
) {
    suspend fun upsertFromRemote(id: String, data: Map<String, Any?>): Boolean {
        val entity = decoders.incrementTier(id, data) ?: return false
        val liftId = entity.liftId
        if (liftId != null && liftDao.getById(liftId) == null) {
            return true
        }
        val equipmentId = entity.equipmentId
        if (equipmentId != null && equipmentDao.getById(equipmentId) == null) {
            return true
        }
        incrementTierDao.upsertAll(listOf(entity))
        return true
    }
}
