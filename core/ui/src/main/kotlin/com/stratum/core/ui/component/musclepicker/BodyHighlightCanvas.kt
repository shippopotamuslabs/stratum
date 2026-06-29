package com.stratum.core.ui.component.musclepicker

import android.graphics.Path as AndroidPath
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.domain.onboarding.MuscleGroup

/**
 * Read-only body silhouette with a single muscle group highlighted. Used by volume summary cards.
 * Crops to upper or lower body so quads/hamstrings fill the icon instead of a tiny full-body figure.
 */
@Composable
internal fun BodyHighlightCanvas(
    regions: List<BodyRegion>,
    highlightMuscle: MuscleGroup,
    modifier: Modifier = Modifier,
) {
    val accent = StratumThemeAccessor.colors.accent
    val surfaceRaised = StratumThemeAccessor.colors.surfaceRaised
    val borderSubtle = StratumThemeAccessor.colors.borderSubtle
    val crop = bodyCropForMuscle(highlightMuscle)

    val parsedRegions = remember(regions) { parseBodyPaths(regions) }
    val visibleRegions = remember(parsedRegions, crop) {
        parsedRegions.filter { region -> bodyTargetCropTier(region.target) == crop }
    }
    val unionBounds = remember(visibleRegions) { unionBoundsForCrop(visibleRegions) }
    val strokePx = with(LocalDensity.current) { 1.dp.toPx() }

    Canvas(modifier = modifier) {
        val matrix = transformMatrix(size, unionBounds) ?: return@Canvas
        val strokePaint =
            android.graphics.Paint().apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = strokePx
                strokeJoin = android.graphics.Paint.Join.ROUND
                strokeCap = android.graphics.Paint.Cap.ROUND
                isAntiAlias = true
                color = borderSubtle.toArgb()
            }
        val fillPaint = android.graphics.Paint().apply { isAntiAlias = true }
        val transformed = AndroidPath()
        val canvas = drawContext.canvas.nativeCanvas
        for (region in visibleRegions) {
            val isHighlighted = regionMatchesMuscle(region.target, highlightMuscle)
            val fillColor =
                when {
                    isHighlighted -> accent
                    region.target is BodyTarget.DeltoidRegion && isDeltoidHead(highlightMuscle) ->
                        accent.copy(alpha = 0.5f)
                    else -> surfaceRaised
                }.toArgb()
            fillPaint.color = fillColor
            for (src in region.paths) {
                transformed.set(src)
                transformed.transform(matrix)
                canvas.drawPath(transformed, fillPaint)
                canvas.drawPath(transformed, strokePaint)
            }
        }
    }
}

internal fun bodyViewForMuscle(muscle: MuscleGroup): BodyView? {
    if (muscle in BACK_VIEW_PREFERRED_MUSCLES) {
        val onBack =
            BODY_PATHS_BACK.any { region -> regionMatchesMuscle(region.target, muscle) }
        if (onBack) return BodyView.Back
    }
    val onFront =
        BODY_PATHS_FRONT.any { region -> regionMatchesMuscle(region.target, muscle) }
    val onBack =
        BODY_PATHS_BACK.any { region -> regionMatchesMuscle(region.target, muscle) }
    return when {
        onBack && !onFront -> BodyView.Back
        onFront -> BodyView.Front
        else -> null
    }
}

private val BACK_VIEW_PREFERRED_MUSCLES =
    setOf(
        MuscleGroup.Tricep,
        MuscleGroup.PosteriorDeltoid,
        MuscleGroup.Calf,
    )

internal fun bodyRegionsForHighlight(muscle: MuscleGroup): List<BodyRegion> {
    val view = bodyViewForMuscle(muscle) ?: return emptyList()
    return if (view == BodyView.Front) BODY_PATHS_FRONT else BODY_PATHS_BACK
}

internal fun unionBoundsForCrop(parsed: List<ParsedRegion>): RectF {
    val measured = RectF()
    parsed.forEach { region -> measured.union(region.bounds) }
    return if (measured.width() <= 0f || measured.height() <= 0f) {
        RectF(UNION_FALLBACK_BOUNDS)
    } else {
        measured
    }
}

private val UNION_FALLBACK_BOUNDS =
    RectF(
        SRC_LEFT,
        SRC_TOP,
        SRC_LEFT + SRC_WIDTH,
        SRC_TOP + SRC_HEIGHT,
    )

private const val SRC_LEFT = 80f
private const val SRC_TOP = 280f
private const val SRC_WIDTH = 1250f
private const val SRC_HEIGHT = 1010f

private fun isDeltoidHead(muscle: MuscleGroup): Boolean =
    muscle == MuscleGroup.AnteriorDeltoid ||
        muscle == MuscleGroup.LateralDeltoid ||
        muscle == MuscleGroup.PosteriorDeltoid

private fun regionMatchesMuscle(target: BodyTarget, muscle: MuscleGroup): Boolean =
    when (target) {
        is BodyTarget.Single -> target.muscle == muscle
        is BodyTarget.DeltoidRegion -> isDeltoidHead(muscle)
    }
