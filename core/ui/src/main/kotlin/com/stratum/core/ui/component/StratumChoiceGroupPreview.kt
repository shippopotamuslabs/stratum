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
private fun StratumChoiceGroupTonalPreview() {
    ChoiceGroupPreviewGrid(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun StratumChoiceGroupNeoPreview() {
    ChoiceGroupPreviewGrid(family = StratumThemeId.NeoBrutalist)
}

@Composable
internal fun ChoiceGroupPreviewGrid(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StratumText(
                text = "Training age",
                style = StratumThemeAccessor.typography.titleLarge,
            )
            StratumChoiceGroup(
                options = listOf(
                    "0–2 years",
                    "2–4 years",
                    "4–7 years",
                    "7–10 years",
                    "10+ years",
                ),
                selectedOption = "4–7 years",
                onOptionSelected = {},
            )
            StratumText(
                text = "Last trained",
                style = StratumThemeAccessor.typography.titleLarge,
            )
            StratumChoiceGroup(
                options = listOf(
                    "Never",
                    "Within the last month",
                    "1–6 months ago",
                    "6–12 months ago",
                    "Over a year ago",
                ),
                selectedOption = "Within the last month",
                onOptionSelected = {},
            )
        }
    }
}
