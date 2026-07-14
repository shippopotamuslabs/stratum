package com.stratum.core.ui.trainingsetup

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.domain.program.schedule.GenerationCycleDefaults

@Preview(name = "Schedule 4-day Light")
@Composable
private fun SchedulePreferencesFourDayPreviewLight() {
    StratumTheme {
        SchedulePreferencesScreen(
            draft =
                ScheduleDraftUi(
                    trainingDayCount = 4,
                    sessionLengthSelection = SESSION_BAND_KEY_MEDIUM,
                    cycleLength = 7,
                    restSlotIndices = setOf(2, 5, 6),
                ),
            onTrainingDayCountChanged = {},
            onSessionPresetSelected = {},
            onCustomSessionMinutesChange = {},
            onTrainingSlotToggled = {},
        )
    }
}

@Preview(name = "Schedule 6-day Light")
@Composable
private fun SchedulePreferencesSixDayPreviewLight() {
    StratumTheme {
        SchedulePreferencesScreen(
            draft =
                ScheduleDraftUi(
                    trainingDayCount = 6,
                    sessionLengthSelection = SESSION_BAND_KEY_MEDIUM,
                    cycleLength = GenerationCycleDefaults.SIX_DAY_ROTATING_CYCLE_LENGTH,
                    restSlotIndices = GenerationCycleDefaults.SIX_DAY_ROTATING_DEFAULT_RESTS,
                ),
            onTrainingDayCountChanged = {},
            onSessionPresetSelected = {},
            onCustomSessionMinutesChange = {},
            onSixDayCycleLengthSelected = {},
            onTrainingSlotToggled = {},
        )
    }
}

@Preview(name = "Schedule 6-day Dark")
@Composable
private fun SchedulePreferencesSixDayPreviewDark() {
    StratumTheme(darkTheme = true) {
        SchedulePreferencesScreen(
            draft =
                ScheduleDraftUi(
                    trainingDayCount = 6,
                    sessionLengthSelection = SESSION_BAND_KEY_MEDIUM,
                    cycleLength = GenerationCycleDefaults.SIX_DAY_FIXED_WEEK_CYCLE_LENGTH,
                    restSlotIndices = setOf(6),
                ),
            onTrainingDayCountChanged = {},
            onSessionPresetSelected = {},
            onCustomSessionMinutesChange = {},
            onSixDayCycleLengthSelected = {},
            onTrainingSlotToggled = {},
        )
    }
}
