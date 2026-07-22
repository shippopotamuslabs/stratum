package com.stratum.feature.program

import com.stratum.core.ui.component.SupersetLinkDirection
import com.stratum.core.ui.component.SupersetLinkableLiftState
import com.stratum.core.ui.component.cleanupSupersetGroups
import com.stratum.core.ui.component.linkSupersetWithAdjacent
import com.stratum.core.ui.component.unlinkLiftFromSuperset
import com.stratum.feature.program.ui.DEFAULT_REP_MAX
import com.stratum.feature.program.ui.DEFAULT_REP_MIN
import com.stratum.feature.program.ui.DEFAULT_REST_SECONDS
import com.stratum.feature.program.ui.DEFAULT_RIR_GOAL
import com.stratum.feature.program.ui.EditLiftState
import com.stratum.feature.program.ui.EditSetState
import com.stratum.feature.program.ui.EditWorkoutUiState
import java.util.UUID

/**
 * Pure list/state mutations for program workout editing (standalone edit + create-program
 * embedded editor). No Compose — call from ViewModels/controllers only.
 */
internal object EditWorkoutLiftMutations {

    fun removeLift(
        loaded: EditWorkoutUiState.Loaded,
        workoutLiftId: String,
    ): EditWorkoutUiState.Loaded {
        if (loaded.lifts.none { it.workoutLiftId == workoutLiftId }) return loaded
        return loaded.copy(
            lifts = removeLiftAndCleanupSupersets(loaded.lifts, workoutLiftId),
            isDirty = true,
        )
    }

    fun linkSuperset(
        loaded: EditWorkoutUiState.Loaded,
        workoutLiftId: String,
        direction: SupersetLinkDirection,
        nextGroupId: () -> String = { UUID.randomUUID().toString() },
    ): EditWorkoutUiState.Loaded =
        loaded.copy(
            lifts =
                applySupersetLinkChanges(
                    loaded.lifts,
                    linkSupersetWithAdjacent(
                        lifts = loaded.lifts,
                        workoutLiftId = workoutLiftId,
                        direction = direction,
                        nextGroupId = nextGroupId,
                    ),
                ),
            isDirty = true,
        )

    fun removeFromSuperset(
        loaded: EditWorkoutUiState.Loaded,
        workoutLiftId: String,
    ): EditWorkoutUiState.Loaded =
        loaded.copy(
            lifts =
                applySupersetLinkChanges(
                    loaded.lifts,
                    unlinkLiftFromSuperset(loaded.lifts, workoutLiftId),
                ),
            isDirty = true,
        )

    fun setUseRestTimerBetweenLifts(
        loaded: EditWorkoutUiState.Loaded,
        workoutLiftId: String,
        enabled: Boolean,
    ): EditWorkoutUiState.Loaded =
        loaded.copy(
            lifts =
                loaded.lifts.map { lift ->
                    if (lift.workoutLiftId != workoutLiftId) {
                        lift
                    } else {
                        lift.copy(useRestTimerBetweenLifts = enabled)
                    }
                },
            isDirty = true,
        )

    fun addSet(
        loaded: EditWorkoutUiState.Loaded,
        workoutLiftId: String,
    ): EditWorkoutUiState.Loaded =
        loaded.copy(
            lifts =
                loaded.lifts.map { lift ->
                    if (lift.workoutLiftId != workoutLiftId) {
                        lift
                    } else {
                        lift.copy(
                            sets = renumberSets(lift.sets + defaultSet(lift.sets.size + 1)),
                        )
                    }
                },
            isDirty = true,
        )

    fun removeSet(
        loaded: EditWorkoutUiState.Loaded,
        setId: String,
    ): EditWorkoutUiState.Loaded =
        loaded.copy(
            lifts =
                loaded.lifts.map { lift ->
                    val next = lift.sets.filterNot { set -> set.setId == setId }
                    if (next.size == lift.sets.size) {
                        lift
                    } else {
                        lift.copy(sets = renumberSets(next))
                    }
                },
            isDirty = true,
        )

    fun updateSet(
        loaded: EditWorkoutUiState.Loaded,
        setId: String,
        transform: (EditSetState) -> EditSetState,
    ): EditWorkoutUiState.Loaded =
        loaded.copy(
            lifts =
                loaded.lifts.map { lift ->
                    lift.copy(
                        sets =
                            lift.sets.map { set ->
                                if (set.setId == setId) transform(set) else set
                            },
                    )
                },
            isDirty = true,
        )

    fun setRestSecondsForAllSetsOnLift(
        loaded: EditWorkoutUiState.Loaded,
        workoutLiftId: String,
        restSeconds: Int,
    ): EditWorkoutUiState.Loaded =
        loaded.copy(
            lifts =
                loaded.lifts.map { lift ->
                    if (lift.workoutLiftId != workoutLiftId) {
                        lift
                    } else {
                        lift.copy(
                            sets = lift.sets.map { set -> set.copy(restSeconds = restSeconds) },
                        )
                    }
                },
            isDirty = true,
        )
}

/**
 * Removes [workoutLiftId] and clears orphan superset groups (singleton survivors lose their
 * group id). No-op (same list identity aside from reindex) when the id is absent — callers that
 * need dirty-skipping should check membership first.
 */
internal fun removeLiftAndCleanupSupersets(
    lifts: List<EditLiftState>,
    workoutLiftId: String,
): List<EditLiftState> {
    if (lifts.none { it.workoutLiftId == workoutLiftId }) return lifts
    val remaining =
        lifts
            .filterNot { lift -> lift.workoutLiftId == workoutLiftId }
            .mapIndexed { index, lift -> lift.copy(position = index) }
    return applySupersetLinkChanges(remaining, cleanupSupersetGroups(remaining))
}

/**
 * Applies [updated] superset group / rest-between flags onto matching [EditLiftState] rows.
 */
internal fun applySupersetLinkChanges(
    lifts: List<EditLiftState>,
    updated: List<SupersetLinkableLiftState>,
): List<EditLiftState> {
    val byId = updated.associateBy { it.workoutLiftId }
    return lifts.map { lift ->
        val match = byId[lift.workoutLiftId]
        if (match != null) {
            lift.copy(
                supersetGroupId = match.supersetGroupId,
                useRestTimerBetweenLifts = match.useRestTimerBetweenLifts,
            )
        } else {
            lift
        }
    }
}

private fun defaultSet(setNumber: Int): EditSetState =
    EditSetState(
        setId = UUID.randomUUID().toString(),
        setNumber = setNumber,
        repMin = if (setNumber == 1) DEFAULT_REP_MIN else 0,
        repMax = if (setNumber == 1) DEFAULT_REP_MAX else 0,
        restSeconds = DEFAULT_REST_SECONDS,
        rirGoal = DEFAULT_RIR_GOAL,
        isTopSet = setNumber == 1,
    )
