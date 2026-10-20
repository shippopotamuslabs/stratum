package com.stratum.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalStratumTheme =
    staticCompositionLocalOf {
        StratumTonalSpec.resolve(dark = true)
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
}
