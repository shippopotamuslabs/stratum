package com.stratum.core.data.mapper

import com.stratum.core.data.db.EquipmentEntity
import com.stratum.core.data.db.IncrementTierEntity
import com.stratum.core.data.db.LiftEntity
import com.stratum.core.data.db.LiftWithTiers
import com.stratum.core.data.seed.LiftCatalogSeeder
import com.stratum.core.data.sync.SyncFlags
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.IncrementTierId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.resolveEffectiveIncrementTiers
import com.stratum.domain.onboarding.MuscleGroup

/**
 * Maps [LiftWithTiers] (and the [LiftEntity]/[IncrementTierEntity] it composes) to/from the
 * domain [Lift], resolving effective increment tiers from lift overrides + equipment defaults.
 */
fun LiftWithTiers.toDomain(
    equipmentById: Map<EquipmentId, Equipment> = emptyMap(),
    fallbackTiers: List<IncrementTier> = DEFAULT_FALLBACK_TIERS,
): Lift {
    val equipment = lift.equipmentId?.let { equipmentById[it] }
    val effective =
        resolveEffectiveIncrementTiers(
            liftOverrideTiers = tiers.toDomainTiers(),
            equipmentTiers = equipment?.incrementTiers,
            fallbackTiers = fallbackTiers,
        )
    return Lift(
        id = lift.id,
        name = lift.name,
        movementPatterns = lift.movementPatternsBitmask.toMovementPatternSet(),
        primaryMuscles = lift.primaryMusclesBitmask.toMuscleSet(),
        secondaryMuscles = lift.secondaryMusclesBitmask.toMuscleSet(),
        incrementTiers = effective.tiers,
        presetTags = lift.presetTags.toPresetSet(),
        equipmentId = lift.equipmentId,
        equipmentName = equipment?.name,
        usesEquipmentDefaultIncrements = effective.usesEquipmentDefaultIncrements,
        inheritsFromEquipmentLattice = effective.inheritsFromEquipmentLattice,
        enabled = lift.enabled,
        isCustom = lift.isCustom,
        description = lift.description,
        imageUris = lift.imageUrisCsv?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
        muscleLengthBias = lift.muscleLengthBias,
        mainstream = lift.mainstream,
        kinematicProfile = lift.kinematicProfile,
        loadType = lift.loadType,
        defaultRestSeconds = lift.defaultRestSeconds,
    )
}

/** Local user write — marks lift dirty for cloud upload. */
fun Lift.toEntity(
    nowMillis: Long = System.currentTimeMillis(),
): LiftEntity = LiftEntity(
    id = id,
    name = name,
    movementPatternsBitmask = movementPatterns.toBitmask(),
    primaryMusclesBitmask = primaryMuscles.toBitmask(),
    secondaryMusclesBitmask = secondaryMuscles.toBitmask(),
    presetTags = presetTags.toPresetCsv(),
    equipmentId = equipmentId,
    isCustom = isCustom,
    enabled = enabled,
    description = description,
    imageUrisCsv = imageUris.joinToString(","),
    muscleLengthBias = muscleLengthBias,
    mainstream = mainstream,
    kinematicProfile = kinematicProfile,
    loadType = loadType,
    defaultRestSeconds = defaultRestSeconds,
    updatedAtEpochMillis = nowMillis,
    pendingSync = SyncFlags.PENDING_UPSERT,
)

/**
 * Lift-scoped **override** tier entities only. Callers must not write equipment defaults as
 * lift rows when [Lift.usesEquipmentDefaultIncrements] is true.
 */
fun Lift.toLiftOverrideTiers(
    nowMillis: Long = System.currentTimeMillis(),
    pendingSync: Int = SyncFlags.PENDING_UPSERT,
): List<IncrementTierEntity> {
    if (usesEquipmentDefaultIncrements) return emptyList()
    return incrementTiers.map { tier ->
        IncrementTierEntity(
            id = IncrementTierId.forBand(id, tier.lowerBound.amount),
            liftId = id,
            equipmentId = null,
            lowerBound = tier.lowerBound.amount,
            upperBound = tier.upperBound?.amount,
            increment = tier.increment.amount,
            updatedAtEpochMillis = nowMillis,
            pendingSync = pendingSync,
        )
    }
}

fun EquipmentEntity.toDomain(tiers: List<IncrementTierEntity>): Equipment =
    Equipment(
        id = id,
        name = name,
        incrementTiers = tiers.toDomainTiers(),
        isCustom = isCustom,
    )

fun Equipment.toEntity(
    nowMillis: Long = System.currentTimeMillis(),
    pendingSync: Int = SyncFlags.PENDING_UPSERT,
): EquipmentEntity =
    EquipmentEntity(
        id = id,
        name = name,
        isCustom = isCustom,
        updatedAtEpochMillis = nowMillis,
        pendingSync = pendingSync,
    )

fun Equipment.toTierEntities(
    nowMillis: Long = System.currentTimeMillis(),
    pendingSync: Int = SyncFlags.PENDING_UPSERT,
): List<IncrementTierEntity> =
    incrementTiers.map { tier ->
        IncrementTierEntity(
            id = IncrementTierId.forEquipmentBand(id, tier.lowerBound.amount),
            liftId = null,
            equipmentId = id,
            lowerBound = tier.lowerBound.amount,
            upperBound = tier.upperBound?.amount,
            increment = tier.increment.amount,
            updatedAtEpochMillis = nowMillis,
            pendingSync = pendingSync,
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

private val DEFAULT_FALLBACK_TIERS: List<IncrementTier> =
    LiftCatalogSeeder.DEFAULT_TIERS.map { t ->
        IncrementTier(
            lowerBound = Weight(t.lowerBound, DEFAULT_LIFT_UNIT),
            upperBound = t.upperBound?.let { Weight(it, DEFAULT_LIFT_UNIT) },
            increment = Weight(t.increment, DEFAULT_LIFT_UNIT),
        )
    }
