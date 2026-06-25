package com.stratum.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
private fun StratumSegmentedControlTonalPreview() {
    SegmentedControlPreviewGrid(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun StratumSegmentedControlNeoPreview() {
    SegmentedControlPreviewGrid(family = StratumThemeId.NeoBrutalist)
}

@Composable
private fun SegmentedControlPreviewGrid(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StratumText(
                text = "Single-select (Front / Back)",
                style = StratumThemeAccessor.typography.titleLarge,
            )
            StratumSegmentedControl(
                segments = listOf("Front", "Back"),
                selectedIndex = 0,
                onSelectedIndexChange = {},
            )
            StratumText(
                text = "Multi-select (weekdays)",
                style = StratumThemeAccessor.typography.titleLarge,
            )
            StratumToggleGroup(
                options = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
                selectedOptions = setOf("Mon", "Wed", "Fri"),
                onOptionToggle = {},
            )
        }
    }
}
