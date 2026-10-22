package com.stratum.core.ui.theme

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Solid offset fill behind the node (neo hard shadow, no blur).
 *
 * Apply **before** [androidx.compose.foundation.background] on custom surfaces so the face
 * paints on top. For Material buttons, attach as an outer [Modifier] — the button face draws
 * above [drawBehind].
 *
 * Prefer [stratumButtonHardShadow] / [stratumCardHardShadow] so offsets come from
 * [StratumElevations] instead of hard-coded theme branches in features.
 *
 * [faceHeight] + [touchTargetHeight] exist for Material buttons (STR-179): the interactive
 * `Surface` inside a Material button wraps the caller's chain in
 * `minimumInteractiveComponentSize`, which inflates the layout node to the touch target
 * (48dp by default) and centers the visible face inside it. This draw scope measures the
 * inflated node, so an outline at the full scope size overshoots the face on every side.
 * When the caller supplies its face height and the scope is exactly that inflated target,
 * the outline is rebuilt at the centered face box instead. Plain surfaces leave both unset
 * and keep the node == face outline.
 */
fun Modifier.stratumHardShadow(
    offset: Dp,
    color: Color,
    shape: Shape,
    faceHeight: Dp? = null,
    touchTargetHeight: Dp = 0.dp,
): Modifier {
    if (offset <= 0.dp) return this
    return drawBehind {
        val offsetPx = offset.toPx()
        val faceHeightPx =
            if (faceHeight != null && touchTargetHeight > 0.dp) {
                val candidatePx = faceHeight.toPx()
                val targetPx = touchTargetHeight.toPx()
                if (size.height > candidatePx && size.height <= targetPx) candidatePx else size.height
            } else {
                size.height
            }
        val faceTopPx = (size.height - faceHeightPx) / 2f
        val outline = shape.createOutline(Size(size.width, faceHeightPx), layoutDirection, this)
        val path = Path().apply { addOutline(outline) }
        withTransform({ translate(left = offsetPx, top = faceTopPx + offsetPx) }) {
            drawPath(path, color)
        }
    }
}

/**
 * Hard-offset shadow for primary/secondary buttons when the active theme enables it.
 * No-ops for Tonal ([StratumElevations.useHardShadow] false). Disabled controls drop the shadow.
 *
 * When [reserveSpace] is true, end/bottom padding equal to the offset keeps parents from
 * clipping the hard edge (full-width CTAs in scroll columns).
 */
@Composable
fun Modifier.stratumButtonHardShadow(
    enabled: Boolean = true,
    shape: Shape = StratumThemeAccessor.shapes.button,
    reserveSpace: Boolean = true,
    faceHeight: Dp? = null,
): Modifier {
    val elevations = StratumThemeAccessor.elevations
    if (!elevations.useHardShadow || !enabled) return this
    val offset = elevations.buttonShadowOffset
    if (offset <= 0.dp) return this
    val touchTargetHeight = LocalMinimumInteractiveComponentSize.current
    val base =
        if (reserveSpace) {
            padding(end = offset, bottom = offset)
        } else {
            this
        }
    return base.stratumHardShadow(
        offset = offset,
        color = elevations.hardShadowColor,
        shape = shape,
        faceHeight = faceHeight,
        touchTargetHeight = touchTargetHeight,
    )
}

/**
 * Hard-offset shadow for raised panels/cards when the active theme enables it.
 * Features should not branch on [StratumThemeId]; call this and let elevations decide.
 */
@Composable
fun Modifier.stratumCardHardShadow(
    shape: Shape = StratumThemeAccessor.shapes.panel,
    reserveSpace: Boolean = true,
): Modifier {
    val elevations = StratumThemeAccessor.elevations
    if (!elevations.useHardShadow) return this
    val offset = elevations.cardShadowOffset
    if (offset <= 0.dp) return this
    val base =
        if (reserveSpace) {
            padding(end = offset, bottom = offset)
        } else {
            this
        }
    return base.stratumHardShadow(
        offset = offset,
        color = elevations.hardShadowColor,
        shape = shape,
    )
}

/**
 * Strong top rule for bottom docks / sticky bars when hard-shadow themes are active.
 * No-ops on Tonal so docks stay flat raised slabs.
 *
 * Apply after [androidx.compose.foundation.background] so the stroke sits on the face.
 */
@Composable
fun Modifier.stratumDockTopRule(): Modifier {
    val elevations = StratumThemeAccessor.elevations
    if (!elevations.useHardShadow) return this
    val strokeWidth = StratumThemeAccessor.shapes.borderWidth
    val color = StratumThemeAccessor.colors.borderStrong
    return drawWithContent {
        drawContent()
        val stroke = strokeWidth.toPx()
        val half = stroke / 2f
        drawLine(
            color = color,
            start = Offset(0f, half),
            end = Offset(size.width, half),
            strokeWidth = stroke,
        )
    }
}
