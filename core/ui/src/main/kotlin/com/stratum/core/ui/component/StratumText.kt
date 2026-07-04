package com.stratum.core.ui.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
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
) {
    Text(
        text = text,
        modifier = modifier,
        style = style,
        color =
            if (muted) {
                StratumThemeAccessor.colors.textMuted
            } else {
                StratumThemeAccessor.colors.textPrimary
            },
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
fun StratumNumber(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = StratumThemeAccessor.typography.dataLarge,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    Text(
        text = text,
        modifier = modifier,
        style = style,
        color = StratumThemeAccessor.colors.textData,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = onTextLayout,
    )
}
