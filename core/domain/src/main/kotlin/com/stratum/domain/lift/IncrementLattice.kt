package com.stratum.domain.lift

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Absolute residual tolerance for lattice fit checks (same unit as the weight amount).
 *
 * Chosen so 62.5 on a 5-step lattice is clearly off-lattice while floating noise near a
 * true plate (e.g. 100/3 rounding) stays on-lattice.
 */
const val LATTICE_EPSILON: Double = 0.05

/**
 * The increment lattice of a lift or equipment row as one validated value object
 * (STR-165): the ordered tier bands plus the micro (adder knob) stops that ride on
 * them. Construction enforces every tier and micro rule in this file, so an invalid
 * lattice is unrepresentable — writers construct the lattice instead of remembering
 * to call a separate validator.
 *
 * Tier rules:
 * 1. **Anchored tiling** — the first band starts at any `lowerBound >= 0` (cable stacks
 *    need no fake `0–X` band); every later tier's `lowerBound` equals the previous
 *    tier's `upperBound`, so the bands tile without gaps or overlaps.
 * 2. **Mixed units forbidden** — every weight shares one unit.
 * 3. **Open final band** — the last tier's `upperBound` is `null` ("and above");
 *    intermediate tiers must have a non-null upper bound.
 * 4. **Non-decreasing increments** — a decreasing increment as load grows is almost
 *    always a data mistake, so it is rejected.
 *
 * Micro rules (empty stop list is always valid — no adder):
 * 1. **Tiers present** — a knob with no stack is meaningless.
 * 2. **Mixed units forbidden** — every stop shares the tiers' unit.
 * 3. **Non-negative**, **strictly ascending** (no duplicates), and **non-zero**
 *    (knob-off, the bare stack load, is always legal, so a `0` stop is meaningless).
 * 4. **Within the coarsest step** — every stop is `<=` the largest major increment.
 *
 * The empty tier list is a valid lattice (a lift with no tiers falls back to a global
 * default elsewhere). Construction failures throw [IncrementTiersInvalidException] or
 * [MicroIncrementsInvalidException]; callers that need the error set without throwing
 * (editor drafts, scan prefill) use [validateTiers] / [validateMicro].
 */
data class IncrementLattice(
    val tiers: List<IncrementTier>,
    val microIncrements: List<Weight> = emptyList(),
) {
    init {
        val tierErrors = tierErrors(tiers)
        if (tierErrors.isNotEmpty()) throw IncrementTiersInvalidException(tierErrors)
        val microErrors = microErrors(tiers, microIncrements)
        if (microErrors.isNotEmpty()) throw MicroIncrementsInvalidException(microErrors)
    }

    val isEmpty: Boolean get() = tiers.isEmpty()

    /** The increment step (raw amount) for the band [amount] falls into; 0.0 when empty. */
    fun incrementForAmount(amount: Double): Double = latticeIncrementForAmount(tiers, amount)

    /** The increment step as a [Weight] for the band [weight] falls into; null when empty. */
    fun incrementFor(weight: Weight): Weight? {
        if (tiers.isEmpty()) return null
        return Weight(incrementForAmount(weight.amount), weight.unit)
    }

    /**
     * Rounds [weight] to the nearest point on this lattice, admitting `base + stop`
     * points for this lattice's own micro stops. Empty tiers are a passthrough.
     */
    fun roundToIncrement(weight: Weight): Weight =
        Weight(latticeRoundMagnitude(tiers, weight.amount, microIncrements), weight.unit)

    /** [LoadType]-aware rounding; see the list-extension form in `IncrementTier.kt`. */
    fun roundToIncrement(weight: Weight, loadType: LoadType): Weight =
        when (loadType) {
            LoadType.Bodyweight -> weight
            LoadType.Weighted -> roundToIncrement(weight)
            LoadType.Assisted -> {
                val rounded = latticeRoundMagnitude(tiers, abs(weight.amount), microIncrements)
                Weight(if (weight.amount < 0) -rounded else rounded, weight.unit)
            }
        }

    /** Distance from [weight] to the nearest on-lattice amount under this lattice. */
    fun residual(
        weight: Weight,
        loadType: LoadType = LoadType.Weighted,
    ): Double {
        if (loadType == LoadType.Bodyweight) return 0.0
        if (tiers.isEmpty()) return Double.POSITIVE_INFINITY
        val rounded = roundToIncrement(weight, loadType)
        return abs(weight.amount - rounded.amount)
    }

    /** Whether [weight] lands on this lattice (within [epsilon]). */
    fun isOnLattice(
        weight: Weight,
        loadType: LoadType = LoadType.Weighted,
        epsilon: Double = LATTICE_EPSILON,
    ): Boolean {
        if (loadType == LoadType.Bodyweight) return true
        if (tiers.isEmpty()) return false
        return residual(weight, loadType) <= epsilon
    }

    /**
     * Indexes of bands whose steps do not divide evenly with at least one of
     * [samplesLb] (raw storage-pound magnitudes). Adder-exact samples are knob use,
     * not band misses, so this lattice's micro stops never trip the warning.
     */
    fun warnedIndexesForSamples(
        samplesLb: List<Double>,
        epsilon: Double = LATTICE_EPSILON,
    ): Set<Int> {
        if (samplesLb.isEmpty() || tiers.isEmpty()) return emptySet()
        val signal =
            if (microIncrements.isEmpty()) {
                samplesLb
            } else {
                samplesLb.filter { sample ->
                    !isOnLattice(Weight(sample, Weight.WeightUnit.Lb), epsilon = epsilon)
                }
            }
        if (signal.isEmpty()) return emptySet()
        return latticeWarnedIndexes(tiers, signal, epsilon)
    }

    companion object {
        /** The empty lattice: no tiers, no adder. */
        val EMPTY: IncrementLattice = IncrementLattice(emptyList())

        /**
         * Canonical non-throwing construction for `Result`-returning write paths:
         * success carries the lattice, failure carries the same
         * [IncrementTiersInvalidException] / [MicroIncrementsInvalidException] the
         * constructor throws.
         */
        fun resultOf(
            tiers: List<IncrementTier>,
            micro: List<Weight> = emptyList(),
        ): Result<IncrementLattice> = runCatching { IncrementLattice(tiers, micro) }

        /**
         * Prepares converted scan tiers for validation (STR-244). A scan reads a real
         * stack, whose printed top band is closed (the maximum weight), while the
         * lattice models the top band as open (tier rule 3). A complete read — no
         * scanned rows dropped in conversion — therefore gets its final band opened.
         * An incomplete read keeps its last band closed: that band is not the stack
         * top, and leaving it closed lets validation fail the scan instead of
         * prefilling an unbounded band the machine does not have.
         */
        fun prepareScannedTiers(
            tiers: List<IncrementTier>,
            complete: Boolean,
        ): List<IncrementTier> {
            if (!complete) return tiers
            val finalTier = tiers.lastOrNull() ?: return tiers
            if (finalTier.upperBound == null) return tiers
            return tiers.dropLast(1) + finalTier.copy(upperBound = null)
        }

        /** Non-throwing tier validation for drafts and other pre-construction checks. */
        fun validateTiers(tiers: List<IncrementTier>): IncrementTiersValidationResult {
            val errors = tierErrors(tiers)
            return if (errors.isEmpty()) {
                IncrementTiersValidationResult.Valid
            } else {
                IncrementTiersValidationResult.Invalid(errors)
            }
        }

        /** Non-throwing micro validation for drafts and other pre-construction checks. */
        fun validateMicro(
            tiers: List<IncrementTier>,
            micro: List<Weight>,
        ): MicroIncrementsValidationResult {
            val errors = microErrors(tiers, micro)
            return if (errors.isEmpty()) {
                MicroIncrementsValidationResult.Valid
            } else {
                MicroIncrementsValidationResult.Invalid(errors)
            }
        }

        /** The tier rule violations in [tiers]; empty set means the tiers form a lattice. */
        fun tierErrors(tiers: List<IncrementTier>): Set<IncrementTiersValidationError> {
            if (tiers.isEmpty()) return emptySet()
            val errors = mutableSetOf<IncrementTiersValidationError>()
            val unit = tiers.first().lowerBound.unit
            val allSameUnit = tiers.all { tier ->
                tier.lowerBound.unit == unit &&
                    (tier.upperBound == null || tier.upperBound.unit == unit) &&
                    tier.increment.unit == unit
            }
            if (!allSameUnit) errors.add(IncrementTiersValidationError.MixedUnits)

            var previousUpper: Double? = null
            var previousIncrement: Double = 0.0
            var contiguous = true
            for ((index, tier) in tiers.withIndex()) {
                val step = checkTier(
                    tier = tier,
                    index = index,
                    last = tiers.lastIndex,
                    previousUpper = previousUpper,
                    previousIncrement = previousIncrement,
                    onError = { error -> errors.add(error) },
                )
                if (!step.first) {
                    contiguous = false
                    break
                }
                previousUpper = step.second
                previousIncrement = step.third
            }
            if (!contiguous) errors.add(IncrementTiersValidationError.NotContiguous)
            return errors
        }

        /** The micro rule violations of [micro] against [tiers]; empty set means valid. */
        fun microErrors(
            tiers: List<IncrementTier>,
            micro: List<Weight>,
        ): Set<MicroIncrementsValidationError> {
            if (micro.isEmpty()) return emptySet()
            if (tiers.isEmpty()) {
                return setOf(MicroIncrementsValidationError.MicroWithoutTiers)
            }
            val errors = mutableSetOf<MicroIncrementsValidationError>()
            val unit = tiers.first().lowerBound.unit
            if (micro.any { it.unit != unit }) {
                errors.add(MicroIncrementsValidationError.MixedUnits)
            }
            val amounts = micro.map { it.amount }
            if (amounts.any { it < 0.0 }) {
                errors.add(MicroIncrementsValidationError.NegativeStop)
            }
            if (!amounts.zipWithNext().all { (prior, next) -> prior < next }) {
                errors.add(MicroIncrementsValidationError.UnsortedOrDuplicate)
            }
            if (amounts.any { it == 0.0 }) {
                errors.add(MicroIncrementsValidationError.MeaninglessZero)
            }
            val largestIncrement = tiers.maxOf { it.increment.amount }
            if (amounts.any { it > largestIncrement }) {
                errors.add(MicroIncrementsValidationError.ExceedsLargestIncrement)
            }
            return errors
        }

        /**
         * Validates one tier against its predecessor. Returns `false` when a structural
         * break (gap, zero-width band, or missing intermediate upper bound) means tiling
         * has failed and the caller should stop; `true` to continue.
         */
        private fun checkTier(
            tier: IncrementTier,
            index: Int,
            last: Int,
            previousUpper: Double?,
            previousIncrement: Double,
            onError: (IncrementTiersValidationError) -> Unit,
        ): Triple<Boolean, Double?, Double> {
            if (previousUpper == null) {
                if (tier.lowerBound.amount < 0.0) {
                    onError(IncrementTiersValidationError.NegativeLowerBound)
                    return Triple(false, previousUpper, previousIncrement)
                }
            } else if (tier.lowerBound.amount != previousUpper) {
                return Triple(false, previousUpper, previousIncrement)
            }
            val upper = tier.upperBound
            val isLast = index == last
            if (isLast && upper != null) {
                onError(IncrementTiersValidationError.FinalTierMustBeOpenEnded)
            }
            if (!isLast && upper == null) {
                onError(IncrementTiersValidationError.IntermediateTierMissingUpperBound)
                return Triple(false, previousUpper, previousIncrement)
            }
            if (upper != null && upper.amount <= tier.lowerBound.amount) {
                onError(IncrementTiersValidationError.ZeroWidthBand)
                return Triple(false, previousUpper, previousIncrement)
            }
            if (tier.increment.amount < previousIncrement) {
                onError(IncrementTiersValidationError.IncrementDecreased)
            }
            return Triple(true, upper?.amount, tier.increment.amount)
        }
    }
}

enum class IncrementTiersValidationError {
    MixedUnits,
    NotContiguous,
    ZeroWidthBand,
    IntermediateTierMissingUpperBound,
    FinalTierMustBeOpenEnded,
    IncrementDecreased,
    NegativeLowerBound,
}

sealed interface IncrementTiersValidationResult {
    data object Valid : IncrementTiersValidationResult
    data class Invalid(val errors: Set<IncrementTiersValidationError>) : IncrementTiersValidationResult
}

/** Thrown when a tier list cannot form an [IncrementLattice]. */
class IncrementTiersInvalidException(
    val errors: Set<IncrementTiersValidationError>,
) : IllegalArgumentException("Invalid increment tiers: $errors")

enum class MicroIncrementsValidationError {
    MixedUnits,
    NegativeStop,
    UnsortedOrDuplicate,
    MeaninglessZero,
    ExceedsLargestIncrement,
    MicroWithoutTiers,
}

sealed interface MicroIncrementsValidationResult {
    data object Valid : MicroIncrementsValidationResult
    data class Invalid(val errors: Set<MicroIncrementsValidationError>) : MicroIncrementsValidationResult
}

/** Thrown when micro stops cannot join an [IncrementLattice] with its tiers. */
class MicroIncrementsInvalidException(
    val errors: Set<MicroIncrementsValidationError>,
) : Exception("Invalid micro increments: $errors")

// ---------------------------------------------------------------------------
// Lattice math. The implementations live here, owned by the lattice; the
// `List<IncrementTier>` extensions in this file and in `IncrementTier.kt` are
// thin delegations for raw-list holders (drafts, proposals, wire shapes) that
// legitimately are not lattices yet.
// ---------------------------------------------------------------------------

/**
 * Rounds [amount] to the nearest point on the multi-band lattice defined by [tiers],
 * optionally shifted by micro (adder) offsets. See [IncrementLattice.roundToIncrement].
 */
internal fun latticeRoundMagnitude(
    tiers: List<IncrementTier>,
    amount: Double,
    micro: List<Weight> = emptyList(),
): Double {
    if (tiers.isEmpty()) return amount
    if (amount == 0.0) return 0.0
    val sign = if (amount < 0.0) -1.0 else 1.0
    return sign * nearestPointWithMicro(tiers, abs(amount), microStopAmounts(tiers, micro))
}

/** The increment step (raw amount) for the band [amount] falls into; 0.0 for empty tiers. */
internal fun latticeIncrementForAmount(tiers: List<IncrementTier>, amount: Double): Double {
    if (tiers.isEmpty()) return 0.0
    val tier = tiers.firstOrNull {
        amount >= it.lowerBound.amount &&
            (it.upperBound == null || amount < it.upperBound.amount)
    } ?: tiers.first()
    return tier.increment.amount
}

/** Indexes of bands whose steps do not divide evenly with at least one sample. */
internal fun latticeWarnedIndexes(
    tiers: List<IncrementTier>,
    samplesLb: List<Double>,
    epsilon: Double,
): Set<Int> {
    if (samplesLb.isEmpty() || tiers.isEmpty()) return emptySet()
    return samplesLb
        .filter { it > 0.0 }
        .mapNotNull { sample -> warnedIndexForSample(tiers, sample, epsilon) }
        .toSet()
}

private fun microStopAmounts(tiers: List<IncrementTier>, micro: List<Weight>): List<Double> {
    if (micro.isEmpty()) return emptyList()
    if (tiers.isEmpty()) return micro.map { it.amount }
    val unit = tiers.first().lowerBound.unit
    return (listOf(0.0) + micro.map {
        WeightConversion.convertAmount(it.amount, it.unit, unit)
    }).distinct()
}

private class NearestTracker {
    var best: Double? = null
        private set
    private var bestDist: Double = Double.POSITIVE_INFINITY

    fun offer(candidate: Double, dist: Double) {
        val current = best
        if (current == null || dist < bestDist - LATTICE_BOUND_EPS) {
            best = candidate
            bestDist = dist
        } else if (abs(dist - bestDist) <= LATTICE_BOUND_EPS && candidate > current) {
            best = candidate
            bestDist = dist
        }
    }
}

private fun nearestPointWithMicro(
    tiers: List<IncrementTier>,
    magnitude: Double,
    microAmounts: List<Double>,
): Double {
    if (microAmounts.isEmpty()) return nearestLatticePoint(tiers, magnitude)
    val tracker = NearestTracker()
    for (offset in microAmounts) {
        val candidate = nearestLatticePoint(tiers, magnitude - offset) + offset
        tracker.offer(candidate, abs(magnitude - candidate))
    }
    return tracker.best ?: nearestLatticePoint(tiers, magnitude)
}

private fun nearestLatticePoint(tiers: List<IncrementTier>, amount: Double): Double {
    val tracker = NearestTracker()

    for (tier in tiers) {
        val lo = tier.lowerBound.amount
        val hi = tier.upperBound?.amount
        val step = tier.increment.amount
        if (step <= 0.0) continue

        val kNearest = ((amount - lo) / step).roundToLong()
        val kMaxInBand =
            if (hi != null) {
                kotlin.math.floor((hi - lo - LATTICE_BOUND_EPS) / step).toLong().coerceAtLeast(0L)
            } else {
                null
            }
        val ks = buildList {
            for (dk in -LATTICE_K_NEIGHBOR_RADIUS..LATTICE_K_NEIGHBOR_RADIUS) {
                add(kNearest + dk)
            }
            add(0L)
            if (kMaxInBand != null) add(kMaxInBand)
        }
        for (k in ks) {
            val candidate = lo + k * step
            val inBand =
                k >= 0L &&
                    candidate >= lo - LATTICE_BOUND_EPS &&
                    (hi == null || candidate < hi - LATTICE_BOUND_EPS)
            if (!inBand) continue

            tracker.offer(candidate, abs(amount - candidate))
        }
    }

    val best = tracker.best
    if (best == null) {
        val step = tiers.first().increment.amount
        if (step <= 0.0) return amount
        return (amount / step).roundToLong() * step
    }
    return best
}

private fun warnedIndexForSample(
    tiers: List<IncrementTier>,
    sample: Double,
    epsilon: Double,
): Int? {
    val index =
        tiers.indexOfFirst { tier ->
            val upper = tier.upperBound
            sample >= tier.lowerBound.amount - epsilon &&
                (upper == null || sample < upper.amount - epsilon)
        }
    if (index < 0) return null
    val tier = tiers[index]
    val step = tier.increment.amount
    if (step <= 0.0) return null
    val remainder = abs((sample - tier.lowerBound.amount) % step)
    return index.takeIf { minOf(remainder, step - remainder) > epsilon }
}

/** Epsilon for half-open band membership and distance ties (same order as [LATTICE_EPSILON]). */
private const val LATTICE_BOUND_EPS: Double = 1e-9

/** Step-index radius around the analytic nearest `k` to sample for the nearest point. */
private const val LATTICE_K_NEIGHBOR_RADIUS: Long = 2L

// ---------------------------------------------------------------------------
// List delegations for raw tier lists that are not lattices (drafts, proposals).
// Prefer the [IncrementLattice] members wherever a validated lattice exists.
// ---------------------------------------------------------------------------

/**
 * Distance from [weight] to the nearest on-lattice amount under these tiers.
 * Empty tiers have no lattice → [Double.POSITIVE_INFINITY].
 */
fun List<IncrementTier>.residual(
    weight: Weight,
    loadType: LoadType = LoadType.Weighted,
    micro: List<Weight> = emptyList(),
): Double {
    if (loadType == LoadType.Bodyweight) return 0.0
    if (isEmpty()) return Double.POSITIVE_INFINITY
    val rounded = roundToIncrement(weight, loadType, micro)
    return abs(weight.amount - rounded.amount)
}

/** Whether [weight] lands on the increment lattice defined by these tiers. */
fun List<IncrementTier>.isOnLattice(
    weight: Weight,
    loadType: LoadType = LoadType.Weighted,
    epsilon: Double = LATTICE_EPSILON,
    micro: List<Weight> = emptyList(),
): Boolean {
    if (loadType == LoadType.Bodyweight) return true
    if (isEmpty()) return false
    return residual(weight, loadType, micro) <= epsilon
}

/** Indexes of bands whose steps do not divide evenly with at least one of [samplesLb]. */
fun List<IncrementTier>.warnedIndexesForSamples(
    samplesLb: List<Double>,
    epsilon: Double = LATTICE_EPSILON,
): Set<Int> = latticeWarnedIndexes(this, samplesLb, epsilon)

/**
 * [warnedIndexesForSamples] excluding samples that land on `base + adder stop`
 * ([micro], storage pounds). Empty [micro] is the plain major-lattice path.
 */
fun List<IncrementTier>.warnedIndexesForSamples(
    samplesLb: List<Double>,
    micro: List<Weight>,
    epsilon: Double = LATTICE_EPSILON,
): Set<Int> {
    if (samplesLb.isEmpty() || isEmpty()) return emptySet()
    if (micro.isEmpty()) return warnedIndexesForSamples(samplesLb, epsilon)
    val signal =
        samplesLb.filter { sample ->
            !isOnLattice(Weight(sample, Weight.WeightUnit.Lb), micro = micro, epsilon = epsilon)
        }
    if (signal.isEmpty()) return emptySet()
    return warnedIndexesForSamples(signal, epsilon)
}
