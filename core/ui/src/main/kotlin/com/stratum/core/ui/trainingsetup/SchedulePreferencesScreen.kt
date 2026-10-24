// STR-142 allowlist: section rhythm (20.dp) pinned; screen margins adopted.
@file:Suppress("ForbiddenRawSpacingLiteralInUi")
package com.stratum.core.ui.trainingsetup

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R
import com.stratum.core.ui.component.InvalidDurationInput
import com.stratum.core.ui.component.StratumDurationStepper
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumSegmentedControl
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumToggleGroup
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.domain.program.schedule.GenerationCycleDefaults

/**
 * Shared schedule preferences step.
 *
 * 1. Training day **count** (stepper, default 4)
 * 2. Training-slot placement: N&lt;6 → 7-slot week; N=6 → cycle length 8 vs 7 + pick N train slots
 * 3. Session length
 *
 * Users pick **training** days (not rests). Domain [ScheduleDraftUi.restSlotIndices] is the
 * complement. No weekday multi-select — synthetic weekdays are filled for loaders only.
 */
@Composable
fun SchedulePreferencesScreen(
    draft: ScheduleDraftUi,
    onTrainingDayCountChanged: (Int) -> Unit,
    onSessionPresetSelected: (String?) -> Unit,
    onCustomSessionMinutesChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onSixDayCycleLengthSelected: (Int) -> Unit = {},
    onTrainingSlotToggled: (Int) -> Unit = {},
    // Glass (§12.8): added inside the scroll (padding sits after
    // verticalScroll in the chain), so content scrolls beneath the bar.
    // 0 keeps other hosts (onboarding) unchanged.
    topClearance: Dp = 0.dp,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = StratumSpacing.s16)
                .padding(top = StratumSpacing.s12 + topClearance, bottom = StratumSpacing.s12),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        TrainingDayCountSection(
            count = draft.trainingDayCount,
            onCountChanged = onTrainingDayCountChanged,
        )

        if (draft.showRestPlacement) {
            TrainingSlotPlacementSection(
                draft = draft,
                onSixDayCycleLengthSelected = onSixDayCycleLengthSelected,
                onTrainingSlotToggled = onTrainingSlotToggled,
            )
        }

        SessionLengthSection(
            draft = draft,
            onSessionPresetSelected = onSessionPresetSelected,
            onCustomSessionMinutesChange = onCustomSessionMinutesChange,
        )

        Spacer(modifier = Modifier.height(StratumSpacing.s8))
    }
}

@Composable
private fun TrainingDayCountSection(
    count: Int,
    onCountChanged: (Int) -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val decreaseCd = stringResource(R.string.training_setup_schedule_days_step_down)
    val increaseCd = stringResource(R.string.training_setup_schedule_days_step_up)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
    ) {
        StratumText(
            text = stringResource(R.string.training_setup_schedule_days_heading),
            style = StratumThemeAccessor.typography.bodyLarge,
        )
        StratumText(
            text = stringResource(R.string.training_setup_schedule_days_body),
            muted = true,
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .border(shapes.borderWidth, colors.borderSubtle, shapes.panel)
                    .padding(horizontal = StratumSpacing.s8, vertical = StratumSpacing.s4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StratumIconButton(
                onClick = { onCountChanged(count - 1) },
                enabled = count > ScheduleDraftUi.MIN_TRAINING_DAY_COUNT,
                modifier = Modifier.semantics { contentDescription = decreaseCd },
            ) {
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
            StratumText(
                text =
                    stringResource(
                        R.string.training_setup_schedule_days_value,
                        count,
                    ),
                style = StratumThemeAccessor.typography.titleMedium,
            )
            StratumIconButton(
                onClick = { onCountChanged(count + 1) },
                enabled = count < TrainingSetupValidationConstants.MAX_TRAINING_DAYS,
                modifier = Modifier.semantics { contentDescription = increaseCd },
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun TrainingSlotPlacementSection(
    draft: ScheduleDraftUi,
    onSixDayCycleLengthSelected: (Int) -> Unit,
    onTrainingSlotToggled: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(StratumSpacing.s8)) {
        if (draft.showSixDayCycleOptions) {
            StratumText(
                text = stringResource(R.string.training_setup_schedule_cycle_heading),
                style = StratumThemeAccessor.typography.bodyLarge,
            )
            StratumText(
                text = stringResource(R.string.training_setup_schedule_cycle_body),
                muted = true,
            )
            val cycleSegments =
                listOf(
                    stringResource(R.string.training_setup_schedule_cycle_8_day),
                    stringResource(R.string.training_setup_schedule_cycle_7_day),
                )
            val selectedCycleIndex =
                when (draft.cycleLength) {
                    GenerationCycleDefaults.SIX_DAY_ROTATING_CYCLE_LENGTH -> 0
                    GenerationCycleDefaults.SIX_DAY_FIXED_WEEK_CYCLE_LENGTH -> 1
                    else -> 0
                }
            StratumSegmentedControl(
                modifier = Modifier.fillMaxWidth(),
                segments = cycleSegments,
                selectedIndex = selectedCycleIndex,
                onSelectedIndexChange = { index ->
                    val length =
                        if (index == 0) {
                            GenerationCycleDefaults.SIX_DAY_ROTATING_CYCLE_LENGTH
                        } else {
                            GenerationCycleDefaults.SIX_DAY_FIXED_WEEK_CYCLE_LENGTH
                        }
                    onSixDayCycleLengthSelected(length)
                },
            )
        }

        val cycleLength =
            draft.cycleLength
                ?: GenerationCycleDefaults.defaultCycleLength(draft.trainingDayCount)
        val expectedTrain = draft.trainingDayCount
        StratumText(
            text =
                stringResource(
                    R.string.training_setup_schedule_train_placement_heading,
                    expectedTrain,
                ),
            style = StratumThemeAccessor.typography.bodyLarge,
        )
        if (!draft.showSixDayCycleOptions) {
            StratumText(
                text = stringResource(R.string.training_setup_schedule_train_placement_body),
                muted = true,
            )
        }
        val slotLabels = (1..cycleLength).map { index -> index.toString() }
        val selectedTrainLabels =
            draft.trainingSlotIndices.map { index -> (index + 1).toString() }.toSet()
        StratumToggleGroup(
            options = slotLabels,
            selectedOptions = selectedTrainLabels,
            onOptionToggle = { label ->
                val slotIndex = label.toIntOrNull()?.minus(1) ?: return@StratumToggleGroup
                onTrainingSlotToggled(slotIndex)
            },
        )
        if (draft.hasRestPlacementError) {
            StratumText(
                text =
                    stringResource(
                        R.string.training_setup_schedule_error_train_placement,
                        expectedTrain,
                    ),
                muted = true,
            )
        }
    }
}

@Composable
private fun SessionLengthSection(
    draft: ScheduleDraftUi,
    onSessionPresetSelected: (String?) -> Unit,
    onCustomSessionMinutesChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.padding(top = StratumSpacing.s4).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
    ) {
        StratumText(
            text = stringResource(R.string.training_setup_schedule_session_length_heading),
            style = StratumThemeAccessor.typography.bodyLarge,
        )
        val sessionLengthLabels =
            listOf(
                stringResource(R.string.training_setup_schedule_session_preset_short),
                stringResource(R.string.training_setup_schedule_session_preset_medium),
                stringResource(R.string.training_setup_schedule_session_preset_long),
                stringResource(R.string.training_setup_schedule_session_custom),
            )
        val selectedSessionIndex =
            draft.sessionLengthSelection?.let { selection ->
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
                incrementContentDescription =
                    stringResource(R.string.training_setup_schedule_session_step_up),
                decrementContentDescription =
                    stringResource(R.string.training_setup_schedule_session_step_down),
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
}

@Composable
private fun SessionLengthErrorKind.toMessage(): String =
    when (this) {
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
private fun InvalidDurationInput.toMessage(): String =
    when (this) {
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
