package com.stratum.domain.workout

import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.Workout
import com.stratum.domain.program.coerceForCompound

/**
 * Pure structure projection use case (D1). Builds the [WorkoutStructureProjection]
 * from the template [Workout] (no load). Slice 3 extends this to fold in overlay
 * structural mutations.
 *
 * [loadTypesById] resolves each workout lift's [LoadType] from the catalog so the
 * projection (and downstream merge/render) can branch on loading modality without a
 * separate catalog lookup. Absent entries default to [LoadType.Weighted]; the map is
 * empty by default so existing callers and tests keep working unchanged.
 *
 * [isCompoundById] heals isolation lifts that were stored as Linear (import legacy):
 * isolation never runs Linear (D8), so the projected scheme is coerced to Double. Absent
 * entries default to compound (`true`) so callers that omit the map keep stored schemes.
 */
class ProjectWorkoutStructureUseCase @javax.inject.Inject constructor() {
    operator fun invoke(
        workout: Workout,
        liftNames: Map<LiftId, String>,
        loadTypesById: Map<LiftId, LoadType> = emptyMap(),
        isCompoundById: Map<LiftId, Boolean> = emptyMap(),
        isPlateLoadedById: Map<LiftId, Boolean> = emptyMap(),
        isDoubleSidedById: Map<LiftId, Boolean> = emptyMap(),
    ): WorkoutStructureProjection {
        val lifts = workout.workoutLifts.map { workoutLift ->
            val isCompound = isCompoundById[workoutLift.liftId] ?: true
            WorkoutLiftProjection(
                workoutLiftId = workoutLift.id,
                liftId = workoutLift.liftId,
                liftName = liftNames[workoutLift.liftId].orEmpty(),
                progressionScheme = workoutLift.progressionScheme.coerceForCompound(isCompound),
                supersetGroupId = workoutLift.supersetGroupId,
                loadType = loadTypesById[workoutLift.liftId] ?: LoadType.Weighted,
                useRestTimerBetweenLifts = workoutLift.useRestTimerBetweenLifts,
                isPlateLoaded = isPlateLoadedById[workoutLift.liftId] ?: false,
                isDoubleSided = isDoubleSidedById[workoutLift.liftId] ?: true,
                slots = workoutLift.sets.map { liftSet ->
                    SetSlot(
                        slotId = SetSlotId.Template(liftSet.id),
                        workoutLiftId = workoutLift.id,
                        liftId = workoutLift.liftId,
                        position = liftSet.position,
                        setType = liftSet.setType,
                        isTopSet = liftSet.isTopSet,
                        repMin = liftSet.repMin,
                        repMax = liftSet.repMax,
                        rirGoal = liftSet.rirGoal,
                        restSeconds = liftSet.restSeconds,
                        myoRepKind = liftSet.myoRepKind,
                    )
                },
            )
        }
        return WorkoutStructureProjection(
            workoutId = workout.id,
            workoutName = workout.name,
            lifts = lifts,
        )
    }
}
