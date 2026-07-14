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
    val (rationaleRes, rationaleArgs) = rationale.toDisplaySpec(liftName)
    return when (val actionLocal = action) {
        is CheckInAction.Maintain -> null
        is CheckInAction.SwapLift ->
            CheckInRecommendationUi.Swap(
                id = workoutLiftId.value,
                titleRes = R.string.checkin_rec_title_swap,
                titleArgs = listOf(liftName, actionLocal.suggestedLiftName),
                rationaleRes = rationaleRes,
                rationaleArgs = rationaleArgs,
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
                titleRes = R.string.checkin_rec_title_increase,
                titleArgs = listOf(actionLocal.addSets, liftName),
                rationaleRes = rationaleRes,
                rationaleArgs = rationaleArgs,
                accepted = accepted,
                liftName = liftName,
                currentLiftId = liftId.value,
                currentSetCount = currentSetCount,
                addSets = actionLocal.addSets,
            )
        is CheckInAction.CutVolume ->
            CheckInRecommendationUi.CutVolume(
                id = workoutLiftId.value,
                titleRes =
                    if (actionLocal.removeSets == 0) {
                        R.string.checkin_rec_title_cut_floor
                    } else {
                        R.string.checkin_rec_title_cut
                    },
                titleArgs =
                    if (actionLocal.removeSets == 0) {
                        listOf(liftName)
                    } else {
                        listOf(actionLocal.removeSets, liftName)
                    },
                rationaleRes = rationaleRes,
                rationaleArgs = rationaleArgs,
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

/** Resource id + format args for a rationale (resolved in Compose via stringResource). */
internal fun CheckInRationale.toDisplaySpec(liftName: String): Pair<Int, List<Any>> {
    val cutPct = (VOLUME_CUT_FRACTION * 100).toInt()
    return when (this) {
        is CheckInRationale.ClimbingMaintain ->
            R.string.checkin_rationale_climbing to listOf(liftName)
        is CheckInRationale.NoClearChange ->
            R.string.checkin_rationale_no_clear_change to listOf(liftName)
        is CheckInRationale.SoftStaleSwap ->
            softStaleSwapRes(softTrend) to listOf(tenureWeeks)
        is CheckInRationale.SoftFreshIncrease ->
            softFreshIncreaseRes(softTrend) to listOf(tenureWeeks)
        is CheckInRationale.SoftStaleIncreaseNoCandidate ->
            softStaleNoCandidateRes(softTrend) to emptyList()
        is CheckInRationale.DroppingMildRecoveryHold ->
            R.string.checkin_rationale_dropping_mild_hold to listOf(liftName)
        is CheckInRationale.SoftFreshAtVolumeCeiling ->
            R.string.checkin_rationale_soft_fresh_at_ceiling to listOf(liftName)
        is CheckInRationale.SoftStaleNoCandidateAtCeiling ->
            R.string.checkin_rationale_soft_stale_no_candidate_ceiling to listOf(liftName)
        is CheckInRationale.MuscleLeverAlreadyTaken ->
            R.string.checkin_rationale_muscle_lever_taken to listOf(liftName)
        is CheckInRationale.JointPainCut ->
            if (removeSets == 0) {
                R.string.checkin_rationale_joint_floor to emptyList()
            } else {
                R.string.checkin_rationale_joint_cut to listOf(cutPct)
            }
        is CheckInRationale.SevereUnderRecoveryCut ->
            if (removeSets == 0) {
                R.string.checkin_rationale_severe_floor to emptyList()
            } else {
                R.string.checkin_rationale_severe_cut to listOf(cutPct)
            }
        is CheckInRationale.AlreadyAtFloorSets ->
            R.string.checkin_rationale_already_floor to emptyList()
    }
}

private fun softStaleSwapRes(trend: SoftPerformanceTrend): Int =
    when (trend) {
        SoftPerformanceTrend.Flat -> R.string.checkin_rationale_soft_stale_swap_flat
        SoftPerformanceTrend.Dropping -> R.string.checkin_rationale_soft_stale_swap_dropping
    }

private fun softFreshIncreaseRes(trend: SoftPerformanceTrend): Int =
    when (trend) {
        SoftPerformanceTrend.Flat -> R.string.checkin_rationale_soft_fresh_increase_flat
        SoftPerformanceTrend.Dropping -> R.string.checkin_rationale_soft_fresh_increase_dropping
    }

private fun softStaleNoCandidateRes(trend: SoftPerformanceTrend): Int =
    when (trend) {
        SoftPerformanceTrend.Flat -> R.string.checkin_rationale_soft_stale_no_candidate_flat
        SoftPerformanceTrend.Dropping -> R.string.checkin_rationale_soft_stale_no_candidate_dropping
    }
