package com.stratum.domain.workout.fatigue

import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.WeightConversion
import com.stratum.domain.lift.incrementForAmount
import com.stratum.domain.lift.roundToIncrement
import com.stratum.domain.lift.toStorageLb
import com.stratum.domain.workout.BACKOFF_REP_FLOOR
import kotlin.math.max

/**
 * Shared last-set floor reduce oracle used by live [com.stratum.domain.workout.RecommendationEngine]
 * and snapshot [com.stratum.domain.workout.progression.resolveStraightSetSharedWeight].
 *
 * Heaviest load ≤ [startFrom] with last-set capacity ≥ [BACKOFF_REP_FLOOR]. Never returns a
 * non-positive weight when a positive continuous solution exists.
 *
 * @param capacityAtRir (weightAmount) → failure capacity at RIR for the last-set index.
 *   Callers supply FatigueModel-backed or [projectReps]-backed capacity so live and snapshot
 *   share the lattice search without cloning it.
 */
fun reduceWeightForLastSetFloor(
    startFrom: Weight,
    incrementTiers: List<IncrementTier>,
    loadType: LoadType,
    capacityAtRir: (weightAmount: Double) -> Int,
    defaultBumpAmount: (Weight.WeightUnit) -> Double = ::defaultLoadBumpAmount,
): Weight {
    // Tiers and capacity lattice are Lb; normalize input so mixed-unit callers stay coherent.
    val startLb = startFrom.toStorageLb()
    val unit = Weight.WeightUnit.Lb
    val ceiling = startLb.amount.coerceAtLeast(0.0)
    if (ceiling <= 0.0) {
        return Weight(defaultBumpAmount(unit), unit)
    }

    fun cap(amount: Double): Int = capacityAtRir(amount)

    var lo = 0.0
    var hi = ceiling
    var bestContinuous = 0.0
    repeat(BINARY_SEARCH_ITERS) {
        val mid = (lo + hi) / 2.0
        if (mid > 0.0 && cap(mid) >= BACKOFF_REP_FLOOR) {
            bestContinuous = mid
            lo = mid
        } else {
            hi = mid
        }
    }

    if (bestContinuous <= 0.0) {
        return Weight(minPositiveStep(ceiling, incrementTiers, unit, defaultBumpAmount), unit)
    }

    if (incrementTiers.isEmpty() || loadType == LoadType.Bodyweight) {
        return Weight(bestContinuous, unit)
    }

    var candidate = incrementTiers.roundToIncrement(Weight(bestContinuous, unit), loadType)
    if (candidate.amount > ceiling) {
        candidate = stepDownAligned(candidate, incrementTiers, loadType, defaultBumpAmount)
    }

    var guard = 0
    while (
        guard < MAX_TIER_STEPS &&
        candidate.amount > 0.0 &&
        cap(candidate.amount) < BACKOFF_REP_FLOOR
    ) {
        val next = stepDownAligned(candidate, incrementTiers, loadType, defaultBumpAmount)
        if (next.amount <= 0.0 || next.amount >= candidate.amount - AMOUNT_EPS) {
            break
        }
        candidate = next
        guard++
    }

    val candidateOk = candidate.amount > 0.0 && cap(candidate.amount) >= BACKOFF_REP_FLOOR
    return if (candidateOk) {
        candidate
    } else {
        Weight(bestContinuous, unit)
    }
}

/**
 * At-RIR capacity via [projectReps] (population / per-lift slope). Used by snapshot look-ahead
 * and as the default for pure unit tests.
 */
fun capacityAtRirWithProjectReps(
    e1rm: Double,
    weight: Weight,
    setIndex: Int,
    rirGoal: Int,
    perLiftSlope: Double?,
    perLiftIntercept: Double?,
): Int {
    if (WeightConversion.loadMagnitudeLb(weight) <= 0.0) return Int.MIN_VALUE / 4
    val raw0Rir = projectReps(
        baselineOneRm = e1rm,
        targetWeight = weight.toStorageLb(),
        nextSetIndex = setIndex,
        perLiftSlope = perLiftSlope,
        perLiftIntercept = perLiftIntercept,
    )
    return raw0Rir - rirGoal
}

/**
 * Convenience: reduce using [projectReps]-backed capacity (snapshot path).
 */
fun reduceWeightForLastSetFloor(
    e1rm: Double,
    startFrom: Weight,
    lastSetIndex: Int,
    rirGoal: Int,
    perLiftSlope: Double?,
    perLiftIntercept: Double?,
    incrementTiers: List<IncrementTier>,
    loadType: LoadType,
): Weight = reduceWeightForLastSetFloor(
    startFrom = startFrom,
    incrementTiers = incrementTiers,
    loadType = loadType,
    capacityAtRir = { amountLb ->
        // Lattice search runs in Lb; amount is always a pound magnitude.
        capacityAtRirWithProjectReps(
            e1rm = e1rm,
            weight = Weight(amountLb, Weight.WeightUnit.Lb),
            setIndex = lastSetIndex,
            rirGoal = rirGoal,
            perLiftSlope = perLiftSlope,
            perLiftIntercept = perLiftIntercept,
        )
    },
)

/** Default too-easy / reduce step when no tier band applies (5 lb / 2.5 kg). */
fun defaultLoadBumpAmount(unit: Weight.WeightUnit): Double = when (unit) {
    Weight.WeightUnit.Lb -> DEFAULT_BUMP_LB
    Weight.WeightUnit.Kg -> DEFAULT_BUMP_KG
}

private fun stepDownAligned(
    weight: Weight,
    incrementTiers: List<IncrementTier>,
    loadType: LoadType,
    defaultBumpAmount: (Weight.WeightUnit) -> Double,
): Weight {
    // Tiers are Lb magnitudes; lattice ops always run in canonical storage unit.
    val weightLb = weight.toStorageLb()
    val step = incrementTiers.incrementForAmount(weightLb.amount).let { s ->
        if (s > 0.0) s else defaultBumpAmount(Weight.WeightUnit.Lb)
    }
    val lowerAmount = max(0.0, weightLb.amount - step)
    if (lowerAmount <= 0.0) {
        return Weight(0.0, Weight.WeightUnit.Lb)
    }
    val rounded =
        incrementTiers.roundToIncrement(Weight(lowerAmount, Weight.WeightUnit.Lb), loadType)
    return if (rounded.amount <= lowerAmount + AMOUNT_EPS) {
        rounded
    } else {
        val forced = max(0.0, rounded.amount - step)
        if (forced <= 0.0) {
            Weight(0.0, Weight.WeightUnit.Lb)
        } else {
            Weight(forced, Weight.WeightUnit.Lb)
        }
    }
}

private fun minPositiveStep(
    ceiling: Double,
    incrementTiers: List<IncrementTier>,
    unit: Weight.WeightUnit,
    defaultBumpAmount: (Weight.WeightUnit) -> Double,
): Double {
    val ceilingLb =
        WeightConversion.convertAmount(ceiling, unit, Weight.WeightUnit.Lb)
    if (incrementTiers.isEmpty()) {
        val bump = defaultBumpAmount(Weight.WeightUnit.Lb)
        return bump.coerceAtMost(ceilingLb.coerceAtLeast(bump))
    }
    val step = incrementTiers.incrementForAmount(ceilingLb.coerceAtLeast(1.0)).let { s ->
        if (s > 0.0) s else defaultBumpAmount(Weight.WeightUnit.Lb)
    }
    return step
}

private const val BINARY_SEARCH_ITERS: Int = 48
private const val MAX_TIER_STEPS: Int = 256
private const val AMOUNT_EPS: Double = 1e-9
private const val DEFAULT_BUMP_LB: Double = 5.0
private const val DEFAULT_BUMP_KG: Double = 2.5
