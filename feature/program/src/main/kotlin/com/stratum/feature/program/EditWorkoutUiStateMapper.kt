package com.stratum.feature.program

import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.onboarding.Weekday
import com.stratum.feature.program.ui.DEFAULT_REP_MAX
import com.stratum.feature.program.ui.DEFAULT_REP_MIN
import com.stratum.feature.program.ui.DEFAULT_REST_SECONDS
import com.stratum.feature.program.ui.DEFAULT_RIR_GOAL
import com.stratum.feature.program.ui.EditLiftReorderState
import com.stratum.feature.program.ui.EditLiftState
import com.stratum.feature.program.ui.EditSetState
import com.stratum.feature.program.ui.EditWorkoutUiState
import java.util.Locale
import java.util.UUID

internal fun Workout.toEditWorkoutUiState(
    liftNamesById: Map<String, String>,
): EditWorkoutUiState.Loaded =
    EditWorkoutUiState.Loaded(
        workoutId = id.value,
        workoutName = name,
        weekdayLabel = weekday?.toDisplayLabel().orEmpty(),
        lifts =
            workoutLifts
                .sortedBy { lift -> lift.position }
                .map { lift -> lift.toEditLiftState(liftNamesById) },
    )

internal fun WorkoutLift.toEditLiftState(liftNamesById: Map<String, String>): EditLiftState =
    EditLiftState(
        workoutLiftId = id.value,
        liftId = liftId.value,
        name = resolveLiftDisplayName(liftId.value, liftNamesById),
        position = position,
        sets =
            sets
                .sortedBy { set -> set.position }
                .mapIndexed { index, set -> set.toEditSetState(index + 1) },
    )

internal fun LiftSet.toEditSetState(setNumber: Int): EditSetState =
    EditSetState(
        setId = id.value,
        setNumber = setNumber,
        repMin = repMin,
        repMax = repMax,
        restSeconds = DEFAULT_REST_SECONDS,
        rirGoal = rirGoal,
    )

internal fun EditLiftState.toReorderState(): EditLiftReorderState =
    EditLiftReorderState(
        workoutLiftId = workoutLiftId,
        name = name,
    )

internal fun List<EditLiftState>.toReorderStates(): List<EditLiftReorderState> =
    map { lift -> lift.toReorderState() }

internal fun renumberSets(sets: List<EditSetState>): List<EditSetState> =
    sets.mapIndexed { index, set -> set.copy(setNumber = index + 1) }

internal fun newSetId(): String = LiftSetId(UUID.randomUUID().toString()).value

internal fun newWorkoutLiftId(): String = WorkoutLiftId(UUID.randomUUID().toString()).value

internal fun newLiftId(): String = com.stratum.domain.lift.identity.LiftId(UUID.randomUUID().toString()).value

internal fun defaultEditSetState(setNumber: Int): EditSetState =
    EditSetState(
        setId = newSetId(),
        setNumber = setNumber,
        repMin = DEFAULT_REP_MIN,
        repMax = DEFAULT_REP_MAX,
        restSeconds = DEFAULT_REST_SECONDS,
        rirGoal = DEFAULT_RIR_GOAL,
    )

private fun Weekday.toDisplayLabel(): String =
    name.replaceFirstChar { char -> char.titlecase(Locale.US) }
