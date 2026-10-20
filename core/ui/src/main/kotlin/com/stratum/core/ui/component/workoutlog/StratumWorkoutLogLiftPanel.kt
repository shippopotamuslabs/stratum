// STR-142 allowlist: dense set-log row rhythm (2.dp) and header inset (20.dp) pinned.
@file:Suppress("ForbiddenRawSpacingLiteralInUi")
package com.stratum.core.ui.component.workoutlog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.StratumEdgeToEdgeCard
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.SupersetGroupChip
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor

private val AddSetIconSize = 16.dp

/** Horizontal inset for lift title / superset chip (table uses [WorkoutLogTableHorizontalPadding]). */
private val WorkoutLogHeaderHorizontalPadding = 16.dp

/**
 * Raised panel for one lift's set-log table. Optional trailing header actions (e.g. ⋮ menu),
 * optional add-set control under the set-number column, and per-set row actions from the caller.
 *
 * Uses [StratumEdgeToEdgeCard] so the panel sits flush with the screen edges on live workout
 * and history edit lists.
 */
@Composable
fun StratumWorkoutLogLiftPanel(
    lift: WorkoutLogLiftUi,
    setRowActions: (WorkoutLogSetUi) -> WorkoutLogSetRowActions,
    modifier: Modifier = Modifier,
    headerTrailing: (@Composable () -> Unit)? = null,
    /** Optional affordance rendered under the lift title (e.g. live "Adjust Equipment" chip). */
    headerUnderTitle: (@Composable () -> Unit)? = null,
    onAddSet: (() -> Unit)? = null,
    addSetContentDescription: String? = null,
    onWorkoutPinnedNoteClick: (() -> Unit)? = null,
    onLiftPinnedNoteClick: (() -> Unit)? = null,
) {
    val showTrailingColumn = lift.sets.any { it.showCheckbox }

    StratumEdgeToEdgeCard(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = WorkoutLogHeaderHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StratumText(
                text = lift.liftName,
                style = StratumThemeAccessor.typography.titleMedium,
                modifier = Modifier.weight(1f).padding(end = 20.dp),
            )
            headerTrailing?.invoke()
        }

        headerUnderTitle?.invoke()

        PinnedLiftNotesSlot(
            workoutNotes = lift.workoutPinnedNotes,
            liftNotes = lift.liftPinnedNotes,
            onWorkoutNoteClick = onWorkoutPinnedNoteClick,
            onLiftNoteClick = onLiftPinnedNoteClick,
        )

        lift.supersetGroupId?.let { groupId ->
            SupersetGroupChip(
                groupId = groupId,
                modifier =
                    Modifier
                        .padding(vertical = 2.dp)
                        .padding(horizontal = WorkoutLogHeaderHorizontalPadding),
            )
        }

        Spacer(modifier = Modifier.height(StratumSpacing.s4))

        StratumWorkoutLogTableHeader(
            showWeightColumn = lift.showWeightColumn,
            weightHeaderLabel = lift.weightHeaderLabel,
            showTrailingColumn = showTrailingColumn,
        )

        // Inset subtle rule under the column headers — matches Edit Workout set table chrome
        // (lighter grey, not full-bleed edge-to-edge).
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = WorkoutLogTableHorizontalPadding)
                    .height(StratumThemeAccessor.shapes.borderWidth)
                    .background(StratumThemeAccessor.colors.borderSubtle),
        )

        lift.sets.forEach { set ->
            StratumWorkoutLogSetRow(
                set = set,
                actions = setRowActions(set),
            )
        }

        if (onAddSet != null) {
            WorkoutLogAddSetRow(
                showWeightColumn = lift.showWeightColumn,
                showTrailingColumn = showTrailingColumn,
                onAddSet = onAddSet,
                contentDescription = addSetContentDescription ?: "Add set",
            )
        }
    }
}

@Composable
private fun PinnedLiftNotesSlot(
    workoutNotes: List<String>,
    liftNotes: List<String>,
    onWorkoutNoteClick: (() -> Unit)?,
    onLiftNoteClick: (() -> Unit)?,
) {
    val workout = workoutNotes.map { it.trim() }.filter { it.isNotEmpty() }
    val catalog = liftNotes.map { it.trim() }.filter { it.isNotEmpty() }
    if (workout.isEmpty() && catalog.isEmpty()) return
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = WorkoutLogHeaderHorizontalPadding)
                .padding(top = StratumSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s4),
    ) {
        workout.forEach { body ->
            PinnedLiftNoteRow(
                caption = stringResource(CoreUiR.string.lift_notes_scope_workout),
                body = body,
                onClick = onWorkoutNoteClick,
            )
        }
        catalog.forEach { body ->
            PinnedLiftNoteRow(
                caption = stringResource(CoreUiR.string.lift_notes_scope_lift),
                body = body,
                onClick = onLiftNoteClick,
            )
        }
    }
}

@Composable
private fun PinnedLiftNoteRow(
    caption: String,
    body: String,
    onClick: (() -> Unit)?,
) {
    val rowModifier =
        if (onClick != null) {
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
        } else {
            Modifier.fillMaxWidth()
        }
    Row(
        modifier = rowModifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
    ) {
        Icon(
            painter = painterResource(CoreUiR.drawable.ic_push_pin),
            contentDescription = null,
            tint = StratumThemeAccessor.colors.textMuted,
            modifier = Modifier.size(14.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            StratumText(
                text = caption,
                style = StratumThemeAccessor.typography.sectionLabel,
                muted = true,
            )
            StratumText(
                text = body,
                style = StratumThemeAccessor.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Circular + control aligned under the set-number column (same affordance as template
 * Edit Workout's add-set row).
 */
@Composable
private fun WorkoutLogAddSetRow(
    showWeightColumn: Boolean,
    showTrailingColumn: Boolean,
    onAddSet: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val weights = workoutLogColumnWeights(showWeightColumn, showTrailingColumn)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = WorkoutLogTableHorizontalPadding, vertical = StratumSpacing.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.weight(weights.label),
            contentAlignment = Alignment.CenterStart,
        ) {
            WorkoutLogSetColumnBadge(onClick = onAddSet) {
                Icon(
                    painter = painterResource(CoreUiR.drawable.ic_add),
                    contentDescription = contentDescription,
                    tint = StratumThemeAccessor.colors.textMuted,
                    modifier = Modifier.size(AddSetIconSize),
                )
            }
        }
        Spacer(modifier = Modifier.weight(weights.prev))
        if (showWeightColumn) {
            Spacer(modifier = Modifier.weight(weights.weight))
        }
        Spacer(modifier = Modifier.weight(weights.reps))
        if (showTrailingColumn) {
            Spacer(modifier = Modifier.weight(weights.trailing))
        }
    }
}
