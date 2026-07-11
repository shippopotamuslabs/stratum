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
 * When both weight and reps columns are present they use equal fractions so multi-digit rep
 * ranges (e.g. `10-12`) fit the same width as load fields.
 */
data class WorkoutLogColumnWeights(
    val label: Float,
    val prev: Float,
    val weight: Float,
    val reps: Float,
    val rir: Float,
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
                prev = 0.22f,
                weight = 0.22f,
                reps = 0.22f,
                rir = 0.12f,
                trailing = 0.12f,
            )
        showWeightColumn && !showTrailingColumn ->
            WorkoutLogColumnWeights(
                label = 0.12f,
                prev = 0.24f,
                weight = 0.24f,
                reps = 0.24f,
                rir = 0.16f,
                trailing = 0f,
            )
        !showWeightColumn && showTrailingColumn ->
            WorkoutLogColumnWeights(
                label = 0.12f,
                prev = 0.30f,
                weight = 0f,
                reps = 0.28f,
                rir = 0.18f,
                trailing = 0.12f,
            )
        else ->
            WorkoutLogColumnWeights(
                label = 0.14f,
                prev = 0.34f,
                weight = 0f,
                reps = 0.32f,
                rir = 0.20f,
                trailing = 0f,
            )
    }
}
