package com.stratum.feature.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.importing.FatigueDecay
import com.stratum.domain.importing.ObserveLiftTrainingInsightsUseCase
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.ObserveWeightDisplayPreferencesUseCase
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.resolvedDefaultRestSeconds
import com.stratum.domain.lift.storageWeightFromDisplay
import com.stratum.domain.workout.intensifier.RestDefaultForTransition

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
import com.stratum.domain.program.coerceForCompound
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
                    sessionStateMutex.withLock { renderLocked() }
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
     * Fingerprint of the last structure build (lift ids + set types/kinds). When merge
     * re-points catalog ids or intensifier authoring changes set types, this changes and we
     * rebuild structure + progression snapshot.
     */
    private var templateLiftFingerprint: List<String> = emptyList()
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

    /**
     * Single writer lock for session memory ([overlay], [structure], snapshot, revisions).
     * All collectors and mutators take this before reading/writing that state. Not re-entrant —
     * never call a `*Locked` helper that re-acquires it.
     */
    private val sessionStateMutex = Mutex()

    /**
     * Monotonic generation for [overlay]. Bumped on every [commitOverlay]. Compared to
     * [lastPersistedRevision] so a stale DataStore snapshot cannot clobber unflushed memory.
     */
    private var overlayRevision: Long = 0L

    /** [overlayRevision] of the last successful DataStore flush of the current overlay value. */
    private var lastPersistedRevision: Long = 0L

    fun startSession(workoutId: WorkoutId) {
        if (activeWorkoutId == workoutId && snapshotLoaded) return
        activeWorkoutId = workoutId
        retainLoadedUiForExit = false
        cachedProgramId = null
        snapshotLoaded = false
        templateLiftFingerprint = emptyList()
        _uiState.value = WorkoutUiState.Loading
        sessionObservationJob?.cancel()
        sessionObservationJob =
            viewModelScope.launch {
                sessionStateMutex.withLock {
                    resetOverlayLocked(WorkoutOverlay())
                    snapshotBySlot = emptyMap()
                    structure = null
                    canSaveTemplateChanges = false
                }
                fatigueByLiftId = observeLiftTrainingInsights()
                // Catalog is combined so a mid-session lift merge (delete/hide source + overlay
                // remap) rebinds even when the program row fingerprint is unchanged.
                combine(
                    observeCycleProgress(),
                    observeLiftCatalog(),
                ) { progress, catalog ->
                    progress to catalog
                }.collect { (progress, catalog) ->
                    // Catalog maps are process-wide read models — update outside the session lock.
                    liftNamesById = catalog.associate { lift -> lift.id to lift.name }
                    liftsById = catalog.associateBy { lift -> lift.id }

                    val program = progress.program
                    val session = progress.activeSession
                    if (program == null || session == null || !sessionMatchesRoute(session, workoutId)) {
                        sessionStateMutex.withLock { clearToNoActiveSessionLocked() }
                        return@collect
                    }
                    val workout = resolveSessionWorkout(session, program, workoutId)
                    if (workout == null) {
                        // Program template deleted mid-session: abandon orphan and surface empty.
                        if (cachedSessionId != session.id) {
                            processScopedAbandon.abandon(session.id) { /* recovery — best effort */ }
                        }
                        // Keep session id so we do not re-abandon the same orphan every collect.
                        sessionStateMutex.withLock {
                            clearToNoActiveSessionLocked(retainSessionId = session.id)
                        }
                        return@collect
                    }

                    // Suspend I/O (snapshot, overlay load) while holding the session lock so a
                    // concurrent log/mutation cannot interleave mid-rebuild.
                    sessionStateMutex.withLock {
                        cachedProgramId = program.id
                        cachedSessionId = session.id
                        sessionStartedAt = session.startedAt
                        baselineWorkout = workout
                        // Kind is source of truth — never save synthetic AD_HOC baselines as program days.
                        canSaveTemplateChanges = session.kind == TrainingSessionKind.PROGRAM_WORKOUT
                        val fingerprint = templateStructureFingerprint(workout)
                        val needsStructureRebuild =
                            !snapshotLoaded || fingerprint != templateLiftFingerprint
                        if (needsStructureRebuild) {
                            val isInitialLoad = !snapshotLoaded
                            val projected = projectStructure(
                                workout,
                                liftNamesById,
                                loadTypesById = liftsById.mapValues { it.value.loadType },
                                isCompoundById = liftsById.mapValues { it.value.isCompound },
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
                                installRestoredOverlayLocked(
                                    restoreOverlayOnInitialLoad(
                                        sessionId = session.id,
                                        projected = projected,
                                    ),
                                )
                            } else {
                                val persisted = manageWorkoutOverlay.load(session.id)
                                if (persisted != null && shouldAdoptPersistedOverlay(persisted)) {
                                    installRestoredOverlayLocked(persisted)
                                }
                            }
                            // In-session swaps (or restored ones) rebind previous/recs to the
                            // performed catalog lift — template snapshot alone is pre-swap.
                            refreshSnapshotForSwappedLifts(workout)
                            templateLiftFingerprint = fingerprint
                            snapshotLoaded = true
                        } else {
                            // External overlay remaps (e.g. lift merge) — adopt only when memory
                            // has no unflushed work and the store signature actually changed.
                            val persisted = manageWorkoutOverlay.load(session.id)
                            if (
                                persisted != null &&
                                catalogIdentitySignature(persisted) !=
                                    catalogIdentitySignature(overlay) &&
                                shouldAdoptPersistedOverlay(persisted)
                            ) {
                                installRestoredOverlayLocked(persisted)
                                refreshSnapshotForSwappedLifts(workout)
                            }
                        }
                        recomputeRecommendationsLocked()
                        renderLocked()
                    }
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
     *
     * Caller must hold [sessionStateMutex] (or be on a single-threaded cold path).
     */
    private fun clearToNoActiveSessionLocked(
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
        resetOverlayLocked(WorkoutOverlay())
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
            // Phase 1 (session lock): Room log + overlay mutation + DataStore flush.
            // Recompute/render stay out of this critical section so other mutators are not
            // blocked on recommendation math after the durable write is ordered.
            val logOutcome =
                sessionStateMutex.withLock {
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
                        return@withLock LogSetOutcome.Failed
                    }

                    var next =
                        overlay.copy(
                            loggedSets =
                                overlay.loggedSets +
                                    (slotId to LoggedSetValue(slotId, normalizedWeight, reps, rir)),
                        )
                    next =
                        LiveIntensifierOverlayOps.applyMyoLifecycleAfterLog(
                            structure = structure,
                            overlay = next,
                            loggedSlotId = slotId,
                        )
                    commitOverlayLocked(next)
                    flushOverlayLocked()
                    LogSetOutcome.Ok(wasAlreadyLogged = wasAlreadyLogged)
                }

            when (logOutcome) {
                LogSetOutcome.Failed ->
                    _events.send(WorkoutUiEvent.SetLogFailed(slotKey = slotKey))
                is LogSetOutcome.Ok -> {
                    sessionStateMutex.withLock {
                        recomputeRecommendationsLocked()
                        renderLocked()
                    }
                    if (logOutcome.wasAlreadyLogged) {
                        _events.send(WorkoutUiEvent.SetAmended(slotKey = slotKey))
                    } else {
                        _events.send(WorkoutUiEvent.SetLogged(slotKey = slotKey))
                    }
                }
            }
        }
    }

    private sealed interface LogSetOutcome {
        data object Failed : LogSetOutcome
        data class Ok(val wasAlreadyLogged: Boolean) : LogSetOutcome
    }

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
            sessionStateMutex.withLock {
                val dbSet = findLoggedSetForSlot(session, slot)
                if (dbSet != null) {
                    val deleteResult = deleteLoggedSet(dbSet.id)
                    if (deleteResult.isFailure) {
                        _userMessage.value = MSG_UNLOG_SET_FAILED
                        return@withLock
                    }
                }
                commitOverlayLocked(
                    overlay.copy(loggedSets = overlay.loggedSets - slotId),
                )
                recomputeRecommendationsLocked()
                renderLocked()
                flushOverlayLocked()
            }
        }
    }

    fun addSet(afterSlotId: SetSlotId) {
        viewModelScope.launch {
            sessionStateMutex.withLock {
                val anchor = resolveSlot(afterSlotId) ?: return@withLock
                val newPosition = anchor.position + 1
                val added =
                    SetSlot(
                        slotId =
                            SetSlotId.Overlay(
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
                val estimatedReps =
                    estimateAddedSetReps(
                        previousRecommendation =
                            baseline ?: SlotRecommendation(afterSlotId, coldWeight, anchor.repMin),
                        nextSetIndex = newPosition,
                        perLiftSlope = fatigue.slope,
                        perLiftIntercept = fatigue.intercept,
                    )
                val addedWeight = baseline?.recommendedWeight ?: coldWeight
                val addedRecommendation =
                    SlotRecommendation(added.slotId, addedWeight, estimatedReps)
                commitOverlayLocked(
                    overlay
                        .apply(StructuralMutation.AddSet(afterSlotId = afterSlotId, newSlot = added))
                        .copy(
                            dynamicBaselines =
                                overlay.dynamicBaselines + (added.slotId to addedRecommendation),
                        ),
                )
                recomputeRecommendationsLocked()
                renderLocked()
                flushOverlayLocked()
            }
        }
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
        viewModelScope.launch {
            sessionStateMutex.withLock {
                // DeleteSet also drops restSecondsOverrides[slotId] (domain apply). Undo does not
                // restore rest overrides — they are not on the undo stack.
                commitOverlayLocked(overlay.apply(StructuralMutation.DeleteSet(slotId)))
                recomputeRecommendationsLocked()
                renderLocked()
                flushOverlayLocked()
            }
        }
    }

    /**
     * Deletes a set by presentation [slotKey]. No-ops when the owning lift has only one
     * remaining set (last-set delete is disabled in UI; lift removal uses [removeLift]).
     * Unlogs a completed set first so Room does not retain orphan rows.
     */
    fun deleteSetByKey(slotKey: String) {
        viewModelScope.launch {
            sessionStateMutex.withLock {
                val slot = resolveSlotByKey(slotKey) ?: return@withLock
                val remainingForLift = visibleSlotsForLift(slot.workoutLiftId)
                if (remainingForLift.size <= 1) return@withLock

                val slotId = slot.slotId
                val workoutLiftId = slot.workoutLiftId
                val session = cachedSessionId

                if (slotId in overlay.loggedSets && session != null) {
                    val dbSet = findLoggedSetForSlot(session, slot)
                    if (dbSet != null) {
                        val deleteResult = deleteLoggedSet(dbSet.id)
                        if (deleteResult.isFailure) {
                            _userMessage.value = MSG_UNLOG_SET_FAILED
                            return@withLock
                        }
                    }
                    // Re-check after suspend — another mutation may have removed this set.
                    if (slotId in overlay.deletedSlotIds) return@withLock
                    if (visibleSlotsForLift(workoutLiftId).size <= 1) return@withLock
                    commitOverlayLocked(
                        overlay
                            .copy(loggedSets = overlay.loggedSets - slotId)
                            .apply(StructuralMutation.DeleteSet(slotId)),
                    )
                } else {
                    if (slotId in overlay.loggedSets) {
                        commitOverlayLocked(
                            overlay
                                .copy(loggedSets = overlay.loggedSets - slotId)
                                .apply(StructuralMutation.DeleteSet(slotId)),
                        )
                    } else {
                        commitOverlayLocked(overlay.apply(StructuralMutation.DeleteSet(slotId)))
                    }
                }
                recomputeRecommendationsLocked()
                renderLocked()
                flushOverlayLocked()
            }
        }
    }

    fun undo() {
        viewModelScope.launch {
            sessionStateMutex.withLock {
                commitOverlayLocked(overlay.undo())
                recomputeRecommendationsLocked()
                renderLocked()
                flushOverlayLocked()
            }
        }
    }

    fun generateMyoRepMiniSet(activationSlotId: SetSlotId) {
        viewModelScope.launch {
            sessionStateMutex.withLock {
                commitOverlayLocked(
                    LiveIntensifierOverlayOps.applyMyoLifecycleAfterLog(
                        structure = structure,
                        overlay = overlay,
                        loggedSlotId = activationSlotId,
                    ),
                )
                recomputeRecommendationsLocked()
                renderLocked()
                flushOverlayLocked()
            }
        }
    }

    fun addDropSet(afterSlotId: SetSlotId) {
        viewModelScope.launch {
            sessionStateMutex.withLock {
                val anchor = resolveSlot(afterSlotId) ?: return@withLock
                val nextRest =
                    RestDefaultForTransition.restAfter(
                        nextType = null,
                        liftDefaultRestSeconds =
                            liftsById[anchor.liftId]?.resolvedDefaultRestSeconds() ?: 120,
                    )
                val drop =
                    SetSlot(
                        slotId =
                            SetSlotId.Overlay(
                                OverlaySetInstanceId("drop-${overlay.addedSlots.size + 1}"),
                            ),
                        workoutLiftId = anchor.workoutLiftId,
                        liftId = anchor.liftId,
                        position = anchor.position + 1,
                        setType = com.stratum.domain.program.SetType.DropSet,
                        isTopSet = false,
                        repMin = 0,
                        repMax = 0,
                        rirGoal = 0,
                        restSeconds = nextRest,
                    )
                // Force zero rest on the anchor when it now leads into a drop.
                val withAnchorRest =
                    if (anchor.restSeconds != 0) {
                        overlay.copy(
                            restSecondsOverrides =
                                overlay.restSecondsOverrides + (afterSlotId to 0),
                        )
                    } else {
                        overlay
                    }
                commitOverlayLocked(
                    withAnchorRest.apply(
                        StructuralMutation.AddDropSet(afterSlotId = afterSlotId, newSlot = drop),
                    ),
                )
                recomputeRecommendationsLocked()
                renderLocked()
                flushOverlayLocked()
            }
        }
    }

    /**
     * Deletes [slotId], cascading myo minis / following drops when required.
     * Callers that need a confirm dialog should check [requiresCascadeDeleteConfirm] first.
     */
    fun deleteSetWithCascade(slotId: SetSlotId) {
        viewModelScope.launch {
            sessionStateMutex.withLock {
                val targets =
                    LiveIntensifierOverlayOps.cascadeDeleteTargets(structure, overlay, slotId)
                var next = overlay
                for (id in targets) {
                    next =
                        next.copy(loggedSets = next.loggedSets - id)
                            .apply(StructuralMutation.DeleteSet(id))
                }
                commitOverlayLocked(next)
                recomputeRecommendationsLocked()
                renderLocked()
                flushOverlayLocked()
            }
        }
    }

    fun requiresCascadeDeleteConfirm(slotId: SetSlotId): Boolean =
        LiveIntensifierOverlayOps.requiresCascadeConfirm(structure, overlay, slotId)

    /**
     * True when changing [slotKey] to [kind] would delete auto-generated myo minis (and
     * their logs). UI should confirm before calling [setSetKind].
     */
    fun requiresMyoMiniDiscardConfirm(
        slotKey: String,
        kind: LiveSetKind,
    ): Boolean {
        val slot = resolveSlotByKey(slotKey) ?: return false
        return LiveSetTypeOverlayOps.requiresMyoMiniDiscardConfirm(
            structure = structure,
            overlay = overlay,
            slotId = slot.slotId,
            newKind = kind,
        )
    }

    /**
     * Changes the set type of [slotKey] (Standard / Myo floor / Myo match / Drop).
     * Auto-generated myo minis are blocked. Demoting a myo activation deletes its minis.
     */
    fun setSetKind(slotKey: String, kind: LiveSetKind) {
        viewModelScope.launch {
            sessionStateMutex.withLock {
                val slot = resolveSlotByKey(slotKey) ?: return@withLock
                val scheme = progressionSchemeForLift(slot.workoutLiftId)
                val loadType =
                    liftsById[slot.liftId]?.loadType ?: LoadType.Weighted
                val liftDefaultRest =
                    liftsById[slot.liftId]?.resolvedDefaultRestSeconds()
                        ?: DEFAULT_LIFT_REST_SECONDS

                // Unlog minis from Room before dropping them from the overlay.
                val minisToDrop =
                    if (
                        LiveSetTypeOverlayOps.requiresMyoMiniDiscardConfirm(
                            structure,
                            overlay,
                            slot.slotId,
                            kind,
                        )
                    ) {
                        LiveSetTypeOverlayOps.miniSlotsDiscardedOnDemote(
                            structure,
                            overlay,
                            slot.slotId,
                        )
                    } else {
                        emptyList()
                    }
                val session = cachedSessionId
                if (session != null && minisToDrop.isNotEmpty()) {
                    for (miniId in minisToDrop) {
                        val miniSlot = resolveSlot(miniId) ?: continue
                        val dbSet = findLoggedSetForSlot(session, miniSlot)
                        if (dbSet != null) {
                            val deleteResult = deleteLoggedSet(dbSet.id)
                            if (deleteResult.isFailure) {
                                _userMessage.value = MSG_UNLOG_SET_FAILED
                                return@withLock
                            }
                        }
                    }
                }

                val next =
                    LiveSetTypeOverlayOps.applySetKind(
                        structure = structure,
                        overlay = overlay,
                        slotId = slot.slotId,
                        newKind = kind,
                        scheme = scheme,
                        loadType = loadType,
                        liftDefaultRestSeconds = liftDefaultRest,
                    ) ?: return@withLock
                commitOverlayLocked(next)
                recomputeRecommendationsLocked()
                renderLocked()
                flushOverlayLocked()
            }
        }
    }

    fun swapLift(workoutLiftId: WorkoutLiftId, newLiftId: LiftId) {
        viewModelScope.launch {
            sessionStateMutex.withLock {
                commitOverlayLocked(
                    overlay.copy(
                        swappedLifts = overlay.swappedLifts + (workoutLiftId to newLiftId),
                    ),
                )
                // Snapshot previous/recs are keyed by template history; recompute them for the
                // performed lift so the card does not keep the pre-swap baselines (D3).
                val workout = baselineWorkout
                val templateLift = workout?.workoutLifts?.firstOrNull { it.id == workoutLiftId }
                if (templateLift != null) {
                    mergeSnapshotForLifts(listOf(templateLift.copy(liftId = newLiftId)))
                }
                recomputeRecommendationsLocked()
                renderLocked()
                flushOverlayLocked()
            }
        }
    }

    /**
     * Regenerates progression-snapshot entries for every in-session swap whose catalog id
     * differs from the template, then merges those entries into [snapshotBySlot].
     * Caller must hold [sessionStateMutex].
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
     * Caller must hold [sessionStateMutex].
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
        viewModelScope.launch {
            sessionStateMutex.withLock {
                val currentIds = merge().lifts.map { it.workoutLiftId.value }
                commitOverlayLocked(
                    LiveWorkoutOverlayOps.addLifts(
                        overlay = overlay,
                        currentLiftIds = currentIds,
                        liftIds = liftIds,
                        asSuperset = asSuperset,
                        liftsById = liftsById,
                        zeroWeight = Weight(0.0, Weight.WeightUnit.Lb),
                    ),
                )
                recomputeRecommendationsLocked()
                renderLocked()
                flushOverlayLocked()
            }
        }
    }

    fun startReorderLifts() {
        val currentRendered = merge()
        val items =
            currentRendered.lifts.map {
                com.stratum.domain.workout.ReorderLiftItem(it.workoutLiftId.value, it.liftName)
            }
        workoutEditSession.setReorderLifts(items)
    }

    fun reorderLifts(orderedWorkoutLiftIds: List<String>) {
        viewModelScope.launch {
            sessionStateMutex.withLock {
                commitOverlayLocked(overlay.copy(orderedLiftIds = orderedWorkoutLiftIds))
                recomputeRecommendationsLocked()
                renderLocked()
                flushOverlayLocked()
            }
        }
    }

    fun removeLift(workoutLiftId: WorkoutLiftId) {
        viewModelScope.launch {
            sessionStateMutex.withLock {
                // Effective (merged) groups/rest flags before removal — includes template values and
                // session-added OverlayAddedLift groups, not only existing override map entries.
                val linkableBefore = getLinkableLifts()
                val currentLifts =
                    linkableBefore.filterNot { it.workoutLiftId == workoutLiftId.value }
                val cleaned = cleanupSupersetGroups(currentLifts)
                val (newGroupIdOverrides, newRestTimerOverrides) =
                    LiveWorkoutOverlayOps.differentialSupersetOverrides(
                        prior = linkableBefore.map { it.toLinkableSupersetFields() },
                        updated = cleaned.map { it.toLinkableSupersetFields() },
                        existingGroupOverrides = overlay.supersetGroupIdOverrides - workoutLiftId,
                        existingRestOverrides =
                            overlay.useRestTimerBetweenLiftsOverrides - workoutLiftId,
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

                commitOverlayLocked(
                    LiveWorkoutOverlayOps.removeLift(
                        overlay = overlay,
                        workoutLiftId = workoutLiftId,
                        cleanedGroupOverrides = newGroupIdOverrides,
                        cleanedRestOverrides = newRestTimerOverrides,
                        resolveSlot = ::resolveSlot,
                        baselineLiftIdsInOrder = baselineLiftIdsInOrder,
                    ),
                )
                recomputeRecommendationsLocked()
                renderLocked()
                flushOverlayLocked()
            }
        }
    }

    fun linkSuperset(workoutLiftId: WorkoutLiftId, direction: SupersetLinkDirection) {
        viewModelScope.launch {
            sessionStateMutex.withLock {
                val prior = getLinkableLifts()
                val updated =
                    linkSupersetWithAdjacent(
                        lifts = prior,
                        workoutLiftId = workoutLiftId.value,
                        direction = direction,
                        nextGroupId = {
                            WorkoutSupersetGroupId(java.util.UUID.randomUUID().toString()).value
                        },
                    )
                applySupersetLinkableUpdateLocked(prior = prior, updated = updated)
            }
        }
    }

    fun removeFromSuperset(workoutLiftId: WorkoutLiftId) {
        viewModelScope.launch {
            sessionStateMutex.withLock {
                val prior = getLinkableLifts()
                val updated = unlinkLiftFromSuperset(prior, workoutLiftId.value)
                applySupersetLinkableUpdateLocked(prior = prior, updated = updated)
            }
        }
    }

    /**
     * Differential override maps + dual-source stamp onto session-added lifts. Explicit null
     * group keys clear template/addedLift groups on merge (see [LiveWorkoutOverlayOps.merge]).
     * Caller must hold [sessionStateMutex].
     */
    private suspend fun applySupersetLinkableUpdateLocked(
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
        commitOverlayLocked(
            overlay.copy(
                supersetGroupIdOverrides = groupOverrides,
                useRestTimerBetweenLiftsOverrides = restOverrides,
                addedLifts =
                    LiveWorkoutOverlayOps.applyGroupOverridesToAddedLifts(
                        addedLifts = overlay.addedLifts,
                        groupOverrides = groupOverrides,
                        restOverrides = restOverrides,
                    ),
            ),
        )
        recomputeRecommendationsLocked()
        renderLocked()
        flushOverlayLocked()
    }

    private fun com.stratum.core.ui.component.SupersetLinkableLiftState.toLinkableSupersetFields() =
        LiveWorkoutOverlayOps.LinkableSupersetFields(
            workoutLiftId = workoutLiftId,
            supersetGroupId = supersetGroupId,
            useRestTimerBetweenLifts = useRestTimerBetweenLifts,
        )

    fun setUseRestTimerBetweenLifts(workoutLiftId: WorkoutLiftId, enabled: Boolean) {
        viewModelScope.launch {
            sessionStateMutex.withLock {
                commitOverlayLocked(
                    overlay.copy(
                        useRestTimerBetweenLiftsOverrides =
                            overlay.useRestTimerBetweenLiftsOverrides + (workoutLiftId to enabled),
                    ),
                )
                recomputeRecommendationsLocked()
                renderLocked()
                flushOverlayLocked()
            }
        }
    }

    /**
     * Applies rest overrides for Configure Rest Times confirm.
     * Keys are presentation [slotKey] strings; marks the overlay dirty so save-template persists.
     */
    fun setRestSecondsForSlots(updates: Map<String, Int>) {
        if (updates.isEmpty()) return
        viewModelScope.launch {
            sessionStateMutex.withLock {
                val next =
                    LiveWorkoutOverlayOps.mergeRestSecondsOverrides(
                        overlay = overlay,
                        updates = updates,
                        resolveSlotByKey = ::resolveSlotByKey,
                    ) ?: return@withLock
                commitOverlayLocked(overlay.copy(restSecondsOverrides = next))
                renderLocked()
                flushOverlayLocked()
            }
        }
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
            viewModelScope.launch {
                resetLocalSessionState()
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

    private suspend fun resetLocalSessionState() {
        sessionStateMutex.withLock {
            sessionObservationJob?.cancel()
            sessionObservationJob = null
            activeWorkoutId = null
            snapshotLoaded = false
            templateLiftFingerprint = emptyList()
            structure = null
            snapshotBySlot = emptyMap()
            fatigueByLiftId = emptyMap()
            resetOverlayLocked(WorkoutOverlay())
            cachedProgramId = null
            cachedSessionId = null
            sessionStartedAt = null
            baselineWorkout = null
            canSaveTemplateChanges = false
            publishNoActiveSessionIfNeeded()
        }
    }

    /**
     * Bumps [overlayRevision] and replaces [overlay]. Caller must hold [sessionStateMutex].
     */
    private fun commitOverlayLocked(next: WorkoutOverlay) {
        overlay = next
        overlayRevision += 1
    }

    /**
     * Install overlay restored from DataStore / Room rebuild and treat it as already flushed
     * (no unpersisted local work). Caller must hold [sessionStateMutex].
     */
    private fun installRestoredOverlayLocked(next: WorkoutOverlay) {
        overlay = next
        overlayRevision += 1
        lastPersistedRevision = overlayRevision
    }

    /** Reset overlay to empty and align revisions. Caller must hold [sessionStateMutex]. */
    private fun resetOverlayLocked(next: WorkoutOverlay) {
        overlay = next
        overlayRevision = 0L
        lastPersistedRevision = 0L
    }

    /**
     * True when [persisted] may replace memory. Rejects any store snapshot while memory has
     * unflushed work ([overlayRevision] > [lastPersistedRevision]).
     * Caller must hold [sessionStateMutex] (or otherwise observe a stable pair of revisions).
     */
    private fun shouldAdoptPersistedOverlay(persisted: WorkoutOverlay): Boolean {
        if (overlayRevision > lastPersistedRevision) return false
        return catalogIdentitySignature(persisted) != catalogIdentitySignature(overlay)
    }

    /**
     * Writes the current overlay and advances [lastPersistedRevision] to match.
     * Caller must hold [sessionStateMutex] for the whole call so revision cannot race.
     */
    private suspend fun flushOverlayLocked() {
        val id = cachedSessionId ?: return
        val rev = overlayRevision
        manageWorkoutOverlay.save(id, overlay)
        lastPersistedRevision = rev
    }

    /**
     * Recomputes overlay-only recommendations for upcoming unlogged sets from the most
     * recent performed set per lift (D4). Never writes back into the snapshot.
     * Caller must hold [sessionStateMutex].
     */
    private fun recomputeRecommendationsLocked() {
        // Best-effort: keep the last good recommendations if recompute throws.
        runCatching {
            val snapshotOnlyOverlay = overlay.copy(recommendations = emptyMap())
            val renderedForRecs =
                LiveWorkoutOverlayOps.merge(
                    structure = structure,
                    overlay = snapshotOnlyOverlay,
                    snapshotBySlot = snapshotBySlot,
                    liftsById = liftsById,
                    liftNamesById = liftNamesById,
                    mergeRenderedWorkout = mergeRenderedWorkout,
                )
            recomputeRecommendationsUseCase(renderedForRecs, liftsById, fatigueByLiftId)
        }.onSuccess { recommendations ->
            // Recs are derived presentation — do not bump overlayRevision (not structural).
            overlay = overlay.copy(recommendations = recommendations)
        }
    }

    /**
     * Fingerprint that rebuilds structure when lift identity **or** set-type/kind/count changes
     * (myo/drop authoring must refresh the live projection).
     */
    private fun templateStructureFingerprint(workout: com.stratum.domain.program.Workout): List<String> =
        workout.workoutLifts.flatMap { lift ->
            listOf("lift:${lift.id.value}:${lift.liftId.value}") +
                lift.sets.map { set ->
                    "set:${set.id.value}:${set.position}:${set.setType.name}:" +
                        "${set.myoRepKind?.name.orEmpty()}:${set.isTopSet}"
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

    /** Caller must hold [sessionStateMutex]. */
    private fun renderLocked() {
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
        // DomainActiveSessionSurfaceSync — do not dual-publish from renderLocked().
        _uiState.value = mapped
    }

    private fun resolveSlot(slotId: SetSlotId): SetSlot? =
        LiveWorkoutOverlayOps.resolveSlot(structure, overlay, slotId)

    private fun resolveSlotByKey(slotKey: String): SetSlot? =
        LiveWorkoutOverlayOps.resolveSlotByKey(structure, overlay, slotKey)

    private fun progressionSchemeForLift(workoutLiftId: WorkoutLiftId): com.stratum.domain.program.ProgressionScheme {
        // Structure projection already coerces isolation Linear → Double (D8).
        structure?.lifts?.firstOrNull { it.workoutLiftId == workoutLiftId }?.progressionScheme
            ?.let { return it }
        val added = overlay.addedLifts.firstOrNull { it.workoutLiftId == workoutLiftId }
        if (added != null) {
            val isCompound = liftsById[added.liftId]?.isCompound ?: false
            return added.progressionScheme.coerceForCompound(isCompound)
        }
        return com.stratum.domain.program.ProgressionScheme.Double
    }

    private companion object {
        const val DEFAULT_LIFT_REST_SECONDS = 120
        const val MSG_LOG_SET_FAILED = "Couldn't save that set. Try again."
        const val MSG_UNLOG_SET_FAILED = "Couldn't remove that set. Try again."
        const val MSG_COMPLETE_FAILED = "Couldn't finish the workout. Try again."
        const val MSG_COMPLETE_FINALIZE_AFTER_TEMPLATE =
            "Template saved, but finishing the session failed. Tap Complete again."
        const val MSG_ABANDON_FAILED = "Couldn't cancel the workout. Try again."
    }
}
