package com.stratum.core.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.theme.StratumRirTargetColors
import com.stratum.core.ui.theme.StratumThemeAccessor

private const val RIR_SHEET_ANIMATION_MS = 220
const val SIX_PLUS_RIR_GOAL = 6

@Composable
fun RirTargetBottomSheet(
    selectedRirGoal: Int,
    onRirSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var showInfo by rememberSaveable { mutableStateOf(false) }

    StratumBottomSheet(
        onDismissRequest = {
            showInfo = false
            onDismiss()
        },
    ) {
        AnimatedContent(
            targetState = showInfo,
            transitionSpec = {
                (
                    fadeIn(animationSpec = tween(RIR_SHEET_ANIMATION_MS)) +
                        scaleIn(initialScale = 0.92f, animationSpec = tween(RIR_SHEET_ANIMATION_MS))
                    ).togetherWith(
                    fadeOut(animationSpec = tween(RIR_SHEET_ANIMATION_MS)) +
                        scaleOut(targetScale = 0.92f, animationSpec = tween(RIR_SHEET_ANIMATION_MS)),
                ) using SizeTransform(clip = false)
            },
            label = "rir_target_sheet_content",
        ) { infoVisible ->
            if (infoVisible) {
                RirTargetInfoContent(onCloseInfo = { showInfo = false })
            } else {
                RirTargetPickerContent(
                    selectedRirGoal = selectedRirGoal,
                    onInfoClick = { showInfo = true },
                    onRirSelected = onRirSelected,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun RirTargetPickerContent(
    selectedRirGoal: Int,
    onInfoClick: () -> Unit,
    onRirSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RirSheetTitleRow(
            onInfoClick = onInfoClick,
        )
        RirTargetOptionRow(
            selectedRirGoal = selectedRirGoal,
            onRirSelected = { rir ->
                onRirSelected(rir)
                onDismiss()
            },
        )
    }
}

/**
 * Horizontal row of RIR chips (0–5, 6+). Selected chip draws a textPrimary ring.
 * Used by the standalone RIR sheet and the set-log keyboard.
 */
@Composable
fun RirTargetOptionRow(
    selectedRirGoal: Int,
    onRirSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    chipSize: Dp = RirOptionDefaultSize,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RIR_TARGET_OPTIONS.forEach { option ->
            RirSheetOption(
                label = option.label,
                rirGoal = option.value,
                isSelected = selectedRirGoal == option.value,
                onClick = { onRirSelected(option.value) },
                size = chipSize,
            )
        }
    }
}

@Composable
private fun RirSheetTitleRow(
    onInfoClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StratumText(
            text = stringResource(CoreUiR.string.edit_workout_rir_sheet_title),
            style = StratumThemeAccessor.typography.titleLarge,
        )
        StratumIconButton(
            onClick = onInfoClick,
            containerColor = Color.Transparent,
            contentColor = StratumThemeAccessor.colors.textMuted,
        ) {
            Icon(
                painter = painterResource(CoreUiR.drawable.ic_info),
                contentDescription = stringResource(CoreUiR.string.edit_workout_rir_info_content_description),
                tint = StratumThemeAccessor.colors.textMuted,
            )
        }
    }
}

@Composable
private fun RirTargetInfoContent(onCloseInfo: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StratumIconButton(onClick = onCloseInfo) {
                Icon(
                    painter = painterResource(CoreUiR.drawable.ic_close),
                    contentDescription = stringResource(CoreUiR.string.edit_workout_rir_info_close),
                    tint = StratumThemeAccessor.colors.textPrimary,
                )
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                StratumText(
                    text = stringResource(CoreUiR.string.edit_workout_rir_sheet_title),
                    style =
                        StratumThemeAccessor.typography.titleLarge.copy(
                            textAlign = TextAlign.Center,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 1,
                )
            }
            Box(modifier = Modifier.size(48.dp))
        }
        StratumText(
            text = stringResource(CoreUiR.string.edit_workout_rir_info_body),
            style = StratumThemeAccessor.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RirSheetOption(
    label: String,
    rirGoal: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    size: Dp = RirOptionDefaultSize,
) {
    val background = StratumRirTargetColors.backgroundFor(rirGoal)
    val contentColor = StratumRirTargetColors.contentColor
    val labelStyle =
        if (size < RirOptionDefaultSize) {
            StratumThemeAccessor.typography.sectionLabel.copy(
                color = contentColor,
                fontSize = CompactRirBadgeFontSize,
                lineHeight = CompactRirBadgeLineHeight,
            )
        } else {
            StratumThemeAccessor.typography.sectionLabel.copy(
                color = contentColor,
            )
        }
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(background)
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = 2.dp,
                            color = StratumThemeAccessor.colors.textPrimary,
                            shape = CircleShape,
                        )
                    } else {
                        Modifier
                    },
                )
                // Keyboard RIR row must not steal focus from the active numeric caret.
                .focusProperties { canFocus = false }
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        StratumNumber(
            text = label,
            style = labelStyle,
        )
    }
}

private val RirOptionDefaultSize = 40.dp

@Composable
fun RirTargetBadge(
    rirGoal: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = RirTargetBadgeDefaultSize,
) {
    val label = formatRirLabel(rirGoal)
    val background = StratumRirTargetColors.backgroundFor(rirGoal)
    val contentColor = StratumRirTargetColors.contentColor
    val labelStyle =
        if (size < RirTargetBadgeDefaultSize) {
            StratumThemeAccessor.typography.sectionLabel.copy(
                color = contentColor,
                fontSize = CompactRirBadgeFontSize,
                lineHeight = CompactRirBadgeLineHeight,
            )
        } else {
            StratumThemeAccessor.typography.sectionLabel.copy(
                color = contentColor,
            )
        }
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(background)
                .then(
                    if (enabled) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    }
                ),
        contentAlignment = Alignment.Center,
    ) {
        StratumNumber(
            text = label,
            style = labelStyle,
        )
    }
}

/** Default diameter for [RirTargetBadge] (workout logging). Compact history uses a smaller size. */
val RirTargetBadgeDefaultSize = 32.dp

private val CompactRirBadgeFontSize = 11.sp
private val CompactRirBadgeLineHeight = 12.sp

private data class RirTargetOption(
    val value: Int,
    val label: String,
)

private val RIR_TARGET_OPTIONS =
    listOf(
        RirTargetOption(0, "0"),
        RirTargetOption(1, "1"),
        RirTargetOption(2, "2"),
        RirTargetOption(3, "3"),
        RirTargetOption(4, "4"),
        RirTargetOption(5, "5"),
        RirTargetOption(SIX_PLUS_RIR_GOAL, "6+"),
    )

fun formatRirLabel(rirGoal: Int): String =
    if (rirGoal >= SIX_PLUS_RIR_GOAL) {
        "6+"
    } else {
        rirGoal.coerceAtLeast(0).toString()
    }
