package com.stratum.domain.lift

import com.stratum.domain.onboarding.MuscleGroup

/**
 * Metadata for a user-defined lift created during import or manual catalog edits.
 */
data class CustomLiftDefinition(
    val name: String,
    val movementPatterns: Set<MovementPattern>,
    val primaryMuscles: Set<MuscleGroup>,
    val secondaryMuscles: Set<MuscleGroup> = emptySet(),
    val equipment: String? = null,
    val increment: Weight? = null,
)
