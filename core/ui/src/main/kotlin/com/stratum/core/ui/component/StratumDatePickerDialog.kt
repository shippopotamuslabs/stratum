package com.stratum.core.ui.component

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.stratum.core.ui.R
import com.stratum.core.ui.theme.StratumThemeAccessor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Material 3 date picker wrapped in Stratum semantic colors so feature modules never import
 * raw [DatePickerDialog] / [DatePicker] APIs (UI_DESIGN.md §4).
 *
 * Dates are interpreted as device-local calendar days; the picker state uses UTC midnight
 * millis (Material 3 convention) via [localDateToUtcMillis] / [utcMillisToLocalDate].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StratumDatePickerDialog(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
    confirmLabel: String = stringResource(R.string.stratum_date_picker_confirm),
    dismissLabel: String = stringResource(R.string.stratum_date_picker_dismiss),
) {
    val colors = StratumThemeAccessor.colors
    val state =
        rememberDatePickerState(
            initialSelectedDateMillis = localDateToUtcMillis(selectedDate),
            selectableDates =
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                        val date = utcMillisToLocalDate(utcTimeMillis)
                        if (minDate != null && date.isBefore(minDate)) return false
                        if (maxDate != null && date.isAfter(maxDate)) return false
                        return true
                    }
                },
        )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        confirmButton = {
            StratumTextButton(
                text = confirmLabel,
                onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        onDateSelected(utcMillisToLocalDate(millis))
                    }
                    onDismiss()
                },
            )
        },
        dismissButton = {
            StratumTextButton(
                text = dismissLabel,
                onClick = onDismiss,
                contentColor = colors.textMuted,
            )
        },
        colors =
            DatePickerDefaults.colors(
                containerColor = colors.surfaceRaised,
                titleContentColor = colors.textPrimary,
                headlineContentColor = colors.textPrimary,
                weekdayContentColor = colors.textMuted,
                subheadContentColor = colors.textMuted,
                navigationContentColor = colors.textPrimary,
                yearContentColor = colors.textPrimary,
                disabledYearContentColor = colors.textMuted,
                currentYearContentColor = colors.accent,
                selectedYearContentColor = colors.onAccent,
                disabledSelectedYearContentColor = colors.textMuted,
                selectedYearContainerColor = colors.accent,
                disabledSelectedYearContainerColor = colors.surfaceSunken,
                dayContentColor = colors.textPrimary,
                disabledDayContentColor = colors.textMuted,
                selectedDayContentColor = colors.onAccent,
                disabledSelectedDayContentColor = colors.textMuted,
                selectedDayContainerColor = colors.accent,
                disabledSelectedDayContainerColor = colors.surfaceSunken,
                todayContentColor = colors.accent,
                todayDateBorderColor = colors.accent,
                dayInSelectionRangeContentColor = colors.textPrimary,
                dayInSelectionRangeContainerColor = colors.surfaceSunken,
            ),
    ) {
        DatePicker(
            state = state,
            title =
                if (title != null) {
                    { Text(text = title, color = colors.textPrimary) }
                } else {
                    null
                },
            colors =
                DatePickerDefaults.colors(
                    containerColor = colors.surfaceRaised,
                    titleContentColor = colors.textPrimary,
                    headlineContentColor = colors.textPrimary,
                    weekdayContentColor = colors.textMuted,
                    subheadContentColor = colors.textMuted,
                    navigationContentColor = colors.textPrimary,
                    yearContentColor = colors.textPrimary,
                    currentYearContentColor = colors.accent,
                    selectedYearContentColor = colors.onAccent,
                    selectedYearContainerColor = colors.accent,
                    dayContentColor = colors.textPrimary,
                    selectedDayContentColor = colors.onAccent,
                    selectedDayContainerColor = colors.accent,
                    todayContentColor = colors.accent,
                    todayDateBorderColor = colors.accent,
                ),
        )
    }
}

/** UTC midnight millis for Material 3 [DatePicker] state. */
fun localDateToUtcMillis(date: LocalDate): Long =
    date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** Inverse of [localDateToUtcMillis]. */
fun utcMillisToLocalDate(utcMillis: Long): LocalDate =
    Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
