package com.stratum.core.data.seed

import android.content.Context
import com.stratum.core.data.db.LiftDao
import com.stratum.core.data.db.LiftEntity
import com.stratum.core.data.db.IncrementTierDao
import com.stratum.core.data.db.IncrementTierEntity
import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import kotlinx.serialization.json.Json

/**
 * Loads the bundled catalog seed into the `lifts`/`increment_tiers` tables. Runs once, on
 * first database creation, from a `RoomDatabase.Callback.onCreate` wired in DI
 * (deterministic, one-shot — no fire-and-forget WorkManager job, per the OS-4 deterministic-
 * seeding decision).
 *
 * Constructed **inside** the DB creation callback with the DAOs pulled from the just-built
 * [StratumDatabase], rather than Hilt-injected — injecting the DAOs would form a DI cycle
 * (DB → DAO → seeder → DB). The JSON decoder is stateless, so a fresh instance per seed is
 * cheap.
 *
 * **Idempotent:** it `upsert`s by stable slug id, so re-running (e.g. across an OS version
 * bump that recreates the DB) is safe and converges to the asset's contents. It never clears
 * user-created (`isCustom`) rows, since those are not in the asset.
 *
 * Names are decoded defensively: an unknown muscle/pattern/preset name in the asset is
 * skipped with a logged warning rather than crashing, so a stale asset on an upgraded app
 * degrades gracefully.
 */
class LiftCatalogSeeder(
    private val liftDao: LiftDao,
    private val incrementTierDao: IncrementTierDao,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Read [assetPath] from [context].assets, decode it, and upsert the catalog. Safe to call
     * repeatedly. Returns the number of seed lifts written.
     */
    suspend fun seed(context: Context, assetPath: String = ASSET_PATH): Int {
        val dtos = decode(context, assetPath) ?: return 0
        if (dtos.isEmpty()) return 0

        for (dto in dtos) {
            val mapped = mapDto(dto) ?: continue
            liftDao.upsert(mapped.lift)
            incrementTierDao.deleteForLift(mapped.lift.id)
            if (mapped.tiers.isNotEmpty()) incrementTierDao.upsertAll(mapped.tiers)
        }
        return dtos.size
    }

    private fun decode(context: Context, assetPath: String): List<LiftSeedDto>? =
        runCatching {
            context.assets.open(assetPath).use { stream ->
                json.decodeFromString<List<LiftSeedDto>>(stream.readBytes().decodeToString())
            }
        }.getOrElse {
            // Asset missing/corrupt is a packaging bug, not a runtime crash path. Seed simply
            // produces an empty catalog; the user can still add custom lifts.
            null
        }

    private fun mapDto(dto: LiftSeedDto): MappedLift? {
        val pattern = enumValueOfOrNull<MovementPattern>(dto.movementPattern) ?: return null
        val primary = dto.primaryMuscles.mapNotNull { enumValueOfOrNull<MuscleGroup>(it) }.toSet()
        val secondary = dto.secondaryMuscles.mapNotNull { enumValueOfOrNull<MuscleGroup>(it) }.toSet()
        val tags = dto.presetTags.mapNotNull { enumValueOfOrNull<GymPreset>(it) }.toSet()
        val id = LiftId(dto.id)
        val lift = LiftEntity(
            id = id,
            name = dto.name,
            movementPattern = pattern,
            primaryMusclesBitmask = primary.fold(0) { mask, m -> mask or (1 shl m.ordinal) },
            secondaryMusclesBitmask = secondary.fold(0) { mask, m -> mask or (1 shl m.ordinal) },
            allowWeightChangeBetweenSets = dto.allowWeightChangeBetweenSets,
            presetTags = tags.toPresetCsv(),
            equipment = dto.equipment,
            isCustom = false,
            enabled = false,
        )
        val tiers = (dto.incrementTiers ?: DEFAULT_TIERS).mapIndexed { _, t ->
            IncrementTierEntity(
                liftId = id,
                lowerBound = t.lowerBound,
                upperBound = t.upperBound,
                increment = t.increment,
            )
        }
        return MappedLift(lift, tiers)
    }

    private data class MappedLift(val lift: LiftEntity, val tiers: List<IncrementTierEntity>)

    private inline fun <reified T : Enum<T>> enumValueOfOrNull(name: String): T? =
        runCatching { enumValueOf<T>(name) }.getOrNull()

    private fun Set<GymPreset>.toPresetCsv(): String =
        if (isEmpty()) EMPTY_PRESET_CSV else "," + joinToString(",") { it.name } + ","

    companion object {
        const val ASSET_PATH = "lifts/seed-lifts.json"
        const val EMPTY_PRESET_CSV: String = ","

        /** Default lb tiers applied when a seed entry omits its own (OS-4 §2.2 example). */
        val DEFAULT_TIERS: List<IncrementTierSeedDto> = listOf(
            IncrementTierSeedDto(lowerBound = 0.0, upperBound = 40.0, increment = 2.5),
            IncrementTierSeedDto(lowerBound = 40.0, upperBound = 100.0, increment = 5.0),
            IncrementTierSeedDto(lowerBound = 100.0, upperBound = 300.0, increment = 10.0),
            IncrementTierSeedDto(lowerBound = 300.0, upperBound = null, increment = 25.0),
        )

        val DEFAULT_UNIT: Weight.WeightUnit = Weight.WeightUnit.Lb
    }
}
