package com.stratum.core.infrastructure.seed

import android.content.Context
import com.stratum.core.infrastructure.db.LiftDao
import com.stratum.core.infrastructure.db.LiftEntity
import com.stratum.core.infrastructure.mapper.toBitmask
import com.stratum.core.infrastructure.mapper.toPresetCsv
import com.stratum.core.infrastructure.sync.SyncFlags
import com.stratum.domain.importing.GymEquipment
import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.lift.requireLoadTypeEquipmentAlignment
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.logging.LoggingPort
import kotlinx.serialization.json.Json

/**
 * Loads the bundled catalog seed into the `lifts`/`increment_tiers` tables. Invoked from DI
 * after [com.stratum.core.infrastructure.db.StratumDatabase] is fully open and after
 * [EquipmentCatalogSeeder] has run.
 *
 * **Idempotent:** upserts by stable slug id. Never clears user-created (`isCustom`) rows.
 * Seed lifts inherit equipment default increments via [LiftEntity.equipmentId].
 */
class LiftCatalogSeeder(
    private val liftDao: LiftDao,
    private val equipmentTagsByLiftId: Map<LiftId, Set<GymEquipment>> = emptyMap(),
    private val loggingPort: LoggingPort,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Read [assetPath] from [context].assets, decode it, and upsert the catalog. Safe to call
     * repeatedly. Returns the number of seed lifts written.
     */
    suspend fun seed(context: Context, assetPath: String = ASSET_PATH): Int {
        val dtos = decode(context, assetPath) ?: return 0
        if (dtos.isEmpty()) return 0

        val lifts =
            dtos
                .filterNot { dto -> isBandVariantLift(dto.id, dto.name) }
                .mapNotNull(::mapDto)
        for (lift in lifts) {
            // STR-131: a tombstoned seed row is never resurrected by reseeding.
            // The fresh seed entity carries isDeleted=false and default user
            // fields, so upserting over a tombstoned row would silently undo
            // the user's delete (and the synced tombstone). Skip it whole.
            if (liftDao.getById(lift.id)?.isDeleted == true) continue
            liftDao.upsert(lift)
        }
        return dtos.size
    }

    private fun decode(context: Context, assetPath: String): List<LiftSeedDto>? =
        runCatching {
            context.assets.open(assetPath).use { stream ->
                json.decodeFromString<List<LiftSeedDto>>(stream.readBytes().decodeToString())
            }
        }.getOrElse { error ->
            loggingPort.error(TAG, "Failed to decode lift catalog seed: ${error.message}", error)
            null
        }

    private fun mapDto(dto: LiftSeedDto): LiftEntity? =
        mapSeedDto(dto, equipmentTagsByLiftId)

    companion object {
        const val ASSET_PATH = "lifts/seed-lifts.json"
        private const val TAG = "LiftCatalogSeed"

        /**
         * Resistance-band exercise variants are excluded from the commercial seed
         * catalog. Internal (not private) so catalog audit tests apply the exact
         * production exclusion instead of mirroring the rule.
         */
        internal fun isBandVariantLift(id: String, name: String): Boolean {
            val pattern = Regex("(^|[-\\s])band($|[-\\s])", RegexOption.IGNORE_CASE)
            return pattern.containsMatchIn(id) || pattern.containsMatchIn(name)
        }

        /**
         * Pure seed DTO → entity mapping (testable without DAO). Missing [LiftSeedDto.fatigueProfile]
         * defaults to [FatigueProfile.Low].
         */
        internal fun mapSeedDto(
            dto: LiftSeedDto,
            equipmentTagsByLiftId: Map<LiftId, Set<GymEquipment>> = emptyMap(),
        ): LiftEntity? {
            val patterns = dto.movementPatterns.toSet()
            if (patterns.isEmpty()) return null
            val primary = dto.primaryMuscles.toSet()
            val secondary = dto.secondaryMuscles.toSet()
            val tags = dto.presetTags.mapNotNull { enumValueOfOrNull<GymPreset>(it) }.toSet()
            val bias = dto.muscleLengthBias ?: MuscleLengthBias.MidRange
            val profile = dto.fatigueProfile ?: FatigueProfile.Low
            val loadType = dto.loadType ?: LoadType.Weighted
            val id = LiftId(dto.id)
            val equipmentId =
                dto.equipmentId?.let { EquipmentId(it) }
                    ?: GymEquipmentToEquipmentId.equipmentIdFor(
                        liftId = id,
                        tags = equipmentTagsByLiftId[id].orEmpty(),
                    )
            // STR-250: the seed's two signals (declared loadType, equipment tags)
            // must agree under the domain alignment rule. A contradictory row fails
            // the seed loudly here — which of the two signals is wrong is unknowable
            // at import time, so the row is never persisted and never silently
            // skipped. The exception propagates out of seed(); DataModule logs it.
            requireLoadTypeEquipmentAlignment(loadType, equipmentId)
            val lift =
                LiftEntity(
                    id = id,
                    name = dto.name,
                    movementPatternsBitmask = patterns.toBitmask(),
                    primaryMusclesBitmask = primary.toBitmask(),
                    secondaryMusclesBitmask = secondary.toBitmask(),
                    presetTags = tags.toPresetCsv(),
                    equipmentId = equipmentId,
                    isCustom = false,
                    enabled = false,
                    description = dto.description,
                    imageUrisCsv = dto.imageUris.joinToString(","),
                    muscleLengthBias = bias,
                    mainstream = dto.mainstream,
                    fatigueProfile = profile,
                    loadType = loadType,
                    updatedAtEpochMillis = 0L,
                    pendingSync = SyncFlags.CLEAN,
                )
            return lift
        }

        /**
         * Last-resort lb tiers when a lift has neither override nor equipment defaults
         * (OS-4 §2.2 example). Not written to seed rows that inherit equipment.
         */
        val DEFAULT_TIERS: List<IncrementTierSeedDto> =
            listOf(
                IncrementTierSeedDto(lowerBound = 0.0, upperBound = 40.0, increment = 2.5),
                IncrementTierSeedDto(lowerBound = 40.0, upperBound = 100.0, increment = 5.0),
                IncrementTierSeedDto(lowerBound = 100.0, upperBound = 300.0, increment = 10.0),
                IncrementTierSeedDto(lowerBound = 300.0, upperBound = null, increment = 25.0),
            )

        private inline fun <reified T : Enum<T>> enumValueOfOrNull(name: String): T? =
            runCatching { enumValueOf<T>(name) }.getOrNull()
    }
}
