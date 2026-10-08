package com.stratum.core.ui.component

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.stratum.core.ui.R
import com.stratum.core.ui.theme.StratumThemeAccessor
import java.time.LocalTime

/**
 * Material 3 time picker wrapped in Stratum semantic colors so feature modules never import
 * raw Material 3 [TimePicker] APIs (UI_DESIGN.md §4).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StratumTimePickerDialog(
    initialTime: LocalTime,
    onTimeSelected: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    is24Hour: Boolean = DateFormat.is24HourFormat(LocalContext.current),
    confirmLabel: String = stringResource(R.string.stratum_time_picker_confirm),
    dismissLabel: String = stringResource(R.string.stratum_time_picker_dismiss),
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        StratumTimePickerDialogContent(
            initialTime = initialTime,
            onTimeSelected = onTimeSelected,
            onDismiss = onDismiss,
            modifier = modifier,
            title = title,
            is24Hour = is24Hour,
            confirmLabel = confirmLabel,
            dismissLabel = dismissLabel,
        )
    }
}

/**
 * Inline dialog body (STR-94): dialog windows are uncapturable in screenshot goldens, so
 * previews and `@PreviewTest` wrappers pin this content directly. Pixel-identical to the
 * dialog body above.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StratumTimePickerDialogContent(
    initialTime: LocalTime,
    onTimeSelected: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    is24Hour: Boolean = false,
    confirmLabel: String = stringResource(R.string.stratum_time_picker_confirm),
    dismissLabel: String = stringResource(R.string.stratum_time_picker_dismiss),
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val state =
        rememberTimePickerState(
            initialHour = initialTime.hour,
            initialMinute = initialTime.minute,
            is24Hour = is24Hour,
        )
    Column(
            modifier =
                modifier
                    .padding(horizontal = 24.dp)
                    .wrapContentWidth()
                    .wrapContentHeight()
                    .background(color = colors.surfaceRaised, shape = shapes.panel)
                    .border(
                        width = shapes.borderWidth,
                        color = colors.borderStrong,
                        shape = shapes.panel,
                    )
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StratumText(
                text = title ?: stringResource(R.string.stratum_time_picker_title),
                style = StratumThemeAccessor.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )

            TimePicker(
                state = state,
                colors =
                    TimePickerDefaults.colors(
                        clockDialColor = colors.surfaceSunken,
                        clockDialSelectedContentColor = colors.onAccent,
                        clockDialUnselectedContentColor = colors.textPrimary,
                        selectorColor = colors.accent,
                        containerColor = colors.surfaceRaised,
                        periodSelectorBorderColor = colors.borderSubtle,
                        periodSelectorSelectedContainerColor = colors.accent,
                        periodSelectorUnselectedContainerColor = colors.surfaceSunken,
                        periodSelectorSelectedContentColor = colors.onAccent,
                        periodSelectorUnselectedContentColor = colors.textPrimary,
                        timeSelectorSelectedContainerColor = colors.accent,
                        timeSelectorUnselectedContainerColor = colors.surfaceSunken,
                        timeSelectorSelectedContentColor = colors.onAccent,
                        timeSelectorUnselectedContentColor = colors.textPrimary,
                    ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StratumTextButton(
                    text = dismissLabel,
                    onClick = onDismiss,
                    contentColor = colors.textMuted,
                )
                StratumTextButton(
                    text = confirmLabel,
                    onClick = {
                        onTimeSelected(LocalTime.of(state.hour, state.minute))
                        onDismiss()
                    },
                )
            }
        }
}
