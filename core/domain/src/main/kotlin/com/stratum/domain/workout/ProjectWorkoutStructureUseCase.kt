package com.stratum.domain.workout

import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.Workout
import com.stratum.domain.program.coerceForCompound
import com.stratum.domain.program.identity.WorkoutLiftId

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
        liftPinnedNotesById: Map<LiftId, String?> = emptyMap(),
        isPlateLoadedById: Map<LiftId, Boolean> = emptyMap(),
        isDoubleSidedById: Map<LiftId, Boolean> = emptyMap(),
        /**
         * Stored multi-note lists by slot (see [GetSessionPinnedNoteListsUseCase]).
         * A present key wins — including an explicitly cleared empty list. Missing keys
         * (slots outside the loaded set, e.g. freshly added template lifts) fall back to
         * the template write-carry list, then the legacy single.
         */
        workoutPinnedNoteListsById: Map<WorkoutLiftId, List<String>> = emptyMap(),
        liftPinnedNoteListsById: Map<LiftId, List<String>> = emptyMap(),
    ): WorkoutStructureProjection {
        val lifts = workout.workoutLifts.map { workoutLift ->
            val isCompound = isCompoundById[workoutLift.liftId] ?: true
            WorkoutLiftProjection(
                workoutLiftId = workoutLift.id,
                liftId = workoutLift.liftId,
                liftName = liftNames[workoutLift.liftId].orEmpty(),
                progressionScheme = workoutLift.progressionScheme.coerceForCompound(isCompound),
                supersetGroupId = workoutLift.supersetGroupId,
                equipmentId = workoutLift.equipmentId,
                loadType = loadTypesById[workoutLift.liftId] ?: LoadType.Weighted,
                useRestTimerBetweenLifts = workoutLift.useRestTimerBetweenLifts,
                workoutPinnedNotes =
                    workoutPinnedNoteListsById[workoutLift.id]
                        ?: workoutLift.effectiveNotes(),
                liftPinnedNotes =
                    liftPinnedNoteListsById[workoutLift.liftId]
                        ?: listOfNotNull(liftPinnedNotesById[workoutLift.liftId]),
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
