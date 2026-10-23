package com.stratum.domain.lift

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * Gym-realistic increment steps (lb). Inference snaps GCD / residual analysis onto this set.
 */
val INCREMENT_STEP_ALLOWLIST_LB: List<Double> =
    listOf(1.0, 2.0, 2.5, 5.0, 10.0, 12.5, 15.0, 20.0, 25.0)

/**
 * Gym-realistic increment steps (kg). Same magnitudes as lb for pin stacks and plate trees
 * common on metric machines.
 */
val INCREMENT_STEP_ALLOWLIST_KG: List<Double> =
    listOf(1.0, 2.0, 2.5, 5.0, 10.0, 12.5, 15.0, 20.0, 25.0)

/** Result of deterministic step + band inference for off-lattice logged weights. */
data class InferredIncrementLattice(
    val step: Double,
    val tiers: List<IncrementTier>,
) {
    /** The proposed tiers as a validated lattice (STR-165); inference only builds valid tiers. */
    val lattice: IncrementLattice get() = IncrementLattice(tiers)
}

/**
 * Bundled tiers + micro inference baseline.
 *
 * Proportionate step toward a single lattice value type (public signatures stay
 * stable while stacked lattice MRs are in flight): internal screening funnels
 * the parallel params through this bundle. Deferred: promote to a public value
 * type and fold in [screenLatticeWeights] once the stack lands.
 */
private data class LatticeBaseline(
    val tiers: List<IncrementTier>,
    val micro: List<Weight> = emptyList(),
)

/** Single normalization point binding parallel tiers + micro params into one baseline. */
private fun normalizeLatticeBaseline(
    tiers: List<IncrementTier>,
    micro: List<Weight>,
): LatticeBaseline = LatticeBaseline(tiers = tiers, micro = micro)

private fun LatticeBaseline.isOnLattice(
    weight: Weight,
    loadType: LoadType,
    epsilon: Double,
): Boolean = tiers.isOnLattice(weight, loadType, epsilon, micro)

/**
 * Infers a step size and an [IncrementLattice]-valid tier list from logged weights
 * that disagree with [currentTiers].
 *
 * Returns `null` when there is nothing to propose: bodyweight load, empty / near-zero sample
 * weights, mixed sample units, sample unit ≠ any [currentTiers] weight unit, or (when
 * [currentTiers] is non-empty) no off-lattice residual.
 *
 * When [currentTiers] is empty, every non-zero sample is treated as residual so a lattice
 * can be built from scratch. When non-empty, at least one sample must be off-lattice.
 * Assisted loads use absolute amounts (negative weights are valid samples).
 *
 * Non-empty [micro] admits `base + adder stop` points as on-lattice, so adder-exact
 * samples are knob use — not residual signal — and never reach step inference (v1 does
 * not propose adder changes).
 */
fun inferIncrementLattice(
    sampleWeights: List<Weight>,
    currentTiers: List<IncrementTier>,
    loadType: LoadType = LoadType.Weighted,
    epsilon: Double = LATTICE_EPSILON,
    validate: (List<IncrementTier>) -> IncrementTiersValidationResult =
        { IncrementLattice.validateTiers(it) },
    micro: List<Weight> = emptyList(),
): InferredIncrementLattice? {
    val prepared =
        prepareIncrementInferenceSamples(
            sampleWeights = sampleWeights,
            currentTiers = currentTiers,
            loadType = loadType,
            epsilon = epsilon,
            micro = micro,
        ) ?: return null

    val step =
        inferIncrementStep(
            residualAmounts = prepared.residualAmounts,
            allAmounts = prepared.allAmounts,
            allowlist = prepared.allowlist,
            epsilon = epsilon,
        ) ?: return null

    val tiers =
        buildProposedIncrementTiers(
            unit = prepared.unit,
            step = step,
            currentTiers = currentTiers,
            observedAmounts = prepared.allAmounts,
            validate = validate,
            epsilon = epsilon,
        )

    return InferredIncrementLattice(step = step, tiers = tiers)
}

/**
 * Validates samples and extracts residual / all amounts for step inference.
 * Returns `null` when inference has nothing to propose.
 */
internal fun prepareIncrementInferenceSamples(
    sampleWeights: List<Weight>,
    currentTiers: List<IncrementTier>,
    loadType: LoadType = LoadType.Weighted,
    epsilon: Double = LATTICE_EPSILON,
    micro: List<Weight> = emptyList(),
): PreparedIncrementInferenceSamples? {
    if (loadType == LoadType.Bodyweight) return null

    val baseline = normalizeLatticeBaseline(currentTiers, micro)

    val usable =
        sampleWeights.filter { weight ->
            abs(weight.amount) > epsilon
        }
    if (usable.isEmpty()) return null

    val unit = usable.first().unit
    val mixedUnits = usable.any { it.unit != unit }
    // Sample unit must match every weight on existing tiers (no silent unit coercion).
    val unitMismatch =
        baseline.tiers.isNotEmpty() &&
            baseline.tiers.any { tier -> !tier.weightsUseUnit(unit) }
    if (mixedUnits || unitMismatch) return null

    val residualWeights =
        if (baseline.tiers.isEmpty()) {
            usable
        } else {
            usable.filter { !baseline.isOnLattice(it, loadType, epsilon) }
        }
    if (residualWeights.isEmpty()) return null

    return PreparedIncrementInferenceSamples(
        unit = unit,
        residualAmounts = residualWeights.map { abs(it.amount) },
        allAmounts = usable.map { abs(it.amount) },
        allowlist = incrementStepAllowlist(unit),
    )
}

/** Intermediate amounts for [inferIncrementLattice] after sample validation. */
internal data class PreparedIncrementInferenceSamples(
    val unit: Weight.WeightUnit,
    val residualAmounts: List<Double>,
    val allAmounts: List<Double>,
    val allowlist: List<Double>,
)

/**
 * Picks the best allowlist step for residual (and preferably all) sample amounts.
 *
 * Preference order:
 * 1. Approximate pairwise-diff GCD snapped to allowlist, if it covers residuals
 * 2. Largest allowlist step covering all sample amounts
 * 3. Largest allowlist step covering residual amounts only
 * 4. **Best-effort fallback** — allowlist step that minimizes the worst residual after
 *    rounding (so odd typed weights like 33.33 still produce a proposal instead of silence)
 */
fun inferIncrementStep(
    residualAmounts: Collection<Double>,
    allAmounts: Collection<Double> = residualAmounts,
    allowlist: List<Double> = INCREMENT_STEP_ALLOWLIST_LB,
    epsilon: Double = LATTICE_EPSILON,
): Double? {
    if (residualAmounts.isEmpty()) return null

    val gcdSnap =
        approximatePairwiseGcd(allAmounts.ifEmpty { residualAmounts }, epsilon)
            ?.let { snapToAllowlist(it, allowlist, epsilon) }
    if (gcdSnap != null && residualAmounts.all { isNearMultiple(it, gcdSnap, epsilon) }) {
        return gcdSnap
    }

    val coveringAll =
        allowlist
            .sortedDescending()
            .firstOrNull { step -> allAmounts.all { isNearMultiple(it, step, epsilon) } }
    if (coveringAll != null) return coveringAll

    val coveringResiduals =
        allowlist
            .sortedDescending()
            .firstOrNull { step -> residualAmounts.all { isNearMultiple(it, step, epsilon) } }
    if (coveringResiduals != null) return coveringResiduals

    // No perfect cover (e.g. 33.33 is not within ε of any allowlist lattice). Pick the step
    // that lands residual amounts closest to a multiple, preferring coarser steps on ties so
    // we do not invent micro-plates when a 1 lb step and a 2 lb step are equally bad.
    return allowlist
        .sortedDescending()
        .minByOrNull { step -> maxRoundingError(residualAmounts, step) }
}

/**
 * Builds a proposed tier list: single open band at [step], or fine/coarse split when the
 * current open-band increment is coarser than [step] and observed loads sit in the fine range.
 *
 * Always returns a list accepted by [validate] (falls back to a single open band on failure).
 */
fun buildProposedIncrementTiers(
    unit: Weight.WeightUnit,
    step: Double,
    currentTiers: List<IncrementTier>,
    observedAmounts: Collection<Double>,
    validate: (List<IncrementTier>) -> IncrementTiersValidationResult =
        { IncrementLattice.validateTiers(it) },
    epsilon: Double = LATTICE_EPSILON,
): List<IncrementTier> {
    require(step > 0.0) { "step must be positive" }

    val coarse =
        currentTiers.lastOrNull()?.increment?.amount
            ?.takeIf { it > step + epsilon }

    val maxObserved = observedAmounts.maxOrNull() ?: 0.0
    val candidate: List<IncrementTier> =
        if (coarse != null && maxObserved > epsilon) {
            val upper = tidyUpperBound(maxObserved, epsilon)
            listOf(
                IncrementTier(
                    lowerBound = Weight(0.0, unit),
                    upperBound = Weight(upper, unit),
                    increment = Weight(step, unit),
                ),
                IncrementTier(
                    lowerBound = Weight(upper, unit),
                    upperBound = null,
                    increment = Weight(coarse, unit),
                ),
            )
        } else {
            singleOpenBand(unit, step)
        }

    return when (validate(candidate)) {
        IncrementTiersValidationResult.Valid -> candidate
        is IncrementTiersValidationResult.Invalid -> singleOpenBand(unit, step)
    }
}

/** True when every weight field on this tier uses [unit]. */
internal fun IncrementTier.weightsUseUnit(unit: Weight.WeightUnit): Boolean =
    lowerBound.unit == unit &&
        increment.unit == unit &&
        (upperBound == null || upperBound.unit == unit)

fun incrementStepAllowlist(unit: Weight.WeightUnit): List<Double> =
    when (unit) {
        Weight.WeightUnit.Lb -> INCREMENT_STEP_ALLOWLIST_LB
        Weight.WeightUnit.Kg -> INCREMENT_STEP_ALLOWLIST_KG
    }

/** Whether [amount] is within [epsilon] of an integer multiple of [step]. */
fun isNearMultiple(
    amount: Double,
    step: Double,
    epsilon: Double = LATTICE_EPSILON,
): Boolean {
    if (step <= 0.0) return false
    return roundingError(amount, step) <= epsilon
}

/** Absolute distance from [amount] to the nearest integer multiple of [step]. */
fun roundingError(
    amount: Double,
    step: Double,
): Double {
    if (step <= 0.0) return Double.POSITIVE_INFINITY
    val n = (amount / step).roundToLong()
    return abs(amount - n * step)
}

/** Worst [roundingError] across [amounts] for [step]. */
fun maxRoundingError(
    amounts: Collection<Double>,
    step: Double,
): Double {
    if (amounts.isEmpty()) return Double.POSITIVE_INFINITY
    return amounts.maxOf { roundingError(it, step) }
}

/**
 * Smallest tidy exclusive upper bound strictly above [maxFineWeight], snapped to a 5-unit grid
 * (common pin / plate landmarks at 5 / 10 / 25 multiples).
 */
fun tidyUpperBound(
    maxFineWeight: Double,
    epsilon: Double = LATTICE_EPSILON,
): Double {
    require(maxFineWeight >= 0.0)
    if (maxFineWeight <= epsilon) return 5.0
    var bound = ceil(maxFineWeight / 5.0) * 5.0
    if (bound <= maxFineWeight + epsilon) {
        bound += 5.0
    }
    return bound
}

/**
 * Approximate GCD of positive pairwise differences among [amounts], with floating ε.
 * Returns `null` when fewer than two distinct values or the GCD collapses under ε.
 */
fun approximatePairwiseGcd(
    amounts: Collection<Double>,
    epsilon: Double = LATTICE_EPSILON,
): Double? {
    val sorted = amounts.map { abs(it) }.filter { it > epsilon }.distinct().sorted()
    if (sorted.size < 2) return null

    val diffs = mutableListOf<Double>()
    for (i in sorted.indices) {
        for (j in i + 1 until sorted.size) {
            val d = sorted[j] - sorted[i]
            if (d > epsilon) diffs.add(d)
        }
    }
    if (diffs.isEmpty()) return null

    var g = diffs.first()
    for (d in diffs.drop(1)) {
        g = approximateGcd(g, d, epsilon)
        if (g <= epsilon) return null
    }
    return g
}

/**
 * Relative slack when snapping a raw GCD to the allowlist (5% of the raw value), so e.g.
 * 2.4 → 2.5 still matches while far outliers are rejected.
 */
const val SNAP_ALLOWLIST_RELATIVE_TOLERANCE: Double = 0.05

/** Snap [value] to the nearest allowlist entry within [epsilon]; null if none close enough. */
fun snapToAllowlist(
    value: Double,
    allowlist: List<Double>,
    epsilon: Double = LATTICE_EPSILON,
): Double? {
    if (value <= epsilon) return null
    return allowlist.minByOrNull { abs(it - value) }
        ?.takeIf {
            abs(it - value) <= epsilon.coerceAtLeast(value * SNAP_ALLOWLIST_RELATIVE_TOLERANCE)
        }
}

private fun singleOpenBand(unit: Weight.WeightUnit, step: Double): List<IncrementTier> =
    listOf(
        IncrementTier(
            lowerBound = Weight(0.0, unit),
            upperBound = null,
            increment = Weight(step, unit),
        ),
    )

/**
 * Euclidean GCD adapted for floating amounts. Stops when remainder ≤ [epsilon].
 */
private fun approximateGcd(
    a: Double,
    b: Double,
    epsilon: Double,
): Double {
    var x = abs(a)
    var y = abs(b)
    while (y > epsilon) {
        val r = x % y
        x = y
        y = r
    }
    return x
}
