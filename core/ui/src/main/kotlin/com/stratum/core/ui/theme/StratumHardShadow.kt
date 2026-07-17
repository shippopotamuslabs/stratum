package com.stratum.core.ui.theme

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
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
 */
fun Modifier.stratumHardShadow(
    offset: Dp,
    color: Color,
    shape: Shape,
): Modifier {
    if (offset <= 0.dp) return this
    return drawBehind {
        val offsetPx = offset.toPx()
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = Path().apply { addOutline(outline) }
        withTransform({ translate(left = offsetPx, top = offsetPx) }) {
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
): Modifier {
    val elevations = StratumThemeAccessor.elevations
    if (!elevations.useHardShadow || !enabled) return this
    val offset = elevations.buttonShadowOffset
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
