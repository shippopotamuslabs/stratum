package com.stratum.feature.program

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.program.ComputeWorkoutMuscleVolumeUseCase
import com.stratum.domain.program.EstimateWorkoutDurationMinutesUseCase
import com.stratum.domain.program.ObserveProgramUseCase
import com.stratum.domain.program.Program
import com.stratum.domain.program.SaveWorkoutUseCase
import com.stratum.domain.program.mergeWorkout
import com.stratum.domain.program.Workout
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.training.ProgramChangeEventPayload
import com.stratum.domain.training.ProgramChangeEventRepository
import com.stratum.domain.training.ProgramChangeEventType
import com.stratum.domain.workout.WorkoutEditSession
import com.stratum.domain.workout.EditSessionEvent
import com.stratum.core.ui.component.SupersetLinkDirection
import com.stratum.core.ui.component.isLastInSupersetChain
import com.stratum.core.ui.component.isSupersetPairMember
import com.stratum.core.ui.component.linkSupersetWithAdjacent
import com.stratum.core.ui.component.unlinkLiftFromSuperset
import com.stratum.core.ui.component.cleanupSupersetGroups
import com.stratum.feature.program.formatEstimatedWorkoutDuration
import com.stratum.feature.program.ui.EditLiftState
import com.stratum.feature.program.ui.EditSetState
import com.stratum.feature.program.ui.EditWorkoutUiState
import com.stratum.feature.program.ui.PLACEHOLDER_LIFT_NAME
import com.stratum.feature.program.ui.SaveBlockedReason
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@HiltViewModel
// Slice 8 (D11): no AppendProgramChangeEventUseCase wrapper exists; SchemeChanged is
// appended directly via the repository port (spec API-contracts note).
@Suppress("ForbiddenRepositoryInViewModel")
class EditWorkoutViewModel @Inject constructor(
    private val observeProgram: ObserveProgramUseCase,
    private val observeLiftCatalog: ObserveLiftCatalogUseCase,
    private val saveWorkoutUseCase: SaveWorkoutUseCase,
    private val computeWorkoutMuscleVolume: ComputeWorkoutMuscleVolumeUseCase,
    private val estimateWorkoutDurationMinutes: EstimateWorkoutDurationMinutesUseCase,
    private val programChangeEventRepository: ProgramChangeEventRepository,
    private val workoutEditSession: WorkoutEditSession,
) : ViewModel() {

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
    }
    private val targetWorkoutId = MutableStateFlow<String?>(null)
    private val hasSnapshot = MutableStateFlow(false)

    private val _uiState = MutableStateFlow<EditWorkoutUiState>(EditWorkoutUiState.Loading)
    val uiState: StateFlow<EditWorkoutUiState> = _uiState.asStateFlow()

    private var catalogLifts: List<Lift> = emptyList()
    private var programSnapshot: Program? = null
    private var baselineWorkout: Workout? = null

    fun initialize(workoutId: String) {
        if (targetWorkoutId.value == workoutId && hasSnapshot.value) {
            return
        }
        targetWorkoutId.value = workoutId
        hasSnapshot.value = false
        _uiState.value = EditWorkoutUiState.Loading
        viewModelScope.launch {
            combine(
                observeProgram(),
                observeLiftCatalog(),
            ) { program, lifts ->
                program to lifts
            }.collect { (program, lifts) ->
                catalogLifts = lifts
                programSnapshot = program
                val liftNamesById = lifts.associate { lift -> lift.id.value to lift.name }
                val workout =
                    program?.workouts?.firstOrNull { workout -> workout.id.value == workoutId }
                baselineWorkout = workout

                when {
                    program == null || workout == null -> {
                        if (!hasSnapshot.value) {
                            _uiState.value =
                                if (program == null) {
                                    EditWorkoutUiState.NotFound
                                } else {
                                    EditWorkoutUiState.NotFound
                                }
                        }
                    }
                    !hasSnapshot.value -> {
                        val compoundByLiftId = catalogLifts.associate { lift -> lift.id.value to lift.isCompound }
                        val loaded = workout.toEditWorkoutUiState(liftNamesById, compoundByLiftId).markSaveState()
                        _uiState.value = applyDerivedMetrics(loaded)
                        hasSnapshot.value = true
                    }
                    else -> {
                        // Active edit session exists. Baseline updates from Room are ignored to keep in-memory edits.
                    }
                }
            }
        }
    }

    fun saveWorkout(onComplete: () -> Unit = {}) {
        val loaded = _uiState.value as? EditWorkoutUiState.Loaded ?: return
        val baseline = baselineWorkout ?: return
        if (loaded.saveBlockedReason != null || loaded.isSaving) {
            return
        }
        viewModelScope.launch {
            _uiState.value = loaded.copy(isSaving = true)
            val updatedWorkout = loaded.lifts.toWorkout(baseline, WorkoutId(loaded.workoutId))
            saveWorkoutUseCase(WorkoutId(loaded.workoutId), updatedWorkout)
                .onSuccess {
                    // Slice 8 (D11): append a SchemeChanged event for each lift whose
                    // progression scheme differs from the baseline (compounds only —
                    // isolation toggles are no-ops in setProgressionScheme). Append-only.
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
                    onComplete()
                }
                .onFailure {
                    _uiState.value = loaded.copy(isSaving = false)
                }
        }
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
            programChangeEventRepository.append(
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
            programChangeEventRepository.append(
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
        val liftName = catalogLifts.firstOrNull { it.id.value == liftId }?.name ?: PLACEHOLDER_LIFT_NAME
        val isCompound = catalogLifts.firstOrNull { it.id.value == liftId }?.isCompound ?: false
        updateLoaded { loaded ->
            if (workoutLiftId == null) {
                val position = loaded.lifts.size
                val newWorkoutLiftId = newWorkoutLiftId()
                val newLift = EditLiftState(
                    workoutLiftId = newWorkoutLiftId,
                    liftId = liftId,
                    name = liftName,
                    position = position,
                    sets = listOf(defaultEditSetState(setNumber = 1)),
                    isCompound = isCompound,
                    progressionScheme = ProgressionScheme.Double,
                    supersetGroupId = null,
                    useRestTimerBetweenLifts = false,
                )
                loaded.copy(lifts = loaded.lifts + newLift)
            } else {
                val updatedLifts = loaded.lifts.map { lift ->
                    if (lift.workoutLiftId == workoutLiftId) {
                        lift.copy(
                            liftId = liftId,
                            name = liftName,
                            isCompound = isCompound,
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
                    EditLiftState(
                        workoutLiftId = newWorkoutLiftId(),
                        liftId = liftId,
                        name = catalog?.name ?: PLACEHOLDER_LIFT_NAME,
                        position = startPosition + index,
                        sets = listOf(defaultEditSetState(setNumber = 1)),
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
                            val newSet = defaultEditSetState(lift.sets.size + 1)
                            lift.copy(sets = renumberSets(lift.sets + newSet))
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
        updateLoaded { loaded ->
            val remainingLifts = loaded.lifts
                .filterNot { lift -> lift.workoutLiftId == workoutLiftId }
                .mapIndexed { index, lift -> lift.copy(position = index) }
            loaded.copy(
                lifts = remainingLifts.applySupersetChanges(cleanupSupersetGroups(remainingLifts)),
            )
        }
    }

    fun removeSet(setId: String) {
        updateLoaded { loaded ->
            loaded.copy(
                lifts =
                    loaded.lifts.map { lift ->
                        val updatedSets = lift.sets.filterNot { set -> set.setId == setId }
                        if (updatedSets.size == lift.sets.size) {
                            lift
                        } else {
                            lift.copy(sets = renumberSets(updatedSets))
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
     * Updates the rep range for a top set. Backoff sets are RIR-only — the progression
     * engines floor them at [com.stratum.domain.workout.BACKOFF_REP_FLOOR] and never clamp
     * into a rep range — so editing a backoff set's reps would be silently discarded. This
     * is a no-op for backoff sets (defense-in-depth behind the UI gating in
     * `EditWorkoutSetTable`).
     */
    fun setReps(setId: String, min: Int? = null, max: Int? = null) {
        val loaded = _uiState.value as? EditWorkoutUiState.Loaded ?: return
        val target = loaded.lifts.flatMap { it.sets }.firstOrNull { it.setId == setId }
        if (target?.isTopSet != true) return
        updateSet(setId) { set ->
            var next = set
            if (min != null) {
                val clampedMin = min.coerceAtLeast(0)
                val repMax = if (next.repMax in 1..<clampedMin) clampedMin else next.repMax
                next = next.copy(repMin = clampedMin, repMax = repMax)
            }
            if (max != null) {
                val clampedMax = max.coerceAtLeast(0)
                next = next.copy(repMax = clampedMax)
            }
            next
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
                lifts = loaded.lifts.map { l ->
                    if (l.workoutLiftId == workoutLiftId) l.copy(progressionScheme = scheme) else l
                },
            )
        }
    }

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
        updateLoaded { loaded ->
            loaded.copy(
                lifts = loaded.lifts.applySupersetChanges(unlinkLiftFromSuperset(loaded.lifts, workoutLiftId)),
            )
        }
    }

    fun linkSuperset(
        workoutLiftId: String,
        direction: SupersetLinkDirection,
    ) {
        updateLoaded { loaded ->
            loaded.copy(
                lifts = loaded.lifts.applySupersetChanges(
                    linkSupersetWithAdjacent(
                        lifts = loaded.lifts,
                        workoutLiftId = workoutLiftId,
                        direction = direction,
                        nextGroupId = ::newSupersetGroupId,
                    )
                ),
            )
        }
    }

    private fun List<EditLiftState>.applySupersetChanges(
        updated: List<com.stratum.core.ui.component.SupersetLinkableLiftState>
    ): List<EditLiftState> {
        return this.map { lift ->
            val match = updated.firstOrNull { it.workoutLiftId == lift.workoutLiftId }
            if (match != null) {
                lift.copy(
                    supersetGroupId = match.supersetGroupId,
                    useRestTimerBetweenLifts = match.useRestTimerBetweenLifts
                )
            } else {
                lift
            }
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

    fun clearSession() {
        hasSnapshot.value = false
        targetWorkoutId.value = null
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
