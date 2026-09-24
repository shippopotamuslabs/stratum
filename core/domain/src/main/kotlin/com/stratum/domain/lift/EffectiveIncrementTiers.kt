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
 *
 * [inheritsFromEquipmentLattice] is true only when non-empty equipment tiers supplied the
 * list. Empty lattices (e.g. seed bodyweight) fall through to global fallback and must **not**
 * be presented as "Using defaults from {equipment name}".
 *
 * Micro (adder) resolution is **independent** of the tier override: the knob is physical, so a
 * lift with band overrides still inherits the linked equipment's stops unless it carries an
 * explicit lift micro override (null = inherit, non-null including empty = lift-scoped value).
 */
data class EffectiveIncrementTiers(
    val tiers: List<IncrementTier>,
    val usesEquipmentDefaultIncrements: Boolean,
    val inheritsFromEquipmentLattice: Boolean = false,
    /** Effective adder stops: lift override when non-null, else equipment, else empty. */
    val microIncrements: List<Weight> = emptyList(),
    /** True when micro is inherited from equipment (or the empty fallback). */
    val usesEquipmentDefaultMicro: Boolean = true,
)

fun resolveEffectiveIncrementTiers(
    liftOverrideTiers: List<IncrementTier>,
    equipmentTiers: List<IncrementTier>?,
    fallbackTiers: List<IncrementTier> = emptyList(),
    liftMicroOverride: List<Weight>? = null,
    equipmentMicro: List<Weight> = emptyList(),
): EffectiveIncrementTiers {
    val tiersResult =
        if (liftOverrideTiers.isNotEmpty()) {
            EffectiveIncrementTiers(
                tiers = liftOverrideTiers,
                usesEquipmentDefaultIncrements = false,
                inheritsFromEquipmentLattice = false,
            )
        } else {
            val fromEquipment = equipmentTiers.orEmpty()
            if (fromEquipment.isNotEmpty()) {
                EffectiveIncrementTiers(
                    tiers = fromEquipment,
                    usesEquipmentDefaultIncrements = true,
                    inheritsFromEquipmentLattice = true,
                )
            } else {
                EffectiveIncrementTiers(
                    tiers = fallbackTiers,
                    usesEquipmentDefaultIncrements = true,
                    inheritsFromEquipmentLattice = false,
                )
            }
        }
    return tiersResult.copy(
        microIncrements = liftMicroOverride ?: equipmentMicro,
        usesEquipmentDefaultMicro = liftMicroOverride == null,
    )
}
