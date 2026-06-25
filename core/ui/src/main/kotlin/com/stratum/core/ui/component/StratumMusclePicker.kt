package com.stratum.core.ui.component

import android.graphics.Matrix
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.PathParser
import com.stratum.core.ui.component.musclepicker.BODY_PATHS_BACK
import com.stratum.core.ui.component.musclepicker.BODY_PATHS_FRONT
import com.stratum.core.ui.component.musclepicker.BodyRegion
import com.stratum.core.ui.component.musclepicker.BodyTarget
import com.stratum.core.ui.component.musclepicker.BodyView
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.domain.onboarding.MuscleGroup

/**
 * Display labels for the three deltoid heads shown in the sub-picker. These must match the
 * [MuscleGroup] deltoid labels produced by the feature's `toDisplayLabel()` mapping
 * ("Front Delts" / "Side Delts" / "Rear Delts") so the emitted toggle labels round-trip
 * through the same string contract the ViewModel consumes.
 */
internal val DELTOID_LABELS: List<Pair<MuscleGroup, String>> = listOf(
    MuscleGroup.AnteriorDeltoid to "Front Delts",
    MuscleGroup.LateralDeltoid to "Side Delts",
    MuscleGroup.PosteriorDeltoid to "Rear Delts",
)

/**
 * Single-muscle display labels owned by the picker so it stays in `:core:ui` without importing
 * the feature's mapping. Kept in sync with the feature's `toDisplayLabel()`; both must agree
 * for the emitted labels to round-trip.
 */
internal fun MuscleGroup.toDisplayLabel(): String = when (this) {
    MuscleGroup.Chest -> "Chest"
    MuscleGroup.Back -> "Back"
    MuscleGroup.Quad -> "Quads"
    MuscleGroup.Hamstring -> "Hamstrings"
    MuscleGroup.Glute -> "Glutes"
    MuscleGroup.PosteriorDeltoid -> "Rear Delts"
    MuscleGroup.LateralDeltoid -> "Side Delts"
    MuscleGroup.AnteriorDeltoid -> "Front Delts"
    MuscleGroup.Tricep -> "Triceps"
    MuscleGroup.Bicep -> "Biceps"
    MuscleGroup.Trap -> "Traps"
    MuscleGroup.Calf -> "Calves"
    MuscleGroup.Forearm -> "Forearms"
    MuscleGroup.Ab -> "Abs"
    MuscleGroup.LowerBack -> "Lower Back"
    MuscleGroup.Abductor -> "Abductors"
    MuscleGroup.Adductor -> "Adductors"
}

/**
 * A tappable front/back body illustration for selecting muscle groups. Tap a region to
 * toggle its specialization; tap the flip control to switch views. The deltoid region is a
 * composite — tapping it opens an inline sub-picker for Front/Side/Rear Delts.
 *
 * The picker emits display-label strings via [onMuscleToggled] so callers stay decoupled from
 * the region geometry (ARCHITECTURE.md §4.3 — UI maps to presentation labels, not domain).
 *
 * @param selectedMuscleLabels the currently-specialized display labels (e.g. "Chest",
 *  "Front Delts"). Drives which regions render highlighted.
 * @param onMuscleToggled invoked with a display label when a region or deltoid chip is tapped.
 *  Toggling an already-selected label deselects it.
 * @param enabled when false, unselected regions are disabled (cap reached); selected regions
 *  remain tappable to deselect.
 */
@Composable
fun StratumMusclePicker(
    selectedMuscleLabels: Set<String>,
    onMuscleToggled: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var view by remember { mutableStateOf(BodyView.Front) }
    var deltoidPickerOpen by remember { mutableStateOf(false) }

    val regions = remember(view) { if (view == BodyView.Front) BODY_PATHS_FRONT else BODY_PATHS_BACK }

    val configuration = LocalConfiguration.current
    val diagramMaxHeight = remember(configuration) {
        (configuration.screenHeightDp * 0.45f).dp
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BodyCanvas(
            regions = regions,
            selectedMuscleLabels = selectedMuscleLabels,
            enabled = enabled,
            onTapTarget = { target ->
                when (target) {
                    is BodyTarget.Single -> onMuscleToggled(target.muscle.toDisplayLabel())
                    is BodyTarget.DeltoidRegion -> deltoidPickerOpen = true
                }
            },
            modifier = Modifier
                // Set explicit height and aspect ratio constraints to prevent layout conflicts.
                .height(diagramMaxHeight)
                .aspectRatio(BODY_ASPECT_RATIO)
                .semantics { contentDescription = "Body map, ${view.name.lowercase()} view" },
        )

        FlipControl(view = view, onFlip = { deltoidPickerOpen = false; view = it })

        if (deltoidPickerOpen) {
            DeltoidSubPickerDialog(
                selectedLabels = selectedMuscleLabels,
                enabled = enabled,
                onToggle = onMuscleToggled,
                onDismiss = { deltoidPickerOpen = false },
            )
        }
    }
}

/**
 * The body silhouette's width:height. Chosen to frame the front view (≈0.51) snugly so the
 * silhouette renders large and sits toward the top of the Canvas, leaving room below for the
 * deltoid sub-picker chips. The back view (≈0.40) is height-limited and centers horizontally
 * via [transformMatrix]; the flip control keeps both views usable at this ratio.
 */
private const val BODY_ASPECT_RATIO = 0.50f



@Composable
private fun BodyCanvas(
    regions: List<BodyRegion>,
    selectedMuscleLabels: Set<String>,
    enabled: Boolean,
    onTapTarget: (BodyTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val accent = StratumThemeAccessor.colors.accent
    val surfaceRaised = StratumThemeAccessor.colors.surfaceRaised
    val surfaceSunken = StratumThemeAccessor.colors.surfaceSunken
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
    is BodyTarget.Single -> target.muscle.toDisplayLabel() in selected
    // Deltoids count as fully "selected" only when all 3 heads are chosen; 1-2 is "partial".
    is BodyTarget.DeltoidRegion -> selectedDeltoidCount(selected) == 3
}

private fun selectedDeltoidCount(selected: Set<String>): Int =
    DELTOID_LABELS.count { (_, label) -> label in selected }

/**
 * Segmented front/back view toggle. Renders one [StratumFilterChip] per view; the active view is
 * shown selected. A segmented control is more discoverable than a single "Show other" button
 * because it communicates *both* that a second view exists and which one is currently shown.
 */
@Composable
private fun FlipControl(view: BodyView, onFlip: (BodyView) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        BodyView.entries.forEach { option ->
            val isSelected = option == view
            StratumFilterChip(
                label = option.name,
                selected = isSelected,
                onSelectedChange = { onFlip(option) },
                modifier = Modifier.semantics {
                    contentDescription = "Show ${option.name.lowercase()} view" +
                        if (isSelected) ", current view" else ""
                },
            )
        }
    }
}

@Composable
private fun DeltoidSubPickerDialog(
    selectedLabels: Set<String>,
    enabled: Boolean,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(
                    color = StratumThemeAccessor.colors.surfaceRaised,
                    shape = StratumThemeAccessor.shapes.panel,
                )
                .border(
                    width = StratumThemeAccessor.shapes.borderWidth,
                    color = StratumThemeAccessor.colors.borderSubtle,
                    shape = StratumThemeAccessor.shapes.panel,
                )
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StratumText(
                text = "Select Shoulder Focus",
                style = StratumThemeAccessor.typography.titleLarge,
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DELTOID_LABELS.forEach { (_, label) ->
                    val isSelected = label in selectedLabels
                    StratumFilterChip(
                        label = label,
                        selected = isSelected,
                        enabled = enabled || isSelected,
                        onSelectedChange = { onToggle(label) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = "$label, ${if (isSelected) "selected" else "not selected"}"
                            },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                StratumButton(text = "Done", onClick = onDismiss)
            }
        }
    }
}

// --- Path transform --------------------------------------------------------

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
