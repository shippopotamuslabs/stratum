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
private fun StratumPillToggleTonalPreview() {
    PillTogglePreviewGrid(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun StratumPillToggleNeoPreview() {
    PillTogglePreviewGrid(family = StratumThemeId.NeoBrutalist)
}

@Composable
internal fun PillTogglePreviewGrid(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StratumText(
                text = "Body tier",
                style = StratumThemeAccessor.typography.titleLarge,
            )
            StratumPillToggle(
                segments = listOf("Upper", "Lower"),
                selectedIndex = 0,
                onSelectedIndexChange = {},
            )
            StratumText(
                text = "Volume scope",
                style = StratumThemeAccessor.typography.titleLarge,
            )
            StratumPillToggle(
                segments = listOf("Workout", "Program"),
                selectedIndex = 1,
                onSelectedIndexChange = {},
            )
        }
    }
}
