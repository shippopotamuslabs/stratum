package com.stratum.feature.program

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.program.ComputeProgramMuscleVolumeUseCase
import com.stratum.domain.program.ObserveProgramUseCase
import com.stratum.domain.program.ObserveProgramsUiPreferencesUseCase
import com.stratum.domain.program.SetExpandedWorkoutIdUseCase
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.training.ActiveTrainingSessionExistsException
import com.stratum.domain.training.AbandonTrainingSessionUseCase
import com.stratum.domain.training.GetLoggedSetsForSessionUseCase
import com.stratum.domain.training.ObserveCycleProgressUseCase
import com.stratum.domain.training.RecordRestDaySkipUseCase
import com.stratum.domain.training.RecordWorkoutSkipUseCase
import com.stratum.domain.training.StartTrainingSessionRequest
import com.stratum.domain.training.StartTrainingSessionUseCase
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionDisplayName
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.UnskipWorkoutUseCase
import com.stratum.domain.training.isOrphanedAgainstActiveProgram
import com.stratum.domain.training.liveWorkoutRouteId
import com.stratum.domain.workout.ManageWorkoutOverlayUseCase
import com.stratum.domain.workout.WorkoutOverlay
import com.stratum.feature.program.ui.MinimizedWorkoutState
import com.stratum.feature.program.ui.ProgramsStartAction
import com.stratum.feature.program.ui.ProgramsUiEvent
import com.stratum.feature.program.ui.ProgramsUiState
import com.stratum.feature.program.ui.findNextWorkoutId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
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
@HiltViewModel
class ProgramsViewModel @Inject constructor(
    observeProgram: ObserveProgramUseCase,
    private val observeLiftCatalog: ObserveLiftCatalogUseCase,
    computeProgramMuscleVolume: ComputeProgramMuscleVolumeUseCase,
    private val startTrainingSession: StartTrainingSessionUseCase,
    private val observeCycleProgress: ObserveCycleProgressUseCase,
    private val abandonTrainingSession: AbandonTrainingSessionUseCase,
    private val getLoggedSetsForSession: GetLoggedSetsForSessionUseCase,
    private val recordWorkoutSkip: RecordWorkoutSkipUseCase,
    private val recordRestDaySkip: RecordRestDaySkipUseCase,
    private val unskipWorkout: UnskipWorkoutUseCase,
    private val manageWorkoutOverlay: ManageWorkoutOverlayUseCase,
    observeProgramsUiPreferences: ObserveProgramsUiPreferencesUseCase,
    private val setExpandedWorkoutId: SetExpandedWorkoutIdUseCase,
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
                        abandonTrainingSession(orphanSessionId)
                            .onFailure { showUserMessage(MSG_ABANDON_FAILED) }
                    }
                }
        }
    }

    val programsUiState: StateFlow<ProgramsUiState> =
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
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ProgramsUiState.Empty,
        )

    /**
     * Last expanded schedule workout card, restored from DataStore. Independent of program load
     * so editing/swapping lifts does not force a different card open.
     */
    val expandedWorkoutId: StateFlow<String?> =
        observeProgramsUiPreferences()
            .map { prefs -> prefs.expandedWorkoutId }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                null,
            )

    fun setExpandedWorkout(workoutId: String?) {
        viewModelScope.launch {
            setExpandedWorkoutId(workoutId)
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
            val result = startTrainingSession(StartTrainingSessionRequest.AdHocEmpty)
            if (result.isSuccess) {
                // Ad-hoc has no template workoutId — route key is the session id.
                _events.send(ProgramsUiEvent.NavigateToWorkout(result.getOrThrow().liveWorkoutRouteId()))
                return@launch
            }
            handleStartFailure(result)
        }
    }

    private suspend fun startOrResumeProgramWorkout(workoutId: WorkoutId) {
        val result = startTrainingSession(StartTrainingSessionRequest.ProgramWorkout(workoutId))
        if (result.isSuccess) {
            _events.send(ProgramsUiEvent.NavigateToWorkout(workoutId.value))
            return
        }
        handleStartFailure(result)
    }

    private suspend fun handleStartFailure(result: Result<TrainingSession>) {
        when (result.exceptionOrNull()) {
            is ActiveTrainingSessionExistsException -> {
                val progress = observeCycleProgress().first()
                val routeId = progress.activeSession?.liveWorkoutRouteId()
                if (routeId != null) {
                    // Resume whatever is live (program or ad-hoc) instead of deadlocking.
                    _events.send(ProgramsUiEvent.NavigateToWorkout(routeId))
                } else {
                    showUserMessage(MSG_START_WORKOUT_FAILED)
                }
            }
            else -> showUserMessage(MSG_START_WORKOUT_FAILED)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeWorkoutBarState: StateFlow<MinimizedWorkoutState?> =
        observeCycleProgress().flatMapLatest { progress ->
            flow {
                val session = progress.activeSession
                if (session == null) {
                    emit(null)
                    return@flow
                }
                when (session.kind) {
                    TrainingSessionKind.AD_HOC -> {
                        val nextLiftName =
                            runCatching {
                                val lifts = observeLiftCatalog().first()
                                val liftNames = lifts.associate { it.id to it.name }
                                val overlay = manageWorkoutOverlay.load(session.id)
                                val loggedSets = getLoggedSetsForSession(session.id)
                                findNextAdHocLiftName(overlay, loggedSets, liftNames)
                            }.getOrNull()
                        emit(
                            MinimizedWorkoutState(
                                workoutId = session.liveWorkoutRouteId(),
                                workoutName = TrainingSessionDisplayName.resolve(
                                    session = session,
                                    program = progress.program,
                                ),
                                nextLiftName = nextLiftName ?: "Add lifts",
                                startedAtEpochMilli = session.startedAt.toEpochMilli(),
                            ),
                        )
                    }
                    TrainingSessionKind.PROGRAM_WORKOUT -> {
                        val workoutId = session.workoutId
                        if (workoutId == null) {
                            emit(null)
                            return@flow
                        }
                        val program = progress.program
                        val workout = program?.workouts?.firstOrNull { it.id == workoutId }
                        if (workout == null) {
                            // Orphan: init collector abandons; hide bar so expand cannot open a dead route.
                            emit(null)
                        } else {
                            // Soft-fail set/catalog load so a bad read never kills the bar or the VM.
                            val nextLiftName =
                                runCatching {
                                    val loggedSets = getLoggedSetsForSession(session.id)
                                    val lifts = observeLiftCatalog().first()
                                    val liftNames = lifts.associate { it.id to it.name }
                                    findNextLiftName(workout, loggedSets, liftNames)
                                }.getOrNull()
                            emit(
                                MinimizedWorkoutState(
                                    workoutId = workoutId.value,
                                    workoutName = workout.name,
                                    nextLiftName = nextLiftName ?: "Workout in progress",
                                    startedAtEpochMilli = session.startedAt.toEpochMilli(),
                                ),
                            )
                        }
                    }
                    // Rest-day ledger rows are not interactive live sessions.
                    TrainingSessionKind.REST_DAY -> emit(null)
                }
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            null
        )

    fun abandonActiveSession() {
        viewModelScope.launch {
            val progress = observeCycleProgress().first()
            val activeSession = progress.activeSession ?: return@launch
            val result = abandonTrainingSession(activeSession.id)
            // Success is silent — cancel from the chip is intentional, not a failure to report.
            if (result.isFailure) {
                showUserMessage(MSG_ABANDON_FAILED)
            } else {
                // Match live-screen abandon: drop process-scoped overlay so a new session
                // cannot restore a dead session's structure from DataStore.
                manageWorkoutOverlay.clear()
            }
        }
    }

    fun skipWorkout(workoutId: String) {
        viewModelScope.launch {
            recordWorkoutSkip(WorkoutId(workoutId))
                .onFailure { showUserMessage(MSG_SKIP_FAILED) }
        }
    }

    fun unskipWorkout(workoutId: String) {
        viewModelScope.launch {
            unskipWorkout(WorkoutId(workoutId))
                .onFailure { showUserMessage(MSG_UNSKIP_FAILED) }
        }
    }

    fun skipRestDay(slotIndex: Int) {
        viewModelScope.launch {
            recordRestDaySkip(slotIndex)
                .onFailure { showUserMessage(MSG_SKIP_FAILED) }
        }
    }

    fun unskipRestDay(slotIndex: Int) {
        viewModelScope.launch {
            unskipWorkout.unskipRestSlot(slotIndex)
                .onFailure { showUserMessage(MSG_UNSKIP_FAILED) }
        }
    }

    fun confirmDiscardAndStart(action: ProgramsStartAction) {
        viewModelScope.launch {
            val progress = observeCycleProgress().first()
            progress.activeSession?.let { session ->
                val abandonResult = abandonTrainingSession(session.id)
                if (abandonResult.isFailure) {
                    showUserMessage(MSG_ABANDON_FAILED)
                    return@launch
                }
                manageWorkoutOverlay.clear()
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

    private fun findNextLiftName(
        workout: com.stratum.domain.program.Workout,
        loggedSets: List<com.stratum.domain.training.LoggedSet>,
        liftNames: Map<LiftId, String>,
    ): String? {
        val sortedLifts = workout.workoutLifts.sortedBy { it.position }
        for (workoutLift in sortedLifts) {
            val sortedSets = workoutLift.sets.sortedBy { it.position }
            for (setIndex in sortedSets.indices) {
                val isLogged = loggedSets.any {
                    it.liftContext.workoutLiftId == workoutLift.id && it.setIndex == setIndex
                }
                if (!isLogged) {
                    return liftNames[workoutLift.liftId] ?: "Unknown Lift"
                }
            }
        }
        return null
    }

    /**
     * Next incomplete session-added lift for the minimized bar. Prefers overlay structure;
     * falls back to durable logs when overlay is missing.
     */
    private fun findNextAdHocLiftName(
        overlay: WorkoutOverlay?,
        loggedSets: List<com.stratum.domain.training.LoggedSet>,
        liftNames: Map<LiftId, String>,
    ): String {
        if (overlay != null && overlay.addedLifts.isNotEmpty()) {
            val order =
                overlay.orderedLiftIds
                    ?: overlay.addedLifts.map { it.workoutLiftId.value }
            for (wlIdValue in order) {
                val workoutLiftId = com.stratum.domain.program.identity.WorkoutLiftId(wlIdValue)
                if (workoutLiftId in overlay.deletedLiftIds) continue
                val slots =
                    overlay.addedSlots
                        .filter { it.workoutLiftId == workoutLiftId }
                        .filterNot { it.slotId in overlay.deletedSlotIds }
                        .sortedBy { it.position }
                val hasUnlogged = slots.any { it.slotId !in overlay.loggedSets }
                if (hasUnlogged || slots.isEmpty()) {
                    val added = overlay.addedLifts.firstOrNull { it.workoutLiftId == workoutLiftId }
                    val liftId = overlay.swappedLifts[workoutLiftId] ?: added?.liftId
                    return liftId?.let { liftNames[it] } ?: "Workout in progress"
                }
            }
            return "Workout in progress"
        }
        if (loggedSets.isEmpty()) return "Add lifts"
        // Overlay lost but sets exist — surface generic progress, not a stale "Add lifts".
        val last = loggedSets.maxByOrNull { it.completedAt }
        val liftId = last?.liftId
        return liftId?.let { liftNames[it] } ?: "Workout in progress"
    }

    private companion object {
        const val MSG_START_WORKOUT_FAILED = "Couldn't start that workout. Try again."
        const val MSG_ABANDON_FAILED = "Couldn't cancel the workout. Try again."
        const val MSG_SKIP_FAILED = "Couldn't skip. Try again."
        const val MSG_UNSKIP_FAILED = "Couldn't undo skip. Try again."
    }
}
