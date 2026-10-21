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
    val dock: HazeBlurStyle,
    val enabled: Boolean,
)

internal fun stratumHazeStyles(
    colors: StratumColors,
    enabled: Boolean,
): StratumHazeStyles =
    StratumHazeStyles(
        // bg-base at 65%, blur 24dp (§12.8 ShippopotamusHazeTopBar).
        topBar = tintedBlurStyle(24.dp, colors.surfaceBase, tintAlpha = 0.65f, noise = 0.5f),
        // surface-1 at 75%, blur 20dp (§12.8 ShippopotamusHazeBottomBar).
        bottomBar = tintedBlurStyle(20.dp, colors.surfaceRaised, tintAlpha = 0.75f, noise = 0.5f),
        // The gospel's Thick sheet (§12.8 ShippopotamusHazeSheet): the materials
        // Thick construction (blur 24dp, near-opaque tint) over the surface-3 tint.
        sheet = tintedBlurStyle(24.dp, colors.surfaceInset, tintAlpha = sheetTintAlpha(colors.surfaceInset)),
        // Dock (STR-162): the sheet construction at BAR depth. A permanent dock
        // (the active-workout drawer) is not a modal sheet — the Thick tint
        // (0.90 dark) leaves only ~10% of the blurred content visible and reads
        // as a flat slab on device. Same blur and base as the sheet, tinted at
        // the bottom bar's 0.75 so the frosting actually reads.
        dock = tintedBlurStyle(24.dp, colors.surfaceInset, tintAlpha = DOCK_TINT_ALPHA),
        enabled = enabled,
    )

/**
 * The one blur-style construction every §12.8 style shares: a blur radius
 * over an opaque [base], a single tint of that base at [tintAlpha], and an
 * optional noise factor (bars carry the gospel's 0.5 grain; sheets and the
 * dock follow the materials construction, which has none). Styles differ
 * ONLY in these inputs — a future blur/tint discipline change lands here
 * once instead of drifting across copy-pasted builder blocks.
 */
private fun tintedBlurStyle(
    blurRadius: Dp,
    base: Color,
    tintAlpha: Float,
    noise: Float? = null,
): HazeBlurStyle =
    HazeBlurStyle {
        blurRadius(blurRadius)
        backgroundColor(base)
        colorEffects(listOf(HazeColorEffect.tint(base.copy(alpha = tintAlpha))))
        if (noise != null) {
            noiseFactor(noise)
        }
    }

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

    /**
     * The glass bottom clearance (§12.8): the FULL nav footprint — the bar's
     * own height PLUS the system navigation-bar inset the bar spans. It is
     * both the bottom clearance screens pad scrollables by
     * (`GlassChromePadding.bottom`) and the offset that docks an overlaid
     * drawer entirely above the nav glass, the position it held pre-glass
     * when the shell reserved that space. STR-156's first dock formula
     * (bar height − s12, inset omitted) left most of the drawer behind the
     * bar on device (STR-162 field report). Pure function so the formula is
     * unit-pinned: Robolectric goldens run with zero insets and cannot
     * catch an omission here (same reasoning as [topClearance]).
     */
    fun dockClearance(navigationBarBottom: Dp): Dp = bottomBarHeight + navigationBarBottom
}

/** §12.8 sheet tint depth (materials-Thick rule): near-opaque, lighter over light surfaces. */
internal fun sheetTintAlpha(surfaceInset: Color): Float =
    if (surfaceInset.luminance() >= 0.5f) 0.83f else 0.9f

/**
 * Dock tint depth (STR-162): the bottom bar's 0.75, in both modes. A docked
 * drawer lives on screen permanently, so it takes bar depth — at the sheet's
 * near-opaque Thick tint the blur underneath contributes only ~10% and the
 * surface reads as a flat solid panel on device. Also drives the floor
 * painted under the dock's blur, following the top-bar precedent that a glass
 * surface's floor alpha equals its style tint alpha
 * (GLASS_BACKGROUND_FLOOR_ALPHA == the top bar's 0.65).
 */
internal const val DOCK_TINT_ALPHA: Float = 0.75f

/**
 * §12.8 dock surface for overlay drawers and docks that live OUTSIDE their
 * hazeSource subtree (e.g. `StratumTabScaffold`'s bottomOverlay slot). The
 * single place the glass-versus-solid choice is made: when glass is inactive
 * this paints [solidColor]; when active it paints the dock tint as a floor
 * under the [StratumHazeStyles.dock] blur, clipped to [shape], so the
 * surface never renders fully transparent even where blur is unavailable —
 * the same floor discipline as `StratumTopBar` (STR-155), with the floor at
 * the dock's tint depth (STR-162: a permanent dock takes the bar-depth
 * [DOCK_TINT_ALPHA], not the modal sheet's near-opaque Thick). Callers pass
 * geometry and their solid color, never a glass conditional of their own.
 */
@Composable
fun Modifier.stratumDockSurface(
    shape: Shape,
    solidColor: Color,
): Modifier {
    val hazeState = rememberGlassHazeState()
    val colors = StratumThemeAccessor.colors
    return if (hazeState == null) {
        background(color = solidColor, shape = shape)
    } else {
        this
            .background(
                color = colors.surfaceInset.copy(alpha = DOCK_TINT_ALPHA),
                shape = shape,
            )
            .clip(shape)
            .hazeBlur(input = HazeInput.Sources(hazeState), style = StratumThemeAccessor.haze.dock)
    }
}
