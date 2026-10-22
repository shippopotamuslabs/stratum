package com.stratum.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.theme.StratumThemeId

@PreviewLightDark
@Composable
private fun StratumListRowTonalPreview() {
    ListRowPreviewGrid(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun StratumListRowNeoPreview() {
    ListRowPreviewGrid(family = StratumThemeId.NeoBrutalist)
}

@Composable
internal fun ListRowPreviewGrid(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(StratumThemeAccessor.colors.surfaceBase),
        ) {
            StratumListRow(
                headline = "One line row",
                leading = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                trailing = {
                    StratumText(text = "Value", muted = true)
                },
                onClick = {},
            )
            StratumDivider()
            StratumListRow(
                headline = "Two line row",
                supporting = "Supporting text explains the row",
                onClick = {},
            )
            StratumDivider()
            StratumListRow(
                headline = "Three line row",
                supporting = "Supporting text explains the row",
                overline = "OVERLINE",
            )
            StratumDivider()
            StratumListRow(
                headline = "Overline row",
                overline = "OVERLINE",
            )
            StratumDivider(band = true)
            StratumListRow(
                headline = "Compact row",
                supporting = "Dense panel variant",
                compact = true,
            )
        }
    }
}
