package com.stratum.domain.importing

import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.Equipment

/**
 * Pure equipment-closure + gym-type derivation used transiently by [ResolveImportLiftsUseCase] for
 * Tier B enablement. No I/O, no Room — unit-testable in isolation.
 *
 * Equipment closure: a catalog lift is "available by closure" when its required equipment (from the
 * bundled asset) is a subset of the equipment the user has confirmed available (from their import
 * history + the Flash inference pass), AND it is not already a Tier A proven lift. The closure is
 * the sole Tier B gate — no preset gate (the import enablement plan §4a).
 *
 * Gym type is derived from [GymEquipment.COMMERCIAL_INDICATORS] only when the closure alone
 * enabled too few lifts (sparse-import fallback). It is never persisted; it exists only to pick a
 * preset backfill and is then discarded.
 */
object ImportEquipmentClosure {
    /** Below this many Tier B closure lifts, the sparse-import preset backfill kicks in. */
    const val MIN_TIER_B_FOR_EXPANSION = 15

    /**
     * Returns the catalog lift ids to enable as Tier B, given the equipment catalog, the confirmed
     * equipment set, and the Tier A ids to exclude (already enabled proven lifts).
     */
    fun tierBLiftIds(
        equipmentCatalog: Map<LiftId, Set<GymEquipment>>,
        confirmedEquipment: Set<GymEquipment>,
        tierAIds: Set<LiftId>,
    ): Set<LiftId> {
        if (confirmedEquipment.isEmpty()) return emptySet()
        return equipmentCatalog
            .filter { (id, required) ->
                id !in tierAIds && required.isNotEmpty() && confirmedEquipment.containsAll(required)
            }
            .keys
    }

    /**
     * Derives a transient gym type from confirmed equipment: any commercial indicator present ->
     * [GymPreset.CommercialGym]; otherwise [GymPreset.GarageGym]. Used only for the sparse-import
     * fallback and never persisted.
     */
    fun deriveGymFromEquipment(confirmedEquipment: Set<GymEquipment>): GymPreset =
        if (confirmedEquipment.intersect(GymEquipment.COMMERCIAL_INDICATORS).isNotEmpty()) {
            GymPreset.CommercialGym
        } else {
            GymPreset.GarageGym
        }

    /**
     * Sparse-import fallback: if [tierBCount] is below [MIN_TIER_B_FOR_EXPANSION], return the
     * derived gym type so the caller can `applyPreset` for all preset-tagged lifts not already
     * enabled; otherwise return null (closure was sufficient, no backfill needed).
     */
    fun sparseImportFallbackGym(
        tierBCount: Int,
        confirmedEquipment: Set<GymEquipment>,
    ): GymPreset? =
        if (tierBCount < MIN_TIER_B_FOR_EXPANSION) deriveGymFromEquipment(confirmedEquipment) else null
}
