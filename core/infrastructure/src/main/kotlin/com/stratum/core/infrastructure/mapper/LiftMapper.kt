package com.stratum.core.infrastructure.mapper

import com.stratum.core.infrastructure.db.EquipmentEntity
import com.stratum.core.infrastructure.db.IncrementTierEntity
import com.stratum.core.infrastructure.db.LiftEntity
import com.stratum.core.infrastructure.seed.LiftCatalogSeeder
import com.stratum.core.infrastructure.sync.SyncFlags
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.WeightConversion
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.IncrementTierId
import com.stratum.domain.lift.resolveEffectiveIncrementTiers
import com.stratum.domain.onboarding.MuscleGroup
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Maps [LiftEntity] to the domain [Lift], resolving effective increment tiers from the
 * linked equipment's defaults (per-lift overrides were removed in STR-82/84).
 */
fun LiftEntity.toDomain(
    equipmentById: Map<EquipmentId, Equipment> = emptyMap(),
    fallbackTiers: List<IncrementTier> = DEFAULT_FALLBACK_TIERS,
): Lift {
    val equipment = equipmentId?.let { equipmentById[it] }
    val effective =
        resolveEffectiveIncrementTiers(
            equipmentTiers = equipment?.incrementTiers,
            fallbackTiers = fallbackTiers,
            equipmentMicro = equipment?.microIncrements.orEmpty(),
        )
    return Lift(
        id = id,
        name = name,
        movementPatterns = movementPatternsBitmask.toMovementPatternSet(),
        primaryMuscles = primaryMusclesBitmask.toMuscleSet(),
        secondaryMuscles = secondaryMusclesBitmask.toMuscleSet(),
        incrementTiers = effective.tiers,
        presetTags = presetTags.toPresetSet(),
        equipmentId = equipmentId,
        equipmentName = equipment?.name,
        inheritsFromEquipmentLattice = effective.inheritsFromEquipmentLattice,
        microIncrements = effective.microIncrements,
        enabled = enabled,
        isCustom = isCustom,
        description = description,
        imageUris = imageUrisCsv?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
        muscleLengthBias = muscleLengthBias,
        mainstream = mainstream,
        fatigueProfile = fatigueProfile,
        loadType = loadType,
        defaultRestSeconds = defaultRestSeconds,
        pinnedNote = pinnedNote,
        isDeleted = isDeleted,
    )
}

/**
 * Local user write — marks lift dirty for cloud upload. The lift `micro_options_json`
 * column is vestigial (per-lift micro overrides were removed in STR-82/84): it is always
 * written null; the effective adder stops come from the linked equipment.
 */
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
    microOptionsJson = null,
    isCustom = isCustom,
    enabled = enabled,
    description = description,
    imageUrisCsv = imageUris.joinToString(","),
    muscleLengthBias = muscleLengthBias,
    mainstream = mainstream,
    fatigueProfile = fatigueProfile,
    loadType = loadType,
    defaultRestSeconds = defaultRestSeconds,
    pinnedNote = pinnedNote,
    updatedAtEpochMillis = nowMillis,
    pendingSync = SyncFlags.PENDING_UPSERT,
    isDeleted = isDeleted,
)

fun EquipmentEntity.toDomain(tiers: List<IncrementTierEntity>): Equipment =
    Equipment(
        id = id,
        name = name,
        incrementTiers = tiers.toDomainTiers(),
        isCustom = isCustom,
        microIncrements = microOptionsJson.toMicroIncrements(),
        isDeleted = isDeleted,
    )

fun Equipment.toEntity(
    nowMillis: Long = System.currentTimeMillis(),
    pendingSync: Int = SyncFlags.PENDING_UPSERT,
): EquipmentEntity =
    EquipmentEntity(
        id = id,
        name = name,
        isCustom = isCustom,
        microOptionsJson = microIncrements.toMicroJsonOrNull(),
        updatedAtEpochMillis = nowMillis,
        pendingSync = pendingSync,
        isDeleted = isDeleted,
    )

fun Equipment.toTierEntities(
    nowMillis: Long = System.currentTimeMillis(),
    pendingSync: Int = SyncFlags.PENDING_UPSERT,
): List<IncrementTierEntity> =
    incrementTiers.map { tier ->
        IncrementTierEntity(
            id = IncrementTierId.forEquipmentBand(id, tier.lowerBound.amount),
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

private val MICRO_JSON = Json { ignoreUnknownKeys = true }

/**
 * Parses an adder-stop JSON payload (`micro_options_json` grammar, owned here);
 * null on corrupt input so callers can fail open. `"[]"` parses to empty.
 */
fun String.parseMicroJson(): List<Double>? =
    runCatching { MICRO_JSON.decodeFromString(ListSerializer(Double.serializer()), this.trim()) }
        .getOrNull()

/**
 * Parses an adder-stop column (`micro_options_json`) into storage-[Weight] stops.
 * Null (pre-v7 rows) and corrupt payloads decode to empty (no adder) so old rows
 * and forward-skewed payloads never break catalog reads.
 */
private fun String?.toMicroIncrements(): List<Weight> =
    this?.parseMicroJson()?.map { Weight(it, DEFAULT_LIFT_UNIT) }.orEmpty()

/**
 * Encodes raw storage-lb doubles as the canonical `micro_options_json` array grammar.
 * Plain-doubles entry point so callers without domain weights (seed, sync) share the
 * one grammar instead of hand-rolling brackets. No empty handling here — callers own
 * their null-vs-empty contract explicitly.
 */
fun List<Double>.toMicroJsonArray(): String =
    MICRO_JSON.encodeToString(ListSerializer(Double.serializer()), this)

/**
 * Encodes adder stops as a JSON array of storage-lb doubles. Empty encodes to null
 * (no adder) so equipment rows stay null-clean. Internal so repositories can write
 * the column without round-tripping through domain mapping.
 */
fun List<Weight>.toMicroJsonOrNull(): String? {
    if (isEmpty()) return null
    return map { WeightConversion.convertAmount(it.amount, it.unit, Weight.WeightUnit.Lb) }
        .toMicroJsonArray()
}

private val DEFAULT_FALLBACK_TIERS: List<IncrementTier> =
    LiftCatalogSeeder.DEFAULT_TIERS.map { t ->
        IncrementTier(
            lowerBound = Weight(t.lowerBound, DEFAULT_LIFT_UNIT),
            upperBound = t.upperBound?.let { Weight(it, DEFAULT_LIFT_UNIT) },
            increment = Weight(t.increment, DEFAULT_LIFT_UNIT),
        )
    }
