package com.stratum.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId
import java.time.LocalTime

@PreviewLightDark
@Composable
private fun StratumTimeFieldTonalPreview() {
    StratumTheme(themeId = StratumThemeId.Tonal, darkTheme = true) {
        TimeFieldPreviewColumn()
    }
}

@PreviewLightDark
@Composable
private fun StratumTimeFieldNeoPreview() {
    StratumTheme(themeId = StratumThemeId.NeoBrutalist, darkTheme = true) {
        TimeFieldPreviewColumn()
    }
}

@Composable
private fun TimeFieldPreviewColumn() {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StratumTimeField(
            label = "Start Time",
            time = LocalTime.of(20, 53),
            onTimeChange = {},
        )
        StratumTimeField(
            label = "Finish Time",
            time = LocalTime.of(21, 38),
            onTimeChange = {},
        )
        StratumTimeField(
            label = "Empty Time",
            time = null,
            placeholder = "Select time",
            onTimeChange = {},
        )
        StratumTimeField(
            label = "Error Time",
            time = LocalTime.of(19, 0),
            onTimeChange = {},
            isError = true,
            errorText = "Finish time cannot be before start time",
        )
    }
}
