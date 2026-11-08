package com.stratum.domain.training

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.Workout
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.workout.WorkoutOverlay

/**
 * Next incomplete lift for the minimized workout bar (program sessions). Prefers template
 * order; returns `null` when every set is logged. Pure over domain types (moved from
 * `ProgramsViewModel` under STR-210 so the bar interactor owns it).
 */
fun findNextLiftName(
    workout: Workout,
    loggedSets: List<LoggedSet>,
    liftNames: Map<LiftId, String>,
): String? {
    val sortedLifts = workout.workoutLifts.sortedBy { it.position }
    for (workoutLift in sortedLifts) {
        val sortedSets = workoutLift.sets.sortedBy { it.position }
        for (setIndex in sortedSets.indices) {
            val isLogged = loggedSets.any {
                it.liftContext.workoutLiftId == workoutLift.id && it.setIndex == setIndex
            }
            if (!isLogged) {
                return liftNames[workoutLift.liftId] ?: "Unknown Lift"
            }
        }
    }
    return null
}

/**
 * Next incomplete session-added lift for the minimized bar (ad-hoc sessions). Prefers
 * overlay structure; falls back to durable logs when the overlay is missing.
 */
fun findNextAdHocLiftName(
    overlay: WorkoutOverlay?,
    loggedSets: List<LoggedSet>,
    liftNames: Map<LiftId, String>,
): String {
    if (overlay != null && overlay.addedLifts.isNotEmpty()) {
        val order =
            overlay.orderedLiftIds
                ?: overlay.addedLifts.map { it.workoutLiftId.value }
        for (wlIdValue in order) {
            val workoutLiftId = WorkoutLiftId(wlIdValue)
            if (workoutLiftId in overlay.deletedLiftIds) continue
            val slots =
                overlay.addedSlots
                    .filter { it.workoutLiftId == workoutLiftId }
                    .filterNot { it.slotId in overlay.deletedSlotIds }
                    .sortedBy { it.position }
            val hasUnlogged = slots.any { it.slotId !in overlay.loggedSets }
            if (hasUnlogged || slots.isEmpty()) {
                val added = overlay.addedLifts.firstOrNull { it.workoutLiftId == workoutLiftId }
                val liftId = overlay.swappedLifts[workoutLiftId] ?: added?.liftId
                return liftId?.let { liftNames[it] } ?: "Workout in progress"
            }
        }
        return "Workout in progress"
    }
    if (loggedSets.isEmpty()) return "Add lifts"
    // Overlay lost but sets exist — surface generic progress, not a stale "Add lifts".
    val last = loggedSets.maxByOrNull { it.completedAt }
    val liftId = last?.liftId
    return liftId?.let { liftNames[it] } ?: "Workout in progress"
}
