package com.stratum.core.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor

/** Presentation row for one day under a library program (workout or rest). */
data class ProgramLibraryDayState(
    val id: String,
    val name: String,
    val isRest: Boolean,
    /** Comma-separated lift names; empty for rest days. */
    val liftNamesSummary: String = "",
    /** Muscle group display labels for badge FlowRow. */
    val muscleLabels: List<String> = emptyList(),
    /** Null for rest days (not navigable to edit workout). */
    val workoutId: String? = null,
)

/** Presentation model for a single expandable program card in the program library. */
data class ProgramLibraryCardState(
    val programId: String,
    val name: String,
    /** Training workout count (excludes rest days); UI formats the subtitle string. */
    val workoutCount: Int,
    val isActive: Boolean,
    val days: List<ProgramLibraryDayState>,
)

/**
 * Expandable program library card: header with optional Active badge, workout count, chevron;
 * expanded body lists workouts (lift summary + muscle badges) and rest days.
 *
 * Full-width [borderSubtle] dividers separate the header from the day list and each day row.
 * Workout rows show a trailing chevron; rest days do not (not navigable).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StratumProgramLibraryCard(
    state: ProgramLibraryCardState,
    expanded: Boolean,
    activeLabel: String,
    workoutCountLabel: String,
    onExpandedChange: (Boolean) -> Unit,
    onProgramClick: () -> Unit,
    onActivateClick: () -> Unit,
    onWorkoutClick: (workoutId: String) -> Unit,
    expandContentDescription: String,
    collapseContentDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "programLibraryChevronRotation",
    )
    val borderColor = if (state.isActive) colors.accent else colors.borderSubtle
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(color = colors.surfaceRaised, shape = shapes.panel)
                .border(width = shapes.borderWidth, color = borderColor, shape = shapes.panel)
                .clip(shapes.panel),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = StratumSpacing.s16, vertical = StratumSpacing.s16),
            horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.isActive) {
                StratumActiveBadge(
                    label = activeLabel,
                    active = true,
                )
            } else {
                StratumActiveBadge(
                    label = activeLabel,
                    active = false,
                    onClick = onActivateClick,
                )
            }
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .clickable(role = Role.Button, onClick = onProgramClick),
                verticalArrangement = Arrangement.spacedBy(StratumSpacing.s4),
            ) {
                StratumText(
                    text = state.name,
                    style = StratumThemeAccessor.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                StratumText(
                    text = workoutCountLabel,
                    muted = true,
                )
            }
            StratumIconButton(
                onClick = { onExpandedChange(!expanded) },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    painter = painterResource(CoreUiR.drawable.ic_chevron_down),
                    contentDescription =
                        if (expanded) collapseContentDescription else expandContentDescription,
                    modifier =
                        Modifier
                            .size(20.dp)
                            .rotate(chevronRotation),
                    tint = colors.textMuted,
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Divider under header (between header and first day).
                ProgramLibraryDivider()
                state.days.forEach { day ->
                    ProgramLibraryDayRow(
                        day = day,
                        onWorkoutClick = onWorkoutClick,
                    )
                    // Divider under each day row (workouts and rest days).
                    ProgramLibraryDivider()
                }
            }
        }
    }
}

@Composable
private fun ProgramLibraryDivider(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(StratumThemeAccessor.colors.borderSubtle),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgramLibraryDayRow(
    day: ProgramLibraryDayState,
    onWorkoutClick: (workoutId: String) -> Unit,
) {
    val workoutId = day.workoutId
    val isWorkout = !day.isRest && workoutId != null
    val clickable =
        if (isWorkout) {
            Modifier.clickable(role = Role.Button, onClick = { onWorkoutClick(workoutId) })
        } else {
            Modifier
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(clickable)
                .padding(horizontal = StratumSpacing.s16, vertical = StratumSpacing.s12),
        horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(StratumSpacing.s4),
        ) {
            StratumText(
                text = day.name,
                style = StratumThemeAccessor.typography.bodyLarge,
            )
            if (!day.isRest && day.liftNamesSummary.isNotBlank()) {
                StratumText(
                    text = day.liftNamesSummary,
                    muted = true,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (day.muscleLabels.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s4),
                    verticalArrangement = Arrangement.spacedBy(StratumSpacing.s4),
                ) {
                    day.muscleLabels.forEach { label ->
                        MetadataBadge(text = label)
                    }
                }
            }
        }
        if (isWorkout) {
            Icon(
                painter = painterResource(CoreUiR.drawable.ic_chevron_right),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = StratumThemeAccessor.colors.textMuted,
            )
        }
    }
}
