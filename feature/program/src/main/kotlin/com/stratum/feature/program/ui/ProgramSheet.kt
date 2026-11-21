package com.stratum.feature.program.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.rememberVectorPainter
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
import com.stratum.core.ui.component.StratumTextButton
import com.stratum.core.ui.component.formatRirLabel
import com.stratum.core.ui.component.workoutlog.rememberOverlayDockListBinding
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.theme.stratumDockTopRule
import com.stratum.feature.program.CreateProgramDayUi
import com.stratum.feature.program.CreateProgramUiState
import com.stratum.feature.program.ProgramSheetClipboard
import com.stratum.feature.program.ProgramSheetClipboardKind
import com.stratum.feature.program.ProgramSheetLiftRef
import com.stratum.feature.program.ProgramSheetLiftStep
import com.stratum.feature.program.ProgramSheetLiftTarget
import com.stratum.feature.program.ProgramSheetPadMoment
import com.stratum.feature.program.ProgramSheetRow
import com.stratum.feature.program.ProgramSheetVolumeChip
import com.stratum.feature.program.R
import com.stratum.feature.program.sheetSetTotal
import com.stratum.feature.program.SheetPlacedItem
import com.stratum.feature.program.expandedAfterSheetRemoval
import com.stratum.feature.program.formatEstimatedWorkoutDuration
import com.stratum.feature.program.programSheetAdjacentLift
import com.stratum.feature.program.programSheetAnchorIndex
import com.stratum.feature.program.programSheetFollowDayId
import com.stratum.feature.program.programSheetLiftIndex
import com.stratum.feature.program.programSheetMuscleSets
import com.stratum.feature.program.programSheetPadAction
import com.stratum.feature.program.programSheetRows
import com.stratum.feature.program.programSheetVolumeChips
import com.stratum.feature.program.sheetStickyHeaderOverlapPx
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Preview seam for one expanded sheet lift. Production leaves this null;
 * expansion after that is remembered on [ProgramSheet], not in the draft.
 */
internal val LocalInitialProgramSheetLift =
    staticCompositionLocalOf<ProgramSheetLiftTarget?> { null }

/** Sheet tray. Null unless a cut or copy is in progress. Not part of the draft. */
internal val LocalProgramSheetClipboard =
    staticCompositionLocalOf<ProgramSheetClipboard?> { null }

/**
 * One scroll of every program day. The editor step always shows this body.
 */
@Composable
internal fun ProgramSheet(
    state: CreateProgramUiState,
    callbacks: CreateProgramScreenCallbacks,
    topClearance: Dp,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalProgramSheetClipboard.current
    val rows =
        remember(state.days, clipboard != null) {
            programSheetRows(state.days, clipboardActive = clipboard != null)
        }
    val seededLift = LocalInitialProgramSheetLift.current
    val selection = LocalProgramSheetSelection.current
    val dockHost = remember { EditWorkoutDockHostState() }
    val scroll =
        rememberProgramSheetListScroll(
            rows = rows,
            days = state.days,
            selectedDayId = state.selectedDayId,
            onSelectDay = callbacks.onSelectDay,
            onLiftScrollSettled = { revealSheetDockAfterLiftScroll(dockHost) },
        )
    var menuDayId by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(seededLift) }
    val sheetCallbacks =
        callbacks.copy(
            onCutSheetLift = { dayId, liftId ->
                expanded =
                    expandedAfterSheetRemoval(
                        expanded = expanded,
                        removedDayId = dayId,
                        removedWorkoutLiftId = liftId,
                    )
                callbacks.onCutSheetLift(dayId, liftId)
            },
            onRemoveSheetLift = { dayId, liftId, message ->
                expanded =
                    expandedAfterSheetRemoval(
                        expanded = expanded,
                        removedDayId = dayId,
                        removedWorkoutLiftId = liftId,
                    )
                callbacks.onRemoveSheetLift(dayId, liftId, message)
            },
        )
    var rirSetId by remember { mutableStateOf<String?>(null) }
    var liftActionsLiftId by remember { mutableStateOf<String?>(null) }
    var showProgressionSchemeExplainer by remember { mutableStateOf(false) }
    LaunchedEffect(selection.active) {
        val moment = if (selection.active) ProgramSheetPadMoment.EnterSelect else ProgramSheetPadMoment.LeaveSelect
        dockHost.applySheetPad(programSheetPadAction(moment, dockHost.openSetId()))
        if (!selection.active) return@LaunchedEffect
        expanded = null
        rirSetId = null
        liftActionsLiftId = null
    }
    DisposableEffect(dockHost) {
        callbacks.onRegisterOpenBulkRepCommit {
            dockHost.applySheetPad(programSheetPadAction(ProgramSheetPadMoment.SaveProceeding, dockHost.openSetId()))
        }
        onDispose {
            dockHost.applySheetPad(programSheetPadAction(ProgramSheetPadMoment.HostDisposed, dockHost.openSetId()))
            callbacks.onRegisterOpenBulkRepCommit(null)
        }
    }
    val dockHeight =
        rememberOverlayDockListBinding(
            listState = scroll.listState,
            dock = dockHost.overlay,
        )
    val openLift = expanded
    val previousEnabled = sheetLiftStepEnabled(rows, openLift, ProgramSheetLiftStep.Previous)
    val nextEnabled = sheetLiftStepEnabled(rows, openLift, ProgramSheetLiftStep.Next)
    val stepTo = { step: ProgramSheetLiftStep, anchorSetId: String? ->
        val moved =
            movedProgramSheetLift(
                expanded = expanded,
                rows = rows,
                days = state.days,
                step = step,
                anchorSetId = anchorSetId,
                host = dockHost,
                onSelectDay = callbacks.onSelectDay,
                onSetRepBand = callbacks.onSetRepBand,
                onSetRestSeconds = callbacks.onSetRestSeconds,
                onSetRestSecondsForAllSetsOnLift = callbacks.onSetRestSecondsForAllSetsOnLift,
                rirSetId = rirSetId,
            )
        if (moved != null) {
            rirSetId = moved.rirSetId
            expanded = moved.target
            scroll.jumpToLift(moved.target)
        }
    }

    ProvideEditWorkoutDock(
        modifier = modifier.fillMaxSize().padding(top = topClearance),
        host = dockHost,
        onPreviousLift =
            if (openLift == null) {
                null
            } else {
                { stepTo(ProgramSheetLiftStep.Previous, dockHost.openSetId()) }
            },
        onNextLift =
            if (openLift == null) {
                null
            } else {
                { stepTo(ProgramSheetLiftStep.Next, dockHost.openSetId()) }
            },
        previousLiftEnabled = previousEnabled,
        nextLiftEnabled = nextEnabled,
    ) { listModifier, _ ->
        Column(modifier = listModifier) {
            DayTabsRow(
                days = state.days,
                selectedDayId = state.selectedDayId,
                canAddDay = state.canAddDay,
                onSelectDay = { dayId ->
                    scroll.jumpToDay(dayId)
                    sheetCallbacks.onSelectDay(dayId)
                },
                onAddDay = {
                    scroll.scrollAfterAdd()
                    sheetCallbacks.onAddDay()
                },
            )
            ProgramSheetVolumeStrip(state = state)
            LazyColumn(
                state = scroll.listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = StratumSpacing.s16 + dockHeight),
            ) {
                rows.forEach { row ->
                    when (row) {
                        is ProgramSheetRow.WorkoutHeader ->
                            stickyHeader(key = "$SHEET_WORKOUT_HEADER_KEY_PREFIX${row.dayId}") { _ ->
                                ProgramSheetStickyWorkout(
                                    dayId = row.dayId,
                                    days = state.days,
                                    onAddLift = { dayId ->
                                        sheetCallbacks.onSelectDay(dayId)
                                        sheetCallbacks.onAddExercise()
                                    },
                                    onOpenMenu = { dayId -> menuDayId = dayId },
                                )
                            }
                        is ProgramSheetRow.ColumnHeader ->
                            item(key = "columns-${row.dayId}") {
                                ProgramSheetColumnHeader(reserveSelection = selection.active)
                            }
                        is ProgramSheetRow.Lift ->
                            item(key = "lift-${row.dayId}-${row.workoutLiftId}") {
                                ProgramSheetLiftSlot(
                                    day = state.days.firstOrNull { it.id == row.dayId },
                                    workoutLiftId = row.workoutLiftId,
                                    expanded = openLift,
                                    callbacks = sheetCallbacks,
                                    dockHost = dockHost,
                                    onExpand = { target ->
                                        rirSetId = null
                                        liftActionsLiftId = null
                                        callbacks.onSelectDay(target.dayId)
                                        expanded = target
                                    },
                                    onCollapse = { _ ->
                                        rirSetId = null
                                        liftActionsLiftId = null
                                        expanded = null
                                    },
                                    onShowLiftActions = { liftActionsLiftId = it },
                                    onRirClick = { set -> rirSetId = set.setId },
                                    onShowProgressionSchemeInfo = { showProgressionSchemeExplainer = true },
                                )
                            }
                        is ProgramSheetRow.PasteSlot ->
                            item(key = "paste-${row.dayId}") {
                                val held = clipboard ?: return@item
                                val message =
                                    when (held.kind) {
                                        ProgramSheetClipboardKind.Cut ->
                                            stringResource(
                                                R.string.create_program_sheet_moved,
                                                held.lift.name,
                                            )
                                        ProgramSheetClipboardKind.Copy ->
                                            stringResource(
                                                R.string.create_program_sheet_copied,
                                                held.lift.name,
                                            )
                                    }
                                ProgramSheetPasteSlotRow(
                                    liftName = held.lift.name,
                                    onClick = {
                                        sheetCallbacks.onPasteSheetLift(row.dayId, message)
                                    },
                                )
                            }
                        is ProgramSheetRow.Rest ->
                            item(key = "rest-${row.dayId}") {
                                val day = state.days.firstOrNull { it.id == row.dayId } ?: return@item
                                ProgramSheetRestRow(
                                    name = day.name,
                                    onOpenMenu = { menuDayId = day.id },
                                )
                            }
                    }
                }
            }
            if (selection.active) {
                ProgramSheetBulkHost(
                    days = state.days,
                    selection = selection.ordered,
                    dockHost = dockHost,
                    onAddSet = sheetCallbacks.onAddSetToSheetSelection,
                    onRemoveSet = sheetCallbacks.onRemoveSetFromSheetSelection,
                    onSetRir = sheetCallbacks.onSetRirOnSheetSelection,
                    onSetRepBand = sheetCallbacks.onSetRepBandOnSheetSelection,
                    onMove = sheetCallbacks.onMoveSheetSelection,
                    onDelete = sheetCallbacks.onDeleteSheetSelection,
                    onCancel = sheetCallbacks.onCancelSheetSelection,
                )
            } else if (clipboard != null) {
                ProgramSheetClipboardTray(
                    clipboard = clipboard,
                    onCancel = sheetCallbacks.onCancelSheetClipboard,
                )
            }
        }
    }

    val menuDay = state.days.firstOrNull { it.id == menuDayId }
    if (menuDay != null) {
        ProgramSheetWorkoutMenu(
            dayName = menuDay.name,
            showReorderLifts = !menuDay.isRest && menuDay.editState != null,
            onRename = {
                menuDayId = null
                sheetCallbacks.onSelectDay(menuDay.id)
                sheetCallbacks.onShowRenameWorkout()
            },
            onRemove = {
                menuDayId = null
                val kept =
                    expandedAfterSheetRemoval(
                        expanded = expanded,
                        removedDayId = menuDay.id,
                        removedWorkoutLiftId = null,
                    )
                if (kept != expanded) {
                    expanded = kept
                    rirSetId = null
                }
                sheetCallbacks.onSelectDay(menuDay.id)
                sheetCallbacks.onRemoveDay()
            },
            onReorderLifts = {
                menuDayId = null
                sheetCallbacks.onSelectDay(menuDay.id)
                sheetCallbacks.onReorderLifts()
            },
            onDismiss = { menuDayId = null },
        )
    }

    ProgramSheetLiftSheets(
        days = state.days,
        callbacks = sheetCallbacks,
        rirSetId = rirSetId,
        onDismissRir = { rirSetId = null },
        onPreviousLift = {
            if (dockHost.tryCommitPendingAndStay()) {
                stepTo(ProgramSheetLiftStep.Previous, rirSetId ?: dockHost.openSetId())
            }
        },
        onNextLift = {
            if (dockHost.tryCommitPendingAndStay()) {
                stepTo(ProgramSheetLiftStep.Next, rirSetId ?: dockHost.openSetId())
            }
        },
        previousEnabled = previousEnabled,
        nextEnabled = nextEnabled,
        liftActionsLiftId = liftActionsLiftId,
        onDismissLiftActions = { liftActionsLiftId = null },
        showProgressionSchemeExplainer = showProgressionSchemeExplainer,
        onDismissProgressionSchemeExplainer = { showProgressionSchemeExplainer = false },
    )
}

private fun sheetLiftStepEnabled(
    rows: List<ProgramSheetRow>,
    openLift: ProgramSheetLiftTarget?,
    step: ProgramSheetLiftStep,
): Boolean {
    val target = openLift ?: return false
    return programSheetAdjacentLift(rows, target.dayId, target.workoutLiftId, step) != null
}

@Composable
private fun ProgramSheetLiftSlot(
    day: CreateProgramDayUi?,
    workoutLiftId: String,
    expanded: ProgramSheetLiftTarget?,
    callbacks: CreateProgramScreenCallbacks,
    dockHost: EditWorkoutDockHostState,
    onExpand: (ProgramSheetLiftTarget) -> Unit,
    onCollapse: (ProgramSheetLiftTarget) -> Unit,
    onShowLiftActions: (String) -> Unit,
    onRirClick: (EditSetState) -> Unit,
    onShowProgressionSchemeInfo: () -> Unit,
) {
    val edit = day?.editState ?: return
    val lift = edit.lifts.firstOrNull { it.workoutLiftId == workoutLiftId } ?: return
    val selection = LocalProgramSheetSelection.current
    val target = ProgramSheetLiftTarget(day.id, lift.workoutLiftId)
    if (selection.active) {
        ProgramSheetSelectableLiftRow(
            lift = lift,
            zebra = edit.lifts.indexOf(lift) % 2 == 1,
            checked = ProgramSheetLiftRef(day.id, lift.workoutLiftId) in selection.ordered,
            onToggle = { callbacks.onToggleSheetLift(day.id, lift.workoutLiftId) },
        )
        return
    }
    val isOpen = expanded == target
    if (isOpen) {
        ProgramSheetExpandedLift(
            lift = lift,
            allLifts = edit.lifts,
            callbacks = editWorkoutCallbacks(callbacks).scopedToDay(day.id, callbacks.onSelectDay),
            onCollapse = collapse@{
                val left =
                    skipDockRestoreForProgramSheetLiftScroll(dockHost) {
                        dockHost.tryCommitPendingOrBlock()
                    }
                if (!left) return@collapse
                onCollapse(target)
            },
            onShowLiftActions = { onShowLiftActions(lift.workoutLiftId) },
            onRirClick = onRirClick,
            onShowProgressionSchemeInfo = onShowProgressionSchemeInfo,
        )
    } else {
        ProgramSheetLiftRow(
            lift = lift,
            zebra = edit.lifts.indexOf(lift) % 2 == 1,
            onClick = expand@{
                val left =
                    skipDockRestoreForProgramSheetLiftScroll(dockHost) {
                        dockHost.tryCommitPendingOrBlock()
                    }
                if (!left) return@expand
                onExpand(target)
            },
            onLongClick = longPress@{
                val left =
                    skipDockRestoreForProgramSheetLiftScroll(dockHost) {
                        dockHost.tryCommitPendingOrBlock()
                    }
                if (!left) return@longPress
                callbacks.onSelectDay(day.id)
                onShowLiftActions(lift.workoutLiftId)
            },
        )
    }
}

private sealed interface SheetScroll {
    data class ToDay(val dayId: String) : SheetScroll

    data class ToLift(val dayId: String, val workoutLiftId: String) : SheetScroll

    data class AfterAdd(val previousCount: Int) : SheetScroll
}

private class ProgramSheetListScroll(
    val listState: LazyListState,
    val jumpToDay: (String) -> Unit,
    val scrollAfterAdd: () -> Unit,
    val jumpToLift: (ProgramSheetLiftTarget) -> Unit,
)

/**
 * Strip taps, add-day, and the opening selection scroll in flight.
 * Follow reads visible indices only, so clearing suppression does not emit.
 * The latched day stays selected while any of its rows are visible — scrolling
 * inside a workout does not jump back to that header or to day 0.
 */
@Composable
private fun rememberProgramSheetListScroll(
    rows: List<ProgramSheetRow>,
    days: List<CreateProgramDayUi>,
    selectedDayId: String?,
    onSelectDay: (String) -> Unit,
    onLiftScrollSettled: () -> Unit,
): ProgramSheetListScroll {
    val listState = rememberLazyListState()
    val entryDayId = remember { selectedDayId }
    val followSuppressed = remember { mutableStateOf(entryDayId != null) }
    val latchedDayId = remember { mutableStateOf(entryDayId) }
    val pendingScroll = remember { mutableStateOf<SheetScroll?>(entryDayId?.let(SheetScroll::ToDay)) }
    val requestId = remember { mutableIntStateOf(if (entryDayId != null) 1 else 0) }
    val rowsState = rememberUpdatedState(rows)
    val daysState = rememberUpdatedState(days)
    val onSelectState = rememberUpdatedState(onSelectDay)
    val onLiftScrollSettledState = rememberUpdatedState(onLiftScrollSettled)
    val generation = requestId.intValue

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { item -> item.index } }
            .distinctUntilChanged()
            .collect { visibleIndices ->
                val dayId =
                    if (followSuppressed.value) {
                        null
                    } else {
                        programSheetFollowDayId(
                            rows = rowsState.value,
                            visibleIndices = visibleIndices,
                            latchedDayId = latchedDayId.value,
                        )
                    }
                if (dayId != null && dayId != latchedDayId.value) {
                    latchedDayId.value = dayId
                    onSelectState.value(dayId)
                }
            }
    }

    LaunchedEffect(generation) {
        val id = requestId.intValue
        val target = pendingScroll.value
        if (target != null) {
            try {
                val dayId =
                    when (target) {
                        is SheetScroll.ToDay -> target.dayId
                        is SheetScroll.ToLift -> target.dayId
                        is SheetScroll.AfterAdd ->
                            awaitAddedSheetDayId(target.previousCount) { daysState.value }
                    }
                if (dayId != null) {
                    latchedDayId.value = dayId
                    when (target) {
                        is SheetScroll.ToLift -> {
                            listState.scrollToSheetLift(rowsState.value, target.dayId, target.workoutLiftId)
                            // Reveal waits until this scroll returns. Starting it from
                            // retarget cancels the lift scroll at the same priority.
                            onLiftScrollSettledState.value()
                        }
                        is SheetScroll.ToDay,
                        is SheetScroll.AfterAdd,
                        -> listState.scrollToSheetAnchor(rowsState.value, dayId)
                    }
                }
            } finally {
                if (requestId.intValue == id) {
                    followSuppressed.value = false
                    pendingScroll.value = null
                }
            }
        }
    }

    return ProgramSheetListScroll(
        listState = listState,
        jumpToDay = { dayId ->
            followSuppressed.value = true
            latchedDayId.value = dayId
            pendingScroll.value = SheetScroll.ToDay(dayId)
            requestId.intValue++
        },
        scrollAfterAdd = {
            followSuppressed.value = true
            pendingScroll.value = SheetScroll.AfterAdd(daysState.value.size)
            requestId.intValue++
        },
        jumpToLift = { target ->
            followSuppressed.value = true
            latchedDayId.value = target.dayId
            pendingScroll.value = SheetScroll.ToLift(target.dayId, target.workoutLiftId)
            requestId.intValue++
        },
    )
}

/**
 * addRestDay appends on the following composition. A no-op never grows the
 * list; after a frame that miss clears suppression instead of leaving follow off.
 */
private suspend fun awaitAddedSheetDayId(
    previousCount: Int,
    days: () -> List<CreateProgramDayUi>,
): String? {
    var framesWaited = 0
    while (days().size <= previousCount && framesWaited < ADD_DAY_LAYOUT_FRAMES) {
        withFrameNanos { }
        framesWaited++
    }
    val resolved = days()
    return if (resolved.size <= previousCount) null else resolved.last().id
}

private suspend fun LazyListState.scrollToSheetAnchor(rows: List<ProgramSheetRow>, dayId: String) {
    val index = programSheetAnchorIndex(rows, dayId) ?: return
    animateScrollToItem(index)
}

private suspend fun LazyListState.scrollToSheetLift(
    rows: List<ProgramSheetRow>,
    dayId: String,
    workoutLiftId: String,
) {
    val index = programSheetLiftIndex(rows, dayId, workoutLiftId) ?: return
    animateScrollToItem(index)
    // A stuck workout header occupies this offset and does not push the lift down.
    val overlap =
        sheetStickyHeaderOverlapPx(
            items =
                layoutInfo.visibleItemsInfo.map { item ->
                    val itemKey = item.key
                    SheetPlacedItem(
                        index = item.index,
                        offsetPx = item.offset,
                        sizePx = item.size,
                        stickyHeader =
                            itemKey is String &&
                                itemKey.startsWith(SHEET_WORKOUT_HEADER_KEY_PREFIX) &&
                                item.index < index,
                    )
                },
            targetIndex = index,
        )
    if (overlap > 0) {
        animateScrollBy(-overlap.toFloat())
    }
}

@Composable
private fun ProgramSheetStickyWorkout(
    dayId: String,
    days: List<CreateProgramDayUi>,
    onAddLift: (String) -> Unit,
    onOpenMenu: (String) -> Unit,
) {
    val day = days.firstOrNull { it.id == dayId } ?: return
    val edit = day.editState ?: return
    val muscles = programSheetMuscleSets(edit.upperVolumeByMuscle + edit.lowerVolumeByMuscle)
    val chips = remember(muscles) { programSheetVolumeChips(muscles) }
    ProgramSheetWorkoutHeader(
        day = day,
        setTotal = sheetSetTotal(edit.lifts),
        minutes = edit.estimatedDurationMinutes,
        chips = chips,
        onAddLift = { onAddLift(day.id) },
        onOpenMenu = { onOpenMenu(day.id) },
    )
}

@Composable
private fun ProgramSheetWorkoutHeader(
    day: CreateProgramDayUi,
    setTotal: Int,
    minutes: Int,
    chips: List<ProgramSheetVolumeChip>,
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
                .background(colors.surfaceRaised)
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
                style = StratumThemeAccessor.typography.titleLarge,
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
        SheetVolumeChips(chips = chips)
    }
}

/**
 * Dashed slot at the end of a workout's lifts while the clipboard holds a
 * lift. Tapping pastes the held lift into this workout, same as the header
 * text button it replaces; the row matches a collapsed lift row's height.
 */
@Composable
private fun ProgramSheetPasteSlotRow(
    liftName: String,
    onClick: () -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val strokeWidth = shapes.borderWidth
    val cornerRadius = shapes.panelCorner
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = StratumSpacing.s16, vertical = StratumSpacing.s4),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .drawBehind {
                        val stroke = strokeWidth.toPx()
                        drawRoundRect(
                            color = colors.borderStrong,
                            topLeft = Offset(stroke / 2, stroke / 2),
                            size = Size(size.width - stroke, size.height - stroke),
                            cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
                            style =
                                Stroke(
                                    width = stroke,
                                    pathEffect =
                                        PathEffect.dashPathEffect(
                                            floatArrayOf(6.dp.toPx(), 4.dp.toPx()),
                                        ),
                                ),
                        )
                    }
                    .clip(shapes.panel)
                    .clickable(onClick = onClick)
                    .padding(horizontal = StratumSpacing.s12),
            horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = rememberVectorPainter(Icons.Outlined.ContentPaste),
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(20.dp),
            )
            StratumText(
                text = stringResource(R.string.create_program_sheet_paste_lift, liftName),
                style = StratumThemeAccessor.typography.bodyLarge,
                color = colors.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ProgramSheetClipboardTray(
    clipboard: ProgramSheetClipboard,
    onCancel: () -> Unit,
) {
    val kind =
        when (clipboard.kind) {
            ProgramSheetClipboardKind.Cut -> stringResource(CoreUiR.string.edit_workout_cut_lift)
            ProgramSheetClipboardKind.Copy -> stringResource(CoreUiR.string.edit_workout_copy_lift)
        }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(StratumThemeAccessor.colors.surfaceRaised)
                .stratumDockTopRule()
                .padding(horizontal = StratumSpacing.s16, vertical = StratumSpacing.s8),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StratumText(
                text = clipboard.lift.name,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StratumText(
                text = kind,
                muted = true,
                style = StratumThemeAccessor.typography.bodyMedium,
            )
            StratumTextButton(
                text = stringResource(R.string.create_program_sheet_cancel),
                onClick = onCancel,
            )
        }
        if (clipboard.leavesSuperset) {
            StratumText(
                text = stringResource(R.string.create_program_sheet_leaves_superset),
                muted = true,
                style = StratumThemeAccessor.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ProgramSheetColumnHeader(reserveSelection: Boolean = false) {
    Row(
        modifier =
            Modifier
                .background(StratumThemeAccessor.colors.surfaceBase)
                .sheetHairline()
                .padding(
                    top = StratumSpacing.s8,
                    end = StratumSpacing.s16,
                    bottom = StratumSpacing.s8,
                    start = if (reserveSelection) 0.dp else StratumSpacing.s16,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (reserveSelection) {
            Spacer(modifier = Modifier.width(ProgramSheetSelectionSlot))
        }
        SheetLine(
            lift = stringResource(R.string.create_program_sheet_column_lift),
            sets = stringResource(R.string.create_program_sheet_column_sets),
            reps = stringResource(R.string.create_program_sheet_column_reps),
            rir = stringResource(R.string.edit_workout_column_rir),
            rest = stringResource(R.string.edit_workout_column_rest),
            header = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(ProgramSheetLiftActionsSlot))
    }
}

@Composable
private fun ProgramSheetLiftRow(
    lift: EditLiftState,
    zebra: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    val background = if (zebra) colors.surfaceSunken else colors.surfaceBase
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(background)
                .sheetHairline()
                .heightIn(min = 48.dp)
                .combinedClickable(
                    role = Role.Button,
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(start = StratumSpacing.s16, end = StratumSpacing.s16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SheetLine(
            lift = lift.name,
            sets = lift.sets.size.toString(),
            reps = sheetRepLabel(lift.sets),
            rir = sheetRirLabel(lift.sets),
            rest = sheetRestLabel(lift.sets),
            header = false,
            modifier = Modifier.weight(1f),
        )
        StratumIconButton(onClick = onLongClick) {
            Icon(
                painter = painterResource(CoreUiR.drawable.ic_more_vert),
                contentDescription = stringResource(CoreUiR.string.edit_workout_lift_menu),
                tint = colors.textPrimary,
            )
        }
    }
}

@Composable
private fun ProgramSheetRestRow(name: String, onOpenMenu: () -> Unit) {
    val colors = StratumThemeAccessor.colors
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                // Day band, distinct from the lift rows around it: inset
                // surface and a title-styled label, so a rest day reads as
                // a day in the scroll (STR-319).
                .background(colors.surfaceInset)
                .sheetHairline()
                .padding(horizontal = StratumSpacing.s16, vertical = StratumSpacing.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StratumText(
            text = name,
            modifier = Modifier.weight(1f),
            muted = true,
            style = StratumThemeAccessor.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        StratumIconButton(onClick = onOpenMenu) {
            Icon(
                painter = painterResource(CoreUiR.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.create_program_sheet_workout_menu),
                tint = colors.textPrimary,
            )
        }
    }
}

@Composable
internal fun SheetLine(
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
    showReorderLifts: Boolean,
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
        if (showReorderLifts) {
            CreateProgramOptionRow(
                text = stringResource(CoreUiR.string.edit_workout_reorder_lifts),
                leadingIconRes = CoreUiR.drawable.ic_swap_vert,
                onClick = onReorderLifts,
            )
        }
        CreateProgramOptionRow(
            text = stringResource(R.string.create_program_remove_day),
            leadingIconRes = CoreUiR.drawable.ic_delete,
            onClick = onRemove,
            destructive = true,
        )
    }
}

@Composable
internal fun Modifier.sheetHairline(): Modifier =
    sheetRule(color = StratumThemeAccessor.colors.borderSubtle, top = false)

/**
 * One rule line along the [top] edge, or the bottom edge when [top] is false.
 * The single drawBehind line implementation for sheet surfaces: hairlines
 * are the bottom-edge subtle case, the volume pivot's totals rule is the
 * top-edge strong case.
 */
@Composable
internal fun Modifier.sheetRule(
    color: Color,
    top: Boolean,
): Modifier {
    val width = StratumThemeAccessor.shapes.borderWidth
    return drawBehind {
        val stroke = width.toPx()
        val y = if (top) stroke / 2f else size.height - stroke / 2f
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = stroke,
        )
    }
}

/** Shared band when every set matches; otherwise the first set's band. */
internal fun sheetRepLabel(sets: List<EditSetState>): String {
    val first = sets.firstOrNull() ?: return SHEET_EMPTY_CELL
    return "${first.repMin}$REP_RANGE_DASH${first.repMax}"
}

internal fun sheetRirLabel(sets: List<EditSetState>): String {
    val first = sets.firstOrNull() ?: return SHEET_EMPTY_CELL
    return formatRirLabel(first.rirGoal)
}

internal fun sheetRestLabel(sets: List<EditSetState>): String {
    val first = sets.firstOrNull() ?: return SHEET_EMPTY_CELL
    return formatRestDisplay(first.restSeconds)
}

private const val SHEET_WORKOUT_HEADER_KEY_PREFIX = "header-"

/** Matches [StratumIconButton]'s 40dp container so lift columns stay under the header. */
internal val ProgramSheetLiftActionsSlot = 40.dp
private const val LIFT_COLUMN_WEIGHT = 0.40f
private const val SETS_COLUMN_WEIGHT = 0.12f
private const val REPS_COLUMN_WEIGHT = 0.16f
private const val RIR_COLUMN_WEIGHT = 0.12f
private const val REST_COLUMN_WEIGHT = 0.20f
private const val ADD_DAY_LAYOUT_FRAMES = 2
private const val REP_RANGE_DASH = "–"
private const val SHEET_EMPTY_CELL = "—"
