package com.stratum.core.data.seed

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One entry in the bundled seed catalog (`src/main/assets/lifts/seed-lifts.json`). Decoded by
 * [LiftCatalogSeeder] into catalog rows on first database creation.
 *
 * The asset uses **readable names**, never ordinals:
 * - [primaryMuscles]/[secondaryMuscles] are `MuscleGroup.name` strings (e.g. `"Chest"`).
 *   Renaming or reordering the enum cannot silently corrupt seed data — an unknown name
 *   fails loudly at seed time rather than decoding to the wrong muscle.
 * - [movementPattern] is a `MovementPattern.name` string (e.g. `"SquatPattern"`).
 * - [presetTags] are `GymPreset.name` strings.
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
    val movementPatterns: List<String> = emptyList(),
    val primaryMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList(),
    val allowWeightChangeBetweenSets: Boolean = false,
    val incrementTiers: List<IncrementTierSeedDto>? = null,
    val presetTags: List<String> = emptyList(),
    val equipment: String? = null,
    val imageSlug: String? = null,
    val description: String? = null,
    val imageUris: List<String> = emptyList(),
    val muscleLengthBias: String? = null,
)

@Serializable
data class IncrementTierSeedDto(
    @SerialName("lowerBound") val lowerBound: Double,
    @SerialName("upperBound") val upperBound: Double? = null,
    @SerialName("increment") val increment: Double,
)
