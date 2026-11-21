package com.stratum.feature.program

import android.graphics.Rect
import androidx.window.layout.FoldingFeature
import com.stratum.feature.program.ui.toSheetFoldSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/** Robolectric so the fixture's [Rect] carries real bounds. */
@ExtendWith(RobolectricExtension::class)
class ProgramSheetPostureTest {
    @Test
    @DisplayName("flat hinge bounds split around the hinge")
    fun flatWithHingeBounds() {
        val posture = sheetFlatHingeFixture()
        val hinge = (posture as SheetPosture.TwoPane).hinge
        assertEquals(HINGE_LEFT_PX.toFloat() / HINGE_WIDTH_PX.toFloat(), hinge?.start)
        assertEquals(HINGE_RIGHT_PX.toFloat() / HINGE_WIDTH_PX.toFloat(), hinge?.end)
    }

    @Test
    @DisplayName("a folding feature fixture is the same hinge the screenshot uses")
    fun foldingFeatureFixtureMatchesDecision() {
        val feature =
            FixtureFold(
                rect = Rect(HINGE_LEFT_PX, 0, HINGE_RIGHT_PX, HINGE_HEIGHT_PX),
                foldState = FoldingFeature.State.FLAT,
                foldOrientation = FoldingFeature.Orientation.VERTICAL,
            )
        val posture =
            decideSheetPosture(
                widthDp = HINGE_WIDTH_DP,
                fold = feature.toSheetFoldSnapshot(windowWidthPx = HINGE_WIDTH_PX),
            )
        assertEquals(sheetFlatHingeFixture(), posture)
    }

    @Test
    @DisplayName("flat with no hinge band splits in half")
    fun flatWithoutBounds() {
        val posture =
            decideSheetPosture(
                widthDp = COMPACT_WIDTH_DP,
                fold =
                    SheetFoldSnapshot(
                        state = SheetFoldState.Flat,
                        axis = SheetHingeAxis.Vertical,
                        hingeLeftPx = 0,
                        hingeRightPx = 0,
                        windowWidthPx = HINGE_WIDTH_PX,
                    ),
            )
        assertEquals(SheetPosture.TwoPane(hinge = null), posture)
    }

    @Test
    @DisplayName("expanded width with no folding feature is two panes")
    fun expandedWidthWithoutFold() {
        assertEquals(
            SheetPosture.TwoPane(hinge = null),
            decideSheetPosture(widthDp = SHEET_EXPANDED_WIDTH_DP, fold = null),
        )
    }

    @Test
    @DisplayName("compact width with no folding feature stays one pane")
    fun compactWidth() {
        assertEquals(
            SheetPosture.SinglePane,
            decideSheetPosture(widthDp = SHEET_EXPANDED_WIDTH_DP - 1, fold = null),
        )
    }

    @Test
    @DisplayName("half-opened stays one pane even when the window is wide")
    fun halfOpened() {
        val posture =
            decideSheetPosture(
                widthDp = SHEET_EXPANDED_WIDTH_DP,
                fold =
                    SheetFoldSnapshot(
                        state = SheetFoldState.HalfOpened,
                        axis = SheetHingeAxis.Horizontal,
                        hingeLeftPx = 0,
                        hingeRightPx = HINGE_WIDTH_PX,
                        windowWidthPx = HINGE_WIDTH_PX,
                    ),
            )
        assertEquals(SheetPosture.SinglePane, posture)
        assertNull((posture as? SheetPosture.TwoPane)?.hinge)
    }

    private class FixtureFold(
        private val rect: Rect,
        private val foldState: FoldingFeature.State,
        private val foldOrientation: FoldingFeature.Orientation,
    ) : FoldingFeature {
        override val bounds: Rect
            get() = rect
        override val isSeparating: Boolean
            get() = true
        override val occlusionType: FoldingFeature.OcclusionType
            get() = FoldingFeature.OcclusionType.NONE
        override val orientation: FoldingFeature.Orientation
            get() = foldOrientation
        override val state: FoldingFeature.State
            get() = foldState
    }

    private companion object {
        const val HINGE_WIDTH_DP = 1000
        const val HINGE_WIDTH_PX = 1000
        const val HINGE_LEFT_PX = 480
        const val HINGE_RIGHT_PX = 520
        const val HINGE_HEIGHT_PX = 800
        const val COMPACT_WIDTH_DP = 400
    }
}
