package com.stratum.application.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import kotlinx.coroutines.flow.Flow
import com.stratum.domain.lift.CatalogNoteInput
import com.stratum.domain.lift.CustomLiftDefinition
import com.stratum.domain.lift.CustomLiftDetails
import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.LiftCatalogNote
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.classifyLiftCategory
import com.stratum.domain.onboarding.MuscleGroup

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
@Suppress("TooManyFunctions")
interface LiftRepository {
    /**
     * Hot stream of the live catalog, each carrying its current `enabled` flag.
     * Tombstoned lifts ([Lift.isDeleted], STR-131) are filtered at the
     * repository layer and never surface here — nor in any other catalog read
     * on this interface. By-id reads ([getLiftsByIds]) stay unfiltered: they
     * exist so history/session resolution can reach retained tombstoned rows.
     */
    fun observeLifts(): Flow<List<Lift>>

    /**
     * Snapshot of lifts whose ids are in [ids] — SQL `WHERE id IN (...)`, not a full-catalog
     * load then filter (DATA_LAYER_RULES.md §5). Empty [ids] returns empty without a query.
     * Order is not guaranteed; callers that need a stable order should sort.
     * Unfiltered by tombstone: tombstoned rows are returned (history and
     * grandfathered sessions resolve through here).
     */
    suspend fun getLiftsByIds(ids: Collection<LiftId>): List<Lift>

    /**
     * Hot stream of only enabled lifts — the SQL-predicate equivalent of
     * `observeLifts().map { it.filter(Lift::enabled) }`. Prefer this over re-filtering in
     * Kotlin so the catalog isn't loaded whole just to drop its disabled rows
     * (DATA_LAYER_RULES.md §5). Consumers that legitimately need the full catalog
     * (including disabled lifts, e.g. insights extraction) keep using [observeLifts].
     */
    fun observeEnabledLifts(): Flow<List<Lift>>

    /**
     * Hot stream of lifts filtered by the [isCustom] flag — the SQL-predicate equivalent of
     * `observeLifts().map { it.filter { l -> l.isCustom == isCustom } }`. Used by import
     * paths that need the seed/custom split without loading the whole catalog.
     */
    fun observeLifts(isCustom: Boolean): Flow<List<Lift>>

    /**
     * Hot stream of lifts whose movement-pattern set includes [pattern] — the SQL-predicate
     * equivalent of `observeLifts().map { it.filter { l -> pattern in l.movementPatterns } }`,
     * evaluated as a bitwise AND on the stored bitmask so the database drops non-matching rows
     * rather than the caller (DATA_LAYER_RULES.md §5). Prefer this over re-filtering in Kotlin
     * when a consumer only needs one pattern's lifts.
     */
    fun observeLiftsContainingPattern(pattern: MovementPattern): Flow<List<Lift>>

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

    /** Create a user-defined lift with full catalog metadata. Returns its id. */
    suspend fun addCustomLift(definition: CustomLiftDefinition): LiftId

    /**
     * Assigns or clears [equipmentId] on a catalog or custom lift and persists [loadType]
     * in the same write. The lift re-inherits the new equipment defaults (or global
     * fallback when unassigned).
     */
    suspend fun setLiftEquipment(id: LiftId, equipmentId: EquipmentId?, loadType: LoadType)

    /**
     * Updates primary/secondary muscle tags for [id]. Bitmasks are derived in the data
     * mapper; no schema change. No-op when the lift is missing or not custom — catalog
     * lifts are immutable from the Details tab.
     */
    suspend fun updateLiftMuscles(
        id: LiftId,
        primaryMuscles: Set<com.stratum.domain.onboarding.MuscleGroup>,
        secondaryMuscles: Set<com.stratum.domain.onboarding.MuscleGroup>,
    )

    /**
     * Updates non-derived metadata for a custom lift. No-op when the lift is missing or
     * not custom. Category is never persisted — it is derived via [classifyLiftCategory].
     */
    suspend fun updateCustomLiftDetails(id: LiftId, details: CustomLiftDetails)

    /**
     * Persists a per-lift default rest override (seconds). Applies to catalog and custom lifts.
     * Null clears the override so [resolvedDefaultRestSeconds] falls back to High→180 / else 120
     * defaults. Does not rewrite existing workout set rest values.
     */
    suspend fun setDefaultRestSeconds(id: LiftId, restSeconds: Int?)

    /**
     * Persists a user-authored note pinned to this catalog lift (seed or custom).
     * Null / blank clears the note. Does not touch [Lift.description].
     */
    suspend fun setPinnedNote(id: LiftId, note: String?)

    /**
     * All catalog notes for one lift, display order (position ascending).
     *
     * Client lazy-migrate: when the lift carries only a legacy `pinned_note`
     * and no rows, implementations persist it as the position-0 row before
     * returning, so this read can dirty the store. Callers must treat it as a
     * read-write operation, not a pure query.
     */
    suspend fun getPinnedNotes(id: LiftId): List<LiftCatalogNote>

    /**
     * Replaces the whole catalog note list for one lift. Blank bodies are dropped;
     * an empty surviving list clears all notes. See
     * [LiftNotesRepository.replaceSessionNotes] for id semantics.
     */
    suspend fun setPinnedNotes(id: LiftId, notes: List<CatalogNoteInput>)

    /**
     * Permanently removes catalog rows for [ids] (increment tiers cascade). Caller must re-point
     * or remove any `workout_lifts` that reference these ids first (FK RESTRICT). Empty [ids]
     * is a no-op. Cloud tombstones are enqueued for each deleted id.
     */
    suspend fun deleteLifts(ids: Collection<LiftId>)
}
