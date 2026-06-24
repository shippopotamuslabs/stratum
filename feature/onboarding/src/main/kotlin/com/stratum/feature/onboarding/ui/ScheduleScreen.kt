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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumFilterChip
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTextField
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.onboarding.ScheduleDraftUi

private val WEEKDAY_OPTIONS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val SESSION_PRESETS = listOf("45" to "45 min", "60" to "60 min", "75" to "75 min")

/**
 * Step 3 of the wizard. Captures training weekdays (multi-select, ≥1) and session
 * length (preset or custom). Presets store their minute value; custom reveals a number
 * field validated against [ScheduleDraftUi.MIN_SESSION_MINUTES]..[ScheduleDraftUi.MAX_SESSION_MINUTES].
 */
@Composable
fun ScheduleScreen(
    draft: ScheduleDraftUi,
    onWeekdayToggled: (String) -> Unit,
    onSessionPresetSelected: (String?) -> Unit,
    onCustomSessionChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        StratumText(text = "Desired Weekly Schedule", style = StratumThemeAccessor.typography.titleLarge)

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StratumText(text = "Training days", style = StratumThemeAccessor.typography.bodyLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WEEKDAY_OPTIONS.forEach { option ->
                    StratumFilterChip(
                        label = option,
                        selected = option in draft.selectedWeekdays,
                        onSelectedChange = { onWeekdayToggled(option) },
                    )
                }
            }
            draft.weekdaysError?.let { error -> StratumText(text = error, muted = true) }
        }

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StratumText(text = "Desired Session Length", style = StratumThemeAccessor.typography.bodyLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SESSION_PRESETS.forEach { (value, label) ->
                    StratumFilterChip(
                        label = label,
                        selected = draft.sessionLengthSelection == value,
                        onSelectedChange = { selected ->
                            onSessionPresetSelected(if (selected) value else null)
                        },
                    )
                }
                StratumFilterChip(
                    label = "Custom",
                    selected = draft.sessionLengthSelection == ScheduleDraftUi.CUSTOM_KEY,
                    onSelectedChange = { selected ->
                        onSessionPresetSelected(if (selected) ScheduleDraftUi.CUSTOM_KEY else null)
                    },
                )
            }
            val sessionError = draft.sessionLengthError
            if (draft.sessionLengthSelection == ScheduleDraftUi.CUSTOM_KEY) {
                StratumTextField(
                    value = draft.customSessionInput,
                    onValueChange = onCustomSessionChange,
                    label = "Minutes",
                    placeholder = "e.g. 90",
                    isError = sessionError != null,
                    errorText = sessionError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            } else if (sessionError != null) {
                StratumText(text = sessionError, muted = true)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}
