package com.stratum.core.data.seed

import android.content.Context
import com.stratum.core.data.db.IncrementTierDao
import com.stratum.core.data.db.IncrementTierEntity
import com.stratum.core.data.db.LiftDao
import com.stratum.core.data.db.LiftEntity
import com.stratum.core.data.mapper.toBitmask
import com.stratum.core.data.mapper.toPresetCsv
import com.stratum.core.data.sync.SyncFlags
import com.stratum.domain.importing.GymEquipment
import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.KinematicProfile
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.IncrementTierId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import kotlinx.serialization.json.Json

/**
 * Loads the bundled catalog seed into the `lifts`/`increment_tiers` tables. Invoked from DI
 * after [com.stratum.core.data.db.StratumDatabase] is fully open and after
 * [EquipmentCatalogSeeder] has run.
 *
 * **Idempotent:** upserts by stable slug id. Never clears user-created (`isCustom`) rows.
 *
 * Seed lifts inherit equipment default increments via [LiftEntity.equipmentId] unless the
 * seed DTO supplies explicit [LiftSeedDto.incrementTiers] (lift-scoped override rows).
 */
class LiftCatalogSeeder(
    private val liftDao: LiftDao,
    private val incrementTierDao: IncrementTierDao,
    private val equipmentTagsByLiftId: Map<LiftId, Set<GymEquipment>> = emptyMap(),
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

        val mappedLifts =
            dtos
                .filterNot { dto -> isBandVariantLift(dto.id, dto.name) }
                .mapNotNull(::mapDto)
        for (mapped in mappedLifts) {
            liftDao.upsert(mapped.lift)
            incrementTierDao.deleteForLift(mapped.lift.id)
            if (mapped.overrideTiers.isNotEmpty()) {
                incrementTierDao.upsertAll(mapped.overrideTiers)
            }
        }
        return dtos.size
    }

    private fun decode(context: Context, assetPath: String): List<LiftSeedDto>? =
        runCatching {
            context.assets.open(assetPath).use { stream ->
                json.decodeFromString<List<LiftSeedDto>>(stream.readBytes().decodeToString())
            }
        }.getOrElse {
            null
        }

    /** Resistance-band exercise variants are excluded from the commercial seed catalog. */
    private fun isBandVariantLift(id: String, name: String): Boolean {
        val pattern = Regex("(^|[-\\s])band($|[-\\s])", RegexOption.IGNORE_CASE)
        return pattern.containsMatchIn(id) || pattern.containsMatchIn(name)
    }

    private fun mapDto(dto: LiftSeedDto): MappedLift? {
        val patterns = dto.movementPatterns.toSet()
        if (patterns.isEmpty()) return null
        val primary = dto.primaryMuscles.toSet()
        val secondary = dto.secondaryMuscles.toSet()
        val tags = dto.presetTags.mapNotNull { enumValueOfOrNull<GymPreset>(it) }.toSet()
        val bias = dto.muscleLengthBias ?: MuscleLengthBias.MidRange
        val profile = dto.kinematicProfile ?: KinematicProfile.Isolation
        val loadType = dto.loadType ?: LoadType.Weighted
        val id = LiftId(dto.id)
        val equipmentId =
            dto.equipmentId?.let { EquipmentId(it) }
                ?: GymEquipmentToEquipmentId.equipmentIdFor(
                    liftId = id,
                    tags = equipmentTagsByLiftId[id].orEmpty(),
                )
        val lift = LiftEntity(
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
            kinematicProfile = profile,
            loadType = loadType,
            updatedAtEpochMillis = 0L,
            pendingSync = SyncFlags.CLEAN,
        )
        // Explicit seed override only — otherwise inherit equipment defaults.
        val overrideTiers =
            dto.incrementTiers?.map { t ->
                IncrementTierEntity(
                    id = IncrementTierId.forBand(id, t.lowerBound),
                    liftId = id,
                    equipmentId = null,
                    lowerBound = t.lowerBound,
                    upperBound = t.upperBound,
                    increment = t.increment,
                    updatedAtEpochMillis = 0L,
                    pendingSync = SyncFlags.CLEAN,
                )
            }.orEmpty()
        return MappedLift(lift, overrideTiers)
    }

    private data class MappedLift(
        val lift: LiftEntity,
        val overrideTiers: List<IncrementTierEntity>,
    )

    private inline fun <reified T : Enum<T>> enumValueOfOrNull(name: String): T? =
        runCatching { enumValueOf<T>(name) }.getOrNull()

    companion object {
        const val ASSET_PATH = "lifts/seed-lifts.json"

        /**
         * Last-resort lb tiers when a lift has neither override nor equipment defaults
         * (OS-4 §2.2 example). Not written to seed rows that inherit equipment.
         */
        val DEFAULT_TIERS: List<IncrementTierSeedDto> = listOf(
            IncrementTierSeedDto(lowerBound = 0.0, upperBound = 40.0, increment = 2.5),
            IncrementTierSeedDto(lowerBound = 40.0, upperBound = 100.0, increment = 5.0),
            IncrementTierSeedDto(lowerBound = 100.0, upperBound = 300.0, increment = 10.0),
            IncrementTierSeedDto(lowerBound = 300.0, upperBound = null, increment = 25.0),
        )
    }
}
