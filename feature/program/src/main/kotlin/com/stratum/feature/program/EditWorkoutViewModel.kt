package com.stratum.feature.program

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.program.ComputeWorkoutMuscleVolumeUseCase
import com.stratum.domain.program.EstimateWorkoutDurationMinutesUseCase
import com.stratum.domain.program.ObserveProgramByIdUseCase
import com.stratum.domain.program.ObserveProgramUseCase
import com.stratum.domain.program.Program
import com.stratum.domain.program.SaveWorkoutUseCase
import com.stratum.domain.program.mergeWorkout
import com.stratum.domain.program.Workout
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.training.AbandonTrainingSessionUseCase
import com.stratum.domain.training.ObserveCycleProgressUseCase
import com.stratum.domain.training.ProgramChangeEventPayload
import com.stratum.domain.training.AppendProgramChangeEventUseCase
import com.stratum.domain.training.ProgramChangeEventType
import com.stratum.domain.training.StartTrainingSessionUseCase
import com.stratum.domain.workout.ManageWorkoutOverlayUseCase
import com.stratum.domain.workout.SlotLiftPair
import com.stratum.domain.workout.WorkoutEditSession
import com.stratum.domain.workout.EditSessionEvent
import com.stratum.core.ui.component.EquipmentSwapOption
import com.stratum.core.ui.component.EquipmentSwapSheetState
import com.stratum.core.ui.component.SupersetLinkDirection
import com.stratum.core.ui.component.isLastInSupersetChain
import com.stratum.core.ui.component.isSupersetPairMember
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.EquipmentPickResult
import com.stratum.domain.lift.EquipmentPickAwaiter
import com.stratum.domain.lift.GetRecentlyUsedEquipmentForLiftUseCase
import com.stratum.domain.lift.ObserveEquipmentCatalogUseCase
import com.stratum.domain.lift.identity.LiftId
import com.stratum.feature.program.formatEstimatedWorkoutDuration
import com.stratum.feature.program.ui.DEFAULT_REST_SECONDS
import com.stratum.feature.program.ui.EditEquipmentSwapUi
import com.stratum.feature.program.ui.EditLiftState
import com.stratum.feature.program.ui.EditSetState
import com.stratum.feature.program.ui.EditWorkoutUiEvent
import com.stratum.feature.program.ui.EditWorkoutUiState
import com.stratum.feature.program.ui.PLACEHOLDER_LIFT_NAME
import com.stratum.feature.program.ui.SaveBlockedReason
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
// Start pipeline + set/superset/scheme mutations keep this façade large; start body lives in
// EditWorkoutStartCoordinator.
@Suppress("TooManyFunctions", "LargeClass")
class EditWorkoutViewModel @Inject constructor(
    private val observeProgram: ObserveProgramUseCase,
    private val observeProgramById: ObserveProgramByIdUseCase,
    private val observeLiftCatalog: ObserveLiftCatalogUseCase,
    private val saveWorkoutUseCase: SaveWorkoutUseCase,
    private val computeWorkoutMuscleVolume: ComputeWorkoutMuscleVolumeUseCase,
    private val estimateWorkoutDurationMinutes: EstimateWorkoutDurationMinutesUseCase,
    private val appendProgramChangeEvent: AppendProgramChangeEventUseCase,
    private val workoutEditSession: WorkoutEditSession,
    private val observeEquipmentCatalog: ObserveEquipmentCatalogUseCase,
    private val getEquipment: com.stratum.domain.lift.GetEquipmentUseCase,
    private val getRecentlyUsedEquipment: GetRecentlyUsedEquipmentForLiftUseCase,
    private val equipmentPickResult: EquipmentPickResult,
    startTrainingSession: StartTrainingSessionUseCase,
    observeCycleProgress: ObserveCycleProgressUseCase,
    abandonTrainingSession: AbandonTrainingSessionUseCase,
    manageWorkoutOverlay: ManageWorkoutOverlayUseCase,
) : ViewModel() {

    private val startCoordinator =
        EditWorkoutStartCoordinator(
            startTrainingSession = startTrainingSession,
            observeCycleProgress = observeCycleProgress,
            abandonTrainingSession = abandonTrainingSession,
            manageWorkoutOverlay = manageWorkoutOverlay,
        )

    // Declared above init: the pick collector launched below reads these eagerly.
    private val _equipmentSwap = MutableStateFlow<EditEquipmentSwapUi?>(null)
    val equipmentSwap: StateFlow<EditEquipmentSwapUi?> = _equipmentSwap.asStateFlow()

    /** Create-new arming: target card + pick generation snapshot (see [beginCreateEquipment]). */
    private val pickAwaiter = EquipmentPickAwaiter(equipmentPickResult)

    init {
        viewModelScope.launch {
            workoutEditSession.events.collect { event ->
                when (event) {
                    is EditSessionEvent.LiftSelected -> {
                        if (event.workoutId == targetWorkoutId.value) {
                            handleLiftSelected(event.workoutLiftId, event.liftId)
                        }
                    }
                    is EditSessionEvent.LiftsSelected -> {
                        if (event.workoutId == targetWorkoutId.value) {
                            handleLiftsSelected(event.liftIds, event.asSuperset)
                        }
                    }
                    is EditSessionEvent.LiftsReordered -> {
                        if (event.workoutId == targetWorkoutId.value) {
                            handleLiftsReordered(event.orderedWorkoutLiftIds)
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            equipmentPickResult.result.collect { pick ->
                pickAwaiter.onPick(pick, _equipmentSwap.value?.workoutLiftId)?.let { (targetId, resolved) ->
                    resolved.equipmentId?.let { newId ->
                        setEquipment(targetId, newId.value)
                        _equipmentSwap.value = null
                    }
                }
            }
        }
    }
    private val targetWorkoutId = MutableStateFlow<String?>(null)
    private val hasSnapshot = MutableStateFlow(false)

    private val _uiState = MutableStateFlow<EditWorkoutUiState>(EditWorkoutUiState.Loading)
    val uiState: StateFlow<EditWorkoutUiState> = _uiState.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    fun userMessageShown() {
        _userMessage.value = null
    }

    private val _events = Channel<EditWorkoutUiEvent>(Channel.BUFFERED)
    val events: Flow<EditWorkoutUiEvent> = _events.receiveAsFlow()

    private val _isStarting = MutableStateFlow(false)
    /** True while a start (save/abandon/session) is in flight — disable Start chrome. */
    val isStarting: StateFlow<Boolean> = _isStarting.asStateFlow()

    private var catalogLifts: List<Lift> = emptyList()
    private var equipmentCatalog: List<Equipment> = emptyList()

    /** Row state stashed per (slot, lift) pair when a lift is swapped out (STR-127). */
    private data class SwappedEditLiftStash(
        val pinnedNote: String?,
        val equipmentId: String?,
        val equipmentName: String?,
    )

    private val liftSwapStash = mutableMapOf<SlotLiftPair, SwappedEditLiftStash>()
    private var programSnapshot: Program? = null
    private var baselineWorkout: Workout? = null
    /** When set, loads/saves that program (library or active); null = active only. */
    private var scopedProgramId: ProgramId? = null
    private var loadJob: Job? = null
    private var startJob: Job? = null

    fun initialize(workoutId: String, programId: String? = null) {
        if (targetWorkoutId.value == workoutId && hasSnapshot.value && scopedProgramId?.value == programId) {
            return
        }
        targetWorkoutId.value = workoutId
        scopedProgramId = programId?.let { ProgramId(it) }
        hasSnapshot.value = false
        _uiState.value = EditWorkoutUiState.Loading
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                combine(
                    editableProgramFlow(scopedProgramId),
                    observeLiftCatalog(),
                    observeEquipmentCatalog(),
                ) { program, lifts, equipment ->
                    Triple(program, lifts, equipment)
                }.collect { (program, lifts, equipment) ->
                    catalogLifts = lifts
                    equipmentCatalog = equipment
                    refreshOpenSheetCatalog()
                    if (hasSnapshot.value) {
                        // Active edit session exists. Baseline updates from Room are ignored,
                        // but equipment display names re-join so renames don't go stale.
                        rejoinEquipmentNames()
                        return@collect
                    }
                    programSnapshot = program
                    val liftNamesById = lifts.associate { lift -> lift.id.value to lift.name }
                    val workout =
                        program?.workouts?.firstOrNull { candidate -> candidate.id.value == workoutId }
                    baselineWorkout = workout
                    if (program == null || workout == null) {
                        _uiState.value = EditWorkoutUiState.NotFound
                        return@collect
                    }
                    val compoundByLiftId = catalogLifts.associate { lift -> lift.id.value to lift.isCompound }
                    // STR-105 Scope 5: slots referencing tombstoned equipment
                    // (legacy orphans) still show the retained name.
                    val catalogNames = equipment.associate { item -> item.id.value to item.name }
                    val referencedIds =
                        workout.workoutLifts.mapNotNull { it.equipmentId?.value }.toSet()
                    val retainedNames =
                        referencedIds
                            .filterNot { it in catalogNames }
                            .mapNotNull { id ->
                                getEquipment(com.stratum.domain.lift.identity.EquipmentId(id))
                                    ?.let { row -> id to row.name }
                            }
                            .toMap()
                    val equipmentNamesById = catalogNames + retainedNames
                    val loaded =
                        workout.toEditWorkoutUiState(liftNamesById, compoundByLiftId, equipmentNamesById)
                            .markSaveState()
                    _uiState.value = applyDerivedMetrics(loaded)
                    hasSnapshot.value = true
                }
            }
    }

    private fun editableProgramFlow(programId: ProgramId?): Flow<Program?> =
        if (programId == null) {
            observeProgram()
        } else {
            observeProgramById(programId)
        }

    fun saveWorkout(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            when (persistWorkoutIfNeeded()) {
                EditWorkoutStartCoordinator.PersistResult.Success -> onComplete()
                EditWorkoutStartCoordinator.PersistResult.Failed -> Unit
                EditWorkoutStartCoordinator.PersistResult.Busy ->
                    _userMessage.value = EditWorkoutStartCoordinator.MSG_START_BUSY
                EditWorkoutStartCoordinator.PersistResult.Blocked ->
                    _userMessage.value = EditWorkoutStartCoordinator.MSG_START_BLOCKED_PLACEHOLDER
            }
        }
    }

    /**
     * Entry after UI committed the dock and resolved dirty edits (or state is clean).
     * May emit [EditWorkoutUiEvent.ShowActiveSessionDiscardDialog].
     */
    fun requestStartWorkout() {
        launchStart(discardActiveFirst = false, resolveDirty = null)
    }

    /**
     * After dirty Confirm/Discard dialog.
     * @param saveEdits true = persist first; false = restore baseline without saving.
     */
    fun startAfterDirtyResolution(saveEdits: Boolean) {
        launchStart(
            discardActiveFirst = false,
            resolveDirty =
                if (saveEdits) {
                    EditWorkoutStartCoordinator.DirtyResolution.Save
                } else {
                    EditWorkoutStartCoordinator.DirtyResolution.DiscardLocal
                },
        )
    }

    /** User confirmed abandon of the live session. */
    fun confirmDiscardActiveAndStart() {
        launchStart(discardActiveFirst = true, resolveDirty = null)
    }

    private fun launchStart(
        discardActiveFirst: Boolean,
        resolveDirty: EditWorkoutStartCoordinator.DirtyResolution?,
    ) {
        if (startJob?.isActive == true) {
            _userMessage.value = EditWorkoutStartCoordinator.MSG_START_BUSY
            return
        }
        startJob =
            viewModelScope.launch {
                _isStarting.value = true
                try {
                    applyStartOutcome(
                        startCoordinator.run(
                            uiState = _uiState.value,
                            scopedProgramId = scopedProgramId,
                            discardActiveFirst = discardActiveFirst,
                            resolveDirty = resolveDirty,
                            persistIfDirty = { persistWorkoutIfNeeded() },
                            discardLocalEdits = { discardLocalEdits() },
                        ),
                    )
                } finally {
                    _isStarting.value = false
                }
            }
    }

    private suspend fun applyStartOutcome(outcome: EditWorkoutStartCoordinator.StartOutcome) {
        when (outcome) {
            EditWorkoutStartCoordinator.StartOutcome.LibraryScoped,
            EditWorkoutStartCoordinator.StartOutcome.NotReady,
            EditWorkoutStartCoordinator.StartOutcome.PersistFailed,
            -> Unit
            EditWorkoutStartCoordinator.StartOutcome.NeedsActiveSessionDiscard ->
                _events.send(EditWorkoutUiEvent.ShowActiveSessionDiscardDialog)
            is EditWorkoutStartCoordinator.StartOutcome.Navigate ->
                _events.send(EditWorkoutUiEvent.NavigateToWorkout(outcome.workoutRouteId))
            is EditWorkoutStartCoordinator.StartOutcome.UserMessage ->
                _userMessage.value = outcome.message
        }
    }

    /** Reset in-memory editor to the last persisted baseline (unsaved edits dropped). */
    private fun discardLocalEdits() {
        liftSwapStash.clear()
        val baseline = baselineWorkout ?: return
        val restored =
            baseline
                .toEditWorkoutUiState(
                    catalogLifts.associate { lift -> lift.id.value to lift.name },
                    catalogLifts.associate { lift -> lift.id.value to lift.isCompound },
                )
                .copy(isDirty = false, isSaving = false)
                .markSaveState()
        _uiState.value = applyDerivedMetrics(restored)
    }

    private suspend fun persistWorkoutIfNeeded(): EditWorkoutStartCoordinator.PersistResult {
        val loaded = _uiState.value as? EditWorkoutUiState.Loaded
        val baseline = baselineWorkout
        if (loaded == null || baseline == null) {
            return EditWorkoutStartCoordinator.PersistResult.Failed
        }
        EditWorkoutStartCoordinator.persistResultFor(loaded, isSaving = loaded.isSaving)?.let {
            return it
        }
        if (!loaded.isDirty) {
            return EditWorkoutStartCoordinator.PersistResult.Success
        }
        // STR-105 Scope 8: never persist a slot pointing at tombstoned equipment.
        val updatedWorkoutPreview = loaded.lifts.toWorkout(baseline, WorkoutId(loaded.workoutId))
        val orphanedSlots =
            updatedWorkoutPreview.workoutLifts.filter { slot ->
                slot.equipmentId?.let { id -> getEquipment(id)?.isTombstoned } == true
            }
        if (orphanedSlots.isNotEmpty()) {
            val names =
                orphanedSlots.joinToString(", ") { slot ->
                    catalogLifts.firstOrNull { it.id == slot.liftId }?.name
                        ?: slot.liftId.value
                }
            _userMessage.value =
                "Deleted equipment is still assigned to $names. Pick a live equipment first."
            return EditWorkoutStartCoordinator.PersistResult.Failed
        }
        _uiState.value = loaded.copy(isSaving = true)
        val updatedWorkout = loaded.lifts.toWorkout(baseline, WorkoutId(loaded.workoutId))
        val result =
            saveWorkoutUseCase(
                workoutId = WorkoutId(loaded.workoutId),
                updatedWorkout = updatedWorkout,
                programId = scopedProgramId ?: programSnapshot?.id,
            )
        return if (result.isSuccess) {
            // Slice 8 (D11): append SchemeChanged / LiftSwapped for baseline diffs.
            appendSchemeChangedEvents(baseline, updatedWorkout)
            appendLiftSwappedEvents(baseline, updatedWorkout)
            baselineWorkout = updatedWorkout
            programSnapshot =
                programSnapshot?.mergeWorkout(WorkoutId(loaded.workoutId), updatedWorkout)
            val savedState =
                updatedWorkout
                    .toEditWorkoutUiState(
                        catalogLifts.associate { lift -> lift.id.value to lift.name },
                        catalogLifts.associate { lift -> lift.id.value to lift.isCompound },
                    )
                    .copy(isDirty = false, isSaving = false)
                    .markSaveState()
            _uiState.value = applyDerivedMetrics(savedState)
            EditWorkoutStartCoordinator.PersistResult.Success
        } else {
            _uiState.value = loaded.copy(isSaving = false)
            _userMessage.value = MSG_SAVE_FAILED
            EditWorkoutStartCoordinator.PersistResult.Failed
        }
    }

    private companion object {
        const val MSG_SAVE_FAILED = "Couldn't save workout. Try again."
    }

    private suspend fun appendSchemeChangedEvents(
        baseline: Workout,
        updated: Workout,
    ) {
        val program = programSnapshot ?: return
        val baselineById = baseline.workoutLifts.associateBy { it.id }
        val changed = updated.workoutLifts.mapNotNull { updatedLift ->
            val baselineLift = baselineById[updatedLift.id]
            if (baselineLift != null && baselineLift.progressionScheme != updatedLift.progressionScheme) {
                baselineLift.progressionScheme to updatedLift
            } else {
                null
            }
        }
        for ((previous, updatedLift) in changed) {
            appendProgramChangeEvent(
                com.stratum.domain.training.ProgramChangeEvent(
                    id = com.stratum.domain.training.identity.ProgramChangeEventId(
                        java.util.UUID.randomUUID().toString(),
                    ),
                    programId = program.id,
                    eventType = ProgramChangeEventType.SCHEME_CHANGED,
                    occurredAt = java.time.Instant.now(),
                    payload = ProgramChangeEventPayload.SchemeChanged(
                        workoutLiftId = updatedLift.id,
                        liftId = updatedLift.liftId,
                        previous = previous,
                        current = updatedLift.progressionScheme,
                    ),
                ),
            )
        }
    }

    private suspend fun appendLiftSwappedEvents(
        baseline: Workout,
        updated: Workout,
    ) {
        val program = programSnapshot ?: return
        val baselineById = baseline.workoutLifts.associateBy { it.id }
        val changed = updated.workoutLifts.mapNotNull { updatedLift ->
            val baselineLift = baselineById[updatedLift.id]
            if (baselineLift != null && baselineLift.liftId != updatedLift.liftId) {
                baselineLift.liftId to updatedLift
            } else {
                null
            }
        }
        for ((previous, updatedLift) in changed) {
            appendProgramChangeEvent(
                com.stratum.domain.training.ProgramChangeEvent(
                    id = com.stratum.domain.training.identity.ProgramChangeEventId(
                        java.util.UUID.randomUUID().toString(),
                    ),
                    programId = program.id,
                    eventType = ProgramChangeEventType.LIFT_SWAPPED,
                    occurredAt = java.time.Instant.now(),
                    payload = ProgramChangeEventPayload.LiftSwapped(
                        workoutLiftId = updatedLift.id,
                        previousLiftId = previous,
                        newLiftId = updatedLift.liftId,
                    ),
                ),
            )
        }
    }

    fun startReorderLifts() {
        val loaded = _uiState.value as? EditWorkoutUiState.Loaded ?: return
        val items = loaded.lifts.map { com.stratum.domain.workout.ReorderLiftItem(it.workoutLiftId, it.name) }
        workoutEditSession.setReorderLifts(items)
    }

    private fun handleLiftsReordered(orderedWorkoutLiftIds: List<String>) {
        updateLoaded { loaded ->
            val liftById = loaded.lifts.associateBy { it.workoutLiftId }
            val reordered = orderedWorkoutLiftIds.mapNotNull { liftById[it] }
                .mapIndexed { index, lift -> lift.copy(position = index) }
            loaded.copy(lifts = reordered)
        }
    }

    private fun handleLiftSelected(workoutLiftId: String?, liftId: String) {
        val catalog = catalogLifts.firstOrNull { it.id.value == liftId }
        val liftName = catalog?.name ?: PLACEHOLDER_LIFT_NAME
        val isCompound = catalog?.isCompound ?: false
        val restSeconds = catalog?.resolvedDefaultRestSeconds() ?: DEFAULT_REST_SECONDS
        updateLoaded { loaded ->
            if (workoutLiftId == null) {
                val position = loaded.lifts.size
                val newWorkoutLiftId = newWorkoutLiftId()
                val newLift = EditLiftState(
                    workoutLiftId = newWorkoutLiftId,
                    liftId = liftId,
                    name = liftName,
                    position = position,
                    sets = listOf(defaultEditSetState(setNumber = 1, restSeconds = restSeconds)),
                    isCompound = isCompound,
                    progressionScheme = ProgressionScheme.Double,
                    supersetGroupId = null,
                    useRestTimerBetweenLifts = false,
                )
                loaded.copy(lifts = loaded.lifts + newLift)
            } else {
                // Swap: replace identity and apply the new lift's default rest to all sets.
                // STR-127: pinned note + equipment are (slot, lift) pair state — stash
                // the outgoing pair's values, restore the incoming pair's stash when
                // it was swapped out earlier in this draft, and otherwise reset
                // equipment to the incoming lift's catalog default (never carry the
                // old lift's assignment or note onto the new lift).
                val updatedLifts = loaded.lifts.map { lift ->
                    if (lift.workoutLiftId == workoutLiftId) {
                        if (lift.liftId != liftId) {
                            liftSwapStash[
                                SlotLiftPair(WorkoutLiftId(workoutLiftId), LiftId(lift.liftId))
                            ] =
                                SwappedEditLiftStash(
                                    pinnedNote = lift.pinnedNote,
                                    equipmentId = lift.equipmentId,
                                    equipmentName = lift.equipmentName,
                                )
                        }
                        val incoming =
                            liftSwapStash[SlotLiftPair(WorkoutLiftId(workoutLiftId), LiftId(liftId))]
                        val defaultEquipmentId = catalog?.equipmentId?.value
                        val defaultEquipmentName =
                            defaultEquipmentId?.let { id ->
                                equipmentCatalog.firstOrNull { it.id.value == id }?.name
                            }
                        lift.copy(
                            liftId = liftId,
                            name = liftName,
                            isCompound = isCompound,
                            sets = lift.sets.map { set -> set.copy(restSeconds = restSeconds) },
                            pinnedNote = incoming?.pinnedNote,
                            equipmentId = incoming?.equipmentId ?: defaultEquipmentId,
                            equipmentName = incoming?.equipmentName ?: defaultEquipmentName,
                        )
                    } else {
                        lift
                    }
                }
                loaded.copy(lifts = updatedLifts)
            }
        }
    }

    private fun handleLiftsSelected(liftIds: List<String>, asSuperset: Boolean) {
        if (liftIds.isEmpty()) return
        updateLoaded { loaded ->
            val groupId =
                if (asSuperset && liftIds.size >= 2) {
                    newSupersetGroupId()
                } else {
                    null
                }
            val startPosition = loaded.lifts.size
            val newLifts =
                liftIds.mapIndexed { index, liftId ->
                    val catalog = catalogLifts.firstOrNull { it.id.value == liftId }
                    val restSeconds = catalog?.resolvedDefaultRestSeconds() ?: DEFAULT_REST_SECONDS
                    EditLiftState(
                        workoutLiftId = newWorkoutLiftId(),
                        liftId = liftId,
                        name = catalog?.name ?: PLACEHOLDER_LIFT_NAME,
                        position = startPosition + index,
                        sets = listOf(defaultEditSetState(setNumber = 1, restSeconds = restSeconds)),
                        isCompound = catalog?.isCompound ?: false,
                        progressionScheme = ProgressionScheme.Double,
                        supersetGroupId = groupId,
                        useRestTimerBetweenLifts = false,
                    )
                }
            loaded.copy(lifts = loaded.lifts + newLifts)
        }
    }

    fun addSet(workoutLiftId: String) {
        updateLoaded { loaded ->
            loaded.copy(
                lifts =
                    loaded.lifts.map { lift ->
                        if (lift.workoutLiftId != workoutLiftId) {
                            lift
                        } else {
                            val restSeconds =
                                lift.sets.lastOrNull()?.restSeconds
                                    ?: catalogLifts
                                        .firstOrNull { it.id.value == lift.liftId }
                                        ?.resolvedDefaultRestSeconds()
                                    ?: DEFAULT_REST_SECONDS
                            val newSet =
                                newEditSetShell(
                                    setNumber = lift.sets.size + 1,
                                    restSeconds = restSeconds,
                                )
                            lift.copy(
                                sets = normalizeSetsForScheme(
                                    lift.sets + newSet,
                                    lift.progressionScheme,
                                ),
                            )
                        }
                    },
            )
        }
    }

    fun addLift(onNavigateToPicker: (workoutId: String, workoutLiftId: String?, liftId: String?, liftName: String?) -> Unit) {
        val loaded = _uiState.value as? EditWorkoutUiState.Loaded ?: return
        onNavigateToPicker(loaded.workoutId, null, null, null)
    }

    fun removeLift(workoutLiftId: String) {
        updateLoaded { loaded -> EditWorkoutLiftMutations.removeLift(loaded, workoutLiftId) }
    }

    fun removeSet(setId: String) {
        updateLoaded { loaded ->
            loaded.copy(
                lifts =
                    loaded.lifts.map { lift ->
                        val removed = lift.sets.firstOrNull { set -> set.setId == setId }
                        val cascadeIds = removeSetCascadeIds(lift.sets, setId)
                        val updatedSets = lift.sets.filterNot { set -> set.setId in cascadeIds }
                        if (removed == null || updatedSets.size == lift.sets.size) {
                            lift
                        } else {
                            // If the removed row carried the only positive band (Double top),
                            // pass it as fallback so promote-to-top does not invent 8–12.
                            val fallbackMin =
                                if (removed.repMin > 0 || removed.repMax > 0) removed.repMin else 0
                            val fallbackMax =
                                if (removed.repMin > 0 || removed.repMax > 0) removed.repMax else 0
                            lift.copy(
                                sets =
                                    normalizeSetsForScheme(
                                        sets = updatedSets,
                                        scheme = lift.progressionScheme,
                                        fallbackRepMin = fallbackMin,
                                        fallbackRepMax = fallbackMax,
                                    ),
                            )
                        }
                    },
            )
        }
    }

    fun setRestSeconds(setId: String?, workoutLiftId: String?, restSeconds: Int) {
        val clamped = restSeconds.coerceAtLeast(0)
        if (workoutLiftId != null) {
            updateLoaded { loaded ->
                loaded.copy(
                    lifts = loaded.lifts.map { lift ->
                        if (lift.workoutLiftId != workoutLiftId) {
                            lift
                        } else {
                            lift.copy(
                                sets = lift.sets.map { set -> set.copy(restSeconds = clamped) },
                            )
                        }
                    },
                )
            }
        } else if (setId != null) {
            updateSet(setId) { set -> set.copy(restSeconds = clamped) }
        }
    }

    /**
     * Changes the intensifier kind for a set (S / M / MM / D) and re-defaults rest edges.
     * Selecting MyoRepMatch materializes default match backoff template rows.
     */
    fun setSetKind(setId: String, kind: com.stratum.feature.program.ui.EditSetKind) {
        updateLoaded { loaded ->
            loaded.copy(
                lifts = loaded.lifts.map { lift ->
                    val setIndex = lift.sets.indexOfFirst { it.setId == setId }
                    if (setIndex < 0) return@map lift
                    val catalog = catalogLifts.firstOrNull { it.id.value == lift.liftId }
                    val defaultRest = catalog?.resolvedDefaultRestSeconds() ?: DEFAULT_REST_SECONDS
                    applySetKindToLift(
                        lift = lift,
                        setIndex = setIndex,
                        newKind = kind,
                        liftDefaultRestSeconds = defaultRest,
                    )
                },
            )
        }
    }

    /**
     * Sets how many match-backoff template rows follow a Myo-Rep Match activation.
     */
    fun setMatchBackoffCount(activationSetId: String, count: Int) {
        updateLoaded { loaded ->
            loaded.copy(
                lifts = loaded.lifts.map { lift ->
                    if (lift.sets.none { it.setId == activationSetId }) return@map lift
                    val catalog = catalogLifts.firstOrNull { it.id.value == lift.liftId }
                    val defaultRest = catalog?.resolvedDefaultRestSeconds() ?: DEFAULT_REST_SECONDS
                    applyMatchBackoffCount(
                        lift = lift,
                        activationSetId = activationSetId,
                        count = count,
                        liftDefaultRestSeconds = defaultRest,
                    )
                },
            )
        }
    }

    /**
     * Updates the rep range for a set that may edit ranges under the lift's scheme.
     *
     * - **Double:** only the top set; backoff sets are RIR-only (no-op).
     * - **Linear:** any set; the shared band is written to every set on the lift.
     *
     * Clamping and scheme fan-out go through [ProgressionRepRangePolicy.applyBand] so
     * `max ≥ min` and Linear/Double shape stay consistent with generation.
     */
    fun setReps(setId: String, min: Int? = null, max: Int? = null) {
        val loaded = _uiState.value as? EditWorkoutUiState.Loaded ?: return
        val ownerLiftId =
            loaded.lifts.firstOrNull { lift -> lift.sets.any { it.setId == setId } }
                ?.workoutLiftId
                ?: return

        updateLoaded { state ->
            val lift = state.lifts.firstOrNull { it.workoutLiftId == ownerLiftId } ?: return@updateLoaded state
            val target = lift.sets.firstOrNull { it.setId == setId } ?: return@updateLoaded state
            if (!showsRepRange(lift.progressionScheme, target)) return@updateLoaded state

            val nextMin = min ?: target.repMin
            val nextMax = max ?: target.repMax
            lift.copy(
                sets =
                    applyUserRepBand(
                        sets = lift.sets,
                        scheme = lift.progressionScheme,
                        repMin = nextMin,
                        repMax = nextMax,
                    ),
            ).let { updatedLift ->
                state.copy(
                    lifts = state.lifts.map { if (it.workoutLiftId == ownerLiftId) updatedLift else it },
                )
            }
        }
    }

    fun setRirGoal(setId: String, rirGoal: Int) {
        updateSet(setId) { set -> set.copy(rirGoal = rirGoal) }
    }

    fun setUseRestTimerBetweenLifts(workoutLiftId: String, enabled: Boolean) {
        updateLoaded { loaded ->
            loaded.copy(
                lifts =
                    loaded.lifts.map { lift ->
                        if (lift.workoutLiftId == workoutLiftId) {
                            lift.copy(useRestTimerBetweenLifts = enabled)
                        } else {
                            lift
                        }
                    },
            )
        }
    }

    /**
     * Slice 8 (D11) — per-lift progression-scheme toggle. Only compound lifts may move off
     * `Double`; isolation lifts are pinned (no-op). On save, a `SchemeChanged` event is
     * appended when the scheme actually changed (see [saveWorkout]).
     *
     * A no-op toggle (isolation lift, or same scheme) re-emits the current state so
     * observers see a value, but does NOT mark the workout dirty — `isDirty` only flips
     * when the scheme actually changes.
     */
    fun setProgressionScheme(workoutLiftId: String, scheme: ProgressionScheme) {
        val current = _uiState.value as? EditWorkoutUiState.Loaded ?: return
        val lift = current.lifts.firstOrNull { it.workoutLiftId == workoutLiftId } ?: return
        // Isolation lifts are pinned to Double; same-scheme is a no-op. Neither marks
        // dirty — isDirty only flips when the scheme actually changes.
        if (!lift.isCompound || lift.progressionScheme == scheme) return
        updateLoaded { loaded ->
            loaded.copy(
                lifts =
                    loaded.lifts.map { l ->
                        if (l.workoutLiftId != workoutLiftId) {
                            l
                        } else {
                            l.copy(
                                progressionScheme = scheme,
                                // Redistribute ranges + isTopSet for the new scheme.
                                sets = applyProgressionSchemeToSets(l.sets, scheme),
                            )
                        }
                    },
            )
        }
    }

    /**
     * Template equipment assignment for one lift. Marks the workout dirty via
     * [updateLoaded]; persisted on save through `toWorkout`.
     */
    fun setEquipment(workoutLiftId: String, equipmentId: String) {
        val name = equipmentCatalog.firstOrNull { it.id.value == equipmentId }?.name
        updateLoaded { loaded ->
            loaded.copy(
                lifts =
                    loaded.lifts.map { lift ->
                        if (lift.workoutLiftId == workoutLiftId) {
                            lift.copy(equipmentId = equipmentId, equipmentName = name)
                        } else {
                            lift
                        }
                    },
            )
        }
    }

    /** Opens the equipment swap sheet for one template lift card; recents load async. */
    fun openEquipmentSwap(workoutLiftId: String) {
        val loaded = _uiState.value as? EditWorkoutUiState.Loaded ?: return
        val lift = loaded.lifts.firstOrNull { it.workoutLiftId == workoutLiftId } ?: return
        val catalog = equipmentCatalog
        _equipmentSwap.value =
            EditEquipmentSwapUi(
                workoutLiftId = workoutLiftId,
                liftId = lift.liftId,
                liftName = lift.name,
                sheet =
                    EquipmentSwapSheetState(
                        current =
                            lift.equipmentId?.let { id ->
                                catalog.firstOrNull { it.id.value == id }?.toSwapOption()
                            },
                        recent = emptyList(),
                        all = catalog.map { it.toSwapOption() },
                    ),
            )
        viewModelScope.launch {
            val recent = getRecentlyUsedEquipment(LiftId(lift.liftId)).map { it.toSwapOption() }
            _equipmentSwap.value?.let { open ->
                if (open.workoutLiftId == workoutLiftId) {
                    _equipmentSwap.value = open.copy(sheet = open.sheet.copy(recent = recent))
                }
            }
        }
    }

    fun setEquipmentSwapQuery(query: String) {
        _equipmentSwap.value?.let { open ->
            _equipmentSwap.value = open.copy(sheet = open.sheet.copy(query = query))
        }
    }

    fun closeEquipmentSwap() {
        pickAwaiter.disarm()
        _equipmentSwap.value = null
    }

    /**
     * Arms the create-new return path for the open sheet's lift. Call before navigating
     * to Manage Equipment in selection mode; the created piece is applied to the
     * template draft when the pick publishes.
     */
    fun beginCreateEquipment() {
        val target = _equipmentSwap.value ?: return
        pickAwaiter.arm(target.workoutLiftId)
    }

    /** Applies the selection to the template draft + closes. */
    fun selectSwapEquipment(optionId: String) {
        val target = _equipmentSwap.value ?: return
        pickAwaiter.disarm()
        setEquipment(target.workoutLiftId, optionId)
        _equipmentSwap.value = null
    }

    /**
     * Re-resolves equipment display names on catalog emissions without touching draft
     * state: bypasses [updateLoaded] (which marks dirty) and skips no-op writes.
     */
    private fun rejoinEquipmentNames() {
        val current = _uiState.value as? EditWorkoutUiState.Loaded ?: return
        val names = equipmentCatalog.associate { it.id.value to it.name }
        val relabeled =
            current.lifts.map { lift -> lift.copy(equipmentName = lift.equipmentId?.let(names::get)) }
        if (relabeled.map { it.equipmentName } == current.lifts.map { it.equipmentName }) return
        _uiState.value = current.copy(lifts = relabeled)
    }

    private fun refreshOpenSheetCatalog() {
        _equipmentSwap.value?.let { open ->
            _equipmentSwap.value =
                open.copy(
                    sheet = open.sheet.copy(all = equipmentCatalog.map { it.toSwapOption() }),
                )
        }
    }

    private fun Equipment.toSwapOption(): EquipmentSwapOption =
        EquipmentSwapOption(id = id.value, name = name)

    private fun updateSet(setId: String, transform: (EditSetState) -> EditSetState) {
        updateLoaded { loaded ->
            loaded.copy(
                lifts =
                    loaded.lifts.map { lift ->
                        lift.copy(
                            sets =
                                lift.sets.map { set ->
                                    if (set.setId == setId) {
                                        transform(set)
                                    } else {
                                        set
                                    }
                                },
                        )
                    },
            )
        }
    }

    fun removeFromSuperset(workoutLiftId: String) {
        updateLoaded { loaded -> EditWorkoutLiftMutations.removeFromSuperset(loaded, workoutLiftId) }
    }

    fun linkSuperset(
        workoutLiftId: String,
        direction: SupersetLinkDirection,
    ) {
        updateLoaded { loaded ->
            EditWorkoutLiftMutations.linkSuperset(
                loaded = loaded,
                workoutLiftId = workoutLiftId,
                direction = direction,
                nextGroupId = ::newSupersetGroupId,
            )
        }
    }

    private inline fun updateLoaded(transform: (EditWorkoutUiState.Loaded) -> EditWorkoutUiState.Loaded) {
        val current = _uiState.value
        if (current is EditWorkoutUiState.Loaded) {
            _uiState.value = applyDerivedMetrics(transform(current).markDirty())
        }
    }

    private fun EditWorkoutUiState.Loaded.markDirty(): EditWorkoutUiState.Loaded =
        copy(isDirty = true).markSaveState()

    private fun EditWorkoutUiState.Loaded.markSaveState(): EditWorkoutUiState.Loaded {
        val catalogIds = catalogLifts.map { lift -> lift.id.value }.toSet()
        val blocked =
            if (lifts.hasPlaceholderLift(catalogIds)) {
                SaveBlockedReason.PlaceholderLift
            } else {
                null
            }
        return copy(saveBlockedReason = blocked)
    }

    /**
     * Explicit hard reset for tests. Production leave relies on entry-scoped ViewModel
     * destruction — do **not** call from composition dispose (Nav3 covers drop the route while
     * the VM stays alive for Lift Swap / Reorder Lifts).
     */
    internal fun clearSession() {
        loadJob?.cancel()
        loadJob = null
        hasSnapshot.value = false
        targetWorkoutId.value = null
        scopedProgramId = null
        programSnapshot = null
        baselineWorkout = null
        catalogLifts = emptyList()
        liftSwapStash.clear()
        _uiState.value = EditWorkoutUiState.Loading
    }

    private fun applyDerivedMetrics(state: EditWorkoutUiState): EditWorkoutUiState =
        when (state) {
            is EditWorkoutUiState.Loaded ->
                state.withDerivedMetrics(
                    liftCatalog = catalogLifts,
                    program = programSnapshot,
                    computeWorkoutMuscleVolume = computeWorkoutMuscleVolume,
                    estimateWorkoutDurationMinutes = estimateWorkoutDurationMinutes,
                )
            else -> state
        }
}
