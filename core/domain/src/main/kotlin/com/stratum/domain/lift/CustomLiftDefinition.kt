package com.stratum.domain.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.onboarding.MuscleGroup

/**
 * Metadata for a user-defined lift created during import or manual catalog edits.
 *
 * Category is intentionally omitted — it is derived via [classifyLiftCategory] from patterns,
 * kinematic profile, and primary muscles. Defaults for bias / kinematic / load type match
 * [Lift] construction defaults.
 *
 * Prefer [equipmentId] when known (seed loading methods from import). [equipmentName] is
 * free-text for custom / manual paths; the repository finds or creates equipment by name when
 * id is null. [equipmentCreateIncrement] is used only when minting new equipment from
 * [equipmentName] (single open band) — it is not a lift-scoped override ([increment] is).
 */
data class CustomLiftDefinition(
    val name: String,
    val movementPatterns: Set<MovementPattern>,
    val primaryMuscles: Set<MuscleGroup>,
    val secondaryMuscles: Set<MuscleGroup> = emptySet(),
    val equipmentId: EquipmentId? = null,
    val equipmentName: String? = null,
    /**
     * When non-null with [equipmentName], new custom equipment is created with a single
     * open-ended tier at this increment. Ignored when linking an existing equipment row.
     */
    val equipmentCreateIncrement: Weight? = null,
    val increment: Weight? = null,
    val muscleLengthBias: MuscleLengthBias = MuscleLengthBias.MidRange,
    val kinematicProfile: KinematicProfile = KinematicProfile.Isolation,
    val loadType: LoadType = LoadType.Weighted,
    val defaultRestSeconds: Int? = null,
)
