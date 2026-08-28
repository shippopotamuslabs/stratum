package com.stratum.core.ui.component.workoutlog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Split full/partial reps cell. Unfocused completed/disabled chips use the same sunken fill as
 * [StratumInlineNumericCell]; the divider uses the border color. While the pad is focused, the
 * selected half uses inset fill and the other is transparent.
 * Outer accent border means the pad is focused on this field.
 */
@Composable
fun StratumSplitRepsCell(
    fullRepsText: String,
    partialRepsText: String,
    fullPlaceholder: String,
    selectedMode: RepsEntryMode,
    enabled: Boolean,
    isActive: Boolean,
    completed: Boolean,
    onSelectFull: () -> Unit,
    onSelectPartial: () -> Unit,
    modifier: Modifier = Modifier,
    fullSelection: TextRange = TextRange.Zero,
    partialSelection: TextRange = TextRange.Zero,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val borderColor =
        when {
            isActive -> colors.accent
            else -> colors.borderSubtle
        }
    Row(
        modifier =
            modifier
                .height(36.dp)
                .clip(shapes.chip)
                .border(
                    width = shapes.borderWidth,
                    color = borderColor,
                    shape = shapes.chip,
                ),
    ) {
        SplitHalf(
            value = fullRepsText,
            placeholder = fullPlaceholder,
            selected = selectedMode == RepsEntryMode.Full,
            enabled = enabled,
            isActive = isActive,
            completed = completed,
            contentDescription = stringResource(CoreUiR.string.set_log_reps_full_half),
            onClick = onSelectFull,
            selection = fullSelection,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        Box(
            modifier =
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(colors.borderSubtle),
        )
        SplitHalf(
            value = partialRepsText,
            placeholder = "",
            selected = selectedMode == RepsEntryMode.Partial,
            enabled = enabled,
            isActive = isActive,
            completed = completed,
            contentDescription = stringResource(CoreUiR.string.set_log_reps_partial_half),
            onClick = onSelectPartial,
            selection = partialSelection,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
private fun SplitHalf(
    value: String,
    placeholder: String,
    selected: Boolean,
    enabled: Boolean,
    isActive: Boolean,
    completed: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selection: TextRange = TextRange.Zero,
) {
    val colors = StratumThemeAccessor.colors
    val sunkenOrInset = if (completed || !enabled) colors.surfaceSunken else colors.surfaceInset
    val background =
        when {
            !isActive -> sunkenOrInset
            selected -> sunkenOrInset
            else -> Color.Transparent
        }
    val display = value.ifBlank { placeholder }
    val muted = value.isBlank()
    val isTextSelected = isActive && selected && value.isNotBlank() && selection.min < selection.max
    Box(
        modifier =
            modifier
                .background(background)
                .semantics {
                    role = Role.Button
                    this.contentDescription = contentDescription
                    this.selected = selected
                }
                .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val textModifier =
            if (isTextSelected) {
                Modifier
                    .background(
                        color = colors.accent.copy(alpha = 0.35f),
                        shape = StratumThemeAccessor.shapes.chip,
                    )
                    .padding(horizontal = 4.dp)
            } else {
                Modifier
            }
        StratumText(
            text = display,
            style = StratumThemeAccessor.typography.bodyLarge,
            color = if (muted) colors.textMuted else colors.textData,
            modifier = textModifier,
        )
    }
}
