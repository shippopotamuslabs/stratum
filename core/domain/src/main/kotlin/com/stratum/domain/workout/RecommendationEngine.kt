package com.stratum.domain.workout

import com.stratum.domain.training.EstimatedOneRm
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.WeightConversion
import com.stratum.domain.lift.incrementForAmount
import com.stratum.domain.lift.toStorageLb
import com.stratum.domain.workout.fatigue.FatigueModel
import com.stratum.domain.workout.fatigue.defaultLoadBumpAmount
import com.stratum.domain.workout.fatigue.reduceWeightForLastSetFloor as sharedReduceWeightForLastSetFloor
import javax.inject.Inject

/**
 * Overlay-only recommendation engine (D4). **Single** Hold-vs-reduce path for remaining
 * straight-set load (Standard block or post-myo/drop standards). Weight selection uses the
 * same [FatigueModel] oracle as rep projection in [RecomputeRecommendationsUseCase].
 *
 * Decision tree (in order):
 * 1. **On-track performance** — prescribed load hit and not too hard / not a large RIR
 *    surplus → `null` when [keepSnapshotWhenOnTrack] is true (caller keeps snapshot **weight**
 *    and still projects fatigue reps). Applies to **any** straight set (top or backoff),
 *    not only the program top set — completing exact prescriptions mid-block must not
 *    re-run last-set floor Reduce (that would drop weight the snapshot already committed
 *    for the whole straight set). When [keepSnapshotWhenOnTrack] is false (post-myo/drop),
 *    fall through so last-set floor runs.
 * 2. **Working weight** — re-anchor heavier/lighter vs intentional backoff
 *    ([resolveWorkingWeight]). Callers must pass **snapshot / program** prescriptions as
 *    [currentWeight] / [completedSetPrescribedWeight], never prior live overlay weights
 *    (avoids too-easy bump ratcheting on every recompute).
 * 3. **Too easy** — ceiling exceeded by ≥ 2 RIR-adjusted reps **or** RIR surplus ≥ 2 on a
 *    top set that still hit the work → try one increment above **performed**. If the bump
 *    fails last-set floor → **Hold performed** (never Reduce after a too-easy top set).
 * 4. **Hold vs Reduce** — last remaining set capacity ≥ [BACKOFF_REP_FLOOR] → Hold;
 *    else Reduce via oracle search (hard / under-performed paths only).
 *
 * Last-set index: performed = 1; with [remainingCount] upcoming slots after the anchor,
 * last index = `remainingCount + 1`. Callers must count open minis/drops between the
 * performance anchor and the last remaining standard in [remainingCount] so floor index
 * matches fatigue set-index for rep projection.
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
        /**
         * Pure straight blocks: on-track top set → `null` (keep snapshot).
         * Post-myo/drop: `false` so the same Hold/Reduce + last-set floor still runs.
         */
        keepSnapshotWhenOnTrack: Boolean = true,
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
            keepSnapshotWhenOnTrack = keepSnapshotWhenOnTrack,
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
        keepSnapshotWhenOnTrack: Boolean,
    ): RecommendationOutcome? {
        val onTrack =
            isOnTrackPerformance(lastPerformed, completedSlot, completedSetPrescribedWeight)
        if (onTrack && keepSnapshotWhenOnTrack) return null

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
        val tooEasy = isTooEasyTopSet(lastPerformed, completedSlot)
        // Post-cluster on-track fall-through skips too-easy (load already matched prescription).
        if (!onTrack && tooEasy) {
            return tooEasyOutcome(
                lastPerformed = lastPerformed,
                e1rm = e1rm,
                lastSetIndex = lastSetIndex,
                targetRirGoal = targetSlot.rirGoal,
                perLiftSlope = perLiftSlope,
                perLiftIntercept = perLiftIntercept,
                incrementTiers = incrementTiers,
                loadType = loadType,
            )
        }
        return holdOrReduce(
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
     * Top set left ≥2 RIR above goal (with real work done) and/or cleared the rep ceiling
     * by ≥2 RIR-adjusted reps.
     */
    internal fun isTooEasyTopSet(
        lastPerformed: LoggedSetValue,
        completedSlot: SetSlot?,
    ): Boolean {
        if (completedSlot == null || !completedSlot.isTopSet) return false
        val effectiveRir = lastPerformed.rir ?: 0
        val rirSurplus = effectiveRir - completedSlot.rirGoal
        // Hit meaningful work with much more left in the tank (e.g. 10@3 when goal was 1).
        if (rirSurplus >= 2 && lastPerformed.reps >= 1) return true
        if (completedSlot.repMax <= 0) return false
        val achieved = lastPerformed.reps + effectiveRir - completedSlot.rirGoal
        return achieved - completedSlot.repMax >= 2
    }

    /**
     * One increment above performed when last-set still clears the floor; otherwise
     * **Hold performed** — never Reduce after a too-easy top set.
     */
    private fun tooEasyOutcome(
        lastPerformed: LoggedSetValue,
        e1rm: Double,
        lastSetIndex: Int,
        targetRirGoal: Int,
        perLiftSlope: Double?,
        perLiftIntercept: Double?,
        incrementTiers: List<IncrementTier>,
        loadType: LoadType,
    ): RecommendationOutcome {
        val performed = lastPerformed.weight
        val bumped = tooEasyBump(performed, incrementTiers, loadType)
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
            RecommendationOutcome.Hold(performed)
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
     * 5 lb / 2.5 kg. Returns the exact sum — does **not** re-snap via [roundToIncrement],
     * which zero-anchors to multiples of the band step and can double-jump micro-plate
     * bases (e.g. 2.5 + 5 → 7.5 → nearest 5s = 10).
     */
    internal fun tooEasyBump(
        weight: Weight,
        incrementTiers: List<IncrementTier>,
        @Suppress("UNUSED_PARAMETER") loadType: LoadType,
    ): Weight {
        // Tiers are Lb magnitudes; step math always runs in Lb.
        val weightLb = weight.toStorageLb()
        val step = when {
            incrementTiers.isNotEmpty() -> {
                val band = incrementTiers.incrementForAmount(weightLb.amount)
                if (band > 0.0) band else defaultBumpAmount(Weight.WeightUnit.Lb)
            }
            else -> defaultBumpAmount(Weight.WeightUnit.Lb)
        }
        return Weight(weightLb.amount + step, Weight.WeightUnit.Lb)
    }

    /**
     * True when the lifter hit the **prescribed load** without under- or over-shooting enough
     * to warrant a live weight rewrite. Used to keep the snapshot's shared straight-set weight.
     *
     * - Top / ranged slots: RIR-adjusted achieved reps in [repMin, repMax].
     * - Backoff (0/0 RIR-only): effective capacity at goal RIR ≥ [BACKOFF_REP_FLOOR]
     *   (same floor the reduce path protects).
     *
     * Too-easy top sets (large RIR surplus) are never on-track so the bump path can run.
     */
    internal fun isOnTrackPerformance(
        lastPerformed: LoggedSetValue,
        completedSlot: SetSlot?,
        completedPrescribed: Weight?,
    ): Boolean {
        val slot = completedSlot
        val prescribed = completedPrescribed
        if (slot == null || prescribed == null || prescribed.amount <= 0.0) return false
        val weightMatches =
            WeightConversion.nearlyEqualLb(
                WeightConversion.amountInLb(lastPerformed.weight),
                WeightConversion.amountInLb(prescribed),
            )
        // Extra RIR on a top set means the load was too light.
        if (!weightMatches || isTooEasyTopSet(lastPerformed, slot)) return false

        val effectiveRir = lastPerformed.rir ?: 0
        val achieved = lastPerformed.reps + effectiveRir - slot.rirGoal
        return if (slot.repMin > 0 || slot.repMax > 0) {
            val lo = slot.repMin
            val hi = if (slot.repMax > 0) slot.repMax else Int.MAX_VALUE
            achieved in lo..hi
        } else {
            // RIR-only backoff: on-track if not "too hard" vs the global floor.
            achieved >= BACKOFF_REP_FLOOR
        }
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

    /** Weight from a non-null [recommendNextSet] outcome. */
    fun weightOf(outcome: RecommendationOutcome): Weight =
        when (outcome) {
            is RecommendationOutcome.Hold -> outcome.weight
            is RecommendationOutcome.Reduce -> outcome.weight
        }

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
