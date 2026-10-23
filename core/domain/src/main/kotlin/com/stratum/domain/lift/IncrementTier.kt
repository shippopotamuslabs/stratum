package com.stratum.domain.lift

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
 * [lowerBound] is inclusive, [upperBound] is exclusive. A tier list only becomes a
 * usable lattice through [IncrementLattice], whose construction enforces the tiling /
 * unit / open-band / non-decreasing rules; all weights on a tier list share one unit
 * (the lift's unit).
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
 * Resolves the increment step (in raw amount) for the band [amount] falls into. The matching
 * band is the first tier whose `[lowerBound, upperBound)` range contains the amount; if none
 * matches (the amount is below the lowest band), the first tier is used. Returns 0.0 for an
 * empty tier list.
 *
 * Raw-list delegation to the lattice math in `IncrementLattice.kt`; prefer
 * [IncrementLattice.incrementForAmount] wherever a validated lattice exists.
 */
fun List<IncrementTier>.incrementForAmount(amount: Double): Double =
    latticeIncrementForAmount(this, amount)

/**
 * Resolves the increment step as a [Weight] for the band [weight] falls into. Returns null
 * for an empty tier list.
 */
fun List<IncrementTier>.incrementFor(weight: Weight): Weight? {
    if (isEmpty()) return null
    return Weight(incrementForAmount(weight.amount), weight.unit)
}

/**
 * Rounds [weight] to the nearest multi-band lattice point defined by these tiers,
 * optionally admitting micro-shifted points (`base + adder stop`). An empty tier list is
 * a passthrough — no rounding. Empty [micro] is the plain major-lattice path.
 *
 * Prefer **not** re-applying this after a deliberate `base + band step` progression add:
 * logged bases can sit off-lattice (e.g. 135 on a 10 lb band), and re-snapping
 * `base + step` can half-step again (`145` → `150`). Use this for continuous estimates;
 * use [incrementFor] + raw add for session-to-session / too-easy advances.
 *
 * Raw-list delegation to the lattice math in `IncrementLattice.kt`; prefer
 * [IncrementLattice.roundToIncrement] wherever a validated lattice exists.
 */
fun List<IncrementTier>.roundToIncrement(
    weight: Weight,
    micro: List<Weight> = emptyList(),
): Weight = Weight(latticeRoundMagnitude(this, weight.amount, micro), weight.unit)

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
            val rounded = latticeRoundMagnitude(this, kotlin.math.abs(weight.amount), micro)
            // Preserve the original sign so -47.3 rounds to -45, not +45.
            val signedRounded = if (weight.amount < 0) -rounded else rounded
            Weight(signedRounded, weight.unit)
        }
    }
