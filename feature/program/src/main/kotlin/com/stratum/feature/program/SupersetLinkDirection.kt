package com.stratum.feature.program

import com.stratum.feature.program.ui.EditLiftState

internal enum class SupersetLinkDirection {
    Previous,
    Next,
    Remove,
}

/**
 * Links [workoutLiftId] with its adjacent lift in [direction], reusing an existing group id when
 * the neighbor (or current lift) is already in a superset/giant set.
 */
internal fun linkSupersetWithAdjacent(
    lifts: List<EditLiftState>,
    workoutLiftId: String,
    direction: SupersetLinkDirection,
    nextGroupId: () -> String,
): List<EditLiftState> {
    val sorted = lifts.sortedBy { lift -> lift.position }
    val index = sorted.indexOfFirst { lift -> lift.workoutLiftId == workoutLiftId }
    if (index < 0) {
        return lifts
    }
    val neighborIndex =
        when (direction) {
            SupersetLinkDirection.Previous -> index - 1
            SupersetLinkDirection.Next -> index + 1
            SupersetLinkDirection.Remove -> return lifts
        }
    if (neighborIndex !in sorted.indices) {
        return lifts
    }

    val current = sorted[index]
    val neighbor = sorted[neighborIndex]
    if (current.supersetGroupId != null && current.supersetGroupId == neighbor.supersetGroupId) {
        return lifts
    }

    val targetGroupId =
        neighbor.supersetGroupId
            ?: current.supersetGroupId
            ?: nextGroupId()

    val liftIdsToUpdate =
        buildSet {
            add(current.workoutLiftId)
            if (neighbor.supersetGroupId == null) {
                add(neighbor.workoutLiftId)
            }
        }

    return lifts.map { lift ->
        if (lift.workoutLiftId in liftIdsToUpdate) {
            lift.copy(
                supersetGroupId = targetGroupId,
                useRestTimerBetweenLifts = false,
            )
        } else {
            lift
        }
    }
}

/** Removes [workoutLiftId] from its superset/giant set and clears any orphaned group ids. */
internal fun unlinkLiftFromSuperset(
    lifts: List<EditLiftState>,
    workoutLiftId: String,
): List<EditLiftState> {
    val updated =
        lifts.map { lift ->
            if (lift.workoutLiftId == workoutLiftId) {
                lift.copy(
                    supersetGroupId = null,
                    useRestTimerBetweenLifts = false,
                )
            } else {
                lift
            }
        }
    return cleanupSupersetGroups(updated)
}

/** Clears group ids that no longer have at least two linked lifts. */
internal fun cleanupSupersetGroups(lifts: List<EditLiftState>): List<EditLiftState> {
    val counts =
        lifts
            .mapNotNull { lift -> lift.supersetGroupId }
            .groupingBy { groupId -> groupId }
            .eachCount()
    return lifts.map { lift ->
        val groupId = lift.supersetGroupId
        if (groupId != null && (counts[groupId] ?: 0) < 2) {
            lift.copy(
                supersetGroupId = null,
                useRestTimerBetweenLifts = false,
            )
        } else {
            lift
        }
    }
}

internal fun isSupersetPairMember(
    lift: EditLiftState,
    allLifts: List<EditLiftState>,
): Boolean {
    val groupId = lift.supersetGroupId ?: return false
    return allLifts.count { member -> member.supersetGroupId == groupId } >= 2
}
