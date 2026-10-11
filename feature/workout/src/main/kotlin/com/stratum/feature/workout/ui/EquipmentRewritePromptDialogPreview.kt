package com.stratum.feature.workout.ui

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
private fun EquipmentRewritePromptTonalPreview() {
    EquipmentRewritePromptPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun EquipmentRewritePromptNeoPreview() {
    EquipmentRewritePromptPreviewContent(family = StratumThemeId.NeoBrutalist)
}

/** Public for the `:app` screenshot test; same-module callers use the dialog. */
@Composable
fun EquipmentRewritePromptPreviewContent(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(StratumThemeAccessor.colors.surfaceBase)
                    .padding(16.dp),
        ) {
            EquipmentRewritePromptContent(
                prompt =
                    EquipmentRewritePromptUi(
                        workoutLiftId = "wl-1",
                        liftId = "back-squat",
                        liftName = "Back Squat",
                        oldName = "Olympic Barbell",
                        newId = "ssb",
                        newName = "Safety Squat Bar",
                        loggedCount = 2,
                    ),
            )
        }
    }
}
