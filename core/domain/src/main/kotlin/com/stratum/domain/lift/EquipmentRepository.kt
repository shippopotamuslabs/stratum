package com.stratum.domain.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import kotlinx.coroutines.flow.Flow

/**
 * Port for the equipment catalog and its default increment tiers.
 *
 * Implemented in `:core:data` (Room, seeded from a bundled asset) and faked in `:core:testing`.
 * Domain and feature code reach this via use cases, never directly from a ViewModel
 * (ARCHITECTURE.md §4.4).
 */
interface EquipmentRepository {
    /**
     * Hot stream of every equipment row with its default increment tiers.
     * Tombstoned rows are excluded; use [getEquipment] for by-id history resolution.
     */
    fun observeEquipment(): Flow<List<Equipment>>

    /**
     * Snapshot lookup by id, or null when missing. Tombstoned rows are still
     * returned so history, PR, and progression displays keep resolving names.
     */
    suspend fun getEquipment(id: EquipmentId): Equipment?

    /**
     * Creates a user-defined equipment row. When [tiers] is empty, a single open-ended default
     * band is applied (5 lb). [micro] seeds the adder stops (empty = no adder).
     * Returns the new id.
     */
    suspend fun addCustomEquipment(
        name: String,
        tiers: List<IncrementTier> = emptyList(),
        micro: List<Weight> = emptyList(),
    ): EquipmentId

    /**
     * Creates custom equipment with [tiers] and assigns each lift in [liftIds] to it in one
     * atomic write (same sync lock + Room transaction in the data layer).
     *
     * Assignment clears any lift-scoped increment override (bands and micro) so the new
     * equipment defaults apply. Used by fork remedies so a crash cannot leave orphan
     * custom equipment without assignments (or assigned lifts without equipment).
     */
    suspend fun createCustomEquipmentAndAssignLifts(
        name: String,
        tiers: List<IncrementTier>,
        liftIds: List<LiftId>,
    ): EquipmentId

    /** Renames equipment. No-op when missing or tombstoned. */
    suspend fun renameEquipment(id: EquipmentId, name: String)

    /**
     * Replaces the full default tier list for [id] (delete + insert) and its adder
     * stops. Empty list clears equipment defaults so lifts without overrides fall
     * through to global fallback. Null [micro] preserves the stored stops so
     * tiers-only callers cannot silently wipe the adder; non-null replaces them
     * (empty means explicitly no adder). No-op when the row is missing or tombstoned.
     */
    suspend fun setIncrementTiers(
        id: EquipmentId,
        tiers: List<IncrementTier>,
        micro: List<Weight>? = null,
    )

    /**
     * Renames equipment and replaces its default tiers and adder stops in one
     * atomic write (same sync lock + Room transaction in the data layer), so an
     * editor save can never land the name without the tiers or vice versa.
     * No-op when the row is missing or tombstoned.
     */
    suspend fun renameAndSetIncrementTiers(
        id: EquipmentId,
        name: String,
        tiers: List<IncrementTier>,
        micro: List<Weight>,
    )

    /**
     * Tombstones equipment: sets the deleted flag and syncs it; the row, name,
     * and tiers are retained so history keeps resolving. Every visible seed row
     * and every custom row is deletable; only the hidden system rows
     * ([SeedEquipmentIds.hiddenFromCatalog]) and missing rows return false.
     *
     * Live pointers (catalog lift links, template slot assignments) are
     * reassigned to [replacementId] in the same atomic write; when pointers
     * exist, no replacement was supplied, and live candidates exist, the delete
     * fails and changes nothing. When no candidate exists, pointers fall back
     * to [SeedEquipmentIds.Bodyweight]. Logged sets and history are never
     * rewritten. Idempotent: re-deleting returns true and sweeps nothing.
     */
    suspend fun deleteCustomEquipment(id: EquipmentId, replacementId: EquipmentId? = null): Boolean

    /**
     * Finds equipment for a free-text label, or creates a custom row with that display name
     * and default single-band tiers. Used by import / free-text paths.
     *
     * Match order (implementations):
     * 1. Exact case-insensitive display name
     * 2. Normalized name match (so `"cable"` hits seed `"Cables"`)
     * 3. Known free-text / import aliases onto seed ids
     * 4. Create custom equipment
     *
     * Data-layer call sites that also write other user-owned rows (e.g. custom lifts) should
     * resolve equipment inside the same write lock / Room transaction as those rows so a
     * crash cannot leave orphan custom equipment.
     *
     * Tombstoned rows never match: a name that only matches tombstones creates a new row.
     */
    suspend fun findOrCreateByName(name: String): EquipmentId
}
