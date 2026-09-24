package com.stratum.core.data.repository

import androidx.room.withTransaction
import com.stratum.core.data.db.EquipmentDao
import com.stratum.core.data.db.EquipmentEntity
import com.stratum.core.data.db.IncrementTierDao
import com.stratum.core.data.db.IncrementTierEntity
import com.stratum.core.data.db.LiftDao
import com.stratum.core.data.db.StratumDatabase
import com.stratum.core.data.mapper.toDomain
import com.stratum.core.data.mapper.toMicroJsonOrNull
import com.stratum.core.data.seed.GymEquipmentToEquipmentId
import com.stratum.core.data.sync.SyncCollection
import com.stratum.core.data.sync.SyncFlags
import com.stratum.core.data.sync.SyncTouch
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.EquipmentNameMatching
import com.stratum.domain.lift.EquipmentRepository
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.Weight
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
            rows.map { row -> row.toDomain(byEquipment[row.id].orEmpty()) }
        }

    override suspend fun getEquipment(id: EquipmentId): Equipment? {
        val row = equipmentDao.getById(id) ?: return null
        return row.toDomain(incrementTierDao.getForEquipment(id))
    }

    override suspend fun addCustomEquipment(name: String, tiers: List<IncrementTier>): EquipmentId {
        val id = EquipmentId(UUID.randomUUID().toString())
        val resolvedTiers = tiers.ifEmpty { defaultSingleTiers() }
        val now = System.currentTimeMillis()
        syncTouch.withWriteLock {
            database.withTransaction {
                insertCustomEquipmentUnlocked(id, name, resolvedTiers, now)
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
                insertCustomEquipmentUnlocked(id, name, resolvedTiers, now)
                for (liftId in distinctLiftIds) {
                    val previousTierIds = incrementTierDao.idsForLift(liftId)
                    syncTouch.replaceIds(
                        SyncCollection.IncrementTiers,
                        previousTierIds,
                        emptyList(),
                        now,
                    )
                    liftDao.setEquipmentId(
                        id = liftId,
                        equipmentId = id,
                        updatedAtEpochMillis = now,
                    )
                    // Assigned lifts inherit the new machine's lattice and knob.
                    clearLiftIncrementOverrides(incrementTierDao, liftDao, liftId, now)
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
            database.withTransaction {
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
            syncTouch.markDirty()
        }
    }

    override suspend fun deleteCustomEquipment(id: EquipmentId): Boolean {
        val row = equipmentDao.getById(id) ?: return false
        if (!row.isCustom) return false
        if (equipmentDao.liftReferenceCount(id) > 0) return false
        val now = System.currentTimeMillis()
        return syncTouch.withWriteLock {
            database.withTransaction {
                val tierIds = incrementTierDao.idsForEquipment(id)
                syncTouch.replaceIds(SyncCollection.IncrementTiers, tierIds, emptyList(), now)
                incrementTierDao.deleteForEquipment(id)
                val deleted = equipmentDao.deleteCustomById(id)
                if (deleted > 0) {
                    syncTouch.enqueueDelete(SyncCollection.Equipment, id.value)
                    syncTouch.markDirty()
                    true
                } else {
                    false
                }
            }
        }
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
                insertCustomEquipmentUnlocked(id, trimmed, defaultSingleTiers(), now)
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
        insertCustomEquipmentUnlocked(id, trimmed, tiers, nowMillis)
        return id
    }

    private suspend fun resolveExistingByName(trimmed: String): EquipmentId? {
        if (trimmed.isEmpty()) return null
        // 1) Exact case-insensitive display-name match.
        equipmentDao.findByNameIgnoreCase(trimmed)?.let { return it.id }
        // 2) Normalized match so free-text "cable" hits seed "Cables".
        val key = EquipmentNameMatching.normalizeKey(trimmed)
        if (key.isNotEmpty()) {
            equipmentDao.getAll()
                .firstOrNull { EquipmentNameMatching.normalizeKey(it.name) == key }
                ?.let { return it.id }
        }
        // 3) Known free-text / GymEquipment aliases → seed id (when that row exists).
        GymEquipmentToEquipmentId.seedIdFromFreeText(trimmed)?.let { seedId ->
            equipmentDao.getById(seedId)?.let { return it.id }
        }
        return null
    }

    private suspend fun insertCustomEquipmentUnlocked(
        id: EquipmentId,
        name: String,
        tiers: List<IncrementTier>,
        nowMillis: Long,
    ) {
        equipmentDao.upsert(
            EquipmentEntity(
                id = id,
                name = name,
                isCustom = true,
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
                liftId = null,
                equipmentId = equipmentId,
                lowerBound = tier.lowerBound.amount,
                upperBound = tier.upperBound?.amount,
                increment = tier.increment.amount,
                updatedAtEpochMillis = now,
                pendingSync = SyncFlags.PENDING_UPSERT,
            )
        }

    private fun defaultSingleTiers(): List<IncrementTier> {
        val lb = Weight.WeightUnit.Lb
        return listOf(IncrementTier(Weight(0.0, lb), null, Weight(DEFAULT_INCREMENT_LB, lb)))
    }

    private companion object {
        const val DEFAULT_INCREMENT_LB = 5.0
    }
}
