package com.stratum.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Compact stat panel (label + value, optional leading icon) used on workout complete and
 * history session detail screens.
 */
@Composable
fun StratumStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    contentPadding: Dp = 16.dp,
    valueStyle: TextStyle = StratumThemeAccessor.typography.titleLarge,
) {
    val panelShape = StratumThemeAccessor.shapes.panel
    Column(
        modifier =
            modifier
                .background(
                    color = StratumThemeAccessor.colors.surfaceRaised,
                    shape = panelShape,
                )
                .border(
                    width = StratumThemeAccessor.shapes.borderWidth,
                    color = StratumThemeAccessor.colors.borderSubtle,
                    shape = panelShape,
                )
                .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            leading?.invoke()
            StratumText(
                text = label,
                style = StratumThemeAccessor.typography.sectionLabel,
                muted = true,
            )
        }
        StratumText(
            text = value,
            style = valueStyle,
        )
    }
}
