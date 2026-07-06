package com.stratum.core.data.seed

import com.stratum.domain.lift.KinematicProfile
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.onboarding.MuscleGroup
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One entry in the bundled seed catalog (`src/main/assets/lifts/seed-lifts.json`). Decoded by
 * [LiftCatalogSeeder] into catalog rows on first database creation.
 *
 * The asset uses **readable names**, never ordinals — kotlinx.serialization decodes the enum
 * values directly from their `.name` strings (e.g. `"Chest"` → [MuscleGroup.Chest]):
 * - [primaryMuscles]/[secondaryMuscles] are [MuscleGroup] values.
 * - [movementPatterns] are [MovementPattern] values.
 * - [muscleLengthBias] is a [MuscleLengthBias] value; [kinematicProfile] is a [KinematicProfile].
 * - [presetTags] are `GymPreset.name` strings (left as strings — they are decoded to enums in
 *   the seeder, which keeps a single conversion boundary for the preset domain).
 *
 * An unknown enum name in the asset is coerced: the seeder's `Json { coerceInputValues = true }`
 * maps it to the enum's default (first declaration) rather than aborting the whole catalog, and
 * [LiftCatalogSeeder.mapDto] then filters degenerate entries (e.g. no resolved movement pattern).
 * Renaming or reordering an enum still cannot silently corrupt seed data — `coerceInputValues`
 * only papers over genuinely-unknown names, which surface as unexpected default values.
 *
 * The id is a stable slug (e.g. `"barbell-full-squat"`) so seeding is deterministic across
 * installs and tests can assert against a known catalog. [incrementTiers] are optional per entry;
 * when absent the seeder supplies a sensible default. Tiers use the lift's unit (lb for seed).
 *
 * [imageSlug] (OS-4b) carries the source dataset's directory id (e.g. `"Barbell_Full_Squat"`)
 * so a future image-display slice can construct lazy-load URLs against the Cloudflare R2
 * mirror. It is **not** persisted by the seeder — forward-looking metadata consumed at display
 * time, not seed time.
 *
 * [equipment] is an optional display string (e.g. "Hammer Strength") — display copy only, not
 * an equipment entity graph. Null on most seed lifts; the seeder passes it through to the entity.
 */
@Serializable
data class LiftSeedDto(
    val id: String,
    val name: String,
    val movementPatterns: List<MovementPattern> = emptyList(),
    val primaryMuscles: List<MuscleGroup> = emptyList(),
    val secondaryMuscles: List<MuscleGroup> = emptyList(),
    val incrementTiers: List<IncrementTierSeedDto>? = null,
    val presetTags: List<String> = emptyList(),
    val equipment: String? = null,
    val imageSlug: String? = null,
    val description: String? = null,
    val imageUris: List<String> = emptyList(),
    val muscleLengthBias: MuscleLengthBias? = null,
    val mainstream: Boolean = false,
    val kinematicProfile: KinematicProfile? = null,
    /**
     * Loading modality (Weighted / Bodyweight / Assisted). Null defaults to
     * [LoadType.Weighted] in [LiftCatalogSeeder.mapDto] so the bulk of the catalog — which
     * is weighted — can omit the field. Bodyweight entries (pull-ups, dips, push-ups) and
     * assisted entries (assisted pull-up, assisted dip) set this explicitly.
     */
    val loadType: LoadType? = null,
)

@Serializable
data class IncrementTierSeedDto(
    @SerialName("lowerBound") val lowerBound: Double,
    @SerialName("upperBound") val upperBound: Double? = null,
    @SerialName("increment") val increment: Double,
)
