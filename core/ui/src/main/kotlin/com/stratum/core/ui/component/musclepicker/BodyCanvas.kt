package com.stratum.core.ui.component.musclepicker

import android.graphics.Matrix
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser
import com.stratum.core.ui.theme.StratumThemeAccessor

@Composable
internal fun BodyCanvas(
    regions: List<BodyRegion>,
    selectedMuscleLabels: Set<String>,
    enabled: Boolean,
    onTapTarget: (BodyTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val accent = StratumThemeAccessor.colors.accent
    val surfaceRaised = StratumThemeAccessor.colors.surfaceRaised
    val borderSubtle = StratumThemeAccessor.colors.borderSubtle

    // Pre-parse to android paths once per region set; geometry is static. The union bounds
    // are measured from the active view's paths so the silhouette centers in the Canvas
    // (front and back views use independent source coordinate systems).
    val parsedRegions = remember(regions) { parseBodyPaths(regions) }
    val unionBounds = remember(parsedRegions) { unionBounds(parsedRegions) }

    var canvasSize by remember { mutableStateOf<Size?>(null) }
    val strokePx = with(density) { 1.dp.toPx() }

    Canvas(
        modifier = modifier
            .pointerInput(regions, enabled) {
                detectTapGestures { offset ->
                    val size = canvasSize ?: return@detectTapGestures
                    val matrix = transformMatrix(size, unionBounds) ?: return@detectTapGestures
                    val probe = AndroidPath()
                    val clip = android.graphics.Region(
                        offset.x.toInt(), offset.y.toInt(),
                        offset.x.toInt() + 1, offset.y.toInt() + 1,
                    )
                    // Iterate in draw order; first region whose outline contains the tap wins.
                    for (region in parsedRegions) {
                        for (src in region.paths) {
                            probe.set(src)
                            probe.transform(matrix)
                            val hit = android.graphics.Region()
                            hit.setPath(probe, clip)
                            if (!hit.bounds.isEmpty) {
                                val isSelected = isRegionSelected(region.target, selectedMuscleLabels)
                                if (!enabled && !isSelected) return@detectTapGestures
                                onTapTarget(region.target)
                                return@detectTapGestures
                            }
                        }
                    }
                }
            }
            .semantics { stateDescription = "${selectedMuscleLabels.size} muscle groups selected" },
    ) {
        canvasSize = size
        val matrix = transformMatrix(size, unionBounds) ?: return@Canvas
        val strokePaint = android.graphics.Paint().apply {
            style = android.graphics.Paint.Style.STROKE
            this.strokeWidth = strokePx
            strokeJoin = android.graphics.Paint.Join.ROUND
            strokeCap = android.graphics.Paint.Cap.ROUND
            isAntiAlias = true
        }
        val fillPaint = android.graphics.Paint().apply { isAntiAlias = true }
        val transformed = AndroidPath()
        val canvas = drawContext.canvas.nativeCanvas
        for (region in parsedRegions) {
            val isSelected = isRegionSelected(region.target, selectedMuscleLabels)
            val deltoidCount = if (region.target is BodyTarget.DeltoidRegion) {
                selectedDeltoidCount(selectedMuscleLabels)
            } else 0
            val fill = when {
                isSelected -> accent
                deltoidCount == 1 -> accent.copy(alpha = 0.35f)
                deltoidCount == 2 -> accent.copy(alpha = 0.70f)
                else -> surfaceRaised
            }.toArgb()
            fillPaint.color = fill
            strokePaint.color = borderSubtle.toArgb()
            for (src in region.paths) {
                transformed.set(src)
                transformed.transform(matrix)
                canvas.drawPath(transformed, fillPaint)
                canvas.drawPath(transformed, strokePaint)
            }
        }
    }
}

private fun isRegionSelected(target: BodyTarget, selected: Set<String>): Boolean = when (target) {
    is BodyTarget.Single -> target.muscle.displayLabel in selected
    // Deltoids count as fully "selected" only when all 3 heads are chosen; 1-2 is "partial".
    is BodyTarget.DeltoidRegion -> selectedDeltoidCount(selected) == 3
}

internal fun selectedDeltoidCount(selected: Set<String>): Int =
    DELTOID_LABELS.count { (_, label) -> label in selected }

internal class ParsedRegion(
    val target: BodyTarget,
    val paths: List<AndroidPath>,
    /**
     * Source-space bounding box of [paths]. Measured at parse time via
     * [AndroidPath.computeBounds] so the picker can fit each view's *own* geometry into its
     * Canvas — front and back views have independent source coordinate systems (see
     * BodyPathData.kt), so a shared box would push the active view off-center.
     */
    val bounds: RectF,
)

/**
 * Empirical source-space bounding box spanning *both* views' path coordinates, kept as a
 * last-resort fallback when a view's paths cannot be measured (e.g. an empty path set). The
 * picker normally fits each view's measured union into its Canvas instead.
 */
private val UNION_FALLBACK = RectF(SRC_LEFT, SRC_TOP, SRC_LEFT + SRC_WIDTH, SRC_TOP + SRC_HEIGHT)
private const val SRC_LEFT = 80f
private const val SRC_TOP = 280f
private const val SRC_WIDTH = 1250f
private const val SRC_HEIGHT = 1010f

/**
 * Union of every region's source-space bounds in [parsed]. Used to scale-fit the *active view*
 * (front or back) into the Canvas, so the silhouette centers regardless of which view's
 * coordinate system the paths live in. Falls back to [UNION_FALLBACK] only when no region has
 * measurable bounds (degenerate/empty path set) — seeding the fold with the fallback would
 * otherwise swallow the real per-view bounds, since the fallback box spans both views.
 */
internal fun unionBounds(parsed: List<ParsedRegion>): RectF {
    val measured = RectF()
    parsed.forEach { region -> measured.union(region.bounds) }
    return if (measured.width() <= 0f || measured.height() <= 0f) {
        RectF(UNION_FALLBACK)
    } else {
        measured
    }
}

/**
 * Builds the affine matrix mapping source path space into [canvasSize], centered both
 * horizontally and vertically and uniform-scaled so the silhouette keeps its proportions.
 *
 * [srcBounds] is the active view's measured source bounding box (see [unionBounds]). A scale
 * fit already centers the geometry *within the active view's own box*; the slack on the
 * non-binding axis is split evenly by the offset terms, so the body renders centered rather
 * than pinned to an edge. [contentPaddingFraction] insets the fit region on every side so the
 * silhouette is enlarged-but-bounded rather than edge-to-edge.
 */
internal fun transformMatrix(
    canvasSize: Size,
    srcBounds: RectF,
    contentPaddingFraction: Float = DEFAULT_CONTENT_PADDING_FRACTION,
): Matrix? {
    if (canvasSize.width <= 0f || canvasSize.height <= 0f) return null
    val inset = minOf(canvasSize.width, canvasSize.height) * contentPaddingFraction
    val availW = (canvasSize.width - inset * 2f).coerceAtLeast(0f)
    val availH = (canvasSize.height - inset * 2f).coerceAtLeast(0f)
    val srcW = srcBounds.width().coerceAtLeast(0f)
    val srcH = srcBounds.height().coerceAtLeast(0f)
    if (srcW <= 0f || srcH <= 0f) return null
    val scale = minOf(availW / srcW, availH / srcH)
    // Center the scaled source box inside the padded region.
    val dx = inset + (availW - srcW * scale) / 2f - srcBounds.left * scale
    val dy = inset + (availH - srcH * scale) / 2f - srcBounds.top * scale
    return Matrix().apply {
        setScale(scale, scale)
        postTranslate(dx, dy)
    }
}

/** Inset applied to the Canvas on each side before fitting the body (keeps it off the edges). */
internal const val DEFAULT_CONTENT_PADDING_FRACTION = 0.04f

/**
 * Parses each [BodyRegion]'s SVG path strings into [ParsedRegion]s. The path data in
 * [BODY_PATHS_FRONT]/[BODY_PATHS_BACK] is pre-serialized to canonical, PathParser-safe form
 * (see BodyPathData.kt), so no runtime normalization is needed — this just hands each string
 * to [PathParser]. Pulled out of the composable so a Robolectric test can exercise the exact
 * parse path every path must survive.
 *
 * Each region also records the source-space bounding box of its parsed paths (via
 * [AndroidPath.computeBounds]) so the picker can fit the *active view's* geometry — not a box
 * spanning both views — into its Canvas.
 */
internal fun parseBodyPaths(regions: List<BodyRegion>): List<ParsedRegion> =
    regions.map { region ->
        val paths = (region.leftPaths + region.rightPaths).mapNotNull { svg ->
            PathParser.createPathFromPathData(svg)
        }
        val bounds = RectF()
        val scratch = RectF()
        paths.forEach { path ->
            path.computeBounds(scratch, true)
            bounds.union(scratch)
        }
        ParsedRegion(target = region.target, paths = paths, bounds = bounds)
    }
