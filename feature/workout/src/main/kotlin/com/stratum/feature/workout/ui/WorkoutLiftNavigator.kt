package com.stratum.feature.workout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.LiftThumbnail
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.workout.R
import com.stratum.feature.workout.isLiftComplete

/** 56.dp + 25% for denser, more tappable tabs. */
internal val LiftNavigatorTabSize = 70.dp
internal val LiftNavigatorTabSpacing = 4.dp
internal val LiftNavigatorUnderlineGap = 6.dp
internal val LiftNavigatorUnderlineHeight = 3.dp
internal val LiftNavigatorRowHeight =
    LiftNavigatorTabSize + LiftNavigatorUnderlineGap + LiftNavigatorUnderlineHeight
private const val LIFT_NAVIGATOR_INACTIVE_ALPHA = 0.4f

/**
 * Sticky horizontal strip of lift tabs (image or initials) + add-lift control.
 * Consecutive supersets share one continuous underline; only [primaryLiftId] is full-opacity.
 */
@Composable
internal fun LiftNavigatorRow(
    lifts: List<RenderedLiftUi>,
    primaryLiftId: String?,
    underlinedLiftIds: Set<String>,
    onLiftClick: (workoutLiftId: String) -> Unit,
    onAddLift: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val addLiftCd = stringResource(R.string.workout_screen_add_lift)
    val groups = remember(lifts) { groupLiftsForNavigator(lifts) }
    // Fixed row height so the underline slot is never clipped by LazyRow measurement.
    LazyRow(
        modifier = modifier.height(LiftNavigatorRowHeight),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(LiftNavigatorTabSpacing),
        verticalAlignment = Alignment.Top,
    ) {
        items(groups, key = { group -> group.joinToString("-") { it.workoutLiftId } }) { group ->
            LiftNavigatorGroup(
                lifts = group,
                primaryLiftId = primaryLiftId,
                showUnderline = group.any { it.workoutLiftId in underlinedLiftIds },
                onLiftClick = onLiftClick,
            )
        }
        item(key = "add-lift") {
            LiftNavigatorAddTab(
                contentDescription = addLiftCd,
                onClick = onAddLift,
            )
        }
    }
}

@Composable
private fun LiftNavigatorGroup(
    lifts: List<RenderedLiftUi>,
    primaryLiftId: String?,
    showUnderline: Boolean,
    onLiftClick: (workoutLiftId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    // Explicit width so the continuous underline has a real span (fillMaxWidth alone can
    // collapse when only Align-positioned children size the Box).
    val groupWidth =
        LiftNavigatorTabSize * lifts.size +
            LiftNavigatorTabSpacing * (lifts.size - 1).coerceAtLeast(0)
    Box(
        modifier = modifier
            .width(groupWidth)
            .height(LiftNavigatorRowHeight),
    ) {
        Row(
            modifier = Modifier.align(Alignment.TopStart),
            horizontalArrangement = Arrangement.spacedBy(LiftNavigatorTabSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            lifts.forEach { lift ->
                LiftNavigatorTab(
                    lift = lift,
                    highlighted = lift.workoutLiftId == primaryLiftId,
                    isComplete = isLiftComplete(lift),
                    onClick = { onLiftClick(lift.workoutLiftId) },
                )
            }
        }
        if (showUnderline) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(LiftNavigatorUnderlineHeight)
                    .clip(RoundedCornerShape(1.5.dp))
                    // High-contrast bar (sample apps use a light line on dark chrome).
                    .background(colors.textPrimary),
            )
        }
    }
}

@Composable
private fun LiftNavigatorTab(
    lift: RenderedLiftUi,
    highlighted: Boolean,
    isComplete: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val alpha = if (highlighted) 1f else LIFT_NAVIGATOR_INACTIVE_ALPHA
    // Prefer catalog image; otherwise two-letter initials (BB / SQ) for denser tabs.
    // Borderless thumbnails — selection is underline + opacity only.
    val hasImage = !lift.imageUrl.isNullOrBlank()
    val completeCd = stringResource(R.string.workout_screen_lift_complete_cd)
    Box(
        modifier = modifier
            .size(LiftNavigatorTabSize)
            .alpha(alpha)
            .clip(shapes.chip)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (hasImage) {
            LiftThumbnail(
                name = lift.liftName,
                imageUrl = lift.imageUrl,
                size = LiftNavigatorTabSize,
                bordered = false,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.surfaceSunken, shapes.chip),
                contentAlignment = Alignment.Center,
            ) {
                StratumText(
                    text = lift.navigatorInitials.ifBlank { "?" },
                    style = StratumThemeAccessor.typography.titleMedium,
                    muted = !highlighted,
                )
            }
        }
        if (isComplete) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(colors.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(CoreUiR.drawable.ic_check),
                    contentDescription = completeCd,
                    tint = colors.onAccent,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun LiftNavigatorAddTab(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    Box(
        modifier = modifier
            .height(LiftNavigatorRowHeight)
            .width(LiftNavigatorTabSize),
    ) {
        Box(
            modifier = Modifier
                .size(LiftNavigatorTabSize)
                .align(Alignment.TopCenter)
                .clip(shapes.chip)
                .background(colors.surfaceRaised)
                .border(
                    width = shapes.borderWidth,
                    color = colors.borderSubtle,
                    shape = shapes.chip,
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(CoreUiR.drawable.ic_add),
                contentDescription = contentDescription,
                tint = colors.textPrimary,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/**
 * Consecutive lifts that share a non-null [RenderedLiftUi.supersetGroupId] form one tab group
 * so a single underline can span the set.
 */
internal fun groupLiftsForNavigator(lifts: List<RenderedLiftUi>): List<List<RenderedLiftUi>> {
    if (lifts.isEmpty()) return emptyList()
    val groups = mutableListOf<MutableList<RenderedLiftUi>>()
    for (lift in lifts) {
        val current = groups.lastOrNull()
        val currentGroupId = current?.firstOrNull()?.supersetGroupId
        if (
            current != null &&
            !lift.supersetGroupId.isNullOrBlank() &&
            lift.supersetGroupId == currentGroupId
        ) {
            current += lift
        } else {
            groups += mutableListOf(lift)
        }
    }
    return groups
}
