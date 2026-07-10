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
import com.stratum.feature.program.ui.EditLiftState
import com.stratum.feature.program.ui.EditSetState
import com.stratum.feature.program.ui.EditWorkoutUiState
import java.util.Locale
import java.util.UUID

internal fun Workout.toEditWorkoutUiState(
    liftNamesById: Map<String, String>,
    compoundByLiftId: Map<String, Boolean> = emptyMap(),
): EditWorkoutUiState.Loaded =
    EditWorkoutUiState.Loaded(
        workoutId = id.value,
        workoutName = name,
        weekdayLabel = weekday?.toDisplayLabel().orEmpty(),
        lifts =
            workoutLifts
                .sortedBy { lift -> lift.position }
                .map { lift -> lift.toEditLiftState(liftNamesById, compoundByLiftId) },
    )

internal fun WorkoutLift.toEditLiftState(
    liftNamesById: Map<String, String>,
    compoundByLiftId: Map<String, Boolean> = emptyMap(),
): EditLiftState {
    val mappedSets =
        sets
            .sortedBy { set -> set.position }
            .mapIndexed { index, set -> set.toEditSetState(index + 1) }
    // Heal legacy / import shapes so the editor always opens on canonical scheme form
    // (Linear shared band, Double RIR-only backoffs, single top).
    val normalizedSets = applyProgressionSchemeToSets(mappedSets, progressionScheme)
    return EditLiftState(
        workoutLiftId = id.value,
        liftId = liftId.value,
        name = resolveLiftDisplayName(liftId.value, liftNamesById),
        position = position,
        sets = normalizedSets,
        supersetGroupId = supersetGroupId?.value,
        useRestTimerBetweenLifts = useRestTimerBetweenLifts,
        isCompound = compoundByLiftId[liftId.value] ?: false,
        progressionScheme = progressionScheme,
    )
}

internal fun LiftSet.toEditSetState(setNumber: Int): EditSetState =
    EditSetState(
        setId = id.value,
        setNumber = setNumber,
        repMin = repMin,
        repMax = repMax,
        restSeconds = restSeconds,
        rirGoal = rirGoal,
        isTopSet = isTopSet,
    )

internal fun renumberSets(sets: List<EditSetState>): List<EditSetState> =
    sets.mapIndexed { index, set -> set.copy(setNumber = index + 1) }

internal fun newSetId(): String = LiftSetId(UUID.randomUUID().toString()).value

internal fun newWorkoutLiftId(): String = WorkoutLiftId(UUID.randomUUID().toString()).value

internal fun newLiftId(): String = com.stratum.domain.lift.identity.LiftId(UUID.randomUUID().toString()).value

internal fun defaultEditSetState(
    setNumber: Int,
    restSeconds: Int = DEFAULT_REST_SECONDS,
): EditSetState =
    EditSetState(
        setId = newSetId(),
        setNumber = setNumber,
        repMin = if (setNumber == 1) DEFAULT_REP_MIN else 0,
        repMax = if (setNumber == 1) DEFAULT_REP_MAX else 0,
        restSeconds = restSeconds,
        rirGoal = DEFAULT_RIR_GOAL,
        isTopSet = setNumber == 1,
    )

private fun Weekday.toDisplayLabel(): String =
    name.replaceFirstChar { char -> char.titlecase(Locale.US) }
