package com.stratum.feature.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.importing.FatigueDecay
import com.stratum.domain.importing.ObserveLiftTrainingInsightsUseCase
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.ObserveWeightDisplayPreferencesUseCase
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.storageWeightFromDisplay

import com.stratum.domain.program.identity.ProgramId
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
import com.stratum.domain.training.ObserveCycleProgressUseCase
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionDisplayName
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.UpdateLoggedSetUseCase
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.training.liveWorkoutRouteId
import com.stratum.domain.program.SaveWorkoutUseCase
import com.stratum.domain.program.Program
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
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
import kotlinx.coroutines.flow.combine
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
 * The progression snapshot is computed at [startSession] from history and **recomputed on
 * in-session exercise swap** (and when a persisted overlay with swaps is restored) so
 * previous-column and baseline recommendations bind to the performed catalog lift.
 * Other overlay mutations (add/delete/undo/log) re-merge against the current snapshot and
 * re-render. Live recommendations for upcoming unlogged sets are recomputed on each merge
 * and stored in the overlay's `recommendations` map (overlay-only — never writes back to
 * the snapshot).
 */
@HiltViewModel
@Suppress("LargeClass", "TooManyFunctions", "LongParameterList")
// Session owner: structure helpers live in LiveWorkoutOverlayOps; further split is a follow-up.
class WorkoutViewModel @Inject constructor(
    private val observeCycleProgress: ObserveCycleProgressUseCase,
    private val generateSnapshot: GenerateProgressionSnapshotUseCase,
    private val mergeRenderedWorkout: MergeRenderedWorkoutUseCase,
    private val projectStructure: ProjectWorkoutStructureUseCase,
    private val recomputeRecommendationsUseCase: RecomputeRecommendationsUseCase,
    private val estimateAddedSetReps: EstimateAddedSetRepsUseCase,
    private val logSetUseCase: LogSetUseCase,
    private val updateLoggedSet: UpdateLoggedSetUseCase,
    private val observeLiftCatalog: ObserveLiftCatalogUseCase,
    private val getLoggedSetsForSession: GetLoggedSetsForSessionUseCase,
    private val deleteLoggedSet: DeleteLoggedSetUseCase,
    private val processScopedAbandon: ProcessScopedAbandonTrainingSession,
    private val finalizeTrainingSession: FinalizeTrainingSessionUseCase,
    private val applyOverlayToWorkout: ApplyOverlayToWorkoutUseCase,
    private val saveWorkoutUseCase: SaveWorkoutUseCase,
    private val observeLiftTrainingInsights: ObserveLiftTrainingInsightsUseCase,
    private val manageWorkoutOverlay: ManageWorkoutOverlayUseCase,
    private val workoutEditSession: WorkoutEditSession,
    private val liveWorkoutSurfaces: LiveWorkoutSurfaces,
    observeWeightDisplayPreferences: ObserveWeightDisplayPreferencesUseCase,
) : ViewModel() {

    /**
     * Preferred **display** unit (Settings). Live log storage is always **Lb** (canonical).
     * Bumped via [displayUnitEpoch] so Compose draft stores clear on flip.
     */
    private val displayUnit = MutableStateFlow(Weight.WeightUnit.Lb)
    private val displayUnitEpoch = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            observeWeightDisplayPreferences().collect { prefs ->
                if (displayUnit.value == prefs.displayUnit) return@collect
                displayUnit.value = prefs.displayUnit
                displayUnitEpoch.value += 1
                // Re-render so recs/logged amounts flip units; drafts reseed via epoch.
                if (_uiState.value is WorkoutUiState.Loaded) {
                    render()
                }
            }
        }
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
    private var cachedProgramId: ProgramId? = null
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
    /**
     * `(workoutLiftId, catalogLiftId)` pairs from the last structure build. When merge (or
     * any template rewrite) re-points catalog ids, this fingerprint changes and we rebuild
     * structure + progression snapshot so names/previous/recs track the survivor lift.
     */
    private var templateLiftFingerprint: List<Pair<String, String>> = emptyList()
    private var baselineWorkout: Workout? = null
    /**
     * True only for [TrainingSessionKind.PROGRAM_WORKOUT] with a real template baseline.
     * Controls save-to-program-day UI (`hasDirtyEdits`) and [SaveWorkoutUseCase] on complete.
     * Ad-hoc sessions always false — structural edits stay overlay/session only.
     */
    private var canSaveTemplateChanges: Boolean = false
    /** Guards double-tap complete; reset when finalize fails so the user can retry. */
    private var isCompleting: Boolean = false
    /**
     * True after a successful template save within the current complete attempt, so a
     * finalize-only retry does not re-apply overlay (and mint new set ids) a second time.
     */
    private var templateSavedThisComplete: Boolean = false
    /**
     * After intentional complete/abandon, keep the last [WorkoutUiState.Loaded] frame so
     * NavHost exit transitions do not flash [WorkoutUiState.NoActiveSession] ("No active
     * session." + Back) while this route is still composed.
     */
    private var retainLoadedUiForExit: Boolean = false
    private val overlayPersistMutex = Mutex()
    /** Serializes log/unlog so concurrent first-completes cannot double-insert. */
    private val setLogMutex = Mutex()

    fun startSession(workoutId: WorkoutId) {
        if (activeWorkoutId == workoutId && snapshotLoaded) return
        activeWorkoutId = workoutId
        retainLoadedUiForExit = false
        cachedProgramId = null
        snapshotLoaded = false
        templateLiftFingerprint = emptyList()
        overlay = WorkoutOverlay()
        snapshotBySlot = emptyMap()
        structure = null
        canSaveTemplateChanges = false
        _uiState.value = WorkoutUiState.Loading
        sessionObservationJob?.cancel()
        sessionObservationJob =
            viewModelScope.launch {
                fatigueByLiftId = observeLiftTrainingInsights()
                // Catalog is combined so a mid-session lift merge (delete/hide source + overlay
                // remap) rebinds even when the program row fingerprint is unchanged.
                combine(
                    observeCycleProgress(),
                    observeLiftCatalog(),
                ) { progress, catalog ->
                    progress to catalog
                }.collect { (progress, catalog) ->
                    liftNamesById = catalog.associate { lift -> lift.id to lift.name }
                    liftsById = catalog.associateBy { lift -> lift.id }

                    val program = progress.program
                    val session = progress.activeSession
                    if (program == null || session == null || !sessionMatchesRoute(session, workoutId)) {
                        clearToNoActiveSession()
                        return@collect
                    }
                    val workout = resolveSessionWorkout(session, program, workoutId)
                    if (workout == null) {
                        // Program template deleted mid-session: abandon orphan and surface empty.
                        if (cachedSessionId != session.id) {
                            processScopedAbandon.abandon(session.id) { /* recovery — best effort */ }
                        }
                        // Keep session id so we do not re-abandon the same orphan every collect.
                        clearToNoActiveSession(retainSessionId = session.id)
                        return@collect
                    }
                    cachedProgramId = program.id
                    cachedSessionId = session.id
                    sessionStartedAt = session.startedAt
                    baselineWorkout = workout
                    // Kind is source of truth — never save synthetic AD_HOC baselines as program days.
                    canSaveTemplateChanges = session.kind == TrainingSessionKind.PROGRAM_WORKOUT
                    val fingerprint =
                        workout.workoutLifts.map { lift ->
                            lift.id.value to lift.liftId.value
                        }
                    val needsStructureRebuild =
                        !snapshotLoaded || fingerprint != templateLiftFingerprint
                    if (needsStructureRebuild) {
                        val isInitialLoad = !snapshotLoaded
                        val projected = projectStructure(
                            workout,
                            liftNamesById,
                            loadTypesById = liftsById.mapValues { it.value.loadType },
                        )
                        structure = projected
                        val snapshotResult = generateSnapshot(
                            SnapshotRequest(
                                programId = program.id,
                                workoutId = workout.id,
                                lifts = workout.workoutLifts,
                                loadTypesById = liftsById.mapValues { it.value.loadType },
                                incrementTiersById =
                                    liftsById.mapValues { (_, lift) -> lift.incrementTiers },
                            ),
                        )
                        val snapshot = snapshotResult.getOrDefault(
                            com.stratum.domain.workout.ProgressionSnapshot(workout.id, emptyMap()),
                        )
                        snapshotBySlot = snapshot.bySlot

                        if (isInitialLoad) {
                            overlay = restoreOverlayOnInitialLoad(
                                sessionId = session.id,
                                projected = projected,
                            )
                        } else {
                            // Template catalog ids changed (e.g. lift merge re-point). Prefer
                            // the store copy — MergeLiftsUseCase remaps swaps/added lifts there.
                            val persisted = manageWorkoutOverlay.load(session.id)
                            if (persisted != null) {
                                overlay = persisted
                            }
                        }
                        // In-session swaps (or restored ones) rebind previous/recs to the
                        // performed catalog lift — template snapshot alone is pre-swap.
                        refreshSnapshotForSwappedLifts(workout)
                        templateLiftFingerprint = fingerprint
                        snapshotLoaded = true
                    } else {
                        // Overlay-only rewrite (merge remaps swaps/adds without template change).
                        val persisted = manageWorkoutOverlay.load(session.id)
                        if (
                            persisted != null &&
                            catalogIdentitySignature(persisted) != catalogIdentitySignature(overlay)
                        ) {
                            overlay = persisted
                            refreshSnapshotForSwappedLifts(workout)
                        }
                    }
                    recomputeRecommendations()
                    render()
                }
            }
    }

    /**
     * Prefer DataStore overlay; else map DB logs onto template slots; else rebuild
     * session-added structure from durable logs (ad-hoc / mid-session adds after overlay loss).
     */
    private suspend fun restoreOverlayOnInitialLoad(
        sessionId: TrainingSessionId,
        projected: WorkoutStructureProjection,
    ): WorkoutOverlay {
        val dbSets = getLoggedSetsForSession(sessionId)
        val templateWorkoutLiftIds = projected.lifts.map { it.workoutLiftId }.toSet()
        val resumedLoggedSets = mutableMapOf<SetSlotId, LoggedSetValue>()
        for (dbSet in dbSets) {
            val slot = projected.lifts.flatMap { it.slots }.firstOrNull {
                it.workoutLiftId == dbSet.liftContext.workoutLiftId &&
                    it.position == dbSet.setIndex
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
        val persisted = manageWorkoutOverlay.load(sessionId)
        if (persisted != null) return persisted

        val orphanSets =
            dbSets.filter { set ->
                val wlId = set.liftContext.workoutLiftId
                wlId != null &&
                    set.liftId != null &&
                    wlId !in templateWorkoutLiftIds
            }
        val rebuilt =
            if (orphanSets.isNotEmpty()) {
                LiveWorkoutOverlayOps.rebuildFromSessionLoggedSets(
                    dbSets = orphanSets,
                    zeroWeight = Weight(0.0, Weight.WeightUnit.Lb),
                )
            } else {
                null
            }
        return when {
            rebuilt != null ->
                rebuilt.copy(loggedSets = rebuilt.loggedSets + resumedLoggedSets)
            else -> WorkoutOverlay(loggedSets = resumedLoggedSets)
        }
    }

    /**
     * Program workouts match on template id; ad-hoc empty sessions match on session id
     * (the route key when [TrainingSession.workoutId] is null).
     */
    private fun sessionMatchesRoute(session: TrainingSession, routeWorkoutId: WorkoutId): Boolean =
        session.liveWorkoutRouteId() == routeWorkoutId.value

    /**
     * Resolves the structural baseline for the live session. Ad-hoc uses a synthetic empty
     * [Workout] so overlay add-lift / log flows work without a program template row.
     */
    private fun resolveSessionWorkout(
        session: TrainingSession,
        program: Program,
        routeWorkoutId: WorkoutId,
    ): Workout? =
        when (session.kind) {
            TrainingSessionKind.PROGRAM_WORKOUT ->
                session.workoutId?.let { boundId ->
                    program.workouts.firstOrNull { it.id == boundId }
                }
            TrainingSessionKind.AD_HOC ->
                Workout(
                    id = routeWorkoutId,
                    programId = program.id,
                    name = TrainingSessionDisplayName.resolve(session = session, program = program),
                    weekday = null,
                    position = 0,
                    workoutLifts = emptyList(),
                )
            TrainingSessionKind.REST_DAY -> null
        }

    /**
     * @param retainSessionId when non-null, keeps [cachedSessionId] so orphan abandon does not
     * re-fire every collect for the same dead session.
     */
    private fun clearToNoActiveSession(
        retainSessionId: TrainingSessionId? = null,
    ) {
        cachedProgramId = null
        cachedSessionId = retainSessionId
        sessionStartedAt = null
        activeWorkoutId = null
        snapshotLoaded = false
        templateLiftFingerprint = emptyList()
        structure = null
        snapshotBySlot = emptyMap()
        overlay = WorkoutOverlay()
        baselineWorkout = null
        canSaveTemplateChanges = false
        liveWorkoutSurfaces.clearSession()
        publishNoActiveSessionIfNeeded()
    }

    /**
     * Publishes [WorkoutUiState.NoActiveSession] only when the empty session is unexpected.
     * Intentional complete/abandon keeps [WorkoutUiState.Loaded] for the exit transition.
     */
    private fun publishNoActiveSessionIfNeeded() {
        if (isCompleting || retainLoadedUiForExit) return
        _uiState.value = WorkoutUiState.NoActiveSession
    }

    private fun catalogIdentitySignature(value: WorkoutOverlay): List<String> =
        buildList {
            value.swappedLifts.forEach { (workoutLiftId, liftId) ->
                add("swap:${workoutLiftId.value}:${liftId.value}")
            }
            value.addedLifts.forEach { added ->
                add("add:${added.workoutLiftId.value}:${added.liftId.value}")
            }
            value.addedSlots.forEach { slot ->
                add("slot:${LiveWorkoutOverlayOps.slotKey(slot.slotId)}:${slot.liftId.value}")
            }
        }.sorted()

    /** Unit live set fields are shown/typed in (Settings preference). */
    fun currentDisplayUnit(): Weight.WeightUnit = displayUnit.value

    fun logSet(slotKey: String, weight: Weight, reps: Int, rir: Int?) {
        val session = cachedSessionId ?: return
        val slot = resolveSlotByKey(slotKey) ?: return
        val slotId = slot.slotId
        // Canonical storage: always Lb. [weight] is display-unit magnitude from the UI.
        val loadType = liftsById[slot.liftId]?.loadType ?: LoadType.Weighted
        val normalizedWeight =
            storageWeightFromDisplay(
                displayAmount = weight.amount,
                displayUnit = weight.unit,
                loadType = loadType,
            )
        viewModelScope.launch {
            setLogMutex.withLock {
                // Re-read after lock so concurrent first-completes do not double-insert.
                val wasAlreadyLogged = slotId in overlay.loggedSets
                val persistOk =
                    persistLoggedSet(
                        session = session,
                        slot = slot,
                        weight = normalizedWeight,
                        reps = reps,
                        rir = rir,
                    )
                if (!persistOk) {
                    _events.send(WorkoutUiEvent.SetLogFailed(slotKey = slotKey))
                    return@withLock
                }

                overlay = overlay.copy(
                    loggedSets =
                        overlay.loggedSets +
                            (slotId to LoggedSetValue(slotId, normalizedWeight, reps, rir)),
                )
                recomputeRecommendations()
                render()
                persistOverlay()
                if (wasAlreadyLogged) {
                    _events.send(WorkoutUiEvent.SetAmended(slotKey = slotKey))
                } else {
                    // First complete — rest timer / focus / complete sheet.
                    _events.send(WorkoutUiEvent.SetLogged(slotKey = slotKey))
                }
            }
        }
    }

    /**
     * Single write path: update existing Room row when present, otherwise insert.
     * Returns false on failure (message already set).
     */
    private suspend fun persistLoggedSet(
        session: TrainingSessionId,
        slot: SetSlot,
        weight: Weight,
        reps: Int,
        rir: Int?,
    ): Boolean {
        val dbSet = findLoggedSetForSlot(session, slot)
        if (dbSet != null) {
            val updateResult =
                updateLoggedSet(
                    dbSet.copy(weight = weight, reps = reps, rir = rir),
                )
            if (updateResult.isFailure) {
                _userMessage.value = MSG_LOG_SET_FAILED
                return false
            }
            return true
        }
        val insertResult =
            logSetUseCase(
                LogSetCommand(
                    sessionId = session,
                    liftId = slot.liftId,
                    workoutLiftId = slot.workoutLiftId,
                    setIndex = slot.position,
                    weight = weight,
                    reps = reps,
                    rir = rir,
                    completedAt = Instant.now(),
                    // Session-only lifts (overlay add / ad-hoc) are not on the template yet;
                    // stamp live slot shape so LoggedLiftContext is complete without a lookup.
                    setType = slot.setType,
                    repMin = slot.repMin,
                    repMax = slot.repMax,
                    rirGoal = slot.rirGoal,
                ),
            )
        if (insertResult.isFailure) {
            _userMessage.value = MSG_LOG_SET_FAILED
            return false
        }
        return true
    }

    private suspend fun findLoggedSetForSlot(
        session: TrainingSessionId,
        slot: SetSlot,
    ): LoggedSet? {
        val dbSets = getLoggedSetsForSession(session)
        return dbSets.firstOrNull {
            it.liftContext.workoutLiftId == slot.workoutLiftId && it.setIndex == slot.position
        }
    }

    fun unlogSet(slotKey: String) {
        val session = cachedSessionId ?: return
        val slot = resolveSlotByKey(slotKey) ?: return
        val slotId = slot.slotId
        viewModelScope.launch {
            setLogMutex.withLock {
                val dbSet = findLoggedSetForSlot(session, slot)
                if (dbSet != null) {
                    val deleteResult = deleteLoggedSet(dbSet.id)
                    if (deleteResult.isFailure) {
                        _userMessage.value = MSG_UNLOG_SET_FAILED
                        return@withLock
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
        // DeleteSet also drops restSecondsOverrides[slotId] (domain apply). Undo does not
        // restore rest overrides — they are not on the undo stack.
        overlay = overlay.apply(StructuralMutation.DeleteSet(slotId))
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

        if (slotId in overlay.loggedSets) {
            val session = cachedSessionId
            if (session != null) {
                viewModelScope.launch {
                    val dbSet = findLoggedSetForSlot(session, slot)
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
            swappedLifts = overlay.swappedLifts + (workoutLiftId to newLiftId),
        )
        // Snapshot previous/recs are keyed by template history; recompute them for the
        // performed lift so the card does not keep the pre-swap baselines (D3).
        // Session-added / ad-hoc lifts: merge applies swappedLifts for display + log;
        // recomputeRecommendations rebinds live recs (keep dynamicBaselines so slots stay).
        viewModelScope.launch {
            val workout = baselineWorkout
            val templateLift = workout?.workoutLifts?.firstOrNull { it.id == workoutLiftId }
            if (templateLift != null) {
                mergeSnapshotForLifts(listOf(templateLift.copy(liftId = newLiftId)))
            }
            recomputeRecommendations()
            render()
            persistOverlay()
        }
    }

    /**
     * Regenerates progression-snapshot entries for every in-session swap whose catalog id
     * differs from the template, then merges those entries into [snapshotBySlot].
     */
    private suspend fun refreshSnapshotForSwappedLifts(workout: Workout) {
        val effective =
            workout.workoutLifts.mapNotNull { lift ->
                val swappedId = overlay.swappedLifts[lift.id] ?: return@mapNotNull null
                if (swappedId == lift.liftId) return@mapNotNull null
                lift.copy(liftId = swappedId)
            }
        if (effective.isEmpty()) return
        mergeSnapshotForLifts(effective)
    }

    /**
     * Runs [generateSnapshot] for [lifts] and overwrites matching slot entries in
     * [snapshotBySlot]. Other lifts' snapshot entries are left intact. On failure the
     * pre-existing baselines are kept so the card does not lose set rows.
     */
    private suspend fun mergeSnapshotForLifts(lifts: List<WorkoutLift>) {
        if (lifts.isEmpty()) return
        val programId = cachedProgramId ?: return
        val workoutId = activeWorkoutId ?: return
        generateSnapshot(
            SnapshotRequest(
                programId = programId,
                workoutId = workoutId,
                lifts = lifts,
                loadTypesById = liftsById.mapValues { it.value.loadType },
                incrementTiersById = liftsById.mapValues { (_, lift) -> lift.incrementTiers },
            ),
        ).onSuccess { snapshot ->
            if (snapshot.bySlot.isNotEmpty()) {
                snapshotBySlot = snapshotBySlot + snapshot.bySlot
            }
        }
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
        // Effective (merged) groups/rest flags before removal — includes template values and
        // session-added OverlayAddedLift groups, not only existing override map entries.
        val linkableBefore = getLinkableLifts()
        val currentLifts = linkableBefore.filterNot { it.workoutLiftId == workoutLiftId.value }
        val cleaned = cleanupSupersetGroups(currentLifts)
        val (newGroupIdOverrides, newRestTimerOverrides) =
            LiveWorkoutOverlayOps.differentialSupersetOverrides(
                prior = linkableBefore.map { it.toLinkableSupersetFields() },
                updated = cleaned.map { it.toLinkableSupersetFields() },
                existingGroupOverrides = overlay.supersetGroupIdOverrides - workoutLiftId,
                existingRestOverrides = overlay.useRestTimerBetweenLiftsOverrides - workoutLiftId,
            )

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
        val prior = getLinkableLifts()
        val updated = linkSupersetWithAdjacent(
            lifts = prior,
            workoutLiftId = workoutLiftId.value,
            direction = direction,
            nextGroupId = { WorkoutSupersetGroupId(java.util.UUID.randomUUID().toString()).value },
        )
        applySupersetLinkableUpdate(prior = prior, updated = updated)
    }

    fun removeFromSuperset(workoutLiftId: WorkoutLiftId) {
        val prior = getLinkableLifts()
        val updated = unlinkLiftFromSuperset(prior, workoutLiftId.value)
        applySupersetLinkableUpdate(prior = prior, updated = updated)
    }

    /**
     * Differential override maps + dual-source stamp onto session-added lifts. Explicit null
     * group keys clear template/addedLift groups on merge (see [LiveWorkoutOverlayOps.merge]).
     */
    private fun applySupersetLinkableUpdate(
        prior: List<com.stratum.core.ui.component.SupersetLinkableLiftState>,
        updated: List<com.stratum.core.ui.component.SupersetLinkableLiftState>,
    ) {
        val (groupOverrides, restOverrides) =
            LiveWorkoutOverlayOps.differentialSupersetOverrides(
                prior = prior.map { it.toLinkableSupersetFields() },
                updated = updated.map { it.toLinkableSupersetFields() },
                existingGroupOverrides = overlay.supersetGroupIdOverrides,
                existingRestOverrides = overlay.useRestTimerBetweenLiftsOverrides,
            )
        overlay = overlay.copy(
            supersetGroupIdOverrides = groupOverrides,
            useRestTimerBetweenLiftsOverrides = restOverrides,
            addedLifts =
                LiveWorkoutOverlayOps.applyGroupOverridesToAddedLifts(
                    addedLifts = overlay.addedLifts,
                    groupOverrides = groupOverrides,
                    restOverrides = restOverrides,
                ),
        )
        recomputeRecommendations()
        render()
        persistOverlay()
    }

    private fun com.stratum.core.ui.component.SupersetLinkableLiftState.toLinkableSupersetFields() =
        LiveWorkoutOverlayOps.LinkableSupersetFields(
            workoutLiftId = workoutLiftId,
            supersetGroupId = supersetGroupId,
            useRestTimerBetweenLifts = useRestTimerBetweenLifts,
        )

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
                // Hard rule: only PROGRAM_WORKOUT sessions may write the program template.
                // Ad-hoc baselines are synthetic (route id = session id) — never SaveWorkout.
                val templateToSave =
                    if (saveTemplate && canSaveTemplateChanges && !templateSavedThisComplete) {
                        workout
                    } else {
                        null
                    }
                if (templateToSave != null) {
                    val updated = applyOverlayToWorkout(templateToSave, overlay)
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
                // Arm before finalize so observe(null session) cannot flash NoActiveSession
                // while NavHost is still composing this route during the exit transition.
                retainLoadedUiForExit = true
                val finalizeResult = finalizeTrainingSession(sessionId)
                if (finalizeResult.isFailure) {
                    retainLoadedUiForExit = false
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

    /**
     * Abandons via [ProcessScopedAbandonTrainingSession] so a concurrent nav pop cannot cancel
     * the domain delete. Surfaces clear eagerly for instant notif UX;
     * [com.stratum.feature.workout.notification.DomainActiveSessionSurfaceSync] re-derives set/clear
     * from domain when the write lands. Navigation is via [WorkoutUiEvent.NavigateBackAfterAbandon]
     * only after success.
     */
    fun abandonSession() {
        val session = cachedSessionId
        if (session == null) {
            liveWorkoutSurfaces.clearSession()
            // Leaving intentionally — avoid NoActiveSession flash during pop when we had UI.
            if (_uiState.value is WorkoutUiState.Loaded) {
                retainLoadedUiForExit = true
            }
            resetLocalSessionState()
            viewModelScope.launch {
                manageWorkoutOverlay.clear()
                _events.send(WorkoutUiEvent.NavigateBackAfterAbandon)
            }
            return
        }
        val restoreWorkoutId = activeWorkoutId
        val restoreName =
            (_uiState.value as? WorkoutUiState.Loaded)?.workoutName
        val restoreStartedMs = sessionStartedAt?.toEpochMilli()
        // Arm before domain delete so observe(null) cannot flash NoActiveSession mid-transition.
        retainLoadedUiForExit = true
        processScopedAbandon.abandon(session) { result ->
            viewModelScope.launch {
                if (result.isFailure) {
                    retainLoadedUiForExit = false
                    _userMessage.value = MSG_ABANDON_FAILED
                    // Eager clear left surfaces empty while domain session remains — re-publish.
                    if (restoreWorkoutId != null && restoreName != null && restoreStartedMs != null) {
                        liveWorkoutSurfaces.activeWorkout.setActive(
                            workoutId = restoreWorkoutId.value,
                            workoutName = restoreName,
                            startedAtEpochMs = restoreStartedMs,
                        )
                    }
                    return@launch
                }
                resetLocalSessionState()
                manageWorkoutOverlay.clear()
                _events.send(WorkoutUiEvent.NavigateBackAfterAbandon)
            }
        }
    }

    private fun resetLocalSessionState() {
        sessionObservationJob?.cancel()
        sessionObservationJob = null
        activeWorkoutId = null
        snapshotLoaded = false
        templateLiftFingerprint = emptyList()
        structure = null
        snapshotBySlot = emptyMap()
        fatigueByLiftId = emptyMap()
        overlay = WorkoutOverlay()
        cachedProgramId = null
        cachedSessionId = null
        sessionStartedAt = null
        baselineWorkout = null
        canSaveTemplateChanges = false
        publishNoActiveSessionIfNeeded()
    }

    /**
     * Recomputes overlay-only recommendations for upcoming unlogged sets from the most
     * recent performed set per lift (D4). Delegates the pipeline to
     * [RecomputeRecommendationsUseCase]; never writes back into the snapshot.
     *
     * Merges with **empty** live recommendations so engine inputs are snapshot/program
     * prescriptions only — never prior overlay weights (avoids too-easy bump ratcheting).
     */
    private fun recomputeRecommendations() {
        // Best-effort: keep the last good recommendations if recompute throws.
        runCatching {
            val snapshotOnlyOverlay = overlay.copy(recommendations = emptyMap())
            val renderedForRecs = LiveWorkoutOverlayOps.merge(
                structure = structure,
                overlay = snapshotOnlyOverlay,
                snapshotBySlot = snapshotBySlot,
                liftsById = liftsById,
                liftNamesById = liftNamesById,
                mergeRenderedWorkout = mergeRenderedWorkout,
            )
            recomputeRecommendationsUseCase(renderedForRecs, liftsById, fatigueByLiftId)
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
        // Presentation name is historical: means "offer save-to-program-day", not raw overlay dirty.
        // Ad-hoc never offers template save (canSaveTemplateChanges == false).
        val hasDirtyEdits = overlay.isDirty && canSaveTemplateChanges
        val imageUrlByLiftId = liftsById.mapValues { (_, lift) -> lift.imageUris.firstOrNull() }
            .mapKeys { (id, _) -> id.value }
        val mapped = WorkoutUiMapper.map(
            rendered,
            startedAtMilli,
            hasIncompleteLifts = hasIncompleteLifts,
            hasDirtyEdits = hasDirtyEdits,
            imageUrlByLiftId = imageUrlByLiftId,
            displayUnit = displayUnit.value,
            displayUnitEpoch = displayUnitEpoch.value,
        )
        // Active-workout notification snapshot is derived from domain session by
        // DomainActiveSessionSurfaceSync — do not dual-publish from render().
        _uiState.value = mapped
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
