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
private fun StratumButtonTonalPreview() {
    StratumTheme(themeId = StratumThemeId.Tonal, darkTheme = true) {
        ButtonPreviewColumn()
    }
}

@PreviewLightDark
@Composable
private fun StratumButtonNeoPreview() {
    StratumTheme(themeId = StratumThemeId.NeoBrutalist, darkTheme = true) {
        ButtonPreviewColumn()
    }
}

@PreviewLightDark
@Composable
private fun StratumButtonNeoLightPreview() {
    StratumTheme(themeId = StratumThemeId.NeoBrutalist, darkTheme = false) {
        ButtonPreviewColumn()
    }
}

@Composable
private fun ButtonPreviewColumn() {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StratumButton(
            text = "Primary action",
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
        StratumOutlinedButton(
            text = "Secondary action",
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
        StratumOutlinedButton(
            text = "Discard Workout",
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
            destructive = true,
        )
        StratumButton(
            text = "Delete account",
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
            destructive = true,
        )
        StratumButton(
            text = "Disabled",
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
        )
    }
}
