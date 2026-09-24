package com.stratum.domain.lift

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * A weight-banded increment step. A lift carries an ordered list of these so that, as the
 * working weight grows, the realistic load jump grows too (§2.2). The top tier has a `null`
 * [upperBound] meaning "and above" — e.g.:
 *
 * ```text
 * 0 – 40 lb   → +2.5 lb
 * 40 – 100    → +5
 * 100 – 300   → +10
 * 300+        → +25
 * ```
 *
 * [lowerBound] is inclusive, [upperBound] is exclusive. Tiers are validated as sorted,
 * non-overlapping, and with a positive [increment] by `ValidateIncrementTiersUseCase` before
 * they are persisted. All weights on a tier list share one unit (the lift's unit).
 */
data class IncrementTier(
    val lowerBound: Weight,
    val upperBound: Weight?,
    val increment: Weight,
) {
    init {
        require(increment.amount > 0) { "Increment must be positive" }
    }
}

/**
 * Rounds [amount] to the nearest point on the **multi-band lattice** defined by these tiers,
 * optionally shifted by micro (adder) offsets.
 *
 * Lattice construction (walk every band): for each tier `[lowerBound, upperBound)` with step
 * `S`, legal loads are `lowerBound + k·S` for integers `k ≥ 0` that stay inside the half-open
 * band. Contiguous default tiers starting at 0 with steps that divide the boundaries
 * (e.g. 0–40 @ 2.5, 40–100 @ 5) match zero-anchored multiples; **shifted** open bands do
 * not — e.g. `[2.5, ∞) @ 5` yields `2.5, 7.5, 12.5…`, not `0, 5, 10…`.
 *
 * With [micro], each legal load additionally admits `base + a` for every stop `a`
 * (knob-off `0` is always admitted). Minimizing over a fixed stop is equivalent to rounding
 * the shifted amount, so each stop rounds independently and the nearest candidate wins.
 *
 * Nearest-point ties break half-away-from-zero (prefer the larger magnitude). Empty tiers
 * are a passthrough. Sign is preserved so assisted negatives round in magnitude space.
 * Micro stops are converted to the tiers' unit here — the single `Weight` → amount
 * mapping point — so rounding call sites never strip units themselves. Write-time
 * validation still enforces a shared unit; conversion keeps unvalidated paths coherent.
 */
private fun List<IncrementTier>.roundMagnitude(
    amount: Double,
    micro: List<Weight> = emptyList(),
): Double {
    if (isEmpty()) return amount
    if (amount == 0.0) return 0.0
    val sign = if (amount < 0.0) -1.0 else 1.0
    return sign * nearestPointWithMicro(abs(amount), microStopAmounts(micro))
}

/**
 * Micro stop amounts in the tiers' unit with knob-off `0` first, distinct and
 * order-preserving. Empty [micro] yields an empty list (plain major-lattice path).
 * Single mapping point for `Weight` → raw amounts, so callers never strip units
 * themselves. Mixed-unit stops are converted to the tiers' unit instead of
 * silently skewing rounding.
 */
private fun List<IncrementTier>.microStopAmounts(micro: List<Weight>): List<Double> {
    if (micro.isEmpty()) return emptyList()
    if (isEmpty()) return micro.map { it.amount }
    val unit = first().lowerBound.unit
    return (listOf(0.0) + micro.map { WeightConversion.convertAmount(it.amount, it.unit, unit) }).distinct()
}

/**
 * Tracks the nearest candidate, breaking distance ties half-away-from-zero (larger
 * magnitude wins). Single owner of the epsilon tie policy shared by
 * [nearestLatticePoint] and [nearestPointWithMicro].
 */
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
            // Tie → half-away-from-zero (larger magnitude).
            best = candidate
            bestDist = dist
        }
    }
}

/**
 * Nearest non-negative lattice point, optionally admitting micro-shifted points
 * (`base + a` per normalized stop `a`, knob-off included). Empty [microAmounts] is
 * the plain [nearestLatticePoint] path.
 */
private fun List<IncrementTier>.nearestPointWithMicro(
    magnitude: Double,
    microAmounts: List<Double>,
): Double {
    if (microAmounts.isEmpty()) return nearestLatticePoint(magnitude)
    val tracker = NearestTracker()
    for (offset in microAmounts) {
        // Fixed-stop minimization: |m − (base + a)| = |(m − a) − base|.
        val candidate = nearestLatticePoint(magnitude - offset) + offset
        tracker.offer(candidate, abs(magnitude - candidate))
    }
    return tracker.best ?: nearestLatticePoint(magnitude)
}

/**
 * Nearest non-negative lattice point under the full tier walk. See [roundMagnitude].
 */
private fun List<IncrementTier>.nearestLatticePoint(amount: Double): Double {
    val tracker = NearestTracker()

    for (tier in this) {
        val lo = tier.lowerBound.amount
        val hi = tier.upperBound?.amount
        val step = tier.increment.amount
        if (step <= 0.0) continue

        val kNearest = ((amount - lo) / step).roundToLong()
        // Window around the analytic nearest, plus band endpoints (k = 0 and last in-band k).
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

    // No in-band candidate (should not happen for valid contiguous tiers from 0); fall back
    // to first-tier zero-anchored step so callers never get a NaN/null path.
    val best = tracker.best
    if (best == null) {
        val step = first().increment.amount
        if (step <= 0.0) return amount
        return (amount / step).roundToLong() * step
    }
    return best
}

/** Epsilon for half-open band membership and distance ties (same order as [LATTICE_EPSILON]). */
private const val LATTICE_BOUND_EPS: Double = 1e-9

/**
 * Inclusive step-index radius around the analytic nearest `k` to sample when searching
 * for the nearest in-band lattice point (e.g. 2 → evaluate kNearest−2 … kNearest+2).
 */
private const val LATTICE_K_NEIGHBOR_RADIUS: Long = 2L

/**
 * Resolves the increment step (in raw amount) for the band [amount] falls into. The matching
 * band is the first tier whose `[lowerBound, upperBound)` range contains the amount; if none
 * matches (the amount is below the lowest band), the first tier is used. Returns 0.0 for an
 * empty tier list. Shared by [roundMagnitude] and the program-generation rep-width math so
 * both resolve the same tier for a given load.
 */
fun List<IncrementTier>.incrementForAmount(amount: Double): Double {
    if (isEmpty()) return 0.0
    val tier = firstOrNull {
        amount >= it.lowerBound.amount &&
            (it.upperBound == null || amount < it.upperBound.amount)
    } ?: first()
    return tier.increment.amount
}

/**
 * Resolves the increment step as a [Weight] for the band [weight] falls into. Convenience
 * wrapper around [incrementForAmount] preserving the weight unit. Returns null for an empty
 * tier list.
 */
fun List<IncrementTier>.incrementFor(weight: Weight): Weight? {
    if (isEmpty()) return null
    return Weight(incrementForAmount(weight.amount), weight.unit)
}

/**
 * Rounds [weight] to the nearest multi-band lattice point (see [roundMagnitude]), optionally
 * admitting micro-shifted points (`base + adder stop`). Used so soft/e1RM loads land on
 * realistic jumps (e.g. `61.35` → `60`, `93.93` → `95`). An empty tier list is a
 * passthrough — no rounding. Empty [micro] is the plain major-lattice path.
 *
 * Prefer **not** re-applying this after a deliberate `base + band step` progression add:
 * logged bases can sit off-lattice (e.g. 135 on a 10 lb band), and re-snapping
 * `base + step` can half-step again (`145` → `150`). Use this for continuous estimates;
 * use [incrementFor] + raw add for session-to-session / too-easy advances.
 */
fun List<IncrementTier>.roundToIncrement(
    weight: Weight,
    micro: List<Weight> = emptyList(),
): Weight = Weight(roundMagnitude(weight.amount, micro), weight.unit)

/**
 * [LoadType]-aware tier rounding. For [LoadType.Assisted], the tier lookup uses
 * `abs(amount)` so negative assistance weights match the correct increment band,
 * and the result preserves the original sign (so `-47.3` rounds to `-45`, not `+45`).
 * For [LoadType.Bodyweight], returns the input unchanged (weight is always 0). For
 * [LoadType.Weighted], delegates to the standard [roundToIncrement].
 *
 * [micro] admits adder-shifted points for Weighted and Assisted alike (magnitude space).
 */
fun List<IncrementTier>.roundToIncrement(
    weight: Weight,
    loadType: LoadType,
    micro: List<Weight> = emptyList(),
): Weight =
    when (loadType) {
        LoadType.Bodyweight -> weight
        LoadType.Weighted -> roundToIncrement(weight, micro)
        LoadType.Assisted -> {
            val rounded = roundMagnitude(abs(weight.amount), micro)
            // Preserve the original sign so -47.3 rounds to -45, not +45.
            val signedRounded = if (weight.amount < 0) -rounded else rounded
            Weight(signedRounded, weight.unit)
        }
    }
