package com.stratum.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Canonical corner scale (§12.2 / §12.4 of `contracts/UI_DESIGN.md`): the five
 * M3 rungs as Dp, and the ONLY stored copy of them. [StratumShapes] derives
 * every Shape and corner accessor from this scale, so a rung's Shape and its
 * corner Dp can never drift apart.
 */
@Immutable
data class StratumCornerScale(
    val extraSmall: Dp,
    val small: Dp,
    val medium: Dp,
    val large: Dp,
    val extraLarge: Dp,
)

@Immutable
data class StratumShapes(
    val button: Shape,
    val borderWidth: Dp,
    val corners: StratumCornerScale,
) {
    // Panel is the medium rung and chip the small rung (§12.2); both derive
    // from the canonical scale rather than storing a second copy.
    val panel: Shape get() = RoundedCornerShape(corners.medium)
    val panelCorner: Dp get() = corners.medium
    val chip: Shape get() = RoundedCornerShape(corners.small)
    val chipCorner: Dp get() = corners.small

    // Button Shape Gospel (§12.7): the square variant and the pressed shape,
    // derived from the canonical scale like panel/chip above. Tonal resolves
    // them to the gospel tokens (12.dp medium / 16.dp large / 12.dp pressed);
    // Neo's flat 4.dp scale collapses every derivation onto its carve-out
    // geometry, so Neo buttons never see a gospel radius.
    val buttonSquare: Shape get() = RoundedCornerShape(corners.medium)
    val buttonSquareCorner: Dp get() = corners.medium
    val buttonSquareLarge: Shape get() = RoundedCornerShape(corners.large)
    val buttonSquareLargeCorner: Dp get() = corners.large
    val buttonPressed: Shape get() = RoundedCornerShape(corners.medium)
    val buttonPressedCorner: Dp get() = corners.medium

    val extraSmall: RoundedCornerShape get() = RoundedCornerShape(corners.extraSmall)
    val extraSmallCorner: Dp get() = corners.extraSmall
    val small: RoundedCornerShape get() = RoundedCornerShape(corners.small)
    val smallCorner: Dp get() = corners.small
    val medium: RoundedCornerShape get() = RoundedCornerShape(corners.medium)
    val mediumCorner: Dp get() = corners.medium
    val large: RoundedCornerShape get() = RoundedCornerShape(corners.large)
    val largeCorner: Dp get() = corners.large
    val extraLarge: RoundedCornerShape get() = RoundedCornerShape(corners.extraLarge)
    val extraLargeCorner: Dp get() = corners.extraLarge
}

/**
 * Gospel nested-radius rule (§12.2): a child nested inside a padded parent takes
 * the parent's corner radius minus the padding, floored at zero.
 *
 * Example: a 28dp bottom sheet with 12dp padding nests a 16dp inner container.
 */
fun nestedChildRadius(
    parentRadius: Dp,
    padding: Dp,
): Dp = (parentRadius - padding).coerceAtLeast(0.dp)

/**
 * Semantic typography bundle. The M3 slots live in [material] carrying the full
 * Shippopotamus Compose Gospel type scale (§12.2 of `contracts/UI_DESIGN.md`);
 * [dataLarge] and [sectionLabel] are Stratum-specific styles with no gospel
 * counterpart and are kept as-is. The 15 M3 accessors below delegate to [material]
 * so existing call sites read unchanged.
 */
@Immutable
data class StratumTypography(
    val material: Typography,
    val dataLarge: TextStyle,
    val sectionLabel: TextStyle,
) {
    val displayLarge: TextStyle get() = material.displayLarge
    val displayMedium: TextStyle get() = material.displayMedium
    val displaySmall: TextStyle get() = material.displaySmall
    val headlineLarge: TextStyle get() = material.headlineLarge
    val headlineMedium: TextStyle get() = material.headlineMedium
    val headlineSmall: TextStyle get() = material.headlineSmall
    val titleLarge: TextStyle get() = material.titleLarge
    val titleMedium: TextStyle get() = material.titleMedium
    val titleSmall: TextStyle get() = material.titleSmall
    val labelLarge: TextStyle get() = material.labelLarge
    val labelMedium: TextStyle get() = material.labelMedium
    val labelSmall: TextStyle get() = material.labelSmall
    val bodyLarge: TextStyle get() = material.bodyLarge
    val bodyMedium: TextStyle get() = material.bodyMedium
    val bodySmall: TextStyle get() = material.bodySmall
}

/**
 * Elevation / shadow tokens for a theme family.
 *
 * When [useHardShadow] is true (Neo Brutalist), components paint a solid offset fill via
 * [com.stratum.core.ui.theme.stratumHardShadow] — never soft Material blur. Offsets are
 * typically larger in dark mode so the hard edge reads against charcoal surfaces.
 * Tonal sets [useHardShadow] false and zero offsets; components no-op the hard path.
 */
@Immutable
data class StratumElevations(
    val cardShadowOffset: Dp,
    val buttonShadowOffset: Dp,
    val useHardShadow: Boolean,
    /** Fill color for hard-offset shadows; ignored when [useHardShadow] is false. */
    val hardShadowColor: Color,
)

@Immutable
data class StratumThemeSpec(
    val id: StratumThemeId,
    val dark: Boolean,
    val colors: StratumColors,
    val shapes: StratumShapes,
    val elevations: StratumElevations,
    val typography: StratumTypography,
    /**
     * Glass Bars styles (§12.8) derived from this spec's own colors — the single
     * construction path, mirroring how [StratumShapes] derives from its
     * canonical corner scale. Glass is enabled for the Tonal family only;
     * Neo Brutalist chrome stays solid under its carve-out (§11.3 ruling 1).
     * The spec objects cache one instance per family/darkness, so these style
     * objects are stable across the app.
     */
    val haze: StratumHazeStyles =
        stratumHazeStyles(colors = colors, enabled = id == StratumThemeId.Tonal),
)

private fun gospelStyle(
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontWeight: FontWeight = FontWeight.Normal,
    letterSpacing: TextUnit = TextUnit.Unspecified,
): TextStyle =
    TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = fontWeight,
        fontSize = fontSize,
        lineHeight = lineHeight,
        letterSpacing = letterSpacing,
    )

internal val DefaultStratumTypography =
    StratumTypography(
        material =
            Typography(
                displayLarge = gospelStyle(57.sp, 64.sp, letterSpacing = (-0.25).sp),
                displayMedium = gospelStyle(45.sp, 52.sp),
                displaySmall = gospelStyle(36.sp, 44.sp),
                headlineLarge = gospelStyle(32.sp, 40.sp),
                headlineMedium = gospelStyle(28.sp, 36.sp),
                headlineSmall = gospelStyle(24.sp, 32.sp),
                titleLarge = gospelStyle(22.sp, 28.sp, FontWeight.SemiBold),
                titleMedium = gospelStyle(16.sp, 24.sp, FontWeight.Medium),
                titleSmall = gospelStyle(14.sp, 20.sp, FontWeight.Medium),
                labelLarge = gospelStyle(14.sp, 20.sp, FontWeight.Medium),
                labelMedium = gospelStyle(12.sp, 16.sp, FontWeight.Medium),
                labelSmall = gospelStyle(11.sp, 16.sp, FontWeight.Medium),
                bodyLarge = gospelStyle(16.sp, 24.sp),
                bodyMedium = gospelStyle(14.sp, 20.sp),
                bodySmall = gospelStyle(12.sp, 16.sp, letterSpacing = 0.15.sp),
            ),
        dataLarge =
            TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp,
                lineHeight = 24.sp,
            ),
        sectionLabel =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                letterSpacing = 0.5.sp,
            ),
    )

internal fun tonalShapes(): StratumShapes =
    StratumShapes(
        // Gospel: Material-family buttons are full pill (§12.3 / §12.7 token).
        button = ShippopotamusButtonShape,
        borderWidth = 1.dp,
        // Gospel scale (§12.2 / §12.4): extraSmall 4, small 8, medium 12,
        // large 16, extraLarge 28.
        corners =
            StratumCornerScale(
                extraSmall = 4.dp,
                small = 8.dp,
                medium = 12.dp,
                large = 16.dp,
                extraLarge = 28.dp,
            ),
    )

internal fun neoBrutalistShapes(): StratumShapes =
    StratumShapes(
        button = RoundedCornerShape(4.dp),
        borderWidth = 2.dp,
        // Sanctioned carve-out (§11.3 ruling 1): Neo keeps its 4dp identity —
        // the gospel radii are NOT adopted here, so every rung of the scale
        // mirrors Neo's own 4dp geometry.
        corners =
            StratumCornerScale(
                extraSmall = 4.dp,
                small = 4.dp,
                medium = 4.dp,
                large = 4.dp,
                extraLarge = 4.dp,
            ),
    )
