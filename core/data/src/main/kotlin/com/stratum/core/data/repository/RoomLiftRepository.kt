package com.stratum.core.data.repository

import androidx.room.withTransaction
import com.stratum.core.data.db.GymPreferenceDao
import com.stratum.core.data.db.GymPreferenceEntity
import com.stratum.core.data.db.IncrementTierDao
import com.stratum.core.data.db.LiftDao
import com.stratum.core.data.db.StratumDatabase
import com.stratum.core.data.mapper.toBitmask
import com.stratum.core.data.mapper.toDomain
import com.stratum.core.data.mapper.toEntity
import com.stratum.core.data.mapper.toTierEntities
import com.stratum.core.data.sync.SyncCollection
import com.stratum.core.data.sync.SyncFlags
import com.stratum.core.data.sync.SyncTouch
import com.stratum.domain.lift.CustomLiftDefinition
import com.stratum.domain.lift.CustomLiftDetails
import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.LiftRepository
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.IncrementTierId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.identity.GymPreferenceId
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room implementation of [LiftRepository]. Reads compose the relation stream into domain
 * [Lift]s; writes are transactional so a lift and its tier rows stay consistent.
 *
 * - [applyPreset] enables every lift tagged with the preset in one `enableAll` statement.
 * - [addCustomLift] mints a UUID id and writes the lift + its tier rows + `enabled = true`.
 *   When the caller omits an increment, the lift gets sensible default tiers so it still has
 *   realistic load jumps for OS-6.
 */
class RoomLiftRepository @Inject constructor(
    private val database: StratumDatabase,
    private val liftDao: LiftDao,
    private val incrementTierDao: IncrementTierDao,
    private val gymPreferenceDao: GymPreferenceDao,
    private val syncTouch: SyncTouch,
) : LiftRepository {

    override fun observeLifts(): Flow<List<Lift>> =
        liftDao.observeLiftsWithTiers().map { rows -> rows.map { it.toDomain() } }

    override fun observeEnabledLifts(): Flow<List<Lift>> =
        liftDao.observeEnabledLiftsWithTiers().map { rows -> rows.map { it.toDomain() } }

    override fun observeLifts(isCustom: Boolean): Flow<List<Lift>> =
        liftDao.observeLiftsWithTiersByCustom(isCustom).map { rows -> rows.map { it.toDomain() } }

    override fun observeLiftsContainingPattern(pattern: MovementPattern): Flow<List<Lift>> =
        liftDao.observeLiftsWithTiersByPattern(1 shl pattern.ordinal)
            .map { rows -> rows.map { it.toDomain() } }

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
        val lift =
            Lift(
                id = id,
                name = definition.name,
                movementPatterns = definition.movementPatterns,
                primaryMuscles = definition.primaryMuscles,
                secondaryMuscles = definition.secondaryMuscles,
                incrementTiers = definition.increment?.let { listOf(singleOpenTier(it)) } ?: defaultDomainTiers(),
                presetTags = emptySet(),
                equipment = definition.equipment,
                enabled = true,
                isCustom = true,
                muscleLengthBias = definition.muscleLengthBias,
                kinematicProfile = definition.kinematicProfile,
                loadType = definition.loadType,
                defaultRestSeconds = definition.defaultRestSeconds,
            )
        syncTouch.withWriteLock {
            database.withTransaction {
                syncTouch.cancelDelete(SyncCollection.Lifts, id.value)
                liftDao.upsert(lift.toEntity())
                incrementTierDao.deleteForLift(id)
                val tierEntities = lift.toTierEntities()
                syncTouch.cancelDelete(SyncCollection.IncrementTiers, tierEntities.map { it.id.value })
                incrementTierDao.upsertAll(tierEntities)
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
                            com.stratum.core.data.db.IncrementTierEntity(
                                id = IncrementTierId.forBand(id, tier.lowerBound.amount),
                                liftId = id,
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
                equipment = details.equipment,
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

    private fun singleOpenTier(increment: Weight): IncrementTier =
        IncrementTier(Weight(0.0, increment.unit), null, increment)

    private fun defaultDomainTiers(): List<IncrementTier> {
        val lb = Weight.WeightUnit.Lb
        return listOf(
            IncrementTier(Weight(0.0, lb), Weight(BAND_1_UPPER, lb), Weight(INC_LOW, lb)),
            IncrementTier(Weight(BAND_1_UPPER, lb), Weight(BAND_2_UPPER, lb), Weight(INC_MID, lb)),
            IncrementTier(Weight(BAND_2_UPPER, lb), Weight(BAND_3_UPPER, lb), Weight(INC_HIGH, lb)),
            IncrementTier(Weight(BAND_3_UPPER, lb), null, Weight(INC_TOP, lb)),
        )
    }

    private companion object {
        const val BAND_1_UPPER: Double = 40.0
        const val BAND_2_UPPER: Double = 100.0
        const val BAND_3_UPPER: Double = 300.0
        const val INC_LOW: Double = 2.5
        const val INC_MID: Double = 5.0
        const val INC_HIGH: Double = 10.0
        const val INC_TOP: Double = 25.0
    }
}
