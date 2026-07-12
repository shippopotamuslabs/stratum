package com.stratum.core.ui.trainingsetup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R
import com.stratum.core.ui.component.InvalidDurationInput
import com.stratum.core.ui.component.StratumDurationStepper
import com.stratum.core.ui.component.StratumSegmentedControl
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumToggleGroup
import com.stratum.core.ui.theme.StratumThemeAccessor

private val WEEKDAY_OPTIONS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/**
 * Shared schedule preferences step (weekdays + session length). Used by onboarding and create program.
 */
@Composable
fun SchedulePreferencesScreen(
    draft: ScheduleDraftUi,
    onWeekdayToggled: (String) -> Unit,
    onSessionPresetSelected: (String?) -> Unit,
    onCustomSessionMinutesChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StratumText(
                text = stringResource(R.string.training_setup_schedule_weekdays_heading),
                style = StratumThemeAccessor.typography.bodyLarge,
            )
            StratumToggleGroup(
                options = WEEKDAY_OPTIONS,
                selectedOptions = draft.selectedWeekdays,
                onOptionToggle = onWeekdayToggled,
            )
            if (draft.hasWeekdaysError) {
                StratumText(
                    text = stringResource(R.string.training_setup_schedule_error_weekdays),
                    muted = true,
                )
            }
        }

        Column(modifier = Modifier.padding(top = 20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StratumText(
                text = stringResource(R.string.training_setup_schedule_session_length_heading),
                style = StratumThemeAccessor.typography.bodyLarge,
            )
            val sessionLengthLabels = listOf(
                stringResource(R.string.training_setup_schedule_session_preset_short),
                stringResource(R.string.training_setup_schedule_session_preset_medium),
                stringResource(R.string.training_setup_schedule_session_preset_long),
                stringResource(R.string.training_setup_schedule_session_custom),
            )
            val selectedSessionIndex = draft.sessionLengthSelection?.let { selection ->
                ScheduleDraftUi.BAND_KEYS.indexOf(selection).takeIf { it >= 0 }
            }
            StratumSegmentedControl(
                modifier = Modifier.fillMaxWidth(),
                segments = sessionLengthLabels,
                selectedIndex = selectedSessionIndex,
                onSelectedIndexChange = { index ->
                    onSessionPresetSelected(ScheduleDraftUi.BAND_KEYS[index])
                },
            )
            val sessionError = draft.sessionLengthErrorKind
            if (draft.sessionLengthSelection == ScheduleDraftUi.CUSTOM_KEY) {
                var inlineInvalid by remember { mutableStateOf<InvalidDurationInput?>(null) }
                StratumDurationStepper(
                    totalMinutes = draft.customSessionMinutes,
                    onTotalMinutesChange = onCustomSessionMinutesChange,
                    minMinutes = TrainingSetupValidationConstants.MIN_SESSION_MINUTES,
                    maxMinutes = TrainingSetupValidationConstants.MAX_SESSION_MINUTES,
                    hoursLabel = stringResource(R.string.training_setup_schedule_session_hours_label),
                    minutesLabel = stringResource(R.string.training_setup_schedule_session_minutes_label),
                    incrementContentDescription = stringResource(R.string.training_setup_schedule_session_step_up),
                    decrementContentDescription = stringResource(R.string.training_setup_schedule_session_step_down),
                    isError = sessionError != null,
                    onInvalidInput = { reason -> inlineInvalid = reason },
                )
                val inlineMessage = inlineInvalid?.toMessage()
                if (sessionError != null) {
                    StratumText(text = sessionError.toMessage(), muted = true)
                } else if (inlineMessage != null) {
                    StratumText(text = inlineMessage, muted = true)
                }
            } else if (sessionError != null) {
                StratumText(text = sessionError.toMessage(), muted = true)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SessionLengthErrorKind.toMessage(): String = when (this) {
    SessionLengthErrorKind.Required ->
        stringResource(R.string.training_setup_schedule_error_session_required)
    SessionLengthErrorKind.CustomBlank ->
        stringResource(R.string.training_setup_schedule_error_session_custom_blank)
    SessionLengthErrorKind.NotANumber ->
        stringResource(R.string.training_setup_schedule_error_session_not_number)
    SessionLengthErrorKind.TooShort ->
        stringResource(
            R.string.training_setup_schedule_error_session_too_short,
            TrainingSetupValidationConstants.MIN_SESSION_MINUTES,
        )
    SessionLengthErrorKind.TooLong ->
        stringResource(
            R.string.training_setup_schedule_error_session_too_long,
            TrainingSetupValidationConstants.MAX_SESSION_MINUTES,
        )
}

@Composable
private fun InvalidDurationInput.toMessage(): String = when (this) {
    InvalidDurationInput.Blank ->
        stringResource(R.string.training_setup_schedule_error_session_custom_blank)
    InvalidDurationInput.NotANumber ->
        stringResource(R.string.training_setup_schedule_error_session_not_number)
    InvalidDurationInput.TooShort ->
        stringResource(
            R.string.training_setup_schedule_error_session_too_short,
            TrainingSetupValidationConstants.MIN_SESSION_MINUTES,
        )
    InvalidDurationInput.TooLong ->
        stringResource(
            R.string.training_setup_schedule_error_session_too_long,
            TrainingSetupValidationConstants.MAX_SESSION_MINUTES,
        )
}
