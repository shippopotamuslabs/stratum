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
    SplitRepsCellPreviewContent(
        family = StratumThemeId.Tonal,
        fullRepsText = "8",
        partialRepsText = "2",
        fullPlaceholder = "8",
        selectedMode = RepsEntryMode.Full,
        isActive = true,
        completed = false,
    )
}

@PreviewLightDark
@Composable
private fun StratumSplitRepsCellPartialPreview() {
    SplitRepsCellPreviewContent(
        family = StratumThemeId.Tonal,
        fullRepsText = "8",
        partialRepsText = "",
        fullPlaceholder = "8",
        selectedMode = RepsEntryMode.Partial,
        isActive = true,
        completed = false,
    )
}

@PreviewLightDark
@Composable
private fun StratumSplitRepsCellCompletedUnfocusedPreview() {
    SplitRepsCellPreviewContent(
        family = StratumThemeId.Tonal,
        fullRepsText = "14",
        partialRepsText = "2",
        fullPlaceholder = "12",
        selectedMode = RepsEntryMode.Partial,
        isActive = false,
        completed = true,
    )
}

@Composable
internal fun SplitRepsCellPreviewContent(
    family: StratumThemeId,
    fullRepsText: String,
    partialRepsText: String,
    fullPlaceholder: String,
    selectedMode: RepsEntryMode,
    isActive: Boolean,
    completed: Boolean,
) {
    StratumTheme(themeId = family) {
        Box(
            modifier =
                Modifier
                    .background(
                        if (completed) {
                            StratumThemeAccessor.colors.setComplete
                        } else {
                            StratumThemeAccessor.colors.surfaceBase
                        },
                    )
                    .fillMaxWidth(),
        ) {
            StratumSplitRepsCell(
                fullRepsText = fullRepsText,
                partialRepsText = partialRepsText,
                fullPlaceholder = fullPlaceholder,
                selectedMode = selectedMode,
                enabled = true,
                isActive = isActive,
                completed = completed,
                onSelectFull = {},
                onSelectPartial = {},
            )
        }
    }
}
