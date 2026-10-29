package com.stratum.domain.lift

import com.stratum.domain.lift.identity.EquipmentId

/** One workout whose template slots point at the equipment being deleted. */
data class AffectedTemplateWorkout(
    val programName: String,
    val workoutName: String,
    val slotCount: Int,
)

/**
 * Delete-impact preview for one equipment row (STR-105 Scope 4.1): the live
 * pointers a delete would have to reassign, plus the replacement candidates.
 * One canonical computation shared by both delete surfaces (Manage Equipment
 * swipe and the increments-editor Delete button).
 */

data class EquipmentDeleteImpact(
    val equipmentId: EquipmentId,
    val equipmentName: String,
    val affectedLifts: List<AffectedLift>,
    val affectedWorkouts: List<AffectedTemplateWorkout>,
    val replacementCandidates: List<Equipment>,
) {
    val hasAffectedPointers: Boolean
        get() = affectedLifts.isNotEmpty() || affectedWorkouts.isNotEmpty()

    /** True when pointers exist but no live replacement can be picked. */
    val usesBodyweightFallback: Boolean
        get() = hasAffectedPointers && replacementCandidates.isEmpty()
}

/**
 * Computes [EquipmentDeleteImpact] for [EquipmentId]: affected catalog lifts
 * reuse [filterEquipmentAffectedLifts] semantics (disabled lifts included),
 * affected slots span every program (active + library), and candidates are
 * the live catalog minus the deleted row and the hidden system rows.
 */
