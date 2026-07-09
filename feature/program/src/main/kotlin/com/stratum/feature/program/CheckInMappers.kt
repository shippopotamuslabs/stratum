package com.stratum.feature.program

import com.stratum.domain.checkin.CheckInAction
import com.stratum.domain.checkin.CheckInApplyCommand
import com.stratum.domain.checkin.CheckInRationale
import com.stratum.domain.checkin.CheckInRecommendation
import com.stratum.domain.checkin.SoftPerformanceTrend
import com.stratum.domain.checkin.VOLUME_CUT_FRACTION
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.feature.program.ui.CheckInRecommendationUi

internal fun CheckInRecommendation.toUi(accepted: Boolean = true): CheckInRecommendationUi? {
    val rationaleText = rationale.toDisplayString(liftName)
    return when (val actionLocal = action) {
        is CheckInAction.Maintain -> null
        is CheckInAction.SwapLift ->
            CheckInRecommendationUi.Swap(
                id = workoutLiftId.value,
                title = "Swap $liftName → ${actionLocal.suggestedLiftName}",
                rationale = rationaleText,
                accepted = accepted,
                liftName = liftName,
                currentLiftId = liftId.value,
                currentSetCount = currentSetCount,
                suggestedLiftId = actionLocal.suggestedLiftId.value,
                suggestedLiftName = actionLocal.suggestedLiftName,
            )
        is CheckInAction.IncreaseVolume ->
            CheckInRecommendationUi.IncreaseVolume(
                id = workoutLiftId.value,
                title = "Add ${actionLocal.addSets} set to $liftName",
                rationale = rationaleText,
                accepted = accepted,
                liftName = liftName,
                currentLiftId = liftId.value,
                currentSetCount = currentSetCount,
                addSets = actionLocal.addSets,
            )
        is CheckInAction.CutVolume ->
            CheckInRecommendationUi.CutVolume(
                id = workoutLiftId.value,
                title =
                    if (actionLocal.removeSets == 0) {
                        "$liftName already at 1 set — manage load in-session"
                    } else {
                        "Remove ${actionLocal.removeSets} set(s) from $liftName"
                    },
                rationale = rationaleText,
                accepted = accepted,
                liftName = liftName,
                currentLiftId = liftId.value,
                currentSetCount = currentSetCount,
                removeSets = actionLocal.removeSets,
            )
    }
}

internal fun CheckInRecommendationUi.toApplyCommand(): CheckInApplyCommand? {
    if (!accepted) return null
    return when (this) {
        is CheckInRecommendationUi.Swap ->
            CheckInApplyCommand.Swap(
                workoutLiftId = WorkoutLiftId(id),
                newLiftId = LiftId(suggestedLiftId),
            )
        is CheckInRecommendationUi.IncreaseVolume ->
            CheckInApplyCommand.SetWorkingSets(
                workoutLiftId = WorkoutLiftId(id),
                targetSetCount = currentSetCount + addSets,
            )
        is CheckInRecommendationUi.CutVolume -> {
            if (removeSets <= 0) {
                null
            } else {
                CheckInApplyCommand.SetWorkingSets(
                    workoutLiftId = WorkoutLiftId(id),
                    targetSetCount = (currentSetCount - removeSets).coerceAtLeast(1),
                )
            }
        }
    }
}

internal fun CheckInRationale.toDisplayString(liftName: String): String {
    val cutPct = (VOLUME_CUT_FRACTION * 100).toInt()
    return when (this) {
        is CheckInRationale.ClimbingMaintain ->
            "e1RM climbing — keep $liftName as programmed"
        is CheckInRationale.NoClearChange ->
            "No clear change needed for $liftName"
        is CheckInRationale.SoftStaleSwap ->
            "e1RM ${softTrend.label()}, lift ${tenureWeeks}w on rotation → swap"
        is CheckInRationale.SoftFreshIncrease ->
            "e1RM ${softTrend.label()}, variation still fresh (${tenureWeeks}w) → add 1 set"
        is CheckInRationale.SoftStaleIncreaseNoCandidate ->
            "e1RM ${softTrend.label()}, stale but no free swap → add 1 set"
        is CheckInRationale.JointPainCut ->
            if (removeSets == 0) {
                "Joint pain reported — already at 1 set; manage load in-session"
            } else {
                "Joint pain reported → cut ~$cutPct% sets"
            }
        is CheckInRationale.SevereUnderRecoveryCut ->
            if (removeSets == 0) {
                "Under-recovered — already at 1 set; manage load in-session"
            } else {
                "Severe under-recovery → cut ~$cutPct% sets"
            }
        is CheckInRationale.AlreadyAtFloorSets ->
            "Already at 1 set — no set cut; manage load in-session"
    }
}

private fun SoftPerformanceTrend.label(): String =
    when (this) {
        SoftPerformanceTrend.Flat -> "flat"
        SoftPerformanceTrend.Dropping -> "soft/dropping"
    }
