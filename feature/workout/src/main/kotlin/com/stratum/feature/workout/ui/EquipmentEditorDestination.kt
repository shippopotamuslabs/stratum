package com.stratum.feature.workout.ui

/**
 * Everything the router needs for one "Edit equipment" request: the catalog lift plus
 * its catalog equipment join. Threaded as one value through the sheet, actions, route,
 * and NavHost so no boundary unpacks and repacks the triple.
 */
data class EquipmentEditRequest(
    val liftId: String,
    val target: EquipmentEditTarget,
)

/**
 * Which increments editor an "Edit equipment" request resolves to.
 *
 * A lift carrying its own per-lift override tiers edits the override; a lift inheriting
 * shared equipment defaults edits the shared equipment; a lift with neither (global
 * fallback bands) edits the per-lift override screen. Pure decision — navigation keys
 * are pushed by `:app` from this result.
 */
sealed interface EquipmentEditorDestination {
    data class LiftOverride(val liftId: String) : EquipmentEditorDestination
    data class SharedEquipment(val equipmentId: String) : EquipmentEditorDestination
}

fun resolveEquipmentEditorDestination(request: EquipmentEditRequest): EquipmentEditorDestination =
    if (request.target.hasIncrementOverride || request.target.equipmentId == null) {
        EquipmentEditorDestination.LiftOverride(request.liftId)
    } else {
        EquipmentEditorDestination.SharedEquipment(request.target.equipmentId)
    }
