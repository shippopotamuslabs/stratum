package com.stratum.feature.workout

import com.stratum.feature.workout.ui.RenderedLiftUi

/**
 * Pure lift-completion / next-lift selection rules for the live workout screen.
 * Kept free of Compose so #5 (zero-complete advance) and #6 (superset cycle) are unit-tested.
 */

/** True when the lift has at least one set and every set is logged. */
fun isLiftComplete(lift: RenderedLiftUi): Boolean =
    lift.sets.isNotEmpty() && lift.sets.all { it.isComplete }

/** True when every lift in the session has all sets logged. Empty sessions are not complete. */
fun isWorkoutFullyComplete(lifts: List<RenderedLiftUi>): Boolean =
    lifts.isNotEmpty() && lifts.all { isLiftComplete(it) }

/** True when the lift has sets and none of them are logged yet. */
fun hasZeroCompletedSets(lift: RenderedLiftUi): Boolean =
    lift.sets.isNotEmpty() && lift.sets.none { it.isComplete }

/**
 * Returns a copy of [lifts] with the set matching [slotKey] marked complete.
 * Used to project post-log state before the ViewModel emits the updated UI model.
 */
fun projectSetComplete(
    lifts: List<RenderedLiftUi>,
    slotKey: String,
): List<RenderedLiftUi> =
    lifts.map { lift ->
        lift.copy(
            sets =
                lift.sets.map { set ->
                    if (set.slotKey == slotKey) set.copy(isComplete = true) else set
                },
        )
    }

/**
 * After a set is logged on [currentLiftId], returns the next lift id to focus, or null when
 * navigation should not change (stay on current, or workout is fully complete).
 *
 * Rules (evaluated against post-log [lifts]):
 * - Superset / giant set (#6): while any group member still has incomplete sets, advance to the
 *   next incomplete member in group position order (wrapping). When the whole group is done,
 *   fall through to rule #5 outside the group.
 * - Non-superset (#5): only when the current lift is fully complete, jump to the next lift
 *   (forward, then wrap) that has zero completed sets.
 */
fun nextLiftAfterSetLogged(
    currentLiftId: String,
    lifts: List<RenderedLiftUi>,
): String? {
    val currentIndex = lifts.indexOfFirst { it.workoutLiftId == currentLiftId }
    if (currentIndex < 0 || isWorkoutFullyComplete(lifts)) return null
    val current = lifts[currentIndex]

    val groupId = current.supersetGroupId?.takeIf { it.isNotBlank() }
    return when {
        groupId != null -> {
            val groupMembers = lifts.filter { it.supersetGroupId == groupId }
            if (groupMembers.any { !isLiftComplete(it) }) {
                nextIncompleteInGroup(currentLiftId, groupMembers)
            } else {
                // Entire superset/giant set done → next outside group with 0 completed sets.
                nextLiftWithZeroCompletedSets(
                    lifts = lifts,
                    afterIndex = currentIndex,
                    excludeGroupId = groupId,
                )
            }
        }
        // Non-superset: only advance after finishing the last incomplete set on this lift.
        !isLiftComplete(current) -> null
        else ->
            nextLiftWithZeroCompletedSets(
                lifts = lifts,
                afterIndex = currentIndex,
                excludeGroupId = null,
            )
    }
}

/** UI side-effect decision after a successful set log (auto-scroll / finish sheet). */
sealed interface AfterSetLoggedDecision {
    data object None : AfterSetLoggedDecision
    data object ShowCompleteWorkoutSheet : AfterSetLoggedDecision
    data class FocusLift(val workoutLiftId: String) : AfterSetLoggedDecision
}

/**
 * Projects the logged set as complete, then decides whether to show the finish sheet or
 * advance focus. Pure — no Compose.
 */
fun decideAfterSetLogged(
    lifts: List<RenderedLiftUi>,
    slotKey: String,
    alreadyAutoShownCompleteSheet: Boolean,
): AfterSetLoggedDecision {
    val currentLiftId = workoutLiftIdForSlot(lifts, slotKey) ?: return AfterSetLoggedDecision.None
    val projected = projectSetComplete(lifts, slotKey)
    return when {
        isWorkoutFullyComplete(projected) && !alreadyAutoShownCompleteSheet ->
            AfterSetLoggedDecision.ShowCompleteWorkoutSheet
        isWorkoutFullyComplete(projected) -> AfterSetLoggedDecision.None
        else -> {
            val nextId = nextLiftAfterSetLogged(currentLiftId = currentLiftId, lifts = projected)
            if (nextId != null) {
                AfterSetLoggedDecision.FocusLift(nextId)
            } else {
                AfterSetLoggedDecision.None
            }
        }
    }
}

/**
 * Next incomplete member after [currentLiftId] within [groupMembers] (position order, wrap).
 * Returns null when the only incomplete member is the current lift (stay put).
 */
internal fun nextIncompleteInGroup(
    currentLiftId: String,
    groupMembers: List<RenderedLiftUi>,
): String? {
    if (groupMembers.isEmpty()) return null
    val start = groupMembers.indexOfFirst { it.workoutLiftId == currentLiftId }
    if (start < 0) return null
    for (offset in 1 until groupMembers.size) {
        val candidate = groupMembers[(start + offset) % groupMembers.size]
        if (!isLiftComplete(candidate)) {
            return candidate.workoutLiftId
        }
    }
    return null
}

/**
 * Forward-then-wrap search for a lift with zero completed sets after [afterIndex].
 * Optionally skips lifts still in [excludeGroupId] (finished superset members).
 */
internal fun nextLiftWithZeroCompletedSets(
    lifts: List<RenderedLiftUi>,
    afterIndex: Int,
    excludeGroupId: String?,
): String? {
    val n = lifts.size
    if (n == 0 || afterIndex !in lifts.indices) return null
    for (offset in 1..n) {
        val index = (afterIndex + offset) % n
        val lift = lifts[index]
        if (excludeGroupId != null && lift.supersetGroupId == excludeGroupId) continue
        if (hasZeroCompletedSets(lift)) return lift.workoutLiftId
    }
    return null
}

/** Finds the owning workout-lift id for a set slot, if present. */
fun workoutLiftIdForSlot(
    lifts: List<RenderedLiftUi>,
    slotKey: String,
): String? =
    lifts.firstOrNull { lift -> lift.sets.any { it.slotKey == slotKey } }?.workoutLiftId
