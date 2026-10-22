package com.stratum.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.theme.stratumCardHardShadow

/**
 * The one card/panel surface (§4.1) — features compose this instead of raw
 * `Card`/`Surface` plus hand-rolled borders.
 *
 * §12.3 mapping: Rest = surfaceRaised at the medium corner, Elevated = the large
 * corner, Sunken = surfaceSunken. Stratum has no surface-2 role (the §12.2 tonal
 * steps fold it into raised, see the StratumTheme bridge), so Elevated separates
 * by corner and shadow rather than a fourth container color. Tonal stays flat and
 * borderless; Neo takes the 2dp strong border on Rest/Elevated, with the
 * elevations hard shadow on Elevated. Content padding is the caller's (cards
 * pad 16dp by convention, §12.3).
 */
@Composable
fun StratumSurface(
    modifier: Modifier = Modifier,
    level: StratumSurfaceLevel = StratumSurfaceLevel.Rest,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val elevations = StratumThemeAccessor.elevations
    val shape =
        when (level) {
            StratumSurfaceLevel.Elevated -> shapes.large
            StratumSurfaceLevel.Rest,
            StratumSurfaceLevel.Sunken,
            -> shapes.medium
        }
    val container =
        when (level) {
            StratumSurfaceLevel.Rest,
            StratumSurfaceLevel.Elevated,
            -> colors.surfaceRaised
            StratumSurfaceLevel.Sunken -> colors.surfaceSunken
        }
    val neo = elevations.useHardShadow
    Box(
        modifier =
            modifier
                .then(
                    if (neo && level == StratumSurfaceLevel.Elevated) {
                        Modifier.stratumCardHardShadow(shape = shape)
                    } else {
                        Modifier
                    },
                )
                // After the hard shadow so the shadow itself stays unclipped;
                // the clip bounds the background, content, and click ripple.
                .clip(shape)
                .background(color = container, shape = shape)
                .then(
                    if (neo && level != StratumSurfaceLevel.Sunken) {
                        Modifier.border(
                            width = shapes.borderWidth,
                            color = colors.borderStrong,
                            shape = shape,
                        )
                    } else {
                        Modifier
                    },
                )
                .then(
                    if (onClick != null) {
                        // §12.5: a clickable surface guarantees the 48dp floor
                        // itself, whatever the caller puts inside it.
                        Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .clickable(role = Role.Button, onClick = onClick)
                    } else {
                        Modifier
                    },
                ),
    ) {
        content()
    }
}
