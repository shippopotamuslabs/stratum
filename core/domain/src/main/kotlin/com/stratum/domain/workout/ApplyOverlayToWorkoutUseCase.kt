package com.stratum.domain.workout

import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.WorkoutLiftId
import java.util.UUID
import javax.inject.Inject

/**
 * Maps session-only [WorkoutOverlay] structural edits back onto a baseline [Workout] template
 * so the changes can be persisted via `SaveWorkoutUseCase`. Only the structural fields are
 * applied — logged sets and live recommendations are session activity, not template edits.
 *
 * Applied overlay fields: swapped lifts, deleted lifts, added lifts, deleted sets, added sets,
 * superset group overrides, rest-timer-between-lifts overrides, and lift reordering.
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
                    .map { slot -> slot.toLiftSet(lift.id) }

                val allSets = (remainingSets + addedSets).sortedBy { it.position }

                lift.copy(
                    liftId = updatedLiftId,
                    sets = allSets,
                    supersetGroupId = updatedSupersetGroupId,
                    useRestTimerBetweenLifts = updatedRestTimer,
                )
            }

        val existingIds = updatedLifts.map { it.id }.toSet()
        val newlyAddedLifts = overlay.addedLifts
            .filterNot { it.workoutLiftId in overlay.deletedLiftIds }
            .filterNot { it.workoutLiftId in existingIds }
            .mapIndexed { index, added ->
                val slots = overlay.addedSlots
                    .filter { it.workoutLiftId == added.workoutLiftId }
                    .sortedBy { it.position }
                val sets = if (slots.isEmpty()) {
                    listOf(
                        LiftSet(
                            id = LiftSetId(UUID.randomUUID().toString()),
                            workoutLiftId = added.workoutLiftId,
                            position = 0,
                            setType = com.stratum.domain.program.SetType.Standard,
                            repMin = DEFAULT_ADDED_LIFT_REP_MIN,
                            repMax = DEFAULT_ADDED_LIFT_REP_MAX,
                            rirGoal = DEFAULT_ADDED_LIFT_RIR,
                            isTopSet = true,
                        ),
                    )
                } else {
                    slots.map { it.toLiftSet(added.workoutLiftId) }
                }
                val restOverride = overlay.useRestTimerBetweenLiftsOverrides[added.workoutLiftId]
                val groupOverride = if (overlay.supersetGroupIdOverrides.containsKey(added.workoutLiftId)) {
                    overlay.supersetGroupIdOverrides[added.workoutLiftId]
                } else {
                    added.supersetGroupId
                }
                WorkoutLift(
                    id = added.workoutLiftId,
                    workoutId = baseline.id,
                    liftId = added.liftId,
                    progressionScheme = added.progressionScheme,
                    position = updatedLifts.size + index,
                    sets = sets,
                    supersetGroupId = groupOverride,
                    useRestTimerBetweenLifts = restOverride ?: added.useRestTimerBetweenLifts,
                )
            }

        val combined = updatedLifts + newlyAddedLifts

        val reorderedLifts = overlay.orderedLiftIds?.let { orderedIds ->
            val orderIndex = orderedIds.withIndex().associate { (index, id) -> id to index }
            combined.sortedBy { lift ->
                orderIndex[lift.id.value] ?: Int.MAX_VALUE
            }
        } ?: combined

        return baseline.copy(workoutLifts = reorderedLifts)
    }

    private fun SetSlot.toLiftSet(workoutLiftId: WorkoutLiftId): LiftSet =
        LiftSet(
            id = LiftSetId(UUID.randomUUID().toString()),
            workoutLiftId = workoutLiftId,
            position = position,
            setType = setType,
            repMin = repMin,
            repMax = repMax,
            rirGoal = rirGoal,
            isTopSet = isTopSet,
            restSeconds = restSeconds,
        )

    private companion object {
        const val DEFAULT_ADDED_LIFT_REP_MIN = 8
        const val DEFAULT_ADDED_LIFT_REP_MAX = 12
        const val DEFAULT_ADDED_LIFT_RIR = 2
    }
}
