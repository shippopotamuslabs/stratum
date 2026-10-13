package com.stratum.feature.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.core.ui.component.SupersetLinkDirection
import com.stratum.domain.importing.ObserveLiftTrainingInsightsUseCase
import com.stratum.core.ui.component.EquipmentSwapOption
import com.stratum.core.ui.component.EquipmentSwapSheetState
import com.stratum.domain.lift.CatalogNoteInput
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.EquipmentPickResult
import com.stratum.domain.lift.GetLiftNoteListsUseCase
import com.stratum.domain.lift.ObserveEquipmentCatalogUseCase
import com.stratum.domain.lift.GetPastSessionNotesForLiftUseCase
import com.stratum.domain.lift.EquipmentPickAwaiter
import com.stratum.domain.lift.GetRecentlyUsedEquipmentForLiftUseCase
import com.stratum.domain.lift.SessionNoteInput
import com.stratum.domain.lift.WorkoutLiftNoteInput
import com.stratum.domain.lift.identity.LiftCatalogNoteId
import com.stratum.domain.lift.identity.LiftSessionNoteId
import com.stratum.domain.program.identity.WorkoutLiftNoteId
import com.stratum.feature.workout.ui.LiftNoteListsSaveRequest
import com.stratum.feature.workout.ui.NoteDraftUi
import com.stratum.feature.workout.ui.NoteListsUi
import com.stratum.domain.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.lift.ObserveWeightDisplayPreferencesUseCase
import com.stratum.domain.lift.OptionalNoteEdit
import com.stratum.domain.lift.SaveLiftNotesCommand
import com.stratum.domain.lift.SaveLiftNotesUseCase
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.logging.LogErrorUseCase
import com.stratum.domain.program.GetProgramByIdUseCase
import com.stratum.domain.program.SaveWorkoutUseCase
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.DeleteLoggedSetUseCase
import com.stratum.domain.training.FinalizeTrainingSessionUseCase
import com.stratum.domain.training.GetLoggedSetsForSessionUseCase
import com.stratum.domain.training.LogSetUseCase
import com.stratum.domain.training.ObserveCycleProgressUseCase
import com.stratum.domain.training.RewriteSessionLiftEquipmentUseCase
import com.stratum.domain.training.UpdateLoggedSetUseCase
import com.stratum.domain.workout.ApplyOverlayToWorkoutUseCase
import com.stratum.domain.workout.ComputeTemplateChangeSummaryUseCase
import com.stratum.domain.workout.GetSessionPinnedNoteListsUseCase
import com.stratum.domain.workout.EditSessionEvent
import com.stratum.domain.workout.EstimateAddedSetRepsUseCase
import com.stratum.domain.workout.GenerateProgressionSnapshotUseCase
import com.stratum.domain.workout.ManageWorkoutOverlayUseCase
import com.stratum.domain.workout.MergeRenderedWorkoutUseCase
import com.stratum.domain.workout.ProjectWorkoutStructureUseCase
import com.stratum.domain.workout.RecomputeRecommendationsUseCase
import com.stratum.domain.workout.SetSlotId
import com.stratum.domain.workout.WorkoutEditSession
import com.stratum.feature.workout.notification.LiveWorkoutSurfaces
import com.stratum.feature.workout.notification.RestTimerState
import com.stratum.feature.workout.ui.EquipmentRewritePromptUi
import com.stratum.feature.workout.ui.enrichTemplateChangeNames
import com.stratum.feature.workout.ui.EquipmentSwapUi
import com.stratum.feature.workout.ui.PastSessionNotesGroupUi
import com.stratum.feature.workout.ui.RenderedLiftUi
import com.stratum.feature.workout.ui.WorkoutUiEvent
import com.stratum.feature.workout.ui.WorkoutUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Screen ViewModel for the live workout session.
 *
 * Session overlay / structure / snapshot memory lives in [LiveWorkoutSessionActor] (single
 * writer via [SessionCommand] channel). This class owns UI StateFlows, process-scoped
 * abandon coordination, rest-timer exposure, and enqueues commands for the actor.
 *
 * The progression snapshot is computed when the actor applies a cycle snapshot and
 * recomputed on in-session exercise swap (and when a persisted overlay with swaps is
 * restored) so previous-column and baseline recommendations bind to the performed catalog
 * lift.
 */
@HiltViewModel
@Suppress("TooManyFunctions", "LongParameterList")
class WorkoutViewModel @Inject constructor(
    private val observeCycleProgress: ObserveCycleProgressUseCase,
    generateSnapshot: GenerateProgressionSnapshotUseCase,
    mergeRenderedWorkout: MergeRenderedWorkoutUseCase,
    projectStructure: ProjectWorkoutStructureUseCase,
    recomputeRecommendationsUseCase: RecomputeRecommendationsUseCase,
    estimateAddedSetReps: EstimateAddedSetRepsUseCase,
    logSetUseCase: LogSetUseCase,
    updateLoggedSet: UpdateLoggedSetUseCase,
    private val observeLiftCatalog: ObserveLiftCatalogUseCase,
    getLoggedSetsForSession: GetLoggedSetsForSessionUseCase,
    deleteLoggedSet: DeleteLoggedSetUseCase,
    private val processScopedAbandon: ProcessScopedAbandonTrainingSession,
    finalizeTrainingSession: FinalizeTrainingSessionUseCase,
    applyOverlayToWorkout: ApplyOverlayToWorkoutUseCase,
    computeTemplateChangeSummary: ComputeTemplateChangeSummaryUseCase,
    saveWorkoutUseCase: SaveWorkoutUseCase,
    getProgramById: GetProgramByIdUseCase,
    observeLiftTrainingInsights: ObserveLiftTrainingInsightsUseCase,
    private val manageWorkoutOverlay: ManageWorkoutOverlayUseCase,
    private val workoutEditSession: WorkoutEditSession,
    private val liveWorkoutSurfaces: LiveWorkoutSurfaces,
    observeWeightDisplayPreferences: ObserveWeightDisplayPreferencesUseCase,
    private val logError: LogErrorUseCase,
    private val saveLiftNotesUseCase: SaveLiftNotesUseCase,
    private val getPastSessionNotesForLift: GetPastSessionNotesForLiftUseCase,
    private val getLiftNoteLists: GetLiftNoteListsUseCase,
    getSessionPinnedNoteLists: GetSessionPinnedNoteListsUseCase,
    private val observeEquipmentCatalog: ObserveEquipmentCatalogUseCase,
    private val getRecentlyUsedEquipment: GetRecentlyUsedEquipmentForLiftUseCase,
    private val equipmentPickResult: EquipmentPickResult,
    private val rewriteSessionLiftEquipment: RewriteSessionLiftEquipmentUseCase,
) : ViewModel() {

    /**
     * Preferred **display** unit (Settings). Live log storage is always **Lb** (canonical).
     * Bumped via [displayUnitEpoch] so Compose draft stores clear on flip.
     */
    private val displayUnit = MutableStateFlow(Weight.WeightUnit.Lb)
    private val displayUnitEpoch = MutableStateFlow(0)

    /** Session frames from the runtime; equipment editing is always enabled. */
    private val sessionFrames = MutableStateFlow<WorkoutUiState>(WorkoutUiState.Loading)

    val uiState: StateFlow<WorkoutUiState> = sessionFrames.asStateFlow()

    /** Equipment catalog mirror for subheader names + the swap sheet full list. */
    private val equipmentCatalog = MutableStateFlow<List<Equipment>>(emptyList())

    /** Open equipment swap sheet, or null when closed. */
    // Declared above init: the pick collector launched below reads these eagerly.
    private val _equipmentSwap = MutableStateFlow<EquipmentSwapUi?>(null)
    val equipmentSwap: StateFlow<EquipmentSwapUi?> = _equipmentSwap.asStateFlow()

    /** Partial-swap prompt, or null when no decision is pending. */
    private val _equipmentRewritePrompt = MutableStateFlow<EquipmentRewritePromptUi?>(null)
    private var rewriteInFlight = false
    val equipmentRewritePrompt: StateFlow<EquipmentRewritePromptUi?> =
        _equipmentRewritePrompt.asStateFlow()

    /** Create-new arming: target card + pick generation snapshot (see [beginCreateEquipment]). */
    private val pickAwaiter = EquipmentPickAwaiter(equipmentPickResult)

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

    /** Guards double-tap complete; reset when finalize fails so the user can retry. */
    private var isCompleting: Boolean = false
    /**
     * After intentional complete/abandon, keep the last [WorkoutUiState.Loaded] frame so
     * NavHost exit transitions do not flash [WorkoutUiState.NoActiveSession] ("No active
     * session." + Back) while this route is still composed.
     */
    private var retainLoadedUiForExit: Boolean = false

    private var sessionObservationJob: Job? = null

    private val sessionEffects =
        object : SessionEffects {
            override fun publishUi(state: WorkoutUiState) {
                sessionFrames.value = attachEquipmentNames(state)
            }

            override fun publishUserMessage(message: String?) {
                _userMessage.value = message
            }

            override suspend fun emitEvent(event: WorkoutUiEvent) {
                _events.send(event)
            }

            override fun clearLiveSurfaces() {
                liveWorkoutSurfaces.clearSession()
            }

            override fun displayUnit(): Weight.WeightUnit = displayUnit.value

            override fun displayUnitEpoch(): Int = displayUnitEpoch.value

            override fun shouldRetainLoadedUi(): Boolean = retainLoadedUiForExit

            override fun isCompleting(): Boolean = isCompleting

            override fun setRetainLoadedUiForExit(retain: Boolean) {
                retainLoadedUiForExit = retain
            }

            override fun markCompleteAttemptFinished() {
                isCompleting = false
            }
        }

    private val session =
        LiveWorkoutSessionActor(
            scope = viewModelScope,
            effects = sessionEffects,
            generateSnapshot = generateSnapshot,
            mergeRenderedWorkout = mergeRenderedWorkout,
            projectStructure = projectStructure,
            recomputeRecommendationsUseCase = recomputeRecommendationsUseCase,
            estimateAddedSetReps = estimateAddedSetReps,
            logSetUseCase = logSetUseCase,
            updateLoggedSet = updateLoggedSet,
            getLoggedSetsForSession = getLoggedSetsForSession,
            deleteLoggedSet = deleteLoggedSet,
            finalizeTrainingSession = finalizeTrainingSession,
            applyOverlayToWorkout = applyOverlayToWorkout,
            computeTemplateChangeSummary = computeTemplateChangeSummary,
            saveWorkoutUseCase = saveWorkoutUseCase,
            getProgramById = getProgramById,
            observeLiftTrainingInsights = observeLiftTrainingInsights,
            manageWorkoutOverlay = manageWorkoutOverlay,
            getSessionPinnedNoteLists = getSessionPinnedNoteLists,
            logError = logError,
        )

    init {
        viewModelScope.launch {
            equipmentPickResult.result.collect { pick ->
                pickAwaiter.onPick(pick, _equipmentSwap.value?.workoutLiftId)?.let { (targetId, resolved) ->
                    resolved.equipmentId?.let { newId ->
                        swapEquipment(WorkoutLiftId(targetId), newId)
                        _equipmentSwap.value = null
                    }
                }
            }
        }
        viewModelScope.launch {
            observeEquipmentCatalog().collect { catalog ->
                equipmentCatalog.value = catalog
                // Re-attach names to the current frame + refresh an open sheet's full list.
                sessionFrames.value = attachEquipmentNames(sessionFrames.value)
                _equipmentSwap.value?.let { open ->
                    _equipmentSwap.value =
                        open.copy(
                            sheet =
                                open.sheet.copy(
                                    all = catalog.map { it.toSwapOption() },
                                ),
                        )
                }
            }
        }
        viewModelScope.launch {
            observeWeightDisplayPreferences()
                .catch {
                    // Keep the last display unit if DataStore throws; do not kill the session.
                }
                .collect { prefs ->
                    if (displayUnit.value == prefs.displayUnit) return@collect
                    displayUnit.value = prefs.displayUnit
                    displayUnitEpoch.value += 1
                    // Re-render so recs/logged amounts flip units; drafts reseed via epoch.
                    if (uiState.value is WorkoutUiState.Loaded) {
                        enqueue(SessionCommand.RenderOnly)
                    }
                }
        }
        viewModelScope.launch {
            workoutEditSession.events.collect { event ->
                when (event) {
                    is EditSessionEvent.LiftSelected -> {
                        val activeWorkoutId = session.activeWorkoutId()?.value
                        if (event.workoutId != activeWorkoutId) {
                            logError(
                                TAG,
                                "Ignored LiftSelected: event workoutId (${event.workoutId}) " +
                                    "does not match active session ($activeWorkoutId)",
                            )
                            return@collect
                        }
                        val targetLiftId = event.workoutLiftId
                        if (targetLiftId != null) {
                            swapLift(WorkoutLiftId(targetLiftId), LiftId(event.liftId))
                        } else {
                            addLifts(listOf(LiftId(event.liftId)), asSuperset = false)
                        }
                    }
                    is EditSessionEvent.LiftsSelected -> {
                        val activeWorkoutId = session.activeWorkoutId()?.value
                        if (event.workoutId != activeWorkoutId) {
                            logError(
                                TAG,
                                "Ignored LiftsSelected: event workoutId (${event.workoutId}) " +
                                    "does not match active session ($activeWorkoutId)",
                            )
                            return@collect
                        }
                        addLifts(
                            liftIds = event.liftIds.map { LiftId(it) },
                            asSuperset = event.asSuperset,
                        )
                    }
                    is EditSessionEvent.LiftsReordered -> {
                        val activeWorkoutId = session.activeWorkoutId()?.value
                        if (event.workoutId == activeWorkoutId) {
                            reorderLifts(event.orderedWorkoutLiftIds)
                        } else {
                            logError(
                                TAG,
                                "Ignored LiftsReordered: event workoutId (${event.workoutId}) " +
                                    "does not match active session ($activeWorkoutId)",
                            )
                        }
                    }
                }
            }
        }
    }

    fun userMessageShown() {
        _userMessage.value = null
    }

    /**
     * Quiets the "Adjust Equipment" chip for one lift card for the rest of the session.
     * Called only from the chip's X affordance — tapping the chip to open the editor
     * leaves it visible, and a real tier save resolves it in the session runtime.
     */
    fun dismissEquipmentAdjust(workoutLiftId: String) {
        enqueue(SessionCommand.DismissEquipmentAdjust(WorkoutLiftId(workoutLiftId)))
    }

    /**
     * Tap-time inference inputs for one live lift card's editor request (read of
     * session memory). The route builds the routed + seeded request from this; null
     * when the card is unknown (caller falls back to the unseeded request).
     */
    fun liveEquipmentSeed(workoutLiftId: String): LiveEquipmentSeed? =
        session.liveEquipmentSeed(WorkoutLiftId(workoutLiftId))

    fun startSession(workoutId: WorkoutId) {
        if (session.isLoadedFor(workoutId)) return
        retainLoadedUiForExit = false
        sessionFrames.value = WorkoutUiState.Loading
        sessionObservationJob?.cancel()
        session.stop()
        session.start()
        sessionObservationJob =
            viewModelScope.launch {
                session.send(SessionCommand.StartSession(workoutId))
                // Catalogs are combined so a mid-session lift merge (delete/hide source + overlay
                // remap) rebinds even when the program row fingerprint is unchanged, and the
                // equipment catalog rides the same snapshot (STR-128) so tier saves on the
                // equipment in use re-resolve the adjust chip atomically with the lifts.
                combine(
                    observeCycleProgress(),
                    observeLiftCatalog(),
                    observeEquipmentCatalog(),
                ) { progress, catalog, equipment ->
                    Triple(progress, catalog, equipment)
                }.collect { (progress, catalog, equipment) ->
                    val program = progress.program
                    val trainingSession = progress.activeSession
                    if (
                        program == null ||
                        trainingSession == null ||
                        !sessionMatchesRoute(trainingSession, workoutId)
                    ) {
                        session.send(
                            SessionCommand.ApplyCycleSnapshot(
                                routeWorkoutId = workoutId,
                                program = program,
                                session = trainingSession,
                                workout = null,
                                catalog = catalog,
                                equipment = equipment,
                            ),
                        )
                        return@collect
                    }
                    val workout = resolveSessionWorkout(trainingSession, program, workoutId)
                    if (workout == null) {
                        // Program template deleted mid-session: abandon orphan and surface empty.
                        if (session.cachedSessionId() != trainingSession.id) {
                            processScopedAbandon.abandon(trainingSession.id) { /* recovery — best effort */ }
                        }
                        session.send(
                            SessionCommand.ApplyCycleSnapshot(
                                routeWorkoutId = workoutId,
                                program = program,
                                session = trainingSession,
                                workout = null,
                                catalog = catalog,
                                equipment = equipment,
                            ),
                        )
                        return@collect
                    }
                    session.send(
                        SessionCommand.ApplyCycleSnapshot(
                            routeWorkoutId = workoutId,
                            program = program,
                            session = trainingSession,
                            workout = workout,
                            catalog = catalog,
                            equipment = equipment,
                        ),
                    )
                }
            }
    }

    /** Unit live set fields are shown/typed in (Settings preference). */
    fun currentDisplayUnit(): Weight.WeightUnit = displayUnit.value

    fun logSet(slotKey: String, weight: Weight, reps: Int, rir: Int?, partialReps: Int? = null) {
        enqueue(SessionCommand.LogSet(slotKey, weight, reps, rir, partialReps))
    }

    fun unlogSet(slotKey: String) {
        enqueue(SessionCommand.UnlogSet(slotKey))
    }

    fun addSet(afterSlotId: SetSlotId) {
        enqueue(SessionCommand.AddSet(afterSlotId))
    }

    /**
     * Adds a set after the last remaining set of [workoutLiftId].
     * UI entry point matching history/template "add set" affordance.
     */
    fun addSetForLift(workoutLiftId: String) {
        enqueue(SessionCommand.AddSetForLift(workoutLiftId))
    }

    fun deleteSet(slotId: SetSlotId) {
        enqueue(SessionCommand.DeleteSet(slotId))
    }

    /**
     * Deletes a set by presentation [slotKey]. No-ops when the owning lift has only one
     * remaining set (last-set delete is disabled in UI; lift removal uses [removeLift]).
     * Unlogs a completed set first so Room does not retain orphan rows.
     */
    fun deleteSetByKey(slotKey: String) {
        enqueue(SessionCommand.DeleteSetByKey(slotKey))
    }

    fun undo() {
        enqueue(SessionCommand.Undo)
    }

    fun generateMyoRepMiniSet(activationSlotId: SetSlotId) {
        enqueue(SessionCommand.GenerateMyoMini(activationSlotId))
    }

    fun addDropSet(afterSlotId: SetSlotId) {
        enqueue(SessionCommand.AddDropSet(afterSlotId))
    }

    /**
     * Deletes [slotId], cascading myo minis / following drops when required.
     * Callers that need a confirm dialog should check [requiresCascadeDeleteConfirm] first.
     */
    fun deleteSetWithCascade(slotId: SetSlotId) {
        enqueue(SessionCommand.DeleteSetWithCascade(slotId))
    }

    fun requiresCascadeDeleteConfirm(slotId: SetSlotId): Boolean =
        session.requiresCascadeDeleteConfirm(slotId)

    /**
     * True when changing [slotKey] to [kind] would delete auto-generated myo minis (and
     * their logs). UI should confirm before calling [setSetKind].
     */
    fun requiresMyoMiniDiscardConfirm(
        slotKey: String,
        kind: LiveSetKind,
    ): Boolean = session.requiresMyoMiniDiscardConfirm(slotKey, kind)

    /**
     * Changes the set type of [slotKey] (Standard / Myo floor / Myo match / Drop).
     * Auto-generated myo minis are blocked. Demoting a myo activation deletes its minis.
     */
    fun setSetKind(slotKey: String, kind: LiveSetKind) {
        enqueue(SessionCommand.SetSetKind(slotKey, kind))
    }

    fun swapLift(workoutLiftId: WorkoutLiftId, newLiftId: LiftId) {
        enqueue(SessionCommand.SwapLift(workoutLiftId, newLiftId))
    }

    fun swapEquipment(workoutLiftId: WorkoutLiftId, newEquipmentId: EquipmentId) {
        enqueue(SessionCommand.SwapEquipment(workoutLiftId, newEquipmentId))
    }

    /** Opens the equipment swap sheet for one lift card; recents load asynchronously. */
    fun openEquipmentSwap(workoutLiftId: String) {
        val loaded = sessionFrames.value as? WorkoutUiState.Loaded ?: return
        val lift = loaded.lifts.firstOrNull { it.workoutLiftId == workoutLiftId } ?: return
        val catalog = equipmentCatalog.value
        _equipmentSwap.value =
            EquipmentSwapUi(
                workoutLiftId = workoutLiftId,
                liftId = lift.liftId,
                liftName = lift.liftName,
                sheet =
                    EquipmentSwapSheetState(
                        current =
                            lift.sessionEquipmentId?.let { id ->
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
     * to Manage Equipment in selection mode; the created piece is applied as a session
     * swap when the pick publishes.
     */
    fun beginCreateEquipment() {
        val target = _equipmentSwap.value ?: return
        pickAwaiter.arm(target.workoutLiftId)
    }

    /**
     * Applies the selection to the session overlay (remaining unlogged sets) + closes.
     * When the lift has logged sets this session, the partial-swap prompt decides their
     * fate first (STR-122) instead of applying immediately.
     */
    fun selectSwapEquipment(optionId: String) {
        val target = _equipmentSwap.value ?: return
        pickAwaiter.disarm()
        val loaded = sessionFrames.value as? WorkoutUiState.Loaded
        val lift = loaded?.lifts?.firstOrNull { it.workoutLiftId == target.workoutLiftId }
        val loggedCount = lift?.sets?.count { it.isComplete } ?: 0
        if (lift != null && loggedCount > 0) {
            _equipmentSwap.value = null
            _equipmentRewritePrompt.value =
                EquipmentRewritePromptUi(
                    workoutLiftId = target.workoutLiftId,
                    liftId = target.liftId,
                    liftName = target.liftName,
                    oldName = lift.sessionEquipmentName,
                    newId = optionId,
                    newName = target.sheet.all.firstOrNull { it.id == optionId }?.name
                        ?: target.sheet.current?.name
                        ?: optionId,
                    loggedCount = loggedCount,
                )
            return
        }
        swapEquipment(WorkoutLiftId(target.workoutLiftId), EquipmentId(optionId))
        _equipmentSwap.value = null
    }

    /** Prompt choice: logged sets keep the old equipment; only remaining sets swap. */
    fun keepLoggedSetsEquipment() {
        if (rewriteInFlight) return
        val prompt = _equipmentRewritePrompt.value ?: return
        _equipmentRewritePrompt.value = null
        swapEquipment(WorkoutLiftId(prompt.workoutLiftId), EquipmentId(prompt.newId))
    }

    /**
     * Prompt choice: rewrite the logged sets' equipment to the new piece, then swap.
     * Rewrite runs first so a failure leaves nothing applied; the prompt stays for
     * retry. Choices are locked while the rewrite is in flight.
     */
    fun confirmRewriteLoggedSets() {
        val prompt = _equipmentRewritePrompt.value ?: return
        if (rewriteInFlight) return
        val sessionId = session.cachedSessionId()
        if (sessionId == null) {
            _userMessage.value = MSG_EQUIPMENT_REWRITE_FAILED
            return
        }
        rewriteInFlight = true
        viewModelScope.launch {
            val result =
                rewriteSessionLiftEquipment(
                    sessionId = sessionId,
                    liftId = LiftId(prompt.liftId),
                    newEquipmentId = EquipmentId(prompt.newId),
                )
            rewriteInFlight = false
            if (result.isSuccess) {
                swapEquipment(WorkoutLiftId(prompt.workoutLiftId), EquipmentId(prompt.newId))
                _equipmentRewritePrompt.value = null
            } else {
                _userMessage.value = MSG_EQUIPMENT_REWRITE_FAILED
            }
        }
    }

    /** Prompt dismissed without choosing: no swap is applied. */
    fun dismissRewritePrompt() {
        if (rewriteInFlight) return
        _equipmentRewritePrompt.value = null
    }

    private fun attachEquipmentNames(state: WorkoutUiState): WorkoutUiState {
        if (state !is WorkoutUiState.Loaded) return state
        val names = equipmentCatalog.value.associate { it.id.value to it.name }
        if (names.isEmpty()) return state
        return state.copy(
            lifts =
                state.lifts.map { lift ->
                    lift.copy(sessionEquipmentName = lift.sessionEquipmentId?.let(names::get))
                },
            templateChanges =
                state.templateChanges?.let { enrichTemplateChangeNames(it, names) },
        )
    }

    private fun Equipment.toSwapOption(): EquipmentSwapOption =
        EquipmentSwapOption(id = id.value, name = name)

    fun addLifts(liftIds: List<LiftId>, asSuperset: Boolean = false) {
        enqueue(SessionCommand.AddLifts(liftIds, asSuperset))
    }

    fun startReorderLifts() {
        val currentRendered = session.mergeRendered()
        val items =
            currentRendered.lifts.map {
                com.stratum.domain.workout.ReorderLiftItem(it.workoutLiftId.value, it.liftName)
            }
        workoutEditSession.setReorderLifts(items)
    }

    fun reorderLifts(orderedWorkoutLiftIds: List<String>) {
        enqueue(SessionCommand.ReorderLifts(orderedWorkoutLiftIds))
    }

    fun removeLift(workoutLiftId: WorkoutLiftId) {
        enqueue(SessionCommand.RemoveLift(workoutLiftId))
    }

    fun linkSuperset(workoutLiftId: WorkoutLiftId, direction: SupersetLinkDirection) {
        enqueue(SessionCommand.LinkSuperset(workoutLiftId, direction))
    }

    fun removeFromSuperset(workoutLiftId: WorkoutLiftId) {
        enqueue(SessionCommand.RemoveFromSuperset(workoutLiftId))
    }

    fun setUseRestTimerBetweenLifts(workoutLiftId: WorkoutLiftId, enabled: Boolean) {
        enqueue(SessionCommand.SetUseRestTimerBetweenLifts(workoutLiftId, enabled))
    }

    /**
     * Applies rest overrides for Configure Rest Times confirm.
     * Keys are presentation [slotKey] strings; marks the overlay dirty so save-template persists.
     */
    fun setRestSecondsForSlots(updates: Map<String, Int>) {
        if (updates.isEmpty()) return
        enqueue(SessionCommand.SetRestSecondsForSlots(updates))
    }

    suspend fun noteLists(workoutLiftId: String, liftId: String): NoteListsUi {
        val sessionId = session.cachedSessionId() ?: return NoteListsUi()
        val slotId = WorkoutLiftId(workoutLiftId)
        val lists =
            getLiftNoteLists(
                sessionId,
                slotId,
                LiftId(liftId),
                // Overlay-added slots have no `workout_lifts` row: resolve the
                // workout tab from the explicit overlay entry when present so
                // reopening the editor shows (and preserves) existing notes.
                overlayWorkoutNotes = session.cachedOverlayWorkoutNotes(slotId),
            )
        return NoteListsUi(
            session = lists.session.map { NoteDraftUi(id = it.id.value, body = it.body) },
            workout = lists.workout.map { NoteDraftUi(id = it.id.value, body = it.body) },
            lift = lists.lift.map { NoteDraftUi(id = it.id.value, body = it.body) },
        )
    }

    suspend fun pastSessionNotes(liftId: String): List<PastSessionNotesGroupUi> {
        val notes =
            getPastSessionNotesForLift(
                LiftId(liftId),
                session.cachedSessionId(),
            )
        val formatter =
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())
        // Notes arrive newest-session first with in-session append order; groupBy
        // preserves first-seen key order, so groups stay newest-first.
        return notes.groupBy { it.sessionId }.map { (_, sessionNotes) ->
            val first = sessionNotes.first()
            PastSessionNotesGroupUi(
                dateLabel = formatter.format(first.sessionStartedAt),
                workoutName = first.workoutName.orEmpty(),
                bodies = sessionNotes.map { it.body },
            )
        }
    }

    fun saveLiftNotes(request: LiftNoteListsSaveRequest) {
        if (!request.isDirty) return
        val sessionId = session.cachedSessionId() ?: return
        viewModelScope.launch {
            val result =
                saveLiftNotesUseCase(
                    SaveLiftNotesCommand(
                        sessionId = sessionId,
                        workoutLiftId = WorkoutLiftId(request.workoutLiftId),
                        liftId = LiftId(request.liftId),
                        sessionNotes = request.sessionNotes?.map { it.toSessionInput() },
                        workoutNotes = request.workoutNotes?.map { it.toWorkoutInput() },
                        liftNotes = request.liftNotes?.map { it.toCatalogInput() },
                    ),
                ).getOrElse {
                    _userMessage.value = MSG_NOTES_SAVE_FAILED
                    return@launch
                }
            if (result.workoutNoteNeedsOverlay) {
                enqueue(
                    SessionCommand.SetWorkoutLiftPinnedNotes(
                        workoutLiftId = WorkoutLiftId(request.workoutLiftId),
                        bodies = request.workoutNotes.orEmpty().map { it.body },
                    ),
                )
            }
            // Saved pin lists live in the repos now; refresh the session's template-side
            // copies so the lift cards render every pin. Session-tab-only saves skip this.
            if (request.workoutNotes != null || request.liftNotes != null) {
                enqueue(SessionCommand.RefreshPinnedNotes)
            }
        }
    }

    fun completeWorkout(saveTemplate: Boolean) {
        if (isCompleting) return
        if (session.cachedSessionId() == null || session.activeWorkoutId() == null) return
        isCompleting = true
        // Suspend send so rebind close cannot silently drop complete.
        viewModelScope.launch {
            try {
                session.send(SessionCommand.CompleteWorkout(saveTemplate))
            } catch (_: Exception) {
                isCompleting = false
                _userMessage.value = MSG_SESSION_COMMAND_DROPPED
            }
        }
    }

    /** Non-critical UI mutators; dispatches to the session actor or surfaces a message if closed. */
    private fun enqueue(cmd: SessionCommand) {
        if (!session.trySend(cmd)) {
            viewModelScope.launch {
                try {
                    session.send(cmd)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    logError(TAG, "Session command dropped: ${error.message}", error)
                    _userMessage.value = MSG_SESSION_COMMAND_DROPPED
                }
            }
        }
    }

    /**
     * Abandons via [ProcessScopedAbandonTrainingSession] so a concurrent nav pop cannot cancel
     * the domain delete. Surfaces clear eagerly for instant notif UX;
     * [com.stratum.feature.workout.notification.DomainActiveSessionSurfaceSync] re-derives set/clear
     * from domain when the write lands. Navigation is via [WorkoutUiEvent.NavigateBackAfterAbandon]
     * only after success.
     *
     * Domain delete stays process-scoped (not an actor command) by design. Before it starts,
     * [SessionCommand.BeginAbandon] freezes actor mutators so logs cannot race the delete;
     * success → [SessionCommand.FinalizeAbandonLocal], failure → [SessionCommand.CancelAbandon].
     */
    fun abandonSession() {
        val trainingSessionId = session.cachedSessionId()
        if (trainingSessionId == null) {
            liveWorkoutSurfaces.clearSession()
            // Leaving intentionally — avoid NoActiveSession flash during pop when we had UI.
            if (uiState.value is WorkoutUiState.Loaded) {
                retainLoadedUiForExit = true
            }
            viewModelScope.launch {
                sessionObservationJob?.cancel()
                sessionObservationJob = null
                session.send(SessionCommand.BeginAbandon)
                session.send(SessionCommand.FinalizeAbandonLocal)
                manageWorkoutOverlay.clear()
                _events.send(WorkoutUiEvent.NavigateBackAfterAbandon)
            }
            return
        }
        val restoreWorkoutId = session.activeWorkoutId()
        val restoreName =
            (uiState.value as? WorkoutUiState.Loaded)?.workoutName
        val restoreStartedMs = session.sessionStartedAtEpochMs()
        // Arm before domain delete so observe(null) cannot flash NoActiveSession mid-transition.
        retainLoadedUiForExit = true
        viewModelScope.launch {
            // Freeze mutators before process-scoped delete can race in-flight log commands.
            session.send(SessionCommand.BeginAbandon)
            processScopedAbandon.abandon(trainingSessionId) { result ->
                viewModelScope.launch {
                    if (result.isFailure) {
                        session.send(SessionCommand.CancelAbandon)
                        retainLoadedUiForExit = false
                        _userMessage.value = MSG_ABANDON_FAILED
                        // Eager clear left surfaces empty while domain session remains — re-publish.
                        if (
                            restoreWorkoutId != null &&
                            restoreName != null &&
                            restoreStartedMs != null
                        ) {
                            liveWorkoutSurfaces.activeWorkout.setActive(
                                workoutId = restoreWorkoutId.value,
                                workoutName = restoreName,
                                startedAtEpochMs = restoreStartedMs,
                            )
                        }
                        return@launch
                    }
                    sessionObservationJob?.cancel()
                    sessionObservationJob = null
                    session.send(SessionCommand.FinalizeAbandonLocal)
                    manageWorkoutOverlay.clear()
                    _events.send(WorkoutUiEvent.NavigateBackAfterAbandon)
                }
            }
        }
    }

    private companion object {
        const val TAG = "WorkoutViewModel"
        const val MSG_ABANDON_FAILED = "Couldn't cancel the workout. Try again."
        const val MSG_SESSION_COMMAND_DROPPED =
            "Couldn't update the workout. Leave and re-open the session."
        const val MSG_NOTES_SAVE_FAILED = "Couldn't save notes. Try again."
        const val MSG_EQUIPMENT_REWRITE_FAILED = "Couldn't update logged sets. Try again."
    }
}

private fun NoteDraftUi.toSessionInput(): SessionNoteInput =
    SessionNoteInput(id = id?.let(::LiftSessionNoteId), body = body)

private fun NoteDraftUi.toWorkoutInput(): WorkoutLiftNoteInput =
    WorkoutLiftNoteInput(id = id?.let(::WorkoutLiftNoteId), body = body)

private fun NoteDraftUi.toCatalogInput(): CatalogNoteInput =
    CatalogNoteInput(id = id?.let(::LiftCatalogNoteId), body = body)
