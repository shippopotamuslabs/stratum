package com.stratum.core.infrastructure.sync.appliers

import androidx.room.withTransaction
import com.stratum.core.infrastructure.db.LiftDao
import com.stratum.core.infrastructure.db.LiftEntity
import com.stratum.core.infrastructure.db.StratumDatabase
import com.stratum.core.infrastructure.repository.sweepLiftTemplateReferences
import com.stratum.core.infrastructure.sync.FirestoreSyncMetadata
import com.stratum.core.infrastructure.sync.SyncFlags
import com.stratum.core.infrastructure.sync.codecs.SyncEntityDecoders
import com.stratum.core.infrastructure.sync.codecs.SyncEntityEncoders
import com.stratum.core.infrastructure.sync.codecs.microWireList
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
    private val database: StratumDatabase,
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
                "is_deleted" to entity.isDeleted,
                "default_rest_seconds" to entity.defaultRestSeconds,
                "equipment_id" to entity.equipmentId?.value,
                "pinned_note" to entity.pinnedNote.orEmpty(),
                "micro_options" to microWireList(entity.microOptionsJson),
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

    /**
     * Nullable overlay-field merge: a present key applies (even when its value decodes
     * to null, which clears), an absent key preserves the local value.
     */
    private fun <T> overlayValue(
        data: Map<String, Any?>,
        key: String,
        existing: T,
        decode: (Any?) -> T,
    ): T = if (data.containsKey(key)) decode(data[key]) else existing

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
        applyRemoteLift(overlayFrom(existing, data))
        return true
    }

    private suspend fun decodeFullAndUpsert(id: String, data: Map<String, Any?>): Boolean {
        val decoded = decoders.lift(id, data) ?: return false
        // STR-131: payloads from pre-tombstone builds carry no `is_deleted`
        // key; their upsert must not un-delete a locally tombstoned row.
        // (An explicit key always wins — LWW already gated stale docs.)
        val full =
            if (data.containsKey("is_deleted")) {
                decoded
            } else {
                val existing = liftDao.getById(LiftId(id))
                decoded.copy(isDeleted = existing?.isDeleted ?: false)
            }
        applyRemoteLift(full)
        return true
    }

    /**
     * Applies a remote lift row; a tombstone additionally runs the STR-131
     * reference sweep in the same transaction (Scope 5): this device's
     * template slots and stash notes for the lift are dropped locally exactly
     * as the origin device's delete dropped them. Idempotent — a re-applied
     * tombstone finds nothing left to sweep. Logged sets are untouched.
     */
    private suspend fun applyRemoteLift(entity: LiftEntity) {
        if (!entity.isDeleted) {
            liftDao.upsert(entity)
            return
        }
        database.withTransaction {
            liftDao.upsert(entity)
            sweepLiftTemplateReferences(database, entity.id)
        }
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
        val pinnedNote =
            overlayValue(data, "pinned_note", existing.pinnedNote) {
                com.stratum.domain.lift.sanitizeLiftNoteBody(it as? String)
            }
        // Per-lift micro overrides were removed (STR-82/84): the lift micro column is
        // never read, so a stale device's `micro_options` must not repopulate it as a
        // zombie override. The key is deliberately not overlaid (mirrors the tier
        // applier's lift_id skip); local uploads always carry null, cleaning the cloud.
        return existing.copy(
            enabled = enabled,
            isDeleted =
                if (data.containsKey("is_deleted")) {
                    data["is_deleted"] as? Boolean ?: existing.isDeleted
                } else {
                    existing.isDeleted
                },
            defaultRestSeconds = rest,
            equipmentId = equipmentId,
            pinnedNote = pinnedNote,
            updatedAtEpochMillis =
                (data[FirestoreSyncMetadata.UPDATED_AT] as? Number)?.toLong()
                    ?: existing.updatedAtEpochMillis,
            pendingSync = SyncFlags.CLEAN,
        )
    }
}
