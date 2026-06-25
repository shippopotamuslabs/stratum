package com.stratum.core.ui.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.stratum.core.ui.R
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Segment-display number rendered with the bundled DSEG7 Classic font (OFL-1.1).
 *
 * Used for digital-clock style values (e.g. the duration stepper) where the
 * seven-segment glyph aesthetic is the point. The font is loaded once and cached
 * via [remember]; callers size it to fill its container and keep it centered.
 *
 * Font: DSEG7 Classic by keshikan — see `res/raw/dseg_license.txt`.
 */
@Composable
fun StratumSegmentNumber(
    text: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    val fontFamily = remember {
        FontFamily(Font(R.font.dseg7_classic_regular))
    }
    val color = if (isError) {
        StratumThemeAccessor.colors.accent
    } else {
        StratumThemeAccessor.colors.textData
    }
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(
            fontFamily = fontFamily,
            fontSize = fontSize,
            lineHeight = TextUnit.Unspecified,
            textAlign = TextAlign.Center,
        ),
        color = color,
        maxLines = 1,
    )
}
