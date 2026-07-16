package com.stratum.domain.lift

import com.stratum.domain.lift.identity.EquipmentId

/**
 * Editable, non-derived catalog fields for a user-created lift. Category is intentionally
 * omitted — it is derived via [classifyLiftCategory] from patterns, fatigue profile, and
 * primary muscles.
 */
data class CustomLiftDetails(
    val movementPatterns: Set<MovementPattern>,
    val muscleLengthBias: MuscleLengthBias,
    val fatigueProfile: FatigueProfile,
    val loadType: LoadType,
    val equipmentId: EquipmentId?,
)
