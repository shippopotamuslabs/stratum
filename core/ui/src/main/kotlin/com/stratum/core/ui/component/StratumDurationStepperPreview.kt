package com.stratum.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId

@PreviewLightDark
@Composable
private fun StratumDurationStepperTonalPreview() {
    DurationStepperPreviewGrid(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun StratumDurationStepperNeoPreview() {
    DurationStepperPreviewGrid(family = StratumThemeId.NeoBrutalist)
}

@Composable
internal fun DurationStepperPreviewGrid(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        var minutes by remember { mutableIntStateOf(90) }
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StratumDurationStepper(
                totalMinutes = minutes,
                onTotalMinutesChange = { minutes = it },
                minMinutes = 15,
                maxMinutes = 240,
                hoursLabel = "Hours",
                minutesLabel = "Min",
                incrementContentDescription = "Increase",
                decrementContentDescription = "Decrease",
            )
            StratumDurationStepper(
                totalMinutes = 20,
                onTotalMinutesChange = {},
                minMinutes = 15,
                maxMinutes = 240,
                hoursLabel = "Hours",
                minutesLabel = "Min",
                incrementContentDescription = "Increase",
                decrementContentDescription = "Decrease",
                isError = true,
            )
        }
    }
}
