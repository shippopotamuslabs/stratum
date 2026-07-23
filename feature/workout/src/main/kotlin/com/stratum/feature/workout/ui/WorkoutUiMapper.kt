package com.stratum.feature.workout.ui

import com.stratum.core.ui.component.formatWeightAmount
import com.stratum.core.ui.component.formatWeightAmountForDisplay
import com.stratum.core.ui.component.formatWeightForDisplay
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.WeightConversion
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
 *
 * Weight amounts on the presentation model are expressed in [displayUnit].
 * Live logging **persists Lb** (canonical storage); the ViewModel converts display → Lb on commit.
 */
internal object WorkoutUiMapper {
    fun map(
        rendered: RenderedWorkout,
        sessionStartedAtEpochMilli: Long,
        hasIncompleteLifts: Boolean = false,
        hasDirtyEdits: Boolean = false,
        imageUrlByLiftId: Map<String, String?> = emptyMap(),
        displayUnit: Weight.WeightUnit = Weight.WeightUnit.Lb,
        displayUnitEpoch: Int = 0,
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
                        val displayWeight = formatWeightForDisplay(
                            weight = prevWeight,
                            displayUnit = displayUnit,
                            assistMode = assistMode,
                            includeUnit = true,
                        )
                        "$displayWeight × $prevReps"
                    } else {
                        "—"
                    }

                    // Domain recs are in the recommended weight's unit (live path: Lb).
                    val recSourceUnit = set.recommendedWeight.unit
                    val recommendedWeightAmount =
                        WeightConversion.convertAmount(
                            if (assistMode) abs(set.recommendedWeight.amount)
                            else set.recommendedWeight.amount,
                            recSourceUnit,
                            displayUnit,
                        )
                    val recommendedWeightLabel =
                        if (!showWeightColumn) {
                            ""
                        } else {
                            formatWeightAmountForDisplay(
                                amount = set.recommendedWeight.amount,
                                sourceUnit = recSourceUnit,
                                displayUnit = displayUnit,
                                assistMode = assistMode,
                            )
                        }

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

                    val loggedDisplayAmount = set.loggedValue?.let { logged ->
                        WeightConversion.convertAmount(
                            if (assistMode) abs(logged.weight.amount) else logged.weight.amount,
                            logged.weight.unit,
                            displayUnit,
                        )
                    }

                    RenderedSetUi(
                        slotKey = slotKey(set.slotId),
                        positionLabel = (set.position + 1).toString(),
                        setTypeLabel = set.setType.name,
                        repRangeLabel = if (set.repMin == 0 && set.repMax == 0) null else "${set.repMin}–${set.repMax}",
                        rirLabel = "RIR ${set.rirGoal}",
                        recommendedWeightLabel = recommendedWeightLabel,
                        recommendedRepsLabel = recommendedRepsLabel,
                        loggedWeightLabel = loggedDisplayAmount?.let {
                            if (!showWeightColumn) "" else formatWeightAmount(it)
                        },
                        loggedRepsLabel = set.loggedValue?.reps?.toString(),
                        loggedWeightAmount = loggedDisplayAmount,
                        loggedReps = set.loggedValue?.reps,
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
            displayUnitEpoch = displayUnitEpoch,
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
