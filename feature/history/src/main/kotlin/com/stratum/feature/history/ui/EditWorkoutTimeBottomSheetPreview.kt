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
    StratumTheme(themeId = StratumThemeId.Tonal, darkTheme = true) {
        EditWorkoutTimeBottomSheet(
            initialStartDate = LocalDate.of(2025, 8, 10),
            initialStartTime = LocalTime.of(20, 53),
            initialFinishDate = LocalDate.of(2025, 8, 10),
            initialFinishTime = LocalTime.of(21, 38),
            onConfirm = { _, _, _, _ -> },
            onDismiss = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun EditWorkoutTimeBottomSheetNeoPreview() {
    StratumTheme(themeId = StratumThemeId.NeoBrutalist, darkTheme = true) {
        EditWorkoutTimeBottomSheet(
            initialStartDate = LocalDate.of(2025, 8, 10),
            initialStartTime = LocalTime.of(22, 0),
            initialFinishDate = LocalDate.of(2025, 8, 11),
            initialFinishTime = LocalTime.of(0, 30),
            onConfirm = { _, _, _, _ -> },
            onDismiss = {},
        )
    }
}
