package com.stratum.core.ui.component

/**
 * Shared two-pane posture decision for foldables and wide windows (STR-331).
 *
 * One decision for every screen that splits into a working pane and a detail pane: a flat
 * fold or a window at least [PANE_EXPANDED_WIDTH_DP] wide expands, half-opened stays one
 * pane, and a vertical hinge is reported as a fractional gap. Pure numbers in, posture out —
 * no framework types, so the decision is unit-testable on the JVM.
 */

/** Book-posture fold, or a width class with no hinge. Half-opened is never expanded. */
enum class PaneFoldState { Flat, HalfOpened }

/** Hinge axis. A horizontal hinge never becomes a left/right split. */
enum class PaneHingeAxis { Vertical, Horizontal }

/**
 * Folding feature reduced to numbers. [hingeLeftPx] and [hingeRightPx] share
 * a coordinate space with [windowWidthPx]. No framework types.
 */
data class PaneFoldSnapshot(
    val state: PaneFoldState,
    val axis: PaneHingeAxis,
    val hingeLeftPx: Int,
    val hingeRightPx: Int,
    val windowWidthPx: Int,
)

/** Hinge band as fractions of the window width, from the left edge. */
data class PaneHingeGap(
    val start: Float,
    val end: Float,
)

sealed interface PanePosture {
    data object SinglePane : PanePosture

    data class TwoPane(val hinge: PaneHingeGap?) : PanePosture
}

/** Expanded width class. Below this, and with no flat fold, a screen stays one pane. */
const val PANE_EXPANDED_WIDTH_DP = 840

fun decidePanePosture(
    widthDp: Int,
    fold: PaneFoldSnapshot?,
): PanePosture {
    if (!isExpandedPanePosture(widthDp, fold)) return PanePosture.SinglePane
    return PanePosture.TwoPane(hinge = fold?.verticalHingeGap())
}

fun isExpandedPanePosture(
    widthDp: Int,
    fold: PaneFoldSnapshot?,
): Boolean =
    when (fold?.state) {
        PaneFoldState.Flat -> true
        PaneFoldState.HalfOpened -> false
        null -> widthDp >= PANE_EXPANDED_WIDTH_DP
    }

/**
 * Flat vertical hinge used by posture unit tests and hinge screenshots.
 * Left edge at 48% of the window, right edge at 52%.
 */
fun paneFlatHingeFixture(): PanePosture =
    decidePanePosture(
        widthDp = HINGE_FIXTURE_WIDTH_DP,
        fold =
            PaneFoldSnapshot(
                state = PaneFoldState.Flat,
                axis = PaneHingeAxis.Vertical,
                hingeLeftPx = HINGE_FIXTURE_LEFT_PX,
                hingeRightPx = HINGE_FIXTURE_RIGHT_PX,
                windowWidthPx = HINGE_FIXTURE_WIDTH_PX,
            ),
    )

private fun PaneFoldSnapshot.verticalHingeGap(): PaneHingeGap? {
    if (state != PaneFoldState.Flat || axis != PaneHingeAxis.Vertical) return null
    if (windowWidthPx <= 0) return null
    val left = hingeLeftPx.coerceIn(0, windowWidthPx)
    val right = hingeRightPx.coerceIn(0, windowWidthPx)
    if (right <= left) return null
    if (left == 0 && right == windowWidthPx) return null
    return PaneHingeGap(
        start = left.toFloat() / windowWidthPx.toFloat(),
        end = right.toFloat() / windowWidthPx.toFloat(),
    )
}

private const val HINGE_FIXTURE_WIDTH_DP = 1000
private const val HINGE_FIXTURE_WIDTH_PX = 1000
private const val HINGE_FIXTURE_LEFT_PX = 480
private const val HINGE_FIXTURE_RIGHT_PX = 520
