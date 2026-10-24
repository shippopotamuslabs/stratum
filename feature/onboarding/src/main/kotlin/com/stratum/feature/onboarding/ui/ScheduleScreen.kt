package com.stratum.feature.onboarding.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    // Glass (§12.8, STR-192): forwarded to the shared screen's in-scroll
    // clearance. 0 keeps other hosts unchanged.
    topClearance: Dp = 0.dp,
) {
    SchedulePreferencesScreen(
        draft = draft,
        onTrainingDayCountChanged = onTrainingDayCountChanged,
        onSessionPresetSelected = onSessionPresetSelected,
        onCustomSessionMinutesChange = onCustomSessionMinutesChange,
        onSixDayCycleLengthSelected = onSixDayCycleLengthSelected,
        onTrainingSlotToggled = onTrainingSlotToggled,
        modifier = modifier,
        topClearance = topClearance,
    )
}
