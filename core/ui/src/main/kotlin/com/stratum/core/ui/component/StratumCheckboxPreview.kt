package com.stratum.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId

@PreviewLightDark
@Composable
private fun StratumCheckboxTonalPreview() {
    CheckboxPreviewGrid(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun StratumCheckboxNeoPreview() {
    CheckboxPreviewGrid(family = StratumThemeId.NeoBrutalist)
}

@Composable
internal fun CheckboxPreviewGrid(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StratumCheckbox(checked = true, onCheckedChange = {})
            StratumCheckbox(checked = false, onCheckedChange = {})
            StratumCheckbox(checked = true, onCheckedChange = {}, enabled = false)
            StratumCheckbox(checked = false, onCheckedChange = {}, enabled = false)
        }
    }
}
