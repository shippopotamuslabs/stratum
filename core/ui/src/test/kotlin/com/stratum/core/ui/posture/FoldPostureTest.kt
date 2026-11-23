package com.stratum.core.ui.posture

import android.graphics.Rect
import androidx.window.layout.FoldingFeature
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/** Robolectric so the folding feature fixture's [Rect] carries real bounds. */
@ExtendWith(RobolectricExtension::class)
class FoldPostureTest {
    @Test
    @DisplayName("flat fold expands at any width")
    fun flatFoldExpands() {
        val posture =
            decideFoldPosture(
                widthDp = COMPACT_WIDTH_DP,
                fold = snapshot(state = FoldState.Flat),
            )
        assertEquals(FoldPosture.TwoPane(hinge = null), posture)
    }

    @Test
    @DisplayName("half-opened stays one pane even when the window is wide")
    fun halfOpenedStaysSingle() {
        val posture =
            decideFoldPosture(
                widthDp = EXPANDED_POSTURE_WIDTH_DP * 2,
                fold =
                    snapshot(
                        state = FoldState.HalfOpened,
                        axis = HingeAxis.Horizontal,
                        hingeRightPx = HINGE_WIDTH_PX,
                    ),
            )
        assertEquals(FoldPosture.SinglePane, posture)
    }

    @Test
    @DisplayName("no fold expands at the expanded width and stays single below it")
    fun widthDecidesWithoutFold() {
        assertEquals(
            FoldPosture.TwoPane(hinge = null),
            decideFoldPosture(widthDp = EXPANDED_POSTURE_WIDTH_DP, fold = null),
        )
        assertEquals(
            FoldPosture.SinglePane,
            decideFoldPosture(widthDp = EXPANDED_POSTURE_WIDTH_DP - 1, fold = null),
        )
    }

    @Test
    @DisplayName("a vertical hinge band becomes fractional gap bounds")
    fun verticalHingeGap() {
        val hinge = (flatHingeFixture() as FoldPosture.TwoPane).hinge
        assertEquals(HINGE_LEFT_PX.toFloat() / HINGE_WIDTH_PX.toFloat(), hinge?.start)
        assertEquals(HINGE_RIGHT_PX.toFloat() / HINGE_WIDTH_PX.toFloat(), hinge?.end)
    }

    @Test
    @DisplayName("a horizontal hinge yields no gap")
    fun horizontalHingeHasNoGap() {
        val posture =
            decideFoldPosture(
                widthDp = HINGE_WIDTH_DP,
                fold =
                    snapshot(
                        state = FoldState.Flat,
                        axis = HingeAxis.Horizontal,
                        hingeLeftPx = HINGE_LEFT_PX,
                        hingeRightPx = HINGE_RIGHT_PX,
                    ),
            )
        assertNull((posture as FoldPosture.TwoPane).hinge)
    }

    @Test
    @DisplayName("a full-width hinge band yields no gap")
    fun fullWidthBandHasNoGap() {
        val posture =
            decideFoldPosture(
                widthDp = HINGE_WIDTH_DP,
                fold = snapshot(state = FoldState.Flat, hingeRightPx = HINGE_WIDTH_PX),
            )
        assertEquals(FoldPosture.TwoPane(hinge = null), posture)
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
            decideFoldPosture(
                widthDp = HINGE_WIDTH_DP,
                fold = feature.toFoldSnapshot(windowWidthPx = HINGE_WIDTH_PX),
            )
        assertEquals(flatHingeFixture(), posture)
    }

    @Test
    @DisplayName("a half-opened folding feature maps to a half-opened snapshot")
    fun halfOpenedFeatureMaps() {
        val feature =
            FixtureFold(
                rect = Rect(0, HINGE_LEFT_PX, HINGE_WIDTH_PX, HINGE_RIGHT_PX),
                foldState = FoldingFeature.State.HALF_OPENED,
                foldOrientation = FoldingFeature.Orientation.HORIZONTAL,
            )
        val snapshot = feature.toFoldSnapshot(windowWidthPx = HINGE_WIDTH_PX)
        assertEquals(FoldState.HalfOpened, snapshot.state)
        assertEquals(HingeAxis.Horizontal, snapshot.axis)
    }

    private fun snapshot(
        state: FoldState,
        axis: HingeAxis = HingeAxis.Vertical,
        hingeLeftPx: Int = 0,
        hingeRightPx: Int = 0,
    ): FoldSnapshot =
        FoldSnapshot(
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
