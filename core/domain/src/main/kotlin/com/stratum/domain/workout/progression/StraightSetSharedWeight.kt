package com.stratum.domain.workout.progression

import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.Weight
import com.stratum.domain.training.EstimatedOneRm
import com.stratum.domain.workout.BACKOFF_REP_FLOOR
import com.stratum.domain.workout.SetSlot
import com.stratum.domain.workout.fatigue.capacityAtRirWithProjectReps
import com.stratum.domain.workout.fatigue.reduceWeightForLastSetFloor

/**
 * Resolves one shared straight-set load for a lift after the scheme gate has chosen a
 * candidate top weight.
 *
 * **Real data first:** for every program slot covered by the most-recent session, capacity
 * at the candidate load is checked against that slot's RIR goal. If any covered slot fails
 * [BACKOFF_REP_FLOOR], the shared load drops to the heaviest weight that clears **all**
 * covered slots (strictest gate — not last-slot-only).
 *
 * **Partial coverage:** when recent sets `M < N`, also projects the missing tail from the
 * weakest covered performance (sequential fatigue). If the last program set would fall
 * under the floor, drops further.
 *
 * Callers then re-emit **all** slot recommendations at that single weight.
 */
fun resolveStraightSetSharedWeight(
    candidateWeight: Weight,
    slots: List<SetSlot>,
    recentBlock: RecentSessionBlock?,
    perLiftSlope: Double?,
    perLiftIntercept: Double?,
    incrementTiers: List<IncrementTier>,
    loadType: LoadType,
): Weight {
    if (loadType == LoadType.Bodyweight || loadType == LoadType.Assisted) return candidateWeight
    if (candidateWeight.amount <= 0.0 || slots.isEmpty()) return candidateWeight

    val n = slots.size
    val m = recentBlock?.performances?.size ?: 0
    var shared = candidateWeight

    // Covered slots (capacity-ordered performances[i] → program slot i): strictest floor.
    if (recentBlock != null && m > 0) {
        val covered = minOf(m, n)
        for (i in 0 until covered) {
            shared = tightenForBaselineSlot(
                candidateWeight = shared,
                performance = recentBlock.performances[i],
                slot = slots[i],
                incrementTiers = incrementTiers,
                loadType = loadType,
            )
        }
    }

    // Missing tail: project from last covered anchor through last program set.
    if (m < n && recentBlock != null && m > 0) {
        shared = tightenForPartialTail(
            candidateWeight = shared,
            slots = slots,
            recentBlock = recentBlock,
            coveredCount = m,
            perLiftSlope = perLiftSlope,
            perLiftIntercept = perLiftIntercept,
            incrementTiers = incrementTiers,
            loadType = loadType,
        )
    }

    return shared
}

/**
 * If [performance] cannot clear [BACKOFF_REP_FLOOR] at [candidateWeight] for [slot],
 * reduce to the heaviest load that can (same-set capacity, no multi-set fatigue).
 */
private fun tightenForBaselineSlot(
    candidateWeight: Weight,
    performance: RecentPerformance,
    slot: SetSlot,
    incrementTiers: List<IncrementTier>,
    loadType: LoadType,
): Weight {
    val set = performance.entry.loggedSet
    val failureReps = (set.reps + (set.rir ?: 0)).coerceAtLeast(1)
    val e1rm = EstimatedOneRm.calculate(set.weight.amount, failureReps)
    val atRir = capacityAtWeightNoFatigue(
        e1rm = e1rm,
        historyWeight = set.weight,
        historyFailureReps = failureReps,
        targetWeight = candidateWeight,
        rirGoal = slot.rirGoal,
    )
    if (atRir >= BACKOFF_REP_FLOOR) return candidateWeight
    return reduceWeightForLastSetFloor(
        e1rm = e1rm,
        startFrom = candidateWeight,
        lastSetIndex = RELATIVE_SET_INDEX_BASELINE,
        rirGoal = slot.rirGoal,
        perLiftSlope = null,
        perLiftIntercept = null,
        incrementTiers = incrementTiers,
        loadType = loadType,
    )
}

private fun tightenForPartialTail(
    candidateWeight: Weight,
    slots: List<SetSlot>,
    recentBlock: RecentSessionBlock,
    coveredCount: Int,
    perLiftSlope: Double?,
    perLiftIntercept: Double?,
    incrementTiers: List<IncrementTier>,
    loadType: LoadType,
): Weight {
    val n = slots.size
    val lastSlot = slots.last()
    val missingCount = n - coveredCount
    val lastSetRelativeIndex = RELATIVE_SET_INDEX_BASELINE + missingCount

    val e1rm = anchorE1rm(recentBlock, coveredCount) ?: return candidateWeight

    val atRir = capacityAtRirWithProjectReps(
        e1rm = e1rm,
        weight = candidateWeight,
        setIndex = lastSetRelativeIndex,
        rirGoal = lastSlot.rirGoal,
        perLiftSlope = perLiftSlope,
        perLiftIntercept = perLiftIntercept,
    )
    if (atRir >= BACKOFF_REP_FLOOR) return candidateWeight

    return reduceWeightForLastSetFloor(
        e1rm = e1rm,
        startFrom = candidateWeight,
        lastSetIndex = lastSetRelativeIndex,
        rirGoal = lastSlot.rirGoal,
        perLiftSlope = perLiftSlope,
        perLiftIntercept = perLiftIntercept,
        incrementTiers = incrementTiers,
        loadType = loadType,
    )
}

private fun anchorE1rm(recentBlock: RecentSessionBlock, coveredCount: Int): Double? {
    if (coveredCount <= 0) return null
    val entry = recentBlock.performances[coveredCount - 1].entry.loggedSet
    val failureReps = (entry.reps + (entry.rir ?: 0)).coerceAtLeast(1)
    return EstimatedOneRm.calculate(entry.weight.amount, failureReps)
}

/** At-RIR capacity with no multi-set fatigue (set index 1 / same-set load change). */
private fun capacityAtWeightNoFatigue(
    e1rm: Double,
    historyWeight: Weight,
    historyFailureReps: Int,
    targetWeight: Weight,
    rirGoal: Int,
): Int {
    if (targetWeight.amount <= 0.0) return Int.MIN_VALUE / 4
    val failureAtTarget =
        if (historyWeight.amount == targetWeight.amount &&
            historyWeight.unit == targetWeight.unit
        ) {
            historyFailureReps
        } else {
            EstimatedOneRm.repsForWeight(e1rm, targetWeight.amount)
        }
    return failureAtTarget - rirGoal
}

/** Set-index of the baseline/anchor performance in [projectReps] (no decay yet). */
private const val RELATIVE_SET_INDEX_BASELINE: Int = 1
