package com.stratum.feature.program

import com.stratum.domain.program.Program
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.feature.program.ui.LiftSetViewState
import com.stratum.feature.program.ui.ProgramViewState
import com.stratum.feature.program.ui.ProgramsUiState
import com.stratum.feature.program.ui.WorkoutLiftViewState
import com.stratum.feature.program.ui.WorkoutViewState

/**
 * Domain → presentation mapping for the Programs screen. `null` (no program) maps to
 * [ProgramsUiState.Empty]; a non-null [Program] maps to [ProgramsUiState.Loaded].
 *
 * The lift display name uses the lift id slug as a v1 fallback; a future slice can enrich it
 * from the lift catalog once the Programs screen has a lift-lookup dependency.
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
