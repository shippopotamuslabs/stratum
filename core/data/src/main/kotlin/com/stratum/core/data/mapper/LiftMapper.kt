package com.stratum.core.data.mapper

import com.stratum.core.data.db.IncrementTierEntity
import com.stratum.core.data.db.LiftEntity
import com.stratum.core.data.db.LiftWithTiers
import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup

/**
 * Maps [LiftWithTiers] (and the [LiftEntity]/[IncrementTierEntity] it composes) to/from the
 * domain [Lift].
 *
 * The four non-trivial encodings live here:
 * - **Movement-pattern bitmask** ↔ `Set<MovementPattern>`: bit `1 shl MovementPattern.ordinal`.
 *   Stored as `movementPatternsBitmask` so a SQL bitwise AND can filter the set in the database
 *   (`movementPatternsBitmask & :bit != 0`) rather than loading rows to filter in Kotlin. The
 *   bit position couples the on-disk format to the enum order; acceptable because the values
 *   are grouped for display and never reordered once released.
 * - **Muscle bitmask** ↔ `Set<MuscleGroup>`: bit `1 shl MuscleGroup.ordinal`. [MuscleGroup]
 *   deliberately carries no bitmask of its own (its KDoc is locked), so the bit position is
 *   derived from the stable enum ordinal. This couples the on-disk format to the enum order,
 *   which is acceptable because [MuscleGroup] is documented locked-for-onboarding-parity.
 * - **Increment tiers** ↔ entity rows: amounts are stored as plain `Double` in the lift's unit
 *   (lb for seed); the mapper re-wraps them into [Weight] of that unit.
 * - **Preset tags** ↔ `Set<GymPreset>`: stored as a delimiter-bounded CSV `,CommercialGym,GarageGym,`
 *   so a SQL `LIKE '%,CommercialGym,%'` matches reliably regardless of position. Empty set ==
 *   the bare delimiters `,`.
 *
 * The typed [LiftId] passes straight through (`id = id`) — the converter is the only String
 * boundary, so this mapper never touches `.value`.
 */
fun LiftWithTiers.toDomain(): Lift = Lift(
    id = lift.id,
    name = lift.name,
    movementPatterns = lift.movementPatternsBitmask.toMovementPatternSet(),
    primaryMuscles = lift.primaryMusclesBitmask.toMuscleSet(),
    secondaryMuscles = lift.secondaryMusclesBitmask.toMuscleSet(),
    incrementTiers = tiers.toDomainTiers(),
    presetTags = lift.presetTags.toPresetSet(),
    equipment = lift.equipment,
    enabled = lift.enabled,
    isCustom = lift.isCustom,
    description = lift.description,
    imageUris = lift.imageUrisCsv?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
    muscleLengthBias = lift.muscleLengthBias,
    mainstream = lift.mainstream,
    kinematicProfile = lift.kinematicProfile,
    loadType = lift.loadType,
)

fun Lift.toEntity(): LiftEntity = LiftEntity(
    id = id,
    name = name,
    movementPatternsBitmask = movementPatterns.toBitmask(),
    primaryMusclesBitmask = primaryMuscles.toBitmask(),
    secondaryMusclesBitmask = secondaryMuscles.toBitmask(),
    presetTags = presetTags.toPresetCsv(),
    equipment = equipment,
    isCustom = isCustom,
    enabled = enabled,
    description = description,
    imageUrisCsv = imageUris.joinToString(","),
    muscleLengthBias = muscleLengthBias,
    mainstream = mainstream,
    kinematicProfile = kinematicProfile,
    loadType = loadType,
)

fun Lift.toTierEntities(): List<IncrementTierEntity> = incrementTiers.map { tier ->
    IncrementTierEntity(
        liftId = id,
        lowerBound = tier.lowerBound.amount,
        upperBound = tier.upperBound?.amount,
        increment = tier.increment.amount,
    )
}

fun List<IncrementTierEntity>.toDomainTiers(): List<IncrementTier> =
    sortedBy { it.lowerBound }.map { entity ->
        IncrementTier(
            lowerBound = Weight(entity.lowerBound, DEFAULT_LIFT_UNIT),
            upperBound = entity.upperBound?.let { Weight(it, DEFAULT_LIFT_UNIT) },
            increment = Weight(entity.increment, DEFAULT_LIFT_UNIT),
        )
    }

/** Decode a movement-pattern bitmask into the matching [MovementPattern] set (by ordinal bit position). */
fun Int.toMovementPatternSet(): Set<MovementPattern> =
    MovementPattern.entries.filter { this and (1 shl it.ordinal) != 0 }.toSet()

/** Encode a [MovementPattern] set into a bitmask (`1 shl ordinal`). */
@JvmName("toMovementPatternBitmask")
fun Set<MovementPattern>.toBitmask(): Int =
    fold(0) { mask, pattern -> mask or (1 shl pattern.ordinal) }

/** Decode a muscle bitmask into the matching [MuscleGroup] set (by ordinal bit position). */
fun Int.toMuscleSet(): Set<MuscleGroup> = MuscleGroup.entries.filter { this and (1 shl it.ordinal) != 0 }.toSet()

/** Encode a [MuscleGroup] set into a bitmask (1 shl ordinal). */
fun Set<MuscleGroup>.toBitmask(): Int = fold(0) { mask, muscle -> mask or (1 shl muscle.ordinal) }

/**
 * Decode a preset-tag column into a [GymPreset] set. The column is a delimiter-bounded CSV
 * (`,CommercialGym,GarageGym,`), so splitting on [PRESET_DELIMITER] and dropping blanks
 * recovers the names regardless of leading/trailing delimiters.
 */
fun String.toPresetSet(): Set<GymPreset> =
    split(PRESET_DELIMITER)
        .filter { it.isNotBlank() }
        .mapNotNull { name -> runCatching { GymPreset.valueOf(name) }.getOrNull() }
        .toSet()

/**
 * Encode a [GymPreset] set into a delimiter-bounded CSV (`,CommercialGym,GarageGym,`). An empty
 * set encodes to the bare delimiters `,` so a SQL `LIKE '%,X,%'` never false-matches it. The
 * leading/trailing delimiters make the query positional-safe for non-empty sets.
 */
fun Set<GymPreset>.toPresetCsv(): String =
    if (isEmpty()) PRESET_DELIMITER
    else PRESET_DELIMITER + joinToString(PRESET_DELIMITER) { it.name } + PRESET_DELIMITER

private const val PRESET_DELIMITER = ","
private val DEFAULT_LIFT_UNIT = Weight.WeightUnit.Lb
