package com.stratum.domain.workout

import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.identity.WorkoutSupersetGroupId
import java.util.UUID
import javax.inject.Inject

/**
 * Maps session-only [WorkoutOverlay] structural edits back onto a baseline [Workout] template
 * so the changes can be persisted via `SaveWorkoutUseCase`. Only the structural fields are
 * applied — logged sets and live recommendations are session activity, not template edits.
 *
 * Applied overlay fields: swapped lifts, deleted lifts, deleted sets, added sets, superset group
 * overrides, rest-timer-between-lifts overrides, and lift reordering.
 */
class ApplyOverlayToWorkoutUseCase @Inject constructor() {
    operator fun invoke(baseline: Workout, overlay: WorkoutOverlay): Workout {
        val updatedLifts = baseline.workoutLifts
            .filterNot { it.id in overlay.deletedLiftIds }
            .map { lift ->
                val swappedLiftId = overlay.swappedLifts[lift.id]
                val updatedLiftId = swappedLiftId ?: lift.liftId

                val supersetOverride = overlay.supersetGroupIdOverrides[lift.id]
                val updatedSupersetGroupId = if (overlay.supersetGroupIdOverrides.containsKey(lift.id)) {
                    supersetOverride
                } else {
                    lift.supersetGroupId
                }

                val updatedRestTimer = overlay.useRestTimerBetweenLiftsOverrides[lift.id]
                    ?: lift.useRestTimerBetweenLifts

                val deletedSetIds = overlay.deletedSlotIds
                    .filterIsInstance<SetSlotId.Template>()
                    .map { it.liftSetId }
                    .toSet()

                val remainingSets = lift.sets.filterNot { it.id in deletedSetIds }

                val addedSets = overlay.addedSlots
                    .filter { it.workoutLiftId == lift.id }
                    .map { slot ->
                        LiftSet(
                            id = LiftSetId(UUID.randomUUID().toString()),
                            workoutLiftId = lift.id,
                            position = slot.position,
                            setType = slot.setType,
                            repMin = slot.repMin,
                            repMax = slot.repMax,
                            rirGoal = slot.rirGoal,
                            isTopSet = slot.isTopSet,
                            restSeconds = slot.restSeconds,
                        )
                    }

                val allSets = (remainingSets + addedSets).sortedBy { it.position }

                lift.copy(
                    liftId = updatedLiftId,
                    sets = allSets,
                    supersetGroupId = updatedSupersetGroupId,
                    useRestTimerBetweenLifts = updatedRestTimer,
                )
            }

        val reorderedLifts = overlay.orderedLiftIds?.let { orderedIds ->
            val orderIndex = orderedIds.withIndex().associate { (index, id) -> id to index }
            updatedLifts.sortedBy { lift ->
                orderIndex[lift.id.value] ?: Int.MAX_VALUE
            }
        } ?: updatedLifts

        return baseline.copy(workoutLifts = reorderedLifts)
    }
}
