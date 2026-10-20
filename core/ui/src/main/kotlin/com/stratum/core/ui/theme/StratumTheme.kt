package com.stratum.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

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
    val materialScheme =
        if (spec.dark) {
            darkColorScheme(
                primary = spec.colors.accent,
                onPrimary = spec.colors.onAccent,
                background = spec.colors.surfaceBase,
                onBackground = spec.colors.textPrimary,
                surface = spec.colors.surfaceRaised,
                onSurface = spec.colors.textPrimary,
                surfaceVariant = spec.colors.surfaceSunken,
                onSurfaceVariant = spec.colors.textMuted,
                outline = spec.colors.borderSubtle,
                outlineVariant = spec.colors.borderStrong,
                error = spec.colors.error,
                onError = spec.colors.onError,
            )
        } else {
            lightColorScheme(
                primary = spec.colors.accent,
                onPrimary = spec.colors.onAccent,
                background = spec.colors.surfaceBase,
                onBackground = spec.colors.textPrimary,
                surface = spec.colors.surfaceRaised,
                onSurface = spec.colors.textPrimary,
                surfaceVariant = spec.colors.surfaceSunken,
                onSurfaceVariant = spec.colors.textMuted,
                outline = spec.colors.borderSubtle,
                outlineVariant = spec.colors.borderStrong,
                error = spec.colors.error,
                onError = spec.colors.onError,
            )
        }

    CompositionLocalProvider(LocalStratumTheme provides spec) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = spec.typography.material,
            content = content,
        )
    }
}

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
