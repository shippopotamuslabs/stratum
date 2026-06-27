package com.stratum.feature.program

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.program.ObserveProgramUseCase
import com.stratum.domain.program.Program
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.feature.program.ui.LiftSetViewState
import com.stratum.feature.program.ui.ProgramViewState
import com.stratum.feature.program.ui.ProgramsUiState
import com.stratum.feature.program.ui.WorkoutLiftViewState
import com.stratum.feature.program.ui.WorkoutViewState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Exposes the persisted program as [ProgramsUiState] for the read-only Programs screen. Maps the
 * domain [Program] to presentation view states so composables never receive domain types
 * (ARCHITECTURE.md §4.1). Depends on [ObserveProgramUseCase] (not the repository) per the
 * `ForbiddenRepositoryInViewModel` contract.
 */
@HiltViewModel
class ProgramsViewModel @Inject constructor(
    observeProgram: ObserveProgramUseCase,
) : ViewModel() {
    val programsUiState: StateFlow<ProgramsUiState> = observeProgram()
        .map { it.toProgramsUiState() }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ProgramsUiState.Empty,
        )
}

/**
 * Domain → presentation mapping. `null` (no program) maps to [ProgramsUiState.Empty]; a non-null
 * [Program] maps to [ProgramsUiState.Loaded] with view states (domain models are never passed to
 * composables — ARCHITECTURE.md §4.1).
 *
 * The lift display name uses the lift id slug as a v1 fallback; a future slice can enrich it from
 * the lift catalog once the Programs screen has a lift-lookup dependency.
 */
internal fun Program?.toProgramsUiState(): ProgramsUiState =
    if (this == null) {
        ProgramsUiState.Empty
    } else {
        ProgramsUiState.Loaded(
            program = ProgramViewState(
                name = name,
                workouts = workouts.sortedBy { it.position }.map { it.toViewState() },
            ),
        )
    }

private fun Workout.toViewState(): WorkoutViewState = WorkoutViewState(
    name = name,
    weekdayLabel = weekday?.name,
    position = position,
    workoutLifts = workoutLifts.sortedBy { it.position }.map { it.toViewState() },
)

private fun WorkoutLift.toViewState(): WorkoutLiftViewState = WorkoutLiftViewState(
    liftId = liftId.value,
    liftName = liftId.value,
    progressionSchemeLabel = progressionScheme.name,
    position = position,
    isExpanded = false,
    sets = sets.sortedBy { it.position }.map { set ->
        LiftSetViewState(
            position = set.position,
            setTypeLabel = set.setType.name,
            repMin = set.repMin,
            repMax = set.repMax,
            rirGoal = set.rirGoal,
            isTopSet = set.isTopSet,
        )
    },
)
