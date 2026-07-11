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
)
