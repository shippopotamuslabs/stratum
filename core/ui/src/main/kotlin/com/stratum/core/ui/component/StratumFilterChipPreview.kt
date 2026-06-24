package com.stratum.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.theme.StratumThemeId

@PreviewLightDark
@Composable
private fun StratumFilterChipTonalPreview() {
    FilterChipPreviewGrid(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun StratumFilterChipNeoPreview() {
    FilterChipPreviewGrid(family = StratumThemeId.NeoBrutalist)
}

@Composable
private fun FilterChipPreviewGrid(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StratumText(
                text = "Multi-select (weekdays)",
                style = StratumThemeAccessor.typography.titleLarge,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StratumFilterChip(label = "Mon", selected = true, onSelectedChange = {})
                StratumFilterChip(label = "Tue", selected = false, onSelectedChange = {})
                StratumFilterChip(label = "Wed", selected = true, onSelectedChange = {})
                StratumFilterChip(label = "Thu", selected = false, onSelectedChange = {})
            }
            StratumText(
                text = "Single-select (experience)",
                style = StratumThemeAccessor.typography.titleLarge,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StratumFilterChip(label = "Novice", selected = false, onSelectedChange = {})
                StratumFilterChip(label = "Intermediate", selected = true, onSelectedChange = {})
                StratumFilterChip(label = "Advanced", selected = false, onSelectedChange = {})
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StratumFilterChip(label = "Disabled", selected = false, onSelectedChange = {}, enabled = false)
                StratumFilterChip(label = "Disabled", selected = true, onSelectedChange = {}, enabled = false)
            }
        }
    }
}
