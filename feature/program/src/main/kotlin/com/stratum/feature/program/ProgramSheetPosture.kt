package com.stratum.feature.program

/** Book-posture fold, or a width class with no hinge. Half-opened is never expanded. */
internal enum class SheetFoldState { Flat, HalfOpened }

/** Hinge axis. A horizontal hinge never becomes a top/bottom split. */
internal enum class SheetHingeAxis { Vertical, Horizontal }

/**
 * Folding feature reduced to numbers. [hingeLeftPx] and [hingeRightPx] share
 * a coordinate space with [windowWidthPx]. No framework types.
 */
internal data class SheetFoldSnapshot(
    val state: SheetFoldState,
    val axis: SheetHingeAxis,
    val hingeLeftPx: Int,
    val hingeRightPx: Int,
    val windowWidthPx: Int,
)

/** Hinge band as fractions of the window width, from the left edge. */
internal data class SheetHingeGap(
    val start: Float,
    val end: Float,
)

internal sealed interface SheetPosture {
    data object SinglePane : SheetPosture

    data class TwoPane(val hinge: SheetHingeGap?) : SheetPosture
}

/** Expanded width class. Below this, and with no flat fold, the sheet stays one pane. */
internal const val SHEET_EXPANDED_WIDTH_DP = 840

internal fun decideSheetPosture(
    widthDp: Int,
    fold: SheetFoldSnapshot?,
): SheetPosture {
    if (!isExpandedSheetPosture(widthDp, fold)) return SheetPosture.SinglePane
    return SheetPosture.TwoPane(hinge = fold?.verticalHingeGap())
}

internal fun isExpandedSheetPosture(
    widthDp: Int,
    fold: SheetFoldSnapshot?,
): Boolean =
    when (fold?.state) {
        SheetFoldState.Flat -> true
        SheetFoldState.HalfOpened -> false
        null -> widthDp >= SHEET_EXPANDED_WIDTH_DP
    }

/**
 * Flat vertical hinge used by the posture unit test and the hinge screenshot.
 * Left edge at 48% of the window, right edge at 52%.
 */
internal fun sheetFlatHingeFixture(): SheetPosture =
    decideSheetPosture(
        widthDp = HINGE_FIXTURE_WIDTH_DP,
        fold =
            SheetFoldSnapshot(
                state = SheetFoldState.Flat,
                axis = SheetHingeAxis.Vertical,
                hingeLeftPx = HINGE_FIXTURE_LEFT_PX,
                hingeRightPx = HINGE_FIXTURE_RIGHT_PX,
                windowWidthPx = HINGE_FIXTURE_WIDTH_PX,
            ),
    )

private fun SheetFoldSnapshot.verticalHingeGap(): SheetHingeGap? {
    if (state != SheetFoldState.Flat || axis != SheetHingeAxis.Vertical) return null
    if (windowWidthPx <= 0) return null
    val left = hingeLeftPx.coerceIn(0, windowWidthPx)
    val right = hingeRightPx.coerceIn(0, windowWidthPx)
    if (right <= left) return null
    if (left == 0 && right == windowWidthPx) return null
    return SheetHingeGap(
        start = left.toFloat() / windowWidthPx.toFloat(),
        end = right.toFloat() / windowWidthPx.toFloat(),
    )
}

private const val HINGE_FIXTURE_WIDTH_DP = 1000
private const val HINGE_FIXTURE_WIDTH_PX = 1000
private const val HINGE_FIXTURE_LEFT_PX = 480
private const val HINGE_FIXTURE_RIGHT_PX = 520
