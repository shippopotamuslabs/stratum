package com.stratum.domain.lift

import com.stratum.domain.onboarding.MuscleGroup

/**
 * Metadata for a user-defined lift created during import or manual catalog edits.
 *
 * Category is intentionally omitted — it is derived via [classifyLiftCategory] from patterns,
 * kinematic profile, and primary muscles. Defaults for bias / kinematic / load type match
 * [Lift] construction defaults.
 */
data class CustomLiftDefinition(
    val name: String,
    val movementPatterns: Set<MovementPattern>,
    val primaryMuscles: Set<MuscleGroup>,
    val secondaryMuscles: Set<MuscleGroup> = emptySet(),
    val equipment: String? = null,
    val increment: Weight? = null,
    val muscleLengthBias: MuscleLengthBias = MuscleLengthBias.MidRange,
    val kinematicProfile: KinematicProfile = KinematicProfile.Isolation,
    val loadType: LoadType = LoadType.Weighted,
    val defaultRestSeconds: Int? = null,
)
