package com.stratum.feature.program.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumSupersetGroupColors
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.program.R
import com.stratum.feature.program.SupersetLinkDirection

@Composable
internal fun SupersetLinkBottomSheet(
    options: List<SupersetLinkSheetOption>,
    showRestBetweenLiftsToggle: Boolean,
    useRestTimerBetweenLifts: Boolean,
    onUseRestTimerBetweenLiftsChange: (Boolean) -> Unit,
    onOptionSelected: (SupersetLinkDirection) -> Unit,
    onDismiss: () -> Unit,
) {
    StratumBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.edit_workout_create_superset),
    ) {
        options.forEach { option ->
            SupersetLinkSheetRow(
                label = stringResource(option.labelRes),
                trailingIconRes = option.trailingIconRes,
                onClick = { onOptionSelected(option.direction) },
            )
        }
        if (showRestBetweenLiftsToggle) {
            SupersetRestBetweenLiftsRow(
                checked = useRestTimerBetweenLifts,
                onCheckedChange = onUseRestTimerBetweenLiftsChange,
            )
        }
    }
}

@Composable
private fun SupersetRestBetweenLiftsRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(color = colors.surfaceRaised, shape = shapes.panel)
                .border(width = shapes.borderWidth, color = colors.borderSubtle, shape = shapes.panel)
                .clickable { onCheckedChange(!checked) }
                .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StratumText(
            text = stringResource(R.string.edit_workout_use_rest_timer_between_lifts),
            style = StratumThemeAccessor.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors =
                CheckboxDefaults.colors(
                    checkedColor = colors.accent,
                    uncheckedColor = colors.textMuted,
                    checkmarkColor = colors.onAccent,
                ),
        )
    }
}

@Composable
private fun SupersetLinkSheetRow(
    label: String,
    @DrawableRes trailingIconRes: Int,
    onClick: () -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(color = colors.surfaceRaised, shape = shapes.panel)
                .border(width = shapes.borderWidth, color = colors.borderSubtle, shape = shapes.panel)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StratumText(
            text = label,
            style = StratumThemeAccessor.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(trailingIconRes),
            contentDescription = null,
            tint = colors.textPrimary,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
internal fun SupersetGroupChip(
    groupId: String,
    modifier: Modifier = Modifier,
) {
    StratumText(
        text = stringResource(R.string.edit_workout_superset_chip),
        style =
            StratumThemeAccessor.typography.sectionLabel.copy(
                color = StratumSupersetGroupColors.contentColor,
            ),
        modifier =
            modifier
                .background(
                    color = StratumSupersetGroupColors.backgroundFor(groupId),
                    shape = StratumThemeAccessor.shapes.chip,
                )
                .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
