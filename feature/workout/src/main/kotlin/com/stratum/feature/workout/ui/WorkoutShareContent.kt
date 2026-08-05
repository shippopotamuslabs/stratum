package com.stratum.feature.workout.ui

/**
 * Presentation model for the Instagram-square share card. Built from
 * [WorkoutCompleteUiState.Loaded] — never from domain models.
 *
 * Rows use [ShareLiftRowUi] (not [LiftBestSetUi]) so share layout is not coupled to the
 * on-screen performance table fields (RIR / e1RM).
 */
data class WorkoutShareContent(
    val workoutName: String,
    val durationFormatted: String,
    val personalRecordCount: Int,
    val visibleSets: List<ShareLiftRowUi>,
    /** Lifts omitted after the hard cap; 0 when everything fits. */
    val overflowCount: Int,
)

/** Share-only lift row — name, performance label, PR mark. */
data class ShareLiftRowUi(
    val liftName: String,
    val performanceLabel: String,
    val isPersonalRecord: Boolean,
)

/** Hard cap so a dense session still fits a 1:1 card without microscopic type. */
const val WORKOUT_SHARE_MAX_LIFTS: Int = 7

/**
 * Truncates [bestSets] for the square share canvas.
 *
 * - Shows at most [maxLifts] rows
 * - [WorkoutShareContent.overflowCount] is the remainder (for “+N more”)
 * - PR count is derived from the **full** list so truncation does not hide PRs
 */
fun buildWorkoutShareContent(
    workoutName: String,
    durationFormatted: String,
    bestSets: List<LiftBestSetUi>,
    maxLifts: Int = WORKOUT_SHARE_MAX_LIFTS,
): WorkoutShareContent {
    require(maxLifts >= 0) { "maxLifts must be non-negative" }
    val visible = bestSets.take(maxLifts).map { it.toShareLiftRow() }
    return WorkoutShareContent(
        workoutName = workoutName,
        durationFormatted = durationFormatted,
        personalRecordCount = bestSets.count { it.isPersonalRecord },
        visibleSets = visible,
        overflowCount = (bestSets.size - visible.size).coerceAtLeast(0),
    )
}

fun WorkoutCompleteUiState.Loaded.toShareContent(
    maxLifts: Int = WORKOUT_SHARE_MAX_LIFTS,
): WorkoutShareContent =
    buildWorkoutShareContent(
        workoutName = workoutName,
        durationFormatted = durationFormatted,
        bestSets = bestSets,
        maxLifts = maxLifts,
    )

private fun LiftBestSetUi.toShareLiftRow(): ShareLiftRowUi =
    ShareLiftRowUi(
        liftName = liftName,
        performanceLabel = performanceLabel,
        isPersonalRecord = isPersonalRecord,
    )
