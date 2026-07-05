package com.stratum.domain.program

import javax.inject.Inject
import kotlin.math.ceil

/** Active work per set before rest (lifting, setup, logging). */
private const val ACTIVE_SECONDS_PER_SET = 60

private const val SECONDS_PER_MINUTE = 60

/**
 * Estimates wall-clock workout duration from set count and configured rest periods.
 * Rest after the final set of each lift is excluded.
 */
class EstimateWorkoutDurationMinutesUseCase @Inject constructor() {
    operator fun invoke(workoutLifts: List<WorkoutLiftDurationInput>): Int {
        val totalSeconds = workoutLifts.sumOf(::estimateLiftSeconds)
        return ceil(totalSeconds.toDouble() / SECONDS_PER_MINUTE).toInt().coerceAtLeast(0)
    }
}

internal fun estimateLiftSeconds(lift: WorkoutLiftDurationInput): Int {
    if (lift.restSecondsBySet.isEmpty()) return 0
    var total = 0
    lift.restSecondsBySet.forEachIndexed { index, rest ->
        total += ACTIVE_SECONDS_PER_SET
        if (index < lift.restSecondsBySet.lastIndex) {
            total += rest.coerceAtLeast(0)
        }
    }
    return total
}
