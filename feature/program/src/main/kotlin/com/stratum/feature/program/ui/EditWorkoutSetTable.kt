package com.stratum.feature.program.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.RirTargetBadge
import com.stratum.core.ui.component.formatRirLabel
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.component.StratumSwipeToRevealRow
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumRirTargetColors
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.domain.program.ProgressionScheme
import com.stratum.feature.program.R
import com.stratum.feature.program.showsRepRange

private val SetTableRowHeight = 48.dp
private val SetTableCellMinWidth = 36.dp

@Composable
internal fun EditWorkoutSetTable(
    sets: List<EditSetState>,
    progressionScheme: ProgressionScheme,
    onAddSet: () -> Unit,
    onRemoveSet: (setId: String) -> Unit,
    onRepMinChange: (setId: String, repMin: Int) -> Unit,
    onRepMaxChange: (setId: String, repMax: Int) -> Unit,
    onRestClick: (EditSetState) -> Unit,
    onRirClick: (EditSetState) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
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
                set = set,
                showRepRange = showsRepRange(progressionScheme, set),
                canRemove = sets.size > 1,
                onRemoveSet = { onRemoveSet(set.setId) },
                onRepMinChange = { value -> onRepMinChange(set.setId, value) },
                onRepMaxChange = { value -> onRepMaxChange(set.setId, value) },
                onRestClick = { onRestClick(set) },
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
    set: EditSetState,
    showRepRange: Boolean,
    canRemove: Boolean,
    onRemoveSet: () -> Unit,
    onRepMinChange: (Int) -> Unit,
    onRepMaxChange: (Int) -> Unit,
    onRestClick: () -> Unit,
    onRirClick: () -> Unit,
) {
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
                    repMin = set.repMin,
                    repMax = set.repMax,
                    onRepMinChange = onRepMinChange,
                    onRepMaxChange = onRepMaxChange,
                    modifier = Modifier.weight(REP_RANGE_COLUMN_WEIGHT),
                )
            } else {
                BackoffRepRangeCell(
                    modifier = Modifier.weight(REP_RANGE_COLUMN_WEIGHT),
                )
            }
            RestTimeCell(
                restSeconds = set.restSeconds,
                onClick = onRestClick,
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
    repMin: Int,
    repMax: Int,
    onRepMinChange: (Int) -> Unit,
    onRepMaxChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InlineNumericCell(
            value = if (repMin > 0) repMin.toString() else "",
            placeholder = "0",
            onCommit = { text ->
                onRepMinChange(text.toIntOrNull()?.coerceAtLeast(0) ?: 0)
            },
            modifier = Modifier.weight(1f),
        )
        StratumText(
            text = stringResource(R.string.edit_workout_rep_range_to),
            style = StratumThemeAccessor.typography.sectionLabel,
            muted = true,
        )
        InlineNumericCell(
            value = if (repMax > 0) repMax.toString() else "",
            placeholder = stringResource(R.string.edit_workout_rep_range_max_placeholder),
            onCommit = { text ->
                onRepMaxChange(text.toIntOrNull()?.coerceAtLeast(0) ?: 0)
            },
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
private fun InlineNumericCell(
    value: String,
    placeholder: String,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusManager = LocalFocusManager.current
    var fieldValue by remember(value) {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }

    LaunchedEffect(value) {
        if (!isFocused) {
            fieldValue = TextFieldValue(text = value, selection = TextRange(value.length))
        }
    }

    LaunchedEffect(isFocused) {
        if (!isFocused && fieldValue.text != value) {
            onCommit(fieldValue.text)
        }
    }

    BasicTextField(
        value = fieldValue,
        onValueChange = { updated ->
            val digitsOnly = updated.text.filter { char -> char.isDigit() }
            fieldValue = updated.copy(text = digitsOnly, selection = TextRange(digitsOnly.length))
        },
        modifier =
            modifier
                .height(36.dp)
                .widthIn(min = SetTableCellMinWidth)
                .clip(StratumThemeAccessor.shapes.chip)
                .background(StratumThemeAccessor.colors.surfaceInset)
                .border(
                    width = StratumThemeAccessor.shapes.borderWidth,
                    color =
                        if (isFocused) {
                            StratumThemeAccessor.colors.accent
                        } else {
                            StratumThemeAccessor.colors.borderSubtle
                        },
                    shape = StratumThemeAccessor.shapes.chip,
                )
                .padding(horizontal = 8.dp),
        textStyle =
            StratumThemeAccessor.typography.bodyLarge.copy(
                color = StratumThemeAccessor.colors.textPrimary,
                textAlign = TextAlign.Center,
            ),
        singleLine = true,
        cursorBrush = SolidColor(StratumThemeAccessor.colors.accent),
        interactionSource = interactionSource,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
        keyboardActions =
            KeyboardActions(
                onDone = {
                    onCommit(fieldValue.text)
                    focusManager.clearFocus()
                },
            ),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.Center) {
                if (fieldValue.text.isEmpty() && !isFocused) {
                    StratumText(text = placeholder, muted = true)
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun RestTimeCell(
    restSeconds: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .padding(horizontal = 4.dp)
                .height(36.dp)
                .clip(StratumThemeAccessor.shapes.chip)
                .background(StratumThemeAccessor.colors.surfaceInset)
                .border(
                    width = StratumThemeAccessor.shapes.borderWidth,
                    color = StratumThemeAccessor.colors.borderSubtle,
                    shape = StratumThemeAccessor.shapes.chip,
                )
                .clickable(onClick = onClick)
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
