package com.stratum.feature.onboarding.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.stratum.core.ui.trainingsetup.ScheduleDraftUi
import com.stratum.core.ui.trainingsetup.SchedulePreferencesScreen

/**
 * Onboarding Schedule step — thin wrapper over the shared [SchedulePreferencesScreen].
 */
@Composable
fun ScheduleScreen(
    draft: ScheduleDraftUi,
    onTrainingDayCountChanged: (Int) -> Unit,
    onSessionPresetSelected: (String?) -> Unit,
    onCustomSessionMinutesChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onSixDayCycleLengthSelected: (Int) -> Unit = {},
    onTrainingSlotToggled: (Int) -> Unit = {},
) {
    SchedulePreferencesScreen(
        draft = draft,
        onTrainingDayCountChanged = onTrainingDayCountChanged,
        onSessionPresetSelected = onSessionPresetSelected,
        onCustomSessionMinutesChange = onCustomSessionMinutesChange,
        onSixDayCycleLengthSelected = onSixDayCycleLengthSelected,
        onTrainingSlotToggled = onTrainingSlotToggled,
        modifier = modifier,
    )
}
