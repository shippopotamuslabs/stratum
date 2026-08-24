package com.stratum.core.ui.component.workoutlog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Compact F/P control that fits a numeric-pad key. Selected letter uses accent fill.
 */
@Composable
fun StratumRepsModeToggle(
    mode: RepsEntryMode,
    onModeChange: (RepsEntryMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    Row(modifier = modifier.fillMaxSize()) {
        ModeLetter(
            letter = stringResource(CoreUiR.string.set_log_keyboard_reps_mode_full_letter),
            contentDescription = stringResource(CoreUiR.string.set_log_keyboard_reps_mode_full),
            selected = mode == RepsEntryMode.Full,
            onClick = { onModeChange(RepsEntryMode.Full) },
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        Box(
            modifier =
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(colors.borderSubtle),
        )
        ModeLetter(
            letter = stringResource(CoreUiR.string.set_log_keyboard_reps_mode_partial_letter),
            contentDescription = stringResource(CoreUiR.string.set_log_keyboard_reps_mode_partial),
            selected = mode == RepsEntryMode.Partial,
            onClick = { onModeChange(RepsEntryMode.Partial) },
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
private fun ModeLetter(
    letter: String,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    Box(
        modifier =
            modifier
                .background(if (selected) colors.accent else colors.surfaceRaised.copy(alpha = 0f))
                .semantics {
                    role = Role.Button
                    this.contentDescription = contentDescription
                    this.selected = selected
                }
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        StratumText(
            text = letter,
            style = StratumThemeAccessor.typography.titleMedium,
            color = if (selected) colors.onAccent else colors.textPrimary,
        )
    }
}
