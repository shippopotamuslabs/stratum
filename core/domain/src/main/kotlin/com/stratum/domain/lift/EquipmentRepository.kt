package com.stratum.domain.lift

import com.stratum.domain.lift.identity.EquipmentId
import kotlinx.coroutines.flow.Flow

/**
 * Port for the equipment catalog and its default increment tiers.
 *
 * Implemented in `:core:data` (Room, seeded from a bundled asset) and faked in `:core:testing`.
 * Domain and feature code reach this via use cases, never directly from a ViewModel
 * (ARCHITECTURE.md §4.4).
 */
interface EquipmentRepository {
    /** Hot stream of every equipment row with its default increment tiers. */
    fun observeEquipment(): Flow<List<Equipment>>

    /** Snapshot lookup by id, or null when missing. */
    suspend fun getEquipment(id: EquipmentId): Equipment?

    /**
     * Creates a user-defined equipment row. When [tiers] is empty, a single open-ended default
     * band is applied (5 lb). Returns the new id.
     */
    suspend fun addCustomEquipment(name: String, tiers: List<IncrementTier> = emptyList()): EquipmentId

    /** Renames equipment. No-op when missing. */
    suspend fun renameEquipment(id: EquipmentId, name: String)

    /**
     * Replaces the full default tier list for [id] (delete + insert). Empty list clears
     * equipment defaults so lifts without overrides fall through to global fallback.
     */
    suspend fun setIncrementTiers(id: EquipmentId, tiers: List<IncrementTier>)

    /**
     * Deletes custom equipment when no lifts reference it. Returns false when the row is
     * seed equipment, missing, or still referenced.
     */
    suspend fun deleteCustomEquipment(id: EquipmentId): Boolean

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
     */
    suspend fun findOrCreateByName(name: String): EquipmentId
}
