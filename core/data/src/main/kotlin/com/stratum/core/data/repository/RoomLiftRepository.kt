package com.stratum.core.data.repository

import androidx.room.withTransaction
import com.stratum.core.data.db.EquipmentDao
import com.stratum.core.data.db.GymPreferenceDao
import com.stratum.core.data.db.GymPreferenceEntity
import com.stratum.core.data.db.IncrementTierDao
import com.stratum.core.data.db.IncrementTierEntity
import com.stratum.core.data.db.LiftDao
import com.stratum.core.data.db.StratumDatabase
import com.stratum.core.data.mapper.toBitmask
import com.stratum.core.data.mapper.toDomain
import com.stratum.core.data.mapper.toEntity
import com.stratum.core.data.sync.SyncCollection
import com.stratum.core.data.sync.SyncFlags
import com.stratum.core.data.sync.SyncTouch
import com.stratum.domain.lift.CustomLiftDefinition
import com.stratum.domain.lift.CustomLiftDetails
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.LiftRepository
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.IncrementTierId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.identity.GymPreferenceId
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Room implementation of [LiftRepository]. Reads compose lift + override tiers with the
 * equipment catalog to resolve **effective** [Lift.incrementTiers].
 */
class RoomLiftRepository @Inject constructor(
    private val database: StratumDatabase,
    private val liftDao: LiftDao,
    private val incrementTierDao: IncrementTierDao,
    private val equipmentDao: EquipmentDao,
    private val equipmentRepository: RoomEquipmentRepository,
    private val gymPreferenceDao: GymPreferenceDao,
    private val syncTouch: SyncTouch,
) : LiftRepository {

    override fun observeLifts(): Flow<List<Lift>> =
        combine(
            liftDao.observeLiftsWithTiers(),
            observeEquipmentById(),
        ) { rows, equipmentById ->
            rows.map { it.toDomain(equipmentById) }
        }

    override suspend fun getLiftsByIds(ids: Collection<LiftId>): List<Lift> {
        if (ids.isEmpty()) return emptyList()
        val distinctIds = ids.distinct()
        val rows = liftDao.getLiftsWithTiersByIds(distinctIds)
        if (rows.isEmpty()) return emptyList()
        val equipmentById = equipmentSnapshotById()
        return rows.map { it.toDomain(equipmentById) }
    }

    override fun observeEnabledLifts(): Flow<List<Lift>> =
        combine(
            liftDao.observeEnabledLiftsWithTiers(),
            observeEquipmentById(),
        ) { rows, equipmentById ->
            rows.map { it.toDomain(equipmentById) }
        }

    override fun observeLifts(isCustom: Boolean): Flow<List<Lift>> =
        combine(
            liftDao.observeLiftsWithTiersByCustom(isCustom),
            observeEquipmentById(),
        ) { rows, equipmentById ->
            rows.map { it.toDomain(equipmentById) }
        }

    override fun observeLiftsContainingPattern(pattern: MovementPattern): Flow<List<Lift>> =
        combine(
            liftDao.observeLiftsWithTiersByPattern(1 shl pattern.ordinal),
            observeEquipmentById(),
        ) { rows, equipmentById ->
            rows.map { it.toDomain(equipmentById) }
        }

    override fun observeSelectedPreset(): Flow<GymPreset?> =
        gymPreferenceDao.observe().map { it?.selectedPreset }

    override suspend fun applyPreset(preset: GymPreset) {
        val ids = liftDao.idsForPresetTag(tagPattern = "%,${preset.name},%")
        if (ids.isEmpty()) return
        syncTouch.withWriteLock {
            liftDao.enableAll(ids, updatedAtEpochMillis = System.currentTimeMillis())
            syncTouch.markDirty()
        }
    }

    override suspend fun setLiftEnabled(id: LiftId, enabled: Boolean) {
        syncTouch.withWriteLock {
            liftDao.setEnabled(id, enabled, updatedAtEpochMillis = System.currentTimeMillis())
            syncTouch.markDirty()
        }
    }

    override suspend fun enableLifts(ids: Set<LiftId>) {
        if (ids.isEmpty()) return
        syncTouch.withWriteLock {
            liftDao.enableAll(ids.toList(), updatedAtEpochMillis = System.currentTimeMillis())
            syncTouch.markDirty()
        }
    }

    override suspend fun setSelectedPreset(preset: GymPreset?) {
        syncTouch.withWriteLock {
            val current = gymPreferenceDao.getOrNull()
            val now = System.currentTimeMillis()
            gymPreferenceDao.upsert(
                GymPreferenceEntity(
                    id = GymPreferenceId.Default,
                    selectedPreset = preset,
                    usedHistoricalImport = current?.usedHistoricalImport ?: false,
                    updatedAtEpochMillis = now,
                    pendingSync = SyncFlags.PENDING_UPSERT,
                ),
            )
            syncTouch.cancelDelete(SyncCollection.GymPreference, GymPreferenceId.Default.value)
            syncTouch.markDirty()
        }
    }

    override suspend fun setUsedHistoricalImport(used: Boolean) {
        syncTouch.withWriteLock {
            val current = gymPreferenceDao.getOrNull()
            val now = System.currentTimeMillis()
            gymPreferenceDao.upsert(
                GymPreferenceEntity(
                    id = GymPreferenceId.Default,
                    selectedPreset = current?.selectedPreset,
                    usedHistoricalImport = used,
                    updatedAtEpochMillis = now,
                    pendingSync = SyncFlags.PENDING_UPSERT,
                ),
            )
            syncTouch.cancelDelete(SyncCollection.GymPreference, GymPreferenceId.Default.value)
            syncTouch.markDirty()
        }
    }

    override fun observeUsedHistoricalImport(): Flow<Boolean> =
        gymPreferenceDao.observe().map { it?.usedHistoricalImport ?: false }

    override suspend fun addCustomLift(definition: CustomLiftDefinition): LiftId {
        val id = LiftId(UUID.randomUUID().toString())
        val hasExplicitIncrement = definition.increment != null
        val overrideTiers =
            definition.increment?.let { listOf(singleOpenTier(it)) }.orEmpty()
        val now = System.currentTimeMillis()
        // One write lock + one Room transaction: free-text equipment create and lift insert
        // must not leave an orphan equipment row if we crash mid-way. Public
        // findOrCreateByName takes its own lock (mutex is not reentrant), so use the
        // unlocked helper while we already hold the coordinator lock.
        syncTouch.withWriteLock {
            database.withTransaction {
                val equipmentId =
                    definition.equipmentId
                        ?: definition.equipmentName
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { name ->
                                // find-or-create: reuse existing (incl. seed aliases); mint only
                                // when unknown. createTiers apply only on first insert.
                                val createTiers =
                                    definition.equipmentCreateIncrement?.let {
                                        listOf(singleOpenTier(it))
                                    }
                                equipmentRepository.findOrCreateByNameUnlocked(
                                    name = name,
                                    nowMillis = now,
                                    createTiers = createTiers,
                                )
                            }
                val lift =
                    Lift(
                        id = id,
                        name = definition.name,
                        movementPatterns = definition.movementPatterns,
                        primaryMuscles = definition.primaryMuscles,
                        secondaryMuscles = definition.secondaryMuscles,
                        incrementTiers = overrideTiers,
                        presetTags = emptySet(),
                        equipmentId = equipmentId,
                        usesEquipmentDefaultIncrements = !hasExplicitIncrement,
                        enabled = true,
                        isCustom = true,
                        muscleLengthBias = definition.muscleLengthBias,
                        kinematicProfile = definition.kinematicProfile,
                        loadType = definition.loadType,
                        defaultRestSeconds = definition.defaultRestSeconds,
                    )
                syncTouch.cancelDelete(SyncCollection.Lifts, id.value)
                liftDao.upsert(lift.toEntity(now))
                incrementTierDao.deleteForLift(id)
                if (overrideTiers.isNotEmpty()) {
                    val tierEntities =
                        overrideTiers.map { tier ->
                            IncrementTierEntity(
                                id = IncrementTierId.forBand(id, tier.lowerBound.amount),
                                liftId = id,
                                equipmentId = null,
                                lowerBound = tier.lowerBound.amount,
                                upperBound = tier.upperBound?.amount,
                                increment = tier.increment.amount,
                                updatedAtEpochMillis = now,
                                pendingSync = SyncFlags.PENDING_UPSERT,
                            )
                        }
                    syncTouch.cancelDelete(
                        SyncCollection.IncrementTiers,
                        tierEntities.map { it.id.value },
                    )
                    incrementTierDao.upsertAll(tierEntities)
                }
            }
            syncTouch.markDirty()
        }
        return id
    }

    override suspend fun setIncrementTiers(id: LiftId, tiers: List<IncrementTier>) {
        val now = System.currentTimeMillis()
        syncTouch.withWriteLock {
            database.withTransaction {
                val nextIds = tiers.map { IncrementTierId.forBand(id, it.lowerBound.amount).value }
                val previousIds = incrementTierDao.idsForLift(id)
                syncTouch.replaceIds(SyncCollection.IncrementTiers, previousIds, nextIds, now)
                incrementTierDao.deleteForLift(id)
                if (tiers.isNotEmpty()) {
                    val entities =
                        tiers.map { tier ->
                            IncrementTierEntity(
                                id = IncrementTierId.forBand(id, tier.lowerBound.amount),
                                liftId = id,
                                equipmentId = null,
                                lowerBound = tier.lowerBound.amount,
                                upperBound = tier.upperBound?.amount,
                                increment = tier.increment.amount,
                                updatedAtEpochMillis = now,
                                pendingSync = SyncFlags.PENDING_UPSERT,
                            )
                        }
                    incrementTierDao.upsertAll(entities)
                }
            }
            syncTouch.markDirty()
        }
    }

    override suspend fun setLiftEquipment(id: LiftId, equipmentId: EquipmentId?) {
        val now = System.currentTimeMillis()
        syncTouch.withWriteLock {
            database.withTransaction {
                val previousTierIds = incrementTierDao.idsForLift(id)
                syncTouch.replaceIds(SyncCollection.IncrementTiers, previousTierIds, emptyList(), now)
                incrementTierDao.deleteForLift(id)
                liftDao.setEquipmentId(
                    id = id,
                    equipmentId = equipmentId,
                    updatedAtEpochMillis = now,
                )
            }
            syncTouch.markDirty()
        }
    }

    override suspend fun updateLiftMuscles(
        id: LiftId,
        primaryMuscles: Set<com.stratum.domain.onboarding.MuscleGroup>,
        secondaryMuscles: Set<com.stratum.domain.onboarding.MuscleGroup>,
    ) {
        syncTouch.withWriteLock {
            liftDao.updateMuscles(
                id = id,
                primaryBitmask = primaryMuscles.toBitmask(),
                secondaryBitmask = secondaryMuscles.toBitmask(),
                updatedAtEpochMillis = System.currentTimeMillis(),
            )
            syncTouch.markDirty()
        }
    }

    override suspend fun updateCustomLiftDetails(
        id: LiftId,
        details: CustomLiftDetails,
    ) {
        syncTouch.withWriteLock {
            liftDao.updateCustomDetails(
                id = id,
                movementPatternsBitmask = details.movementPatterns.toBitmask(),
                muscleLengthBias = details.muscleLengthBias,
                kinematicProfile = details.kinematicProfile,
                loadType = details.loadType,
                equipmentId = details.equipmentId,
                updatedAtEpochMillis = System.currentTimeMillis(),
            )
            syncTouch.markDirty()
        }
    }

    override suspend fun setDefaultRestSeconds(id: LiftId, restSeconds: Int?) {
        syncTouch.withWriteLock {
            liftDao.setDefaultRestSeconds(
                id = id,
                restSeconds = restSeconds,
                updatedAtEpochMillis = System.currentTimeMillis(),
            )
            syncTouch.markDirty()
        }
    }

    private fun observeEquipmentById(): Flow<Map<EquipmentId, Equipment>> =
        combine(
            equipmentDao.observeAll(),
            incrementTierDao.observeAllEquipmentTiers(),
        ) { equipmentRows, allEquipmentTiers ->
            val tiersByEquipment = allEquipmentTiers.groupBy { it.equipmentId }
            equipmentRows.associate { row ->
                row.id to row.toDomain(tiersByEquipment[row.id].orEmpty())
            }
        }

    /** One-shot equipment map for suspend reads (same resolution as [observeEquipmentById]). */
    private suspend fun equipmentSnapshotById(): Map<EquipmentId, Equipment> {
        val equipmentRows = equipmentDao.getAll()
        val tiersByEquipment = incrementTierDao.getAllEquipmentTiers().groupBy { it.equipmentId }
        return equipmentRows.associate { row ->
            row.id to row.toDomain(tiersByEquipment[row.id].orEmpty())
        }
    }

    private fun singleOpenTier(increment: Weight): IncrementTier =
        IncrementTier(Weight(0.0, increment.unit), null, increment)
}
