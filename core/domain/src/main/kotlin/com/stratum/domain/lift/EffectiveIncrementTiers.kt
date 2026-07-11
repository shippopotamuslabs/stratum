package com.stratum.domain.lift

/**
 * Resolves the increment tiers a lift should use for progression and UI.
 *
 * Precedence:
 * 1. Non-empty [liftOverrideTiers] — explicit per-lift override
 * 2. Non-empty [equipmentTiers] — defaults from the lift's equipment
 * 3. [fallbackTiers] — last-resort catalog defaults (may be empty)
 *
 * [usesEquipmentDefaultIncrements] is true only when no lift override rows are present
 * (equipment or fallback may still supply the effective list).
 */
data class EffectiveIncrementTiers(
    val tiers: List<IncrementTier>,
    val usesEquipmentDefaultIncrements: Boolean,
)

fun resolveEffectiveIncrementTiers(
    liftOverrideTiers: List<IncrementTier>,
    equipmentTiers: List<IncrementTier>?,
    fallbackTiers: List<IncrementTier> = emptyList(),
): EffectiveIncrementTiers {
    if (liftOverrideTiers.isNotEmpty()) {
        return EffectiveIncrementTiers(
            tiers = liftOverrideTiers,
            usesEquipmentDefaultIncrements = false,
        )
    }
    val fromEquipment = equipmentTiers.orEmpty()
    if (fromEquipment.isNotEmpty()) {
        return EffectiveIncrementTiers(
            tiers = fromEquipment,
            usesEquipmentDefaultIncrements = true,
        )
    }
    return EffectiveIncrementTiers(
        tiers = fallbackTiers,
        usesEquipmentDefaultIncrements = true,
    )
}
