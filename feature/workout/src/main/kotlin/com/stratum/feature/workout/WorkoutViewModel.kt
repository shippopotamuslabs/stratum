package com.stratum.feature.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.importing.FatigueDecay
import com.stratum.domain.importing.ObserveLiftTrainingInsightsUseCase
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.identity.WorkoutSupersetGroupId
import com.stratum.domain.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.workout.WorkoutEditSession
import com.stratum.domain.workout.EditSessionEvent
import com.stratum.core.ui.component.SupersetLinkDirection
import com.stratum.core.ui.component.isLastInSupersetChain
import com.stratum.core.ui.component.isSupersetPairMember
import com.stratum.core.ui.component.linkSupersetWithAdjacent
import com.stratum.core.ui.component.unlinkLiftFromSuperset
import com.stratum.core.ui.component.cleanupSupersetGroups
import com.stratum.domain.training.DeleteLoggedSetUseCase
import com.stratum.domain.training.FinalizeTrainingSessionUseCase
import com.stratum.domain.training.GetLoggedSetsForSessionUseCase
import com.stratum.domain.training.LogSetCommand
import com.stratum.domain.training.LogSetUseCase
import com.stratum.domain.training.AbandonTrainingSessionUseCase
import com.stratum.domain.training.ObserveCycleProgressUseCase
import com.stratum.domain.program.SaveWorkoutUseCase
import com.stratum.domain.program.Workout
import com.stratum.domain.workout.ApplyOverlayToWorkoutUseCase
import com.stratum.domain.workout.EstimateAddedSetRepsUseCase
import com.stratum.domain.workout.GenerateProgressionSnapshotUseCase
import com.stratum.domain.workout.LoggedSetValue
import com.stratum.domain.workout.ManageWorkoutOverlayUseCase
import com.stratum.domain.workout.MergeRenderedWorkoutUseCase
import com.stratum.domain.workout.OverlaySetInstanceId
import com.stratum.domain.workout.ProjectWorkoutStructureUseCase
import com.stratum.domain.workout.RecomputeRecommendationsUseCase
import com.stratum.domain.workout.RenderedWorkout
import com.stratum.domain.workout.SetSlot
import com.stratum.domain.workout.SetSlotId
import com.stratum.domain.workout.SlotRecommendation
import com.stratum.domain.workout.SnapshotRequest
import com.stratum.domain.workout.StructuralMutation
import com.stratum.domain.workout.WorkoutOverlay
import com.stratum.domain.workout.WorkoutStructureProjection
import com.stratum.feature.workout.notification.LiveWorkoutSurfaces
import com.stratum.feature.workout.notification.RestTimerState
import com.stratum.feature.workout.ui.WorkoutUiEvent
import com.stratum.feature.workout.ui.WorkoutUiMapper
import com.stratum.feature.workout.ui.WorkoutUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the overlay layer (`MutableStateFlow<WorkoutOverlay>`) as in-memory session state
 * (D2, D9). Consumes use cases only (no repository ports directly — ARCHITECTURE.md
 * §4.4). Maps the merged rendered workout → presentation model.
 *
 * The snapshot is computed once per [startSession] from history; overlay mutations
 * (add/delete/undo/log) re-merge against the immutable snapshot and re-render. Live
 * recommendations for upcoming unlogged sets are recomputed on each merge and stored in
 * the overlay's `recommendations` map (overlay-only — never writes back to the snapshot).
 */
@HiltViewModel
@Suppress("LargeClass", "TooManyFunctions")
// Session owner: structure helpers live in LiveWorkoutOverlayOps; further split is a follow-up.
class WorkoutViewModel @Inject constructor(
    private val observeCycleProgress: ObserveCycleProgressUseCase,
    private val generateSnapshot: GenerateProgressionSnapshotUseCase,
    private val mergeRenderedWorkout: MergeRenderedWorkoutUseCase,
    private val projectStructure: ProjectWorkoutStructureUseCase,
    private val recomputeRecommendationsUseCase: RecomputeRecommendationsUseCase,
    private val estimateAddedSetReps: EstimateAddedSetRepsUseCase,
    private val logSetUseCase: LogSetUseCase,
    private val observeLiftCatalog: ObserveLiftCatalogUseCase,
    private val getLoggedSetsForSession: GetLoggedSetsForSessionUseCase,
    private val deleteLoggedSet: DeleteLoggedSetUseCase,
    private val abandonTrainingSession: AbandonTrainingSessionUseCase,
    private val finalizeTrainingSession: FinalizeTrainingSessionUseCase,
    private val applyOverlayToWorkout: ApplyOverlayToWorkoutUseCase,
    private val saveWorkoutUseCase: SaveWorkoutUseCase,
    private val observeLiftTrainingInsights: ObserveLiftTrainingInsightsUseCase,
    private val manageWorkoutOverlay: ManageWorkoutOverlayUseCase,
    private val workoutEditSession: WorkoutEditSession,
    private val liveWorkoutSurfaces: LiveWorkoutSurfaces,
) : ViewModel() {

    init {
        viewModelScope.launch {
            workoutEditSession.events.collect { event ->
                when (event) {
                    is EditSessionEvent.LiftSelected -> {
                        if (event.workoutId != activeWorkoutId?.value) return@collect
                        val targetLiftId = event.workoutLiftId
                        if (targetLiftId != null) {
                            swapLift(WorkoutLiftId(targetLiftId), LiftId(event.liftId))
                        } else {
                            addLifts(listOf(LiftId(event.liftId)), asSuperset = false)
                        }
                    }
                    is EditSessionEvent.LiftsSelected -> {
                        if (event.workoutId != activeWorkoutId?.value) return@collect
                        addLifts(
                            liftIds = event.liftIds.map { LiftId(it) },
                            asSuperset = event.asSuperset,
                        )
                    }
                    is EditSessionEvent.LiftsReordered -> {
                        if (event.workoutId == activeWorkoutId?.value) {
                            reorderLifts(event.orderedWorkoutLiftIds)
                        }
                    }
                }
            }
        }
    }

    private val _uiState = MutableStateFlow<WorkoutUiState>(WorkoutUiState.Loading)
    val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()

    /** Transient snackbar copy; cleared via [userMessageShown] after the UI presents it. */
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    val restTimerState: StateFlow<RestTimerState> = liveWorkoutSurfaces.restTimer.state

    /** Process-scoped rest timer; adjust/skip/remaining are exposed for the route + notifications. */
    val restTimer get() = liveWorkoutSurfaces.restTimer

    /** Single completion gate (bell + silent heads-up) shared with the rest FGS. */
    val restCompletion get() = liveWorkoutSurfaces.restCompletion

    private val _events = Channel<WorkoutUiEvent>(Channel.BUFFERED)
    val events: Flow<WorkoutUiEvent> = _events.receiveAsFlow()

    fun userMessageShown() {
        _userMessage.value = null
    }

    private var activeWorkoutId: WorkoutId? = null
    private var cachedSessionId: com.stratum.domain.training.identity.TrainingSessionId? = null
    private var sessionStartedAt: java.time.Instant? = null
    private var structure: WorkoutStructureProjection? = null
    private var snapshotBySlot: Map<SetSlotId, SlotRecommendation> = emptyMap()
    private var overlay: WorkoutOverlay = WorkoutOverlay()
    private var sessionObservationJob: Job? = null
    private var liftNamesById: Map<LiftId, String> = emptyMap()
    private var liftsById: Map<LiftId, com.stratum.domain.lift.Lift> = emptyMap()
    private var fatigueByLiftId: Map<LiftId, FatigueDecay> = emptyMap()
    private var snapshotLoaded = false
    private var baselineWorkout: Workout? = null
    /** Guards double-tap complete; reset when finalize fails so the user can retry. */
    private var isCompleting: Boolean = false
    /**
     * True after a successful template save within the current complete attempt, so a
     * finalize-only retry does not re-apply overlay (and mint new set ids) a second time.
     */
    private var templateSavedThisComplete: Boolean = false
    private val overlayPersistMutex = Mutex()

    fun startSession(workoutId: WorkoutId) {
        if (activeWorkoutId == workoutId && snapshotLoaded) return
        activeWorkoutId = workoutId
        snapshotLoaded = false
        overlay = WorkoutOverlay()
        snapshotBySlot = emptyMap()
        structure = null
        _uiState.value = WorkoutUiState.Loading
        sessionObservationJob?.cancel()
        sessionObservationJob =
            viewModelScope.launch {
                liftNamesById =
                    observeLiftCatalog().first().associate { lift -> lift.id to lift.name }
                liftsById =
                    observeLiftCatalog().first().associateBy { lift -> lift.id }
                fatigueByLiftId = observeLiftTrainingInsights()
                observeCycleProgress().collect { progress ->
                    val program = progress.program
                    val session = progress.activeSession
                    if (program == null || session == null || session.workoutId != workoutId) {
                        cachedSessionId = null
                        sessionStartedAt = null
                        activeWorkoutId = null
                        snapshotLoaded = false
                        structure = null
                        snapshotBySlot = emptyMap()
                        overlay = WorkoutOverlay()
                        baselineWorkout = null
                        liveWorkoutSurfaces.clearSession()
                        _uiState.value = WorkoutUiState.NoActiveSession
                        return@collect
                    }
                    cachedSessionId = session.id
                    sessionStartedAt = session.startedAt
                    val workout = program.workouts.firstOrNull { it.id == workoutId } ?: return@collect
                    baselineWorkout = workout
                    if (!snapshotLoaded) {
                        val projected = projectStructure(
                            workout,
                            liftNamesById,
                            loadTypesById = liftsById.mapValues { it.value.loadType },
                        )
                        structure = projected
                        val snapshotResult = generateSnapshot(
                            SnapshotRequest(
                                programId = program.id,
                                workoutId = workoutId,
                                lifts = workout.workoutLifts,
                                loadTypesById = liftsById.mapValues { it.value.loadType },
                                incrementTiersById =
                                    liftsById.mapValues { (_, lift) -> lift.incrementTiers },
                            ),
                        )
                        val snapshot = snapshotResult.getOrDefault(
                            com.stratum.domain.workout.ProgressionSnapshot(workoutId, emptyMap()),
                        )
                        snapshotBySlot = snapshot.bySlot
                        
                        // Load already logged sets for the session from database (resume session support)
                        val dbSets = getLoggedSetsForSession(session.id)
                        val resumedLoggedSets = mutableMapOf<SetSlotId, LoggedSetValue>()
                        for (dbSet in dbSets) {
                            val slot = projected.lifts.flatMap { it.slots }.firstOrNull {
                                it.workoutLiftId == dbSet.liftContext.workoutLiftId && it.position == dbSet.setIndex
                            }
                            if (slot != null) {
                                resumedLoggedSets[slot.slotId] = LoggedSetValue(
                                    slotId = slot.slotId,
                                    weight = dbSet.weight,
                                    reps = dbSet.reps,
                                    rir = dbSet.rir,
                                )
                            }
                        }
                        // Prefer a persisted overlay (process-death restore). On a fresh
                        // session nothing is stored, so fall back to the DB-resumed logged
                        // sets. The persisted overlay is authoritative: every mutation
                        // persists in lockstep, so loggedSets/addedSlots/undoStack/etc. are
                        // all captured for the resumed session.
                        val persisted = manageWorkoutOverlay.load(session.id)
                        overlay = persisted ?: overlay.copy(loggedSets = resumedLoggedSets)
                        snapshotLoaded = true
                    }
                    recomputeRecommendations()
                    render()
                }
            }
    }

    fun logSet(slotKey: String, weight: Weight, reps: Int, rir: Int?) {
        val session = cachedSessionId ?: return
        val slot = resolveSlotByKey(slotKey) ?: return
        val slotId = slot.slotId
        // loadType-aware weight normalization before the value reaches the log use case:
        //  - Bodyweight: force 0 (the UI hides the weight input, but guard anyway).
        //  - Assisted: the user types a positive assistance amount; negate it so the
        //    persisted value matches the negative-weight sign convention (LoadType.kt).
        //  - Weighted: pass through unchanged.
        val loadType = liftsById[slot.liftId]?.loadType ?: LoadType.Weighted
        val normalizedWeight = when (loadType) {
            LoadType.Bodyweight -> Weight(0.0, weight.unit)
            LoadType.Assisted ->
                if (weight.amount > 0.0) Weight(-weight.amount, weight.unit) else weight
            LoadType.Weighted -> weight
        }
        viewModelScope.launch {
            val result = logSetUseCase(
                LogSetCommand(
                    sessionId = session,
                    liftId = slot.liftId,
                    workoutLiftId = slot.workoutLiftId,
                    setIndex = slot.position,
                    weight = normalizedWeight,
                    reps = reps,
                    rir = rir,
                    completedAt = Instant.now(),
                ),
            )
            if (result.isFailure) {
                _userMessage.value = MSG_LOG_SET_FAILED
                return@launch
            }
            overlay = overlay.copy(
                loggedSets = overlay.loggedSets + (slotId to LoggedSetValue(slotId, normalizedWeight, reps, rir)),
            )
            recomputeRecommendations()
            render()
            persistOverlay()
        }
    }

    fun unlogSet(slotKey: String) {
        val session = cachedSessionId ?: return
        val slot = resolveSlotByKey(slotKey) ?: return
        val slotId = slot.slotId
        viewModelScope.launch {
            val dbSets = getLoggedSetsForSession(session)
            val dbSet = dbSets.firstOrNull {
                it.liftContext.workoutLiftId == slot.workoutLiftId && it.setIndex == slot.position
            }
            if (dbSet != null) {
                val deleteResult = deleteLoggedSet(dbSet.id)
                if (deleteResult.isFailure) {
                    _userMessage.value = MSG_UNLOG_SET_FAILED
                    return@launch
                }
            }
            overlay = overlay.copy(
                loggedSets = overlay.loggedSets - slotId,
            )
            recomputeRecommendations()
            render()
            persistOverlay()
        }
    }

    fun addSet(afterSlotId: SetSlotId) {
        val anchor = resolveSlot(afterSlotId) ?: return
        val newPosition = anchor.position + 1
        val added = SetSlot(
            slotId = SetSlotId.Overlay(
                OverlaySetInstanceId("added-${overlay.addedSlots.size + 1}"),
            ),
            workoutLiftId = anchor.workoutLiftId,
            liftId = anchor.liftId,
            position = newPosition,
            setType = anchor.setType,
            isTopSet = false,
            repMin = anchor.repMin,
            repMax = anchor.repMax,
            rirGoal = anchor.rirGoal,
            restSeconds = anchor.restSeconds,
        )
        val baseline = snapshotBySlot[afterSlotId]
        val fatigue = fatigueByLiftId[anchor.liftId] ?: FatigueDecay(null, null)
        val coldWeight = Weight(0.0, Weight.WeightUnit.Lb)
        val estimatedReps = estimateAddedSetReps(
            previousRecommendation = baseline ?: SlotRecommendation(afterSlotId, coldWeight, anchor.repMin),
            nextSetIndex = newPosition,
            perLiftSlope = fatigue.slope,
            perLiftIntercept = fatigue.intercept,
        )
        val addedWeight = baseline?.recommendedWeight ?: coldWeight
        val addedRecommendation = SlotRecommendation(added.slotId, addedWeight, estimatedReps)
        overlay = overlay.apply(StructuralMutation.AddSet(afterSlotId = afterSlotId, newSlot = added))
            .copy(dynamicBaselines = overlay.dynamicBaselines + (added.slotId to addedRecommendation))
        recomputeRecommendations()
        render()
        persistOverlay()
    }

    /**
     * Adds a set after the last remaining set of [workoutLiftId].
     * UI entry point matching history/template "add set" affordance.
     */
    fun addSetForLift(workoutLiftId: String) {
        val lastSlot = visibleSlotsForLift(WorkoutLiftId(workoutLiftId)).maxByOrNull { it.position }
            ?: return
        addSet(afterSlotId = lastSlot.slotId)
    }

    fun deleteSet(slotId: SetSlotId) {
        overlay = overlay
            .apply(StructuralMutation.DeleteSet(slotId))
            .copy(restSecondsOverrides = overlay.restSecondsOverrides - slotId)
        recomputeRecommendations()
        render()
        persistOverlay()
    }

    /**
     * Deletes a set by presentation [slotKey]. No-ops when the owning lift has only one
     * remaining set (last-set delete is disabled in UI; lift removal uses [removeLift]).
     * Unlogs a completed set first so Room does not retain orphan rows.
     */
    fun deleteSetByKey(slotKey: String) {
        val slot = resolveSlotByKey(slotKey) ?: return
        val remainingForLift = visibleSlotsForLift(slot.workoutLiftId)
        if (remainingForLift.size <= 1) return

        val slotId = slot.slotId
        val workoutLiftId = slot.workoutLiftId
        val setPosition = slot.position

        if (slotId in overlay.loggedSets) {
            val session = cachedSessionId
            if (session != null) {
                viewModelScope.launch {
                    val dbSets = getLoggedSetsForSession(session)
                    val dbSet = dbSets.firstOrNull {
                        it.liftContext.workoutLiftId == workoutLiftId &&
                            it.setIndex == setPosition
                    }
                    if (dbSet != null) {
                        val deleteResult = deleteLoggedSet(dbSet.id)
                        if (deleteResult.isFailure) {
                            _userMessage.value = MSG_UNLOG_SET_FAILED
                            return@launch
                        }
                    }
                    // Re-check on the latest overlay after suspend — another mutation may have
                    // already removed this set or left the lift with a single remaining set.
                    if (slotId in overlay.deletedSlotIds) return@launch
                    if (visibleSlotsForLift(workoutLiftId).size <= 1) return@launch
                    overlay = overlay
                        .copy(loggedSets = overlay.loggedSets - slotId)
                        .apply(StructuralMutation.DeleteSet(slotId))
                    recomputeRecommendations()
                    render()
                    persistOverlay()
                }
                return
            }
            overlay = overlay.copy(loggedSets = overlay.loggedSets - slotId)
        }
        deleteSet(slotId)
    }

    fun undo() {
        overlay = overlay.undo()
        recomputeRecommendations()
        render()
        persistOverlay()
    }

    fun generateMyoRepMiniSet(activationSlotId: SetSlotId) {
        val anchor = resolveSlot(activationSlotId) ?: return
        val mini = SetSlot(
            slotId = SetSlotId.Overlay(
                com.stratum.domain.workout.OverlaySetInstanceId("mini-${overlay.addedSlots.size + 1}"),
            ),
            workoutLiftId = anchor.workoutLiftId,
            liftId = anchor.liftId,
            position = anchor.position + 1,
            setType = com.stratum.domain.program.SetType.MyoRep,
            isTopSet = false,
            repMin = 0,
            repMax = 0,
            rirGoal = 0,
            restSeconds = 15,
        )
        overlay = overlay.apply(
            StructuralMutation.GenerateMyoRepMiniSet(activationSlotId = activationSlotId, newSlot = mini),
        )
        recomputeRecommendations()
        render()
        persistOverlay()
    }

    fun addDropSet(afterSlotId: SetSlotId) {
        val anchor = resolveSlot(afterSlotId) ?: return
        val drop = SetSlot(
            slotId = SetSlotId.Overlay(
                com.stratum.domain.workout.OverlaySetInstanceId("drop-${overlay.addedSlots.size + 1}"),
            ),
            workoutLiftId = anchor.workoutLiftId,
            liftId = anchor.liftId,
            position = anchor.position + 1,
            setType = com.stratum.domain.program.SetType.DropSet,
            isTopSet = false,
            repMin = 0,
            repMax = 0,
            rirGoal = 0,
            restSeconds = 60,
        )
        overlay = overlay.apply(StructuralMutation.AddDropSet(afterSlotId = afterSlotId, newSlot = drop))
        recomputeRecommendations()
        render()
        persistOverlay()
    }

    fun swapLift(workoutLiftId: WorkoutLiftId, newLiftId: LiftId) {
        overlay = overlay.copy(
            swappedLifts = overlay.swappedLifts + (workoutLiftId to newLiftId)
        )
        recomputeRecommendations()
        render()
        persistOverlay()
    }

    fun addLifts(liftIds: List<LiftId>, asSuperset: Boolean = false) {
        val currentIds = merge().lifts.map { it.workoutLiftId.value }
        overlay = LiveWorkoutOverlayOps.addLifts(
            overlay = overlay,
            currentLiftIds = currentIds,
            liftIds = liftIds,
            asSuperset = asSuperset,
            liftsById = liftsById,
            zeroWeight = Weight(0.0, Weight.WeightUnit.Lb),
        )
        recomputeRecommendations()
        render()
        persistOverlay()
    }

    fun startReorderLifts() {
        val currentRendered = merge()
        val items = currentRendered.lifts.map { com.stratum.domain.workout.ReorderLiftItem(it.workoutLiftId.value, it.liftName) }
        workoutEditSession.setReorderLifts(items)
    }

    fun reorderLifts(orderedWorkoutLiftIds: List<String>) {
        overlay = overlay.copy(orderedLiftIds = orderedWorkoutLiftIds)
        recomputeRecommendations()
        render()
        persistOverlay()
    }

    fun removeLift(workoutLiftId: WorkoutLiftId) {
        val currentLifts = getLinkableLifts().filterNot { it.workoutLiftId == workoutLiftId.value }
        val cleaned = cleanupSupersetGroups(currentLifts)
        // Start from existing overrides and drop the removed lift — do not write null
        // overrides for every remaining lift (that would keep isDirty true forever).
        val newGroupIdOverrides = overlay.supersetGroupIdOverrides.toMutableMap()
        val newRestTimerOverrides = overlay.useRestTimerBetweenLiftsOverrides.toMutableMap()
        newGroupIdOverrides.remove(workoutLiftId)
        newRestTimerOverrides.remove(workoutLiftId)
        cleaned.forEach { lift ->
            val id = WorkoutLiftId(lift.workoutLiftId)
            val group = lift.supersetGroupId?.let { WorkoutSupersetGroupId(it) }
            val priorGroup =
                if (overlay.supersetGroupIdOverrides.containsKey(id)) {
                    overlay.supersetGroupIdOverrides[id]
                } else {
                    // No explicit override yet — compare against post-cleanup value only when
                    // cleanup actually assigned a group (or cleared an existing one).
                    null
                }
            if (group != priorGroup && (group != null || overlay.supersetGroupIdOverrides.containsKey(id))) {
                newGroupIdOverrides[id] = group
            }
            if (overlay.useRestTimerBetweenLiftsOverrides.containsKey(id) ||
                lift.useRestTimerBetweenLifts
            ) {
                val priorRest = overlay.useRestTimerBetweenLiftsOverrides[id]
                if (priorRest != lift.useRestTimerBetweenLifts) {
                    newRestTimerOverrides[id] = lift.useRestTimerBetweenLifts
                }
            }
        }

        val nextDeletedLiftIds =
            if (overlay.addedLifts.any { it.workoutLiftId == workoutLiftId }) {
                overlay.deletedLiftIds
            } else {
                overlay.deletedLiftIds + workoutLiftId
            }
        val baselineLiftIdsInOrder =
            structure
                ?.lifts
                ?.filterNot { it.workoutLiftId in nextDeletedLiftIds }
                ?.map { it.workoutLiftId.value }
                .orEmpty()

        overlay = LiveWorkoutOverlayOps.removeLift(
            overlay = overlay,
            workoutLiftId = workoutLiftId,
            cleanedGroupOverrides = newGroupIdOverrides,
            cleanedRestOverrides = newRestTimerOverrides,
            resolveSlot = ::resolveSlot,
            baselineLiftIdsInOrder = baselineLiftIdsInOrder,
        )
        recomputeRecommendations()
        render()
        persistOverlay()
    }

    fun linkSuperset(workoutLiftId: WorkoutLiftId, direction: SupersetLinkDirection) {
        val currentLifts = getLinkableLifts()
        val updated = linkSupersetWithAdjacent(
            lifts = currentLifts,
            workoutLiftId = workoutLiftId.value,
            direction = direction,
            nextGroupId = { WorkoutSupersetGroupId(java.util.UUID.randomUUID().toString()).value }
        )
        val newGroupIdOverrides = overlay.supersetGroupIdOverrides.toMutableMap()
        val newRestTimerOverrides = overlay.useRestTimerBetweenLiftsOverrides.toMutableMap()
        updated.forEach { lift ->
            newGroupIdOverrides[WorkoutLiftId(lift.workoutLiftId)] = lift.supersetGroupId?.let { WorkoutSupersetGroupId(it) }
            newRestTimerOverrides[WorkoutLiftId(lift.workoutLiftId)] = lift.useRestTimerBetweenLifts
        }
        overlay = overlay.copy(
            supersetGroupIdOverrides = newGroupIdOverrides,
            useRestTimerBetweenLiftsOverrides = newRestTimerOverrides
        )
        recomputeRecommendations()
        render()
        persistOverlay()
    }

    fun removeFromSuperset(workoutLiftId: WorkoutLiftId) {
        val currentLifts = getLinkableLifts()
        val updated = unlinkLiftFromSuperset(currentLifts, workoutLiftId.value)
        val newGroupIdOverrides = overlay.supersetGroupIdOverrides.toMutableMap()
        val newRestTimerOverrides = overlay.useRestTimerBetweenLiftsOverrides.toMutableMap()
        updated.forEach { lift ->
            newGroupIdOverrides[WorkoutLiftId(lift.workoutLiftId)] = lift.supersetGroupId?.let { WorkoutSupersetGroupId(it) }
            newRestTimerOverrides[WorkoutLiftId(lift.workoutLiftId)] = lift.useRestTimerBetweenLifts
        }
        overlay = overlay.copy(
            supersetGroupIdOverrides = newGroupIdOverrides,
            useRestTimerBetweenLiftsOverrides = newRestTimerOverrides
        )
        recomputeRecommendations()
        render()
        persistOverlay()
    }

    fun setUseRestTimerBetweenLifts(workoutLiftId: WorkoutLiftId, enabled: Boolean) {
        overlay = overlay.copy(
            useRestTimerBetweenLiftsOverrides = overlay.useRestTimerBetweenLiftsOverrides + (workoutLiftId to enabled)
        )
        recomputeRecommendations()
        render()
        persistOverlay()
    }

    /**
     * Applies rest overrides for Configure Rest Times confirm.
     * Keys are presentation [slotKey] strings; marks the overlay dirty so save-template persists.
     */
    fun setRestSecondsForSlots(updates: Map<String, Int>) {
        if (updates.isEmpty()) return
        val next = LiveWorkoutOverlayOps.mergeRestSecondsOverrides(
            overlay = overlay,
            updates = updates,
            resolveSlotByKey = ::resolveSlotByKey,
        ) ?: return
        overlay = overlay.copy(restSecondsOverrides = next)
        render()
        persistOverlay()
    }

    private fun getLinkableLifts(): List<com.stratum.core.ui.component.SupersetLinkableLiftState> {
        val currentRendered = merge()
        return currentRendered.lifts.map { lift ->
            com.stratum.core.ui.component.SupersetLinkableLiftState(
                workoutLiftId = lift.workoutLiftId.value,
                liftId = lift.liftId.value,
                name = lift.liftName,
                supersetGroupId = lift.supersetGroupId?.value,
                useRestTimerBetweenLifts = overlay.useRestTimerBetweenLiftsOverrides[lift.workoutLiftId] ?: lift.useRestTimerBetweenLifts
            )
        }
    }

    fun completeWorkout(saveTemplate: Boolean) {
        if (isCompleting) return
        val sessionId = cachedSessionId ?: return
        val workoutId = activeWorkoutId ?: return
        val workout = baselineWorkout
        isCompleting = true
        viewModelScope.launch {
            try {
                var templateSaved = false
                if (saveTemplate && workout != null && !templateSavedThisComplete) {
                    val updated = applyOverlayToWorkout(workout, overlay)
                    val saveResult = saveWorkoutUseCase(workoutId, updated)
                    if (saveResult.isFailure) {
                        _userMessage.value = MSG_COMPLETE_FAILED
                        return@launch
                    }
                    // Fold applied structure into baseline so a finalize-only retry is safe and
                    // does not re-apply (or double-mint) template ids.
                    baselineWorkout = updated
                    templateSavedThisComplete = true
                    templateSaved = true
                }
                val finalizeResult = finalizeTrainingSession(sessionId)
                if (finalizeResult.isFailure) {
                    _userMessage.value =
                        if (templateSaved || templateSavedThisComplete) {
                            MSG_COMPLETE_FINALIZE_AFTER_TEMPLATE
                        } else {
                            MSG_COMPLETE_FAILED
                        }
                    return@launch
                }
                templateSavedThisComplete = false
                manageWorkoutOverlay.clear()
                liveWorkoutSurfaces.clearSession()
                _events.send(WorkoutUiEvent.NavigateToFinalize(sessionId.value))
            } finally {
                isCompleting = false
            }
        }
    }

    fun abandonSession() {
        val session = cachedSessionId
        if (session != null) {
            viewModelScope.launch {
                val result = abandonTrainingSession(session)
                if (result.isFailure) {
                    _userMessage.value = MSG_ABANDON_FAILED
                    return@launch
                }
                sessionObservationJob?.cancel()
                sessionObservationJob = null
                activeWorkoutId = null
                snapshotLoaded = false
                structure = null
                snapshotBySlot = emptyMap()
                fatigueByLiftId = emptyMap()
                overlay = WorkoutOverlay()
                manageWorkoutOverlay.clear()
                liveWorkoutSurfaces.clearSession()
                _uiState.value = WorkoutUiState.NoActiveSession
            }
        } else {
            sessionObservationJob?.cancel()
            sessionObservationJob = null
            activeWorkoutId = null
            snapshotLoaded = false
            structure = null
            snapshotBySlot = emptyMap()
            fatigueByLiftId = emptyMap()
            overlay = WorkoutOverlay()
            viewModelScope.launch {
                manageWorkoutOverlay.clear()
            }
            liveWorkoutSurfaces.clearSession()
            _uiState.value = WorkoutUiState.NoActiveSession
        }
    }



    /**
     * Recomputes overlay-only recommendations for upcoming unlogged sets from the most
     * recent performed set per lift (D4). Delegates the pipeline to
     * [RecomputeRecommendationsUseCase]; never writes back into the snapshot.
     */
    private fun recomputeRecommendations() {
        // Best-effort: keep the last good recommendations if recompute throws.
        runCatching {
            recomputeRecommendationsUseCase(merge(), liftsById, fatigueByLiftId)
        }.onSuccess { recommendations ->
            overlay = overlay.copy(recommendations = recommendations)
        }
    }

    /**
     * Persists the current overlay to the DataStore-backed store so the session survives an
     * app process death. Serialized via [overlayPersistMutex] so concurrent mutations always
     * write the **latest** overlay at lock time (avoids out-of-order saves on process death).
     * Fire-and-forget: [ManageWorkoutOverlayUseCase] logs failures; never surface to the user.
     */
    private fun persistOverlay() {
        val sessionId = cachedSessionId ?: return
        viewModelScope.launch {
            overlayPersistMutex.withLock {
                manageWorkoutOverlay.save(sessionId, overlay)
            }
        }
    }

    private fun merge(): RenderedWorkout =
        LiveWorkoutOverlayOps.merge(
            structure = structure,
            overlay = overlay,
            snapshotBySlot = snapshotBySlot,
            liftsById = liftsById,
            liftNamesById = liftNamesById,
            mergeRenderedWorkout = mergeRenderedWorkout,
        )

    private fun visibleSlotsForLift(workoutLiftId: WorkoutLiftId): List<SetSlot> =
        LiveWorkoutOverlayOps.visibleSlotsForLift(structure, overlay, workoutLiftId)

    private fun render() {
        val rendered = merge()
        val startedAtMilli = sessionStartedAt?.toEpochMilli() ?: java.time.Instant.now().toEpochMilli()
        val hasIncompleteLifts = rendered.lifts.any { lift ->
            lift.sets.any { it.loggedValue == null }
        }
        val hasDirtyEdits = overlay.isDirty
        val imageUrlByLiftId = liftsById.mapValues { (_, lift) -> lift.imageUris.firstOrNull() }
            .mapKeys { (id, _) -> id.value }
        val mapped = WorkoutUiMapper.map(
            rendered,
            startedAtMilli,
            hasIncompleteLifts = hasIncompleteLifts,
            hasDirtyEdits = hasDirtyEdits,
            imageUrlByLiftId = imageUrlByLiftId,
        )
        _uiState.value = mapped
        liveWorkoutSurfaces.activeWorkout.setActive(
            workoutId = mapped.workoutId,
            workoutName = mapped.workoutName,
            startedAtEpochMs = mapped.sessionStartedAtEpochMilli,
        )
    }

    private fun resolveSlot(slotId: SetSlotId): SetSlot? =
        LiveWorkoutOverlayOps.resolveSlot(structure, overlay, slotId)

    private fun resolveSlotByKey(slotKey: String): SetSlot? =
        LiveWorkoutOverlayOps.resolveSlotByKey(structure, overlay, slotKey)

    private companion object {
        const val MSG_LOG_SET_FAILED = "Couldn't save that set. Try again."
        const val MSG_UNLOG_SET_FAILED = "Couldn't remove that set. Try again."
        const val MSG_COMPLETE_FAILED = "Couldn't finish the workout. Try again."
        const val MSG_COMPLETE_FINALIZE_AFTER_TEMPLATE =
            "Template saved, but finishing the session failed. Tap Complete again."
        const val MSG_ABANDON_FAILED = "Couldn't cancel the workout. Try again."
    }
}
