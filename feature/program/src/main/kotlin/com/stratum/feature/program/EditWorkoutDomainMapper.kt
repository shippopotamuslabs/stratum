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
                                    repMin = editSet.repMin,
                                    repMax = editSet.repMax,
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

internal fun isLastInSupersetChain(
    lift: EditLiftState,
    allLifts: List<EditLiftState>,
): Boolean {
    val groupId = lift.supersetGroupId ?: return true
    val chain =
        allLifts
            .filter { member -> member.supersetGroupId == groupId }
            .sortedBy { member -> member.position }
    return chain.lastOrNull()?.workoutLiftId == lift.workoutLiftId
}

internal fun newSupersetGroupId(): String = WorkoutSupersetGroupId(java.util.UUID.randomUUID().toString()).value
