package com.stratum.domain.workout

import com.stratum.domain.training.EstimatedOneRm
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.WeightConversion
import com.stratum.domain.lift.incrementForAmount
import com.stratum.domain.lift.roundToIncrement
import com.stratum.domain.lift.toStorageLb
import com.stratum.domain.workout.fatigue.FatigueModel
import com.stratum.domain.workout.fatigue.defaultLoadBumpAmount
import com.stratum.domain.workout.fatigue.reduceWeightForLastSetFloor as sharedReduceWeightForLastSetFloor
import javax.inject.Inject

/**
 * Overlay-only recommendation engine (D4). Hold-vs-reduce decision for remaining sets,
 * computed from the most recent performed set. **Weight selection uses the same
 * [FatigueModel] oracle as rep projection** in [RecomputeRecommendationsUseCase].
 *
 * Decision tree (in order):
 * 1. **On-track top set** — goal weight in RIR-adjusted range → `null` (snapshot stays put).
 * 2. **Working weight** — re-anchor heavier/lighter vs intentional backoff
 *    ([resolveWorkingWeight]). Callers must pass **snapshot / program** prescriptions as
 *    [currentWeight] / [completedSetPrescribedWeight], never prior live overlay weights
 *    (avoids too-easy bump ratcheting on every recompute).
 * 3. **Too easy** — ceiling exceeded by ≥ 2 RIR-adjusted reps → try one increment step
 *    (tier step, or 5 lb / 2.5 kg default). If bump fails last-set floor → keep current.
 *    Only reduce when even current fails the floor.
 * 4. **Hold vs Reduce** — last remaining set capacity ≥ [BACKOFF_REP_FLOOR] → Hold;
 *    else Reduce via oracle search.
 *
 * Last-set index: performed = 1; with [remainingCount] upcoming slots after the anchor,
 * last index = `remainingCount + 1`.
 *
 * [loadType]: Bodyweight / Assisted → `null`.
 */
class RecommendationEngine @Inject constructor(
    private val fatigueModel: FatigueModel,
) {
    fun recommendNextSet(
        lastPerformed: LoggedSetValue?,
        completedSlot: SetSlot?,
        targetSlot: SetSlot,
        currentWeight: Weight,
        remainingCount: Int = 1,
        incrementTiers: List<IncrementTier> = emptyList(),
        perLiftSlope: Double? = null,
        perLiftIntercept: Double? = null,
        loadType: LoadType = LoadType.Weighted,
        completedSetPrescribedWeight: Weight? = null,
    ): RecommendationOutcome? {
        if (loadType == LoadType.Bodyweight || loadType == LoadType.Assisted) return null
        if (lastPerformed == null || lastPerformed.weight.amount <= 0.0) return null

        return recommendForPerformed(
            lastPerformed = lastPerformed,
            completedSlot = completedSlot,
            targetSlot = targetSlot,
            currentWeight = currentWeight,
            remainingCount = remainingCount,
            incrementTiers = incrementTiers,
            perLiftSlope = perLiftSlope,
            perLiftIntercept = perLiftIntercept,
            loadType = loadType,
            completedSetPrescribedWeight = completedSetPrescribedWeight,
        )
    }

    private fun recommendForPerformed(
        lastPerformed: LoggedSetValue,
        completedSlot: SetSlot?,
        targetSlot: SetSlot,
        currentWeight: Weight,
        remainingCount: Int,
        incrementTiers: List<IncrementTier>,
        perLiftSlope: Double?,
        perLiftIntercept: Double?,
        loadType: LoadType,
        completedSetPrescribedWeight: Weight?,
    ): RecommendationOutcome? {
        if (isOnTrackTopSet(lastPerformed, completedSlot, completedSetPrescribedWeight)) return null

        val effectiveRir = lastPerformed.rir ?: 0
        val reps = lastPerformed.reps + effectiveRir
        val loadMagnitudeLb = WeightConversion.loadMagnitudeLb(lastPerformed.weight)
        val e1rm =
            if (reps > 0 && loadMagnitudeLb > 0.0) {
                EstimatedOneRm.calculate(loadMagnitudeLb, reps)
            } else {
                0.0
            }
        if (e1rm <= 0.0) return null

        val effectiveWeight = resolveWorkingWeight(
            performed = lastPerformed.weight,
            targetPrescribed = currentWeight,
            completedPrescribed = completedSetPrescribedWeight,
        )
        val lastSetIndex = lastRemainingSetIndex(remainingCount)
        val tooEasyHold = tryTooEasyHold(
            lastPerformed = lastPerformed,
            completedSlot = completedSlot,
            e1rm = e1rm,
            lastSetIndex = lastSetIndex,
            targetRirGoal = targetSlot.rirGoal,
            perLiftSlope = perLiftSlope,
            perLiftIntercept = perLiftIntercept,
            incrementTiers = incrementTiers,
            loadType = loadType,
        )
        return tooEasyHold ?: holdOrReduce(
            e1rm = e1rm,
            effectiveWeight = effectiveWeight,
            lastSetIndex = lastSetIndex,
            targetRirGoal = targetSlot.rirGoal,
            perLiftSlope = perLiftSlope,
            perLiftIntercept = perLiftIntercept,
            incrementTiers = incrementTiers,
            loadType = loadType,
        )
    }

    /**
     * When the completed top set was too easy, try one increment above the performed load.
     * Returns Hold(bumped) if the last set still clears the floor; otherwise null so the
     * caller falls through to current-load Hold/Reduce (never force a cut for a failed bump).
     */
    private fun tryTooEasyHold(
        lastPerformed: LoggedSetValue,
        completedSlot: SetSlot?,
        e1rm: Double,
        lastSetIndex: Int,
        targetRirGoal: Int,
        perLiftSlope: Double?,
        perLiftIntercept: Double?,
        incrementTiers: List<IncrementTier>,
        loadType: LoadType,
    ): RecommendationOutcome.Hold? {
        val effectiveRir = lastPerformed.rir ?: 0
        val achieved = lastPerformed.reps + effectiveRir - (completedSlot?.rirGoal ?: 0)
        val isTooEasy = completedSlot != null && completedSlot.isTopSet &&
            completedSlot.repMax > 0 &&
            (achieved - completedSlot.repMax >= 2)
        if (!isTooEasy) return null

        val bumped = tooEasyBump(lastPerformed.weight, incrementTiers, loadType)
        val bumpedCapacity = capacityAtRir(
            e1rm = e1rm,
            weight = bumped,
            setIndex = lastSetIndex,
            rirGoal = targetRirGoal,
            perLiftSlope = perLiftSlope,
            perLiftIntercept = perLiftIntercept,
        )
        return if (bumpedCapacity >= BACKOFF_REP_FLOOR) {
            RecommendationOutcome.Hold(bumped)
        } else {
            null
        }
    }

    private fun holdOrReduce(
        e1rm: Double,
        effectiveWeight: Weight,
        lastSetIndex: Int,
        targetRirGoal: Int,
        perLiftSlope: Double?,
        perLiftIntercept: Double?,
        incrementTiers: List<IncrementTier>,
        loadType: LoadType,
    ): RecommendationOutcome {
        val lastSetCapacityAtRir = capacityAtRir(
            e1rm = e1rm,
            weight = effectiveWeight,
            setIndex = lastSetIndex,
            rirGoal = targetRirGoal,
            perLiftSlope = perLiftSlope,
            perLiftIntercept = perLiftIntercept,
        )
        return if (lastSetCapacityAtRir >= BACKOFF_REP_FLOOR) {
            RecommendationOutcome.Hold(effectiveWeight)
        } else {
            RecommendationOutcome.Reduce(
                reduceWeightForLastSetFloor(
                    e1rm = e1rm,
                    startFrom = effectiveWeight,
                    lastSetIndex = lastSetIndex,
                    rirGoal = targetRirGoal,
                    perLiftSlope = perLiftSlope,
                    perLiftIntercept = perLiftIntercept,
                    incrementTiers = incrementTiers,
                    loadType = loadType,
                ),
            )
        }
    }

    /**
     * One product increment above [weight]: the lift's band step when tiers exist, else
     * 5 lb / 2.5 kg. Then tier-round so the suggestion lands on plates.
     */
    internal fun tooEasyBump(
        weight: Weight,
        incrementTiers: List<IncrementTier>,
        loadType: LoadType,
    ): Weight {
        // Tiers are Lb magnitudes; lattice ops always run in Lb.
        val weightLb = weight.toStorageLb()
        val step = when {
            incrementTiers.isNotEmpty() -> {
                val band = incrementTiers.incrementForAmount(weightLb.amount)
                if (band > 0.0) band else defaultBumpAmount(Weight.WeightUnit.Lb)
            }
            else -> defaultBumpAmount(Weight.WeightUnit.Lb)
        }
        val raw = Weight(weightLb.amount + step, Weight.WeightUnit.Lb)
        return incrementTiers.roundToIncrement(raw, loadType)
    }

    internal fun isOnTrackTopSet(
        lastPerformed: LoggedSetValue,
        completedSlot: SetSlot?,
        completedPrescribed: Weight?,
    ): Boolean {
        if (completedSlot == null || !completedSlot.isTopSet) return false
        if (completedPrescribed == null || completedPrescribed.amount <= 0.0) return false
        val performedLb = WeightConversion.amountInLb(lastPerformed.weight)
        val prescribedLb = WeightConversion.amountInLb(completedPrescribed)
        if (!WeightConversion.nearlyEqualLb(performedLb, prescribedLb)) return false
        if (completedSlot.repMin <= 0 && completedSlot.repMax <= 0) return false
        val effectiveRir = lastPerformed.rir ?: 0
        val achieved = lastPerformed.reps + effectiveRir - completedSlot.rirGoal
        val lo = completedSlot.repMin
        val hi = if (completedSlot.repMax > 0) completedSlot.repMax else Int.MAX_VALUE
        return achieved in lo..hi
    }

    internal fun resolveWorkingWeight(
        performed: Weight,
        targetPrescribed: Weight,
        completedPrescribed: Weight?,
    ): Weight {
        val performedLb = performed.toStorageLb()
        val targetLb = targetPrescribed.toStorageLb()
        if (targetLb.amount <= 0.0) return performedLb
        val baselineAmount =
            completedPrescribed?.let { WeightConversion.amountInLb(it) } ?: return targetLb
        return when {
            performedLb.amount > maxOf(baselineAmount, targetLb.amount) -> performedLb
            performedLb.amount < baselineAmount -> {
                if (targetLb.amount < performedLb.amount) targetLb else performedLb
            }
            else -> targetLb
        }
    }

    /** Performed anchor = 1; last of [remainingCount] post-anchor upcoming = remainingCount + 1. */
    internal fun lastRemainingSetIndex(remainingCount: Int): Int =
        (remainingCount + 1).coerceAtLeast(1)

    /**
     * Capacity at slot RIR. Non-positive loads are treated as failing the floor
     * (never “infinite capacity”), so reduce never treats 0 lb as a successful lattice stop.
     */
    internal fun capacityAtRir(
        e1rm: Double,
        weight: Weight,
        setIndex: Int,
        rirGoal: Int,
        perLiftSlope: Double?,
        perLiftIntercept: Double?,
    ): Int {
        if (weight.amount <= 0.0) return Int.MIN_VALUE / 4
        val raw0Rir = fatigueModel.expectedRepsForAddedSet(
            baselineOneRm = e1rm,
            targetWeight = weight,
            nextSetIndex = setIndex,
            perLiftSlope = perLiftSlope,
            perLiftIntercept = perLiftIntercept,
        )
        return raw0Rir - rirGoal
    }

    /**
     * Heaviest load ≤ [startFrom] with last-set capacity ≥ [BACKOFF_REP_FLOOR].
     * Delegates to shared [reduceWeightForLastSetFloor] with [FatigueModel]-backed capacity.
     */
    private fun reduceWeightForLastSetFloor(
        e1rm: Double,
        startFrom: Weight,
        lastSetIndex: Int,
        rirGoal: Int,
        perLiftSlope: Double?,
        perLiftIntercept: Double?,
        incrementTiers: List<IncrementTier>,
        loadType: LoadType,
    ): Weight = sharedReduceWeightForLastSetFloor(
        startFrom = startFrom,
        incrementTiers = incrementTiers,
        loadType = loadType,
        capacityAtRir = { amountLb ->
            capacityAtRir(
                e1rm = e1rm,
                weight = Weight(amountLb, Weight.WeightUnit.Lb),
                setIndex = lastSetIndex,
                rirGoal = rirGoal,
                perLiftSlope = perLiftSlope,
                perLiftIntercept = perLiftIntercept,
            )
        },
        defaultBumpAmount = ::defaultBumpAmount,
    )

    companion object {
        /** Default too-easy / step size when no tier band applies. */
        fun defaultBumpAmount(unit: Weight.WeightUnit): Double = defaultLoadBumpAmount(unit)
    }
}
