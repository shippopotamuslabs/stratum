package com.stratum.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId

@PreviewLightDark
@Composable
private fun StratumAssistChipTonalPreview() {
    AssistChipPreviewGrid(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun StratumAssistChipNeoPreview() {
    AssistChipPreviewGrid(family = StratumThemeId.NeoBrutalist)
}

@Composable
internal fun AssistChipPreviewGrid(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StratumAssistChip(
                label = "Adjust Equipment",
                onClick = {},
                onDismissClick = {},
                dismissContentDescription = "Dismiss equipment suggestion",
            )
            StratumAssistChip(
                label = "Adjust Equipment",
                onClick = {},
            )
            StratumAssistChip(
                label = "Adjust Equipment",
                onClick = {},
                onDismissClick = {},
                enabled = false,
            )
        }
    }
}
