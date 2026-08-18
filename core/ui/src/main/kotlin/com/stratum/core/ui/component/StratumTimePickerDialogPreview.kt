package com.stratum.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId
import java.time.LocalTime

@PreviewLightDark
@Composable
private fun StratumTimePickerDialogTonalPreview() {
    StratumTheme(themeId = StratumThemeId.Tonal, darkTheme = true) {
        StratumTimePickerDialog(
            initialTime = LocalTime.of(14, 30),
            onTimeSelected = {},
            onDismiss = {},
            is24Hour = false,
        )
    }
}

@PreviewLightDark
@Composable
private fun StratumTimePickerDialogNeoPreview() {
    StratumTheme(themeId = StratumThemeId.NeoBrutalist, darkTheme = true) {
        StratumTimePickerDialog(
            initialTime = LocalTime.of(9, 15),
            onTimeSelected = {},
            onDismiss = {},
            is24Hour = false,
        )
    }
}
