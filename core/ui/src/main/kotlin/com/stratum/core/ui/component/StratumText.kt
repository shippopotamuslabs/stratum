package com.stratum.core.ui.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.stratum.core.ui.theme.StratumThemeAccessor

@Composable
fun StratumText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = StratumThemeAccessor.typography.bodyLarge,
    muted: Boolean = false,
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
    )
}

@Composable
fun StratumNumber(
    text: String,
    modifier: Modifier = Modifier,
) {
    StratumText(
        text = text,
        modifier = modifier,
        style = StratumThemeAccessor.typography.dataLarge,
    )
}
