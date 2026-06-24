package com.stratum.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId

@PreviewLightDark
@Composable
private fun StratumMusclePickerTonalPreview() {
    MusclePickerPreviewGrid(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun StratumMusclePickerNeoPreview() {
    MusclePickerPreviewGrid(family = StratumThemeId.NeoBrutalist)
}

@Composable
private fun MusclePickerPreviewGrid(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Empty (skip) — nothing selected, picker enabled.
            StratumMusclePicker(
                selectedMuscleLabels = emptySet(),
                onMuscleToggled = {},
                enabled = true,
            )
            // One muscle selected.
            StratumMusclePicker(
                selectedMuscleLabels = setOf("Chest"),
                onMuscleToggled = {},
                enabled = true,
            )
            // At cap (3 selected) — picker disabled, selection visible.
            StratumMusclePicker(
                selectedMuscleLabels = setOf("Chest", "Quads", "Hamstrings"),
                onMuscleToggled = {},
                enabled = false,
            )
            // Partial deltoids (1 of 3 heads) — region renders in the partial color.
            StratumMusclePicker(
                selectedMuscleLabels = setOf("Side Delts"),
                onMuscleToggled = {},
                enabled = true,
            )
        }
    }
}
