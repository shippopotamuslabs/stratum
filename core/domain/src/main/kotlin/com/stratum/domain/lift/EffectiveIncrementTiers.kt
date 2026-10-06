package com.stratum.domain.lift

/**
 * Resolves the increment tiers a lift should use for progression and UI.
 *
 * Precedence:
 * 1. Non-empty [equipmentTiers] — defaults from the lift's equipment
 * 2. [fallbackTiers] — last-resort catalog defaults (may be empty)
 *
 * Per-lift tier overrides were removed (STR-82/84): every lift inherits its equipment's
 * lattice or the global fallback.
 *
 * [inheritsFromEquipmentLattice] is true only when non-empty equipment tiers supplied the
 * list. Empty lattices (e.g. seed bodyweight) fall through to global fallback and must **not**
 * be presented as "Using defaults from {equipment name}".
 *
 * Micro (adder) resolution: the knob is physical, so a lift always inherits the linked
 * equipment's stops. Per-lift micro overrides were removed in STR-82/84 (no writers remain;
 * the 7→8 migration folded them into "<lift name> Stack" equipment clones), so there is no
 * inherit-vs-clear distinction left — the equipment's stops are the effective stops.
 */
data class EffectiveIncrementTiers(
    val tiers: List<IncrementTier>,
    val inheritsFromEquipmentLattice: Boolean = false,
    /** Effective adder stops: the linked equipment's stops, else empty (no adder). */
    val microIncrements: List<Weight> = emptyList(),
)

fun resolveEffectiveIncrementTiers(
    equipmentTiers: List<IncrementTier>?,
    fallbackTiers: List<IncrementTier> = emptyList(),
    equipmentMicro: List<Weight> = emptyList(),
): EffectiveIncrementTiers {
    val fromEquipment = equipmentTiers.orEmpty()
    return if (fromEquipment.isNotEmpty()) {
        EffectiveIncrementTiers(
            tiers = fromEquipment,
            inheritsFromEquipmentLattice = true,
            microIncrements = equipmentMicro,
        )
    } else {
        EffectiveIncrementTiers(
            tiers = fallbackTiers,
            inheritsFromEquipmentLattice = false,
            microIncrements = equipmentMicro,
        )
    }
}
