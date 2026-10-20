package com.stratum.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect

/**
 * Glass Bars Gospel styles (§12.8 of `contracts/UI_DESIGN.md`), derived from a
 * family's semantic colors so light and dark resolve correctly. One shared
 * `HazeState` per screen feeds the bars; these styles are the ONLY place the
 * blur / tint / noise numbers live — components never inline them.
 *
 * API note: §12.8's sketch predates the published Haze 2.0.1 API surface. In
 * 2.0.1 the sketch's `HazeStyle` / `hazeChild` are `HazeBlurStyle` / `hazeBlur`
 * (artifact `haze-blur`), and a style's tint is a [HazeColorEffect.tint] over an
 * opaque `backgroundColor` base — the same construction `HazeMaterials` uses.
 * The gospel's values and behavior are unchanged; only the calls differ.
 *
 * [enabled] is the family switch: the Tonal family adopts glass chrome; Neo
 * Brutalist keeps its solid chrome under the sanctioned carve-out (§11.3
 * ruling 1), so its styles exist but chrome must not apply them while
 * [enabled] is false.
 */
@Immutable
data class StratumHazeStyles(
    val topBar: HazeBlurStyle,
    val bottomBar: HazeBlurStyle,
    val sheet: HazeBlurStyle,
    val enabled: Boolean,
)

internal fun stratumHazeStyles(
    colors: StratumColors,
    enabled: Boolean,
): StratumHazeStyles =
    StratumHazeStyles(
        // bg-base at 65%, blur 24dp (§12.8 ShippopotamusHazeTopBar).
        topBar =
            HazeBlurStyle {
                blurRadius(24.dp)
                backgroundColor(colors.surfaceBase)
                colorEffects(listOf(HazeColorEffect.tint(colors.surfaceBase.copy(alpha = 0.65f))))
                noiseFactor(0.5f)
            },
        // surface-1 at 75%, blur 20dp (§12.8 ShippopotamusHazeBottomBar).
        bottomBar =
            HazeBlurStyle {
                blurRadius(20.dp)
                backgroundColor(colors.surfaceRaised)
                colorEffects(listOf(HazeColorEffect.tint(colors.surfaceRaised.copy(alpha = 0.75f))))
                noiseFactor(0.5f)
            },
        // The gospel's Thick sheet (§12.8 ShippopotamusHazeSheet): the materials
        // Thick construction (blur 24dp, near-opaque tint) over the surface-3 tint.
        sheet =
            HazeBlurStyle {
                blurRadius(24.dp)
                backgroundColor(colors.surfaceInset)
                colorEffects(
                    listOf(
                        HazeColorEffect.tint(
                            colors.surfaceInset.copy(
                                alpha = if (colors.surfaceInset.luminance() >= 0.5f) 0.83f else 0.9f,
                            ),
                        ),
                    ),
                )
            },
        enabled = enabled,
    )

/** Canonical glass chrome heights (§12.8): content hosted under glass pads its ends by these. */
object StratumGlassChrome {
    val topBarHeight: Dp = 64.dp
    val bottomBarHeight: Dp = 80.dp
}
