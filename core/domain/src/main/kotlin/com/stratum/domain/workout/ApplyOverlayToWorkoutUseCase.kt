package com.stratum.domain.workout

import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.SetType
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
 *
 * Overlay-added slots that are also present in [WorkoutOverlay.deletedSlotIds] are **not**
 * written to the template (matches live merge semantics).
 */
class ApplyOverlayToWorkoutUseCase @Inject constructor() {
    operator fun invoke(baseline: Workout, overlay: WorkoutOverlay): Workout {
        val liveAddedSlots =
            overlay.addedSlots.filterNot { it.slotId in overlay.deletedSlotIds }

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

                val deletedTemplateSetIds = overlay.deletedSlotIds
                    .filterIsInstance<SetSlotId.Template>()
                    .map { it.liftSetId }
                    .toSet()

                val remainingSets = lift.sets.filterNot { it.id in deletedTemplateSetIds }

                val addedSets = liveAddedSlots
                    .filter { it.workoutLiftId == lift.id }
                    .map { slot -> slot.toLiftSet(lift.id) }

                val allSets = (remainingSets + addedSets)
                    .sortedBy { it.position }
                    .mapIndexed { index, set -> set.copy(position = index) }

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
            .map { added ->
                val slots = liveAddedSlots
                    .filter { it.workoutLiftId == added.workoutLiftId }
                    .sortedBy { it.position }
                val sets = if (slots.isEmpty()) {
                    listOf(
                        LiftSet(
                            id = LiftSetId(UUID.randomUUID().toString()),
                            workoutLiftId = added.workoutLiftId,
                            position = 0,
                            setType = SetType.Standard,
                            repMin = AddedLiftDefaults.REP_MIN,
                            repMax = AddedLiftDefaults.REP_MAX,
                            rirGoal = AddedLiftDefaults.RIR,
                            isTopSet = true,
                            restSeconds = AddedLiftDefaults.REST_SECONDS,
                        ),
                    )
                } else {
                    slots.mapIndexed { index, slot ->
                        slot.toLiftSet(added.workoutLiftId).copy(position = index)
                    }
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
                    // Temporary; renumbered after final order is known.
                    position = 0,
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

        // Room loads workout_lifts ORDER BY position — keep field in sync with list order.
        val positioned =
            reorderedLifts.mapIndexed { index, lift ->
                lift.copy(position = index)
            }

        return baseline.copy(workoutLifts = positioned)
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
}
