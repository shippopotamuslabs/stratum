package com.stratum.core.ui.component

import android.graphics.Rect
import androidx.window.layout.FoldingFeature
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/** Robolectric so the folding-feature fixture's [Rect] carries real bounds. */
@ExtendWith(RobolectricExtension::class)
class StratumPanePostureTest {
    @Test
    @DisplayName("flat fold expands at any width")
    fun flatFoldExpands() {
        val posture =
            decidePanePosture(
                widthDp = COMPACT_WIDTH_DP,
                fold = snapshot(state = PaneFoldState.Flat),
            )
        assertEquals(PanePosture.TwoPane(hinge = null), posture)
    }

    @Test
    @DisplayName("half-opened stays one pane even when the window is wide")
    fun halfOpenedStaysSingle() {
        val posture =
            decidePanePosture(
                widthDp = PANE_EXPANDED_WIDTH_DP + 360,
                fold = snapshot(state = PaneFoldState.HalfOpened, hingeLeftPx = 480, hingeRightPx = 520),
            )
        assertEquals(PanePosture.SinglePane, posture)
    }

    @Test
    @DisplayName("no fold expands at the expanded width and stays single below it")
    fun widthDecidesWithoutFold() {
        assertEquals(
            PanePosture.TwoPane(hinge = null),
            decidePanePosture(widthDp = PANE_EXPANDED_WIDTH_DP, fold = null),
        )
        assertEquals(
            PanePosture.SinglePane,
            decidePanePosture(widthDp = PANE_EXPANDED_WIDTH_DP - 1, fold = null),
        )
    }

    @Test
    @DisplayName("a vertical hinge band becomes fractional gap bounds")
    fun verticalHingeGap() {
        val hinge = (paneFlatHingeFixture() as PanePosture.TwoPane).hinge
        assertEquals(HINGE_LEFT_PX.toFloat() / HINGE_WIDTH_PX.toFloat(), hinge?.start)
        assertEquals(HINGE_RIGHT_PX.toFloat() / HINGE_WIDTH_PX.toFloat(), hinge?.end)
    }

    @Test
    @DisplayName("a horizontal hinge yields no gap")
    fun horizontalHingeHasNoGap() {
        val posture =
            decidePanePosture(
                widthDp = HINGE_WIDTH_DP,
                fold =
                    snapshot(
                        state = PaneFoldState.Flat,
                        axis = PaneHingeAxis.Horizontal,
                        hingeLeftPx = HINGE_LEFT_PX,
                        hingeRightPx = HINGE_RIGHT_PX,
                    ),
            )
        assertNull((posture as PanePosture.TwoPane).hinge)
    }

    @Test
    @DisplayName("a hinge band with no width or covering the window splits in half")
    fun degenerateHingeSplitsInHalf() {
        val empty = decidePanePosture(widthDp = HINGE_WIDTH_DP, fold = snapshot(state = PaneFoldState.Flat))
        val full =
            decidePanePosture(
                widthDp = HINGE_WIDTH_DP,
                fold = snapshot(state = PaneFoldState.Flat, hingeLeftPx = 0, hingeRightPx = HINGE_WIDTH_PX),
            )
        assertEquals(PanePosture.TwoPane(hinge = null), empty)
        assertEquals(PanePosture.TwoPane(hinge = null), full)
    }

    @Test
    @DisplayName("a folding feature fixture is the same hinge the screenshots use")
    fun foldingFeatureFixtureMatchesDecision() {
        val feature =
            FixtureFold(
                rect = Rect(HINGE_LEFT_PX, 0, HINGE_RIGHT_PX, HINGE_HEIGHT_PX),
                foldState = FoldingFeature.State.FLAT,
                foldOrientation = FoldingFeature.Orientation.VERTICAL,
            )
        val posture =
            decidePanePosture(
                widthDp = HINGE_WIDTH_DP,
                fold = feature.toPaneFoldSnapshot(windowWidthPx = HINGE_WIDTH_PX),
            )
        assertEquals(paneFlatHingeFixture(), posture)
    }

    @Test
    @DisplayName("a half-opened folding feature maps to half-opened")
    fun halfOpenedFeatureMapsToHalfOpened() {
        val feature =
            FixtureFold(
                rect = Rect(0, HINGE_LEFT_PX, HINGE_WIDTH_PX, HINGE_RIGHT_PX),
                foldState = FoldingFeature.State.HALF_OPENED,
                foldOrientation = FoldingFeature.Orientation.HORIZONTAL,
            )
        val snapshot = feature.toPaneFoldSnapshot(windowWidthPx = HINGE_WIDTH_PX)
        assertEquals(PaneFoldState.HalfOpened, snapshot.state)
        assertEquals(PaneHingeAxis.Horizontal, snapshot.axis)
        assertEquals(PanePosture.SinglePane, decidePanePosture(HINGE_WIDTH_DP, snapshot))
    }

    private fun snapshot(
        state: PaneFoldState,
        axis: PaneHingeAxis = PaneHingeAxis.Vertical,
        hingeLeftPx: Int = 0,
        hingeRightPx: Int = 0,
    ): PaneFoldSnapshot =
        PaneFoldSnapshot(
            state = state,
            axis = axis,
            hingeLeftPx = hingeLeftPx,
            hingeRightPx = hingeRightPx,
            windowWidthPx = HINGE_WIDTH_PX,
        )

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
