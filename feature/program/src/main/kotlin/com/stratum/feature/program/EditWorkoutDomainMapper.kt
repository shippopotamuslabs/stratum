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
): Workout =
    baseline.copy(
        workoutLifts =
            sortedBy { lift -> lift.position }
                .map { editLift ->
                    val progressionScheme = editLift.progressionScheme
                    // Re-assert scheme invariants via ProgressionRepRangePolicy; field copy only.
                    val normalizedSets =
                        applyProgressionSchemeToSets(editLift.sets, progressionScheme)
                    WorkoutLift(
                        id = WorkoutLiftId(editLift.workoutLiftId),
                        workoutId = workoutId,
                        liftId = LiftId(editLift.liftId),
                        progressionScheme = progressionScheme,
                        position = editLift.position,
                        sets =
                            normalizedSets.map { editSet ->
                                LiftSet(
                                    id = LiftSetId(editSet.setId),
                                    workoutLiftId = WorkoutLiftId(editLift.workoutLiftId),
                                    position = editSet.setNumber - 1,
                                    setType = editSet.setKind.toDomainSetType(),
                                    repMin = editSet.repMin,
                                    repMax = editSet.repMax,
                                    rirGoal = editSet.rirGoal,
                                    isTopSet = editSet.isTopSet,
                                    restSeconds = editSet.restSeconds,
                                    myoRepKind = editSet.setKind.toMyoRepKind(),
                                )
                            },
                        supersetGroupId = editLift.supersetGroupId?.let { WorkoutSupersetGroupId(it) },
                        useRestTimerBetweenLifts = persistUseRestTimerBetweenLifts(editLift, this),
                    )
                },
    )

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
