package com.stratum.core.ui.share

/**
 * Presentation model for the Instagram-square share card.
 *
 * Built from presentation models (e.g. `WorkoutCompleteUiState.Loaded` or
 * `WorkoutSessionDetailUiState.Loaded`) — never from domain models directly (ARCHITECTURE.md §4.3).
 *
 * Rows use [ShareLiftRowUi] so share layout is decoupled from on-screen performance tables.
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
 * Truncates [liftRows] for the square share canvas.
 *
 * - Shows at most [maxLifts] rows
 * - [WorkoutShareContent.overflowCount] is the remainder (for “+N more”)
 * - If [overridePersonalRecordCount] is null, PR count is derived from the **full** [liftRows] list
 *   so truncation does not hide PRs.
 */
fun buildWorkoutShareContent(
    workoutName: String,
    durationFormatted: String,
    liftRows: List<ShareLiftRowUi>,
    maxLifts: Int = WORKOUT_SHARE_MAX_LIFTS,
    overridePersonalRecordCount: Int? = null,
): WorkoutShareContent {
    require(maxLifts >= 0) { "maxLifts must be non-negative" }
    val visible = liftRows.take(maxLifts)
    return WorkoutShareContent(
        workoutName = workoutName,
        durationFormatted = durationFormatted,
        personalRecordCount = overridePersonalRecordCount ?: liftRows.count { it.isPersonalRecord },
        visibleSets = visible,
        overflowCount = (liftRows.size - visible.size).coerceAtLeast(0),
    )
}
