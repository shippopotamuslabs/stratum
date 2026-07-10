package com.stratum.core.ui.component.workoutlog

/**
 * Column weight fractions for one set-log row. Weight column is present for weighted + assisted
 * lifts and absent for bodyweight. Checkbox/delete column can be hidden for pure edit mode.
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
                prev = 0.26f,
                weight = 0.22f,
                reps = 0.18f,
                rir = 0.12f,
                trailing = 0.12f,
            )
        showWeightColumn && !showTrailingColumn ->
            WorkoutLogColumnWeights(
                label = 0.12f,
                prev = 0.28f,
                weight = 0.24f,
                reps = 0.20f,
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
