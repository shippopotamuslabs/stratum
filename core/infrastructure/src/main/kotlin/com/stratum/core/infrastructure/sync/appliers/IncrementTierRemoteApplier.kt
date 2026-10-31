package com.stratum.core.infrastructure.sync.appliers

import com.stratum.core.infrastructure.db.EquipmentDao
import com.stratum.core.infrastructure.db.IncrementTierDao
import com.stratum.core.infrastructure.sync.codecs.SyncEntityDecoders

/**
 * Applies remote increment-tier documents. Missing parent equipment is treated as a
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
        // Per-lift overrides were removed (STR-82/84): a stale device may still upload
        // lift-scoped docs. Skip them before decode so they can never rehydrate as
        // zombie overrides (the decoder now requires equipment_id).
        if (data["lift_id"] != null) return true
        val entity = decoders.incrementTier(id, data) ?: return false
        val equipmentId = entity.equipmentId
        if (equipmentId != null && equipmentDao.getById(equipmentId) == null) {
            return true
        }
        incrementTierDao.upsertAll(listOf(entity))
        return true
    }
}
