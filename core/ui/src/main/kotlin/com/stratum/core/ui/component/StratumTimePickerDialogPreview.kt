package com.stratum.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.stratum.core.ui.R
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId
import java.time.LocalTime

@PreviewLightDark
@Composable
private fun StratumTimePickerDialogTonalPreview() {
    TimePickerDialogPreviewContent(
        family = StratumThemeId.Tonal,
        initialTime = LocalTime.of(14, 30),
    )
}

@PreviewLightDark
@Composable
private fun StratumTimePickerDialogNeoPreview() {
    TimePickerDialogPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        initialTime = LocalTime.of(9, 15),
    )
}

/**
 * Pins the dialog body inline: dialog windows are uncapturable in screenshot goldens
 * (STR-89 pattern).
 */
@Composable
internal fun TimePickerDialogPreviewContent(
    family: StratumThemeId,
    initialTime: LocalTime,
) {
    StratumTheme(themeId = family) {
        StratumTimePickerDialogContent(
            initialTime = initialTime,
            onTimeSelected = {},
            onDismiss = {},
            modifier = Modifier,
            title = null,
            is24Hour = false,
            confirmLabel = stringResource(R.string.stratum_time_picker_confirm),
            dismissLabel = stringResource(R.string.stratum_time_picker_dismiss),
        )
    }
}
