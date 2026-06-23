package com.stratum.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
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
    val button: Shape,
    val chip: Shape,
    val borderWidth: Dp,
)

@Immutable
data class StratumTypography(
    val bodyLarge: TextStyle,
    val titleLarge: TextStyle,
    val dataLarge: TextStyle,
)

@Immutable
data class StratumElevations(
    val cardShadowOffset: Dp,
    val buttonShadowOffset: Dp,
    val useHardShadow: Boolean,
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
        dataLarge =
            TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp,
                lineHeight = 24.sp,
            ),
    )

internal fun tonalShapes(): StratumShapes =
    StratumShapes(
        panel = RoundedCornerShape(12.dp),
        button = RoundedCornerShape(12.dp),
        chip = RoundedCornerShape(8.dp),
        borderWidth = 1.dp,
    )

internal fun neoBrutalistShapes(): StratumShapes =
    StratumShapes(
        panel = RoundedCornerShape(4.dp),
        button = RoundedCornerShape(4.dp),
        chip = RoundedCornerShape(4.dp),
        borderWidth = 2.dp,
    )
