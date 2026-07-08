package com.stratum.feature.program

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.identity.WorkoutSupersetGroupId
import com.stratum.feature.program.ui.EditLiftState
import com.stratum.feature.program.ui.PLACEHOLDER_LIFT_NAME

import com.stratum.core.ui.component.isLastInSupersetChain

internal fun List<EditLiftState>.toWorkout(
    baseline: Workout,
    workoutId: WorkoutId,
): Workout {
    val baselineSetsById =
        baseline.workoutLifts
            .flatMap { lift -> lift.sets }
            .associateBy { set -> set.id.value }

    return baseline.copy(
        workoutLifts =
            sortedBy { lift -> lift.position }
                .map { editLift ->
                    val progressionScheme = editLift.progressionScheme
                    WorkoutLift(
                        id = WorkoutLiftId(editLift.workoutLiftId),
                        workoutId = workoutId,
                        liftId = LiftId(editLift.liftId),
                        progressionScheme = progressionScheme,
                        position = editLift.position,
                        sets =
                            editLift.sets.map { editSet ->
                                val baselineSet = baselineSetsById[editSet.setId]
                                LiftSet(
                                    id = LiftSetId(editSet.setId),
                                    workoutLiftId = WorkoutLiftId(editLift.workoutLiftId),
                                    position = editSet.setNumber - 1,
                                    setType = baselineSet?.setType ?: SetType.Standard,
                                    // Backoff sets are RIR-only — the progression engines floor them at
                                    // BACKOFF_REP_FLOOR and never use a rep range. Normalize to the
                                    // documented 0/0 convention (Program.kt) regardless of stale state.
                                    repMin = if (editSet.isTopSet) editSet.repMin else 0,
                                    repMax = if (editSet.isTopSet) editSet.repMax else 0,
                                    rirGoal = editSet.rirGoal,
                                    isTopSet =
                                        baselineSet?.isTopSet
                                            ?: (
                                                progressionScheme == ProgressionScheme.Double &&
                                                    editSet.setNumber == 1
                                            ),
                                    restSeconds = editSet.restSeconds,
                                )
                            },
                        supersetGroupId = editLift.supersetGroupId?.let { WorkoutSupersetGroupId(it) },
                        useRestTimerBetweenLifts = persistUseRestTimerBetweenLifts(editLift, this),
                    )
                },
    )
}

internal fun persistUseRestTimerBetweenLifts(
    lift: EditLiftState,
    allLifts: List<EditLiftState>,
): Boolean {
    if (lift.supersetGroupId == null) {
        return false
    }
    if (isLastInSupersetChain(lift, allLifts)) {
        return false
    }
    return lift.useRestTimerBetweenLifts
}

internal fun List<EditLiftState>.hasPlaceholderLift(catalogLiftIds: Set<String>): Boolean =
    any { lift ->
        lift.name == PLACEHOLDER_LIFT_NAME || lift.liftId !in catalogLiftIds
    }

internal fun newSupersetGroupId(): String = WorkoutSupersetGroupId(java.util.UUID.randomUUID().toString()).value
