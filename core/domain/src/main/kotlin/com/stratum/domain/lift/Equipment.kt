package com.stratum.domain.lift

import com.stratum.domain.lift.identity.EquipmentId

/**
 * Shared equipment used to default weight-increment bands across many lifts.
 *
 * Equipment is intentionally narrow: a stable name plus default [incrementTiers]. It is not an
 * availability or programming graph — lifts remain the primary catalog entity. Import-time
 * [com.stratum.domain.importing.GymEquipment] tags stay separate and are not persisted here.
 *
 * Seed rows (`isCustom = false`) ship from `seed-equipment.json`. Custom rows are user-created
 * from Manage Equipment or find-or-create on lift import/create.
 */
data class Equipment(
    val id: EquipmentId,
    val name: String,
    val incrementTiers: List<IncrementTier>,
    val isCustom: Boolean = false,
    /**
     * Micro-adjust (adder knob) stops as storage-pound offsets, non-zero only
     * (e.g. `[5, 10]` displays as `0 / 5 / 10` — knob-off, the bare stack load,
     * is always legal). Empty means the machine has no adder. Legal loads are
     * major-lattice points plus a selected stop (see IncrementLattice.kt).
     */
    val microIncrements: List<Weight> = emptyList(),
)
