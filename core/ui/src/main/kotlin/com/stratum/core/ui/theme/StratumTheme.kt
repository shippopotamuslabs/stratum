package com.stratum.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val LocalStratumTheme =
    staticCompositionLocalOf {
        StratumTonalSpec.resolve(dark = true)
    }

/**
 * The hosting shell's shared HazeState (§12.8: one per screen, shared by both
 * bars), provided by the shell (`MainTabShell`). Null outside a glass host —
 * previews, tests, detail routes — where chrome renders its solid treatment.
 * Consumers read it through [rememberGlassHazeState], never directly, so the
 * family enablement check lives in exactly one place.
 */
val LocalStratumHazeState =
    staticCompositionLocalOf<dev.chrisbanes.haze.HazeState?> { null }

/**
 * The shared glass state for this composition, or null when glass does not
 * apply (no host state, or the Neo Brutalist carve-out). This is THE canonical
 * glass check — components and screens must not re-derive it.
 */
@Composable
fun rememberGlassHazeState(): dev.chrisbanes.haze.HazeState? =
    if (StratumThemeAccessor.haze.enabled) LocalStratumHazeState.current else null

/**
 * The glass clearances for content hosted under the chrome, read once so the
 * host-activation gate is subscribed a single time. Screens consume this
 * instead of composing the active/top/bottom reads themselves; the overlay
 * decision itself stays in `StratumTabScaffold`, which pairs the bar treatment
 * with its source subtree atomically. All zeros when glass does not apply (no
 * host state, Neo carve-out, previews), so solid layouts need no branching.
 *
 * Both clearances are FULL chrome footprints, symmetric by construction:
 * [top] is the status-bar inset plus the top bar's height
 * ([StratumGlassChrome.topClearance], STR-155) and [bottom] is the
 * navigation-bar inset plus the bottom bar's height
 * ([StratumGlassChrome.dockClearance], STR-162) — the space the glass
 * actually occupies, so content padded by either one clears the glass
 * entirely, inset included.
 */
data class GlassChromePadding(
    val active: Boolean,
    val top: androidx.compose.ui.unit.Dp,
    val bottom: androidx.compose.ui.unit.Dp,
)

/**
 * Bottom offset for a drawer/dock overlaid at the shell's bottom edge
 * (`StratumTabScaffold`'s bottomOverlay slot): under glass it lifts the
 * surface clear of the shell's FULL nav footprint, so the whole surface
 * rests above the nav glass, the position it held pre-glass (STR-162;
 * STR-156's bar-height-minus-s12 formula omitted the inset and left most
 * of the drawer behind the bar on device). The footprint itself is
 * [GlassChromePadding.bottom] from the single [rememberGlassChromePadding]
 * snapshot — one source of truth, shared with every screen's scroll
 * clearance; this accessor exists so dock call sites name their intent
 * and never hand-compute nav clearance. Zero when glass is inactive —
 * the slot's own anchor already sits above any in-flow bottom bar there.
 */
@Composable
fun rememberGlassBottomDockPadding(): Dp {
    val chrome = rememberGlassChromePadding()
    return if (chrome.active) chrome.bottom else 0.dp
}

/** Single-subscription snapshot of [GlassChromePadding] for this composition. */
@Composable
fun rememberGlassChromePadding(): GlassChromePadding {
    if (rememberGlassHazeState() == null) {
        return GlassChromePadding(
            active = false,
            top = 0.dp,
            bottom = 0.dp,
        )
    }
    val top =
        StratumGlassChrome.topClearance(
            WindowInsets.statusBars
                .asPaddingValues()
                .calculateTopPadding(),
        )
    val bottom =
        StratumGlassChrome.dockClearance(
            WindowInsets.navigationBars
                .asPaddingValues()
                .calculateBottomPadding(),
        )
    return GlassChromePadding(
        active = true,
        top = top,
        bottom = bottom,
    )
}

object StratumThemeSpecFactory {
    fun resolve(
        family: StratumThemeId,
        dark: Boolean,
    ): StratumThemeSpec =
        when (family) {
            StratumThemeId.Tonal -> StratumTonalSpec.resolve(dark)
            StratumThemeId.NeoBrutalist -> NeoBrutalistSpec.resolve(dark)
        }
}

/**
 * Root Stratum theme. Prefer [preferences] for production; [themeId] + [darkTheme] remain for
 * previews and one-off forced modes.
 *
 * When [darkTheme] is null, dark tokens follow [ThemePreferences.colorMode] (System / Light /
 * Dark). Explicit [darkTheme] wins so existing `@Preview` call sites stay valid.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StratumTheme(
    preferences: ThemePreferences = ThemePreferences(),
    themeId: StratumThemeId = preferences.family,
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val resolvedDark =
        darkTheme ?: preferences.colorMode.resolveDark(isSystemInDarkTheme())
    val spec = StratumThemeSpecFactory.resolve(themeId, resolvedDark)

    CompositionLocalProvider(LocalStratumTheme provides spec) {
        MaterialTheme(
            colorScheme = materialColorScheme(spec),
            shapes = materialShapes(spec),
            typography = spec.typography.material,
            // §12.5: M3 Expressive spring physics are the motion default
            // (material3 1.5.0-beta01 pin; the scheme is internal in the
            // BOM's managed 1.4.0). Stratum's own specs: StratumMotion.
            motionScheme = MotionScheme.expressive(),
            content = content,
        )
    }
}

/**
 * Material bridge for the semantic ramp: every slot is derived from
 * [StratumColors] so M3-default internals agree with Stratum tokens.
 *
 * Container mapping follows the gospel tonal steps (§12.2 — dark: 0dp bg-base, 1dp
 * surface-1, 3dp surface-2, 6dp surface-3; light analogous per §11.4, where
 * elevation climbs toward white instead of away from black):
 *
 * - Dark: Lowest = base, Low = sunken, Container = raised, High = raised (Stratum
 *   has no surface-2 role and its dialogs/sheets use raised, so the 3dp step
 *   folds here), Highest = inset (surface-3, top of the ramp).
 * - Light: Lowest = raised (white), Low = base (grey canvas), Container = inset
 *   (tonal fills), High = raised, Highest = raised (light modals are white, §11.4
 *   surface-3, and Stratum sheets/dialogs use raised).
 *
 * No drop shadows are added in dark — [StratumElevations] stays the elevation
 * model (§2.3); `surfaceTint` only tints M3 tonal-elevation surfaces.
 */
/** The five M3 surface-container slots, mapped from Stratum tokens per §12.4. */
private data class ContainerRamp(
    val lowest: Color,
    val low: Color,
    val container: Color,
    val high: Color,
    val highest: Color,
)

internal fun materialColorScheme(spec: StratumThemeSpec): ColorScheme {
    val colors = spec.colors
    // Container ramp per the §12.4 mapping in the KDoc above.
    val ramp =
        if (spec.dark) {
            ContainerRamp(
                lowest = colors.surfaceBase,
                low = colors.surfaceSunken,
                container = colors.surfaceRaised,
                high = colors.surfaceRaised,
                highest = colors.surfaceInset,
            )
        } else {
            ContainerRamp(
                lowest = colors.surfaceRaised,
                low = colors.surfaceBase,
                container = colors.surfaceInset,
                high = colors.surfaceRaised,
                highest = colors.surfaceRaised,
            )
        }
    val base =
        if (spec.dark) {
            darkColorScheme(
                primary = colors.accent,
                onPrimary = colors.onAccent,
                background = colors.surfaceBase,
                onBackground = colors.textPrimary,
                surface = colors.surfaceRaised,
                onSurface = colors.textPrimary,
                surfaceVariant = colors.surfaceSunken,
                onSurfaceVariant = colors.textMuted,
                outline = colors.borderSubtle,
                outlineVariant = colors.borderStrong,
                error = colors.error,
                onError = colors.onError,
            )
        } else {
            lightColorScheme(
                primary = colors.accent,
                onPrimary = colors.onAccent,
                background = colors.surfaceBase,
                onBackground = colors.textPrimary,
                surface = colors.surfaceRaised,
                onSurface = colors.textPrimary,
                surfaceVariant = colors.surfaceSunken,
                onSurfaceVariant = colors.textMuted,
                outline = colors.borderSubtle,
                outlineVariant = colors.borderStrong,
                error = colors.error,
                onError = colors.onError,
            )
        }
    return base.copy(
        surfaceTint = colors.accent,
        surfaceContainerLowest = ramp.lowest,
        surfaceContainerLow = ramp.low,
        surfaceContainer = ramp.container,
        surfaceContainerHigh = ramp.high,
        surfaceContainerHighest = ramp.highest,
    )
}

/**
 * Material bridge for the gospel shape scale (§12.4): the M3 slots carry the
 * spec's scale rungs. Tonal maps its gospel radii; Neo maps its own 4dp
 * geometry (carve-out, §11.3 ruling 1) — never pill buttons.
 */
internal fun materialShapes(spec: StratumThemeSpec): Shapes =
    Shapes(
        extraSmall = spec.shapes.extraSmall,
        small = spec.shapes.small,
        medium = spec.shapes.medium,
        large = spec.shapes.large,
        extraLarge = spec.shapes.extraLarge,
    )

object StratumThemeAccessor {
    val colors: StratumColors
        @Composable get() = LocalStratumTheme.current.colors

    val typography: StratumTypography
        @Composable get() = LocalStratumTheme.current.typography

    val shapes: StratumShapes
        @Composable get() = LocalStratumTheme.current.shapes

    val elevations: StratumElevations
        @Composable get() = LocalStratumTheme.current.elevations

    val haze: StratumHazeStyles
        @Composable get() = LocalStratumTheme.current.haze
}
