package com.stratum.feature.workout.ui

import com.stratum.domain.workout.RenderedWorkout
import com.stratum.domain.workout.SetSlotId
import kotlin.math.roundToInt

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
                    val recommendedWeightLabel = formatWeightAmount(recommendedWeightAmount)

                    // Show a concrete rep count when either (a) a live overlay
                    // recommendation projected the next-set reps from the most recent
                    // performed set (D4), or (b) the snapshot baseline carries a prior
                    // performed set's reps. Otherwise fall back to the rep-range label.
                    val recommendedRepsLabel = if (set.recommendedReps > 0 &&
                        (set.hasLiveRepsRecommendation || set.previousReps != null)
                    ) {
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
                        loggedWeightLabel = set.loggedValue?.let { formatWeightAmount(it.weight.amount) },
                        loggedRepsLabel = set.loggedValue?.reps?.toString(),
                        isComplete = set.loggedValue != null,
                        previousWeightLabel = prevLabel,
                        rirGoal = set.rirGoal,
                        repMin = set.repMin,
                        repMax = set.repMax,
                        recommendedWeightAmount = recommendedWeightAmount,
                        recommendedReps = set.recommendedReps,
                        hasLiveRepsRecommendation = set.hasLiveRepsRecommendation,
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

    /**
     * Formats a weight amount for display: blank when 0, otherwise rounded to at most one
     * decimal place with trailing `.0` stripped (so `77.4216` → `"77.4"`, `75.0` →
     * `"75"`). Keeps the prior `previousWeightLabel` formatting contract for consistency.
     */
    private fun formatWeightAmount(amount: Double): String {
        if (amount == 0.0) return ""
        val rounded = (amount * 10.0).roundToInt() / 10.0
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    }
}
