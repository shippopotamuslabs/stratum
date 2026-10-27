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
     * major-lattice points plus a selected stop (see [IncrementLattice]).
     */
    val microIncrements: List<Weight> = emptyList(),
    /**
     * True once the user deletes this equipment. The row, name, and tiers are retained so
     * history keeps resolving; catalog and picker surfaces exclude tombstoned rows while
     * by-id lookups still return them.
     */
    val isDeleted: Boolean = false,
) {
    /**
     * The validated lattice these tiers and micro stops form (STR-165).
     *
     * Every write path constructs the lattice before persisting, so stored rows are
     * lattices. This is an explicit fail-fast function, not a property: a row whose
     * stops are carried against replacement tiers (the fork destination-tier
     * hand-off in `ForkEquipmentUseCase`, where the destination write validates
     * them) throws here until that write lands — the model owns that meaning in
     * one place instead of each consumer re-deciding it.
     */
    fun requireLattice(): IncrementLattice = IncrementLattice(incrementTiers, microIncrements)

    /**
     * Fork semantics, owned by the model (STR-169): a fork (clone) of this equipment
     * starts with this row's adder stops unless the caller passes an explicit
     * replacement — null inherits the parent's stops, a non-null list (empty = no
     * knob) replaces them. The destination write validates replacement stops
     * against the new tiers; see `ForkEquipmentUseCase`.
     */
    fun resolvedForkMicro(explicit: List<Weight>?): List<Weight> = explicit ?: microIncrements

    /**
     * Tombstone meaning, named once (STR-169): the equipment was deleted, its row,
     * name, and tiers are retained so history keeps resolving it by id, and
     * catalog/picker reads exclude it. Call sites check this instead
     * of re-reading the raw [isDeleted] flag.
     */
    val isTombstoned: Boolean
        get() = isDeleted

}

/**
 * The single open band repositories persist when custom equipment is created without
 * tiers. Single source of truth for the write-default so validation (which must run
 * against the persisted context) and every [EquipmentRepository] implementation agree.
 */
fun defaultSingleTiers(): List<IncrementTier> {
    val lb = Weight.WeightUnit.Lb
    return listOf(IncrementTier(Weight(0.0, lb), null, Weight(DEFAULT_INCREMENT_LB, lb)))
}

/** Default major step (lb) of [defaultSingleTiers]. */
const val DEFAULT_INCREMENT_LB = 5.0
