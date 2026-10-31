package com.stratum.core.data.repository

import androidx.room.withTransaction
import com.stratum.core.infrastructure.db.EquipmentDao
import com.stratum.core.infrastructure.db.EquipmentEntity
import com.stratum.core.infrastructure.db.IncrementTierDao
import com.stratum.core.infrastructure.db.IncrementTierEntity
import com.stratum.core.infrastructure.db.LiftDao
import com.stratum.core.infrastructure.db.StratumDatabase
import com.stratum.core.infrastructure.mapper.toDomain
import com.stratum.core.infrastructure.mapper.toMicroJsonOrNull
import com.stratum.core.infrastructure.seed.GymEquipmentToEquipmentId
import com.stratum.core.data.sync.SyncCollection
import com.stratum.core.infrastructure.sync.SyncFlags
import com.stratum.core.data.sync.SyncTouch
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.EquipmentNameMatching
import com.stratum.application.lift.EquipmentRepository
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.alignedWithEquipment
import com.stratum.domain.lift.uniqueifyForkEquipmentName
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.defaultSingleTiers
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.IncrementTierId
import com.stratum.domain.lift.identity.LiftId
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class RoomEquipmentRepository @Inject constructor(
    private val database: StratumDatabase,
    private val equipmentDao: EquipmentDao,
    private val incrementTierDao: IncrementTierDao,
    private val liftDao: LiftDao,
    private val syncTouch: SyncTouch,
) : EquipmentRepository {

    override fun observeEquipment(): Flow<List<Equipment>> =
        combine(
            equipmentDao.observeAll(),
            incrementTierDao.observeAllEquipmentTiers(),
        ) { rows, tiers ->
            val byEquipment = tiers.groupBy { it.equipmentId }
            rows
                .filterNot { it.isDeleted }
                .map { row -> row.toDomain(byEquipment[row.id].orEmpty()) }
        }

    override suspend fun getEquipment(id: EquipmentId): Equipment? {
        val row = equipmentDao.getById(id) ?: return null
        return row.toDomain(incrementTierDao.getForEquipment(id))
    }

    override suspend fun addCustomEquipment(
        name: String,
        tiers: List<IncrementTier>,
        micro: List<Weight>,
    ): EquipmentId {
        val id = EquipmentId(UUID.randomUUID().toString())
        val resolvedTiers = tiers.ifEmpty { defaultSingleTiers() }
        val now = System.currentTimeMillis()
        syncTouch.withWriteLock {
            database.withTransaction {
                insertCustomEquipmentUnlocked(id, name, resolvedTiers, micro, now)
            }
            syncTouch.markDirty()
        }
        return id
    }

    override suspend fun createCustomEquipmentAndAssignLifts(
        name: String,
        tiers: List<IncrementTier>,
        liftIds: List<LiftId>,
    ): EquipmentId {
        val id = EquipmentId(UUID.randomUUID().toString())
        val resolvedTiers = tiers.ifEmpty { defaultSingleTiers() }
        val now = System.currentTimeMillis()
        val distinctLiftIds = liftIds.distinct()
        syncTouch.withWriteLock {
            database.withTransaction {
                insertCustomEquipmentUnlocked(id, name, resolvedTiers, micro = emptyList(), nowMillis = now)
                for (liftId in distinctLiftIds) {
                    liftDao.setEquipmentId(
                        id = liftId,
                        equipmentId = id,
                        updatedAtEpochMillis = now,
                    )
                    // Assigned lifts inherit the new machine's adder stops; a stale
                    // lift-scoped micro override must not survive the move (STR-84).
                    liftDao.clearMicroOptions(id = liftId, updatedAtEpochMillis = now)
                }
            }
            syncTouch.markDirty()
        }
        return id
    }

    override suspend fun renameEquipment(id: EquipmentId, name: String) {
        syncTouch.withWriteLock {
            equipmentDao.rename(id, name, updatedAtEpochMillis = System.currentTimeMillis())
            syncTouch.markDirty()
        }
    }

    override suspend fun setIncrementTiers(
        id: EquipmentId,
        tiers: List<IncrementTier>,
        micro: List<Weight>?,
    ) {
        val now = System.currentTimeMillis()
        syncTouch.withWriteLock {
            if (equipmentDao.getById(id)?.isDeleted == true) return@withWriteLock
            database.withTransaction {
                replaceIncrementTiers(id, tiers, micro, now)
            }
            syncTouch.markDirty()
        }
    }

    override suspend fun renameAndSetIncrementTiers(
        id: EquipmentId,
        name: String,
        tiers: List<IncrementTier>,
        micro: List<Weight>,
    ) {
        val now = System.currentTimeMillis()
        syncTouch.withWriteLock {
            val row = equipmentDao.getById(id) ?: return@withWriteLock
            if (row.isDeleted) return@withWriteLock
            database.withTransaction {
                equipmentDao.rename(id, name, updatedAtEpochMillis = now)
                replaceIncrementTiers(id, tiers, micro, now)
            }
            syncTouch.markDirty()
        }
    }

    /** Tier replace shared by the tiers-only and rename+tiers writes; call inside a transaction. */
    private suspend fun replaceIncrementTiers(
        id: EquipmentId,
        tiers: List<IncrementTier>,
        micro: List<Weight>?,
        now: Long,
    ) {
        val nextIds =
            tiers.map { IncrementTierId.forEquipmentBand(id, it.lowerBound.amount).value }
        val previousIds = incrementTierDao.idsForEquipment(id)
        syncTouch.replaceIds(SyncCollection.IncrementTiers, previousIds, nextIds, now)
        incrementTierDao.deleteForEquipment(id)
        if (tiers.isNotEmpty()) {
            incrementTierDao.upsertAll(tiers.toEquipmentEntities(id, now))
        }
        // Null micro preserves the stored stops (tiers-only write must not
        // silently wipe the adder); non-null replaces them outright.
        if (micro != null) {
            equipmentDao.updateMicroOptions(
                id = id,
                microOptionsJson = micro.toMicroJsonOrNull(),
                updatedAtEpochMillis = now,
            )
        }
    }

    override suspend fun deleteCustomEquipment(id: EquipmentId, replacementId: EquipmentId?): Boolean {
        val row = equipmentDao.getById(id) ?: return false
        if (id in com.stratum.domain.lift.SeedEquipmentIds.hiddenFromCatalog) return false
        if (row.isDeleted) return true
        val now = System.currentTimeMillis()
        var deleted = false
        syncTouch.withWriteLock {
            database.withTransaction {
                // Re-read under the lock: a concurrent tombstone wins idempotently.
                val current = equipmentDao.getById(id) ?: return@withTransaction
                if (current.isDeleted) {
                    deleted = true
                    return@withTransaction
                }
                val workoutLiftDao = database.workoutLiftDao()
                val affectedLifts = liftDao.getLifts().filter { it.equipmentId == id }
                val affectedSlots = workoutLiftDao.getByEquipmentId(id)
                val hasPointers = affectedLifts.isNotEmpty() || affectedSlots.isNotEmpty()
                val candidates =
                    equipmentDao.getAll().filter {
                        !it.isDeleted && it.id != id &&
                            it.id !in com.stratum.domain.lift.SeedEquipmentIds.hiddenFromCatalog
                    }
                if (hasPointers && replacementId == null && candidates.isNotEmpty()) {
                    // Replacement required but not chosen: fail, change nothing.
                    return@withTransaction
                }
                val effectiveReplacement =
                    replacementId
                        ?: if (hasPointers) com.stratum.domain.lift.SeedEquipmentIds.Bodyweight else null
                if (replacementId != null && !isValidReplacementTarget(id, replacementId)) {
                    return@withTransaction
                }
                if (hasPointers && effectiveReplacement != null) {
                    for (lift in affectedLifts) {
                        liftDao.setEquipmentIdAndLoadType(
                            id = lift.id,
                            equipmentId = effectiveReplacement,
                            loadType = lift.loadType.alignedWithEquipment(effectiveReplacement),
                            updatedAtEpochMillis = now,
                        )
                    }
                    workoutLiftDao.repointEquipmentId(id, effectiveReplacement, now)
                }
                // Tiers stay with the tombstoned row; the flag rides a normal upsert
                // (no cloud-delete enqueue — the row is retained, not removed).
                equipmentDao.tombstoneById(id, now)
                deleted = true
            }
            if (deleted) syncTouch.markDirty()
        }
        return deleted
    }

    override suspend fun findOrCreateByName(name: String): EquipmentId {
        val trimmed = name.trim()
        resolveExistingByName(trimmed)?.let { return it }
        val id = EquipmentId(UUID.randomUUID().toString())
        val now = System.currentTimeMillis()
        return syncTouch.withWriteLock {
            // Double-check under the write lock to avoid duplicate custom rows.
            resolveExistingByName(trimmed)?.let { return@withWriteLock it }
            database.withTransaction {
                insertCustomEquipmentUnlocked(id, trimmed, defaultSingleTiers(), micro = emptyList(), nowMillis = now)
            }
            syncTouch.markDirty()
            id
        }
    }

    /**
     * Find or create equipment **without** taking [SyncTouch.withWriteLock] or calling
     * [SyncTouch.markDirty].
     *
     * Caller **must** already hold the write lock and should wrap DAO work in a Room
     * transaction (nested [androidx.room.withTransaction] joins the outer one). Used by
     * [RoomLiftRepository.addCustomLift] so equipment + lift land in one atomic write.
     *
     * When minting a new row, [createTiers] overrides the default single +5 lb band (used by
     * import custom loading). Existing rows are returned as-is; [createTiers] is ignored.
     */
    suspend fun findOrCreateByNameUnlocked(
        name: String,
        nowMillis: Long,
        createTiers: List<IncrementTier>? = null,
    ): EquipmentId {
        val trimmed = name.trim()
        resolveExistingByName(trimmed)?.let { return it }
        val id = EquipmentId(UUID.randomUUID().toString())
        val tiers = createTiers?.takeIf { it.isNotEmpty() } ?: defaultSingleTiers()
        insertCustomEquipmentUnlocked(id, trimmed, tiers, micro = emptyList(), nowMillis = nowMillis)
        return id
    }

    /**
     * Mints a "<baseName> Stack" equipment clone carrying [tiers]. Per-lift overrides were
     * removed (STR-82/84): a lift that needs its own bands gets a visible clone instead of an
     * invisible override. The name is deduped with the shared [uniqueifyForkEquipmentName]
     * policy so repeated imports never collide. Caller must already hold the write lock
     * and a Room transaction.
     */
    suspend fun createStackCloneUnlocked(
        baseName: String,
        tiers: List<IncrementTier>,
        nowMillis: Long,
    ): EquipmentId {
        val id = EquipmentId(UUID.randomUUID().toString())
        val existingNames = equipmentDao.getAll().filterNot { it.isDeleted }.map { it.name }
        val cloneName = uniqueifyForkEquipmentName("$baseName Stack", existingNames)
        insertCustomEquipmentUnlocked(id, cloneName, tiers, micro = emptyList(), nowMillis = nowMillis)
        return id
    }

    private suspend fun isValidReplacementTarget(
        deletedId: EquipmentId,
        replacementId: EquipmentId,
    ): Boolean {
        val target = equipmentDao.getById(replacementId) ?: return false
        return !target.isDeleted && replacementId != deletedId &&
            replacementId !in com.stratum.domain.lift.SeedEquipmentIds.hiddenFromCatalog
    }

    private suspend fun resolveExistingByName(trimmed: String): EquipmentId? {
        if (trimmed.isEmpty()) return null
        // 1) Exact case-insensitive display-name match.
        equipmentDao.findByNameIgnoreCase(trimmed)?.takeUnless { it.isDeleted }?.let { return it.id }
        // 2) Normalized match so free-text "cable" hits seed "Cables".
        val key = EquipmentNameMatching.normalizeKey(trimmed)
        if (key.isNotEmpty()) {
            equipmentDao.getAll()
                .firstOrNull { !it.isDeleted && EquipmentNameMatching.normalizeKey(it.name) == key }
                ?.let { return it.id }
        }
        // 3) Known free-text / GymEquipment aliases → seed id (live rows only:
        // a tombstoned alias target must not resurrect — mint a new row instead).
        GymEquipmentToEquipmentId.seedIdFromFreeText(trimmed)?.let { seedId ->
            equipmentDao.getById(seedId)?.takeUnless { it.isDeleted }?.let { return it.id }
        }
        return null
    }

    private suspend fun insertCustomEquipmentUnlocked(
        id: EquipmentId,
        name: String,
        tiers: List<IncrementTier>,
        micro: List<Weight>,
        nowMillis: Long,
    ) {
        equipmentDao.upsert(
            EquipmentEntity(
                id = id,
                name = name,
                isCustom = true,
                microOptionsJson = micro.toMicroJsonOrNull(),
                updatedAtEpochMillis = nowMillis,
                pendingSync = SyncFlags.PENDING_UPSERT,
            ),
        )
        val entities = tiers.toEquipmentEntities(id, nowMillis)
        syncTouch.cancelDelete(SyncCollection.Equipment, id.value)
        syncTouch.cancelDelete(
            SyncCollection.IncrementTiers,
            entities.map { it.id.value },
        )
        if (entities.isNotEmpty()) incrementTierDao.upsertAll(entities)
    }

    private fun List<IncrementTier>.toEquipmentEntities(
        equipmentId: EquipmentId,
        now: Long,
    ): List<IncrementTierEntity> =
        map { tier ->
            IncrementTierEntity(
                id = IncrementTierId.forEquipmentBand(equipmentId, tier.lowerBound.amount),
                equipmentId = equipmentId,
                lowerBound = tier.lowerBound.amount,
                upperBound = tier.upperBound?.amount,
                increment = tier.increment.amount,
                updatedAtEpochMillis = now,
                pendingSync = SyncFlags.PENDING_UPSERT,
            )
        }

}
