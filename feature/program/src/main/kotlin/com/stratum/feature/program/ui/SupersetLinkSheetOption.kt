package com.stratum.feature.program.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.stratum.core.ui.R as CoreUiR
import com.stratum.feature.program.R
import com.stratum.feature.program.SupersetLinkDirection
import com.stratum.feature.program.isSupersetPairMember

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
            add(
                SupersetLinkSheetOption(
                    direction = SupersetLinkDirection.Previous,
                    labelRes =
                        if (previous.supersetGroupId != null) {
                            R.string.edit_workout_giant_set_with_previous
                        } else {
                            R.string.edit_workout_superset_with_previous
                        },
                    trailingIconRes = CoreUiR.drawable.ic_join_left,
                ),
            )
        }
        if (index < sorted.lastIndex) {
            val next = sorted[index + 1]
            add(
                SupersetLinkSheetOption(
                    direction = SupersetLinkDirection.Next,
                    labelRes =
                        if (next.supersetGroupId != null) {
                            R.string.edit_workout_giant_set_with_next
                        } else {
                            R.string.edit_workout_superset_with_next
                        },
                    trailingIconRes = CoreUiR.drawable.ic_join_right,
                ),
            )
        }
    }
}

internal fun EditLiftState.showsSupersetChip(allLifts: List<EditLiftState>): Boolean =
    isSupersetPairMember(this, allLifts)
