package com.stratum.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.theme.StratumThemeId

@PreviewLightDark
@Composable
private fun StratumChoiceDialogTonalPreview() {
    StratumChoiceDialogPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun StratumChoiceDialogNeoPreview() {
    StratumChoiceDialogPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@Composable
private fun StratumChoiceDialogPreviewContent(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(StratumThemeAccessor.colors.surfaceBase)
                    .padding(16.dp),
        ) {
            StratumChoiceDialogContent(
                title = "Sets already logged",
                body = "Olympic Barbell is on 2 logged sets. Rewrite them to Safety Squat Bar, " +
                    "or keep the old equipment on the logged sets?",
                primaryLabel = "Rewrite to new",
                onPrimary = {},
                secondaryLabel = "Keep old",
                onSecondary = {},
            )
        }
    }
}
