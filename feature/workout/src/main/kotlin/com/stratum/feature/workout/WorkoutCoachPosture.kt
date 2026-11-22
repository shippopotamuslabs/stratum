package com.stratum.feature.workout

/** Book-posture fold, or a width class with no hinge. Half-opened is never expanded. */
internal enum class WorkoutFoldState { Flat, HalfOpened }

/** Hinge axis. A horizontal hinge never becomes a left/right split. */
internal enum class WorkoutHingeAxis { Vertical, Horizontal }

/**
 * Folding feature reduced to numbers. [hingeLeftPx] and [hingeRightPx] share
 * a coordinate space with [windowWidthPx]. No framework types.
 */
internal data class WorkoutFoldSnapshot(
    val state: WorkoutFoldState,
    val axis: WorkoutHingeAxis,
    val hingeLeftPx: Int,
    val hingeRightPx: Int,
    val windowWidthPx: Int,
)

/** Hinge band as fractions of the window width, from the left edge. */
internal data class WorkoutHingeGap(
    val start: Float,
    val end: Float,
)

internal sealed interface WorkoutPosture {
    data object SinglePane : WorkoutPosture

    data class TwoPane(val hinge: WorkoutHingeGap?) : WorkoutPosture
}

/** Expanded width class. Below this, and with no flat fold, the screen stays one pane. */
internal const val WORKOUT_EXPANDED_WIDTH_DP = 840

internal fun decideWorkoutPosture(
    widthDp: Int,
    fold: WorkoutFoldSnapshot?,
): WorkoutPosture {
    if (!isExpandedWorkoutPosture(widthDp, fold)) return WorkoutPosture.SinglePane
    return WorkoutPosture.TwoPane(hinge = fold?.verticalHingeGap())
}

internal fun isExpandedWorkoutPosture(
    widthDp: Int,
    fold: WorkoutFoldSnapshot?,
): Boolean =
    when (fold?.state) {
        WorkoutFoldState.Flat -> true
        WorkoutFoldState.HalfOpened -> false
        null -> widthDp >= WORKOUT_EXPANDED_WIDTH_DP
    }

/**
 * Flat vertical hinge used by the posture unit test and the hinge screenshot.
 * Left edge at 48% of the window, right edge at 52%.
 */
internal fun workoutFlatHingeFixture(): WorkoutPosture =
    decideWorkoutPosture(
        widthDp = HINGE_FIXTURE_WIDTH_DP,
        fold =
            WorkoutFoldSnapshot(
                state = WorkoutFoldState.Flat,
                axis = WorkoutHingeAxis.Vertical,
                hingeLeftPx = HINGE_FIXTURE_LEFT_PX,
                hingeRightPx = HINGE_FIXTURE_RIGHT_PX,
                windowWidthPx = HINGE_FIXTURE_WIDTH_PX,
            ),
    )

private fun WorkoutFoldSnapshot.verticalHingeGap(): WorkoutHingeGap? {
    if (state != WorkoutFoldState.Flat || axis != WorkoutHingeAxis.Vertical) return null
    if (windowWidthPx <= 0) return null
    val left = hingeLeftPx.coerceIn(0, windowWidthPx)
    val right = hingeRightPx.coerceIn(0, windowWidthPx)
    if (right <= left) return null
    if (left == 0 && right == windowWidthPx) return null
    return WorkoutHingeGap(
        start = left.toFloat() / windowWidthPx.toFloat(),
        end = right.toFloat() / windowWidthPx.toFloat(),
    )
}

private const val HINGE_FIXTURE_WIDTH_DP = 1000
private const val HINGE_FIXTURE_WIDTH_PX = 1000
private const val HINGE_FIXTURE_LEFT_PX = 480
private const val HINGE_FIXTURE_RIGHT_PX = 520
