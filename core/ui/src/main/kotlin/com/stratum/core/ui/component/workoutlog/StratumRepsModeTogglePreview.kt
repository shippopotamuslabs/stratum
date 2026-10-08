package com.stratum.core.ui.component.workoutlog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.theme.StratumThemeId

@PreviewLightDark
@Composable
private fun StratumRepsModeToggleFullPreview() {
    RepsModeTogglePreviewContent(family = StratumThemeId.Tonal, mode = RepsEntryMode.Full)
}

@PreviewLightDark
@Composable
private fun StratumRepsModeTogglePartialPreview() {
    RepsModeTogglePreviewContent(family = StratumThemeId.Tonal, mode = RepsEntryMode.Partial)
}

@Composable
internal fun RepsModeTogglePreviewContent(
    family: StratumThemeId,
    mode: RepsEntryMode,
) {
    StratumTheme(themeId = family) {
        Box(
            modifier =
                Modifier
                    .background(StratumThemeAccessor.colors.surfaceBase)
                    .width(72.dp)
                    .height(56.dp),
        ) {
            StratumRepsModeToggle(mode = mode, onModeChange = {})
        }
    }
}
