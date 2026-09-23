package com.stratum.feature.workout.ui

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

fun resolveEquipmentEditorDestination(
    liftId: String,
    equipmentId: String?,
    hasIncrementOverride: Boolean,
): EquipmentEditorDestination =
    if (hasIncrementOverride || equipmentId == null) {
        EquipmentEditorDestination.LiftOverride(liftId)
    } else {
        EquipmentEditorDestination.SharedEquipment(equipmentId)
    }
