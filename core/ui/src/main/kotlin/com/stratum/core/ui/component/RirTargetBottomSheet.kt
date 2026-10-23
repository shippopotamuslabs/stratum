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
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor

private const val RIR_SHEET_ANIMATION_MS = 220
const val SIX_PLUS_RIR_GOAL = 6

@Composable
fun RirTargetBottomSheet(
    selectedRirGoal: Int,
    onRirSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    StratumBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        RirTargetSheetContent(
            selectedRirGoal = selectedRirGoal,
            onRirSelected = onRirSelected,
            onDismiss = onDismiss,
        )
    }
}

/**
 * RIR target sheet body without the dialog window. Screenshot tests cannot capture
 * dialog windows (an open sheet blanks the golden), so goldens pin this content inline
 * (see docs/screenshot-tests.md).
 */
@Composable
fun RirTargetSheetContent(
    selectedRirGoal: Int,
    onRirSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var showInfo by rememberSaveable { mutableStateOf(false) }

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

@Composable
private fun RirTargetPickerContent(
    selectedRirGoal: Int,
    onInfoClick: () -> Unit,
    onRirSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s16),
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
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s16),
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
    // §12.5: 48dp target on the outer box, the sized circle stays the visual;
    // the ripple is drawn clipped to the circle via the shared source.
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier =
            Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                // Keyboard RIR row must not steal focus from the active numeric caret.
                .focusProperties { canFocus = false }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(background)
                    .indication(interactionSource, ripple())
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
                    ),
            contentAlignment = Alignment.Center,
        ) {
            StratumNumber(
                text = label,
                style = labelStyle,
            )
        }
    }
}

private val RirOptionDefaultSize = 40.dp

/**
 * Round RIR badge carrying the §12.5 split: a 48dp touch target outside, the
 * [size] circle as the visual, ripple clipped to the circle. For the set-log
 * corner placement, where a 48dp target would swallow the host cell, use
 * [RirCornerBadge] instead.
 */
@Composable
fun RirTargetBadge(
    rirGoal: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = RirTargetBadgeDefaultSize,
) {
    // The outer box is always present so enabled and disabled badges keep
    // the same footprint.
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier =
            modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .then(
                    if (enabled) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    }
                ),
        contentAlignment = Alignment.Center,
    ) {
        RirBadgeCircle(
            rirGoal = rirGoal,
            size = size,
            enabled = enabled,
            interactionSource = interactionSource,
        )
    }
}

/**
 * The set-log corner badge (STR-17 Choice D, STR-180): the same circle as
 * [RirTargetBadge] at a smaller size, deliberately WITHOUT the 48dp touch
 * expansion — it is centered on the reps cell's top-right corner, where an
 * expanded box pulled the visual inside the cell and covered the cell's
 * top-right with the badge's tap area (tapping the field to edit reps opened
 * the RIR sheet). Layout and tap area are the circle itself.
 *
 * Anchoring is part of the component: the caller aligns it to the corner
 * (e.g. `Modifier.align(Alignment.TopEnd)` inside a Box) and the badge
 * offsets itself by half its size, so its center lands exactly on the
 * corner and it floats half-out of the cell.
 */
@Composable
fun RirCornerBadge(
    rirGoal: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = RirCornerBadgeSize,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier =
            modifier
                .offset(x = size / 2, y = -size / 2)
                .size(size)
                .then(
                    if (enabled) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    }
                ),
        contentAlignment = Alignment.Center,
    ) {
        RirBadgeCircle(
            rirGoal = rirGoal,
            size = size,
            enabled = enabled,
            interactionSource = interactionSource,
        )
    }
}

/** The RIR circle itself: traffic-light fill, label, and (when enabled) the ripple. */
@Composable
private fun RirBadgeCircle(
    rirGoal: Int,
    size: Dp,
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
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
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(background)
                .then(
                    if (enabled) {
                        Modifier.indication(interactionSource, ripple())
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

/** Diameter of [RirCornerBadge] on the set-log reps cell. */
val RirCornerBadgeSize = 22.dp

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
