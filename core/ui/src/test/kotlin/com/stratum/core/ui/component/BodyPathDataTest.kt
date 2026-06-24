package com.stratum.core.ui.component

import android.graphics.PointF
import android.graphics.RectF
import androidx.compose.ui.geometry.Size
import com.stratum.core.ui.component.musclepicker.BODY_PATHS_BACK
import com.stratum.core.ui.component.musclepicker.BODY_PATHS_FRONT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Guards the body-map asset against the SVG arc-flag parse crash: the upstream
 * `react-native-body-highlighter` data writes arc flags concatenated (e.g. `... 51.1 01-.9`),
 * which Android's `PathParser` rejects with `ArrayIndexOutOfBoundsException`. Every path in
 * both views must round-trip through [parseBodyPaths] (the exact code the composable runs)
 * without throwing. If an asset update reintroduces the pattern, this fails with the path.
 *
 * Also guards the picker's per-view centering: front and back views live in *independent*
 * source coordinate systems (front x≈[100,620], back x≈[900,1300]; both y≈[280,1290]). The
 * picker must fit each view's *own* bounds into the Canvas — a shared box spanning both views
 * would render the active view off-center (the bug this regresses against).
 */
@ExtendWith(RobolectricExtension::class)
class BodyPathDataTest {

    @Test
    @DisplayName("Given every front-view path when parsed then none throw and all are non-null")
    fun everyFrontPathParses() {
        val parsed = parseBodyPaths(BODY_PATHS_FRONT)
        val inputCount = BODY_PATHS_FRONT.sumOf { it.leftPaths.size + it.rightPaths.size }
        val outputCount = parsed.sumOf { it.paths.size }
        assertEquals(inputCount, outputCount, "Some front paths failed to parse (count mismatch)")
    }

    @Test
    @DisplayName("Given every back-view path when parsed then none throw and all are non-null")
    fun everyBackPathParses() {
        val parsed = parseBodyPaths(BODY_PATHS_BACK)
        val inputCount = BODY_PATHS_BACK.sumOf { it.leftPaths.size + it.rightPaths.size }
        val outputCount = parsed.sumOf { it.paths.size }
        assertEquals(inputCount, outputCount, "Some back paths failed to parse (count mismatch)")
    }

    @Test
    @DisplayName("Given the known-failing triceps path when parsed then its arc flags are split")
    fun knownFailingTricepsPathParses() {
        // The original crash path contained an arc with concatenated flags `01`. After
        // pre-cleaning via svg.path (see BodyPathData.kt) its leading `M 287.33,452.44` was
        // re-serialized, so match by the distinctive arc rotation `51.1` rather than the
        // exact (now-different) prefix. The point: this exact path must round-trip through
        // PathParser — if the asset regresses, it throws here.
        val knownBad: String = BODY_PATHS_FRONT
            .flatMap { it.leftPaths + it.rightPaths }
            .first { it.contains("51.1") && it.contains("0.84") }

        val parsed = parseBodyPaths(
            listOf(
                com.stratum.core.ui.component.musclepicker.BodyRegion(
                    target = com.stratum.core.ui.component.musclepicker.BodyTarget.DeltoidRegion,
                    leftPaths = listOf(knownBad),
                    rightPaths = emptyList(),
                ),
            ),
        )
        assertEquals(1, parsed[0].paths.size, "Known-failing path did not parse")
    }

    @Test
    @DisplayName("Given the front view when bounds measured then they cover only front source coords")
    fun frontBoundsCoverFrontSourceRangeOnly() {
        // Regression for the off-center bug: the union must be the *front* view's own box
        // (x≈[100,620], y≈[280,1290]), NOT the both-views fallback (x up to ~1300). If the
        // fix regresses (shared box used again), the right edge blows past 700.
        val bounds = unionBounds(parseBodyPaths(BODY_PATHS_FRONT))

        assertTrue(bounds.left in 50f..150f, "front bounds left=$${bounds.left} should be near 100")
        assertTrue(bounds.right in 500f..700f, "front bounds right=${bounds.right} must stay in front range, not span to ~1300")
        assertTrue(bounds.top in 200f..400f, "front bounds top=${bounds.top} should be near 280")
        assertTrue(bounds.bottom in 1100f..1400f, "front bounds bottom=${bounds.bottom} should be near 1290")
    }

    @Test
    @DisplayName("Given the back view when bounds measured then they cover only back source coords")
    fun backBoundsCoverBackSourceRangeOnly() {
        // Symmetric to the front test: back view lives at x≈[900,1300]. A shared-box
        // regression would report a left edge near 100 instead.
        val bounds = unionBounds(parseBodyPaths(BODY_PATHS_BACK))

        assertTrue(bounds.left in 800f..1000f, "back bounds left=${bounds.left} should be near 900")
        assertTrue(bounds.right in 1200f..1400f, "back bounds right=${bounds.right} should be near 1300")
    }

    @Test
    @DisplayName("Given measured view bounds when transformed then the silhouette centers horizontally")
    fun transformCentersHorizontallyWithinCanvas() {
        // A square canvas (width == height) with a portrait body (taller than wide) is the
        // width-binding case: the body fills the available width, so horizontal slack is zero
        // and the scaled box's horizontal center equals the canvas center (minus the equal
        // padding on both sides). This is the exact symptom the bug reported: body pinned
        // bottom-left; here it must sit centered.
        val bounds = unionBounds(parseBodyPaths(BODY_PATHS_FRONT))
        val canvas = Size(width = 1000f, height = 1000f)
        val matrix = transformMatrix(canvas, bounds)

        assertNotNull(matrix, "matrix must be produced for a valid canvas")
        val srcCenterX = bounds.centerX()
        val mapped = mapPoint(matrix!!, srcCenterX, bounds.centerY())
        val padding = minOf(canvas.width, canvas.height) * DEFAULT_CONTENT_PADDING_FRACTION
        val canvasCenterX = canvas.width / 2f

        assertEquals(canvasCenterX, mapped.x, 1f, "front body center should map to canvas horizontal center (padding=${padding})")
    }

    @Test
    @DisplayName("Given front and back when transformed then both center horizontally (no shared-box drift)")
    fun bothViewsCenterIndependently() {
        // The crux: front (x≈400 center) and back (x≈1100 center) have very different source
        // centers. If a shared box were used, only one could center. With per-view bounds both
        // must map their source center onto the same canvas center.
        val canvas = Size(width = 1000f, height = 1000f)
        val frontBounds = unionBounds(parseBodyPaths(BODY_PATHS_FRONT))
        val backBounds = unionBounds(parseBodyPaths(BODY_PATHS_BACK))
        val frontMatrix = transformMatrix(canvas, frontBounds)!!
        val backMatrix = transformMatrix(canvas, backBounds)!!

        val canvasCenterX = canvas.width / 2f
        val frontMapped = mapPoint(frontMatrix, frontBounds.centerX(), frontBounds.centerY())
        val backMapped = mapPoint(backMatrix, backBounds.centerX(), backBounds.centerY())

        assertEquals(canvasCenterX, frontMapped.x, 1f, "front view must center horizontally")
        assertEquals(canvasCenterX, backMapped.x, 1f, "back view must center horizontally")
    }

    @Test
    @DisplayName("Given degenerate (empty) path set when bounds measured then fallback box is used")
    fun degenerateBoundsFallBackToBothViewsBox() {
        // Empty path set → no measurable geometry → unionBounds must return the fallback so the
        // picker still produces a matrix rather than NPE-ing. Empty path strings parse to null
        // and are dropped by mapNotNull, exercising the zero-paths case. (Malformed non-empty
        // data instead throws NumberFormatException inside PathParser, which is a different —
        // asset-validation — concern covered by the round-trip tests above.)
        val parsed = parseBodyPaths(
            listOf(
                com.stratum.core.ui.component.musclepicker.BodyRegion(
                    target = com.stratum.core.ui.component.musclepicker.BodyTarget.DeltoidRegion,
                    leftPaths = listOf(""),
                    rightPaths = emptyList(),
                ),
            ),
        )
        val bounds = unionBounds(parsed)
        assertTrue(bounds.width() > 0f, "fallback bounds must be non-empty for a degenerate set")
    }

    @Test
    @DisplayName("Given zero-size canvas when transformed then no matrix is produced")
    fun zeroSizeCanvasProducesNoMatrix() {
        val bounds = unionBounds(parseBodyPaths(BODY_PATHS_FRONT))
        assertEquals(null, transformMatrix(Size(0f, 1000f), bounds), "zero-width canvas must yield no matrix")
        assertEquals(null, transformMatrix(Size(1000f, 0f), bounds), "zero-height canvas must yield no matrix")
    }

    private fun mapPoint(matrix: android.graphics.Matrix, x: Float, y: Float): PointF {
        val pts = floatArrayOf(x, y)
        matrix.mapPoints(pts)
        return PointF(pts[0], pts[1])
    }
}
