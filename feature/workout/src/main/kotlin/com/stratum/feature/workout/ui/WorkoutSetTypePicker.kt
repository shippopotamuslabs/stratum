package com.stratum.feature.workout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumSetTypeColors
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.workout.R

/**
 * Bottom sheet to pick set type during a live session — same options and chrome as
 * template Edit Workout (S / M / MM / D).
 */
@Composable
internal fun WorkoutSetTypePickerSheet(
    selected: WorkoutSetKind,
    allowedKinds: List<WorkoutSetKind>,
    onSelect: (WorkoutSetKind) -> Unit,
    onDismiss: () -> Unit,
) {
    StratumBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.workout_set_type_sheet_title),
    ) {
        allowedKinds.forEach { kind ->
            SetTypeOptionRow(
                kind = kind,
                selected = kind == selected,
                onClick = { onSelect(kind) },
            )
        }
    }
}

@Composable
private fun SetTypeOptionRow(
    kind: WorkoutSetKind,
    selected: Boolean,
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
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SetTypeLetterBadge(
            kind = kind,
            selected = selected,
            size = SetTypeBadgeSheetSize,
        )
        StratumText(
            text = stringResource(kind.displayNameRes()),
            style = StratumThemeAccessor.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                painter = painterResource(CoreUiR.drawable.ic_check),
                contentDescription = stringResource(R.string.workout_set_type_selected),
                tint = colors.textPrimary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun SetTypeLetterBadge(
    kind: WorkoutSetKind,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    size: Dp = SetTypeBadgeSheetSize,
) {
    val label = kind.chipLabel()
    val background = kind.badgeBackground()
    val contentColor = StratumSetTypeColors.contentColor
    val labelStyle =
        if (label.length > 1) {
            StratumThemeAccessor.typography.sectionLabel.copy(
                color = contentColor,
                fontSize = CompactSetTypeBadgeFontSize,
                lineHeight = CompactSetTypeBadgeLineHeight,
            )
        } else {
            StratumThemeAccessor.typography.sectionLabel.copy(color = contentColor)
        }
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(background)
                .then(
                    if (selected) {
                        Modifier.border(
                            width = 2.dp,
                            color = StratumThemeAccessor.colors.textPrimary,
                            shape = CircleShape,
                        )
                    } else {
                        Modifier
                    },
                )
                .border(
                    width = StratumThemeAccessor.shapes.borderWidth,
                    color = StratumThemeAccessor.colors.borderSubtle,
                    shape = CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        StratumNumber(text = label, style = labelStyle)
    }
}

internal fun WorkoutSetKind.chipLabel(): String =
    when (this) {
        WorkoutSetKind.Standard -> "S"
        WorkoutSetKind.MyoRepFloor -> "M"
        WorkoutSetKind.MyoRepMatch -> "MM"
        WorkoutSetKind.DropSet -> "D"
    }

private fun WorkoutSetKind.badgeBackground(): Color =
    when (this) {
        WorkoutSetKind.Standard -> StratumSetTypeColors.standard
        WorkoutSetKind.MyoRepFloor -> StratumSetTypeColors.myoRep
        WorkoutSetKind.MyoRepMatch -> StratumSetTypeColors.myoRepMatch
        WorkoutSetKind.DropSet -> StratumSetTypeColors.dropSet
    }

private fun WorkoutSetKind.displayNameRes(): Int =
    when (this) {
        WorkoutSetKind.Standard -> R.string.workout_set_type_standard
        WorkoutSetKind.MyoRepFloor -> R.string.workout_set_type_myo_rep
        WorkoutSetKind.MyoRepMatch -> R.string.workout_set_type_myo_rep_match
        WorkoutSetKind.DropSet -> R.string.workout_set_type_drop_set
    }

private val SetTypeBadgeSheetSize = 40.dp
private val CompactSetTypeBadgeFontSize = 11.sp
private val CompactSetTypeBadgeLineHeight = 12.sp
