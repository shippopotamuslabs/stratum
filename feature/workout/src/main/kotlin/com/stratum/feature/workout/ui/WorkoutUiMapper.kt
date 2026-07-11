package com.stratum.feature.workout.ui

import com.stratum.core.ui.component.formatWeightAmount
import com.stratum.domain.lift.LoadType
import com.stratum.domain.workout.RenderedWorkout
import com.stratum.domain.workout.SetSlotId
import kotlin.math.abs

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
    fun map(
        rendered: RenderedWorkout,
        sessionStartedAtEpochMilli: Long,
        hasIncompleteLifts: Boolean = false,
        hasDirtyEdits: Boolean = false,
        imageUrlByLiftId: Map<String, String?> = emptyMap(),
    ): WorkoutUiState.Loaded {
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
                liftId = lift.liftId.value,
                liftName = lift.liftName,
                showWeightColumn = showWeightColumn,
                weightHeaderLabel = weightHeaderLabel,
                useRestTimerBetweenLifts = lift.useRestTimerBetweenLifts,
                imageUrl = imageUrlByLiftId[lift.liftId.value],
                navigatorInitials = liftNavigatorInitials(lift.liftName),
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
                    // performed set's reps, or (c) it was projected via straight-sets/fatigue decay.
                    // Otherwise fall back to the rep-range label for top sets, or blank for
                    // RIR-only backoff slots with no history (repMin=repMax=0 → domain
                    // cold-starts at 0, same as weight amount 0 → blank placeholder).
                    val showConcreteReps = set.recommendedReps > 0 &&
                        (set.hasLiveRepsRecommendation || set.previousReps != null || set.isProjected)

                    val recommendedRepsLabel = if (showConcreteReps) {
                        set.recommendedReps.toString()
                    } else {
                        if (set.repMin == 0 && set.repMax == 0) "" else "${set.repMin}-${set.repMax}"
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
                        isProjected = set.isProjected,
                        loggedRir = set.loggedValue?.rir,
                        showWeightInput = showWeightColumn,
                        assistMode = assistMode,
                        restSeconds = set.restSeconds,
                    )
                },
                supersetGroupId = lift.supersetGroupId?.value,
            )
        }
        return WorkoutUiState.Loaded(
            workoutId = rendered.workoutId.value,
            workoutName = rendered.workoutName,
            lifts = lifts,
            isFinalizing = false,
            sessionStartedAtEpochMilli = sessionStartedAtEpochMilli,
            hasIncompleteLifts = hasIncompleteLifts,
            hasDirtyEdits = hasDirtyEdits,
        )
    }

    private fun slotKey(slotId: SetSlotId): String = when (slotId) {
        is SetSlotId.Template -> slotId.liftSetId.value
        is SetSlotId.Overlay -> slotId.instanceId.value
    }

    /**
     * Navigator initials: first letter of first two words, or first two letters of one word.
     * Non-letter characters are ignored within each word token.
     */
    internal fun liftNavigatorInitials(name: String): String {
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return ""
        return if (words.size == 1) {
            words[0].filter { it.isLetterOrDigit() }.take(2).uppercase()
        } else {
            buildString {
                words.take(2).forEach { word ->
                    val ch = word.firstOrNull { it.isLetterOrDigit() }
                    if (ch != null) append(ch.uppercaseChar())
                }
            }
        }
    }
}
