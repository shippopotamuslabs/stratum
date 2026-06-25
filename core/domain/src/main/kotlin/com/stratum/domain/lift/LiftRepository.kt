package com.stratum.domain.lift

import com.stratum.domain.lift.identity.LiftId
import kotlinx.coroutines.flow.Flow

/**
 * Port for the lift catalog and this user's availability + preset choice, captured during the
 * Gym & Lifts onboarding step (OS-4) and re-used by program generation (OS-6), voice catalog
 * edits (OS-8), and logging.
 *
 * Implemented in `:core:data` (Room, seeded from a bundled asset) and faked in
 * `:core:testing`. Domain and feature code depend only on this interface — and reach it via
 * use cases, never directly from a ViewModel (ARCHITECTURE.md §4.4).
 *
 * Availability lives on the lift itself: [setLiftEnabled] flips a single lift's `enabled`
 * flag; [applyPreset] enables every lift tagged with the preset. The selected preset is
 * tracked separately ([observeSelectedPreset]) because it is a global onboarding choice, not
 * a per-lift attribute, and OS-5 Review needs to recall it by name.
 */
interface LiftRepository {
    /** Hot stream of every lift in the catalog, each carrying its current `enabled` flag. */
    fun observeLifts(): Flow<List<Lift>>

    /** Hot stream of the preset the user selected in this step, or `null` until they pick one. */
    fun observeSelectedPreset(): Flow<GymPreset?>

    /** Enable every lift tagged with [preset]. Used when the user taps a preset chip. */
    suspend fun applyPreset(preset: GymPreset)

    /** Flip a single lift's availability (the manual on/off toggle). */
    suspend fun setLiftEnabled(id: LiftId, enabled: Boolean)

    /** Enable many lifts in one persistence operation (historical import batch enablement). */
    suspend fun enableLifts(ids: Set<LiftId>)

    /** Record (or clear, with `null`) the user's preset choice for OS-5 Review. */
    suspend fun setSelectedPreset(preset: GymPreset?)

    /** True when the user imported historical data instead of picking a preset (OS-6). */
    suspend fun setUsedHistoricalImport(used: Boolean)

    fun observeUsedHistoricalImport(): Flow<Boolean>

    /**
     * Create a user-defined lift (`isCustom = true`, `enabled = true`) and return its id. A
     * single optional [increment] covers the minimal v1 custom form; full tier editing is
     * deferred. When [increment] is null the lift still loads with sensible default tiers.
     */
    suspend fun addCustomLift(
        name: String,
        allowWeightChangeBetweenSets: Boolean,
        increment: Weight?,
    ): LiftId

    /** Create a user-defined lift with full catalog metadata (import LLM enrichment path). */
    suspend fun addCustomLift(definition: CustomLiftDefinition): LiftId
}
