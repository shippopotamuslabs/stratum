package com.stratum.feature.program.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.formatRirLabel
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.program.CreateProgramDayUi
import com.stratum.feature.program.CreateProgramUiState
import com.stratum.feature.program.ProgramSheetRow
import com.stratum.feature.program.R
import com.stratum.feature.program.formatEstimatedWorkoutDuration
import com.stratum.feature.program.programSheetActiveDayId
import com.stratum.feature.program.programSheetAnchorIndex
import com.stratum.feature.program.programSheetRows
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

/**
 * One scroll of every program day. The tabbed editor stays the default;
 * [CreateProgramUiState.editorSurface] chooses this body.
 */
@Composable
internal fun ProgramSheet(
    state: CreateProgramUiState,
    callbacks: CreateProgramScreenCallbacks,
    topClearance: Dp,
    modifier: Modifier = Modifier,
) {
    val rows = remember(state.days) { programSheetRows(state.days) }
    val scroll =
        rememberProgramSheetListScroll(
            rows = rows,
            days = state.days,
            selectedDayId = state.selectedDayId,
            onSelectDay = callbacks.onSelectDay,
        )
    var menuDayId by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<SheetLiftTarget?>(null) }

    Column(modifier = modifier.fillMaxSize().padding(top = topClearance)) {
        DayTabsRow(
            days = state.days,
            selectedDayId = state.selectedDayId,
            canAddDay = state.canAddDay,
            onSelectDay = { dayId ->
                scroll.jumpToDay(dayId)
                callbacks.onSelectDay(dayId)
            },
            onAddDay = {
                scroll.scrollAfterAdd()
                callbacks.onAddDay()
            },
        )
        LazyColumn(
            state = scroll.listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = StratumSpacing.s16),
        ) {
            rows.forEach { row ->
                when (row) {
                    is ProgramSheetRow.WorkoutHeader ->
                        stickyHeader(key = "header-${row.dayId}") { _ ->
                            val day = state.days.firstOrNull { it.id == row.dayId } ?: return@stickyHeader
                            val edit = day.editState ?: return@stickyHeader
                            ProgramSheetWorkoutHeader(
                                day = day,
                                setTotal = setTotalFor(state, day, edit),
                                minutes = minutesFor(state, day, edit),
                                onAddLift = {
                                    callbacks.onSelectDay(day.id)
                                    callbacks.onAddExercise()
                                },
                                onOpenMenu = { menuDayId = day.id },
                            )
                        }
                    is ProgramSheetRow.ColumnHeader ->
                        item(key = "columns-${row.dayId}") {
                            ProgramSheetColumnHeader()
                        }
                    is ProgramSheetRow.Lift ->
                        item(key = "lift-${row.dayId}-${row.workoutLiftId}") {
                            val day = state.days.firstOrNull { it.id == row.dayId } ?: return@item
                            val edit = day.editState ?: return@item
                            val lift =
                                edit.lifts.firstOrNull { it.workoutLiftId == row.workoutLiftId }
                                    ?: return@item
                            ProgramSheetLiftRow(
                                lift = lift,
                                zebra = edit.lifts.indexOf(lift) % 2 == 1,
                                onClick = {
                                    callbacks.onSelectDay(day.id)
                                    editing = SheetLiftTarget(day.id, lift.workoutLiftId)
                                },
                            )
                        }
                    is ProgramSheetRow.Rest ->
                        item(key = "rest-${row.dayId}") {
                            val day = state.days.firstOrNull { it.id == row.dayId } ?: return@item
                            ProgramSheetRestRow(name = day.name)
                        }
                }
            }
        }
    }

    val menuDay = state.days.firstOrNull { it.id == menuDayId }
    if (menuDay != null) {
        ProgramSheetWorkoutMenu(
            dayName = menuDay.name,
            onRename = {
                menuDayId = null
                callbacks.onSelectDay(menuDay.id)
                callbacks.onShowRenameWorkout()
            },
            onRemove = {
                menuDayId = null
                callbacks.onSelectDay(menuDay.id)
                callbacks.onRemoveDay()
            },
            onReorderLifts = {
                menuDayId = null
                callbacks.onSelectDay(menuDay.id)
                callbacks.onReorderLifts()
            },
            onDismiss = { menuDayId = null },
        )
    }

    val target = editing
    val edit = target?.let { open -> state.days.firstOrNull { it.id == open.dayId }?.editState }
    val lift = target?.let { open -> edit?.lifts?.firstOrNull { it.workoutLiftId == open.workoutLiftId } }
    if (edit != null && lift != null) {
        ProgramSheetLiftEditor(
            liftName = lift.name,
            // Display copy only. The stored day's lift list is not filtered.
            editState = edit.copy(lifts = listOf(lift)),
            callbacks = callbacks,
            onDismiss = { editing = null },
        )
    }
}

private data class SheetLiftTarget(val dayId: String, val workoutLiftId: String)

private sealed interface SheetScroll {
    data class ToDay(val dayId: String) : SheetScroll

    data class AfterAdd(val previousCount: Int) : SheetScroll
}

private class ProgramSheetListScroll(
    val listState: LazyListState,
    val jumpToDay: (String) -> Unit,
    val scrollAfterAdd: () -> Unit,
)

/**
 * Strip taps and add-day scroll in flight. Scroll-follow is ignored until that
 * scroll finishes, and a follow-driven selection does not request another scroll.
 */
@Composable
private fun rememberProgramSheetListScroll(
    rows: List<ProgramSheetRow>,
    days: List<CreateProgramDayUi>,
    selectedDayId: String?,
    onSelectDay: (String) -> Unit,
): ProgramSheetListScroll {
    val listState = rememberLazyListState()
    val followSuppressed = remember { mutableStateOf(false) }
    val requestId = remember { mutableIntStateOf(0) }
    val pendingScroll = remember { mutableStateOf<SheetScroll?>(null) }
    val rowsState = rememberUpdatedState(rows)
    val daysState = rememberUpdatedState(days)
    val selectedState = rememberUpdatedState(selectedDayId)
    val onSelectState = rememberUpdatedState(onSelectDay)
    val generation = requestId.intValue

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to followSuppressed.value }
            .distinctUntilChanged()
            .drop(1)
            .collect { (index, suppressed) ->
                if (suppressed) return@collect
                val dayId = programSheetActiveDayId(rowsState.value, index) ?: return@collect
                if (dayId != selectedState.value) onSelectState.value(dayId)
            }
    }

    LaunchedEffect(generation, days.size) {
        val id = requestId.intValue
        val target = pendingScroll.value ?: return@LaunchedEffect
        val dayId =
            when (target) {
                is SheetScroll.ToDay -> target.dayId
                is SheetScroll.AfterAdd -> {
                    if (daysState.value.size <= target.previousCount) return@LaunchedEffect
                    daysState.value.last().id
                }
            }
        try {
            listState.scrollToSheetAnchor(rowsState.value, dayId)
        } finally {
            if (requestId.intValue == id) {
                followSuppressed.value = false
                pendingScroll.value = null
            }
        }
    }

    return ProgramSheetListScroll(
        listState = listState,
        jumpToDay = { dayId ->
            followSuppressed.value = true
            pendingScroll.value = SheetScroll.ToDay(dayId)
            requestId.intValue++
        },
        scrollAfterAdd = {
            followSuppressed.value = true
            pendingScroll.value = SheetScroll.AfterAdd(daysState.value.size)
            requestId.intValue++
        },
    )
}

private suspend fun LazyListState.scrollToSheetAnchor(rows: List<ProgramSheetRow>, dayId: String) {
    val index = programSheetAnchorIndex(rows, dayId) ?: return
    animateScrollToItem(index)
}

@Composable
private fun ProgramSheetWorkoutHeader(
    day: CreateProgramDayUi,
    setTotal: Int,
    minutes: Int,
    onAddLift: () -> Unit,
    onOpenMenu: () -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    val setsLabel = pluralStringResource(R.plurals.create_program_sheet_set_total, setTotal, setTotal)
    val meta =
        stringResource(
            R.string.create_program_sheet_workout_meta,
            setsLabel,
            formatEstimatedWorkoutDuration(minutes),
        )
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.surfaceBase)
                .sheetHairline()
                .padding(horizontal = StratumSpacing.s16, vertical = StratumSpacing.s8),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StratumText(
                text = day.name,
                modifier = Modifier.weight(1f),
                style = StratumThemeAccessor.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StratumIconButton(onClick = onAddLift) {
                Icon(
                    painter = painterResource(CoreUiR.drawable.ic_add),
                    contentDescription = stringResource(R.string.edit_workout_add_lift),
                    tint = colors.textPrimary,
                )
            }
            StratumIconButton(onClick = onOpenMenu) {
                Icon(
                    painter = painterResource(CoreUiR.drawable.ic_more_vert),
                    contentDescription = stringResource(R.string.create_program_sheet_workout_menu),
                    tint = colors.textPrimary,
                )
            }
        }
        StratumText(
            text = meta,
            muted = true,
            style = StratumThemeAccessor.typography.bodyMedium,
        )
    }
}

@Composable
private fun ProgramSheetColumnHeader() {
    SheetLine(
        lift = stringResource(R.string.create_program_sheet_column_lift),
        sets = stringResource(R.string.create_program_sheet_column_sets),
        reps = stringResource(R.string.create_program_sheet_column_reps),
        rir = stringResource(R.string.edit_workout_column_rir),
        rest = stringResource(R.string.edit_workout_column_rest),
        header = true,
        modifier =
            Modifier
                .background(StratumThemeAccessor.colors.surfaceBase)
                .sheetHairline()
                .padding(horizontal = StratumSpacing.s16, vertical = StratumSpacing.s8),
    )
}

@Composable
private fun ProgramSheetLiftRow(
    lift: EditLiftState,
    zebra: Boolean,
    onClick: () -> Unit,
) {
    val background =
        if (zebra) {
            StratumThemeAccessor.colors.surfaceSunken
        } else {
            StratumThemeAccessor.colors.surfaceBase
        }
    SheetLine(
        lift = lift.name,
        sets = lift.sets.size.toString(),
        reps = sheetRepLabel(lift.sets),
        rir = sheetRirLabel(lift.sets),
        rest = sheetRestLabel(lift.sets),
        header = false,
        modifier =
            Modifier
                .background(background)
                .sheetHairline()
                .clickable(role = Role.Button, onClick = onClick)
                .heightIn(min = 48.dp)
                .padding(horizontal = StratumSpacing.s16),
    )
}

@Composable
private fun ProgramSheetRestRow(name: String) {
    StratumText(
        text = name,
        muted = true,
        style = StratumThemeAccessor.typography.bodyLarge,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(StratumThemeAccessor.colors.surfaceBase)
                .sheetHairline()
                .padding(horizontal = StratumSpacing.s16, vertical = StratumSpacing.s8),
    )
}

@Composable
private fun SheetLine(
    lift: String,
    sets: String,
    reps: String,
    rir: String,
    rest: String,
    header: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (header) {
            HeaderCell(lift, LIFT_COLUMN_WEIGHT, TextAlign.Start)
            HeaderCell(sets, SETS_COLUMN_WEIGHT, TextAlign.Center)
            HeaderCell(reps, REPS_COLUMN_WEIGHT, TextAlign.Center)
            HeaderCell(rir, RIR_COLUMN_WEIGHT, TextAlign.Center)
            HeaderCell(rest, REST_COLUMN_WEIGHT, TextAlign.Center)
        } else {
            NameCell(lift, LIFT_COLUMN_WEIGHT)
            NumberCell(sets, SETS_COLUMN_WEIGHT)
            NumberCell(reps, REPS_COLUMN_WEIGHT)
            NumberCell(rir, RIR_COLUMN_WEIGHT)
            NumberCell(rest, REST_COLUMN_WEIGHT)
        }
    }
}

@Composable
private fun RowScope.HeaderCell(text: String, weight: Float, align: TextAlign) {
    StratumText(
        text = text,
        modifier = Modifier.weight(weight),
        style = StratumThemeAccessor.typography.sectionLabel,
        muted = true,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = align,
    )
}

@Composable
private fun RowScope.NameCell(text: String, weight: Float) {
    StratumText(
        text = text,
        modifier = Modifier.weight(weight),
        style = StratumThemeAccessor.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun RowScope.NumberCell(text: String, weight: Float) {
    StratumNumber(
        text = text,
        modifier = Modifier.weight(weight),
        style = StratumThemeAccessor.typography.sectionLabel.copy(fontFeatureSettings = "tnum"),
        maxLines = 1,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ProgramSheetWorkoutMenu(
    dayName: String,
    onRename: () -> Unit,
    onRemove: () -> Unit,
    onReorderLifts: () -> Unit,
    onDismiss: () -> Unit,
) {
    StratumBottomSheet(onDismissRequest = onDismiss, title = dayName) {
        CreateProgramOptionRow(
            text = stringResource(R.string.create_program_rename_workout),
            leadingIcon = rememberVectorPainter(Icons.Outlined.Edit),
            onClick = onRename,
        )
        CreateProgramOptionRow(
            text = stringResource(R.string.create_program_remove_day),
            leadingIconRes = CoreUiR.drawable.ic_delete,
            onClick = onRemove,
        )
        CreateProgramOptionRow(
            text = stringResource(CoreUiR.string.edit_workout_reorder_lifts),
            leadingIconRes = CoreUiR.drawable.ic_swap_vert,
            onClick = onReorderLifts,
        )
    }
}

@Composable
private fun ProgramSheetLiftEditor(
    liftName: String,
    editState: EditWorkoutUiState.Loaded,
    callbacks: CreateProgramScreenCallbacks,
    onDismiss: () -> Unit,
) {
    val listHeight = (LocalConfiguration.current.screenHeightDp * LIFT_EDITOR_HEIGHT_FRACTION).dp
    StratumBottomSheet(onDismissRequest = onDismiss, title = liftName) {
        EditWorkoutEmbeddedContent(
            state = editState,
            callbacks = editWorkoutCallbacks(callbacks),
            modifier = Modifier.fillMaxWidth().height(listHeight),
            showVolume = false,
        )
    }
}

@Composable
private fun Modifier.sheetHairline(): Modifier {
    val color = StratumThemeAccessor.colors.borderSubtle
    val width = StratumThemeAccessor.shapes.borderWidth
    return drawBehind {
        val stroke = width.toPx()
        val y = size.height - stroke / 2f
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = stroke,
        )
    }
}

private fun setTotalFor(
    state: CreateProgramUiState,
    day: CreateProgramDayUi,
    edit: EditWorkoutUiState.Loaded,
): Int =
    state.sheetMetrics?.workouts?.firstOrNull { workout -> workout.dayId == day.id }?.setTotal
        ?: edit.lifts.sumOf { lift -> lift.sets.size }

private fun minutesFor(
    state: CreateProgramUiState,
    day: CreateProgramDayUi,
    edit: EditWorkoutUiState.Loaded,
): Int =
    state.sheetMetrics?.workouts?.firstOrNull { workout -> workout.dayId == day.id }?.estimatedDurationMinutes
        ?: edit.estimatedDurationMinutes

/** Shared band when every set matches; otherwise the first set's band. */
private fun sheetRepLabel(sets: List<EditSetState>): String {
    val first = sets.firstOrNull() ?: return SHEET_EMPTY_CELL
    return "${first.repMin}$REP_RANGE_DASH${first.repMax}"
}

private fun sheetRirLabel(sets: List<EditSetState>): String {
    val first = sets.firstOrNull() ?: return SHEET_EMPTY_CELL
    return formatRirLabel(first.rirGoal)
}

private fun sheetRestLabel(sets: List<EditSetState>): String {
    val first = sets.firstOrNull() ?: return SHEET_EMPTY_CELL
    return formatRestDisplay(first.restSeconds)
}

private const val LIFT_COLUMN_WEIGHT = 0.40f
private const val SETS_COLUMN_WEIGHT = 0.12f
private const val REPS_COLUMN_WEIGHT = 0.16f
private const val RIR_COLUMN_WEIGHT = 0.12f
private const val REST_COLUMN_WEIGHT = 0.20f
private const val LIFT_EDITOR_HEIGHT_FRACTION = 0.55f
private const val REP_RANGE_DASH = "–"
private const val SHEET_EMPTY_CELL = "—"
