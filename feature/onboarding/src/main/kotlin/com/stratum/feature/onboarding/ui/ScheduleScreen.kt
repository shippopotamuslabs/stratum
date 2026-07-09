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
    onWeekdayToggled: (String) -> Unit,
    onSessionPresetSelected: (String?) -> Unit,
    onCustomSessionMinutesChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    SchedulePreferencesScreen(
        draft = draft,
        onWeekdayToggled = onWeekdayToggled,
        onSessionPresetSelected = onSessionPresetSelected,
        onCustomSessionMinutesChange = onCustomSessionMinutesChange,
        modifier = modifier,
    )
}
