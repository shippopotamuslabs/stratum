package com.stratum.core.ui.posture

/** Book-posture fold, or a width class with no hinge. Half-opened is never expanded. */
enum class FoldState { Flat, HalfOpened }

/** Hinge axis. A horizontal hinge never becomes a left/right split. */
enum class HingeAxis { Vertical, Horizontal }

/**
 * Folding feature reduced to numbers. [hingeLeftPx] and [hingeRightPx] share
 * a coordinate space with [windowWidthPx]. No framework types.
 */
data class FoldSnapshot(
    val state: FoldState,
    val axis: HingeAxis,
    val hingeLeftPx: Int,
    val hingeRightPx: Int,
    val windowWidthPx: Int,
)

/** Hinge band as fractions of the window width, from the left edge. */
data class HingeGap(
    val start: Float,
    val end: Float,
)

/**
 * Shared foldable posture for every two-pane surface (program sheet, workout
 * coach, Program tab). A flat fold, or a window at least
 * [EXPANDED_POSTURE_WIDTH_DP] wide, splits into two panes. Half-opened stays single.
 */
sealed interface FoldPosture {
    data object SinglePane : FoldPosture

    data class TwoPane(val hinge: HingeGap?) : FoldPosture
}

/** Expanded width class. Below this, and with no flat fold, a screen stays one pane. */
const val EXPANDED_POSTURE_WIDTH_DP = 840

fun decideFoldPosture(
    widthDp: Int,
    fold: FoldSnapshot?,
): FoldPosture {
    val expanded =
        when (fold?.state) {
            FoldState.Flat -> true
            FoldState.HalfOpened -> false
            null -> widthDp >= EXPANDED_POSTURE_WIDTH_DP
        }
    if (!expanded) return FoldPosture.SinglePane
    return FoldPosture.TwoPane(hinge = fold?.verticalHingeGap())
}

/**
 * Flat vertical hinge used by posture unit tests and hinge screenshots.
 * Left edge at 48% of the window, right edge at 52%.
 */
fun flatHingeFixture(): FoldPosture =
    decideFoldPosture(
        widthDp = HINGE_FIXTURE_WIDTH_DP,
        fold =
            FoldSnapshot(
                state = FoldState.Flat,
                axis = HingeAxis.Vertical,
                hingeLeftPx = HINGE_FIXTURE_LEFT_PX,
                hingeRightPx = HINGE_FIXTURE_RIGHT_PX,
                windowWidthPx = HINGE_FIXTURE_WIDTH_PX,
            ),
    )

private fun FoldSnapshot.verticalHingeGap(): HingeGap? {
    if (state != FoldState.Flat || axis != HingeAxis.Vertical) return null
    if (windowWidthPx <= 0) return null
    val left = hingeLeftPx.coerceIn(0, windowWidthPx)
    val right = hingeRightPx.coerceIn(0, windowWidthPx)
    if (right <= left) return null
    if (left == 0 && right == windowWidthPx) return null
    return HingeGap(
        start = left.toFloat() / windowWidthPx.toFloat(),
        end = right.toFloat() / windowWidthPx.toFloat(),
    )
}

private const val HINGE_FIXTURE_WIDTH_DP = 1000
private const val HINGE_FIXTURE_WIDTH_PX = 1000
private const val HINGE_FIXTURE_LEFT_PX = 480
private const val HINGE_FIXTURE_RIGHT_PX = 520
