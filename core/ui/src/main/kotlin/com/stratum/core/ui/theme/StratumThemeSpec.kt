package com.stratum.core.ui.theme

import androidx.compose.foundation.shape.CircleShape
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

@Immutable
data class StratumShapes(
    val panel: Shape,
    val panelCorner: Dp,
    val button: Shape,
    val chip: Shape,
    val chipCorner: Dp,
    val borderWidth: Dp,
)

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
        panel = RoundedCornerShape(12.dp),
        panelCorner = 12.dp,
        // Gospel: Material-family buttons are full pill (§12.3).
        button = CircleShape,
        chip = RoundedCornerShape(8.dp),
        chipCorner = 8.dp,
        borderWidth = 1.dp,
    )

internal fun neoBrutalistShapes(): StratumShapes =
    StratumShapes(
        panel = RoundedCornerShape(4.dp),
        panelCorner = 4.dp,
        button = RoundedCornerShape(4.dp),
        chip = RoundedCornerShape(4.dp),
        chipCorner = 4.dp,
        borderWidth = 2.dp,
    )
