package com.stratum.core.infrastructure.seed

import kotlinx.serialization.Serializable

/**
 * One entry in `seed-equipment.json`. [incrementTiers] may be empty (e.g. bodyweight) or omit
 * upperBound on the final open-ended band. [microIncrements] holds optional adder-knob
 * stops as storage-lb offsets (non-zero only); shipped seeds keep it empty.
 */
@Serializable
data class EquipmentSeedDto(
    val id: String,
    val name: String,
    val incrementTiers: List<IncrementTierSeedDto> = emptyList(),
    val microIncrements: List<Double> = emptyList(),
)
