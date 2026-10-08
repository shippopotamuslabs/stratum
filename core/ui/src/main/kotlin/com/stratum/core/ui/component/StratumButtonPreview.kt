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
    ButtonPreviewContent(family = StratumThemeId.Tonal, darkTheme = true)
}

@PreviewLightDark
@Composable
private fun StratumButtonNeoPreview() {
    ButtonPreviewContent(family = StratumThemeId.NeoBrutalist, darkTheme = true)
}

@PreviewLightDark
@Composable
private fun StratumButtonNeoLightPreview() {
    ButtonPreviewContent(family = StratumThemeId.NeoBrutalist, darkTheme = false)
}

@Composable
internal fun ButtonPreviewContent(
    family: StratumThemeId,
    darkTheme: Boolean? = null,
) {
    StratumTheme(themeId = family, darkTheme = darkTheme) {
        ButtonPreviewBody()
    }
}

@Composable
private fun ButtonPreviewBody() {
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
