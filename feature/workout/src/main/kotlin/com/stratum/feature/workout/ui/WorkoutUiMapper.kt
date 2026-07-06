package com.stratum.feature.workout.ui

import com.stratum.domain.workout.RenderedWorkout
import com.stratum.domain.workout.SetSlotId

/**
 * Maps the domain [RenderedWorkout] → presentation [WorkoutUiState.Loaded]. No domain
 * types are embedded in the presentation model (Detekt `ForbiddenDomainTypeInUiState`).
 */
internal object WorkoutUiMapper {
    fun map(rendered: RenderedWorkout, sessionStartedAtEpochMilli: Long): WorkoutUiState.Loaded {
        val lifts = rendered.lifts.map { lift ->
            RenderedLiftUi(
                workoutLiftId = lift.workoutLiftId.value,
                liftName = lift.liftName,
                progressionSchemeLabel = lift.progressionScheme.name,
                sets = lift.sets.map { set ->
                    val prevWeight = set.previousWeight
                    val prevReps = set.previousReps
                    val prevLabel = if (prevWeight != null && prevReps != null) {
                        val amt = prevWeight.amount
                        val amtStr = if (amt % 1.0 == 0.0) amt.toInt().toString() else amt.toString()
                        val unitStr = prevWeight.unit.name.lowercase()
                        "$amtStr $unitStr × $prevReps"
                    } else {
                        "—"
                    }

                    val recommendedWeightAmount = set.recommendedWeight.amount
                    val recommendedWeightLabel = if (recommendedWeightAmount == 0.0) "" else recommendedWeightAmount.toString()

                    val recommendedRepsLabel = if (set.previousReps != null && set.recommendedReps > 0) {
                        set.recommendedReps.toString()
                    } else {
                        if (set.repMin == 0 && set.repMax == 0) "0" else "${set.repMin}-${set.repMax}"
                    }

                    RenderedSetUi(
                        slotKey = slotKey(set.slotId),
                        positionLabel = (set.position + 1).toString(),
                        setTypeLabel = set.setType.name,
                        repRangeLabel = if (set.repMin == 0 && set.repMax == 0) null else "${set.repMin}–${set.repMax}",
                        rirLabel = "RIR ${set.rirGoal}",
                        recommendedWeightLabel = recommendedWeightLabel,
                        recommendedRepsLabel = recommendedRepsLabel,
                        loggedWeightLabel = set.loggedValue?.let { it.weight.amount.toString() },
                        loggedRepsLabel = set.loggedValue?.reps?.toString(),
                        isComplete = set.loggedValue != null,
                        previousWeightLabel = prevLabel,
                        rirGoal = set.rirGoal,
                        repMin = set.repMin,
                        repMax = set.repMax,
                        recommendedWeightAmount = recommendedWeightAmount,
                        recommendedReps = set.recommendedReps,
                        loggedRir = set.loggedValue?.rir,
                    )
                },
                supersetGroupId = lift.supersetGroupId?.value,
            )
        }
        return WorkoutUiState.Loaded(
            workoutName = rendered.workoutName,
            lifts = lifts,
            isFinalizing = false,
            sessionStartedAtEpochMilli = sessionStartedAtEpochMilli,
        )
    }

    private fun slotKey(slotId: SetSlotId): String = when (slotId) {
        is SetSlotId.Template -> slotId.liftSetId.value
        is SetSlotId.Overlay -> slotId.instanceId.value
    }
}
