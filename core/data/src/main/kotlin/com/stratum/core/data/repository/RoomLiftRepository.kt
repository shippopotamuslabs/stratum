package com.stratum.core.data.repository

import androidx.room.withTransaction
import com.stratum.core.data.db.GymPreferenceDao
import com.stratum.core.data.db.GymPreferenceEntity
import com.stratum.core.data.db.IncrementTierDao
import com.stratum.core.data.db.LiftDao
import com.stratum.core.data.db.StratumDatabase
import com.stratum.core.data.mapper.toDomain
import com.stratum.core.data.mapper.toEntity
import com.stratum.core.data.mapper.toTierEntities
import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.LiftRepository
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
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
) : LiftRepository {

    override fun observeLifts(): Flow<List<Lift>> =
        liftDao.observeLiftsWithTiers().map { rows -> rows.map { it.toDomain() } }

    override fun observeSelectedPreset(): Flow<GymPreset?> =
        gymPreferenceDao.observe().map { it?.selectedPreset }

    override suspend fun applyPreset(preset: GymPreset) {
        // The DAO matches with SQL LIKE, so the pattern needs % wildcards around the
        // delimiter-bounded tag (`,CommercialGym,`) to substring-match inside any CSV.
        val ids = liftDao.idsForPresetTag(tagPattern = "%,${preset.name},%")
        if (ids.isNotEmpty()) liftDao.enableAll(ids)
    }

    override suspend fun setLiftEnabled(id: LiftId, enabled: Boolean) {
        liftDao.setEnabled(id, enabled)
    }

    override suspend fun setSelectedPreset(preset: GymPreset?) {
        gymPreferenceDao.upsert(GymPreferenceEntity(selectedPreset = preset))
    }

    override suspend fun addCustomLift(
        name: String,
        allowWeightChangeBetweenSets: Boolean,
        increment: Weight?,
    ): LiftId {
        val id = LiftId(UUID.randomUUID().toString())
        val lift = Lift(
            id = id,
            name = name,
            movementPattern = MovementPattern.LegPush, // v1 custom lifts default to a compound slot
            primaryMuscles = emptySet(), // unknown for a free-form lift; OS-6 voice edits fill this in
            secondaryMuscles = emptySet(),
            allowWeightChangeBetweenSets = allowWeightChangeBetweenSets,
            incrementTiers = increment?.let { listOf(singleOpenTier(it)) } ?: defaultDomainTiers(),
            presetTags = emptySet(),
            enabled = true,
            isCustom = true,
        )
        database.withTransaction {
            liftDao.upsert(lift.toEntity())
            incrementTierDao.deleteForLift(id)
            incrementTierDao.upsertAll(lift.toTierEntities())
        }
        return id
    }

    private fun singleOpenTier(increment: Weight): IncrementTier =
        IncrementTier(Weight(0.0, increment.unit), null, increment)

    private fun defaultDomainTiers(): List<IncrementTier> {
        val lb = Weight.WeightUnit.Lb
        // §2.2 example bands, as lb Weight tiers. Named to satisfy detekt MagicNumber and to
        // document the intent at each boundary.
        return listOf(
            IncrementTier(Weight(0.0, lb), Weight(BAND_1_UPPER, lb), Weight(INC_LOW, lb)),
            IncrementTier(Weight(BAND_1_UPPER, lb), Weight(BAND_2_UPPER, lb), Weight(INC_MID, lb)),
            IncrementTier(Weight(BAND_2_UPPER, lb), Weight(BAND_3_UPPER, lb), Weight(INC_HIGH, lb)),
            IncrementTier(Weight(BAND_3_UPPER, lb), null, Weight(INC_TOP, lb)),
        )
    }

    private companion object {
        // §2.2 example increment bands (lb): 0–40, 40–100, 100–300, 300+.
        const val BAND_1_UPPER: Double = 40.0
        const val BAND_2_UPPER: Double = 100.0
        const val BAND_3_UPPER: Double = 300.0
        const val INC_LOW: Double = 2.5
        const val INC_MID: Double = 5.0
        const val INC_HIGH: Double = 10.0
        const val INC_TOP: Double = 25.0
    }
}
