package com.stratum.core.ui.component.workoutlog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.theme.StratumThemeId

@PreviewLightDark
@Composable
private fun StratumSplitRepsCellFullPreview() {
    StratumTheme(themeId = StratumThemeId.Tonal) {
        Box(
            modifier =
                Modifier
                    .background(StratumThemeAccessor.colors.surfaceBase)
                    .fillMaxWidth(),
        ) {
            StratumSplitRepsCell(
                fullRepsText = "8",
                partialRepsText = "2",
                fullPlaceholder = "8",
                selectedMode = RepsEntryMode.Full,
                enabled = true,
                isActive = true,
                completed = false,
                onSelectFull = {},
                onSelectPartial = {},
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun StratumSplitRepsCellPartialPreview() {
    StratumTheme(themeId = StratumThemeId.Tonal) {
        Box(
            modifier =
                Modifier
                    .background(StratumThemeAccessor.colors.surfaceBase)
                    .fillMaxWidth(),
        ) {
            StratumSplitRepsCell(
                fullRepsText = "8",
                partialRepsText = "",
                fullPlaceholder = "8",
                selectedMode = RepsEntryMode.Partial,
                enabled = true,
                isActive = true,
                completed = false,
                onSelectFull = {},
                onSelectPartial = {},
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun StratumSplitRepsCellCompletedUnfocusedPreview() {
    StratumTheme(themeId = StratumThemeId.Tonal) {
        Box(
            modifier =
                Modifier
                    .background(StratumThemeAccessor.colors.setComplete)
                    .fillMaxWidth(),
        ) {
            StratumSplitRepsCell(
                fullRepsText = "14",
                partialRepsText = "2",
                fullPlaceholder = "12",
                selectedMode = RepsEntryMode.Partial,
                enabled = true,
                isActive = false,
                completed = true,
                onSelectFull = {},
                onSelectPartial = {},
            )
        }
    }
}
