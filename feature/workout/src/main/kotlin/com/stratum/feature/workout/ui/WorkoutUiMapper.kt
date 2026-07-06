package com.stratum.feature.workout.ui

import com.stratum.domain.workout.RenderedWorkout
import com.stratum.domain.workout.SetSlotId

/**
 * Maps the domain [RenderedWorkout] → presentation [WorkoutUiState.Loaded]. No domain
 * types are embedded in the presentation model (Detekt `ForbiddenDomainTypeInUiState`).
 */
internal object WorkoutUiMapper {
    fun map(rendered: RenderedWorkout): WorkoutUiState.Loaded {
        val lifts = rendered.lifts.map { lift ->
            RenderedLiftUi(
                workoutLiftId = lift.workoutLiftId.value,
                liftName = lift.liftName,
                progressionSchemeLabel = lift.progressionScheme.name,
                sets = lift.sets.map { set ->
                    RenderedSetUi(
                        slotKey = slotKey(set.slotId),
                        positionLabel = (set.position + 1).toString(),
                        setTypeLabel = set.setType.name,
                        repRangeLabel = if (set.repMin == 0 && set.repMax == 0) null else "${set.repMin}–${set.repMax}",
                        rirLabel = "RIR ${set.rirGoal}",
                        recommendedWeightLabel = set.recommendedWeight.amount.toString(),
                        recommendedRepsLabel = set.recommendedReps.toString(),
                        loggedWeightLabel = set.loggedValue?.let { it.weight.amount.toString() },
                        loggedRepsLabel = set.loggedValue?.reps?.toString(),
                        isComplete = set.loggedValue != null,
                    )
                },
            )
        }
        return WorkoutUiState.Loaded(
            workoutName = rendered.workoutName,
            lifts = lifts,
            isFinalizing = false,
        )
    }

    private fun slotKey(slotId: SetSlotId): String = when (slotId) {
        is SetSlotId.Template -> slotId.liftSetId.value
        is SetSlotId.Overlay -> slotId.instanceId.value
    }
}
