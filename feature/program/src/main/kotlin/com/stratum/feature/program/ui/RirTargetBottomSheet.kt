package com.stratum.feature.program.ui

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumRirTargetColors
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.program.R

private const val RIR_SHEET_ANIMATION_MS = 220

@Composable
internal fun RirTargetBottomSheet(
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RIR_TARGET_OPTIONS.forEach { option ->
                val isSelected = selectedRirGoal == option.value
                RirSheetOption(
                    label = option.label,
                    rirGoal = option.value,
                    isSelected = isSelected,
                    onClick = {
                        onRirSelected(option.value)
                        onDismiss()
                    },
                )
            }
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
            text = stringResource(R.string.edit_workout_rir_sheet_title),
            style = StratumThemeAccessor.typography.titleLarge,
        )
        StratumIconButton(
            onClick = onInfoClick,
            containerColor = Color.Transparent,
            contentColor = StratumThemeAccessor.colors.textMuted,
        ) {
            Icon(
                painter = painterResource(CoreUiR.drawable.ic_info),
                contentDescription = stringResource(R.string.edit_workout_rir_info_content_description),
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
                    contentDescription = stringResource(R.string.edit_workout_rir_info_close),
                    tint = StratumThemeAccessor.colors.textPrimary,
                )
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                StratumText(
                    text = stringResource(R.string.edit_workout_rir_sheet_title),
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
            text = stringResource(R.string.edit_workout_rir_info_body),
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
) {
    val background = StratumRirTargetColors.backgroundFor(rirGoal)
    val contentColor = StratumRirTargetColors.contentColor
    Box(
        modifier =
            Modifier
                .size(40.dp)
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
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        StratumNumber(
            text = label,
            style =
                StratumThemeAccessor.typography.sectionLabel.copy(
                    color = contentColor,
                ),
        )
    }
}

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

internal fun formatRirLabel(rirGoal: Int): String =
    if (rirGoal >= SIX_PLUS_RIR_GOAL) {
        "6+"
    } else {
        rirGoal.coerceAtLeast(0).toString()
    }
