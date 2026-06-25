package com.stratum.feature.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import com.stratum.core.ui.component.InvalidDurationInput
import com.stratum.core.ui.component.StratumDurationStepper
import com.stratum.core.ui.component.StratumFilterChip
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.onboarding.OnboardingValidationConstants
import com.stratum.feature.onboarding.R
import com.stratum.feature.onboarding.ScheduleDraftUi
import com.stratum.feature.onboarding.SessionLengthErrorKind

private val WEEKDAY_OPTIONS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val SESSION_PRESETS = listOf("45" to "45", "60" to "60", "75" to "75")

/**
 * Step 3 of the wizard. Captures training weekdays and session length.
 */
@Composable
fun ScheduleScreen(
    draft: ScheduleDraftUi,
    onWeekdayToggled: (String) -> Unit,
    onSessionPresetSelected: (String?) -> Unit,
    onCustomSessionMinutesChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StratumText(
                text = stringResource(R.string.schedule_weekdays_heading),
                style = StratumThemeAccessor.typography.bodyLarge,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WEEKDAY_OPTIONS.forEach { option ->
                    StratumFilterChip(
                        label = option,
                        selected = option in draft.selectedWeekdays,
                        onSelectedChange = { onWeekdayToggled(option) },
                    )
                }
            }
            if (draft.hasWeekdaysError) {
                StratumText(
                    text = stringResource(R.string.schedule_error_weekdays),
                    muted = true,
                )
            }
        }

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StratumText(
                text = stringResource(R.string.schedule_session_length_heading),
                style = StratumThemeAccessor.typography.bodyLarge,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SESSION_PRESETS.forEach { (value, labelKey) ->
                    val label = when (labelKey) {
                        "45" -> stringResource(R.string.schedule_session_preset_45)
                        "60" -> stringResource(R.string.schedule_session_preset_60)
                        else -> stringResource(R.string.schedule_session_preset_75)
                    }
                    StratumFilterChip(
                        label = label,
                        selected = draft.sessionLengthSelection == value,
                        onSelectedChange = { selected ->
                            onSessionPresetSelected(if (selected) value else null)
                        },
                    )
                }
                StratumFilterChip(
                    label = stringResource(R.string.schedule_session_custom),
                    selected = draft.sessionLengthSelection == ScheduleDraftUi.CUSTOM_KEY,
                    onSelectedChange = { selected ->
                        onSessionPresetSelected(if (selected) ScheduleDraftUi.CUSTOM_KEY else null)
                    },
                )
            }
            val sessionError = draft.sessionLengthErrorKind
            if (draft.sessionLengthSelection == ScheduleDraftUi.CUSTOM_KEY) {
                // Latest rejection reported by the stepper for a typed value that was
                // blank, non-numeric, or out of range. Lives only in presentation: the
                // persisted draft keeps the last valid value and is re-validated on Next.
                var inlineInvalid by remember { mutableStateOf<InvalidDurationInput?>(null) }
                StratumDurationStepper(
                    totalMinutes = draft.customSessionMinutes,
                    onTotalMinutesChange = onCustomSessionMinutesChange,
                    minMinutes = OnboardingValidationConstants.MIN_SESSION_MINUTES,
                    maxMinutes = OnboardingValidationConstants.MAX_SESSION_MINUTES,
                    hoursLabel = stringResource(R.string.schedule_session_hours_label),
                    minutesLabel = stringResource(R.string.schedule_session_minutes_label),
                    incrementContentDescription = stringResource(R.string.schedule_session_step_up),
                    decrementContentDescription = stringResource(R.string.schedule_session_step_down),
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
        stringResource(R.string.schedule_error_session_required)
    SessionLengthErrorKind.CustomBlank ->
        stringResource(R.string.schedule_error_session_custom_blank)
    SessionLengthErrorKind.NotANumber ->
        stringResource(R.string.schedule_error_session_not_number)
    SessionLengthErrorKind.TooShort ->
        stringResource(
            R.string.schedule_error_session_too_short,
            OnboardingValidationConstants.MIN_SESSION_MINUTES,
        )
    SessionLengthErrorKind.TooLong ->
        stringResource(
            R.string.schedule_error_session_too_long,
            OnboardingValidationConstants.MAX_SESSION_MINUTES,
        )
}

@Composable
private fun InvalidDurationInput.toMessage(): String = when (this) {
    InvalidDurationInput.Blank ->
        stringResource(R.string.schedule_error_session_custom_blank)
    InvalidDurationInput.NotANumber ->
        stringResource(R.string.schedule_error_session_not_number)
    InvalidDurationInput.TooShort ->
        stringResource(
            R.string.schedule_error_session_too_short,
            OnboardingValidationConstants.MIN_SESSION_MINUTES,
        )
    InvalidDurationInput.TooLong ->
        stringResource(
            R.string.schedule_error_session_too_long,
            OnboardingValidationConstants.MAX_SESSION_MINUTES,
        )
}
