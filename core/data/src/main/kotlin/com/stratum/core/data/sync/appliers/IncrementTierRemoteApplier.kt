package com.stratum.core.data.sync.appliers

import com.stratum.core.data.db.EquipmentDao
import com.stratum.core.data.db.IncrementTierDao
import com.stratum.core.data.sync.codecs.SyncEntityDecoders

/**
 * Applies remote increment-tier documents. Missing parent lift/equipment is treated as a
 * successful skip so one orphan cannot freeze the collection cursor. Parents are pulled
 * first; a failed parent collection skips this collection entirely so retryable gaps
 * are not skipped forever.
 */
class IncrementTierRemoteApplier(
    private val incrementTierDao: IncrementTierDao,
    private val equipmentDao: EquipmentDao,
    private val decoders: SyncEntityDecoders,
) {
    suspend fun upsertFromRemote(id: String, data: Map<String, Any?>): Boolean {
        val entity = decoders.incrementTier(id, data) ?: return false
        // Per-lift overrides were removed (STR-82/STR-85): a stale device may still upload
        // lift-scoped docs. Skip them so they can never rehydrate as zombie overrides.
        if (entity.liftId != null) return true
        val equipmentId = entity.equipmentId
        if (equipmentId != null && equipmentDao.getById(equipmentId) == null) {
            return true
        }
        incrementTierDao.upsertAll(listOf(entity))
        return true
    }
}
