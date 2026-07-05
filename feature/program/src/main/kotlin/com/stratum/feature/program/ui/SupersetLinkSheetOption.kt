package com.stratum.feature.program.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.stratum.core.ui.R as CoreUiR
import com.stratum.feature.program.R
import com.stratum.feature.program.SupersetLinkDirection
import com.stratum.feature.program.isLastInSupersetChain
import com.stratum.feature.program.isSupersetPairMember
import com.stratum.feature.program.linkSupersetWithAdjacent

internal data class SupersetLinkSheetOption(
    val direction: SupersetLinkDirection,
    @StringRes val labelRes: Int,
    @DrawableRes val trailingIconRes: Int,
)

internal fun supersetLinkSheetOptions(
    lifts: List<EditLiftState>,
    workoutLiftId: String,
): List<SupersetLinkSheetOption> {
    val sorted = lifts.sortedBy { lift -> lift.position }
    val index = sorted.indexOfFirst { lift -> lift.workoutLiftId == workoutLiftId }
    if (index < 0) {
        return emptyList()
    }
    return buildList {
        val current = sorted[index]
        if (isSupersetPairMember(current, lifts)) {
            val groupSize =
                lifts.count { lift -> lift.supersetGroupId == current.supersetGroupId }
            add(
                SupersetLinkSheetOption(
                    direction = SupersetLinkDirection.Remove,
                    labelRes =
                        if (groupSize >= 3) {
                            R.string.edit_workout_remove_from_giant_set
                        } else {
                            R.string.edit_workout_remove_from_superset
                        },
                    trailingIconRes = CoreUiR.drawable.ic_join,
                ),
            )
        }
        if (index > 0) {
            val previous = sorted[index - 1]
            if (canLinkWithNeighbor(current, previous)) {
                add(
                    SupersetLinkSheetOption(
                        direction = SupersetLinkDirection.Previous,
                        labelRes = linkLabelForNeighbor(current, previous, isPrevious = true),
                        trailingIconRes = CoreUiR.drawable.ic_join_left,
                    ),
                )
            }
        }
        if (index < sorted.lastIndex) {
            val next = sorted[index + 1]
            if (canLinkWithNeighbor(current, next)) {
                add(
                    SupersetLinkSheetOption(
                        direction = SupersetLinkDirection.Next,
                        labelRes = linkLabelForNeighbor(current, next, isPrevious = false),
                        trailingIconRes = CoreUiR.drawable.ic_join_right,
                    ),
                )
            }
        }
    }
}

/** True when [current] can link with [neighbor] (not already in the same superset group). */
private fun canLinkWithNeighbor(current: EditLiftState, neighbor: EditLiftState): Boolean =
    current.supersetGroupId == null || current.supersetGroupId != neighbor.supersetGroupId

/**
 * Giant set labels apply when joining an existing superset group (3rd+ lift). Superset labels
 * apply when pairing with an ungrouped neighbor or forming a new pair.
 */
private fun linkLabelForNeighbor(
    current: EditLiftState,
    neighbor: EditLiftState,
    isPrevious: Boolean,
): Int {
    val joiningExistingGroup =
        neighbor.supersetGroupId != null && neighbor.supersetGroupId != current.supersetGroupId
    return when {
        joiningExistingGroup && isPrevious -> R.string.edit_workout_giant_set_with_previous
        joiningExistingGroup -> R.string.edit_workout_giant_set_with_next
        isPrevious -> R.string.edit_workout_superset_with_previous
        else -> R.string.edit_workout_superset_with_next
    }
}

internal fun EditLiftState.showsSupersetChip(allLifts: List<EditLiftState>): Boolean =
    isSupersetPairMember(this, allLifts)

/**
 * After a link action, keep the configure sheet open when the lift is in a superset and not
 * terminal so the rest-between-lifts toggle can appear without the sheet closing first.
 */
internal fun shouldKeepSupersetSheetOpenAfterLink(
    lifts: List<EditLiftState>,
    workoutLiftId: String,
    direction: SupersetLinkDirection,
): Boolean {
    if (direction == SupersetLinkDirection.Remove) {
        return false
    }
    val updated =
        linkSupersetWithAdjacent(
            lifts = lifts,
            workoutLiftId = workoutLiftId,
            direction = direction,
            nextGroupId = { "preview-group-id" },
        )
    val current = updated.firstOrNull { lift -> lift.workoutLiftId == workoutLiftId } ?: return false
    return isSupersetPairMember(current, updated) && !isLastInSupersetChain(current, updated)
}
