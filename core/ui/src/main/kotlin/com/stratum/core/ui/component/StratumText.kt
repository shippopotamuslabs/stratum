package com.stratum.core.ui.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.stratum.core.ui.theme.StratumThemeAccessor

@Composable
fun StratumText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = StratumThemeAccessor.typography.bodyLarge,
    muted: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
    /** When set, overrides [muted] / primary (e.g. error copy on a keyboard). */
    color: Color? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        style = style,
        color =
            color
                ?: if (muted) {
                    StratumThemeAccessor.colors.textMuted
                } else {
                    StratumThemeAccessor.colors.textPrimary
                },
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
    )
}

@Composable
fun StratumNumber(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = StratumThemeAccessor.typography.dataLarge,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign? = null,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    Text(
        text = text,
        modifier = modifier,
        style = style,
        color =
            if (style.color != Color.Unspecified) {
                style.color
            } else {
                StratumThemeAccessor.colors.textData
            },
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        onTextLayout = onTextLayout,
    )
}
