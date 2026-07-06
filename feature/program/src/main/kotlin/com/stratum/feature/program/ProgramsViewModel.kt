package com.stratum.feature.program

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.program.ComputeProgramMuscleVolumeUseCase
import com.stratum.domain.program.ObserveProgramUseCase
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.training.ActiveTrainingSessionExistsException
import com.stratum.domain.training.AbandonTrainingSessionUseCase
import com.stratum.domain.training.GetLoggedSetsForSessionUseCase
import com.stratum.domain.training.ObserveCycleProgressUseCase
import com.stratum.domain.training.StartTrainingSessionRequest
import com.stratum.domain.training.StartTrainingSessionUseCase
import com.stratum.domain.training.TrainingSession
import com.stratum.feature.program.ui.MinimizedWorkoutState
import com.stratum.feature.program.ui.ProgramsUiEvent
import com.stratum.feature.program.ui.ProgramsUiState
import com.stratum.feature.program.ui.findNextWorkoutId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
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
) : ViewModel() {
    val programsUiState: StateFlow<ProgramsUiState> =
        combine(
            observeProgram(),
            observeLiftCatalog(),
        ) { program, lifts ->
            val volumeSummaries =
                program?.let { activeProgram ->
                    computeProgramMuscleVolume(activeProgram, lifts)
                }.orEmpty()
            program.toProgramsUiState(
                liftNamesById = lifts.associate { lift -> lift.id.value to lift.name },
                volumeSummaries = volumeSummaries,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ProgramsUiState.Empty,
        )

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
                _events.send(
                    ProgramsUiEvent.ShowMessage(
                        "Empty workout session started. Structured workout screen requires a program workout.",
                    ),
                )
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
        if (result.exceptionOrNull() is ActiveTrainingSessionExistsException) {
            val progress = observeCycleProgress().first()
            val activeWorkoutId = progress.activeSession?.workoutId?.value
            if (activeWorkoutId != null) {
                _events.send(ProgramsUiEvent.NavigateToWorkout(activeWorkoutId))
            } else {
                _events.send(
                    ProgramsUiEvent.ShowMessage(
                        "An empty workout is already in progress. Finish it before starting a program workout.",
                    ),
                )
            }
        }
    }

    val activeWorkoutBarState: StateFlow<MinimizedWorkoutState?> =
        observeCycleProgress().flatMapLatest { progress ->
            flow {
                val session = progress.activeSession
                val workoutId = session?.workoutId
                if (session == null || workoutId == null) {
                    emit(null)
                } else {
                    val program = progress.program
                    val workout = program?.workouts?.firstOrNull { it.id == workoutId }
                    if (workout == null) {
                        emit(null)
                    } else {
                        val loggedSets = getLoggedSetsForSession(session.id)
                        val lifts = observeLiftCatalog().first()
                        val liftNames = lifts.associate { it.id to it.name }
                        val nextLiftName = findNextLiftName(workout, loggedSets, liftNames)
                        emit(
                            MinimizedWorkoutState(
                                workoutId = workoutId.value,
                                workoutName = workout.name,
                                nextLiftName = nextLiftName ?: "All sets complete"
                            )
                        )
                    }
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
            abandonTrainingSession(activeSession.id)
            _events.send(ProgramsUiEvent.ShowMessage("Workout cancelled."))
        }
    }

    private fun findNextLiftName(
        workout: com.stratum.domain.program.Workout,
        loggedSets: List<com.stratum.domain.training.LoggedSet>,
        liftNames: Map<com.stratum.domain.lift.identity.LiftId, String>,
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
}
