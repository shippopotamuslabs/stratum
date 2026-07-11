package com.stratum.core.data.seed

import kotlinx.serialization.Serializable

/**
 * One entry in `seed-equipment.json`. [incrementTiers] may be empty (e.g. bodyweight) or omit
 * upperBound on the final open-ended band.
 */
@Serializable
data class EquipmentSeedDto(
    val id: String,
    val name: String,
    val incrementTiers: List<IncrementTierSeedDto> = emptyList(),
)
