package com.stratum.feature.history.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumDateField
import com.stratum.core.ui.component.StratumOutlinedButton
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTimeField
import com.stratum.core.ui.component.formatWorkoutDuration
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.history.R
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Composable
fun EditWorkoutTimeBottomSheet(
    initialStartDate: LocalDate,
    initialStartTime: LocalTime,
    initialFinishDate: LocalDate,
    initialFinishTime: LocalTime,
    onConfirm: (
        startDate: LocalDate,
        startTime: LocalTime,
        finishDate: LocalDate,
        finishTime: LocalTime,
    ) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    zoneId: ZoneId = ZoneId.systemDefault(),
) {
    StratumBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = stringResource(R.string.history_edit_time_sheet_title),
    ) {
        EditWorkoutTimeSheetContent(
            initialStartDate = initialStartDate,
            initialStartTime = initialStartTime,
            initialFinishDate = initialFinishDate,
            initialFinishTime = initialFinishTime,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            zoneId = zoneId,
        )
    }
}

/**
 * Inline sheet body (STR-95): dialog windows are uncapturable in screenshot goldens, so
 * previews and `@PreviewTest` wrappers pin this content directly. Pixel-identical to the
 * sheet body above.
 */
@Composable
internal fun EditWorkoutTimeSheetContent(
    initialStartDate: LocalDate,
    initialStartTime: LocalTime,
    initialFinishDate: LocalDate,
    initialFinishTime: LocalTime,
    onConfirm: (
        startDate: LocalDate,
        startTime: LocalTime,
        finishDate: LocalDate,
        finishTime: LocalTime,
    ) -> Unit,
    onDismiss: () -> Unit,
    zoneId: ZoneId = ZoneId.systemDefault(),
) {
    var selectedStartDate by remember { mutableStateOf(initialStartDate) }
    var selectedStartTime by remember {
        mutableStateOf(initialStartTime.truncatedTo(ChronoUnit.MINUTES))
    }
    var selectedFinishDate by remember { mutableStateOf(initialFinishDate) }
    var selectedFinishTime by remember {
        mutableStateOf(initialFinishTime.truncatedTo(ChronoUnit.MINUTES))
    }

    val startInstant =
        remember(selectedStartDate, selectedStartTime, zoneId) {
            selectedStartDate.atTime(selectedStartTime).atZone(zoneId).toInstant()
        }
    val finishInstant =
        remember(selectedFinishDate, selectedFinishTime, zoneId) {
            selectedFinishDate.atTime(selectedFinishTime).atZone(zoneId).toInstant()
        }
    val isOrderError = finishInstant.isBefore(startInstant)
    val durationMillis =
        remember(startInstant, finishInstant, isOrderError) {
            if (isOrderError) 0L else Duration.between(startInstant, finishInstant).toMillis()
        }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StratumDateField(
                label = stringResource(R.string.history_edit_time_start_date_label),
                date = selectedStartDate,
                onDateChange = { newStartDate ->
                    val dayDiff = ChronoUnit.DAYS.between(selectedStartDate, selectedFinishDate)
                    selectedStartDate = newStartDate
                    selectedFinishDate = newStartDate.plusDays(dayDiff.coerceAtLeast(0))
                },
                modifier = Modifier.weight(1f),
            )
            StratumTimeField(
                label = stringResource(R.string.history_edit_time_start_time_label),
                time = selectedStartTime,
                onTimeChange = { selectedStartTime = it },
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StratumDateField(
                label = stringResource(R.string.history_edit_time_finish_date_label),
                date = selectedFinishDate,
                onDateChange = { selectedFinishDate = it },
                modifier = Modifier.weight(1f),
                isError = isOrderError,
            )
            StratumTimeField(
                label = stringResource(R.string.history_edit_time_finish_time_label),
                time = selectedFinishTime,
                onTimeChange = { selectedFinishTime = it },
                modifier = Modifier.weight(1f),
                isError = isOrderError,
            )
        }

        if (isOrderError) {
            StratumText(
                text = stringResource(R.string.history_edit_time_error_order),
                style = StratumThemeAccessor.typography.sectionLabel,
                color = StratumThemeAccessor.colors.error,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StratumText(
                    text =
                        stringResource(
                            R.string.history_edit_time_duration_label,
                            formatWorkoutDuration(durationMillis),
                        ),
                    style = StratumThemeAccessor.typography.titleMedium,
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StratumButton(
                text = stringResource(R.string.history_edit_time_save),
                onClick = {
                    if (!isOrderError) {
                        onConfirm(
                            selectedStartDate,
                            selectedStartTime,
                            selectedFinishDate,
                            selectedFinishTime,
                        )
                    }
                },
                enabled = !isOrderError,
                modifier = Modifier.fillMaxWidth(),
            )
            StratumOutlinedButton(
                text = stringResource(R.string.history_edit_time_cancel),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
