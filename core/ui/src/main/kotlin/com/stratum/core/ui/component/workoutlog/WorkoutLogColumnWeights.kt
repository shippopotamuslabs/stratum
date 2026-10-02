package com.stratum.core.ui.component.workoutlog

import androidx.compose.ui.unit.dp

/**
 * Horizontal inset for set-log table header, set rows, and add-set row.
 * Shared so the three call sites cannot drift (edge-to-edge densify layout).
 */
internal val WorkoutLogTableHorizontalPadding = 8.dp

/**
 * Column weight fractions for one set-log row. Weight column is present for weighted + assisted
 * lifts and absent for bodyweight. Checkbox/delete column can be hidden for pure edit mode.
 *
 * Weight and reps are close in width but deliberately not equal: reps holds at most
 * 2 digits while weight often holds 3, so reps is slightly narrower (still fitting ranges
 * like `10-12`). The difference widens the trailing column as a touch buffer between the
 * RIR corner badge overhanging the reps cell and the checkbox. The removed RIR column's
 * fraction is otherwise split evenly among the present content columns (prev, weight, reps).
 */
data class WorkoutLogColumnWeights(
    val label: Float,
    val prev: Float,
    val weight: Float,
    val reps: Float,
    val trailing: Float,
)

fun workoutLogColumnWeights(
    showWeightColumn: Boolean,
    showTrailingColumn: Boolean,
): WorkoutLogColumnWeights {
    return when {
        showWeightColumn && showTrailingColumn ->
            WorkoutLogColumnWeights(
                label = 0.10f,
                prev = 0.26f,
                weight = 0.26f,
                reps = 0.22f,
                trailing = 0.16f,
            )
        showWeightColumn && !showTrailingColumn ->
            WorkoutLogColumnWeights(
                label = 0.12f,
                prev = 0.30f,
                weight = 0.29f,
                reps = 0.29f,
                trailing = 0f,
            )
        !showWeightColumn && showTrailingColumn ->
            WorkoutLogColumnWeights(
                label = 0.12f,
                prev = 0.39f,
                weight = 0f,
                reps = 0.33f,
                trailing = 0.16f,
            )
        else ->
            WorkoutLogColumnWeights(
                label = 0.14f,
                prev = 0.44f,
                weight = 0f,
                reps = 0.42f,
                trailing = 0f,
            )
    }
}
