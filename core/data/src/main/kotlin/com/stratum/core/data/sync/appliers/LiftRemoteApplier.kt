package com.stratum.core.data.sync.appliers

import com.stratum.core.data.db.LiftDao
import com.stratum.core.data.db.LiftEntity
import com.stratum.core.data.sync.FirestoreSyncMetadata
import com.stratum.core.data.sync.SyncFlags
import com.stratum.core.data.sync.codecs.SyncEntityDecoders
import com.stratum.core.data.sync.codecs.SyncEntityEncoders
import com.stratum.domain.lift.identity.LiftId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Catalog lifts upload user-mutable overlays only; custom lifts upload full documents.
 * Remote apply merges overlays onto seeded catalog rows (CLEAN) or full-decodes customs.
 *
 * Remote shapes match encode only: overlay (`catalog_overlay=true`) or custom full (`is_custom=true`).
 */
@Singleton
class LiftRemoteApplier @Inject constructor(
    private val liftDao: LiftDao,
    private val encoders: SyncEntityEncoders,
    private val decoders: SyncEntityDecoders,
) {
    fun encode(entity: LiftEntity): Map<String, Any?> =
        if (entity.isCustom) {
            encoders.lift(entity)
        } else {
            mapOf(
                "is_custom" to false,
                "catalog_overlay" to true,
                "enabled" to entity.enabled,
                "default_rest_seconds" to entity.defaultRestSeconds,
                "equipment_id" to entity.equipmentId?.value,
            )
        }

    suspend fun upsertFromRemote(id: String, data: Map<String, Any?>): Boolean {
        val shape = classifyRemote(data) ?: return false
        val local = classifyLocal(id)
        return when (shape) {
            RemoteLiftShape.Overlay ->
                when (local) {
                    is LocalLiftState.CatalogSeed -> applyOverlay(local.entity, data)
                    LocalLiftState.Missing -> false // seed not ready — retry after catalog seed
                    is LocalLiftState.Custom -> false // overlay does not apply to custom rows
                }
            RemoteLiftShape.CustomFull ->
                decodeFullAndUpsert(id, data)
        }
    }

    private sealed class RemoteLiftShape {
        /** User-mutable fields only (`catalog_overlay=true`). */
        data object Overlay : RemoteLiftShape()

        /** Full custom lift document (`is_custom=true`). */
        data object CustomFull : RemoteLiftShape()
    }

    private sealed class LocalLiftState {
        data object Missing : LocalLiftState()
        data class CatalogSeed(val entity: LiftEntity) : LocalLiftState()
        data class Custom(val entity: LiftEntity) : LocalLiftState()
    }

    private fun classifyRemote(data: Map<String, Any?>): RemoteLiftShape? {
        if (data["catalog_overlay"] as? Boolean == true) return RemoteLiftShape.Overlay
        if (data["is_custom"] as? Boolean == true) return RemoteLiftShape.CustomFull
        return null
    }

    private suspend fun classifyLocal(id: String): LocalLiftState {
        val existing = liftDao.getById(LiftId(id)) ?: return LocalLiftState.Missing
        return if (existing.isCustom) {
            LocalLiftState.Custom(existing)
        } else {
            LocalLiftState.CatalogSeed(existing)
        }
    }

    private suspend fun applyOverlay(existing: LiftEntity, data: Map<String, Any?>): Boolean {
        liftDao.upsert(overlayFrom(existing, data))
        return true
    }

    private suspend fun decodeFullAndUpsert(id: String, data: Map<String, Any?>): Boolean {
        val full = decoders.lift(id, data) ?: return false
        liftDao.upsert(full)
        return true
    }

    private fun overlayFrom(existing: LiftEntity, data: Map<String, Any?>): LiftEntity {
        val enabled = data["enabled"] as? Boolean ?: existing.enabled
        val rest =
            (data["default_rest_seconds"] as? Number)?.toInt()
                ?: existing.defaultRestSeconds
        val equipmentId =
            if (data.containsKey("equipment_id")) {
                (data["equipment_id"] as? String)?.let {
                    runCatching { com.stratum.domain.lift.identity.EquipmentId(it) }.getOrNull()
                }
            } else {
                existing.equipmentId
            }
        return existing.copy(
            enabled = enabled,
            defaultRestSeconds = rest,
            equipmentId = equipmentId,
            updatedAtEpochMillis =
                (data[FirestoreSyncMetadata.UPDATED_AT] as? Number)?.toLong()
                    ?: existing.updatedAtEpochMillis,
            pendingSync = SyncFlags.CLEAN,
        )
    }
}
