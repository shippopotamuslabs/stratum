package com.stratum.feature.history.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId
import java.time.LocalDate
import java.time.LocalTime

@PreviewLightDark
@Composable
private fun EditWorkoutTimeBottomSheetTonalPreview() {
    EditWorkoutTimePreviewContent(
        family = StratumThemeId.Tonal,
        initialStartTime = LocalTime.of(20, 53),
        initialFinishDate = LocalDate.of(2025, 8, 10),
        initialFinishTime = LocalTime.of(21, 38),
    )
}

@PreviewLightDark
@Composable
private fun EditWorkoutTimeBottomSheetNeoPreview() {
    EditWorkoutTimePreviewContent(
        family = StratumThemeId.NeoBrutalist,
        initialStartTime = LocalTime.of(22, 0),
        initialFinishDate = LocalDate.of(2025, 8, 11),
        initialFinishTime = LocalTime.of(0, 30),
    )
}

/**
 * Pins the sheet body inline: dialog windows are uncapturable in screenshot goldens
 * (STR-89 pattern).
 */
@Composable
internal fun EditWorkoutTimePreviewContent(
    family: StratumThemeId,
    initialStartTime: LocalTime,
    initialFinishDate: LocalDate,
    initialFinishTime: LocalTime,
) {
    StratumTheme(themeId = family) {
        EditWorkoutTimeSheetContent(
            initialStartDate = LocalDate.of(2025, 8, 10),
            initialStartTime = initialStartTime,
            initialFinishDate = initialFinishDate,
            initialFinishTime = initialFinishTime,
            onConfirm = { _, _, _, _ -> },
            onDismiss = {},
        )
    }
}
