package com.stratum.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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

@Immutable
data class StratumTypography(
    val bodyLarge: TextStyle,
    val titleLarge: TextStyle,
    val titleMedium: TextStyle,
    val dataLarge: TextStyle,
    val sectionLabel: TextStyle,
)

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

internal val DefaultStratumTypography =
    StratumTypography(
        bodyLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                lineHeight = 28.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 22.sp,
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
        button = RoundedCornerShape(12.dp),
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
