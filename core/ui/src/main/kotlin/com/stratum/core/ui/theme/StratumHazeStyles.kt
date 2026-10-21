package com.stratum.core.ui.theme

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

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
 *
 * Deliberately NOT `@Immutable`: [HazeBlurStyle] is an opaque style spec, so
 * claiming value immutability for the wrapper would overstate what Compose
 * can rely on. Stability comes from the spec singletons that hold these.
 */
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
                            colors.surfaceInset.copy(alpha = sheetTintAlpha(colors.surfaceInset)),
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

    /**
     * Top clearance for content scrolling under a glass top bar (§12.8): the
     * status-bar inset the bar spans PLUS the bar's own height. Clearing only
     * [topBarHeight] lets content slide up under the bar by exactly the inset,
     * colliding with the title and eating taps (STR-155 field report). Pure
     * function so the formula is unit-pinned: Robolectric goldens run with zero
     * insets and cannot catch an omission here.
     */
    fun topClearance(statusBarTop: Dp): Dp = statusBarTop + topBarHeight
}

/** §12.8 sheet tint depth (materials-Thick rule): near-opaque, lighter over light surfaces. */
internal fun sheetTintAlpha(surfaceInset: Color): Float =
    if (surfaceInset.luminance() >= 0.5f) 0.83f else 0.9f

/**
 * §12.8 sheet glass (ShippopotamusHazeSheet) for overlay drawers and docks that
 * live OUTSIDE their hazeSource subtree (e.g. `StratumTabScaffold`'s
 * bottomOverlay slot): paints the sheet tint as a floor under the
 * [StratumHazeStyles.sheet] blur, clipped to [shape], so the surface never
 * renders fully transparent even where blur is unavailable — the same floor
 * discipline as `StratumTopBar` (STR-155). No-op when glass is inactive;
 * callers keep their solid background for that case.
 */
@Composable
fun Modifier.stratumGlassSheet(shape: Shape): Modifier {
    val hazeState = rememberGlassHazeState() ?: return this
    val colors = StratumThemeAccessor.colors
    return this
        .background(
            color = colors.surfaceInset.copy(alpha = sheetTintAlpha(colors.surfaceInset)),
            shape = shape,
        )
        .clip(shape)
        .hazeBlur(input = HazeInput.Sources(hazeState), style = StratumThemeAccessor.haze.sheet)
}
