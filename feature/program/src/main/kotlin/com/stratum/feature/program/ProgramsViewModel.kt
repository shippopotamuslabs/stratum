package com.stratum.feature.program

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.application.lift.ObserveLiftCatalogUseCase
import com.stratum.application.program.ComputeProgramMuscleVolumeUseCase
import com.stratum.application.program.ObserveProgramUseCase
import com.stratum.application.program.ObserveProgramsUiPreferencesUseCase
import com.stratum.application.program.SetExpandedWorkoutIdUseCase
import com.stratum.application.program.ProgramsUiPreferenceResult
import com.stratum.application.diagnostics.OperationalDiagnostic
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.training.StartTrainingSessionResult
import com.stratum.application.training.AbandonTrainingSessionResult
import com.stratum.application.training.AbandonTrainingSessionUseCase
import com.stratum.application.training.MinimizedWorkoutBar
import com.stratum.application.training.ObserveCycleProgressUseCase
import com.stratum.application.training.ObserveMinimizedWorkoutBarUseCase
import com.stratum.application.training.RecordRestDaySkipResult
import com.stratum.application.training.RecordRestDaySkipUseCase
import com.stratum.application.training.RecordWorkoutSkipResult
import com.stratum.application.training.RecordWorkoutSkipUseCase
import com.stratum.domain.training.StartTrainingSessionRequest
import com.stratum.application.training.StartTrainingSessionUseCase
import com.stratum.application.training.UnskipWorkoutResult
import com.stratum.application.training.UnskipWorkoutUseCase
import com.stratum.domain.training.isOrphanedAgainstActiveProgram
import com.stratum.domain.training.liveWorkoutRouteId
import com.stratum.application.workout.ManageWorkoutOverlayUseCase
import com.stratum.feature.program.ui.MinimizedWorkoutState
import com.stratum.feature.program.ui.ProgramsStartAction
import com.stratum.feature.program.ui.ProgramsUiEvent
import com.stratum.feature.program.ui.ProgramsUiState
import com.stratum.feature.program.ui.findNextWorkoutId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Exposes the persisted program as [ProgramsUiState] for the read-only Programs screen. Maps the
 * domain program to presentation view states and resolves lift ids through the catalog so the UI
 * shows human-readable lift names (ARCHITECTURE.md §4.3).
 *
 * Also owns the lift-swap bottom sheet state ([liftSwapSheet]). The sheet is opened with
 * [openLiftSwap] (targeting a workout lift); the ranked candidate stream is produced by
 * [RankSwapCandidatesUseCase] and the filter overlay is reduced here against [LiftSwapFilters]
 * (ARCHITECTURE.md §4.4 — only transient state lives in the ViewModel). Actual program-mutation
 * after a swap is a follow-up slice; this ViewModel emits the swap selection via
 * [swapSelections] for the route to persist.
 */
// STR-216: STR-207 §2 exception — `userMessage` transient-snackbar side-flow
// alongside `programsUiState`; bless-vs-migrate decision recorded on STR-216.
@Suppress("ForbiddenMultipleStateFlowInViewModel")
@HiltViewModel
class ProgramsViewModel @Inject constructor(
    observeProgram: ObserveProgramUseCase,
    private val observeLiftCatalog: ObserveLiftCatalogUseCase,
    computeProgramMuscleVolume: ComputeProgramMuscleVolumeUseCase,
    private val startTrainingSession: StartTrainingSessionUseCase,
    private val observeCycleProgress: ObserveCycleProgressUseCase,
    private val abandonTrainingSession: AbandonTrainingSessionUseCase,
    private val observeMinimizedBar: ObserveMinimizedWorkoutBarUseCase,
    private val recordWorkoutSkip: RecordWorkoutSkipUseCase,
    private val recordRestDaySkip: RecordRestDaySkipUseCase,
    private val unskipWorkout: UnskipWorkoutUseCase,
    private val manageWorkoutOverlay: ManageWorkoutOverlayUseCase,
    observeProgramsUiPreferences: ObserveProgramsUiPreferencesUseCase,
    private val setExpandedWorkoutId: SetExpandedWorkoutIdUseCase,
    private val diagnosticDrain: DiagnosticDrain,
) : ViewModel() {

    /** Transient snackbar copy; cleared via [userMessageShown] after the UI presents it. */
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    fun userMessageShown() {
        _userMessage.value = null
    }

    private fun showUserMessage(message: String) {
        _userMessage.value = message
    }

    init {
        // Safety net for already-orphaned sessions (mid-session delete / program switch without
        // confirm). Prefer confirm-before-discard at mutation sites; this only cleans ghosts so the
        // bottom bar does not offer a non-resumable session.
        viewModelScope.launch {
            observeCycleProgress()
                .map { progress ->
                    val session = progress.activeSession
                    if (session != null && session.isOrphanedAgainstActiveProgram(progress.program)) {
                        session.id
                    } else {
                        null
                    }
                }
                .distinctUntilChanged()
                .collect { orphanSessionId ->
                    if (orphanSessionId != null) {
                        val abandonResult = abandonTrainingSession(orphanSessionId)
                        if (abandonResult is AbandonTrainingSessionResult.PersistenceFailed) {
                            diagnosticDrain.drain(abandonResult.diagnostics)
                            showUserMessage(MSG_ABANDON_FAILED)
                        }
                    }
                }
        }
    }

    private val programBase: Flow<ProgramsUiState> =
        combine(
            observeProgram(),
            observeLiftCatalog(),
            observeCycleProgress(),
        ) { program, lifts, progress ->
            val volumeSummaries =
                program?.let { activeProgram ->
                    computeProgramMuscleVolume(activeProgram, lifts)
                }.orEmpty()
            program.toProgramsUiState(
                liftNamesById = lifts.associate { lift -> lift.id.value to lift.name },
                volumeSummaries = volumeSummaries,
                completedWorkoutIds = progress.completedWorkoutIdsThisCycle
                    .map { it.value }
                    .toSet(),
                activeWorkoutId = progress.activeSession?.workoutId?.value,
                completedRestSlotIndices = progress.completedRestSlotIndicesThisCycle,
            )
        }

    /**
     * Single public state flow for the Programs screen (ARCHITECTURE.md §4.1). The
     * minimized-bar assembly lives in [ObserveMinimizedWorkoutBarUseCase] (§4.4); this
     * pipeline only folds the bar read model and the persisted expansion id into the
     * presentation state at the boundary. Expansion stays independent of program load
     * so editing/swapping lifts does not force a different card open.
     */
    val programsUiState: StateFlow<ProgramsUiState> =
        combine(
            programBase,
            observeMinimizedBar(),
            observeProgramsUiPreferences(),
        ) { base, bar, prefs ->
            val activeBar = bar?.toMinimizedWorkoutState()
            when (base) {
                is ProgramsUiState.Empty -> base.copy(activeBar = activeBar)
                is ProgramsUiState.Loaded ->
                    base.copy(
                        activeBar = activeBar,
                        expandedWorkoutId = prefs.expandedWorkoutId,
                    )
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ProgramsUiState.Empty(),
        )

    private fun MinimizedWorkoutBar.toMinimizedWorkoutState(): MinimizedWorkoutState =
        MinimizedWorkoutState(
            workoutId = workoutId,
            workoutName = workoutName,
            nextLiftName = nextLiftName,
            startedAtEpochMilli = startedAtEpochMilli,
        )

    fun setExpandedWorkout(workoutId: String?) {
        viewModelScope.launch {
            val result = setExpandedWorkoutId(workoutId)
            if (result is ProgramsUiPreferenceResult.PersistenceFailed) {
                diagnosticDrain.drain(result.diagnostics)
            }
        }
    }

    private val _events = Channel<ProgramsUiEvent>(Channel.BUFFERED)
    val events: Flow<ProgramsUiEvent> = _events.receiveAsFlow()

    fun startNextWorkout() {
        val loaded = programsUiState.value as? ProgramsUiState.Loaded ?: return
        val nextId = findNextWorkoutId(loaded.scheduleItems) ?: return
        startProgramWorkout(nextId)
    }

    fun startProgramWorkout(workoutId: String) {
        viewModelScope.launch {
            startOrResumeProgramWorkout(WorkoutId(workoutId))
        }
    }

    fun startEmptyWorkout() {
        viewModelScope.launch {
            when (val result = startTrainingSession(StartTrainingSessionRequest.AdHocEmpty)) {
                is StartTrainingSessionResult.Success -> {
                    // Ad-hoc has no template workoutId — route key is the session id.
                    _events.send(ProgramsUiEvent.NavigateToWorkout(result.session.liveWorkoutRouteId()))
                }
                is StartTrainingSessionResult.ActiveSessionExists -> {
                    handleActiveSessionConflict()
                }
                is StartTrainingSessionResult.PersistenceFailed -> {
                    drainStartFailure(result)
                    showUserMessage(MSG_START_WORKOUT_FAILED)
                }
                else -> {
                    showUserMessage(MSG_START_WORKOUT_FAILED)
                }
            }
        }
    }

    private suspend fun startOrResumeProgramWorkout(workoutId: WorkoutId) {
        when (val result = startTrainingSession(StartTrainingSessionRequest.ProgramWorkout(workoutId))) {
            is StartTrainingSessionResult.Success -> {
                _events.send(ProgramsUiEvent.NavigateToWorkout(workoutId.value))
            }
            is StartTrainingSessionResult.ActiveSessionExists -> {
                handleActiveSessionConflict()
            }
            is StartTrainingSessionResult.PersistenceFailed -> {
                drainStartFailure(result)
                showUserMessage(MSG_START_WORKOUT_FAILED)
            }
            else -> {
                showUserMessage(MSG_START_WORKOUT_FAILED)
            }
        }
    }

    /**
     * STR-237: the domain-owned outcome carries the failure as diagnostic
     * values (cause only); render the legacy tag/message here and drain
     * exactly once — the same shape as EditWorkoutStartCoordinator.
     */
    private fun drainStartFailure(result: StartTrainingSessionResult.PersistenceFailed) {
        result.diagnostics.forEach { diagnostic ->
            diagnosticDrain.drain(
                OperationalDiagnostic(
                    tag = EditWorkoutStartCoordinator.TAG_START_TRAINING_SESSION,
                    message = "Failed to start training session: ${diagnostic.cause.message}",
                    cause = diagnostic.cause,
                ),
            )
        }
    }

    private suspend fun handleActiveSessionConflict() {
        val progress = observeCycleProgress().first()
        val routeId = progress.activeSession?.liveWorkoutRouteId()
        if (routeId != null) {
            // Resume whatever is live (program or ad-hoc) instead of deadlocking.
            _events.send(ProgramsUiEvent.NavigateToWorkout(routeId))
        } else {
            showUserMessage(MSG_START_WORKOUT_FAILED)
        }
    }

    fun abandonActiveSession() {
        viewModelScope.launch {
            val progress = observeCycleProgress().first()
            val activeSession = progress.activeSession ?: return@launch
            val result = abandonTrainingSession(activeSession.id)
            if (result is AbandonTrainingSessionResult.PersistenceFailed) {
                diagnosticDrain.drain(result.diagnostics)
            }
            // Success is silent — cancel from the chip is intentional, not a failure to report.
            if (result != AbandonTrainingSessionResult.Success) {
                showUserMessage(MSG_ABANDON_FAILED)
            } else {
                // Match live-screen abandon: drop process-scoped overlay so a new session
                // cannot restore a dead session's structure from DataStore.
                diagnosticDrain.drain(manageWorkoutOverlay.clear())
            }
        }
    }

    fun skipWorkout(workoutId: String) {
        viewModelScope.launch {
            val result = recordWorkoutSkip(WorkoutId(workoutId))
            if (result is RecordWorkoutSkipResult.PersistenceFailed) {
                diagnosticDrain.drain(result.diagnostics)
            }
            if (result !is RecordWorkoutSkipResult.Success) {
                showUserMessage(MSG_SKIP_FAILED)
            }
        }
    }

    fun unskipWorkout(workoutId: String) {
        viewModelScope.launch {
            val result = unskipWorkout(WorkoutId(workoutId))
            if (result is UnskipWorkoutResult.PersistenceFailed) {
                diagnosticDrain.drain(result.diagnostics)
            }
            if (result != UnskipWorkoutResult.Success) {
                showUserMessage(MSG_UNSKIP_FAILED)
            }
        }
    }

    fun skipRestDay(slotIndex: Int) {
        viewModelScope.launch {
            val result = recordRestDaySkip(slotIndex)
            if (result is RecordRestDaySkipResult.PersistenceFailed) {
                diagnosticDrain.drain(result.diagnostics)
            }
            if (result !is RecordRestDaySkipResult.Success) {
                showUserMessage(MSG_SKIP_FAILED)
            }
        }
    }

    fun unskipRestDay(slotIndex: Int) {
        viewModelScope.launch {
            val result = unskipWorkout.unskipRestSlot(slotIndex)
            if (result is UnskipWorkoutResult.PersistenceFailed) {
                diagnosticDrain.drain(result.diagnostics)
            }
            if (result != UnskipWorkoutResult.Success) {
                showUserMessage(MSG_UNSKIP_FAILED)
            }
        }
    }

    fun confirmDiscardAndStart(action: ProgramsStartAction) {
        viewModelScope.launch {
            val progress = observeCycleProgress().first()
            progress.activeSession?.let { session ->
                val abandonResult = abandonTrainingSession(session.id)
                if (abandonResult is AbandonTrainingSessionResult.PersistenceFailed) {
                    diagnosticDrain.drain(abandonResult.diagnostics)
                }
                if (abandonResult != AbandonTrainingSessionResult.Success) {
                    showUserMessage(MSG_ABANDON_FAILED)
                    return@launch
                }
                diagnosticDrain.drain(manageWorkoutOverlay.clear())
            }
            when (action) {
                is ProgramsStartAction.StartWorkout ->
                    startOrResumeProgramWorkout(WorkoutId(action.workoutId))
                ProgramsStartAction.StartNextWorkout -> {
                    val loaded = programsUiState.value as? ProgramsUiState.Loaded ?: return@launch
                    val nextId = findNextWorkoutId(loaded.scheduleItems) ?: return@launch
                    startOrResumeProgramWorkout(WorkoutId(nextId))
                }
                ProgramsStartAction.StartEmptyWorkout -> startEmptyWorkout()
            }
        }
    }

    private companion object {
        const val MSG_START_WORKOUT_FAILED = "Couldn't start that workout. Try again."
        const val MSG_ABANDON_FAILED = "Couldn't cancel the workout. Try again."
        const val MSG_SKIP_FAILED = "Couldn't skip. Try again."
        const val MSG_UNSKIP_FAILED = "Couldn't undo skip. Try again."
    }
}
