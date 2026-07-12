package com.stratum.feature.program.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.RirTargetBadge
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.component.StratumSwipeToRevealRow
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.workoutlog.DockCellRevealEffect
import com.stratum.core.ui.component.workoutlog.RepRangeKeyboardField
import com.stratum.core.ui.component.workoutlog.StratumInlineNumericCell
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.domain.program.ProgressionScheme
import com.stratum.feature.program.R
import com.stratum.feature.program.showsRepRange

private val SetTableRowHeight = 48.dp

@Composable
internal fun EditWorkoutSetTable(
    workoutLiftId: String,
    sets: List<EditSetState>,
    progressionScheme: ProgressionScheme,
    onAddSet: () -> Unit,
    onRemoveSet: (setId: String) -> Unit,
    onRepBandChange: (setId: String, repMin: Int, repMax: Int) -> Unit,
    onSetRestSeconds: (setId: String, restSeconds: Int) -> Unit,
    onSetRestSecondsForAllSetsOnLift: (workoutLiftId: String, restSeconds: Int) -> Unit,
    onRirClick: (EditSetState) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        // Match live workout log table inset; panel itself is edge-to-edge.
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EditWorkoutSetTableHeader()
            SetTableHeaderDivider()
        }
        sets.forEach { set ->
            EditWorkoutSetTableRow(
                workoutLiftId = workoutLiftId,
                set = set,
                showRepRange = showsRepRange(progressionScheme, set),
                canRemove = sets.size > 1,
                onRemoveSet = { onRemoveSet(set.setId) },
                onRepBandChange = { min, max -> onRepBandChange(set.setId, min, max) },
                onSetRestSeconds = onSetRestSeconds,
                onSetRestSecondsForAllSetsOnLift = onSetRestSecondsForAllSetsOnLift,
                onRirClick = { onRirClick(set) },
            )
        }
        EditWorkoutAddSetRow(onAddSet = onAddSet)
    }
}

@Composable
private fun EditWorkoutSetTableHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SetTableHeaderCell(
            text = stringResource(R.string.edit_workout_column_set),
            modifier = Modifier.weight(SET_COLUMN_WEIGHT),
            textAlign = TextAlign.Start,
            contentAlignment = Alignment.CenterStart,
        )
        SetTableHeaderCell(
            text = stringResource(R.string.edit_workout_column_rep_range),
            modifier = Modifier.weight(REP_RANGE_COLUMN_WEIGHT),
        )
        SetTableHeaderCell(
            text = stringResource(R.string.edit_workout_column_rest),
            modifier = Modifier.weight(REST_COLUMN_WEIGHT),
        )
        SetTableHeaderCell(
            text = stringResource(R.string.edit_workout_column_rir),
            modifier = Modifier.weight(RIR_COLUMN_WEIGHT),
        )
    }
}

@Composable
private fun SetTableHeaderDivider() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(StratumThemeAccessor.shapes.borderWidth)
                .background(StratumThemeAccessor.colors.borderSubtle),
    )
}

@Composable
private fun SetTableHeaderCell(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Center,
    contentAlignment: Alignment = Alignment.Center,
) {
    Box(
        modifier = modifier,
        contentAlignment = contentAlignment,
    ) {
        StratumText(
            text = text,
            style =
                StratumThemeAccessor.typography.sectionLabel.copy(
                    textAlign = textAlign,
                ),
            muted = true,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EditWorkoutSetTableRow(
    workoutLiftId: String,
    set: EditSetState,
    showRepRange: Boolean,
    canRemove: Boolean,
    onRemoveSet: () -> Unit,
    onRepBandChange: (repMin: Int, repMax: Int) -> Unit,
    onSetRestSeconds: (setId: String, restSeconds: Int) -> Unit,
    onSetRestSecondsForAllSetsOnLift: (workoutLiftId: String, restSeconds: Int) -> Unit,
    onRirClick: () -> Unit,
) {
    val committedMinText = if (set.repMin > 0) set.repMin.toString() else ""
    val committedMaxText = if (set.repMax > 0) set.repMax.toString() else ""

    StratumSwipeToRevealRow(
        onActionClick = onRemoveSet,
        actionContentDescription = stringResource(R.string.edit_workout_remove_set),
        enabled = canRemove,
        contentBackgroundColor = StratumThemeAccessor.colors.surfaceRaised,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(SetTableRowHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.weight(SET_COLUMN_WEIGHT),
                contentAlignment = Alignment.CenterStart,
            ) {
                SetNumberBadge(number = set.setNumber)
            }
            // Linear: every set shows the shared band. Double: top set only (RIR-only backoffs).
            if (showRepRange) {
                RepRangeCell(
                    setId = set.setId,
                    committedMinText = committedMinText,
                    committedMaxText = committedMaxText,
                    onRepBandChange = onRepBandChange,
                    modifier = Modifier.weight(REP_RANGE_COLUMN_WEIGHT),
                )
            } else {
                BackoffRepRangeCell(
                    modifier = Modifier.weight(REP_RANGE_COLUMN_WEIGHT),
                )
            }
            RestTimeCell(
                setId = set.setId,
                workoutLiftId = workoutLiftId,
                restSeconds = set.restSeconds,
                onSetRestSeconds = onSetRestSeconds,
                onSetRestSecondsForAllSetsOnLift = onSetRestSecondsForAllSetsOnLift,
                modifier = Modifier.weight(REST_COLUMN_WEIGHT),
            )
            Box(
                modifier = Modifier.weight(RIR_COLUMN_WEIGHT),
                contentAlignment = Alignment.Center,
            ) {
                RirTargetBadge(
                    rirGoal = set.rirGoal,
                    onClick = onRirClick,
                )
            }
        }
    }
}

@Composable
private fun EditWorkoutAddSetRow(onAddSet: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(SetTableRowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.weight(SET_COLUMN_WEIGHT),
            contentAlignment = Alignment.CenterStart,
        ) {
            SetColumnCircleBadge(onClick = onAddSet) {
                Icon(
                    painter = painterResource(CoreUiR.drawable.ic_add),
                    contentDescription = stringResource(R.string.edit_workout_add_set),
                    tint = StratumThemeAccessor.colors.textMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Box(modifier = Modifier.weight(REP_RANGE_COLUMN_WEIGHT))
        Box(modifier = Modifier.weight(REST_COLUMN_WEIGHT))
        Box(modifier = Modifier.weight(RIR_COLUMN_WEIGHT))
    }
}

@Composable
private fun SetNumberBadge(number: Int) {
    SetColumnCircleBadge {
        StratumNumber(
            text = number.toString(),
            style = StratumThemeAccessor.typography.sectionLabel,
        )
    }
}

@Composable
private fun SetColumnCircleBadge(
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(StratumThemeAccessor.colors.surfaceInset)
                .border(
                    width = StratumThemeAccessor.shapes.borderWidth,
                    color = StratumThemeAccessor.colors.borderSubtle,
                    shape = CircleShape,
                )
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun RepRangeCell(
    setId: String,
    committedMinText: String,
    committedMaxText: String,
    onRepBandChange: (repMin: Int, repMax: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dock = LocalEditWorkoutDock.current
    val sessionOpen = dock?.isRepSessionFor(setId) == true
    val minActive = dock?.isRepFieldActive(setId, RepRangeKeyboardField.Min) == true
    val maxActive = dock?.isRepFieldActive(setId, RepRangeKeyboardField.Max) == true
    val validation = if (sessionOpen) dock?.repRangeValidation else null
    // While editing, show host drafts (not applyBand-clamped storage).
    val displayMin = if (sessionOpen) dock?.minText.orEmpty() else committedMinText
    val displayMax = if (sessionOpen) dock?.maxText.orEmpty() else committedMaxText

    val openField: (RepRangeKeyboardField) -> Unit = { field ->
        // Same set: tab field only (no commit). Other set / new session: host commits or blocks.
        if (dock?.isRepSessionFor(setId) == true) {
            dock.setRepField(field)
        } else {
            dock?.openRepRange(
                setId = setId,
                field = field,
                minText = committedMinText,
                maxText = committedMaxText,
                onCommitBand = onRepBandChange,
            )
        }
    }

    Row(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StratumInlineNumericCell(
            value = displayMin,
            placeholder = "",
            enabled = true,
            isActive = minActive,
            isError = validation?.minInvalid == true,
            onActivate = { openField(RepRangeKeyboardField.Min) },
            modifier = Modifier.weight(1f),
        )
        StratumText(
            text = stringResource(R.string.edit_workout_rep_range_to),
            style = StratumThemeAccessor.typography.sectionLabel,
            muted = true,
        )
        StratumInlineNumericCell(
            value = displayMax,
            placeholder = "",
            enabled = true,
            isActive = maxActive,
            isError = validation?.maxInvalid == true,
            onActivate = { openField(RepRangeKeyboardField.Max) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BackoffRepRangeCell(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Backoff sets are RIR-only; the rep range is not prescribed (see Program.kt and
        // BACKOFF_REP_FLOOR). Shown as a muted dash so the column stays aligned with top sets.
        StratumText(
            text = stringResource(R.string.edit_workout_rep_range_backoff_placeholder),
            style = StratumThemeAccessor.typography.sectionLabel,
            muted = true,
        )
    }
}

@Composable
private fun RestTimeCell(
    setId: String,
    workoutLiftId: String,
    restSeconds: Int,
    onSetRestSeconds: (setId: String, restSeconds: Int) -> Unit,
    onSetRestSecondsForAllSetsOnLift: (workoutLiftId: String, restSeconds: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dock = LocalEditWorkoutDock.current
    val isActive = dock?.isRestActive(setId) == true
    var cellBoundsInWindow by remember { mutableStateOf(Rect.Zero) }

    DockCellRevealEffect(
        isActive = isActive,
        cellBoundsInWindow = cellBoundsInWindow,
    )

    Box(
        modifier =
            modifier
                .onGloballyPositioned { coords ->
                    cellBoundsInWindow = coords.boundsInWindow()
                }
                .padding(horizontal = 4.dp)
                .height(36.dp)
                .clip(StratumThemeAccessor.shapes.chip)
                .background(StratumThemeAccessor.colors.surfaceInset)
                .border(
                    width = StratumThemeAccessor.shapes.borderWidth,
                    color =
                        if (isActive) {
                            StratumThemeAccessor.colors.accent
                        } else {
                            StratumThemeAccessor.colors.borderSubtle
                        },
                    shape = StratumThemeAccessor.shapes.chip,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        dock?.openRest(
                            setId = setId,
                            restSeconds = restSeconds,
                            onApplyToSet = { seconds -> onSetRestSeconds(setId, seconds) },
                            onApplyToAllSets = { seconds ->
                                onSetRestSecondsForAllSetsOnLift(workoutLiftId, seconds)
                            },
                        )
                    },
                )
                .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        StratumNumber(
            text = formatRestDisplay(restSeconds),
            style = StratumThemeAccessor.typography.sectionLabel,
        )
    }
}

internal fun formatRestDisplay(restSeconds: Int): String {
    val total = restSeconds.coerceAtLeast(0)
    val minutes = total / 60
    val seconds = total % 60
    return "%d:%02d".format(minutes, seconds)
}

private const val SET_COLUMN_WEIGHT = 0.16f
private const val REP_RANGE_COLUMN_WEIGHT = 0.44f
private const val REST_COLUMN_WEIGHT = 0.24f
private const val RIR_COLUMN_WEIGHT = 0.16f
