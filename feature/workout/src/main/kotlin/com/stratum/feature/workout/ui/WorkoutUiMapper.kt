package com.stratum.feature.workout.ui

import com.stratum.domain.lift.LoadType
import com.stratum.domain.workout.RenderedWorkout
import com.stratum.domain.workout.SetSlotId
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Maps the domain [RenderedWorkout] → presentation [WorkoutUiState.Loaded]. No domain
 * types are embedded in the presentation model (Detekt `ForbiddenDomainTypeInUiState`).
 *
 * Loading modality ([LoadType]) shapes the presentation copy without leaking the domain
 * enum: bodyweight lifts hide the weight column; assisted lifts display weights as their
 * absolute value (the user types a positive assistance amount; the ViewModel negates it
 * before persisting); weighted lifts are unchanged.
 */
internal object WorkoutUiMapper {
    fun map(rendered: RenderedWorkout, sessionStartedAtEpochMilli: Long): WorkoutUiState.Loaded {
        val lifts = rendered.lifts.map { lift ->
            val showWeightColumn = lift.loadType != LoadType.Bodyweight
            val weightHeaderLabel = when (lift.loadType) {
                LoadType.Bodyweight -> ""
                LoadType.Assisted -> "Assist"
                LoadType.Weighted -> "Weight"
            }
            val assistMode = lift.loadType == LoadType.Assisted
            RenderedLiftUi(
                workoutLiftId = lift.workoutLiftId.value,
                liftName = lift.liftName,
                showWeightColumn = showWeightColumn,
                weightHeaderLabel = weightHeaderLabel,
                sets = lift.sets.map { set ->
                    val prevWeight = set.previousWeight
                    val prevReps = set.previousReps
                    val prevLabel = if (prevWeight != null && prevReps != null) {
                        // Assisted "prev" is the absolute assistance amount (sign-stripped).
                        val raw = if (assistMode) abs(prevWeight.amount) else prevWeight.amount
                        val amtStr = if (raw % 1.0 == 0.0) raw.toInt().toString() else raw.toString()
                        val unitStr = prevWeight.unit.name.lowercase()
                        "$amtStr $unitStr × $prevReps"
                    } else {
                        "—"
                    }

                    val recommendedWeightAmount = set.recommendedWeight.amount
                    val recommendedWeightLabel =
                        if (!showWeightColumn) ""
                        else formatWeightAmount(recommendedWeightAmount, assistMode)

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
                        loggedWeightLabel = set.loggedValue?.let {
                            if (!showWeightColumn) "" else formatWeightAmount(it.weight.amount, assistMode)
                        },
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
                        showWeightInput = showWeightColumn,
                        assistMode = assistMode,
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
     *
     * When [assistMode] is true the amount is sign-stripped first (assisted weights are
     * stored negative; the user sees a positive assistance figure).
     */
    private fun formatWeightAmount(amount: Double, assistMode: Boolean = false): String {
        val display = if (assistMode) abs(amount) else amount
        if (display == 0.0) return ""
        val rounded = (display * 10.0).roundToInt() / 10.0
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    }
}
