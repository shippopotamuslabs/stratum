package com.stratum.core.ui.component

import android.text.format.DateFormat
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Tappable time field that opens [StratumTimePickerDialog]. Displays a locale-short formatted
 * time (e.g. "8:53 PM" or "20:53") — never free-text entry.
 */
@Composable
fun StratumTimeField(
    label: String,
    time: LocalTime?,
    onTimeChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "",
    isError: Boolean = false,
    errorText: String? = null,
    dialogTitle: String? = null,
    is24Hour: Boolean = DateFormat.is24HourFormat(LocalContext.current),
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    var showPicker by remember { mutableStateOf(false) }
    val display =
        time?.let { formatShortTime(it) } ?: placeholder.ifBlank { "—" }

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
                muted = time == null,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(CoreUiR.drawable.ic_schedule),
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
        StratumTimePickerDialog(
            initialTime = time ?: LocalTime.now(),
            onTimeSelected = onTimeChange,
            onDismiss = { showPicker = false },
            title = dialogTitle ?: label,
            is24Hour = is24Hour,
        )
    }
}

/** Locale-aware short time (e.g. "8:53 PM" or "20:53"). */
fun formatShortTime(time: LocalTime, locale: Locale = Locale.getDefault()): String =
    time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
