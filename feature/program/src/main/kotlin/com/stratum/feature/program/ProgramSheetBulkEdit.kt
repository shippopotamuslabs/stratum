package com.stratum.feature.program

import com.stratum.feature.program.ui.EditLiftState
import com.stratum.feature.program.ui.EditSetState
import com.stratum.feature.program.ui.EditWorkoutUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One selected sheet lift. Order in [ProgramSheetSelection.ordered] is selection order.
 * Not stored on [CreateProgramUiState] and not read by [toDomainProgram].
 */
internal data class ProgramSheetLiftRef(
    val dayId: String,
    val workoutLiftId: String,
)

/**
 * [active] is separate from [ordered] so select mode can be on with nothing checked.
 * An empty list is also the idle state.
 */
internal data class ProgramSheetSelection(
    val active: Boolean = false,
    val ordered: List<ProgramSheetLiftRef> = emptyList(),
)

internal data class ProgramSheetBulkEdit(
    val days: List<CreateProgramDayUi>,
    val undo: Map<String, List<EditLiftState>>,
    val selection: List<ProgramSheetLiftRef>,
)

/** Toggling off removes [target] and leaves the rest in order. Toggling on appends. */
internal fun toggleProgramSheetSelection(
    ordered: List<ProgramSheetLiftRef>,
    target: ProgramSheetLiftRef,
): List<ProgramSheetLiftRef> {
    val index = ordered.indexOf(target)
    return if (index < 0) ordered + target else ordered.filterIndexed { position, _ -> position != index }
}

/** Drops refs whose lift is no longer on that day. Same list instance when nothing drops. */
internal fun pruneProgramSheetSelection(
    ordered: List<ProgramSheetLiftRef>,
    days: List<CreateProgramDayUi>,
): List<ProgramSheetLiftRef> {
    if (ordered.isEmpty()) return ordered
    val kept = ordered.filter { ref -> findProgramSheetLift(days, ref) != null }
    return if (kept.size == ordered.size) ordered else kept
}

internal fun findProgramSheetLift(
    days: List<CreateProgramDayUi>,
    ref: ProgramSheetLiftRef,
): EditLiftState? =
    days
        .firstOrNull { day -> day.id == ref.dayId }
        ?.editState
        ?.lifts
        ?.firstOrNull { lift -> lift.workoutLiftId == ref.workoutLiftId }

/**
 * Adds one set to each selected lift via [EditWorkoutLiftMutations.addSet].
 * A missing id is skipped. No selected lift returns [days].
 */
internal fun addSetToSelectedSheetLifts(
    days: List<CreateProgramDayUi>,
    selection: List<ProgramSheetLiftRef>,
): ProgramSheetBulkEdit =
    bulkOverSelection(
        days = days,
        selection = selection,
        applies = { _ -> true },
        undoDayIds = { present -> present.map { ref -> ref.dayId } },
        apply = { current, ref ->
            updateProgramDay(current, ref.dayId) { loaded ->
                EditWorkoutLiftMutations.addSet(loaded, ref.workoutLiftId)
            }
        },
        selectionAfter = { present, _ -> present },
    )

/**
 * Removes the last set by [EditSetState.setNumber] via [EditWorkoutLiftMutations.removeSet].
 * A 1-set lift is left as it is, including its set id. No removable set returns [days].
 */
internal fun removeSetFromSelectedSheetLifts(
    days: List<CreateProgramDayUi>,
    selection: List<ProgramSheetLiftRef>,
): ProgramSheetBulkEdit =
    bulkOverSelection(
        days = days,
        selection = selection,
        applies = { lift -> lift.sets.size > 1 },
        undoDayIds = { present -> present.map { ref -> ref.dayId } },
        apply = { current, ref ->
            updateProgramDay(current, ref.dayId) { loaded ->
                val lift = loaded.lifts.first { item -> item.workoutLiftId == ref.workoutLiftId }
                EditWorkoutLiftMutations.removeSet(loaded, lastSheetSet(lift).setId)
            }
        },
        selectionAfter = { _, next -> pruneProgramSheetSelection(selection, next) },
    )

/** Writes [rir] onto every set of each selected lift. Rep ranges stay. Backoff sets are included. */
internal fun setRirOnSelectedSheetLifts(
    days: List<CreateProgramDayUi>,
    selection: List<ProgramSheetLiftRef>,
    rir: Int,
): ProgramSheetBulkEdit =
    bulkOverSelection(
        days = days,
        selection = selection,
        applies = { _ -> true },
        undoDayIds = { present -> present.map { ref -> ref.dayId } },
        apply = { current, ref ->
            updateProgramDay(current, ref.dayId) { loaded ->
                loaded.copy(
                    lifts =
                        loaded.lifts.map { lift ->
                            if (lift.workoutLiftId != ref.workoutLiftId) {
                                lift
                            } else {
                                lift.copy(sets = lift.sets.map { set -> set.copy(rirGoal = rir) })
                            }
                        },
                    isDirty = true,
                )
            }
        },
        selectionAfter = { present, _ -> present },
    )

/** One band through [applyUserRepBand] per selected lift. Same helper as edit-workout setReps. */
internal fun setRepBandOnSelectedSheetLifts(
    days: List<CreateProgramDayUi>,
    selection: List<ProgramSheetLiftRef>,
    repMin: Int,
    repMax: Int,
): ProgramSheetBulkEdit =
    bulkOverSelection(
        days = days,
        selection = selection,
        applies = { _ -> true },
        undoDayIds = { present -> present.map { ref -> ref.dayId } },
        apply = { current, ref ->
            updateProgramDay(current, ref.dayId) { loaded ->
                loaded.copy(
                    lifts =
                        loaded.lifts.map { lift ->
                            if (lift.workoutLiftId != ref.workoutLiftId) {
                                lift
                            } else {
                                lift.copy(
                                    sets =
                                        applyUserRepBand(
                                            sets = lift.sets,
                                            scheme = lift.progressionScheme,
                                            repMin = repMin,
                                            repMax = repMax,
                                        ),
                                )
                            }
                        },
                    isDirty = true,
                )
            }
        },
        selectionAfter = { present, _ -> present },
    )

/**
 * Appends each selected lift, in selection order, at the end of [toDayId].
 * A rest day, a missing destination, or no selected lift returns [days].
 */
internal fun moveSelectedSheetLifts(
    days: List<CreateProgramDayUi>,
    selection: List<ProgramSheetLiftRef>,
    toDayId: String,
): ProgramSheetBulkEdit {
    val destination = days.firstOrNull { day -> day.id == toDayId }
    if (destination == null || destination.isRest || destination.editState == null) {
        return ProgramSheetBulkEdit(days, emptyMap(), pruneProgramSheetSelection(selection, days))
    }
    return bulkOverSelection(
        days = days,
        selection = selection,
        applies = { _ -> true },
        undoDayIds = { present -> present.map { ref -> ref.dayId } + toDayId },
        apply = { current, ref ->
            val targetSize = current.firstOrNull { day -> day.id == toDayId }?.editState?.lifts?.size
            if (targetSize == null) {
                current
            } else {
                moveProgramSheetLift(
                    days = current,
                    fromDayId = ref.dayId,
                    toDayId = toDayId,
                    workoutLiftId = ref.workoutLiftId,
                    targetPosition = targetSize,
                )
            }
        },
        selectionAfter = { present, _ ->
            present.map { ref -> ProgramSheetLiftRef(dayId = toDayId, workoutLiftId = ref.workoutLiftId) }
        },
    )
}

/** Removes each selected lift with [removeLiftAndCleanupSupersets]. No selected lift returns [days]. */
internal fun deleteSelectedSheetLifts(
    days: List<CreateProgramDayUi>,
    selection: List<ProgramSheetLiftRef>,
): ProgramSheetBulkEdit =
    bulkOverSelection(
        days = days,
        selection = selection,
        applies = { _ -> true },
        undoDayIds = { present -> present.map { ref -> ref.dayId } },
        apply = { current, ref ->
            updateProgramDay(current, ref.dayId) { loaded ->
                loaded.copy(
                    lifts = removeLiftAndCleanupSupersets(loaded.lifts, ref.workoutLiftId),
                    isDirty = true,
                )
            }
        },
        selectionAfter = { _, next -> pruneProgramSheetSelection(selection, next) },
    )

/**
 * Sheet multi-select. Not on [CreateProgramUiState].
 * Entering select mode puts a pending cut back before the mode turns on.
 */
internal class ProgramSheetSelectionStore(
    private val scope: CoroutineScope,
    private val uiState: StateFlow<CreateProgramUiState>,
    private val applyDays: (List<CreateProgramDayUi>) -> Unit,
    private val publishUndo: (message: String, undo: Map<String, List<EditLiftState>>) -> Unit,
    private val returnPendingCut: () -> Boolean,
) {
    private val _selection = MutableStateFlow(ProgramSheetSelection())
    val selection: StateFlow<ProgramSheetSelection> = _selection.asStateFlow()

    init {
        scope.launch {
            uiState
                .map { state -> state.days }
                .distinctUntilChanged()
                .collect { days ->
                    _selection.update { current ->
                        if (!current.active) {
                            current
                        } else {
                            val pruned = pruneProgramSheetSelection(current.ordered, days)
                            if (pruned === current.ordered) current else current.copy(ordered = pruned)
                        }
                    }
                }
        }
    }

    fun begin(initial: ProgramSheetLiftRef?) {
        if (!returnPendingCut()) return
        val ordered =
            if (initial == null) {
                emptyList()
            } else {
                pruneProgramSheetSelection(listOf(initial), uiState.value.days)
            }
        _selection.value = ProgramSheetSelection(active = true, ordered = ordered)
    }

    fun toggle(target: ProgramSheetLiftRef) {
        _selection.update { current ->
            if (!current.active) {
                current
            } else {
                current.copy(ordered = toggleProgramSheetSelection(current.ordered, target))
            }
        }
    }

    fun clear() {
        if (!_selection.value.active) return
        _selection.value = ProgramSheetSelection()
    }

    fun addSet(message: String) = edit(message, ::addSetToSelectedSheetLifts)

    fun removeSet(message: String) = edit(message, ::removeSetFromSelectedSheetLifts)

    fun setRir(rir: Int, message: String) =
        edit(message) { days, ordered -> setRirOnSelectedSheetLifts(days, ordered, rir) }

    fun setRepBand(repMin: Int, repMax: Int, message: String) =
        edit(message) { days, ordered ->
            setRepBandOnSelectedSheetLifts(days, ordered, repMin, repMax)
        }

    fun moveTo(toDayId: String, message: String) =
        edit(message) { days, ordered -> moveSelectedSheetLifts(days, ordered, toDayId) }

    fun delete(message: String) = edit(message, ::deleteSelectedSheetLifts)

    private fun edit(
        message: String,
        transform: (List<CreateProgramDayUi>, List<ProgramSheetLiftRef>) -> ProgramSheetBulkEdit,
    ) {
        val current = _selection.value
        if (!current.active) return
        val days = uiState.value.days
        val result = transform(days, current.ordered)
        if (result.days !== days) {
            publishUndo(message, result.undo)
            applyDays(result.days)
        }
        // After the day list, so a days collector cannot drop lifts that just moved.
        val live = _selection.value
        if (live.active && live.ordered != result.selection) {
            _selection.value = live.copy(ordered = result.selection)
        }
    }
}

private fun lastSheetSet(lift: EditLiftState): EditSetState =
    lift.sets.reduce { acc, set -> if (set.setNumber >= acc.setNumber) set else acc }

private fun snapshotSheetLifts(
    days: List<CreateProgramDayUi>,
    dayIds: Iterable<String>,
): Map<String, List<EditLiftState>> =
    dayIds.distinct().mapNotNull { dayId ->
        val lifts = days.firstOrNull { day -> day.id == dayId }?.editState?.lifts ?: return@mapNotNull null
        dayId to lifts
    }.toMap()

private fun bulkOverSelection(
    days: List<CreateProgramDayUi>,
    selection: List<ProgramSheetLiftRef>,
    applies: (EditLiftState) -> Boolean,
    undoDayIds: (List<ProgramSheetLiftRef>) -> Collection<String>,
    apply: (List<CreateProgramDayUi>, ProgramSheetLiftRef) -> List<CreateProgramDayUi>,
    selectionAfter: (present: List<ProgramSheetLiftRef>, next: List<CreateProgramDayUi>) -> List<ProgramSheetLiftRef>,
): ProgramSheetBulkEdit {
    val present =
        selection.filter { ref ->
            findProgramSheetLift(days, ref)?.let(applies) == true
        }
    if (present.isEmpty()) {
        return ProgramSheetBulkEdit(days, emptyMap(), pruneProgramSheetSelection(selection, days))
    }
    val undo = snapshotSheetLifts(days, undoDayIds(present))
    val next = present.fold(days, apply)
    if (next === days) {
        return ProgramSheetBulkEdit(days, emptyMap(), pruneProgramSheetSelection(selection, days))
    }
    return ProgramSheetBulkEdit(days = next, undo = undo, selection = selectionAfter(present, next))
}
