package com.stratum.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Tappable date field that opens [StratumDatePickerDialog]. Displays a locale-medium formatted
 * date (e.g. "Jul 16, 2026") — never free-text ISO entry.
 */
@Composable
fun StratumDateField(
    label: String,
    date: LocalDate?,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "",
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
    isError: Boolean = false,
    errorText: String? = null,
    dialogTitle: String? = null,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    var showPicker by remember { mutableStateOf(false) }
    val display =
        date?.let { formatMediumDate(it) } ?: placeholder.ifBlank { "—" }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
    ) {
        StratumText(
            text = label,
            style = StratumThemeAccessor.typography.sectionLabel,
            muted = true,
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(color = colors.surfaceRaised, shape = shapes.panel)
                    .border(
                        width = shapes.borderWidth,
                        color = if (isError) colors.error else colors.borderSubtle,
                        shape = shapes.panel,
                    )
                    .then(
                        if (enabled) {
                            Modifier.clickable(role = Role.Button) { showPicker = true }
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = StratumSpacing.s16, vertical = StratumSpacing.s16),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StratumText(
                text = display,
                style = StratumThemeAccessor.typography.bodyLarge,
                muted = date == null,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(CoreUiR.drawable.ic_calendar),
                contentDescription = null,
                tint = if (enabled) colors.accent else colors.textMuted,
            )
        }
        if (isError && !errorText.isNullOrBlank()) {
            StratumText(
                text = errorText,
                style = StratumThemeAccessor.typography.sectionLabel,
                color = colors.error,
            )
        }
    }

    if (showPicker) {
        StratumDatePickerDialog(
            selectedDate = date ?: LocalDate.now(),
            onDateSelected = onDateChange,
            onDismiss = { showPicker = false },
            title = dialogTitle ?: label,
            minDate = minDate,
            maxDate = maxDate,
        )
    }
}

/** Locale-aware medium date (e.g. "Jul 16, 2026"). */
fun formatMediumDate(date: LocalDate, locale: Locale = Locale.getDefault()): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
